# Keystore de depuración del proyecto

`debug.keystore` firma el APK de release que se publica en cada GitHub Release (`sprint-N`). Es una keystore de **depuración** compartida por el equipo y por CI: se versiona a propósito para que todos los APK del proyecto lleven la misma firma y las actualizaciones se instalen sobre la versión anterior en los dispositivos de prueba. Nunca se usa para publicar en una tienda ni se guarda aquí una keystore de producción (`docs/08-flujo-de-trabajo-git.md`, sección 5).

| Dato | Valor |
|---|---|
| Archivo | `keystore/debug.keystore` (PKCS12) |
| Alias | `vinilos` |
| Algoritmo | RSA 2048, SHA384withRSA, 10.000 días |
| Titular | CN=Vinilos debug, OU=MISW Aplicaciones Moviles, O=Equipo 3, L=Bogota, C=CO |
| Creada | 2026-10-19 |

Las contraseñas no están en el repositorio. Cada integrante las pone en su `local.properties` (no versionado) y CI las lee de los secrets del repositorio:

| `local.properties` | Secret de GitHub Actions |
|---|---|
| `keystore.password` | `KEYSTORE_PASSWORD` |
| `keystore.alias` (`vinilos`) | `KEY_ALIAS` |
| `keystore.keyPassword` | `KEY_PASSWORD` |

Sin esos valores, `./gradlew assembleRelease` falla con un mensaje que indica cuál falta; `assembleDebug` no los necesita.

Regenerarla solo si se pierde (los APK anteriores dejarán de actualizarse sobre los nuevos):

```
keytool -genkeypair -v -keystore keystore/debug.keystore -storetype PKCS12 -alias vinilos \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Vinilos debug, OU=MISW Aplicaciones Moviles, O=Equipo 3, L=Bogota, C=CO"
```

Verificar la firma de un APK: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`.
