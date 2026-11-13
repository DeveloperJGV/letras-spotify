"""Maquetas (imagenes, no codigo de la app): la barra de la cancion + la bocina de silencio, y los botones de
arriba (ayuda y opciones), de la familia del tocadiscos, el volumen y la ventanita.

Uso:  python arte/controles/maquetas_barra_botones.py
Sale en docs/capturas/controles-pixel/: maqueta-barra-bocina.png y maqueta-botones-arriba.png (con su -x2).

Todo a escala real (1 px de arte = 1 px de la tablet) y montado sobre las capturas reales de despues de T1/F1
(despues-pildora-dia-sonando.png y despues-pildora-noche-sonando.png). Sin PixelLab: todo dibujado aqui, con la
paleta del cuarto; los perros salen de sus hojas (perros_hunter.png / perros_tango.png).
"""

import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas as M  # noqa: E402
from maquetas import P, Lienzo, glifo, mezcla, nuevo, noche_obj, noche, texto  # noqa: E402

SALIDA = M.SALIDA
CAP = {False: Image.open(os.path.join(SALIDA, "despues-pildora-dia-sonando.png")).convert("RGBA"),
       True: Image.open(os.path.join(SALIDA, "despues-pildora-noche-sonando.png")).convert("RGBA")}

# Donde esta cada cosa en la captura vertical (medido).
BAR_Y0, BAR_Y1 = 804, 848          # renglon de la barra (adentro de la pildora)
BAR_X0, BAR_X1 = 22, 578
SPK = (256, 736, 298, 790)          # hueco de la bocina, entre el tocadiscos y el volumen
HELP_C = (448, 34)
OPT_C = (560, 34)

BLANCO = (255, 255, 255, 255)
NEGRO = (0, 0, 0, 255)

# ---------------------------------------------------------------------------------------------------------------
# Letra en pixel para los tiempos (4 x 7)
# ---------------------------------------------------------------------------------------------------------------

DIGITOS = {
    "0": [".XX.", "X..X", "X..X", "X..X", "X..X", "X..X", ".XX."],
    "1": [".X..", "XX..", ".X..", ".X..", ".X..", ".X..", "XXX."],
    "2": [".XX.", "X..X", "...X", "..X.", ".X..", "X...", "XXXX"],
    "3": ["XXX.", "...X", "...X", ".XX.", "...X", "...X", "XXX."],
    "4": ["X..X", "X..X", "X..X", "XXXX", "...X", "...X", "...X"],
    "5": ["XXXX", "X...", "XXX.", "...X", "...X", "X..X", ".XX."],
    "6": [".XX.", "X...", "XXX.", "X..X", "X..X", "X..X", ".XX."],
    "7": ["XXXX", "...X", "..X.", "..X.", ".X..", ".X..", ".X.."],
    "8": [".XX.", "X..X", "X..X", ".XX.", "X..X", "X..X", ".XX."],
    "9": [".XX.", "X..X", "X..X", ".XXX", "...X", "...X", ".XX."],
    ":": [".", "X", ".", ".", ".", "X", "."],
}


def ancho_texto(s):
    return sum(len(DIGITOS[ch][0]) + 1 for ch in s) - 1


def escribir(L, x, y, s, c, sombra=None):
    for ch in s:
        filas = DIGITOS[ch]
        if sombra is not None:
            L.mapa(x + 1, y + 1, filas, {"X": sombra})
        L.mapa(x, y, filas, {"X": c})
        x += len(filas[0]) + 1


def letrero(s, brilla=False):
    """Letrerito de madera con el tiempo (cuando se arrastra, el de la izquierda se enciende)."""
    w = ancho_texto(s) + 8
    L = nuevo(w, 13)
    L.rect(1, 1, w - 2, 11, P["amarillo"] if brilla else P["madera"])
    L.rect(1, 1, w - 2, 1, P["crema"] if brilla else P["madera_luz"])
    L.rect(1, 10, w - 2, 11, P["oro"] if brilla else P["madera_osc"])
    L.caja(0, 0, w - 1, 12, P["contorno_mad"], esquina=True)
    L.p(2, 2, P["nogal"])
    L.p(w - 3, 2, P["nogal"])
    escribir(L, 4, 3, s, P["nogal"] if brilla else P["crema"], None if brilla else P["contorno_mad"])
    return L.im


def contorno_claro(im, c=(226, 226, 236, 210)):
    """Contorno de 1 px claro alrededor de un perro (como el que la app ya les pone de dia)."""
    a = im.split()[3]
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    px, pa = out.load(), a.load()
    w, hh = im.size
    for y in range(hh):
        for x in range(w):
            if pa[x, y] == 0 and any(0 <= x + dx < w and 0 <= y + dy < hh and pa[x + dx, y + dy] > 0
                                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = c
    out.alpha_composite(im)
    return out


def recorte(im):
    return im.crop(im.getbbox())


# ---------------------------------------------------------------------------------------------------------------
# Barra B1: el cable de foquitos
# ---------------------------------------------------------------------------------------------------------------

FOCOS = [M.h(c) for c in ("fdc83f", "35dbfb", "87d855", "fb792c", "f6ef74", "e0457b")]
W_BAR, H_BAR = BAR_X1 - BAR_X0, BAR_Y1 - BAR_Y0


def pista(n):
    """Donde corre la barra (x) en el renglon, dejando lugar a los letreros."""
    return 46, W_BAR - 46


def barra_focos(p, estado, n, t0="1:15", t1="3:32"):
    L = nuevo(W_BAR, H_BAR)
    x0, x1 = pista(n)
    tx = int(x0 + (x1 - x0) * p)
    cy = 15
    # cable con caida entre clavitos cada 24 px
    def y_cable(x):
        k = (x - x0) % 24
        return cy + int(round(2 * math.sin(math.pi * k / 24)))
    for x in range(x0, x1 + 1):
        L.p(x, y_cable(x), P["tinta"] if n < 0.5 else P["gris"])
        if (x - x0) % 24 == 0:
            L.p(x, cy - 1, P["topo"])
    for i, x in enumerate(range(x0 + 5, x1 - 3, 10)):
        if abs(x - tx) < 8:
            continue
        y = y_cable(x) + 1
        prendido = x < tx
        c = FOCOS[i % len(FOCOS)]
        L.rect(x - 1, y, x, y, P["piedra"])
        foco = [".XX.", "XXXX", "XXXX", ".XX."]
        if prendido:
            if n > 0.5 or estado != "pausa":
                for (dx, dy) in ((-3, 3), (2, 3), (-1, 6), (0, 6), (-2, 1), (1, 1)):  # brillito de 1 px (no degradado)
                    L.p(x + dx, y + dy, c[:3] + (90,))
            tono = c if estado != "pausa" else mezcla(c, NEGRO, 0.25)
            L.mapa(x - 2, y + 1, foco, {"X": tono})
            L.p(x - 1, y + 2, mezcla(tono, BLANCO, 0.6))
            L.p(x, y + 4, mezcla(tono, NEGRO, 0.3))
        else:
            L.mapa(x - 2, y + 1, foco, {"X": P["carbon"]})
            L.p(x - 1, y + 2, P["gris"])
    # el foco grande (la bolita): late con la musica, se apaga a medias en pausa, crece al arrastrar
    r = 5 if estado == "arrastrando" else 4
    by = y_cable(tx) + 2 + r
    L.rect(tx - 1, y_cable(tx), tx + 1, y_cable(tx) + 1, P["piedra"])
    cuerpo = P["amarillo"] if estado != "pausa" else P["niebla"]
    L.elipse(tx, by, r + 1, r + 1, P["oro"] if estado != "pausa" else P["piedra"])
    L.elipse(tx, by, r, r, cuerpo)
    L.p(tx - 1, by - 2, BLANCO)
    L.p(tx - 2, by - 1, BLANCO)
    if estado == "sonando":
        for (dx, dy) in ((-7, 0), (7, 0), (0, -7), (-5, -5), (5, -5), (-5, 5), (5, 5)):
            L.p(tx + dx, by + dy, P["amarillo"])
    elif estado == "arrastrando":
        for (dx, dy, ln) in ((-9, 0, 3), (7, 0, 3), (-7, -6, 2), (6, -6, 2), (-7, 6, 2), (6, 6, 2)):
            L.rect(tx + dx, by + dy, tx + dx + ln - 1, by + dy, P["crema"])
    ponerle_letreros(L, t0, t1, estado)
    return L.im if n < 0.5 else noche_foquitos(L.im)


def noche_foquitos(im):
    """De noche: el cable y los letreros se oscurecen (filtro fuerte); los focos prendidos no (son luz)."""
    oscura = noche_obj(im)
    px_o, px_i = oscura.load(), im.load()
    luces = set(FOCOS) | {P["amarillo"], BLANCO, P["crema"], P["oro"]}
    for y in range(im.height):
        for x in range(im.width):
            c = px_i[x, y]
            if c[3] == 255 and c in luces:
                px_o[x, y] = c
            elif 0 < c[3] < 255:
                px_o[x, y] = c[:3] + (min(255, c[3] + 60),)
    return oscura


def ponerle_letreros(L, t0, t1, estado):
    a = letrero(t0, brilla=estado == "arrastrando")
    b = letrero(t1)
    L.pegar(a, 4, (H_BAR - a.height) // 2 + 2)
    L.pegar(b, W_BAR - b.width - 4, (H_BAR - b.height) // 2 + 2)


# ---------------------------------------------------------------------------------------------------------------
# Barra B2: Tango en la cuerda (la bolita es Tango)
# ---------------------------------------------------------------------------------------------------------------

def barra_tango(p, estado, n, t0="1:15", t1="3:32"):
    L = nuevo(W_BAR, H_BAR)
    x0, x1 = pista(n)
    tx = int(x0 + (x1 - x0) * p)
    ry = 33
    # postes de madera en las puntas
    for xp in (x0 - 3, x1 + 1):
        L.rect(xp, ry - 6, xp + 2, ry + 3, P["madera"])
        L.rect(xp + 2, ry - 6, xp + 2, ry + 3, P["madera_osc"])
        L.caja(xp - 1, ry - 7, xp + 3, ry + 4, P["contorno_mad"], esquina=True)
    # cuerda: lo escuchado es cuerda trenzada; lo que falta, un hilito punteado
    for x in range(x0, x1 + 1):
        if x <= tx:
            L.p(x, ry, P["madera_luz"] if x % 3 else P["madera"])
            L.p(x, ry + 1, P["madera_osc"] if x % 3 != 1 else P["nogal"])
        elif x % 2 == 0:
            L.p(x, ry, P["piedra"])
    if estado == "sonando":
        d = M.perro("tango", "walk", 1)
    elif estado == "pausa":
        d = M.perro("tango", "sit", 2)
    else:
        d = M.perro("tango", "run", 2)
    d = recorte(d)
    if n > 0.5:
        d = noche(d)
    d = contorno_claro(d, (120, 170, 220, 230) if n > 0.5 else (226, 226, 236, 210))
    L.pegar(d, tx - d.width // 2, ry - d.height + 1)
    if estado == "arrastrando":
        for (xx, yy) in ((tx - 26, ry - 3), (tx - 30, ry - 6), (tx - 23, ry - 7)):
            L.rect(xx, yy, xx + 1, yy + 1, P["crema"])
    if estado == "sonando":
        glifo(L, tx + 16, ry - 30, M.G_NOTA, P["oro"], P["madera_osc"])
    ponerle_letreros(L, t0, t1, estado)
    return L.im


# ---------------------------------------------------------------------------------------------------------------
# Barra B3: el piso de madera y la pelota de Hunter
# ---------------------------------------------------------------------------------------------------------------

def pelota(L, cx, cy, cuadro):
    L.elipse(cx, cy, 4, 4, M.h("c8e04a"))
    L.elipse(cx + 1, cy + 1, 3, 3, M.h("9fbf2a"))
    L.elipse(cx - 1, cy - 1, 2, 2, M.h("d8ec66"))
    L.p(cx - 2, cy - 2, M.h("f2f7a6"))
    L.anillo(cx, cy, 4, 4, M.h("4c6a10"))
    # la costura blanca da vuelta
    for k in range(7):
        a = math.radians(cuadro * 50 + k * 26)
        L.p(int(round(cx + 3 * math.cos(a))), int(round(cy + 3 * math.sin(a) * 0.55)), BLANCO)


def barra_piso(p, estado, n, t0="1:15", t1="3:32"):
    L = nuevo(W_BAR, H_BAR)
    x0, x1 = pista(n)
    tx = int(x0 + (x1 - x0) * p)
    fy = 30
    piso = M.CUARTO[False].crop((96, 52, 128, 59))  # tablones del cuarto (de noche, con el filtro de abajo)
    for x in range(x0, x1 + 1, piso.width):
        L.im.paste(piso, (x, fy))
    L.px = L.im.load()
    L.rect(x1 + 1, fy, W_BAR - 1, fy + 6, (0, 0, 0, 0))
    # lo que falta: el piso sin luz
    L.oscurecer(tx + 1, fy, x1, fy + 6, 0.42)
    L.caja(x0 - 1, fy - 1, x1 + 1, fy + 7, P["contorno_mad"], esquina=True)
    if estado == "pausa":
        d = recorte(M.perro("hunter", "sleep", 8))
        if n > 0.5:
            d = noche(d)
        d = contorno_claro(d)
        L.pegar(d, tx - d.width - 2, fy - d.height + 1)
        pelota(L, tx, fy - 5, 0)
        L.mapa(tx - 12, fy - d.height - 6, ["XXX", "..X", ".X.", "XXX"], {"X": P["azulgris"]})
    elif estado == "arrastrando":
        d = recorte(M.perro("hunter", "run", 3))
        if n > 0.5:
            d = noche(d)
        d = contorno_claro(d)
        L.pegar(d, tx - d.width - 4, fy - d.height + 1)
        pelota(L, tx, fy - 11, 2)
        for (xx, ln) in ((tx - 8, 3), (tx - 9, 4)):
            pass
        L.rect(tx + 5, fy - 10, tx + 7, fy - 10, P["crema"])
        L.rect(tx + 5, fy - 7, tx + 8, fy - 7, P["crema"])
    else:
        pelota(L, tx, fy - 5, 1)
        L.rect(tx - 8, fy - 5, tx - 6, fy - 5, P["piedra"])
        L.rect(tx - 9, fy - 3, tx - 6, fy - 3, P["piedra"])
    ponerle_letreros(L, t0, t1, estado)
    return L.im if n < 0.5 else noche_obj(L.im)


def noche_mitad(im):
    return noche(im, 0.8)


# ---------------------------------------------------------------------------------------------------------------
# Bocinas de silencio
# ---------------------------------------------------------------------------------------------------------------

def bocina_cuarto(sonando, n, silencio=None):
    """S1: la bocina de madera del cuarto. Sonando el cono late y salen ondas; en silencio, cono quieto y zZ."""
    if silencio is None:
        silencio = not sonando
    L = nuevo(44, 56)
    x0, y0, x1, y1 = 9, 16, 30, 49
    L.rect(x0, y0, x1, y1, P["madera"])
    L.rect(x0, y0, x1, y0, P["madera_luz"])
    L.rect(x1, y0 + 1, x1, y1, P["madera_osc"])
    L.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, P["nogal"])
    L.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P["contorno_mad"], esquina=True)
    L.rect(x0 + 1, y1 + 2, x0 + 3, y1 + 3, P["contorno_mad"])
    L.rect(x1 - 3, y1 + 2, x1 - 1, y1 + 3, P["contorno_mad"])
    cx = (x0 + x1) // 2
    ty = y0 + 7
    L.elipse(cx, ty, 3, 3, P["perla"])
    L.elipse(cx, ty, 2, 2, P["carbon"])
    L.p(cx - 1, ty - 1, P["hueso"])
    cy = y0 + 21
    r = 8 if sonando else 7
    L.elipse(cx, cy, r, r, P["perla"])
    L.elipse(cx, cy, r - 1, r - 1, P["tinta"])
    L.anillo(cx, cy, r - 3, r - 3, P["gris"])
    L.elipse(cx, cy, 2, 2, P["topo"])
    L.p(cx - 1, cy - 1, P["piedra"])
    L.p(cx - 3, cy - 4, P["gris"])
    if sonando:
        for rr in (4, 8):
            L.arco(x1 + 2, cy, rr, rr + 2, -55, 55, P["crema"], paso=7)
        glifo(L, x1 - 2, 2, M.G_NOTA2, P["amarillo"], P["madera_osc"])
    elif silencio:
        L.mapa(x1 - 6, 8, ["XXXX", "...X", "..X.", ".X..", "XXXX"], {"X": P["azulgris"]})
        L.mapa(x1 + 1, 1, ["XXXXX", "....X", "...X.", "..X..", ".X...", "XXXXX"], {"X": P["lila"]})
        # el cono apagado: una rayita de cinta
        L.linea(cx - 6, cy + 5, cx + 6, cy - 5, P["hueso"])
        L.linea(cx - 6, cy + 6, cx + 6, cy - 4, P["niebla"])
    return L.im if n < 0.5 else noche_obj(L.im)


def bocina_corneta(sonando, n, silencio=None):
    """S2: la corneta de gramofono. En silencio le meten un calcetin rojo (asi se callaban de verdad)."""
    if silencio is None:
        silencio = not sonando
    L = nuevo(44, 56)
    # base de madera
    L.rect(4, 45, 22, 52, P["madera"])
    L.rect(4, 45, 22, 45, P["madera_luz"])
    L.rect(4, 51, 22, 52, P["madera_osc"])
    L.caja(3, 44, 23, 53, P["contorno_mad"], esquina=True)
    L.elipse(13, 48, 2, 1, P["oro"])
    # cuello
    L.linea(12, 43, 16, 36, P["tinta"])
    L.linea(13, 43, 17, 36, P["oro"])
    L.linea(14, 43, 18, 36, M.h("8a4b12"))
    # la corneta: circulos que crecen a lo largo del eje
    ax0, ay0, ax1, ay1 = 17, 36, 28, 17
    pasos = 30
    for capa, extra in ((M.h("5a2a0a"), 1), (P["oro"], 0)):
        for k in range(pasos + 1):
            t = k / pasos
            x = int(round(ax0 + (ax1 - ax0) * t))
            y = int(round(ay0 + (ay1 - ay0) * t))
            r = 1 + int(7 * t * t)
            L.elipse(x, y, r + extra, r + extra, capa)
    for k in range(4, pasos, 3):  # brillo del laton
        t = k / pasos
        r = 1 + int(7 * t * t)
        x = int(round(ax0 + (ax1 - ax0) * t)) - r // 2
        y = int(round(ay0 + (ay1 - ay0) * t)) - r // 2
        L.p(x, y, P["amarillo"])
    # la boca
    L.elipse(ax1, ay1, 9, 9, M.h("5a2a0a"))
    L.elipse(ax1, ay1, 8, 8, P["amarillo"])
    L.elipse(ax1, ay1, 6, 6, M.h("6a3410"))
    L.elipse(ax1 + 1, ay1 - 1, 3, 3, P["tinta"])
    if sonando:
        glifo(L, 37, 2, M.G_NOTA, P["crema"], P["madera_osc"])
        glifo(L, 30, 0, M.G_NOTA2, P["amarillo"], P["madera_osc"])
    elif silencio:
        # el calcetin: el puno metido en la boca y la pata colgando por fuera
        L.elipse(ax1, ay1, 6, 6, ROJO_CALCETIN[1])
        L.elipse(ax1 - 1, ay1 - 1, 4, 4, ROJO_CALCETIN[2])
        L.rect(ax1 - 6, ay1 + 1, ax1 + 6, ay1 + 2, P["crema"])
        L.rect(ax1 + 4, ay1 + 3, ax1 + 8, ay1 + 14, ROJO_CALCETIN[1])
        L.rect(ax1 + 4, ay1 + 3, ax1 + 4, ay1 + 14, ROJO_CALCETIN[2])
        L.rect(ax1 + 4, ay1 + 8, ax1 + 8, ay1 + 9, P["crema"])
        L.rect(ax1 + 1, ay1 + 13, ax1 + 8, ay1 + 16, ROJO_CALCETIN[1])
        L.rect(ax1 + 1, ay1 + 16, ax1 + 7, ay1 + 16, ROJO_CALCETIN[0])
        L.caja(ax1 + 3, ay1 + 2, ax1 + 9, ay1 + 17, ROJO_CALCETIN[0])
    return L.im if n < 0.5 else noche_obj(L.im)


ROJO_CALCETIN = [M.ROJO[1], M.ROJO[3], M.ROJO[4]]


# ---------------------------------------------------------------------------------------------------------------
# Botones de arriba
# ---------------------------------------------------------------------------------------------------------------

G_PREGUNTA = [
    "..XXXXXX..",
    ".XXXXXXXX.",
    "XXX....XXX",
    "XXX....XXX",
    "......XXX.",
    ".....XXX..",
    "....XXX...",
    "....XXX...",
    "..........",
    "....XXX...",
    "....XXX...",
]


G_PREGUNTA_G = [
    "...XXXXXX...",
    "..XXXXXXXX..",
    ".XXX....XXX.",
    ".XXX....XXX.",
    "........XXX.",
    ".......XXX..",
    "......XXX...",
    ".....XXX....",
    ".....XXX....",
    "............",
    ".....XXX....",
    ".....XXX....",
]


def sesgar(im, k, cx):
    """Inclina (y' = y + k (x - cx)) sin antialias: el letrero que se columpia."""
    return im.transform(im.size, Image.AFFINE, (1, 0, 0, -k, 1, k * cx), resample=Image.NEAREST)


def letrero_ayuda(tocado, n, lado=4):
    """A1 ayuda: letrero de madera colgado de un clavo con un ? tallado. Al tocarlo se columpia."""
    W, H = 44, 48
    tabla = nuevo(W, H)
    x0, y0, x1, y1 = 4, 15, 39, 45
    tabla.rect(x0, y0, x1, y1, P["madera"])
    tabla.rect(x0, y0, x1, y0, P["madera_luz"])
    tabla.rect(x0, y1 - 1, x1, y1, P["madera_osc"])
    for yy in (y0 + 7, y0 + 18, y0 + 25):  # vetas
        for xx in range(x0 + 2, x1 - 1, 5):
            tabla.p(xx, yy, P["madera_med"])
            tabla.p(xx + 1, yy, P["madera_med"])
    tabla.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P["contorno_mad"], esquina=True)
    tabla.p(x0 + 2, y0 + 2, P["nogal"])
    tabla.p(x1 - 2, y0 + 2, P["nogal"])
    gx = (x0 + x1) // 2 - 6
    glifo(tabla, gx, y0 + 9, G_PREGUNTA_G, P["crema"], P["nogal"])
    L = nuevo(W, H)
    # al tocarlo se columpia: la tabla se va de lado y sube un poquito (los hilos se inclinan)
    dx, dy = (lado, -1 if lado else 0) if tocado else (0, 0)
    L.linea(W // 2, 3, x0 + 2 + dx, y0 - 1 + dy, P["tinta"])
    L.linea(W // 2, 3, x1 - 2 + dx, y0 - 1 + dy + (2 if tocado and dx > 0 else 0), P["tinta"])
    sombra = tabla.im.copy()
    if tocado:
        tabla = Lienzo(tabla.im.transform(tabla.im.size, Image.AFFINE, (1, 0, -dx, 0, 1, -dy), resample=Image.NEAREST))
    L.im.alpha_composite(tabla.im)
    L.px = L.im.load()
    L.rect(W // 2 - 1, 1, W // 2 + 1, 3, P["perla"])
    L.p(W // 2 - 1, 1, BLANCO)
    if tocado and dx > 0:
        for (xx, yy) in ((1, 22), (1, 26), (2, 30)):  # rayitas de "se mece"
            L.p(xx, yy, P["crema"])
            L.p(xx, yy + 1, P["crema"])
    return L.im if n < 0.5 else noche_obj(L.im)


def comoda_opciones(tocado, n):
    """A1 opciones: comodita de 3 cajones; sus 3 perillas son el ⋮. Al tocarla se abre el de en medio (y adentro
    esta el hueso de Tango)."""
    W, H = 44, 48
    L = nuevo(W, H)
    # maceta chiquita encima
    L.rect(29, 4, 33, 8, P["teja"])
    L.caja(28, 4, 34, 9, P["vino"], esquina=True)
    L.mapa(27, 0, [".X..X.", "XX.XX.", ".XXX..", "..X..."], {"X": P["verde"]})
    # cuerpo
    x0, y0, x1, y1 = 6, 10, 37, 44
    L.rect(x0 - 2, y0 - 1, x1 + 2, y0 + 1, P["madera_luz"])
    L.caja(x0 - 3, y0 - 2, x1 + 3, y0 + 2, P["contorno_mad"], esquina=True)
    L.rect(x0, y0 + 3, x1, y1, P["madera_osc"])
    L.caja(x0 - 1, y0 + 2, x1 + 1, y1 + 1, P["contorno_mad"])
    for xp in (x0, x1 - 2):
        L.rect(xp, y1 + 2, xp + 2, y1 + 3, P["contorno_mad"])
    cajones = [(y0 + 4, y0 + 13), (y0 + 15, y0 + 24), (y0 + 26, y1 - 1)]
    for i, (a, b) in enumerate(cajones):
        abierto = tocado and i == 1
        if abierto:
            # el hueco oscuro, el hueso de Tango adentro y el cajon salido hacia el frente (mas ancho y mas abajo)
            L.rect(x0 + 1, a - 1, x1 - 1, a + 4, P["negro"])
            L.mapa(x0 + 9, a, ["XX.......XX", "XXXXXXXXXXX", "XX.......XX"], {"X": P["crema"]})
            L.p(x0 + 9, a, P["hueso"])
            a2, b2 = a + 5, b + 5
            L.rect(x0 - 1, a2, x1 + 1, b2, P["madera"])
            L.rect(x0 - 1, a2, x1 + 1, a2, P["madera_luz"])
            L.rect(x0 - 1, b2, x1 + 1, b2, P["madera_osc"])
            L.caja(x0 - 2, a2 - 1, x1 + 2, b2 + 1, P["contorno_mad"], esquina=True)
            ky = (a2 + b2) // 2
        else:
            L.rect(x0 + 1, a, x1 - 1, b, P["madera"])
            L.rect(x0 + 1, a, x1 - 1, a, P["madera_luz"])
            L.rect(x0 + 1, b, x1 - 1, b, P["madera_med"])
            L.caja(x0, a - 1, x1, b + 1, P["contorno_mad"])
            ky = (a + b) // 2
        kx = (x0 + x1) // 2
        L.elipse(kx, ky, 2, 2, P["oro"])
        L.p(kx - 1, ky - 1, P["amarillo"])
        L.p(kx + 1, ky + 1, M.h("8a4b12"))
    return L.im if n < 0.5 else noche_obj(L.im)


def libreta_ayuda(tocado, n):
    """A2 ayuda: libreta de espiral con un ? en la portada. Al tocarla se levanta la esquina (pasa la hoja)."""
    W, H = 44, 48
    L = nuevo(W, H)
    x0, y0, x1, y1 = 7, 6, 36, 45
    L.rect(x0 + 2, y0 + 2, x1 + 2, y1 + 2, (0, 0, 0, 90))
    L.rect(x0, y0, x1, y1, P["crema"])  # hojas
    L.rect(x0, y0, x1 - 1, y1 - 1, P["teal"])
    L.rect(x0, y0, x1 - 1, y0, P["teal_luz"])
    L.rect(x0, y1 - 2, x1 - 1, y1 - 1, P["teal_osc"])
    L.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P["tinta"], esquina=True)
    for xx in range(x0 + 2, x1 - 1, 4):  # espiral
        L.rect(xx, y0 - 3, xx + 1, y0 + 1, P["perla"])
        L.p(xx, y0 - 3, BLANCO)
    L.rect(x0 + 4, y0 + 5, x1 - 5, y0 + 9, P["crema"])  # etiqueta
    L.rect(x0 + 6, y0 + 7, x1 - 7, y0 + 7, P["piedra"])
    glifo(L, (x0 + x1) // 2 - 6, y0 + 15, G_PREGUNTA_G, P["crema"], P["teal_osc"])
    if tocado:
        # esquina de abajo a la derecha levantada: se ve la hoja de adentro
        for k in range(12):
            for j in range(12 - k):
                L.p(x1 - j, y1 - k, P["crema"] if j > 0 else P["piedra"])
        L.linea(x1 - 12, y1, x1, y1 - 12, P["tinta"])
        for k in range(1, 4):
            L.rect(x1 - 9 + k * 2, y1 - 3, x1 - 9 + k * 2, y1 - 3, P["piedra"])
    return L.im if n < 0.5 else noche_obj(L.im)


def caja_opciones(tocado, n):
    """A2 opciones: caja de herramientas roja; al tocarla se abre la tapa y asoma la llave."""
    W, H = 44, 48
    L = nuevo(W, H)
    x0, y0, x1, y1 = 4, 22, 39, 44
    L.rect(x0, y0, x1, y1, M.ROJO[3])
    L.rect(x0, y0, x1, y0 + 1, M.ROJO[4])
    L.rect(x0, y1 - 2, x1, y1, M.ROJO[2])
    L.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, M.ROJO[0], esquina=True)
    for i, yy in enumerate((y0 + 5, y0 + 10, y0 + 15)):  # tres tornillos (el ⋮)
        L.elipse((x0 + x1) // 2, yy, 1, 1, P["hueso"])
        L.p((x0 + x1) // 2, yy, P["topo"])
    if tocado:
        # tapa abierta hacia atras y la llave asomando
        L.rect(x0 + 2, y0 - 12, x1 - 2, y0 - 2, M.ROJO[2])
        L.rect(x0 + 2, y0 - 12, x1 - 2, y0 - 11, M.ROJO[3])
        L.caja(x0 + 1, y0 - 13, x1 - 1, y0 - 1, M.ROJO[0], esquina=True)
        L.rect(x0 + 1, y0, x1 - 1, y0 + 1, P["negro"])
        L.rect(x0 + 8, y0 - 18, x0 + 9, y0 + 1, P["perla"])
        L.mapa(x0 + 6, y0 - 22, ["X..X", "X..X", "XXXX", ".XX."], {"X": P["perla"]})
    else:
        L.rect(x0 + 1, y0 - 5, x1 - 1, y0 - 1, M.ROJO[3])
        L.rect(x0 + 1, y0 - 5, x1 - 1, y0 - 5, M.ROJO[4])
        L.caja(x0, y0 - 6, x1, y0, M.ROJO[0], esquina=True)
        # asa
        L.caja(x0 + 11, y0 - 11, x1 - 11, y0 - 6, P["tinta"])
        L.rect(x0 + 12, y0 - 10, x1 - 12, y0 - 10, P["perla"])
    return L.im if n < 0.5 else noche_obj(L.im)


def pizarra_ayuda(tocado, n):
    """A3 ayuda: pizarroncito verde con un ? de gis; al tocarlo el gis se reescribe (y queda polvito)."""
    W, H = 44, 48
    L = nuevo(W, H)
    x0, y0, x1, y1 = 3, 6, 40, 42
    L.rect(x0, y0, x1, y1, P["madera"])
    L.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P["contorno_mad"], esquina=True)
    L.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, M.h("244a3a"))
    for y in range(y0 + 3, y1 - 2):
        for x in range(x0 + 3, x1 - 2):
            if (x * 7 + y * 3) % 11 == 0:
                L.p(x, y, M.h("2c5646"))
    g = [f.replace("X", "C") for f in G_PREGUNTA]
    for j, f in enumerate(g):
        for i, ch in enumerate(f):
            if ch == "C" and (i + j) % 5 != 0:
                L.p((x0 + x1) // 2 - 5 + i, y0 + 8 + j, P["humo"] if (i * j) % 3 else P["niebla"])
    L.rect(x0 + 4, y1 - 2, x0 + 8, y1 - 1, P["crema"])  # el gis en la repisa
    L.rect(x0 + 2, y1 - 1, x1 - 2, y1 + 2, P["madera_osc"])
    L.caja(x0 + 1, y1 - 2, x1 - 1, y1 + 3, P["contorno_mad"])
    if tocado:
        for (xx, yy) in ((30, 30), (31, 32), (29, 34), (33, 33)):
            L.p(xx, yy, P["humo"])
    return L.im if n < 0.5 else noche_obj(L.im)


def perchero_opciones(tocado, n):
    """A3 opciones: tabla de pared con tres perillas (el ⋮) y la correa de Hunter colgada; al tocarla se mece."""
    W, H = 44, 48
    L = nuevo(W, H)
    x0, y0, x1, y1 = 15, 2, 28, 46
    L.rect(x0, y0, x1, y1, P["madera"])
    L.rect(x0, y0, x0, y1, P["madera_luz"])
    L.rect(x1, y0, x1, y1, P["madera_osc"])
    L.caja(x0 - 1, y0 - 1, x1 + 1, y1 + 1, P["contorno_mad"], esquina=True)
    cx = (x0 + x1) // 2
    for yy in (10, 23, 36):
        L.elipse(cx, yy, 3, 3, P["oro"])
        L.elipse(cx, yy, 2, 2, P["amarillo"])
        L.p(cx - 1, yy - 1, BLANCO)
        L.anillo(cx, yy, 3, 3, M.h("8a4b12"))
    # correa roja de la perilla de arriba, con su mosqueton
    dx = 4 if tocado else 0
    L.linea(cx + 3, 11, cx + 6 + dx, 30, ROJO_CALCETIN[1])
    L.linea(cx + 4, 11, cx + 7 + dx, 30, ROJO_CALCETIN[2])
    L.caja(cx + 4 + dx, 30, cx + 9 + dx, 35, P["perla"])
    if tocado:
        for (xx, yy) in ((cx + 13, 26), (cx + 14, 30)):
            L.p(xx, yy, P["crema"])
    return L.im if n < 0.5 else noche_obj(L.im)


# ---------------------------------------------------------------------------------------------------------------
# Montaje sobre las capturas
# ---------------------------------------------------------------------------------------------------------------

def tapar_renglon(scr, x0, y0, x1, y1, col=24):
    """Borra lo que hay en un renglon de la pildora con su propio color (muestreado fila por fila en x=col)."""
    L = Lienzo(scr)
    for y in range(y0, y1 + 1):
        c = scr.getpixel((col, y))
        c2 = scr.getpixel((scr.width - col, y))
        L.rect(x0, y, x1, y, tuple((a + b) // 2 for a, b in zip(c, c2)))


def pildora(n, barra=None, bocina=None):
    scr = CAP[n > 0.5].copy()
    if barra is not None:
        tapar_renglon(scr, BAR_X0, BAR_Y0, BAR_X1, BAR_Y1)
        scr.alpha_composite(barra, (BAR_X0, BAR_Y0))
    if bocina is not None:
        L = Lienzo(scr)
        # solo se borra el icono viejo (lo demas lo tapa la bocina nueva)
        for y in range(750, 776):
            L.rect(267, y, 291, y, scr.getpixel((294, y)))
        x = (SPK[0] + SPK[2]) // 2 - bocina.width // 2
        y = (SPK[1] + SPK[3]) // 2 - bocina.height // 2
        scr.alpha_composite(bocina, (x, y))
    return scr


def arriba(n, ayuda=None, opciones=None):
    scr = CAP[n > 0.5].copy()
    L = Lienzo(scr)
    for y in range(4, 66):
        c = scr.getpixel((380, y))
        if ayuda is not None:
            L.rect(HELP_C[0] - 25, y, HELP_C[0] + 25, y, c)
        if opciones is not None:
            L.rect(OPT_C[0] - 25, y, OPT_C[0] + 25, y, c)
    if ayuda is not None:
        scr.alpha_composite(ayuda, (HELP_C[0] - ayuda.width // 2, HELP_C[1] - ayuda.height // 2 + 2))
    if opciones is not None:
        scr.alpha_composite(opciones, (OPT_C[0] - opciones.width // 2, OPT_C[1] - opciones.height // 2 + 2))
    return scr


FONDO = M.h("1b1c24")
GRIS = (170, 172, 185, 255)
DORADO = (250, 200, 120, 255)

ESTADOS_BARRA = [("sonando", 0.38, "1:15"), ("pausa", 0.38, "1:15"), ("arrastrando", 0.66, "2:19")]
OPC_BARRA = [("B1  Cable de foquitos", barra_focos), ("B2  Tango en la cuerda", barra_tango),
             ("B3  El piso y la pelota de Hunter", barra_piso)]


def lamina_barra():
    cw = 568 + 16
    recorte_pildora = (16, 700, 584, 860)
    ph = recorte_pildora[3] - recorte_pildora[1]
    W = 24 + 3 * cw
    filas = 1 + len(OPC_BARRA) + 1 + 2
    H = 60 + filas * (ph + 50) + 60
    W = max(W, 24 + 8 * (400 - 170 + 16))
    lam = Image.new("RGBA", (W, H), FONDO)
    texto(lam, (24, 14), "Barra de la canción + bocina de silencio  (escala real, sobre la captura de la píldora en vertical)", M.F_TIT)
    y = 56
    # hoy
    texto(lam, (24, y), "Hoy", M.F_ETQ, DORADO)
    lam.alpha_composite(CAP[False].crop(recorte_pildora), (24, y + 20))
    lam.alpha_composite(CAP[True].crop(recorte_pildora), (24 + cw, y + 20))
    texto(lam, (24 + 2 * cw, y + 30), "SeekBar blanca con bolita, tiempos en texto y la bocina de Material.", M.F_PEQ, GRIS)
    texto(lam, (24 + 2 * cw, y + 48), "Zona de toque de la barra: todo el renglón (44 px de alto), aunque el dibujo sea fino.", M.F_PEQ, GRIS)
    y += ph + 34
    bocinas = [bocina_cuarto, bocina_corneta, bocina_cuarto]
    for k, (nombre, fn) in enumerate(OPC_BARRA):
        texto(lam, (24, y), nombre + ("   (con S1, la bocina del cuarto)" if k != 1 else "   (con S2, la corneta)"), M.F_ETQ)
        for c, (est, p, t0) in enumerate(ESTADOS_BARRA):
            b = fn(p, est, 0.0, t0=t0)
            boc = bocinas[k](est != "pausa", 0.0, silencio=False)
            lam.alpha_composite(pildora(0.0, b, boc).crop(recorte_pildora), (24 + c * cw, y + 20))
            texto(lam, (24 + c * cw + 4, y + 20 + ph + 1), est, M.F_PEQ, GRIS)
        y += ph + 50
    # noche: las tres barras sonando
    texto(lam, (24, y), "De noche (sonando)", M.F_ETQ, DORADO)
    for c, (nombre, fn) in enumerate(OPC_BARRA):
        b = fn(0.38, "sonando", 1.0)
        boc = bocinas[c](True, 1.0)
        lam.alpha_composite(pildora(1.0, b, boc).crop(recorte_pildora), (24 + c * cw, y + 20))
        texto(lam, (24 + c * cw + 4, y + 20 + ph + 1), nombre.split("  ")[0], M.F_PEQ, GRIS)
    y += ph + 50
    # bocinas: sonando y en silencio, de dia y de noche, en su lugar
    texto(lam, (24, y), "Bocina de silencio:  S1 la bocina del cuarto (en silencio, zZ)  ·  S2 la corneta (en silencio, le meten un calcetín)", M.F_ETQ)
    rb = (170, 712, 400, 808)
    bw = rb[2] - rb[0] + 16
    xs = 24
    for nn in (0.0, 1.0):
        for fn, nom in ((bocina_cuarto, "S1"), (bocina_corneta, "S2")):
            for son in (True, False):
                lam.alpha_composite(pildora(nn, None, fn(son, nn)).crop(rb), (xs, y + 20))
                texto(lam, (xs + 2, y + 20 + rb[3] - rb[1] + 2), f"{nom} {'sonando' if son else 'silencio'} {'noche' if nn else 'día'}", M.F_PEQ, GRIS)
                xs += bw
    y += (rb[3] - rb[1]) + 50
    lam = lam.crop((0, 0, max(W, xs + 10), y))
    M.guardar(lam, "maqueta-barra-bocina")


OPC_BOTONES = [("A1  Letrero de madera + comodita (sus 3 perillas son los tres puntitos)", letrero_ayuda, comoda_opciones),
               ("A2  Libreta + caja de herramientas", libreta_ayuda, caja_opciones),
               ("A3  Pizarrón + perchero con la correa de Hunter", pizarra_ayuda, perchero_opciones)]


def lamina_botones():
    rc = (330, 0, 600, 72)
    cw = rc[2] - rc[0] + 16
    rh = rc[3] - rc[1]
    W = 24 + 4 * cw
    H = 60 + (1 + len(OPC_BOTONES)) * (rh + 40) + 20
    lam = Image.new("RGBA", (W, H), FONDO)
    texto(lam, (24, 14), "Botones de arriba: ayuda y opciones, junto a la ventanita  (escala real)", M.F_TIT)
    y = 56
    texto(lam, (24, y), "Hoy", M.F_ETQ, DORADO)
    lam.alpha_composite(CAP[False].crop(rc), (24, y + 20))
    lam.alpha_composite(CAP[True].crop(rc), (24 + cw, y + 20))
    y += rh + 40
    for nombre, fa, fo in OPC_BOTONES:
        texto(lam, (24, y), nombre, M.F_ETQ)
        vistas = [(0.0, False, "día"), (0.0, True, "día, al tocarlos"), (1.0, False, "noche"), (1.0, True, "noche, al tocarlos")]
        for c, (nn, toc, etq) in enumerate(vistas):
            scr = arriba(nn, fa(toc, nn), fo(toc, nn))
            lam.alpha_composite(scr.crop(rc), (24 + c * cw, y + 20))
            texto(lam, (24 + c * cw + 2, y + 20 + rh), etq, M.F_PEQ, GRIS)
        y += rh + 40
    M.guardar(lam.crop((0, 0, W, y)), "maqueta-botones-arriba")


if __name__ == "__main__":
    lamina_barra()
    lamina_botones()
