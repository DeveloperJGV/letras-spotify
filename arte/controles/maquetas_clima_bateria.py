"""Maquetas (imagenes, no codigo de la app) del clima y la bateria en pixel art, de la familia del volumen.

Tres opciones de clima y tres de bateria, a escala real (1 px de arte = 1 px de pantalla, densidad 1.0), con lo de
hoy al lado (recortado de las capturas reales) y la opcion recomendada montada sobre la pantalla del reloj.

Uso:  python arte/controles/maquetas_clima_bateria.py
Sale en docs/capturas/controles-pixel/: maqueta-clima, maqueta-bateria, maqueta-reloj-clima-bateria (+ -x2).

Se toma de maquetas.py (sin cambiarlo): la paleta del cuarto, los perros (perros_*.png + perros.json), la capsula
del volumen, los filtros de noche y las herramientas de dibujo. Todo lo demas se dibuja aqui a mano.
"""

import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas as M  # noqa: E402
from maquetas import P, h, Lienzo, nuevo, perro, espejo, noche, noche_obj, mezcla, capsula, texto, fuente  # noqa: E402

SALIDA = M.SALIDA
CAP_DIA = Image.open(os.path.join(SALIDA, "despues-charola-dia-escogido.png")).convert("RGBA")
CAP_IMG = Image.open(os.path.join(SALIDA, "despues-charola-dia.png")).convert("RGBA")
CAP_NOCHE = Image.open(os.path.join(SALIDA, "despues-charola-noche.png")).convert("RGBA")

F_TXT = fuente("tahomabd.ttf", 11)   # texto del sistema chico, sin antialias (en la app: sans-serif-medium 12sp)
F_TXT_N = fuente("tahoma.ttf", 11)
BLANCO = h("ffffff")
SOMBRA_TXT = (20, 22, 34, 255)
CONTORNO = P["negro"]

# ---------------------------------------------------------------------------------------------------------------
# Numeros en pixel
# ---------------------------------------------------------------------------------------------------------------

# Numeros grandes dibujados a mano: GRANDE de 10 x 16 (trazo de 3 px) para la temperatura y MEDIANO de 7 x 11
# (trazo de 2 px) para el porcentaje. Se pintan con contorno de 1 px, la ultima cuarta parte en tono de sombra.
GRANDE = {
    "0": ["..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX",
          "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "1": ["...XXXX...", "..XXXXX...", ".XXXXXX...", ".XX.XXX...", "....XXX...", "....XXX...", "....XXX...", "....XXX...",
          "....XXX...", "....XXX...", "....XXX...", "....XXX...", "....XXX...", "....XXX...", ".XXXXXXXX.", ".XXXXXXXX."],
    "2": ["..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXX....XXX", ".......XXX", "......XXXX", ".....XXXX.", "....XXXX..",
          "...XXXX...", "..XXXX....", ".XXXX.....", "XXXX......", "XXX.......", "XXX.......", "XXXXXXXXXX", "XXXXXXXXXX"],
    "3": ["..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXX....XXX", ".......XXX", "......XXXX", "...XXXXXX.", "...XXXXXX.",
          "......XXXX", ".......XXX", ".......XXX", ".......XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "4": [".....XXXX.", "....XXXXX.", "...XXXXXX.", "..XXX.XXX.", ".XXX..XXX.", "XXX...XXX.", "XXX...XXX.", "XXX...XXX.",
          "XXXXXXXXXX", "XXXXXXXXXX", "......XXX.", "......XXX.", "......XXX.", "......XXX.", "......XXX.", "......XXX."],
    "5": ["XXXXXXXXXX", "XXXXXXXXXX", "XXX.......", "XXX.......", "XXX.......", "XXXXXXXX..", "XXXXXXXXX.", "......XXXX",
          ".......XXX", ".......XXX", ".......XXX", ".......XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "6": ["..XXXXXX..", ".XXXXXXXX.", "XXXX...XX.", "XXX.......", "XXX.......", "XXXXXXXX..", "XXXXXXXXX.", "XXXX..XXXX",
          "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "7": ["XXXXXXXXXX", "XXXXXXXXXX", ".......XXX", ".......XXX", "......XXXX", "......XXX.", ".....XXXX.", ".....XXX..",
          "....XXXX..", "....XXX...", "....XXX...", "...XXXX...", "...XXX....", "...XXX....", "...XXX....", "...XXX...."],
    "8": ["..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", ".XXXXXXXX.",
          "XXXX..XXXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "9": ["..XXXXXX..", ".XXXXXXXX.", "XXXX..XXXX", "XXX....XXX", "XXX....XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXXX",
          "..XXXXXXXX", ".......XXX", ".......XXX", ".......XXX", "XXX....XXX", "XXXX..XXXX", ".XXXXXXXX.", "..XXXXXX.."],
    "-": ["......", "......", "......", "......", "......", "......", "......", "XXXXXX", "XXXXXX", "......", "......",
          "......", "......", "......", "......", "......"],
    "°": [".XXXX.", "XXXXXX", "XX..XX", "XXXXXX", ".XXXX."],
    "%": [".XX....XXX", "XXXX...XXX", "XXXX..XXX.", ".XX...XXX.", ".....XXX..", ".....XXX..", "....XXX...", "....XXX...",
          "...XXX....", "...XXX....", "..XXX.....", "..XXX.....", ".XXX...XX.", ".XXX..XXXX", "XXX...XXXX", "XXX....XX."],
}
MEDIANO = {
    "0": [".XXXXX.", "XXXXXXX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "1": ["..XXX..", ".XXXX..", "XX.XX..", "...XX..", "...XX..", "...XX..", "...XX..", "...XX..", "...XX..", "XXXXXXX", "XXXXXXX"],
    "2": [".XXXXX.", "XXXXXXX", "XX...XX", ".....XX", "....XXX", "..XXXX.", ".XXX...", "XXX....", "XX.....", "XXXXXXX", "XXXXXXX"],
    "3": [".XXXXX.", "XXXXXXX", "XX...XX", ".....XX", "..XXXX.", "..XXXX.", ".....XX", ".....XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "4": ["....XX.", "...XXX.", "..XXXX.", ".XX.XX.", "XX..XX.", "XX..XX.", "XXXXXXX", "XXXXXXX", "....XX.", "....XX.", "....XX."],
    "5": ["XXXXXXX", "XXXXXXX", "XX.....", "XX.....", "XXXXXX.", "XXXXXXX", ".....XX", ".....XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "6": [".XXXXX.", "XXXXXXX", "XX...XX", "XX.....", "XXXXXX.", "XXXXXXX", "XX...XX", "XX...XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "7": ["XXXXXXX", "XXXXXXX", ".....XX", "....XXX", "....XX.", "...XXX.", "...XX..", "..XXX..", "..XX...", "..XX...", "..XX..."],
    "8": [".XXXXX.", "XXXXXXX", "XX...XX", "XX...XX", "XXXXXXX", ".XXXXX.", "XX...XX", "XX...XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "9": [".XXXXX.", "XXXXXXX", "XX...XX", "XX...XX", "XX...XX", "XXXXXXX", ".XXXXXX", ".....XX", "XX...XX", "XXXXXXX", ".XXXXX."],
    "%": ["XX....X", "XX...XX", "....XX.", "....XX.", "...XX..", "...XX..", "..XX...", "..XX...", ".XX....", "XX...XX", "X....XX"],
    "°": [".XXX.", "XX.XX", "XX.XX", ".XXX."],
}


def al_doble(fuente):
    """La fuente MEDIANO al doble (14 x 22, trazo de 4 px) para la temperatura: se lee desde lejos como el numero
    de hoy (46sp). El '°' se queda del tamano de GRANDE."""
    out = {}
    for ch, filas in fuente.items():
        if ch == "°":
            continue
        out[ch] = [f.replace("X", "XX").replace(".", "..") for f in filas for _ in (0, 1)]
    out["°"] = [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX."]
    return out


# 5 x 7 para lo chico (max/min, lluvia, porcentaje en la etiqueta)
CHICO = {
    "0": [".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."],
    "1": ["..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."],
    "2": [".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"],
    "3": ["XXXXX", "...X.", "..X..", "...X.", "....X", "X...X", ".XXX."],
    "4": ["...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."],
    "5": ["XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."],
    "6": ["..XX.", ".X...", "X....", "XXXX.", "X...X", "X...X", ".XXX."],
    "7": ["XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."],
    "8": [".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."],
    "9": [".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XX.."],
    "%": ["XX...", "XX..X", "...X.", "..X..", ".X...", "X..XX", "...XX"],
    "°": [".X.", "X.X", ".X.", "...", "...", "...", "..."],
    "/": ["....X", "...X.", "...X.", "..X..", ".X...", ".X...", "X...."],
    " ": ["..", "..", "..", "..", "..", "..", ".."],
}
XL = al_doble(MEDIANO)
FLECHA_ARRIBA = ["..X..", ".XXX.", "XXXXX"]
FLECHA_ABAJO = FLECHA_ARRIBA[::-1]
GOTA = ["..X..", "..X..", ".XXX.", "XXXXX", "XXXXX", "XXXXX", ".XXX."]


def mascara_mapa(filas):
    return {(i, j) for j, f in enumerate(filas) for i, c in enumerate(f) if c == "X"}, len(filas[0]), len(filas)


def pintar_mascara(L, ox, oy, pts, c, sombra=None, alto=0, contorno=CONTORNO, luz=None):
    """Contorno de 1 px alrededor, relleno, y las ultimas filas en tono de sombra (y la primera en luz)."""
    if contorno is not None:
        for (x, y) in pts:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1, 2):
                    if (x + dx, y + dy) not in pts:
                        L.p(ox + x + dx, oy + y + dy, contorno)
    for (x, y) in pts:
        col = c
        if sombra is not None and y >= alto - max(2, alto // 4):
            col = sombra
        if luz is not None and (x, y - 1) not in pts:
            col = luz
        L.p(ox + x, oy + y, col)


def ancho_numero(s, fuente):
    return sum(len(fuente[ch][0]) + 2 for ch in s) - 2


def numero(L, x, y, s, fuente=GRANDE, c=None, sombra=None, luz=None, contorno=CONTORNO):
    """Numero en pixel con contorno; el '°' queda arriba, chico."""
    c = c or P["crema"]
    sombra = sombra or P["rosa"]
    alto = len(fuente["0"])
    for ch in s:
        pts, w, hh = mascara_mapa(fuente[ch])
        pintar_mascara(L, x, y, pts, c, sombra if ch != "°" else None, alto, contorno, luz)
        x += w + 2
    return x


def chico(L, x, y, s, c, contorno=SOMBRA_TXT):
    """Texto 5 x 7 con contorno; devuelve la x final."""
    for ch in s:
        pts, w, _ = mascara_mapa(CHICO[ch])
        pintar_mascara(L, x, y, pts, c, contorno=contorno)
        x += w + 1
    return x


def icono(L, x, y, filas, c, contorno=SOMBRA_TXT):
    pts, w, _ = mascara_mapa(filas)
    pintar_mascara(L, x, y, pts, c, contorno=contorno)
    return x + w + 2


def palabra(im, xy, s, c=(245, 245, 250, 255), f=F_TXT):
    """Texto del sistema, sin antialias, con sombra de 1 px."""
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    d.text((xy[0] + 1, xy[1] + 1), s, font=f, fill=SOMBRA_TXT)
    d.text(xy, s, font=f, fill=c)


def detalles(L, x, y, maxc, minc, lluvia, n=0.0, dos_lineas=False):
    """▲19° ▼12°  gota 90%  (todo 5 x 7); con dos_lineas la lluvia va abajo."""
    x0 = x
    x = icono(L, x, y + 2, FLECHA_ARRIBA, P["durazno"])
    x = chico(L, x, y, f"{maxc}°", P["crema"]) + 4
    x = icono(L, x, y + 2, FLECHA_ABAJO, P["azulgris"] if n < 0.5 else h("8fa8d8"))
    x = chico(L, x, y, f"{minc}°", P["crema"]) + 6
    if lluvia and dos_lineas:
        x, y = x0, y + 12
    if lluvia:
        x = icono(L, x, y, GOTA, h("6cc3f0"))
        x = chico(L, x, y, f"{lluvia}%", P["crema"])
    return x


# ---------------------------------------------------------------------------------------------------------------
# Formas con sombra (3 tonos + contorno)
# ---------------------------------------------------------------------------------------------------------------

def forma(L, pts, fill, sombra, luz, contorno, ox=0, oy=0):
    for (x, y) in pts:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in pts:
                L.p(ox + x + dx, oy + y + dy, contorno)
    for (x, y) in pts:
        if (x, y - 1) not in pts or (x - 1, y) not in pts and (x, y - 2) not in pts:
            col = luz
        elif (x, y + 2) not in pts or (x + 2, y) not in pts and (x, y + 3) not in pts:
            col = sombra
        else:
            col = fill
        L.p(ox + x, oy + y, col)


def elipse_pts(cx, cy, a, b):
    return {(x, y) for y in range(int(cy - b - 1), int(cy + b + 2)) for x in range(int(cx - a - 1), int(cx + a + 2))
            if ((x - cx) / (a + 0.5)) ** 2 + ((y - cy) / (b + 0.5)) ** 2 <= 1.0}


def nube_pts(x, y, w):
    """Nube de 'w' px de ancho con la base plana; (x, y) = esquina de arriba a la izquierda."""
    hh = max(6, w // 3)
    pts = set()
    pts |= elipse_pts(x + w * 0.30, y + hh * 0.62, w * 0.22, hh * 0.38)
    pts |= elipse_pts(x + w * 0.55, y + hh * 0.45, w * 0.24, hh * 0.45)
    pts |= elipse_pts(x + w * 0.78, y + hh * 0.66, w * 0.18, hh * 0.32)
    base = y + hh - 1
    pts = {(a, b) for (a, b) in pts if b <= base}
    for xx in range(int(x + w * 0.12), int(x + w * 0.92)):
        pts.add((xx, base))
        pts.add((xx, base - 1))
    return pts


NUBE_CLARA = dict(fill=P["humo"], sombra=P["niebla"], luz=h("ffffff"), contorno=P["perla"])
NUBE_GRIS = dict(fill=h("a9b1bf"), sombra=h("858ea0"), luz=h("c8cfd9"), contorno=h("5f677a"))
NUBE_OSCURA = dict(fill=h("5a6078"), sombra=h("43475c"), luz=h("737a94"), contorno=h("2b2d3d"))
NUBE_NOCHE = dict(fill=h("3a4268"), sombra=h("2c3254"), luz=h("4c557f"), contorno=h("1d2140"))


def nube(L, x, y, w, pal):
    forma(L, nube_pts(x, y, w), pal["fill"], pal["sombra"], pal["luz"], pal["contorno"])


def sol(L, cx, cy, r, rayos=True, grande=False):
    if rayos:
        largo = 3 if grande else 2
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for k in range(largo):
                L.p(cx + dx * (r + 2 + k), cy + dy * (r + 2 + k), P["amarillo"])
        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            for k in range(largo - 1 if not grande else largo):
                d = int((r + 1) * 0.72) + 1 + k
                L.p(cx + dx * d, cy + dy * d, P["oro"])
    pts = elipse_pts(cx, cy, r, r)
    forma(L, {(x - cx, y - cy) for x, y in pts}, P["amarillo"], P["oro"], h("fff0a8"), P["naranja"], cx, cy)


def luna(L, cx, cy, r):
    disco = elipse_pts(cx, cy, r, r)
    corte = elipse_pts(cx + r * 0.75, cy - r * 0.35, r, r)
    pts = {(x - cx, y - cy) for (x, y) in disco - corte}
    forma(L, pts, P["crema"], P["rosa"], h("ffffff"), P["piedra"], cx, cy)
    L.p(cx - r // 2, cy + r // 3, P["rosa"])


def estrella(L, x, y, c, cruz=False):
    L.p(x, y, c)
    if cruz:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            L.p(x + dx, y + dy, mezcla(c, h("1c2350"), 0.4))


def rayo(L, x, y):
    filas = ["..XXX", ".XXX.", ".XX..", "XXXXX", "..XX.", ".XX..", ".X...", "X...."]
    pts, _, _ = mascara_mapa(filas)
    forma(L, pts, P["amarillo"], P["oro"], h("fff6c0"), P["naranja"], x, y)


def gotas_lluvia(L, x0, y0, w, hh, semilla, c=h("b7dcf2"), densidad=0.016):
    rnd = random.Random(semilla)
    for _ in range(int(w * hh * densidad)):
        x = rnd.randrange(x0, x0 + w)
        y = rnd.randrange(y0, y0 + hh)
        L.p(x, y, c)
        L.p(x, y + 1, c)
        L.p(x - 1, y + 2, mezcla(c, h("5f6e85"), 0.35))


# ---------------------------------------------------------------------------------------------------------------
# Estados del clima (datos inventados como los de Open-Meteo: codigo WMO, temp, max, min, lluvia, dia/noche)
# ---------------------------------------------------------------------------------------------------------------

ESTADOS = [
    # clave, titulo de la columna, nombre, codigo WMO, temp, max, min, lluvia %, noche
    ("despejado", "Despejado de día", "Despejado", 0, 24, 26, 13, 0, 0.0),
    ("lluvia", "Lluvia", "Lluvia", 63, 17, 19, 12, 90, 0.0),
    ("nublado", "Nublado", "Nublado", 3, 19, 21, 12, 20, 0.0),
    ("noche", "Noche despejada", "Despejado", 0, 14, 24, 11, 0, 1.0),
    ("calor", "Calor (≥ 28°)", "Despejado", 0, 31, 33, 18, 0, 0.0),
    ("frio", "Frío (≤ 10°)", "Despejado", 1, 7, 15, 3, 10, 0.0),
]

CIELOS = {
    "despejado": ["3f8fd8", "4f9de0", "63ade8", "7cc0ef", "9ed4f5"],
    "lluvia": ["4a566c", "556278", "616f86", "6e7c93", "7b8aa1"],
    "nublado": ["7a8ba3", "8796ac", "95a3b7", "a5b1c3", "b7c1d0"],
    "noche": ["101536", "151b44", "1b2352", "222b60", "2b356e"],
    "calor": ["3586d4", "4c9be0", "7fb8e0", "e9c27a", "f2a94e"],
    "frio": ["7fa7cf", "93b6d9", "a9c6e2", "c0d6eb", "d8e7f4"],
    "tormenta": ["262a3d", "2e3349", "373d56", "414864", "4b5372"],
}

# El perro que reacciona en cada estado: (perro, fila de perros.json, cuadro que se queda quieto, espejo)
PERRO_CLIMA = {
    "despejado": ("hunter", "sunglasses", 6, False),   # se pone los lentes
    "lluvia": ("tango", "umbrella", 6, False),         # saca el paraguas
    "nublado": ("hunter", "look_up", 6, False),        # mira al cielo
    "noche": ("hunter", "telescope", 7, True),         # el telescopio
    "calor": ("tango", "icecream", 4, False),          # nieve de limon
    "frio": ("hunter", "scarf", 7, False),             # bufanda
}


def cielo(w, hh, est, semilla=1, lejos=True, sol_en=None):
    """Pedazo de cielo en pixel: bandas (sin degradado suave), sol/luna/nubes/lluvia/estrellas segun el estado y,
    si 'lejos', la ciudad del cuarto abajo (la misma de la ventana de cuarto_dia.png)."""
    L = nuevo(w, hh)
    bandas = [h(c) for c in CIELOS[est]]
    for y in range(hh):
        t = y / hh * len(bandas)
        i = min(int(t), len(bandas) - 1)
        f = t - int(t)
        for x in range(w):
            c = bandas[i]
            # tramado de 1 px en la orilla de cada banda
            if f > 0.82 and i + 1 < len(bandas) and (x + y) % 2 == 0:
                c = bandas[i + 1]
            L.p(x, y, c)
    rnd = random.Random(semilla)
    sx, sy = sol_en or (int(w * 0.28), int(hh * 0.30))
    if est == "despejado":
        sol(L, sx, sy, 5)
        nube(L, int(w * 0.55), int(hh * 0.12), 20, NUBE_CLARA)
    elif est == "calor":
        sol(L, sx, sy, 7, grande=True)
        # aire caliente que ondula sobre la ciudad
        for k, yy in enumerate((int(hh * 0.62), int(hh * 0.70))):
            for x in range(2 + k * 3, w - 2, 8):
                for i, d in enumerate((0, -1, -1, 0)):
                    L.p(x + i, yy + d, h("ffd9a0"))
    elif est == "nublado":
        nube(L, int(w * 0.02), int(hh * 0.10), 26, NUBE_GRIS)
        nube(L, int(w * 0.42), int(hh * 0.26), 30, NUBE_CLARA)
    elif est == "lluvia" or est == "tormenta":
        gotas_lluvia(L, 0, 0, w, hh, semilla)
        nube(L, int(w * -0.05), int(hh * 0.02), 32, NUBE_OSCURA if est == "tormenta" else NUBE_GRIS)
        nube(L, int(w * 0.45), int(hh * 0.08), 30, NUBE_OSCURA)
        if est == "tormenta":
            rayo(L, int(w * 0.62), int(hh * 0.38))
    elif est == "noche":
        for _ in range(w * hh // 40):
            estrella(L, rnd.randrange(w), rnd.randrange(int(hh * 0.7)), P["crema"] if rnd.random() < 0.5 else P["azulgris"])
        for _ in range(2):
            estrella(L, rnd.randrange(4, w - 4), rnd.randrange(3, int(hh * 0.5)), P["crema"], cruz=True)
        luna(L, sx, sy, 5)
    elif est == "frio":
        sol(L, sx, sy, 4, rayos=False)
        nube(L, int(w * 0.50), int(hh * 0.16), 22, NUBE_CLARA)
    if lejos:
        ciudad(L, w, hh, est, rnd)
    return L.im


def ciudad(L, w, hh, est, rnd):
    """Edificios lejanos en lila (como los de la ventana del cuarto); de noche con ventanitas prendidas."""
    de_noche = est == "noche"
    c1, c2, c3 = ((h("8a86b3"), h("75739d"), h("9a9cc4")) if not de_noche else (h("2a2c55"), h("232447"), h("33366a")))
    if est in ("lluvia", "tormenta", "nublado"):
        c1, c2, c3 = mezcla(c1, h("6a7088"), 0.5), mezcla(c2, h("5a6078"), 0.5), mezcla(c3, h("7a8098"), 0.5)
    x = 0
    rr = random.Random(7)
    while x < w:
        bw = rr.randrange(6, 11)
        bh = rr.randrange(int(hh * 0.18), int(hh * 0.38))
        top = hh - bh
        L.rect(x, top, x + bw - 1, hh - 1, c1)
        L.rect(x + bw - 2, top + 1, x + bw - 1, hh - 1, c2)
        L.rect(x, top, x + bw - 1, top, c3)
        if rr.random() < 0.4:
            L.rect(x + bw // 2, top - 3, x + bw // 2, top - 1, c2)  # antenita
        if de_noche:
            for wy in range(top + 2, hh - 2, 3):
                for wx in range(x + 1, x + bw - 2, 2):
                    if rr.random() < 0.22:
                        L.p(wx, wy, P["amarillo"] if rr.random() < 0.7 else P["oro"])
        x += bw + rr.randrange(0, 2)


def perro_clima(est, n):
    nom, fila, cuadro, esp = PERRO_CLIMA[est]
    d = perro(nom, fila, cuadro)
    if esp:
        d = espejo(d)
    return noche(d) if n > 0.5 else d


def pegar_pies(im, d, cx, pies):
    """Pega el perro con el centro de su caja en cx y la ultima fila con pixeles en 'pies'."""
    caja = d.getbbox()
    M.pegar_recortado(im, d, int(cx - (caja[0] + caja[2]) / 2), pies - caja[3] + 1)


def con_noche(im, n):
    return noche_obj(im) if n > 0.5 else im


# ---------------------------------------------------------------------------------------------------------------
# Clima C1: la ventana (de madera, como la del boton de fondo) con el cielo de verdad; el perro en la repisa
# ---------------------------------------------------------------------------------------------------------------

def ventana_clima(est, n, W=76, H=64):
    out = Image.new("RGBA", (W + 4, H + 8), (0, 0, 0, 0))
    # el cielo detras del vidrio
    vid = cielo(W - 8, H - 8, est, semilla=3, sol_en=(12, 11))
    out.alpha_composite(vid, (6, 4))
    L = Lienzo(out)
    gx0, gy0, gx1, gy1 = 6, 4, 6 + W - 9, 4 + H - 9
    # gotas escurriendo en el vidrio / escarcha en las esquinas / reflejo
    if est in ("lluvia", "tormenta"):
        rnd = random.Random(11)
        for _ in range(7):
            x = rnd.randrange(gx0 + 2, gx1 - 2)
            y = rnd.randrange(gy0 + 4, gy1 - 8)
            for k in range(rnd.randrange(2, 6)):
                L.p(x, y + k, h("d6ecf8") if k else h("ffffff"))
            L.p(x, y + 1 + k, h("8fb3cc"))
    if est == "frio":
        for (cx, cy, sx, sy) in ((gx0, gy0, 1, 1), (gx1, gy0, -1, 1), (gx0, gy1, 1, -1), (gx1, gy1, -1, -1),
                                 (gx0 + (gx1 - gx0) // 2 - 2, gy1, -1, -1), (gx0 + (gx1 - gx0) // 2 + 2, gy1, 1, -1)):
            for i in range(10):
                for j in range(10 - i):
                    if (i + j) < 5 or (i * 3 + j * 5) % 3 == 0:
                        L.p(cx + sx * i, cy + sy * j, h("eef6fc") if (i + j) % 3 else h("ffffff"))
    for (a, b) in ((gx0 + 4, gy0 + 14), (gx0 + 8, gy0 + 14)):
        for k in range(6):
            L.p(a + k, b - k, mezcla(L.px[a + k, b - k], h("ffffff"), 0.35 if est != "noche" else 0.15))
    # marco de madera (en capa aparte para pasarla por el filtro de noche)
    mar = nuevo(W + 4, H + 8)
    x0, y0, x1, y1 = 2, 0, W + 1, H - 1
    madera = (P["madera"], P["madera_luz"], P["madera_osc"], P["contorno_mad"])
    for t in range(4):
        mar.caja(x0 + t, y0 + t, x1 - t, y1 - t, madera[0])
    mar.caja(x0 + 1, y0 + 1, x1 - 1, y1 - 1, madera[1])
    mar.rect(x0 + 1, y1 - 2, x1 - 1, y1 - 1, madera[2])
    mar.rect(x1 - 2, y0 + 1, x1 - 1, y1 - 1, madera[2])
    mar.caja(x0, y0, x1, y1, madera[3], esquina=True)
    mar.caja(x0 + 4, y0 + 4, x1 - 4, y1 - 4, madera[3])
    # parteluz
    mx = (x0 + x1) // 2
    mar.rect(mx - 1, y0 + 4, mx + 1, y1 - 4, madera[0])
    mar.rect(mx - 1, y0 + 4, mx - 1, y1 - 4, madera[1])
    mar.rect(mx + 1, y0 + 4, mx + 1, y1 - 4, madera[2])
    mar.rect(mx - 2, y0 + 4, mx - 2, y1 - 4, madera[3])
    mar.rect(mx + 2, y0 + 4, mx + 2, y1 - 4, madera[3])
    # persiana enrollada arriba (como la de la ventana del cuarto)
    mar.rect(x0 + 4, y0 + 4, x1 - 4, y0 + 6, P["hueso"])
    mar.rect(x0 + 4, y0 + 6, x1 - 4, y0 + 6, P["perla"])
    mar.rect(x0 + 4, y0 + 7, x1 - 4, y0 + 7, P["piedra"])
    mar.p(mx + 8, y0 + 8, P["gris"])
    mar.p(mx + 8, y0 + 9, P["gris"])
    # repisa
    ry = y1 - 1
    mar.rect(x0 - 2, ry, x1 + 2, ry + 4, P["madera_med"])
    mar.rect(x0 - 2, ry, x1 + 2, ry, P["madera_luz"])
    mar.rect(x0 - 2, ry + 3, x1 + 2, ry + 3, P["madera_osc"])
    mar.caja(x0 - 3, ry - 1, x1 + 3, ry + 5, madera[3], esquina=True)
    mar.rect(x0 - 1, ry + 6, x1 + 1, ry + 6, (0, 0, 0, 60))
    # cosas en la repisa
    if est == "frio":  # taza de chocolate con vapor
        tx = x0 + 6
        mar.mapa(tx, ry - 7, ["..x.x.", ".x.x..", "oooooo", "occcco", "occccoo", "occcco.o", ".oooo.."],
                 {"x": h("e8eef6"), "o": P["tinta"], "c": P["teja"]})
        mar.rect(tx + 1, ry - 4, tx + 4, ry - 4, P["cafe"])
    if est == "calor":  # maceta: la plantita con gotas de sudor... y un vaso de agua de limon
        tx = x0 + 6
        mar.mapa(tx, ry - 9, ["o....o", "ollllo", "ollllo", "oLllLo", "ollllo", "ollllo", "ollllo", "oLLLLo", ".oooo."],
                 {"o": P["tinta"], "l": h("c9e98a"), "L": P["verde"]})
        mar.p(tx + 3, ry - 11, P["verde_osc"])
        mar.p(tx + 4, ry - 12, P["verde_osc"])
    out.alpha_composite(con_noche(mar.im, n))
    # vidrio de noche: la ventana de noche se ve oscura pero sin pasar el cielo por el filtro
    return out


def opcion_c1(est, n, mini=False):
    W = 210
    im = Image.new("RGBA", (W, 80), (0, 0, 0, 0))
    v = ventana_clima(est, n)
    im.alpha_composite(v, (0, 4))
    d = perro_clima(est, n)
    pegar_pies(im, d, 61, 4 + 64 - 2)
    _, nombre, codigo, t, mx, mn, ll = info(est)
    L = Lienzo(im)
    tx = 92
    x = numero(L, tx, 6, f"{t}°", XL)
    palabra(im, (tx, 36), nombre)
    detalles(Lienzo(im), tx, 54, mx, mn, ll, n)
    return im


INFO_EXTRA = {}


def info(est):
    if est in INFO_EXTRA:
        return INFO_EXTRA[est]
    for e in ESTADOS:
        if e[0] == est:
            return e[1], e[2], e[3], e[4], e[5], e[6], e[7]
    raise KeyError(est)


# ---------------------------------------------------------------------------------------------------------------
# Clima C2: la capsula del volumen pero con el patio: el cielo, la ciudad, el pasto y el letrero con el numero
# ---------------------------------------------------------------------------------------------------------------

ANCHO_C2 = 168


def suelo(L, w, hh, est, alto=9):
    y0 = hh - alto
    pasto = (P["verde"], P["verde_osc"], h("98c450"))
    if est in ("lluvia", "nublado", "tormenta"):
        pasto = (h("6a9a3a"), h("4c7a22"), h("86b04e"))
    if est == "calor":
        pasto = (h("a8a24a"), h("8a7f30"), h("c6bc62"))
    L.rect(0, y0, w - 1, hh - 1, pasto[0])
    L.rect(0, y0, w - 1, y0, pasto[2])
    rnd = random.Random(5)
    for x in range(0, w, 2):
        if rnd.random() < 0.5:
            L.p(x, y0 - 1, pasto[0])
        L.p(x + rnd.randrange(2), y0 + 2 + rnd.randrange(alto - 3), pasto[1])
    # banqueta de abajo
    L.rect(0, hh - 3, w - 1, hh - 1, P["piedra"])
    L.rect(0, hh - 3, w - 1, hh - 3, P["perla"])
    for x in range(6, w, 14):
        L.p(x, hh - 2, P["topo"])
        L.p(x, hh - 1, P["topo"])
    if est == "frio":  # escarcha en el pasto
        for x in range(1, w, 3):
            L.p(x, y0 + (x * 7) % (alto - 4), h("eaf4fb"))
            L.p(x + 1, y0, h("eaf4fb"))


def letrero(L, x, y, s, n, est):
    """Letrero de madera clavado en el pasto con el numero pintado."""
    w = ancho_numero(s, XL) + 10
    hh = 30
    # poste
    L.rect(x + w // 2 - 1, y + hh, x + w // 2 + 1, y + hh + 12, P["madera_osc"])
    L.rect(x + w // 2 - 1, y + hh, x + w // 2 - 1, y + hh + 12, P["madera_med"])
    L.rect(x + w // 2 - 2, y + hh, x + w // 2 - 2, y + hh + 12, P["contorno_mad"])
    L.rect(x + w // 2 + 2, y + hh, x + w // 2 + 2, y + hh + 12, P["contorno_mad"])
    # tabla
    L.rect(x + 1, y + 1, x + w - 2, y + hh - 2, P["madera"])
    L.rect(x + 1, y + 1, x + w - 2, y + 1, P["madera_luz"])
    L.rect(x + 1, y + hh - 3, x + w - 2, y + hh - 2, P["madera_osc"])
    L.rect(x + 2, y + 15, x + w - 3, y + 15, P["madera_med"])  # junta entre tablas
    L.caja(x, y, x + w - 1, y + hh - 1, P["contorno_mad"], esquina=True)
    L.p(x + 2, y + 3, P["nogal"])
    L.p(x + w - 3, y + 3, P["nogal"])
    if est in ("lluvia", "tormenta"):
        for k in range(3):
            L.p(x + 3 + k * 9, y + hh - 1, h("b7dcf2"))
    if est == "frio":
        L.rect(x + 2, y, x + w - 3, y, h("ffffff"))
        L.rect(x + 3, y - 1, x + w - 5, y - 1, h("eaf4fb"))
    return w


def escena_c2(est, n):
    w, hh = ANCHO_C2, 64
    im = cielo(w, hh - 7, est, semilla=9, sol_en=(26, 14))
    esc = Image.new("RGBA", (w, hh), (0, 0, 0, 0))
    esc.alpha_composite(im, (0, 0))
    suelo_l = nuevo(w, hh)
    suelo(suelo_l, w, hh, est)
    _, nombre, codigo, t, mx, mn, ll = info(est)
    sx, sy = 98, 8
    letrero(suelo_l, sx, sy, f"{t}°", n, est)
    if est in ("lluvia", "tormenta"):   # charco con anillos
        cx = 40
        forma(suelo_l, {(x - cx, y - (hh - 6)) for (x, y) in elipse_pts(cx, hh - 6, 12, 2)}, h("5d7ea0"), h("4a6a8c"),
              h("8fb6d6"), h("3d5571"), cx, hh - 6)
        suelo_l.p(cx - 5, hh - 6, h("d0e8f7"))
        suelo_l.p(cx + 4, hh - 7, h("d0e8f7"))
    esc.alpha_composite(con_noche(suelo_l.im, n))
    if est in ("lluvia", "tormenta"):  # la lluvia cae tambien delante del letrero
        gotas_lluvia(Lienzo(esc), 80, 0, 70, 50, 21, densidad=0.01)
    # el numero no pasa por el filtro de noche (de noche lo alumbra un foquito)
    Le = Lienzo(esc)
    if n > 0.5:
        ww = ancho_numero(f"{t}°", XL) + 10
        Le.rect(sx + ww // 2 - 2, sy - 3, sx + ww // 2 + 1, sy - 1, P["tinta"])
        Le.rect(sx + ww // 2 - 1, sy - 1, sx + ww // 2, sy, P["amarillo"])
        for yy in range(sy + 1, sy + 29):
            for xx in range(sx + 1, sx + ww - 1):
                if (xx + yy) % 2 == 0 and abs(xx - (sx + ww / 2)) < 4 + (yy - sy) * 0.8:
                    Le.p(xx, yy, mezcla(Le.px[xx, yy], P["oro"], 0.25))
    numero(Le, sx + 5, sy + 4, f"{t}°", XL, c=P["crema"], sombra=P["rosa"], contorno=P["nogal"])
    d = perro_clima(est, n)
    pegar_pies(esc, d, 50, hh - 5)
    return esc


def opcion_c2(est, n):
    im = Image.new("RGBA", (270, 80), (0, 0, 0, 0))
    cap = capsula(escena_c2(est, n), n, sobra=0)
    im.alpha_composite(cap, (0, 2))
    _, nombre, codigo, t, mx, mn, ll = info(est)
    palabra(im, (ANCHO_C2 + 16, 18), nombre)
    detalles(Lienzo(im), ANCHO_C2 + 16, 37, mx, mn, ll, n, dos_lineas=True)
    return im


# ---------------------------------------------------------------------------------------------------------------
# Clima C3: termometro de pared + letrero colgado con el cielo pintado y el numero
# ---------------------------------------------------------------------------------------------------------------

def termometro(L, x, y, t):
    """Tablita de madera con tubo de vidrio; la columna sube con la temperatura (de -5 a 40); azul con frio."""
    w, hh = 13, 66
    L.rect(x + 1, y + 1, x + w - 2, y + hh - 2, P["madera"])
    L.rect(x + 1, y + 1, x + 1, y + hh - 2, P["madera_luz"])
    L.rect(x + w - 2, y + 1, x + w - 2, y + hh - 2, P["madera_osc"])
    L.caja(x, y, x + w - 1, y + hh - 1, P["contorno_mad"], esquina=True)
    L.p(x + 6, y + 3, P["nogal"])  # clavito
    tx = x + 5
    top, bot = y + 7, y + hh - 13
    # rayitas
    for k, yy in enumerate(range(bot, top, -4)):
        L.rect(tx - 3, yy, tx - 2 if k % 2 else tx - 3, yy, P["nogal"])
        L.rect(tx + 4, yy, tx + 5 if k % 2 else tx + 4, yy, P["nogal"])
    # tubo
    L.rect(tx, top, tx + 2, bot, h("e6f2f8"))
    L.rect(tx - 1, top, tx - 1, bot, P["tinta"])
    L.rect(tx + 3, top, tx + 3, bot, P["tinta"])
    L.p(tx, top - 1, P["tinta"])
    L.p(tx + 1, top - 1, P["tinta"])
    L.p(tx + 2, top - 1, P["tinta"])
    frio = t <= 10
    c, cs, cl = (h("4fa3e0"), h("2e6fb0"), h("9ad2f5")) if frio else (h("e0453a"), h("a8261f"), h("ff8a72"))
    nivel = int(bot - (min(max(t, -5), 40) + 5) / 45 * (bot - top))
    L.rect(tx + 1, nivel, tx + 1, bot + 2, c)
    L.rect(tx + 2, nivel, tx + 2, bot + 2, cs)
    L.p(tx + 1, nivel, cl)
    # bulbo
    bulbo = {(a - (tx + 1), b - (bot + 5)) for (a, b) in elipse_pts(tx + 1, bot + 5, 3, 3)}
    forma(L, bulbo, c, cs, cl, P["tinta"], tx + 1, bot + 5)
    return w


def letrero_colgado(L, im, x, y, est, t, n):
    """Letrero colgado de un clavo: a la izquierda el cielo pintado en chiquito, a la derecha el numero."""
    s = f"{t}°"
    wn = ancho_numero(s, XL)
    pic = 24
    w = 6 + pic + 6 + wn + 7
    hh = 32
    # cordel y clavo
    L.linea(x + 6, y + 2, x + w // 2, y - 7, P["cafe"])
    L.linea(x + w - 7, y + 2, x + w // 2, y - 7, P["cafe"])
    L.p(x + w // 2, y - 8, P["gris"])
    L.p(x + w // 2, y - 7, P["tinta"])
    # tabla
    L.rect(x + 1, y + 1, x + w - 2, y + hh - 2, P["madera"])
    L.rect(x + 1, y + 1, x + w - 2, y + 1, P["madera_luz"])
    L.rect(x + 1, y + hh - 3, x + w - 2, y + hh - 2, P["madera_osc"])
    L.rect(x + 1, y + 15, x + w - 2, y + 15, P["madera_med"])
    L.caja(x, y, x + w - 1, y + hh - 1, P["contorno_mad"], esquina=True)
    # el cielo pintado (marquito)
    c = cielo(pic, pic, est, semilla=4, lejos=False, sol_en=(8, 8))
    px, py = x + 5, y + (hh - pic) // 2
    L.im.alpha_composite(c, (px, py))
    L.px = L.im.load()
    L.caja(px - 1, py - 1, px + pic, py + pic, P["nogal"])
    return w, (x + 6 + pic + 6, y + (hh - 22) // 2)


def opcion_c3(est, n):
    im = Image.new("RGBA", (230, 84), (0, 0, 0, 0))
    obj = nuevo(230, 84)
    _, nombre, codigo, t, mx, mn, ll = info(est)
    termometro(obj, 2, 6, t)
    w, nxy = letrero_colgado(obj, obj.im, 22, 11, est, t, n)
    if est in ("lluvia", "tormenta"):  # goteras: gotitas escurriendo de la tabla
        for k in (8, 30, 50):
            obj.p(22 + k, 12 + 31, h("b7dcf2"))
            obj.p(22 + k, 12 + 33, h("b7dcf2"))
    if est == "frio":
        obj.rect(23, 12, 22 + w - 2, 12, h("ffffff"))
        for k in range(24, 22 + w - 2, 3):
            obj.p(k, 13, h("eaf4fb"))
            if k % 2:
                obj.p(k, 14, h("eaf4fb"))
    im.alpha_composite(con_noche(obj.im, n))
    numero(Lienzo(im), nxy[0], nxy[1], f"{t}°", XL, c=P["crema"], sombra=P["rosa"], contorno=P["nogal"])
    d = perro_clima(est, n)
    pegar_pies(im, d, max(22 + w + 14, 150), 70)
    palabra(im, (22, 50), nombre)
    detalles(Lienzo(im), 22, 66, mx, mn, ll, n)
    return im


# ---------------------------------------------------------------------------------------------------------------
# Bateria
# ---------------------------------------------------------------------------------------------------------------

BATERIAS = [("100c", "100 % cargando", 100, True), ("60", "60 % sin cargar", 60, False), ("12", "12 % baja", 12, False)]
ROJO_PLATO = [h("a8261f"), h("d6402f"), h("f07a5a"), h("5d1915")]
NUM_X = 104   # el porcentaje va siempre en el mismo lugar


def color_pct(p):
    if p <= 15:
        return h("ff7a6b"), h("c8463a")
    if p <= 40:
        return h("ffc45c"), h("d08a2a")
    return P["crema"], P["rosa"]


def numero_bateria(im, x, y, p, cargando):
    L = Lienzo(im)
    c, s = color_pct(p)
    numero(L, x, y, f"{p}%", GRANDE, c=c, sombra=s)
    etq = ("CARGADA" if p >= 100 else "CARGANDO") if cargando else ("BATERÍA BAJA" if p <= 15 else "")
    if etq:
        palabra(im, (x, y + 21), etq, (255, 170, 160, 255) if p <= 15 else (215, 220, 230, 255))


def textura_croquetas(L, pts, borde_arriba=True):
    for (x, y) in pts:
        k = (x * 5 + y * 3) % 7
        col = P["madera_med"] if k < 3 else (P["madera_osc"] if k < 5 else (P["madera_luz"] if k == 5 else P["nogal"]))
        if borde_arriba and (x, y - 1) not in pts:
            col = P["oro"] if (x + y) % 3 else P["madera_luz"]
        L.p(x, y, col)


def croqueta(L, x, y):
    L.p(x, y, P["madera_luz"])
    L.p(x + 1, y, P["madera_med"])
    L.p(x, y + 1, P["madera_med"])
    L.p(x + 1, y + 1, P["nogal"])


def plato(L, cx, base, p):
    """Plato de croquetas rojo visto de 3/4, con un huesito pintado. 100 % = copeton; 60 % = parejo con la pared de
    atras asomando; 12 % = tres croquetas en el fondo."""
    a, b, hondo = 23, 6, 12
    top = base - hondo
    cuerpo = set()
    for y in range(top, base + 1):
        k = (y - top) / hondo
        half = int(a - k * 4)
        for x in range(cx - half, cx + half + 1):
            cuerpo.add((x, y))
    cuerpo |= {(x, y) for (x, y) in elipse_pts(cx, base - 1, a - 4, 2) if y >= base - 1}
    forma(L, cuerpo, ROJO_PLATO[1], ROJO_PLATO[0], ROJO_PLATO[2], P["vino"])
    L.mapa(cx - 5, top + 7, ["X.......X", "XXXXXXXXX", "X.......X"], {"X": P["crema"]})
    boca = elipse_pts(cx, top, a, b)
    forma(L, boca, ROJO_PLATO[1], ROJO_PLATO[0], ROJO_PLATO[2], P["vino"])
    adentro = elipse_pts(cx, top, a - 3, b - 2)
    for (x, y) in adentro:
        L.p(x, y, ROJO_PLATO[0] if y < top else ROJO_PLATO[3])
    if p >= 95:
        monte = adentro | {(x, y) for (x, y) in elipse_pts(cx, top + 1, a - 5, 10) if y <= top}
        textura_croquetas(L, monte)
    elif p >= 30:
        corte = top - (b - 2) + 1 + int((95 - p) / 65 * 2)
        textura_croquetas(L, {(x, y) for (x, y) in adentro if y >= corte})
    else:
        for (dx, dy) in ((-3, 1), (2, 1), (-1, 2), (5, 2), (-6, 2))[: max(1, p // 4)]:
            croqueta(L, cx + dx, top + dy)
    return top


COSTAL = [
    "......oooo.........",
    "....ooLLLLoo.......",
    "...oLLccccccoo.....",
    "..oLcccccccccco....",
    ".oLccccyccccccco...",
    ".oLcccyycccccccso..",
    "oLcccyyyyyccccccso.",
    "oLcccccyyccccccsso.",
    "oLccccyycccccccsso.",
    ".oLcccyccccccsssoMo",
    "..oLcccccccsssooMMo",
    "...oLccccsssooMMMo.",
    "....ooosssoooMMo...",
    ".......ooooooo.....",
]


def opcion_b1(p, cargando, n):
    """B1 el plato de croquetas."""
    im = Image.new("RGBA", (230, 80), (0, 0, 0, 0))
    obj = nuevo(230, 80)
    cx, base = 34, 68
    for x in range(cx - 20, cx + 21):
        obj.p(x, base + 2, (0, 0, 0, 70))
    top = plato(obj, cx, base, p)
    if cargando:
        # el costal (con el rayo pintado) vacia croquetas en el plato
        obj.mapa(cx - 26, top - 24, COSTAL, {"o": P["contorno_mad"], "L": P["rosa"], "c": h("c9a774"),
                                             "s": h("a3814f"), "y": P["amarillo"], "M": P["nogal"]})
        for (dx, dy) in ((-8, -10), (-6, -6), (-9, -3)):
            croqueta(obj, cx + dx, top + dy)
        if p >= 100:  # se desborda: unas croquetas en el piso
            for (dx, dy) in ((-25, 0), (21, 1), (25, 0)):
                croqueta(obj, cx + dx, base + dy)
    im.alpha_composite(con_noche(obj.im, n))
    L = Lienzo(im)
    if cargando:  # chispita en la orilla del costal
        for (dx, dy) in ((0, -2), (0, 2), (-2, 0), (2, 0), (0, 0)):
            L.p(cx - 26 + dx, top - 26 + dy, h("fff6c0") if (dx, dy) == (0, 0) else P["amarillo"])
        d = perro("hunter", "idle", 3)            # Hunter espera sentado junto al plato
        pegar_pies(im, noche(d) if n > 0.5 else d, cx + 38, base + 1)
    elif p > 15:
        d = espejo(perro("hunter", "chew", 3))    # Hunter echado, mordiendo una croqueta
        pegar_pies(im, noche(d) if n > 0.5 else d, cx + 44, base + 1)
    else:
        d = espejo(perro("tango", "sniff", 4))    # Tango llega a oler el plato casi vacio
        pegar_pies(im, noche(d) if n > 0.5 else d, cx + 44, base + 1)
        g = nuevo(18, 13)
        g.mapa(0, 0, [
            ".oooooooooooooo.",
            "oHHHHHHHHHHHHHHo",
            "oHHrrrrrrrrrrHHo",
            "oHHHRRRRRRRRHHHo",
            "oHHHHrrrrrrHHHHo",
            "oHHHHHHHHHHHHHHo",
            ".oooooHHoooooooo",
            "......oHo.......",
            ".......o........",
        ], {"o": P["tinta"], "H": P["crema"], "r": ROJO_PLATO[1], "R": ROJO_PLATO[3]})
        im.alpha_composite(noche_obj(g.im) if n > 0.5 else g.im, (cx + 40, base - 36))
    numero_bateria(im, NUM_X, 22, p, cargando)
    return im


def lampara(L, cx, base, p, top):
    """La lampara de pie del cuarto. La pantalla es el medidor: se prende de abajo hacia arriba."""
    sw_top, sw_bot, sh = 20, 32, 24
    L.rect(cx - 1, top + sh, cx, base - 3, P["nogal"])
    L.rect(cx - 1, top + sh, cx - 1, base - 3, P["madera_osc"])
    L.rect(cx - 7, base - 2, cx + 6, base, P["nogal"])
    L.rect(cx - 7, base - 2, cx + 6, base - 2, P["madera_med"])
    L.caja(cx - 8, base - 3, cx + 7, base + 1, P["contorno_mad"], esquina=True)
    lleno = int(round(sh * p / 100))
    pts = set()
    for y in range(sh):
        half = sw_top / 2 + (sw_bot - sw_top) / 2 * y / (sh - 1)
        for xx in range(int(cx - half), int(cx + half)):
            pts.add((xx, top + y))
    apagada = (h("8a6a3a"), h("6e5230"), h("a07e48"))
    prendida = (P["amarillo"], P["oro"], h("fff0a8"))
    for (xx, yy) in pts:
        pal = prendida if (top + sh - 1 - yy) < lleno else apagada
        col = pal[0]
        if (xx - 1, yy) not in pts or (xx - 2, yy) not in pts:
            col = pal[2]
        if (xx + 1, yy) not in pts or (xx + 2, yy) not in pts:
            col = pal[1]
        L.p(xx, yy, col)
    for k in (1, 2, 3):  # costuras de la tela cada 25 %
        yy = top + sh - int(sh * k / 4)
        for (xx, y2) in pts:
            if y2 == yy and xx % 2 == 0:
                L.p(xx, yy, mezcla(L.px[xx, yy], P["contorno_mad"], 0.3))
    for (xx, yy) in pts:
        for dx, dy in ((1, 0), (-1, 0), (0, -1), (0, 1)):
            if (xx + dx, yy + dy) not in pts:
                L.p(xx + dx, yy + dy, P["contorno_mad"])
    # foco asomando abajo
    L.rect(cx - 2, top + sh + 1, cx + 1, top + sh + 2, P["amarillo"] if p > 15 else P["perla"])
    return sh


def multicontacto(L, x, y, enchufado):
    L.rect(x, y, x + 21, y + 5, P["hueso"])
    L.rect(x, y, x + 21, y, h("ffffff"))
    L.rect(x, y + 5, x + 21, y + 5, P["perla"])
    L.caja(x - 1, y - 1, x + 22, y + 6, P["tinta"], esquina=True)
    for k in range(3):
        L.p(x + 3 + k * 6, y + 2, P["tinta"])
        L.p(x + 5 + k * 6, y + 2, P["tinta"])
    L.rect(x + 19, y + 2, x + 20, y + 3, h("ff5040") if enchufado else P["topo"])


def opcion_b2(p, cargando, n):
    """B2 la lampara de pie."""
    im = Image.new("RGBA", (230, 80), (0, 0, 0, 0))
    obj = nuevo(230, 80)
    cx, base, top = 54, 72, 6
    for x in range(cx - 10, cx + 10):
        obj.p(x, base + 2, (0, 0, 0, 60))
    sh = lampara(obj, cx, base, p, top)
    mx = 72
    obj.linea(cx + 8, base, mx + 2 if cargando else mx - 6, base, P["tinta"])
    multicontacto(obj, mx, base - 5, cargando)
    if cargando:   # la clavija puesta en el multicontacto
        obj.mapa(mx + 8, base - 10, ["ooooo", "oXXXo", "oXXXo", ".oXo."], {"o": P["tinta"], "X": P["carbon"]})
        obj.linea(mx + 2, base, mx + 2, base - 7, P["tinta"])
        obj.linea(mx + 2, base - 7, mx + 7, base - 7, P["tinta"])
    else:          # la clavija suelta en el piso, con las patitas doradas
        obj.mapa(mx - 7, base - 2, ["ooo..", "oXXoo", "oXXoY", "oXXoo", "ooo.."],
                 {"o": P["tinta"], "X": P["carbon"], "Y": P["oro"]})
    im.alpha_composite(con_noche(obj.im, n))
    L = Lienzo(im)
    if n > 0.5 and p > 15:
        # de noche la luz de la pantalla no pasa por el filtro: se repinta prendida y cae un cono al piso
        luz = Image.new("RGBA", im.size, (0, 0, 0, 0))
        Ld = Lienzo(luz)
        for y in range(top + sh + 1, base + 2):
            k = (y - top - sh) / (base - top - sh)
            half = 15 + k * 14
            for x in range(int(cx - half), int(cx + half)):
                if (x + y) % 2 == 0:
                    Ld.p(x, y, (255, 214, 120, int(30 + 60 * p / 100)))
        im.alpha_composite(luz)
        tmp = nuevo(230, 80)
        lampara(tmp, cx, base, p, top)
        pant = tmp.im.crop((0, top - 1, 230, top + sh + 3))
        im.alpha_composite(pant, (0, top - 1))
    if cargando:   # chispa en la clavija
        for (dx, dy, c) in ((0, 0, h("fff6c0")), (-2, -1, P["amarillo"]), (2, -2, P["amarillo"]), (1, -4, P["oro"]),
                            (-1, 1, P["oro"])):
            L.p(mx + 10 + dx, base - 13 + dy, c)
        d = perro("hunter", "newspaper", 7)        # con la luz llena, Hunter lee el periodico
        pegar_pies(im, noche(d) if n > 0.5 else d, cx - 34, base)
    elif p > 15:
        d = perro("hunter", "sleep_curl", 6)       # a media luz, Hunter se duerme
        pegar_pies(im, noche(d) if n > 0.5 else d, cx - 34, base)
    else:
        d = perro("tango", "scared", 6)            # casi a oscuras: Tango con miedo
        pegar_pies(im, noche(d) if n > 0.5 else d, cx - 32, base)
    numero_bateria(im, NUM_X, 22, p, cargando)
    return im


def frasco(L, x, y, p, cargando):
    """Frasco de vidrio con croquetas; la etiqueta (arriba) lleva un huesito. Es un medidor vertical."""
    w, hh = 40, 56
    tapa_y = y + (-6 if cargando else 0)
    L.rect(x + 4, tapa_y, x + w - 5, tapa_y + 5, ROJO_PLATO[1])
    L.rect(x + 4, tapa_y, x + w - 5, tapa_y, ROJO_PLATO[2])
    L.rect(x + 4, tapa_y + 4, x + w - 5, tapa_y + 5, ROJO_PLATO[0])
    for k in range(x + 6, x + w - 6, 3):
        L.p(k, tapa_y + 2, ROJO_PLATO[0])
    L.caja(x + 3, tapa_y - 1, x + w - 4, tapa_y + 6, P["vino"], esquina=True)
    by = y + 7
    cuerpo = set()
    for yy in range(by, y + hh):
        ins = 3 if yy < by + 3 else 0
        for xx in range(x + 1 + ins, x + w - 1 - ins):
            cuerpo.add((xx, yy))
    for d in ((x + 1, y + hh - 1), (x + w - 2, y + hh - 1), (x + 2, y + hh - 1), (x + w - 3, y + hh - 1),
              (x + 1, y + hh - 2), (x + w - 2, y + hh - 2)):
        cuerpo.discard(d)
    for (xx, yy) in cuerpo:
        L.p(xx, yy, (207, 230, 238, 90))
    alto_util = hh - 11
    nivel = y + hh - 2 - int(alto_util * p / 100)
    textura_croquetas(L, {(xx, yy) for (xx, yy) in cuerpo if yy >= nivel})
    for yy in range(by + 4, y + hh - 6):
        L.p(x + 4, yy, (255, 255, 255, 170))
    for yy in range(by + 6, by + 14):
        L.p(x + 6, yy, (255, 255, 255, 120))
    for (xx, yy) in cuerpo:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (xx + dx, yy + dy) not in cuerpo:
                L.p(xx + dx, yy + dy, h("5f8796"))
    # rayitas de medida en el vidrio (cada 25 %)
    for k in (1, 2, 3):
        yy = y + hh - 2 - int(alto_util * k / 4)
        L.rect(x + w - 6, yy, x + w - 4, yy, (255, 255, 255, 200))
    # etiqueta con huesito, arriba
    ex, ey, ew = x + 10, by + 4, w - 20
    L.rect(ex, ey, ex + ew - 1, ey + 7, P["crema"])
    L.rect(ex, ey + 7, ex + ew - 1, ey + 7, P["rosa"])
    L.caja(ex - 1, ey - 1, ex + ew, ey + 8, P["topo"], esquina=True)
    L.mapa(ex + ew // 2 - 5, ey + 2, ["X........X", "XXXXXXXXXX", "X........X"], {"X": P["teja"]})
    return nivel


def opcion_b3(p, cargando, n):
    """B3 el frasco de croquetas."""
    im = Image.new("RGBA", (230, 80), (0, 0, 0, 0))
    obj = nuevo(230, 80)
    x, y = 40, 15
    for xx in range(x, x + 41):
        obj.p(xx, y + 57, (0, 0, 0, 70))
    frasco(obj, x, y, p, cargando)
    im.alpha_composite(con_noche(obj.im, n))
    L = Lienzo(im)
    if cargando:
        croqueta(L, x + 18, y - 1)
        croqueta(L, x + 23, y - 4)
        for (dx, dy) in ((0, 0), (0, -2), (0, 2), (-2, 0), (2, 0)):
            L.p(x + 38 + dx, y - 9 + dy, h("fff6c0") if (dx, dy) == (0, 0) else P["amarillo"])
        d = perro("tango", "idle", 2)                # Tango mira como se llena
        pegar_pies(im, noche(d) if n > 0.5 else d, x - 16, y + 56)
    elif p > 15:
        d = perro("tango", "sleep_curl", 6)          # Tango dormido junto al frasco
        pegar_pies(im, noche(d) if n > 0.5 else d, x - 20, y + 56)
    else:
        d = perro("hunter", "look_up", 6)            # Hunter mira el frasco, esperando
        pegar_pies(im, noche(d) if n > 0.5 else d, x - 16, y + 56)
    numero_bateria(im, NUM_X - 12, 22, p, cargando)
    return im


# ---------------------------------------------------------------------------------------------------------------
# Fondos de las celdas (el cielo real de la pantalla)
# ---------------------------------------------------------------------------------------------------------------

def fondo_dia(w, hh, y_pantalla=560):
    """El cielo de la captura de dia en la zona del clima, sin el clima viejo: la columna x=20 repetida."""
    col = CAP_DIA.crop((20, y_pantalla, 21, y_pantalla + hh))
    return col.resize((w, hh), Image.NEAREST)


def fondo_noche(w, hh, x=40, y=560):
    return CAP_NOCHE.crop((x, y, x + w, y + hh))


def celda(w, hh, n, x=40):
    return fondo_noche(w, hh, x) if n > 0.5 else fondo_dia(w, hh)


# ---------------------------------------------------------------------------------------------------------------
# Laminas
# ---------------------------------------------------------------------------------------------------------------

FONDO_LAM = M.FONDO_LAM
GRIS = (170, 172, 185, 255)
NARANJA = (250, 200, 120, 255)

OPC_C = [
    ("C1  La ventana", "el cielo de verdad detrás del vidrio; el perro en la repisa", lambda e, n: opcion_c1(e, n)),
    ("C2  El patio (cápsula)", "la cápsula del volumen, pero afuera: letrero con el número", lambda e, n: opcion_c2(e, n)),
    ("C3  Termómetro y letrero", "termómetro de pared + letrero colgado con el cielo pintado", lambda e, n: opcion_c3(e, n)),
]


def lamina_clima():
    colw, rowh = 280, 112
    W = 24 + 6 * colw
    H = 70 + 150 + 3 * (rowh + 40) + 10
    lam = Image.new("RGBA", (W, H), FONDO_LAM)
    texto(lam, (24, 14), "Clima: tres escenitas en pixel  (escala real, 1 px = 1 dp; fondo = el cielo real de la pantalla)", M.F_TIT)
    # hoy
    y = 52
    texto(lam, (24, y), "Hoy (WeatherView): ícono de Meteocons + texto", M.F_ETQ, NARANJA)
    texto(lam, (24, y + 18), "vertical sin imagen (bajo la tarjeta)", M.F_PEQ, GRIS)
    lam.alpha_composite(CAP_DIA.crop((60, 572, 360, 690)), (24, y + 36))
    texto(lam, (24 + 330, y + 18), "con imagen de fondo (arriba a la derecha)", M.F_PEQ, GRIS)
    lam.alpha_composite(CAP_IMG.crop((290, 66, 590, 176)), (24 + 330, y + 36))
    y0 = 52 + 160
    for r, (tit, sub, fn) in enumerate(OPC_C):
        y = y0 + r * (rowh + 40)
        texto(lam, (24, y), tit, M.F_ETQ, NARANJA)
        texto(lam, (24 + 230, y + 2), sub, M.F_PEQ, GRIS)
        for c, (clave, etq, *_rest) in enumerate(ESTADOS):
            n = _rest[-1]
            x = 24 + c * colw
            if r == 0:
                pass
            texto(lam, (x, y + 20), etq, M.F_PEQ, GRIS)
            fondo = celda(colw - 12, rowh - 8, n, x=20 + (c % 2) * 200)
            lam.alpha_composite(fondo, (x, y + 38))
            img = fn(clave, n)
            lam.alpha_composite(img.crop((0, 0, min(img.width, colw - 12), min(img.height, rowh - 8))), (x + 6, y + 38 + 8))
    M.guardar(lam, "maqueta-clima")


def lamina_bateria():
    colw, rowh = 250, 96
    opciones = [
        ("B1  El plato de croquetas", "lleno = 100 %; cargando, el costal lo rellena; con poca, Tango llega a oler", opcion_b1),
        ("B2  La lámpara de pie", "la pantalla se prende de abajo hacia arriba; cargando, la clavija en el multicontacto", opcion_b2),
        ("B3  El frasco de croquetas", "medidor vertical (rayitas cada 25 %); cargando, la tapa se abre y entran croquetas", opcion_b3),
    ]
    W = 24 + 6 * colw
    H = 52 + 120 + 3 * (rowh + 44) + 10
    lam = Image.new("RGBA", (W, H), FONDO_LAM)
    texto(lam, (24, 14), "Batería: tres cosas del cuarto que se llenan  (escala real; día a la izquierda, noche a la derecha)", M.F_TIT)
    y = 52
    texto(lam, (24, y), "Hoy (BatteryView): pila redondeada + porcentaje", M.F_ETQ, NARANJA)
    texto(lam, (24, y + 18), "vertical sin imagen", M.F_PEQ, GRIS)
    lam.alpha_composite(CAP_DIA.crop((380, 600, 560, 670)), (24, y + 36))
    texto(lam, (24 + 230, y + 18), "con imagen de fondo", M.F_PEQ, GRIS)
    lam.alpha_composite(CAP_IMG.crop((420, 196, 590, 250)), (24 + 230, y + 36))
    y0 = 52 + 120
    for r, (tit, sub, fn) in enumerate(opciones):
        y = y0 + r * (rowh + 44)
        texto(lam, (24, y), tit, M.F_ETQ, NARANJA)
        texto(lam, (24 + 250, y + 2), sub, M.F_PEQ, GRIS)
        k = 0
        for n in (0.0, 1.0):
            for (clave, etq, p, carg) in BATERIAS:
                x = 24 + k * colw
                texto(lam, (x, y + 20), etq + ("  · noche" if n > 0.5 else ""), M.F_PEQ, GRIS)
                fondo = celda(colw - 12, rowh - 8, n, x=20 + (k % 3) * 160)
                lam.alpha_composite(fondo, (x, y + 38))
                img = fn(p, carg, n)
                lam.alpha_composite(img.crop((0, 0, colw - 18, rowh - 10)), (x + 4, y + 38 + 4))
                k += 1
    M.guardar(lam, "maqueta-bateria")


def lamina_reloj(clima_fn, bat_fn, est="nublado"):
    """La opcion recomendada sobre la captura real: se tapan el clima y la pila viejos con el cielo de alrededor."""
    scr = CAP_DIA.copy()
    L = Lienzo(scr)
    for y in range(560, 700):
        c = scr.getpixel((20, y))
        L.rect(40, y, 580, y, c)
    # el clima de la captura: 12°, nublado, 19/12, lluvia 100 %
    INFO_EXTRA[est] = ("", "Nublado", 3, 12, 19, 12, 100)
    img = clima_fn(est, 0.0)
    INFO_EXTRA.clear()
    scr.alpha_composite(img, (60, 580))
    b = bat_fn(100, True, 0.0)
    scr.alpha_composite(b, (384, 600))
    lam = Image.new("RGBA", (24 + 600 + 24, 1024 + 76), FONDO_LAM)
    texto(lam, (24, 16), "Reloj con C2 + B1 (captura real: 12°, nublado, lluvia 100 %)", M.F_TIT)
    lam.alpha_composite(scr, (24, 56))
    M.guardar(lam, "maqueta-reloj-clima-bateria")
    return scr


if __name__ == "__main__":
    lamina_clima()
    lamina_bateria()
    lamina_reloj(lambda e, n: opcion_c2(e, n), opcion_b1, est="nublado")
