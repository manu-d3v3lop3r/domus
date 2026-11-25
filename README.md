# Domus

**Tus finanzas, en armonía.** Aplicación Android nativa, sin conexión, para gestionar finanzas personales y del hogar compartido entre varios perfiles y entre los móviles de la familia.

> Proyecto personal de estudio para practicar desarrollo Android moderno: arquitectura limpia, Jetpack Compose, persistencia cifrada, criptografía aplicada, comunicación entre dispositivos, accesibilidad y pruebas automatizadas.

- Package: `com.domus.app` · minSdk 26 · targetSdk/compileSdk 37
- Idioma: español · Moneda: EUR
- Kotlin, Jetpack Compose (Material 3), MVVM + Clean Architecture, Hilt, Room + SQLCipher, DataStore, Navigation Compose, Coroutines/Flow.

## Requisitos

- Android Studio con soporte para AGP 9.5 y JDK 17 o superior (se usa el JDK embebido).
- Gradle 9.6 (wrapper incluido).
- SDK de Android 37 instalado.

## Compilar y ejecutar

```bash
./gradlew :app:assembleDebug          # APK de depuración
./gradlew :app:installDebug           # instalar en el emulador/dispositivo conectado
./gradlew :app:testDebugUnitTest      # pruebas unitarias (JVM)
./gradlew :app:connectedDebugAndroidTest  # pruebas de integración (requiere emulador)
```

En Android Studio basta con abrir la carpeta del proyecto, sincronizar Gradle y ejecutar la configuración `app`.

## Funcionalidades

| Área | Detalle |
|---|---|
| Perfiles | Crear, editar (nombre/avatar), desactivar; selector de perfiles. |
| Autenticación | PIN de 6 dígitos (rechaza secuencias y repeticiones), bloqueo progresivo tras 5 fallos (30 s, 1 min, 2 min… máx. 1 h), código de recuperación de 16 caracteres mostrado una sola vez, cambio de PIN y regeneración del código. |
| Sesión | Solo en memoria; bloqueo automático por inactividad (1–30 min) también al volver de segundo plano; bloqueo manual y cambio de perfil. |
| Hogar | Hogar único (id = 1), miembros, balance doméstico, movimientos del periodo y actividad con trazabilidad (creado/editado/eliminado por). Cualquier miembro puede crear, editar y eliminar movimientos domésticos. |
| Movimientos | Ingresos y gastos personales o domésticos, importe, categoría, fecha y descripción; edición; borrado lógico; historial con filtros por periodo (mes, año, todo, rango), tipo, ámbito y categoría. |
| Categorías | Generales, personales y domésticas; ingreso/gasto; crear, editar, activar/desactivar; solo se pueden eliminar si nunca se han usado. |
| Dashboard | Balance personal y doméstico, ingresos/gastos, gastos por categoría, movimientos recientes, selector de periodo y accesos rápidos. |
| Ajustes | Tema sistema/claro/oscuro, confirmación de borrado, tiempo de bloqueo, seguridad y gestión de perfil. |

## Arquitectura

```
com.domus.app
├── AplicacionDomus, ActividadPrincipal
├── nucleo/              Utilidades transversales (sin dependencias de UI)
│   ├── comun/           ExcepcionDominio, mensajeUsuario(), ejecutarCapturando, @AmbitoAplicacion
│   ├── dinero/          AnalizadorImporte y FormateadorDinero (Long en céntimos)
│   ├── seguridad/       DerivadorSecretos (PBKDF2), PoliticaPin/PoliticaBloqueo, CodigosRecuperacion,
│   │                    GuardianBloqueo, GestorClaveBaseDatos (Keystore), CriptografiaHogar
│   └── sesion/          GestorSesion (sesión en memoria + autobloqueo)
├── dominio/
│   ├── modelo/          Modelos, Periodo, reglas de validación
│   ├── repositorio/     Interfaces de repositorio y EjecutorTransacciones (atomicidad)
│   ├── casouso/         Casos de uso (acceso, perfil y hogar, movimientos, categorías y resumen)
│   └── sincronizacion/  Modelos y reglas de fusión de la sincronización entre móviles
├── datos/
│   ├── local/           Room: BaseDatosDomus, entidades, DAOs, migraciones, categorías iniciales
│   ├── mapeo/           Conversión entidad ↔ dominio
│   ├── preferencias/    DataStore (preferencias globales)
│   ├── repositorio/     Implementaciones Room
│   └── sincronizacion/  CodificadorSinc, ProtocoloSincronizacion, RepositorioSincronizacionRoom,
│                        cercania/TransporteCercano (Nearby Connections)
├── inyeccion/           Módulos Hilt
├── funcionalidad/       Pantallas + ViewModels: acceso, inicio, movimientos, hogar, categorias,
│                        ajustes, sincronizacion
├── navegacion/          ViewModelApp (arranque/tema/sesión), Rutas y grafos de navegación
└── interfaz/            tema/ (TemaDomus, colores, tipografía) y componentes/ reutilizables
```

Flujo de datos: `Composable → ViewModel (StateFlow) → caso de uso → repositorio → DAO (Flow)`. Toda la lógica financiera y de permisos vive en `dominio`; la interfaz solo presenta.

### Idioma del código

Clases, funciones, variables, ficheros, paquetes, rutas de navegación y recursos están en español. Se mantienen en inglés, a propósito, los valores que ya están **guardados** en los móviles de los usuarios o que **viajan entre móviles**, porque cambiarlos rompería datos existentes o la compatibilidad entre versiones:

| Qué | Por qué no se traduce |
|---|---|
| Nombres de tablas y columnas de Room (`profiles`, `amountMinor`…) | Las propiedades están en español y se enlazan con `@ColumnInfo(name = …)`. El esquema exportado es idéntico al anterior. |
| Valores guardados de los enumerados (`INCOME`, `EXPENSE`, `HOUSEHOLD`, `SYSTEM`…) | Cada enumerado tiene un `codigo` y Room usa `Convertidores` para traducirlo. |
| Claves de DataStore (`theme_mode`, `auto_lock_minutes`, `confirm_deletions`) y fichero `domus_preferences` | Conservan los ajustes ya guardados. |
| Alias del Keystore, fichero `domus_db.key`, base de datos `domus.db` y algoritmo `PBKDF2WithHmacSHA256` | Permiten descifrar la base de datos y verificar los PIN existentes. |
| Protocolo de sincronización (cabecera `DMS1`, códigos de mensaje, etiquetas `domus-tag`/`domus-proof-*`/`domus-session`, `domus-sync-v1`, `SERVICE_ID`, prefijos `dpair:`/`djoin:`/`dsync:`) | Móviles con versiones distintas deben seguir entendiéndose. Una prueba fija el formato binario. |
| Claves de icono de categoría (`food`, `work`…) e identificadores `default:<icono>` | Se guardan en la base de datos y se sincronizan. |
| Componente `.MainActivity` | Es un `activity-alias` que abre `ActividadPrincipal`, para no romper los accesos directos ya creados en el lanzador. |
| Iconos `ic_launcher*`, parámetro `modifier` de Compose y APIs de Android/bibliotecas | Convenciones del sistema y de las herramientas. |

## Reglas financieras

- Los importes se guardan en céntimos (`Long`), siempre positivos; el tipo (ingreso/gasto) determina el signo.
- Nunca se usa `Float`/`Double`: el parseo se hace con aritmética entera y el formateo con `BigDecimal`.
- Balance = ingresos − gastos (`Math.subtractExact`, con detección de desbordamiento).
- Balance personal: solo movimientos `PERSONAL` del perfil. Balance doméstico: solo movimientos `HOUSEHOLD` (un único registro por movimiento, contabilizado una vez).
- Los movimientos con `deletedAt` no computan en ningún balance.
- Las operaciones de escritura se ejecutan dentro de transacciones de Room (`EjecutorTransacciones`).

## Seguridad

- **PIN**: PBKDF2-HMAC-SHA256 (120 000 iteraciones, salt aleatorio de 16 bytes por perfil, comparación en tiempo constante). El número de iteraciones se guarda en `algorithm`, lo que permite endurecerlo sin invalidar credenciales. PBKDF2 se implementa sobre `Mac("HmacSHA256")` (Conscrypt, nativo) porque `SecretKeyFactory("PBKDF2WithHmacSHA256")` en Android usa Bouncy Castle en Java puro y tarda minutos con este número de iteraciones; una prueba unitaria verifica que el resultado coincide con la implementación de referencia del JDK.
- **Código de recuperación**: 16 caracteres aleatorios (~80 bits de entropía); se almacena solo su derivación PBKDF2 (10 000 iteraciones) en `recoveryVerifier`, comparte contador de intentos con el PIN y se invalida tras usarse o regenerarse.
- **Cifrado de la base de datos**: SQLCipher (`net.zetetic:sqlcipher-android`) integrado con Room mediante `SupportOpenHelperFactory`. La clave son 32 bytes aleatorios (no derivados del PIN), envueltos con AES-256-GCM por una clave no exportable del **Android Keystore** y guardados en `noBackupFilesDir`.
- **Errores de descifrado**: la base de datos se abre al arrancar; si la clave falta o no puede descifrarse, se muestra una pantalla que permite restablecer los datos (doble confirmación).
- Copias de seguridad desactivadas (`allowBackup=false` y reglas de extracción vacías): la clave del Keystore no viaja a otros dispositivos.
- `FLAG_SECURE`: sin capturas, grabaciones de pantalla ni miniatura en Recientes.
- `filterTouchesWhenObscured`: se ignoran los toques cuando otra ventana se superpone (tapjacking).
- Campos de PIN y código de recuperación con teclado de tipo contraseña y sin autocorrección.
- Sin acceso a Internet: la app no declara `INTERNET`. Los únicos permisos peligrosos son los de Nearby (ver *Sincronización*), pedidos solo al sincronizar. El único componente exportado es la actividad de inicio.

### Dependencias

| Origen | Librerías |
|---|---|
| Google / AndroidX | Compose, Material 3, Activity, Lifecycle, Navigation, Room, SQLite, DataStore, Hilt/Dagger y sus transitivas (`javax.inject`, `jspecify`, `guava:listenablefuture`, `jsr305`) |
| JetBrains (lenguaje oficial de Android) | Kotlin stdlib, kotlinx-coroutines, kotlinx-serialization |
| Transitiva de AndroidX DataStore | Okio (Square) |
| Terceros directa | SQLCipher (`net.zetetic:sqlcipher-android`, BSD): cifrado completo de la base de datos; no existe alternativa de Google para cifrar Room |

Las pruebas usan además JUnit 4 y AndroidX Test.

- **Bloqueo resistente a cambios de hora**: además del bloqueo persistido (reloj de pared, limitado a 1 h por si la hora se retrasa), `GuardianBloqueo` registra el mismo bloqueo con el reloj monótono `SystemClock.elapsedRealtime()`, que no cambia al modificar la hora del sistema.

### Limitaciones conocidas

- El bloqueo monótono vive en memoria: para saltarse un bloqueo habría que cerrar el proceso *y* adelantar la hora del sistema. El riesgo es limitado porque la base de datos está cifrada y no es accesible fuera de la app.
- Los perfiles comparten una única clave de base de datos (protegida por el Keystore del dispositivo); la separación entre perfiles es lógica, aplicada por la capa de dominio tras autenticar el PIN.
- Si el Keystore se invalida (por ejemplo, tras restablecer el dispositivo), los datos cifrados no son recuperables por diseño.
- La tipografía usa la familia `sans-serif` del sistema, que en Android es Roboto; no se empaqueta la fuente para mantener el funcionamiento sin conexión y el tamaño reducido.

## Sincronización del hogar entre móviles

Varias personas pueden compartir el hogar desde sus propios móviles. Los móviles se sincronizan **directamente cuando están cerca** (Bluetooth y Wi-Fi mediante *Nearby Connections* de Google Play Services), **sin Internet ni servidores**. La app no declara el permiso `INTERNET`.

**Qué se comparte:** nombre del hogar, miembros (nombre y avatar), categorías del hogar y las generales que usan sus movimientos, y los movimientos domésticos (incluidos los borrados). **Nunca** se comparten movimientos ni categorías personales, ni PIN, ni códigos de recuperación.

**Uso:** Hogar › *Vincular otro móvil* en un móvil y *Unirme a un hogar de otro móvil* en el otro → ambas personas comparan el código que muestran los dos móviles y confirman. Después, *Sincronizar ahora* en ambos móviles cuando estén juntos.

### Modelo de seguridad

| Amenaza | Defensa |
|---|---|
| Alguien cercano se hace pasar por el otro móvil al vincular (*man in the middle*) | Las dos personas comparan el código de autenticación de Nearby antes de aceptar; la clave del hogar solo se envía tras esa confirmación. |
| Un móvil ajeno intenta sincronizar | Autenticación mutua por desafío con HMAC-SHA256 sobre la clave del hogar (que nunca viaja). Sin la clave no se recibe ni se envía ningún dato. |
| Lectura o manipulación de los datos en el aire | Además del cifrado de Nearby, cada envío va cifrado con AES-256-GCM con una clave de sesión nueva (derivada de nonces de ambos móviles); los datos asociados fijan dirección y orden. |
| Reenvío de mensajes de una sesión anterior | La clave de sesión cambia en cada sincronización. |
| Datos maliciosos de un miembro | Todo lo recibido se valida (importes, fechas, categorías, autores, tamaños) y **nunca** puede sobrescribir datos personales ni perfiles del móvil receptor. Codificación binaria estricta con límites. |
| Seguimiento de la presencia del hogar | El nombre anunciado es una etiqueta derivada de la clave que cambia cada hora; no contiene nombres de personas ni del hogar. |
| Miembro expulsado | Al retirar a un miembro de otro móvil se genera una clave nueva; su móvil deja de poder sincronizar. |

**Conflictos:** gana la modificación más reciente de cada elemento (con desempate determinista) y los borrados son definitivos.

**Limitaciones:** tras retirar a un miembro de otro móvil, el resto de móviles debe volver a vincularse (reciben la nueva clave en persona). Un miembro retirado conserva los datos que ya tenía. La resolución por «última modificación» depende de que los relojes de los móviles sean razonablemente correctos. Requiere Google Play Services.

**Permisos** (solo se piden al sincronizar, con explicación previa): Bluetooth (anunciar, conectar y buscar), dispositivos Wi-Fi cercanos y red local (Android 17+). En Android 12L e inferiores el sistema los agrupa como «ubicación»; Domus no consulta la ubicación.

## Diseño y accesibilidad

- Icono adaptativo propio (casa con hoja, con versión monocroma para iconos temáticos) y pantalla de inicio nativa con `androidx.core:core-splashscreen`.
- Paleta de marca para rellenos, iconos y gráficos; para **texto** se usan variantes que cumplen WCAG 2.2 AA (≥ 4,5:1): verde `#117A60` y rojo `#B23F47`. Los colores de marca `#16866A` y `#C94F55` dan 4,27:1 y 4,18:1 sobre el fondo, insuficiente para texto normal.
- Encabezados marcados para TalkBack, errores y estados anunciados con *live regions*, importes descritos verbalmente («Gasto de 42,50 €»), código de recuperación leído carácter a carácter.
- Áreas táctiles ≥ 48 dp y botones con altura mínima (no fija) para respetar el tamaño de fuente del sistema.
- Cifras tabulares en importes, respuesta háptica en el teclado del PIN y sacudida al fallar.

## Firma de la versión release

Copia `keystore.properties.example` como `keystore.properties` (ignorado por Git) con los datos de tu almacén de claves. Sin ese fichero, `assembleRelease` genera un APK sin firmar.

## Publicar el código

El repositorio no contiene datos personales, rutas locales ni credenciales. `.gitignore` excluye:

- `local.properties` (ruta del SDK de cada máquina) y la carpeta `.idea/` (ajustes y rutas de Android Studio).
- `keystore.properties`, `*.jks`, `*.keystore` y otros secretos de firma.
- Carpetas de compilación (`build/`, `.gradle/`, `.kotlin/`) y artefactos (`*.apk`, `*.aab`).

`.gitattributes` fija los finales de línea para que `gradlew` funcione en Linux y macOS. En la versión release, R8 elimina todas las llamadas a `android.util.Log`.

## Base de datos y migraciones

- Esquema exportado en `app/schemas/` (plugin `androidx.room`). Versión actual: **2**.
- **v1 → v2**: perfiles de otros móviles (`isLocal`), identidad y clave del hogar compartido (`syncId`, `syncKey`, `lastSyncAt`), marcas `updatedAt` en miembros y categorías, e identificadores fijos (`default:<icono>`) para las categorías por defecto, con reasignación de sus movimientos.
- Para un cambio de esquema: incrementar `BaseDatosDomus.VERSION`, añadir `Migration(n, n+1)` en `MigracionesDomus.TODAS` y ampliar `PruebasMigracionesDomus`. No se usa migración destructiva.

## Pruebas

- `app/src/test`: análisis y formato de importes, PBKDF2, políticas de PIN y bloqueo, códigos de recuperación, periodos y balances, casos de uso de acceso y finanzas con repositorios en memoria, y criptografía, codificación y protocolo de la sincronización.
- `app/src/androidTest`: integración con Room (balances, filtros, borrado lógico, trazabilidad), cifrado real con SQLCipher + Keystore, validación de migraciones y sincronización entre dos bases de datos reales.
