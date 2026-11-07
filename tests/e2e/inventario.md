# Correspondencia de pruebas instrumentadas

Base: `4dd58f9`. Conteo estático de métodos `@Test`; no representa resultados ejecutados. Se conservan los 119 métodos existentes y se añaden seis comprobaciones de infraestructura, independientes de escenarios funcionales.

| Clase | Métodos | Tipo | Datos/regla reutilizados |
|---|---:|---|---|
| [AppLaunchTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/AppLaunchTest.kt) | 2 | UI/humo | Sin helper HTTP propio |
| [AlbumDaoTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/database/AlbumDaoTest.kt) | 9 | Room/migraciones | Sin helper HTTP propio |
| [CollectorDaoTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/database/CollectorDaoTest.kt) | 2 | Room/migraciones | Sin helper HTTP propio |
| [PerformerDaoTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/database/PerformerDaoTest.kt) | 7 | Room/migraciones | Sin helper HTTP propio |
| [TrackDaoTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/database/TrackDaoTest.kt) | 4 | Room/migraciones | Sin helper HTTP propio |
| [VinilosMigrationTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/database/VinilosMigrationTest.kt) | 3 | Room/migraciones | Sin helper HTTP propio |
| [BackendConnectionTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/network/BackendConnectionTest.kt) | 1 | HTTP | Sin helper HTTP propio |
| [AsyncWorkIdlingResourceTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/testing/AsyncWorkIdlingResourceTest.kt) | 3 | Infraestructura | Sin helper HTTP propio |
| [NetworkFaultsTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/testing/NetworkFaultsTest.kt) | 3 | Infraestructura | Sin helper HTTP propio |
| [AlbumCreateScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/AlbumCreateScreenTest.kt) | 6 | UI/humo | Sin helper HTTP propio |
| [AlbumDetailScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/AlbumDetailScreenTest.kt) | 4 | UI/humo | Sin helper HTTP propio |
| [AlbumListScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/AlbumListScreenTest.kt) | 5 | UI/humo | Sin helper HTTP propio |
| [BottomNavigationTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/BottomNavigationTest.kt) | 2 | UI/humo | Sin helper HTTP propio |
| [CollectorAdapterTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/CollectorAdapterTest.kt) | 1 | Componente UI | Sin helper HTTP propio |
| [CollectorAlbumPickerScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/CollectorAlbumPickerScreenTest.kt) | 8 | UI/humo | BackendCollectorAlbums, BackendFirewall |
| [CollectorDetailScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/CollectorDetailScreenTest.kt) | 7 | UI/humo | BackendCollectors, BackendFirewall |
| [CollectorListScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/CollectorListScreenTest.kt) | 6 | UI/humo | BackendCollectors, BackendFirewall |
| [PerformerDetailScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/PerformerDetailScreenTest.kt) | 6 | UI/humo | BackendPerformers |
| [PerformerListScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/PerformerListScreenTest.kt) | 11 | UI/humo | BackendFirewall, BackendPerformers |
| [PrizeCreateScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/PrizeCreateScreenTest.kt) | 6 | UI/humo | BackendPrizes |
| [PrizePickerScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/PrizePickerScreenTest.kt) | 5 | UI/humo | BackendPrizes |
| [RoleSelectTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/RoleSelectTest.kt) | 5 | UI/humo | Sin helper HTTP propio |
| [SharedComponentsTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/SharedComponentsTest.kt) | 3 | UI/humo | Sin helper HTTP propio |
| [TabNavigationTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/TabNavigationTest.kt) | 9 | UI/humo | BackendCollectors, BackendPerformers |
| [TrackCreateScreenTest](../../app/src/androidTest/java/co/edu/uniandes/vinilos/ui/TrackCreateScreenTest.kt) | 7 | UI/humo | BackendFirewall |

**Total declarado: 125 métodos instrumentados.** Los seis nuevos verifican el contador y NetworkFaults; no son seis nuevos recorridos E2E. Las unitarias JVM se cuentan por separado. Los 110 de la referencia anterior no incluían las nueve pruebas de navegación integradas posteriormente.

Los nombres de los métodos funcionales y sus correspondencias con los casos de `docs/06-pruebas.md` se conservan. Las pruebas de premios mantienen sus fixtures de Jhon. Algunos casos siguen utilizando datos de compatibilidad de la base efímera; no se borran esos escenarios para reducir el conteo.
