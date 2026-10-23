# Hell's Gate Android

Port Android ARM64 de Dante's Inferno (Xbox 360), recompilado estáticamente
con el SDK [ReXGlue](https://github.com/rexglue/rexglue-sdk). El juego se
ejecuta como código nativo ARM64; no se utiliza un emulador ni JIT.

El repositorio no contiene archivos del juego. Debes proporcionar tu propia
copia legal de `default.xex` y los archivos `bigfile*.viv` dentro de `game/`.

## Requisitos

- Android SDK 35
- NDK 28 o compatible
- CMake 3.22+
- JDK 17+
- Git, Ninja y Clang para preparar las dependencias

Las dependencias clonadas (`thirdparty/rexglue-sdk` y
`thirdparty/diligent-core`) no se versionan. Para prepararlas:

```bash
./setup.sh
```

## Generar el código del juego

Con los archivos del juego en `game/`, ejecuta el generador de ReXGlue según
la documentación del SDK. Los archivos generados deben quedar en
`generated/default/`; esa carpeta está ignorada porque se regenera localmente.

## Compilar e instalar

Desde `android/`:

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Para una compilación de depuración:

```bash
./gradlew :app:assembleDebug
```

El launcher Android permite configurar VSync, compilación asíncrona de
shaders, núcleos rápidos, filtrado anisotrópico hasta 16x, buffer de audio y
hilos de creación de pipelines. Los cambios se aplican al iniciar de nuevo el
juego.

## Documentación

- [`android/README.md`](android/README.md): flujo Android y arquitectura.
- [`docs/NATIVE_RENDERER_DESIGN.md`](docs/NATIVE_RENDERER_DESIGN.md): diseño
  del renderer nativo.
- [`docs/NATIVE_D3D_MAP.md`](docs/NATIVE_D3D_MAP.md): mapa de las entradas
  gráficas del juego.
- [`docs/frame_rate.md`](docs/frame_rate.md): sincronización y frecuencia de
  imágenes.
- [`docs/vp6_fmv_corruption_fix.md`](docs/vp6_fmv_corruption_fix.md): vídeo
  VP6.
- [`docs/rexglue_notes.md`](docs/rexglue_notes.md): notas del SDK y parches.

## Licencia y archivos del juego

Hell's Gate es un proyecto no oficial y no está afiliado a Electronic Arts,
Visceral Games ni Microsoft. Dante's Inferno es una marca de sus respectivos
propietarios. El repositorio no incluye código ni assets propietarios del
juego; usa únicamente una copia obtenida legalmente.
