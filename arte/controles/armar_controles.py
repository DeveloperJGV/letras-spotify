"""Arma los sprites de los controles en pixel art que usa la app (T1 "El tocadiscos" y F1 "Postales").

Uso:  python arte/controles/armar_controles.py

Salen en android/app/src/main/res/drawable-nodpi/ (tocadiscos_*, postal_*, ventana_*) y la posicion de cada pieza
del tocadiscos (en pixeles de la escena de 200 x 64) en res/raw/tocadiscos.json. Todo a escala 1: en la tablet
(densidad 1.0) 1 px de arte = 1 px de pantalla. De noche la app les pone LightingColorFilter(0x6E73AA, 0x000012)
(lo mismo que noche_obj de maquetas.py).

El dibujo es el de las maquetas (maquetas.py), con el frente del tocadiscos mas alto para que el glifo de
play/pausa mida 11 px (en la maqueta media 7 y se leia poco).
"""

import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas as M  # noqa: E402
from maquetas import P, Lienzo, glifo, mezcla, nuevo  # noqa: E402

DRAW = os.path.join(M.RES, "drawable-nodpi")
RAW = os.path.join(M.RES, "raw")
W, H = 200, 64

posiciones = {}


def guardar(im, nombre, pos=None):
    im.save(os.path.join(DRAW, nombre + ".png"))
    if pos is not None:
        posiciones[nombre] = list(pos)
    print("ok", nombre, im.size, pos if pos is not None else "")


def recortar(im):
    """Recorta a lo que tiene dibujo; regresa (imagen, (x, y) donde va en la escena)."""
    caja = im.getbbox()
    return im.crop(caja), caja[:2]


# ---- glifos grandes (11 px) para la tecla del frente
G_PLAY11 = ["X......", "XX.....", "XXX....", "XXXX...", "XXXXX..", "XXXXXX.", "XXXXX..", "XXXX...", "XXX....", "XX.....", "X......"]
G_PLAY11 = [f[:6] for f in G_PLAY11]
G_PAUSA11 = ["XXX...XXX"] * 11

# ---- geometria (escena 200 x 64)
TX0, TX1, TY0, TY1, FY1 = 73, 126, 8, 24, 39       # tocadiscos: cara de arriba y frente
KX0, KX1, KY0, KY1 = 89, 109, 26, 38              # tecla de play/pausa (adentro del frente)
DCX, DCY = 95, 16                                 # centro del disco
MX0, MX1, MY = 68, 131, 42                        # mesita: de x0 a x1, cubierta desde y


def mueble():
    """Mesita, repisa con discos, tocadiscos con plato vacio, tecla (sin glifo), foco apagado y perilla."""
    L = nuevo(W, H)
    sombra = (0, 0, 0, 110)
    for x in range(MX0 + 1, MX1 + 2):
        L.p(x, 59, sombra)
        if x % 2:
            L.p(x, 60, sombra)
    for xp in (75, 123):
        L.rect(xp, MY + 5, xp + 1, 55, P["nogal"])
    L.rect(74, 54, 125, 55, P["madera_osc"])
    L.rect(74, 56, 125, 56, P["nogal"])
    lomos = [P["naranja"], P["crema"], P["teal"], P["tinta"], P["amarillo"], P["vino"], P["lila"], P["crema"],
             P["verde"], P["tinta"], P["teja"], P["cian"], P["carbon"], P["durazno"]]
    for i, xx in enumerate(range(80, 80 + len(lomos) * 2, 2)):
        alto = 5 if i % 3 else 4
        L.rect(xx, 54 - alto, xx, 53, lomos[i])
        L.p(xx, 54 - alto, mezcla(lomos[i], (255, 255, 255, 255), 0.35))
        L.rect(xx + 1, 54 - alto + 1, xx + 1, 53, mezcla(lomos[i], (0, 0, 0, 255), 0.4))
    for xp in (70, 127):
        L.rect(xp, MY + 5, xp + 2, 58, P["madera_med"])
        L.rect(xp + 2, MY + 5, xp + 2, 58, P["madera_osc"])
        L.p(xp, MY + 5, P["madera"])
        L.caja(xp - 1, MY + 4, xp + 3, 59, P["contorno_mad"])
    L.rect(MX0, MY, MX1, MY + 3, P["madera"])
    L.rect(MX0 + 1, MY, MX1 - 1, MY, P["madera_luz"])
    L.rect(MX0, MY + 2, MX1, MY + 3, P["madera_osc"])
    L.caja(MX0 - 1, MY - 1, MX1 + 1, MY + 4, P["contorno_mad"], esquina=True)
    for xx in range(MX0 + 3, MX1 - 2, 9):
        L.p(xx, MY + 1, P["madera_med"])
        L.p(xx + 1, MY + 1, P["madera_med"])
    # tocadiscos
    L.rect(TX0, TY0, TX1, TY1, P["hueso"])
    L.rect(TX0 + 1, TY0, TX1 - 1, TY0, P["humo"])
    L.rect(TX0, TY1, TX1, TY1, P["perla"])
    L.rect(TX0, TY1 + 1, TX1, FY1, P["madera_osc"])
    L.rect(TX0, TY1 + 1, TX1, TY1 + 1, P["madera_med"])
    L.rect(TX0, FY1, TX1, FY1, P["nogal"])
    for xx in range(TX0 + 2, TX1 - 1, 7):  # veta del frente
        L.p(xx, 33, P["madera_med"])
        L.p(xx + 1, 33, P["madera_med"])
    L.caja(TX0 - 1, TY0 - 1, TX1 + 1, FY1 + 1, P["tinta"], esquina=True)
    L.rect(TX0 + 2, FY1 + 1, TX0 + 4, FY1 + 1, P["tinta"])
    L.rect(TX1 - 4, FY1 + 1, TX1 - 2, FY1 + 1, P["tinta"])
    # la tecla (el glifo lo pone la app)
    L.rect(KX0, KY0, KX1, KY1, P["nogal"])
    L.rect(KX0, KY0, KX1, KY0, P["cafe"])
    L.caja(KX0 - 1, KY0 - 1, KX1 + 1, KY1 + 1, P["contorno_mad"], esquina=True)
    # foco (apagado: la app lo prende) y perilla
    L.rect(78, 31, 80, 33, P["tinta"])
    L.p(79, 32, P["gris"])
    L.elipse(119, 32, 2, 2, P["niebla"])
    L.p(118, 31, P["crema"])
    L.p(120, 33, P["topo"])
    # plato vacio
    L.elipse(DCX, DCY + 1, 20, 6, P["topo"])
    L.elipse(DCX, DCY, 20, 6, P["perla"])
    L.elipse(DCX, DCY, 17, 5, P["piedra"])
    L.anillo(DCX, DCY, 12, 3, P["perla"])
    L.p(DCX, DCY, P["tinta"])
    L.p(DCX, DCY - 1, P["niebla"])
    # pivote del brazo y su descanso
    L.elipse(121, 13, 3, 2, P["gris"])
    L.elipse(121, 12, 2, 1, P["niebla"])
    L.rect(124, 19, 125, 21, P["topo"])
    return L.im


def disco(cuadro):
    """Disco negro de 3/4 sin etiqueta (la etiqueta va aparte, del color de la portada)."""
    L = nuevo(43, 15)
    cx, cy, a, b = 21, 7, 19, 6
    L.elipse(cx, cy, a, b, P["negro"])
    L.anillo(cx, cy, a, b, M.h("120d0e"))
    L.anillo(cx, cy, a - 5, b - 2, P["tinta"])
    L.anillo(cx, cy, a - 9, b - 3, P["tinta"])
    g = cuadro * 45
    L.arco(cx, cy, a - 3, b - 1, g, g + 40, P["topo"], paso=5)
    L.arco(cx, cy, a - 3, b - 1, g + 180, g + 220, P["gris"], paso=5)
    return L.im


def etiqueta(cuadro):
    """Etiqueta blanca (la app la multiplica por el color de la portada) con una marca que da vuelta."""
    L = nuevo(11, 5)
    L.elipse(5, 2, 5, 2, (255, 255, 255, 255))
    r = math.radians(cuadro * 45 + 90)
    L.p(int(round(5 + 3 * math.cos(r))), int(round(2 + 1.2 * math.sin(r))), (150, 150, 150, 255))
    L.p(5, 2, (215, 215, 215, 255))
    return L.im


def brazo(arriba):
    L = nuevo(W, H)
    if not arriba:
        L.linea(121, 12, 111, 17, P["blanco"])
        L.linea(121, 13, 111, 18, P["perla"])
        L.rect(108, 17, 110, 18, P["carbon"])
        L.p(108, 19, P["tinta"])
    else:
        L.linea(120, 10, 113, 4, P["tinta"])
        L.linea(121, 11, 114, 5, P["blanco"])
        L.linea(122, 11, 115, 5, P["perla"])
        L.linea(123, 11, 116, 5, P["tinta"])
        L.rect(110, 3, 113, 5, P["carbon"])
        L.caja(109, 2, 114, 6, P["tinta"], esquina=True)
        L.p(110, 7, P["tinta"])
        for xx in range(108, 116):  # sombra en el plato
            L.p(xx, 15 + (xx - 108) // 3, (0, 0, 0, 90))
    return recortar(L.im)


def tecla_lateral(filas, hundida):
    L = nuevo(33, 36)
    M.tecla(L, 0, 0, 31, 26, 7, hundida, filas)
    return L.im


def glifo_sprite(filas):
    L = nuevo(len(filas[0]), len(filas) + 1)
    glifo(L, 0, 0, filas, P["crema"], P["contorno_mad"])
    return L.im


def nota(filas, c, sombra):
    L = nuevo(len(filas[0]), len(filas) + 1)
    glifo(L, 0, 0, filas, c, sombra)
    return L.im


def tocadiscos():
    guardar(M.pared(W, 0.0), "tocadiscos_pared_dia", (0, 0))
    guardar(M.pared(W, 1.0), "tocadiscos_pared_noche", (0, 0))
    guardar(mueble(), "tocadiscos_mueble", (0, 0))
    tira = Image.new("RGBA", (43 * 4, 15), (0, 0, 0, 0))
    for k in range(4):
        tira.alpha_composite(disco(k), (43 * k, 0))
    guardar(tira, "tocadiscos_disco", (DCX - 21, DCY - 7))
    tira = Image.new("RGBA", (11 * 4, 5), (0, 0, 0, 0))
    for k in range(4):
        tira.alpha_composite(etiqueta(k), (11 * k, 0))
    guardar(tira, "tocadiscos_etiqueta", (DCX - 5, DCY - 2))
    im, pos = brazo(False)
    guardar(im, "tocadiscos_brazo_abajo", pos)
    im, pos = brazo(True)
    guardar(im, "tocadiscos_brazo_arriba", pos)
    gp, gz = glifo_sprite(G_PLAY11), glifo_sprite(G_PAUSA11)
    cx = (KX0 + KX1 + 1) // 2
    guardar(gp, "tocadiscos_play", (cx - gp.width // 2, KY0 + 1))
    guardar(gz, "tocadiscos_pausa", (cx - gz.width // 2, KY0 + 1))
    guardar(tecla_lateral(M.G_ANT, False), "tocadiscos_tecla_ant", (10, 14))
    guardar(tecla_lateral(M.G_ANT, True), "tocadiscos_tecla_ant_hundida", (10, 14))
    guardar(tecla_lateral(M.G_SIG, False), "tocadiscos_tecla_sig", (W - 41, 14))
    guardar(tecla_lateral(M.G_SIG, True), "tocadiscos_tecla_sig_hundida", (W - 41, 14))
    guardar(nota(M.G_NOTA, P["oro"], P["madera_osc"]), "tocadiscos_nota1", (58, 6))
    guardar(nota(M.G_NOTA2, P["teal_osc"], P["teal_luz"]), "tocadiscos_nota2", (134, 2))
    # foco prendido (verde) y otros datos para la app
    posiciones["foco"] = [79, 32]
    posiciones["centro_disco"] = [DCX, DCY]
    posiciones["piso"] = [0, 61]
    with open(os.path.join(RAW, "tocadiscos.json"), "w", encoding="utf-8") as f:
        json.dump(posiciones, f, indent=1)
    print("ok tocadiscos.json")


# ---------------------------------------------------------------------------------------------------------------
# F1 postales y la ventanita
# ---------------------------------------------------------------------------------------------------------------

def postal_marco():
    """Papel de la postal (118 x 100) con orilla dentada; la foto (108 x 68 en 5,5) y el nombre los pone la app."""
    w, hh = 118, 100
    L = nuevo(w, hh)
    L.rect(0, 0, w - 1, hh - 1, P["crema"])
    L.rect(0, hh - 1, w - 1, hh - 1, P["niebla"])
    L.rect(w - 1, 0, w - 1, hh - 1, P["niebla"])
    L.caja(4, 4, w - 5, 73, P["piedra"])
    M.borde_dentado(L, 0, 0, w - 1, hh - 1)
    return L.im


def estampillas():
    tira = Image.new("RGBA", (12 * 4, 16), (0, 0, 0, 0))
    for i, c in enumerate([P["naranja"], P["teal"], M.h("d23b5f"), P["oro"]]):
        L = nuevo(12, 16)
        L.rect(0, 1, 11, 14, P["crema"])
        L.rect(2, 3, 9, 12, c)
        L.mapa(3, 5, ["..X..", ".XXX.", "XXXXX", "..X.."], {"X": P["crema"]})
        for k in range(0, 13, 2):
            L.p(k, 1, (0, 0, 0, 0))
            L.p(k, 14, (0, 0, 0, 0))
        tira.alpha_composite(L.im, (12 * i, 0))
    return tira


def chinche():
    L = nuevo(7, 9)
    M.chinche(L, 0, 0)
    return L.im


def corcho():
    t = 24
    L = nuevo(t, t)
    c = [M.h("d3ac89"), M.h("bea98d"), M.h("c9a27a"), M.h("b59f88")]
    for y in range(t):
        for x in range(t):
            L.p(x, y, c[(x * 7 + y * 13 + (x * y) % 5) % 4] if (x * 3 + y * 5) % 7 < 2 else c[0])
    return L.im


def ventana_marco():
    """La ventanita de 46 x 50 con el vidrio vacio (transparente) para que la app ponga detras el fondo en chiquito."""
    hueco = Image.new("RGBA", (36, 35), (0, 0, 0, 0))
    im = M.ventanita(hueco)
    return im


def postales():
    guardar(postal_marco(), "postal_marco")
    guardar(estampillas(), "postal_estampillas")
    guardar(chinche(), "postal_chinche")
    guardar(corcho(), "postal_corcho")
    guardar(ventana_marco(), "ventana_marco")


# ---------------------------------------------------------------------------------------------------------------
# Barra de la cancion (cable de foquitos), la corneta de silencio y los botones de arriba
# ---------------------------------------------------------------------------------------------------------------

import maquetas_barra_botones as MB  # noqa: E402

# Numeros de los letreritos: 5 x 9 (el ":" de 2), legibles en la tablet.
DIGITOS9 = {
    "0": [".XXX.", "XX.XX", "X...X", "X...X", "X...X", "X...X", "X...X", "XX.XX", ".XXX."],
    "1": ["..X..", ".XX..", "XXX..", "..X..", "..X..", "..X..", "..X..", "..X..", "XXXXX"],
    "2": [".XXX.", "XX.XX", "....X", "...XX", "..XX.", ".XX..", "XX...", "X....", "XXXXX"],
    "3": ["XXXX.", "...XX", "....X", "...XX", ".XXX.", "...XX", "....X", "...XX", "XXXX."],
    "4": ["...X.", "..XX.", ".X.X.", "X..X.", "X..X.", "XXXXX", "...X.", "...X.", "...X."],
    "5": ["XXXXX", "X....", "X....", "XXXX.", "...XX", "....X", "....X", "X..XX", ".XXX."],
    "6": [".XXX.", "XX...", "X....", "XXXX.", "XX.XX", "X...X", "X...X", "XX.XX", ".XXX."],
    "7": ["XXXXX", "....X", "...XX", "...X.", "..XX.", "..X..", "..X..", ".XX..", ".X..."],
    "8": [".XXX.", "XX.XX", "X...X", "XX.XX", ".XXX.", "XX.XX", "X...X", "XX.XX", ".XXX."],
    "9": [".XXX.", "XX.XX", "X...X", "X...X", "XX.XX", ".XXXX", "....X", "...XX", ".XXX."],
    ":": ["..", "..", "XX", "XX", "..", "..", "XX", "XX", ".."],
}
ORDEN = "0123456789:"


def digitos():
    """Dos renglones de celdas de 6 x 10 (con su sombra): normal (crema) y encendido (nogal, al arrastrar)."""
    tira = Image.new("RGBA", (6 * len(ORDEN), 20), (0, 0, 0, 0))
    for fila, (c, sombra) in enumerate(((P["crema"], P["contorno_mad"]), (P["nogal"], None))):
        L = Lienzo(tira)
        for i, ch in enumerate(ORDEN):
            glifo(L, i * 6, fila * 10, DIGITOS9[ch], c, sombra)
    return tira


FOCOS = MB.FOCOS


def focos():
    """7 cuadros de 8 x 9: los 6 colores prendidos (con su brillito de 1 px) y el apagado. El cable pasa por y=0."""
    tira = Image.new("RGBA", (8 * 7, 9), (0, 0, 0, 0))
    forma = [".XX.", "XXXX", "XXXX", ".XX."]
    for i in range(7):
        L = nuevo(8, 9)
        L.rect(3, 0, 4, 1, P["piedra"])
        if i < 6:
            c = FOCOS[i]
            for (dx, dy) in ((1, 4), (6, 4), (3, 8), (4, 8), (2, 2), (5, 2)):
                L.p(dx, dy, c[:3] + (90,))
            L.mapa(2, 2, forma, {"X": c})
            L.p(3, 3, mezcla(c, (255, 255, 255, 255), 0.6))
            L.p(4, 5, mezcla(c, (0, 0, 0, 255), 0.3))
        else:
            L.mapa(2, 2, forma, {"X": P["carbon"]})
            L.p(3, 3, P["gris"])
        tira.alpha_composite(L.im, (8 * i, 0))
    return tira


def foco_grande():
    """4 cuadros de 21 x 21 (el cable pasa por y=2, centro del foco en x=10): sonando A y B (late), pausa y
    arrastrando (mas grande, con chispas)."""
    tira = Image.new("RGBA", (21 * 4, 21), (0, 0, 0, 0))
    for k, (r, cuerpo, borde, deco) in enumerate((
        (4, P["amarillo"], P["oro"], "rayos"), (4, P["amarillo"], P["oro"], "puntos"),
        (4, P["niebla"], P["piedra"], None), (5, P["amarillo"], P["oro"], "chispas"),
    )):
        L = nuevo(21, 21)
        cx, cy = 10, 4 + r + 1
        L.rect(cx - 1, 2, cx + 1, 3, P["piedra"])
        L.elipse(cx, cy, r + 1, r + 1, borde)
        L.elipse(cx, cy, r, r, cuerpo)
        L.p(cx - 1, cy - 2, (255, 255, 255, 255))
        L.p(cx - 2, cy - 1, (255, 255, 255, 255))
        if deco == "rayos":
            for (dx, dy) in ((-8, 0), (8, 0), (0, 8), (-6, -5), (6, -5), (-6, 6), (6, 6)):
                L.p(cx + dx, cy + dy, P["amarillo"])
        elif deco == "puntos":
            for (dx, dy) in ((-7, 0), (7, 0), (0, 7), (-5, -4), (5, -4), (-5, 5), (5, 5)):
                L.p(cx + dx, cy + dy, P["oro"])
        elif deco == "chispas":
            for (dx, dy, ln) in ((-10, 0, 3), (8, 0, 3), (-8, -6, 2), (7, -6, 2), (-8, 6, 2), (7, 6, 2)):
                L.rect(cx + dx, cy + dy, cx + dx + ln - 1, cy + dy, P["crema"])
        tira.alpha_composite(L.im, (21 * k, 0))
    return tira


def tango_corre():
    """La fila "run" de Tango con el contorno claro de 1 px (es negro: sobre la pildora oscura no se veria)."""
    cw, ch = M.INDICE["tango"]["celda"]
    n = M.INDICE["tango"]["cuadros"][M.INDICE["filas"].index("run")]
    tira = Image.new("RGBA", (cw * n, ch), (0, 0, 0, 0))
    for k in range(n):
        tira.alpha_composite(MB.contorno_claro(M.perro("tango", "run", k)), (cw * k, 0))
    return tira


def barra():
    guardar(digitos(), "barra_digitos")
    guardar(focos(), "barra_focos")
    guardar(foco_grande(), "barra_foco_grande")
    guardar(tango_corre(), "barra_tango")


def corneta():
    guardar(MB.bocina_corneta(False, 0.0, silencio=False).crop((0, 0, 40, 56)), "corneta")
    guardar(MB.bocina_corneta(False, 0.0, silencio=True).crop((0, 0, 40, 56)), "corneta_calcetin")


def botones():
    for k, lado in enumerate((0, 4, -3)):
        guardar(MB.letrero_ayuda(lado != 0, 0.0, lado=lado), f"boton_ayuda_{k}")
    guardar(MB.comoda_opciones(False, 0.0), "boton_opciones_0")
    guardar(MB.comoda_opciones(True, 0.0), "boton_opciones_1")


if __name__ == "__main__":
    tocadiscos()
    postales()
    barra()
    corneta()
    botones()
