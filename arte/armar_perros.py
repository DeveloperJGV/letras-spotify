"""Arma las hojas de Hunter y Tango para la app desde los cuadros de PixelLab (arte/pixellab/<perro>/<animación>/N.png).

Cada hoja (android/app/src/main/res/drawable-nodpi/perros_<perro>.png) tiene una fila por animación, en el orden
de FILAS, y celdas del mismo tamaño: el recorte común de todos los cuadros del perro, así los pies quedan a la misma
altura en todas las animaciones. res/raw/perros.json dice qué fila es cada animación y cuántos cuadros tiene
(DogsView lo lee). Las animaciones que aún no existen en arte/pixellab/<perro>/ se saltan.

Ajustes a mano:
  - Hunter: PixelLab lo hace café liso; Hunter es atigrado gris. El café pasa a gris cálido con rayitas
    (misma regla que en Javbots), sin tocar el naranja de los audífonos (más saturado que el pelo).
  - Dormir panza arriba se intentó dos veces y no salió (no se voltean): no se usa.
  - Caminar sin audífonos: Tango sin los cuadros 4 y 5 (mancha amarilla); Hunter solo los pasos 0 y 1
    (los demás se agachan de más).

También arma la hoja de los sueños (arte/pixellab/suenos/).

Uso: python arte/armar_perros.py [--preview salida.png]
"""
import colorsys
import json
import os
import sys

from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
ORIGEN = os.path.join(AQUI, 'pixellab')
DESTINO = os.path.join(AQUI, '..', 'android', 'app', 'src', 'main', 'res', 'drawable-nodpi')

FILAS = [
    # base
    'walk', 'aud_walk', 'run', 'sleep', 'idle', 'headbob', 'sit', 'party',
    # noche
    'sleep_curl', 'sleep_side', 'sleep_sploot', 'dream',
    # día
    'sniff', 'scratch', 'stretch', 'chew', 'play', 'bark', 'shake', 'tail_chase', 'roll', 'look_up', 'yawn',
    # con música
    'howl', 'listen_lie', 'wiggle',
]
INDICE = os.path.join(AQUI, '..', 'android', 'app', 'src', 'main', 'res', 'raw', 'perros.json')
CUADROS = {
    ('tango', 'walk'): [0, 1, 2, 3],
    ('hunter', 'walk'): [0, 1, 0, 1],
}

GRIS_TONO = 28 / 360
GRIS_SAT = 0.12
RAYA_OSCURECE = 0.62


def es_pelo_cafe(r, g, b):
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    # El naranja de los audífonos tiene s ~0.69; el pelo café, 0.40-0.45.
    return 0.25 < s < 0.6 and 0.15 < l < 0.62


def atigrado(im, origen):
    out = im.copy()
    px = out.load()
    ox, oy = origen
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0 or not es_pelo_cafe(r, g, b):
                continue
            _, l, _ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if ((x - ox) + 2 * (y - oy)) % 5 == 0:
                l *= RAYA_OSCURECE
            nr, ng, nb = colorsys.hls_to_rgb(GRIS_TONO, l, GRIS_SAT)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def cuadros(perro, anim):
    carpeta = os.path.join(ORIGEN, perro, anim)
    total = len([f for f in os.listdir(carpeta) if f.endswith('.png')])
    indices = CUADROS.get((perro, anim), range(total))
    ims = [Image.open(os.path.join(carpeta, f'{i}.png')).convert('RGBA') for i in indices]
    if perro == 'hunter':
        ims = [atigrado(im, (0, 0)) for im in ims]
    return ims


def disponibles():
    """Animaciones que tienen los dos perros (la hoja de ambos usa las mismas filas)."""
    return [a for a in FILAS if all(os.path.isdir(os.path.join(ORIGEN, p, a)) for p in ('hunter', 'tango'))]


def armar(perro, anims):
    filas = [cuadros(perro, a) for a in anims]
    columnas = max(len(f) for f in filas)
    # Recorte común: la unión de lo dibujado en todos los cuadros.
    caja = None
    for fila in filas:
        for im in fila:
            b = im.getbbox()
            if b:
                caja = b if caja is None else (min(caja[0], b[0]), min(caja[1], b[1]), max(caja[2], b[2]), max(caja[3], b[3]))
    ancho, alto = caja[2] - caja[0], caja[3] - caja[1]
    hoja = Image.new('RGBA', (ancho * columnas, alto * len(anims)))
    for f, fila in enumerate(filas):
        for c, im in enumerate(fila):
            hoja.alpha_composite(im.crop(caja), (c * ancho, f * alto))
    salida = os.path.join(DESTINO, f'perros_{perro}.png')
    hoja.save(salida)
    print(perro, 'celda', ancho, 'x', alto, '->', os.path.relpath(salida, AQUI))
    return hoja, {'celda': [ancho, alto], 'cuadros': [len(f) for f in filas]}


SUENOS = ['hueso', 'pelota', 'salchicha', 'ardilla', 'gato', 'filete', 'corazon', 'croqueta']


def armar_suenos():
    """Los dibujitos de la nube de sueños, en una fila de cuadros iguales (res/drawable-nodpi/suenos.png)."""
    ims = [Image.open(os.path.join(ORIGEN, 'suenos', f'{n}.png')).convert('RGBA') for n in SUENOS]
    lado = max(max(i.size) for i in ims)
    hoja = Image.new('RGBA', (lado * len(ims), lado))
    for k, im in enumerate(ims):
        hoja.alpha_composite(im, (k * lado + (lado - im.width) // 2, (lado - im.height) // 2))
    hoja.save(os.path.join(DESTINO, 'suenos.png'))
    print(len(ims), 'sueños ->', 'suenos.png')


if __name__ == '__main__':
    armar_suenos()
    anims = disponibles()
    resultado = {p: armar(p, anims) for p in ('hunter', 'tango')}
    hojas = [h for h, _ in resultado.values()]
    os.makedirs(os.path.dirname(INDICE), exist_ok=True)
    with open(INDICE, 'w', encoding='utf-8') as f:
        json.dump({'filas': anims, **{p: datos for p, (_, datos) in resultado.items()}}, f, indent=1)
    print(len(anims), 'animaciones ->', os.path.relpath(INDICE, AQUI))
    if '--preview' in sys.argv:
        ruta = sys.argv[sys.argv.index('--preview') + 1]
        w = max(h.width for h in hojas)
        prev = Image.new('RGBA', (w, sum(h.height for h in hojas)), (70, 80, 110, 255))
        y = 0
        for h in hojas:
            prev.alpha_composite(h, (0, y))
            y += h.height
        prev.resize((prev.width * 3, prev.height * 3), Image.NEAREST).save(ruta)
        print('vista previa', ruta)
