# BacheWatch

Aplicación móvil para reportar baches con ubicación, fotografía y consulta en mapa.

## Funcionamiento general

La app permite que un usuario entre con su nombre, consulte los reportes existentes, registre un nuevo bache, tome evidencia con la cámara y guarde la ubicación mediante GPS.

## Estructura principal

- `MainActivity.kt`: punto de entrada de la aplicación.
- `BacheViewModel.kt`: controla estados de pantalla, formularios, filtros, votos, sesión del usuario y envío de reportes.
- `data/BacheReport.kt`: modelo de datos del reporte.
- `data/BacheDao.kt`: consultas de Room para guardar, leer, actualizar y eliminar reportes.
- `data/BacheDatabase.kt`: base de datos local.
- `data/BacheRepository.kt`: puente entre Room y el ViewModel.
- `data/FirebaseSyncService.kt`: sincronización con Firebase/Firestore y conversión de imágenes a Base64.
- `ui/DashboardScreen.kt`: interfaz principal de la app.
- `ui/RealStreetMap.kt`: mapa con OSMDroid, ubicación actual y marcadores.

## Cambios de esta versión

- Se quitaron los baches iniciales de prueba.
- La base local inicia vacía y solo muestra reportes reales del usuario o de Firebase.
- Se quitaron imágenes simuladas para reportar; ahora la evidencia se toma con la cámara.
- Se agregaron comentarios superficiales en español para explicar las partes principales del código.
- Se subió la versión de Room a 3 para limpiar datos locales viejos cuando se actualice desde una versión anterior.

## Nota

Si ya se había instalado una versión anterior con baches de prueba, al instalar esta versión la base local se reinicia por el cambio de versión de Room. Los reportes sincronizados en Firebase pueden volver a aparecer si siguen guardados en la colección remota.
