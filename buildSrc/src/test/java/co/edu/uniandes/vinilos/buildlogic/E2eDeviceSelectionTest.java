package co.edu.uniandes.vinilos.buildlogic;

import org.gradle.api.GradleException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class E2eDeviceSelectionTest {
    private static final String BOTH = "List of devices attached\nTABLET\tdevice\nemulator-5554\tdevice\n";

    @Test public void selectsTabletWithAnEmulatorAlsoConnected() {
        assertEquals("TABLET", E2eEnvironment.selectSerial(BOTH, "TABLET"));
    }

    @Test public void selectsEmulatorWithATabletAlsoConnected() {
        assertEquals("emulator-5554", E2eEnvironment.selectSerial(BOTH, "emulator-5554"));
    }

    @Test public void rejectsAmbiguousSelectionInsteadOfTestingAnotherDevice() {
        assertThrows(GradleException.class, () -> E2eEnvironment.selectSerial(BOTH, ""));
    }

    @Test public void rejectsMissingOrUnauthorizedTarget() {
        assertThrows(GradleException.class, () -> E2eEnvironment.selectSerial(BOTH, "MISSING"));
        assertThrows(GradleException.class, () -> E2eEnvironment.selectSerial("TABLET\tunauthorized\n", "TABLET"));
        assertThrows(GradleException.class, () -> E2eEnvironment.selectSerial("emulator-5554\toffline\n", "emulator-5554"));
    }

    @Test public void selectsTheOnlyAuthorizedDeviceByDefault() {
        assertEquals("TABLET", E2eEnvironment.selectSerial("TABLET\tdevice\nother\toffline\n", ""));
    }

    @Test public void rejectsAnEmptyDeviceList() {
        assertThrows(GradleException.class, () -> E2eEnvironment.selectSerial("List of devices attached\n\n", ""));
    }
}
