# AVIVA · Letras

Un proyecto de **AVIVA**. App para una tablet vieja (Lenovo Tab 3 7, Android 5.1, 1 GB) que la convierte en una pantalla de la casa:
muestra la **letra sincronizada** de lo que suena en Spotify, sobre un fondo vivo, con Hunter y Tango
paseando abajo. Cuando no suena nada: reloj, o marco de fotos.

## Qué hace

- **Letra al ritmo de la canción.** La línea que se canta se resalta y la pantalla avanza sola.
- **De dónde sale la música:**
  1. **Javbots** (la oficina de agentes en la PC): sabe exactamente qué suena en el Spotify de la PC y ya trae
     la letra oficial. La tablet encuentra la PC sola en la red.
  2. **API de Spotify** (respaldo, opcional): para cuando suena en otro aparato. Letra de [LRCLIB](https://lrclib.net).
- **Fondos** (doble toque para cambiar):
  - **Cielo según la hora:** amanecer, día con nubes, atardecer, noche con estrellas y luna.
  - **Artistas:** tus imágenes o GIF animados, con su crédito en la esquina.
  - **Carátula:** la portada del disco, borrosa.
- **Hunter y Tango** en la orilla de abajo:
  - Suena música: se ponen **audífonos**, pasean y al pararse **mueven la cabeza al ritmo** (con notitas) o se
    **sientan moviendo la cola**.
  - Se busca la letra: **corren**. Aparece la letra: **bailan** un momento.
  - **De día** sin música: olfatean, se rascan, se estiran, mordisquean un hueso, juegan, ladran, se sacuden,
    persiguen su cola, ruedan, miran al cielo… y a veces toman una siesta.
  - **De noche** sin música: se duermen en distintas posiciones (echados, enroscados, de lado, como rana) y a
    veces **sueñan**: mueven las patitas y sale una nube con un hueso, una pelota, una ardilla…
  - De noche llevan su lucecita.
- **Marco de fotos** cuando no suena nada.

## Poner tu arte y tus fotos

Conecta la tablet a la PC por USB, ábrela en el Explorador de archivos y copia a:

| Carpeta en la tablet             | Qué va                                                         |
| -------------------------------- | -------------------------------------------------------------- |
| `Pictures/Letras/`               | Fotos para el marco (sin música)                               |
| `Pictures/Letras/fondos/`        | Fondos de artistas: se usan a cualquier hora                    |
| `Pictures/Letras/fondos/dia/`    | Solo de día (7:00 a 18:30, más o menos)                        |
| `Pictures/Letras/fondos/noche/`  | Solo de noche                                                   |

- Sirven **JPG, PNG, WebP y GIF animado** (hasta 25 MB cada uno).
- **El crédito sale del nombre del archivo:** `Artista - Título.gif` → "Arte: Artista · Título".
  Sin guion, solo el artista.
- No hace falta reiniciar la app: los toma en el siguiente cambio de fondo.

Por cable con adb también se puede:
`adb push "Artista - Título.gif" /sdcard/Pictures/Letras/fondos/`

## Opciones (mantener presionado en la pantalla)

| Opción                         | Valores                                                   |
| ------------------------------ | --------------------------------------------------------- |
| Fondo                          | cielo · artistas · carátula (también con doble toque)     |
| Arte                           | completo (con relleno borroso) · llenar la pantalla       |
| Cambiar arte                   | con cada canción · cada 5, 15 o 60 min                    |
| Oscurecer detrás de la letra   | poco · medio · mucho                                      |
| Siguiente fondo de artista     |                                                           |
| Cerrar sesión de Spotify       | (si hay sesión)                                           |

## Configurar y compilar

`android/local.properties` (no se sube a git):

```properties
sdk.dir=C\:/Users/<tú>/AppData/Local/Android/Sdk
javbots.token=<token de ~/.pixel-agents/music.json>
# opcionales
javbots.host=
javbots.port=3101
spotify.clientId=
```

En la PC, Javbots necesita `"screenPort": 3101` en `~/.pixel-agents/music.json` (ver el README de música de Javbots).

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Sin AndroidX ni librerías de red a propósito: la tablet es API 22 y tiene 1 GB. El APK pesa ~1.5 MB.

## Créditos

- Hunter y Tango: los personajes de [Javbots](https://github.com/DeveloperJGV/Javbots), con poses nuevas hechas en
  [PixelLab](https://pixellab.ai). Los cuadros originales están en `arte/pixellab/` y `arte/armar_perros.py` arma
  las hojas de la app (Hunter se recolorea a gris atigrado).
- Letras: Spotify (vía Javbots) y [LRCLIB](https://lrclib.net).
- Fondos de artistas: de sus autores, con el crédito que se muestra en pantalla. No se incluyen en este repo.
