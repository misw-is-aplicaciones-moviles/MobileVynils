package co.edu.uniandes.vinilos.buildlogic;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/** Una base y un backend por invocación; nunca descubre ni reutiliza contenedores de desarrollo. */
public abstract class E2eEnvironment implements BuildService<E2eEnvironment.Parameters>, AutoCloseable {
    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getRoot();
        DirectoryProperty getReports();
        RegularFileProperty getAdb();
        Property<String> getImage();
        Property<String> getSerial();
    }

    private static final String DEVICE_PORT = "tcp:31370";
    private String project;
    private String serial;
    private boolean reversed;
    private boolean ready;
    private File report;
    private long startedAtMillis;

    public synchronized long getStartedAtMillis() {
        return startedAtMillis;
    }

    public synchronized void start() {
        if (ready) return;
        startedAtMillis = System.currentTimeMillis();
        project = "vinilos-e2e-" + UUID.randomUUID().toString().substring(0, 12);
        report = new File(getParameters().getReports().get().getAsFile(), project);
        report.mkdirs();
        try {
            command(List.of("docker", "compose", "version"), null);
            command(List.of("docker", "image", "inspect", "--format", "{{.Id}}", getParameters().getImage().get()), null);
            command(List.of("docker", "image", "inspect", "--format", "{{.Id}}", "postgres:17"), null);
            selectDevice();
            Files.writeString(new File(report, "project.txt").toPath(), project + "\n");
            compose(List.of("up", "-d", "--wait", "--wait-timeout", "90"), null);
            String db = compose(List.of("ps", "-q", "db"), null).trim();
            if (db.isEmpty()) throw new GradleException("No se creó PostgreSQL de E2E");
            command(List.of("docker", "exec", "-i", db, "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", "postgres", "-d", "vinilos_e2e"),
                    new File(getParameters().getRoot().get().getAsFile(), "tests/e2e/seed.sql"));
            String endpoint = compose(List.of("port", "backend", "3000"), null).trim();
            String hostPort = endpoint.substring(endpoint.lastIndexOf(':') + 1);
            if (!hostPort.matches("[0-9]+")) throw new GradleException("Puerto E2E inválido: " + endpoint);
            // No sustituye una redirección ya existente en la tablet.
            adb(List.of("reverse", "--no-rebind", DEVICE_PORT, "tcp:" + hostPort));
            reversed = true;
            ready = true;
            System.out.println("E2E aislado: " + project + " en " + serial + "; reportes: " + report);
        } catch (Exception error) {
            try { close(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw new GradleException("No se pudo preparar E2E aislado. Revisa " + report
                    + ". Requiere Docker, Compose, imágenes locales y un dispositivo autorizado.\n" + error.getMessage(), error);
        }
    }

    private void selectDevice() throws Exception {
        String devices = command(List.of(getParameters().getAdb().get().getAsFile().getAbsolutePath(), "devices"), null);
        serial = selectSerial(devices, getParameters().getSerial().get());
        Files.writeString(new File(report, "device.txt").toPath(), serial + "\n");
    }

    // ANDROID_SERIAL lo consume también AGP: preparar y ejecutar siempre apuntan al mismo equipo.
    static String selectSerial(String devices, String chosen) {
        List<String> available = new ArrayList<>();
        for (String line : devices.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2 && parts[1].equals("device")) available.add(parts[0]);
        }
        if (!chosen.isEmpty()) {
            if (!available.contains(chosen)) {
                throw new GradleException("ANDROID_SERIAL=" + chosen
                        + " no corresponde a un dispositivo autorizado. Disponibles: " + available);
            }
            return chosen;
        }
        if (available.size() != 1) {
            throw new GradleException("Elige un dispositivo con ANDROID_SERIAL. Disponibles: " + available
                    + ". Puedes mantener la tablet y los emuladores conectados.");
        }
        return available.get(0);
    }

    private String adb(List<String> arguments) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                getParameters().getAdb().get().getAsFile().getAbsolutePath(), "-s", serial));
        args.addAll(arguments);
        return command(args, null);
    }

    private String compose(List<String> arguments, File input) throws Exception {
        List<String> args = new ArrayList<>(List.of("docker", "compose", "--project-name", project,
                "--file", new File(getParameters().getRoot().get().getAsFile(), "tests/e2e/compose.yml").getAbsolutePath()));
        args.addAll(arguments);
        return command(args, input);
    }

    private String command(List<String> args, File input) throws Exception {
        File output = File.createTempFile("command-", ".log", report);
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output);
        builder.environment().putAll(Map.of("E2E_BACKEND_IMAGE", getParameters().getImage().get()));
        if (input != null) builder.redirectInput(input);
        Process process = builder.start();
        if (input == null) process.getOutputStream().close();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new GradleException("Tiempo agotado: " + String.join(" ", args));
        }
        String text = Files.readString(output.toPath(), StandardCharsets.UTF_8);
        Files.writeString(new File(report, "commands.log").toPath(),
                "$ " + String.join(" ", args) + "\n" + text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (process.exitValue() != 0) throw new GradleException(String.join(" ", args) + "\n" + text);
        return text;
    }

    public synchronized void stop() {
        try { close(); } catch (Exception error) { throw new GradleException("Falló el descarte de " + project, error); }
    }

    @Override public synchronized void close() throws Exception {
        if (project == null) return;
        Exception failure = null;
        if (reversed) {
            try { adb(List.of("reverse", "--remove", DEVICE_PORT)); reversed = false; }
            catch (Exception error) { failure = error; }
        }
        try {
            compose(List.of("logs", "--no-color"), null);
        } catch (Exception error) {
            // Un fallo al capturar logs no debe impedir retirar los recursos.
            System.err.println("No se pudieron guardar logs E2E: " + error.getMessage());
        }
        try {
            compose(List.of("down", "--volumes", "--remove-orphans", "--timeout", "10"), null);
        } catch (Exception error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
        Files.writeString(new File(report, "stopped.txt").toPath(), "Entorno descartado\n");
        project = null;
        ready = false;
    }
}
