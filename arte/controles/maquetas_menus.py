"""Maquetas (imagenes, no codigo de la app) de los menus en pixel art de Letras: opciones, ayuda y "Reproducir en...".

Hoy son AlertDialog grises de Android 5 (Pantalla.showOptions, showHelp, pickDevice). Aqui van 2 opciones de cada uno,
a escala real (1 px de arte = 1 px de pantalla, densidad 1.0), de dia y de noche, montadas sobre capturas reales, con
lo de hoy al lado. Cada lamina se guarda tambien ampliada x2 con vecino mas cercano.

Uso:  python arte/controles/maquetas_menus.py
Sale en docs/capturas/controles-pixel/: maqueta-opciones, maqueta-ayuda, maqueta-aparatos (+ -x2).

Se toma del proyecto (no se baja nada): las piezas de maquetas.py (paleta, Lienzo, perros, filtros de noche, mini
fondos, ventanita), los PNG ya hechos de drawable-nodpi (letrero "?", comodita, tocadiscos, corneta, foquitos,
postal) y las capturas despues2-*.png / antes-*.png. Lo demas se dibuja aqui. El texto de los renglones es texto del
sistema (Segoe UI aqui, en la tablet Roboto); el marco y los controles son pixel art sin antialias.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas as M  # noqa: E402
from maquetas import P, Lienzo, nuevo, noche, noche_obj, perro, pegar_recortado, h, ROJO, mini, ventanita  # noqa: E402

SALIDA = M.SALIDA
NODPI = M.NODPI
CAP = {n: Image.open(os.path.join(SALIDA, f)).convert("RGBA") for n, f in (
    ("dia", "despues2-pildora-dia-sonando.png"), ("noche", "despues2-pildora-noche-sonando.png"),
    ("horiz", "despues2-horizontal.png"), ("hoy_opc", "antes-2-opciones.png"), ("hoy_ayuda", "antes-3-ayuda.png"))}
# Botones de arriba en las capturas (medido): letrero "?" y comodita, 44 x 48.
BTN = {"v": {"ayuda": (429, 13), "opc": (539, 13)}, "h": {"ayuda": (853, 13), "opc": (963, 13)}}


def png(nombre):
    return M.cargar(nombre + ".png")


# ---------------------------------------------------------------------------------------------------------------
# Texto del sistema (con antialias, como Android) y texto pixel
# ---------------------------------------------------------------------------------------------------------------

def _f(nombre, tam):
    return ImageFont.truetype(os.path.join(M.FUENTE, nombre), tam)


F16 = _f("segoeui.ttf", 16)
F16B = _f("segoeuib.ttf", 16)
F15 = _f("segoeui.ttf", 15)
F18B = _f("segoeuib.ttf", 18)
F14 = _f("segoeui.ttf", 14)
F14B = _f("segoeuib.ttf", 14)

TINTA_TXT = (46, 38, 36, 255)
VALOR_TXT = (130, 54, 18, 255)


def tx(im, xy, s, f=F16, c=TINTA_TXT, ancla="la"):
    ImageDraw.Draw(im).text(xy, s, font=f, fill=c, anchor=ancla)


def largo(s, f=F16):
    return ImageDraw.Draw(Image.new("L", (1, 1))).textlength(s, font=f)


def envolver(s, ancho, f=F16):
    """Parte un parrafo en renglones que quepan en 'ancho'."""
    out, linea = [], ""
    for p in s.split(" "):
        prueba = (linea + " " + p).strip()
        if largo(prueba, f) <= ancho:
            linea = prueba
        else:
            out.append(linea)
            linea = p
    if linea:
        out.append(linea)
    return out


# digitos 3 x 5 para pantallitas LCD
DIG = {
    "0": ["XXX", "X.X", "X.X", "X.X", "XXX"], "1": [".X.", "XX.", ".X.", ".X.", "XXX"],
    "2": ["XXX", "..X", "XXX", "X..", "XXX"], "3": ["XXX", "..X", ".XX", "..X", "XXX"],
    "4": ["X.X", "X.X", "XXX", "..X", "..X"], "5": ["XXX", "X..", "XXX", "..X", "XXX"],
    "6": ["XXX", "X..", "XXX", "X.X", "XXX"], "7": ["XXX", "..X", ".X.", ".X.", ".X."],
    "8": ["XXX", "X.X", "XXX", "X.X", "XXX"], "9": ["XXX", "X.X", "XXX", "..X", "XXX"],
    ":": [".", "X", ".", "X", "."], " ": ["...", "...", "...", "...", "..."],
    "E": ["XXX", "X..", "XX.", "X..", "XXX"], "S": ["XXX", "X..", "XXX", "..X", "XXX"],
    "N": ["X.X", "XXX", "XXX", "X.X", "X.X"], "-": ["...", "...", "XXX", "...", "..."],
    "m": ["...", "...", "XXX", "XXX", "X.X"], "?": ["XXX", "..X", ".XX", "...", ".X."],
}


def digitos(L, x, y, s, c):
    for ch in s:
        f = DIG[ch]
        L.mapa(x, y, f, {"X": c})
        x += len(f[0]) + 1


def ancho_dig(s):
    return sum(len(DIG[ch][0]) + 1 for ch in s) - 1


# ---------------------------------------------------------------------------------------------------------------
# Madera y piezas comunes
# ---------------------------------------------------------------------------------------------------------------

def ruido(x, y):
    return ((x * 73856093) ^ (y * 19349663) ^ (x * y * 83492791)) & 0xFFFF


def vetas(L, x0, y0, x1, y1, c, paso=5):
    """Vetas de madera: rayitas horizontales de largo y lugar pseudo-aleatorios."""
    for y in range(y0, y1 + 1, paso):
        x = x0 + ruido(y, x0) % 13
        while x < x1:
            n = 6 + ruido(x, y) % 22
            L.rect(x, y, min(x1, x + n), y, c)
            x += n + 10 + ruido(y, x) % 30


def tabla(L, x0, y0, x1, y1, med=None, luz=None, osc=None, borde=None, veta=None, esquina=True):
    med = med or P["madera"]
    luz = luz or P["madera_luz"]
    osc = osc or P["madera_osc"]
    borde = borde or P["contorno_mad"]
    L.rect(x0, y0, x1, y1, med)
    if veta is not False and y1 - y0 > 6:
        vetas(L, x0 + 3, y0 + 3, x1 - 3, y1 - 3, veta or P["madera_med"])
    L.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, luz)
    L.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, luz)
    L.rect(x1 - 1, y0 + 2, x1 - 1, y1 - 1, osc)
    L.rect(x0 + 2, y1 - 1, x1 - 1, y1 - 1, osc)
    L.caja(x0, y0, x1, y1, borde, esquina=esquina)


def sombra(L, x0, y0, x1, y1, k=0.6):
    L.oscurecer(x0, y0, x1, y1, k)


def perilla_redonda(L, cx, cy, r=3):
    """El jalador redondo de los cajones (igual al de la comodita)."""
    L.elipse(cx, cy + 1, r, r, P["madera_osc"])
    L.elipse(cx, cy, r, r, P["naranja"])
    L.elipse(cx, cy, r - 1, r - 1, P["oro"])
    L.p(cx - 1, cy - 1, P["amarillo"])
    L.anillo(cx, cy, r, r, P["vino"])


def hueso(L, x, y, c=None, borde=None):
    c = c or P["crema"]
    borde = borde or P["topo"]
    L.mapa(x, y, [
        ".BB.......BB.",
        "BCCB.....BCCB",
        "BCCCBBBBBCCCB",
        ".BCCCCCCCCCB.",
        "BCCCBBBBBCCCB",
        "BCCB.....BCCB",
        ".BB.......BB.",
    ], {"B": borde, "C": c})


def nota(L, x, y, c, grande=False):
    M.glifo(L, x, y, M.G_NOTA2 if grande else M.G_NOTA, c)


# ---------------------------------------------------------------------------------------------------------------
# Objetos (iconos de 30-36 px): se usan en las fichas del cajon (A) y en la repisa (B)
# ---------------------------------------------------------------------------------------------------------------

def obj_fondo(n=0.0):
    """Postal chiquita con el fondo actual (Lofi) y su estampilla."""
    L = nuevo(32, 28)
    L.rect(0, 2, 31, 27, P["crema"])
    L.rect(1, 27, 31, 27, P["niebla"])
    L.rect(31, 3, 31, 27, P["niebla"])
    L.im.paste(mini(1, 26, 18), (3, 5))
    L.px = L.im.load()
    L.caja(2, 4, 29, 23, P["piedra"])
    L.rect(22, 6, 27, 11, P["crema"])
    L.rect(23, 7, 26, 10, P["naranja"])
    M.borde_dentado(L, 0, 2, 31, 27)
    M.chinche(L, 13, 0)
    return L.im


def obj_arte(completo=True):
    """Cuadro en caballete: 'completo' deja franjas arriba y abajo; 'llenar' la imagen llena el marco."""
    L = nuevo(32, 34)
    # caballete
    L.linea(8, 30, 13, 18, P["madera_osc"])
    L.linea(23, 30, 18, 18, P["madera_osc"])
    L.linea(15, 33, 15, 18, P["madera_osc"])
    L.rect(5, 22, 26, 23, P["madera"])
    L.rect(5, 23, 26, 23, P["madera_osc"])
    # cuadro
    L.rect(3, 1, 28, 21, P["madera"])
    L.rect(4, 2, 27, 2, P["madera_luz"])
    L.caja(3, 1, 28, 21, P["contorno_mad"], esquina=True)
    L.rect(6, 4, 25, 18, P["tinta"])
    img = mini(1, 20, 15)
    if completo:
        L.im.paste(img.resize((20, 9), Image.NEAREST), (6, 7))
    else:
        L.im.paste(img, (6, 4))
    L.px = L.im.load()
    return L.im


def obj_reloj(minutos=5, colon=True):
    """Reloj digital (siempre digital) con la cuenta del cambio de arte: 05:00 o una nota si es con cada cancion."""
    L = nuevo(34, 26)
    L.rect(1, 3, 32, 21, P["carbon"])
    L.rect(2, 3, 31, 3, P["gris"])
    L.caja(0, 2, 33, 22, P["negro"], esquina=True)
    L.rect(4, 6, 29, 17, h("1c3b2c"))
    L.caja(3, 5, 30, 18, P["tinta"])
    lcd = h("7df0a0")
    if minutos == 0:
        M.glifo(L, 14, 8, M.G_NOTA, lcd)
    else:
        s = "%02d" % minutos + (":" if colon else " ") + "00"
        digitos(L, 5 + (25 - ancho_dig(s)) // 2, 9, s, lcd)
    # botoncito arriba y patitas
    L.rect(23, 0, 27, 1, P["naranja"])
    L.caja(22, 0, 28, 2, P["negro"])
    L.rect(4, 23, 7, 24, P["negro"])
    L.rect(26, 23, 29, 24, P["negro"])
    return L.im


def obj_persiana(nivel=1):
    """Ventanita con persiana: poco / medio / mucho = la persiana baja 1/3, 2/3 o casi toda (oscurece la letra)."""
    L = nuevo(32, 34)
    L.rect(2, 2, 29, 29, P["madera"])
    L.caja(1, 1, 30, 30, P["contorno_mad"], esquina=True)
    L.rect(2, 2, 29, 2, P["madera_luz"])
    # cielo
    for y in range(5, 27):
        L.rect(5, y, 26, y, mezcla_paleta(y, 5, 27))
    L.rect(15, 5, 16, 26, P["madera"])
    L.caja(4, 4, 27, 27, P["madera_osc"])
    # persiana: tablillas cada 3 px
    baja = [8, 15, 21][nivel]
    for y in range(5, 5 + baja):
        c = P["humo"] if (y - 5) % 3 < 2 else P["piedra"]
        L.rect(4, y, 27, y, c)
    L.rect(3, 3, 28, 4, P["niebla"])
    L.caja(3, 3, 28, 4 + baja, P["topo"])
    # cordon con bolita
    L.rect(25, 5 + baja, 25, 12 + baja if baja < 20 else 30, P["topo"])
    yb = min(13 + baja, 31)
    L.rect(24, yb, 26, yb + 1, P["oro"])
    # repisa
    L.rect(0, 30, 31, 32, P["madera"])
    L.caja(0, 30, 31, 33, P["contorno_mad"], esquina=True)
    return L.im


def mezcla_paleta(y, y0, y1):
    t = (y - y0) / max(1, (y1 - y0))
    return M.mezcla(h("6fb6e8"), h("cfe9f2"), t)


def obj_luna(auto=True):
    """Lamparita de luna (modo tranquilo): encendida con zzz si es automatico; gris si esta apagado."""
    L = nuevo(30, 34)
    c1, c2, c3 = (P["amarillo"], P["oro"], P["naranja"]) if auto else (P["niebla"], P["perla"], P["piedra"])
    L.elipse(13, 13, 10, 10, c3)
    L.elipse(13, 13, 9, 9, c2)
    L.elipse(12, 12, 7, 7, c1)
    L.elipse(19, 9, 8, 8, (0, 0, 0, 0))
    L.p(7, 10, P["crema"]) if auto else None
    # base
    L.rect(6, 26, 21, 29, P["madera"])
    L.rect(6, 26, 21, 26, P["madera_luz"])
    L.caja(5, 25, 22, 30, P["contorno_mad"], esquina=True)
    L.rect(12, 21, 14, 25, P["madera_osc"])
    if auto:
        L.mapa(21, 2, ["XXX", "..X", ".X.", "XXX"], {"X": P["blanco"]})
        L.mapa(25, 8, ["XX", ".X", "XX"], {"X": P["blanco"]})
    return L.im


def obj_libro(idioma="ES"):
    """Diccionario parado con el idioma en el lomo y la tapa. '--' = sin traduccion (libro cerrado con liga)."""
    L = nuevo(30, 34)
    tapa, tapa_l, tapa_o = {"ES": (P["teal"], P["teal_luz"], P["teal_osc"]),
                            "EN": (ROJO[2], ROJO[3], ROJO[1]),
                            "--": (P["perla"], P["niebla"], P["piedra"])}[idioma]
    # libro de atras (el otro idioma), inclinado
    L.rect(3, 6, 9, 31, P["lila"])
    L.caja(2, 5, 10, 32, P["tinta"])
    # libro de frente
    L.rect(9, 2, 26, 31, tapa)
    L.rect(10, 3, 25, 3, tapa_l)
    L.rect(10, 3, 10, 30, tapa_l)
    L.rect(25, 4, 25, 30, tapa_o)
    L.caja(8, 1, 27, 32, P["tinta"], esquina=True)
    L.rect(12, 8, 23, 16, P["crema"])
    L.caja(12, 8, 23, 16, tapa_o)
    digitos(L, 18 - ancho_dig(idioma) // 2, 10, idioma, P["tinta"])
    L.rect(11, 22, 24, 22, P["oro"])
    L.rect(11, 25, 24, 25, P["oro"])
    if idioma == "--":
        L.rect(8, 19, 27, 20, P["vino"])
    return L.im


def obj_siguiente():
    """Montoncito de fotos: la de encima se desliza a la derecha."""
    L = nuevo(34, 30)
    for k, (x, y, c) in enumerate(((2, 8, P["perla"]), (4, 5, P["humo"]))):
        L.rect(x, y, x + 20, y + 17, P["crema"])
        L.caja(x, y, x + 20, y + 17, P["piedra"])
        L.rect(x + 2, y + 2, x + 18, y + 12, c)
    L.im.paste(mini(1, 17, 11), (6, 7))
    L.px = L.im.load()
    x, y = 11, 2
    sombra(L, x + 1, y + 1, x + 21, y + 18, 0.7)
    L.rect(x, y, x + 20, y + 17, P["crema"])
    L.caja(x, y, x + 20, y + 17, P["piedra"])
    L.im.paste(mini(0, 17, 11), (x + 2, y + 2))
    L.px = L.im.load()
    L.mapa(x + 15, y + 20, ["X...", "XX..", "XXX.", "XXXX", "XXX.", "XX..", "X..."], {"X": P["naranja"]})
    L.rect(x + 6, y + 23, x + 14, y + 23, P["naranja"])
    return L.im


def obj_protector():
    """Tablet en su base, cargando (rayito en la pantalla)."""
    L = nuevo(34, 30)
    L.rect(2, 2, 31, 21, P["tinta"])
    L.caja(1, 1, 32, 22, P["negro"], esquina=True)
    L.rect(4, 4, 29, 19, h("27304a"))
    # estrellitas y luna en la pantalla (el protector de noche)
    for (x, y) in ((7, 7), (12, 15), (25, 6)):
        L.p(x, y, P["crema"])
    L.mapa(20, 8, [".XX", "X..", "X..", ".XX"], {"X": P["amarillo"]})
    # rayito
    L.mapa(13, 7, ["..X", ".X.", "XXX", ".X.", "X.."], {"X": P["verde"]})
    # base
    L.rect(12, 23, 21, 25, P["gris"])
    L.rect(8, 26, 25, 27, P["carbon"])
    L.caja(7, 25, 26, 28, P["negro"], esquina=True)
    # cable
    L.linea(26, 27, 33, 27, P["carbon"])
    return L.im


def obj_ayuda():
    """El letrero "?" en chiquito."""
    L = nuevo(30, 30)
    L.linea(15, 0, 5, 7, P["tinta"])
    L.linea(15, 0, 24, 7, P["tinta"])
    L.p(15, 0, P["perla"])
    tabla(L, 2, 6, 27, 27, veta=False)
    L.mapa(11, 10, [".XXXX.", "XX..XX", "....XX", "..XXX.", "..XX..", "......", "..XX.."],
           {"X": P["crema"]})
    L.mapa(11, 11, [".XXXX.", "XX..XX", "....XX", "..XXX.", "..XX..", "......", "..XX.."],
           {"X": P["nogal"]}) if False else None
    return L.im


def obj_bocina():
    """Bocinita de madera con notas saliendo (Reproducir en...)."""
    L = nuevo(32, 34)
    tabla(L, 3, 6, 22, 33, veta=False)
    L.elipse(12, 14, 4, 4, P["tinta"])
    L.elipse(12, 14, 2, 2, P["carbon"])
    L.elipse(12, 25, 6, 5, P["tinta"])
    L.elipse(12, 25, 3, 3, P["carbon"])
    L.p(11, 24, P["gris"])
    nota(L, 24, 0, P["naranja"])
    nota(L, 26, 11, P["teal"])
    return L.im


def obj_llaves():
    """Llavero colgado de un clavito: tocarlo = te llevas las llaves (cerrar sesion)."""
    L = nuevo(30, 34)
    L.rect(13, 0, 15, 2, P["carbon"])
    L.anillo(14, 9, 5, 5, P["perla"])
    L.anillo(14, 9, 4, 4, P["piedra"])
    # llave 1 (dorada)
    L.elipse(9, 17, 3, 3, P["oro"])
    L.anillo(9, 17, 3, 3, P["madera_osc"])
    L.p(9, 17, (0, 0, 0, 0))
    L.rect(9, 20, 10, 31, P["oro"])
    L.rect(11, 26, 12, 26, P["oro"])
    L.rect(11, 29, 13, 29, P["oro"])
    L.rect(8, 20, 8, 31, P["madera_osc"])
    # llave 2 (plateada)
    L.elipse(19, 17, 3, 3, P["niebla"])
    L.anillo(19, 17, 3, 3, P["gris"])
    L.p(19, 17, (0, 0, 0, 0))
    L.rect(19, 20, 20, 29, P["niebla"])
    L.rect(21, 25, 22, 25, P["niebla"])
    L.rect(21, 28, 22, 28, P["niebla"])
    # llaverito de hueso
    hueso(L, 12, 26, P["crema"], P["vino"]) if False else None
    L.rect(14, 14, 14, 20, P["perla"])
    L.rect(12, 21, 16, 26, h("d23b5f"))
    L.caja(11, 21, 17, 27, P["vino"], esquina=True)
    return L.im


# ---------------------------------------------------------------------------------------------------------------
# Controles en pixel: perilla de posiciones, interruptor, flechita
# ---------------------------------------------------------------------------------------------------------------

def perilla(L, cx, cy, posiciones, i):
    """Perilla de laton con rayitas alrededor; la rayita de la posicion actual se enciende."""
    import math
    angs = [-150 + k * (120 / max(1, posiciones - 1)) for k in range(posiciones)] if posiciones > 1 else [-90]
    for k, a in enumerate(angs):
        r = math.radians(a)
        for d in ((11, 12, 13, 14) if k == i else (12, 13)):
            for dx, dy in ((0, 0), (1, 0)) if k == i else ((0, 0),):
                L.p(int(round(cx + d * math.cos(r))) + dx, int(round(cy + d * math.sin(r))) + dy,
                    P["naranja"] if k == i else P["topo"])
    L.elipse(cx, cy + 1, 9, 9, P["madera_osc"])
    L.elipse(cx, cy, 9, 9, P["oro"])
    L.elipse(cx - 1, cy - 1, 6, 6, P["amarillo"])
    L.elipse(cx, cy, 5, 5, P["oro"])
    L.anillo(cx, cy, 9, 9, P["nogal"])
    # muescas del borde
    for a in range(0, 360, 45):
        r = math.radians(a + 22)
        L.p(int(round(cx + 8 * math.cos(r))), int(round(cy + 8 * math.sin(r))), P["madera_med"])
    # indicador
    r = math.radians(angs[i])
    ex, ey = int(round(cx + 7 * math.cos(r))), int(round(cy + 7 * math.sin(r)))
    L.linea(cx, cy, ex, ey, P["nogal"])
    L.linea(cx + 1, cy, ex + 1, ey, P["nogal"])
    L.p(cx, cy, P["nogal"])


def interruptor(L, x, y, encendido):
    """Interruptor de corredera de 34 x 18: canal oscuro, botoncito de madera; foquito verde si esta prendido."""
    L.rect(x + 1, y + 3, x + 29, y + 14, P["tinta"])
    L.caja(x, y + 2, x + 30, y + 15, P["negro"], esquina=True)
    L.rect(x + 2, y + 4, x + 28, y + 4, P["negro"])
    if encendido:
        L.rect(x + 2, y + 5, x + 14, y + 13, P["verde_osc"])
    bx = x + 15 if encendido else x + 2
    tabla(L, bx, y, bx + 13, y + 17, veta=False)
    L.rect(bx + 4, y + 5, bx + 4, y + 12, P["madera_osc"])
    L.rect(bx + 7, y + 5, bx + 7, y + 12, P["madera_osc"])
    L.rect(bx + 10, y + 5, bx + 10, y + 12, P["madera_osc"])
    # foquito
    fx = x + 33
    L.elipse(fx + 2, y + 8, 2, 2, P["verde"] if encendido else P["gris"])
    if encendido:
        L.p(fx + 1, y + 7, P["crema"])


def flechita(L, x, y, c=None):
    """Pestanita de laton con flecha: 'abre otra cosa'."""
    c = c or P["oro"]
    L.rect(x, y, x + 15, y + 17, c)
    L.rect(x + 1, y + 1, x + 14, y + 1, P["amarillo"])
    L.caja(x, y, x + 15, y + 17, P["madera_osc"], esquina=True)
    L.mapa(x + 6, y + 5, ["X...", "XX..", "XXX.", "XXXX", "XXX.", "XX..", "X..."], {"X": P["nogal"]})


# ---------------------------------------------------------------------------------------------------------------
# Capas: el arte se dibuja en una capa; el texto del sistema se anota y se pinta al final (despues del filtro de
# noche), como lo haria Android (el texto no pasa por el ColorFilter del arte).
# ---------------------------------------------------------------------------------------------------------------

class Capa:
    def __init__(self, w, hh):
        self.fondo = None             # imagen que ya viene en su version de dia o noche (la pared del cuarto)
        self.marco = nuevo(w, hh)     # madera, marcos: de noche noche_obj (mas oscuro)
        self.papel = nuevo(w, hh)     # fichas, hojas, etiquetas: de noche noche() (mas suave, para leer)
        self.textos = []              # (xy, s, fuente, (color_dia, color_noche), ancla)
        self.encima = []              # (sprite, x, y, filtro) perros y cosas que van sobre todo

    def t(self, xy, s, f=F16, c=None, cn=None, ancla="la"):
        c = c or TINTA_TXT
        self.textos.append((xy, s, f, (c, cn or c), ancla))

    def render(self, n):
        out = Image.new("RGBA", self.marco.im.size, (0, 0, 0, 0))
        if self.fondo is not None:
            out.alpha_composite(self.fondo)
        out.alpha_composite(self.marco.im if n < 0.5 else noche_obj(self.marco.im))
        out.alpha_composite(self.papel.im if n < 0.5 else noche(self.papel.im, 0.75))
        # 'luz': lo que brilla de noche (el cuadrante del radio): casi sin filtro
        for im, x, y in getattr(self, "luz", []):
            pegar_recortado(out, im if n < 0.5 else noche(im, 0.2), x, y)
        for spr, x, y, filtro in self.encima:
            s = spr if n < 0.5 else (noche(spr) if filtro == "perro" else noche_obj(spr))
            pegar_recortado(out, s, x, y)
        for xy, s, f, cs, ancla in self.textos:
            tx(out, xy, s, f, cs[0 if n < 0.5 else 1], ancla)
        return out


VAL_N = (88, 34, 30, 255)

OPCIONES = [
    # (etiqueta, valor, control, objeto)
    ("Fondo", "Lofi (89 imágenes)", ("abre",), lambda: obj_fondo()),
    ("Arte", "completo", ("perilla", 2, 0), lambda: obj_arte(True)),
    ("Cambiar arte", "cada 5 min", ("perilla", 4, 1), lambda: obj_reloj(5)),
    ("Oscurecer detrás de la letra", "medio", ("perilla", 3, 1), lambda: obj_persiana(1)),
    ("Modo tranquilo de noche", "automático", ("switch", True), lambda: obj_luna(True)),
    ("Traducir a", "español", ("perilla", 3, 0), lambda: obj_libro("ES")),
    ("Siguiente imagen", None, ("abre",), lambda: obj_siguiente()),
    ("Protector de pantalla", None, ("abre",), lambda: obj_protector()),
    ("Cómo se usa", None, ("abre",), lambda: obj_ayuda()),
    ("Reproducir en…", None, ("abre",), lambda: obj_bocina()),
    ("Cerrar sesión", None, ("abre",), lambda: obj_llaves()),
]
FICHA_H = 44


def ficha(C, x, y, w, op, levantada=False, valor=None, control=None, objeto=None):
    """Una ficha de fichero (tarjeta de biblioteca) de w x 44: objeto a la izquierda, texto, control a la derecha."""
    etq, val, ctl, fobj = op
    val = valor if valor is not None else val
    ctl = control or ctl
    L = C.papel
    if levantada:
        y -= 3
        sombra(C.marco, x + 2, y + 4, x + w + 1, y + FICHA_H + 3, 0.45)
    papel = P["rosa"] if etq == "Cerrar sesión" else P["crema"]
    L.rect(x, y, x + w - 1, y + FICHA_H - 1, papel)
    L.rect(x + 1, y + FICHA_H - 2, x + w - 2, y + FICHA_H - 2, P["niebla"] if papel == P["crema"] else P["piedra"])
    L.rect(x + w - 2, y + 1, x + w - 2, y + FICHA_H - 2, P["niebla"] if papel == P["crema"] else P["piedra"])
    L.rect(x + 1, y + 1, x + w - 3, y + 1, P["blanco"])
    L.caja(x, y, x + w - 1, y + FICHA_H - 1, P["topo"], esquina=True)
    # renglon rojo de ficha bibliografica
    L.rect(x + 46, y + 7, x + w - 8, y + 7, h("e9a0a0"))
    # objeto
    o = objeto or fobj()
    pegar_recortado(L.im, o, x + 6 + (34 - o.width) // 2, y + (FICHA_H - o.height) // 2)
    L.px = L.im.load()
    # control
    xc = x + w - 10
    if ctl[0] == "perilla":
        perilla(L, xc - 14, y + 23, ctl[1], ctl[2])
        xc -= 36
    elif ctl[0] == "switch":
        interruptor(L, xc - 38, y + 13, ctl[1])
        xc -= 46
    else:
        flechita(L, xc - 16, y + 13)
        xc -= 26
    C.t((x + 48, y + 23), etq, F16, ancla="lm")
    if val:
        C.t((xc, y + 23), val, F16B, VALOR_TXT, VAL_N, ancla="rm")


def cajon(W, columnas=1, levantada=None, valores=None):
    """El cajon de la comodita abierto, visto desde arriba y de frente, con una ficha por opcion."""
    filas = (len(OPCIONES) + columnas - 1) // columnas
    alto_fichas = filas * FICHA_H + (filas - 1) * 3 + (6 if columnas == 1 else 0)
    FONDO = 14 + 6 + alto_fichas + 6
    FRENTE = 46
    H = FONDO + FRENTE
    C = Capa(W, H + 8)
    L = C.marco
    # orillas de arriba (tablas del cajon) y caras de adentro
    tabla(L, 0, 0, W - 1, FONDO + 4, med=P["madera_med"], veta=P["madera_osc"])
    L.rect(9, 9, W - 10, FONDO + 4, P["madera_osc"])
    L.rect(9, 9, W - 10, 9, P["nogal"])
    # piso de fieltro (verde azulado, para que Tango negro se vea)
    fx0, fy0, fx1, fy1 = 13, 15, W - 14, FONDO + 4
    for yy in range(fy0, fy1 + 1):
        for xx in range(fx0, fx1 + 1):
            L.p(xx, yy, P["teal_osc"] if ruido(xx, yy) % 7 else h("3b7563"))
    L.rect(fx0, fy0, fx1, fy0 + 1, h("1d4538"))
    L.caja(fx0 - 1, fy0 - 1, fx1 + 1, fy1, P["nogal"])
    for k in range(5):
        L.p(9 + k, 9 + k, P["nogal"])
        L.p(W - 10 - k, 9 + k, P["nogal"])
    # fichas
    ancho_col = (fx1 - fx0 + 1 - 8 - (columnas - 1) * 8) // columnas
    for i, op in enumerate(OPCIONES):
        c, r = divmod(i, filas)
        x = fx0 + 4 + c * (ancho_col + 8)
        y = fy0 + 4 + r * (FICHA_H + 3)
        w = ancho_col
        if columnas == 1 and op[0] == "Cerrar sesión":
            # la ultima ficha va a la mitad: en el hueco de al lado se echa Tango con su hueso
            y += 6
            w = ancho_col // 2 + 20
            hueco = (x + w + 6, y, ancho_col - w - 6, FICHA_H)
        if columnas == 2 and i == len(OPCIONES) - 1:
            hueco = (x, y + FICHA_H + 3, ancho_col, FICHA_H)
        v = (valores or {}).get(i, {})
        ficha(C, x, y, w, op, levantada=(levantada == i), **v)
    # el frente del cajon (lo vemos de frente)
    fy = FONDO
    tabla(L, 0, fy, W - 1, fy + FRENTE - 1)
    L.rect(1, fy + FRENTE, W - 2, fy + FRENTE + 2, (0, 0, 0, 80))
    # portaetiqueta de laton con su tarjetita
    pw = 132
    px0 = W // 2 - pw // 2
    L.rect(px0, fy + 8, px0 + pw - 1, fy + 27, P["oro"])
    L.rect(px0 + 1, fy + 9, px0 + pw - 2, fy + 9, P["amarillo"])
    L.caja(px0, fy + 8, px0 + pw - 1, fy + 27, P["madera_osc"], esquina=True)
    L.rect(px0 + 4, fy + 11, px0 + pw - 5, fy + 24, P["crema"])
    L.caja(px0 + 3, fy + 10, px0 + pw - 4, fy + 25, P["piedra"])
    for xx in (px0 + 1, px0 + pw - 2):
        L.p(xx, fy + 17, P["nogal"])
    C.t((W // 2, fy + 18), "Opciones", F14B, (70, 40, 20, 255), (40, 24, 20, 255), ancla="mm")
    perilla_redonda(L, W // 2, fy + 36, 4)
    for xx in (6, W - 7):
        L.p(xx, fy + 6, P["nogal"])
        L.p(xx, fy + FRENTE - 7, P["nogal"])
    return C, FONDO, hueco


def x2(im):
    return im.resize((im.width * 2, im.height * 2), Image.NEAREST)


def tango_hueso(cuadro=5, escala=2):
    """Tango echado mordiendo su hueso (el del cajon). A 2x, como los perros grandes de abajo de la pantalla."""
    t = perro("tango", "chew", cuadro)
    t = t.crop(t.getbbox())
    return x2(t) if escala == 2 else t


# ---------------------------------------------------------------------------------------------------------------
# Pantallas: captura real + velo + boton de arriba en su estado "abierto" + el panel
# ---------------------------------------------------------------------------------------------------------------

def pantalla(n, horiz=False):
    return (CAP["horiz"] if horiz else CAP["noche" if n > 0.5 else "dia"]).copy()


def velo(scr, a=130):
    scr.alpha_composite(Image.new("RGBA", scr.size, (6, 6, 14, a)))


def boton(scr, cual, estado, n, horiz=False):
    """Pone el boton de arriba (sin velo) en su estado: ayuda 0/1/2, opciones 0/1."""
    x, y = BTN["h" if horiz else "v"][cual]
    src = CAP["horiz"] if horiz else CAP["noche" if n > 0.5 else "dia"]
    # tapar el boton viejo con el fondo que hay a su izquierda (sin botones) y poner el estado nuevo
    L = Lienzo(scr)
    mx = x + 50 if cual == "opc" else x - 6
    for yy in range(y - 4, y + 52):
        c = scr.getpixel((min(scr.width - 1, mx), yy))
        L.rect(x - 2, yy, x + 46, yy, c)
    b = png(("boton_ayuda_" if cual == "ayuda" else "boton_opciones_") + str(estado))
    scr.alpha_composite(b if n < 0.5 else noche_obj(b), (x, y))


def pantalla_opciones_a(n, horiz=False, levantada=None, valores=None):
    scr = pantalla(n, horiz)
    velo(scr)
    t = tango_hueso()
    t = t if n < 0.5 else noche(t)
    if horiz:
        W, x0, y0 = 992, 16, 68
    else:
        W, x0, y0 = 568, 16, 70
    C, FONDO, (hx, hy, hw, hh) = cajon(W, columnas=2 if horiz else 1, levantada=levantada, valores=valores)
    scr.alpha_composite(C.render(n), (x0, y0))
    # Tango en el hueco que sobra, echado sobre el fieltro mordiendo su hueso (el hueso vive en este cajon)
    scr.alpha_composite(t, (x0 + hx + (hw - t.width) // 2 + 20, y0 + hy + hh - t.height - 1))
    boton(scr, "opc", 1, n, horiz)
    return scr


TMP = os.path.join(os.environ.get("TEMP", "."), "claude", "b0d12307302f1aa1", "24549638-27ef-4d4b-9194-032da90b22a9",
                   "scratchpad", "menus")


# ---------------------------------------------------------------------------------------------------------------
# Opciones B: la repisa del cuarto. Cada opcion es un objeto parado en una tabla; debajo cuelga su etiqueta con el
# nombre y el valor. Tocar el objeto lo cambia (la persiana baja, el reloj cambia, el libro cambia de tapa...).
# ---------------------------------------------------------------------------------------------------------------

SLOT_H = 58
TABLA_Y = 46          # dentro del slot: donde se para el objeto (encima de la tabla)


def tarjeta_parada(C, x, yb, w, nombre, valor, volteando=False):
    """Tarjetita doblada parada en la tabla (como las de mesa): nombre y valor. Al cambiar el valor se voltea."""
    L = C.papel
    hh = 40
    y = yb - hh
    if volteando:
        # a media vuelta: se ve angosta y del reverso (rayada)
        cx = x + w // 2
        L.rect(cx - 30, y + 4, cx + 29, yb - 1, P["humo"])
        for yy in range(y + 8, yb - 3, 4):
            L.rect(cx - 27, yy, cx + 26, yy, P["perla"])
        L.caja(cx - 30, y + 4, cx + 29, yb - 1, P["topo"], esquina=True)
        L.rect(cx - 30, yb, cx + 29, yb, (0, 0, 0, 80))
        return
    L.rect(x, y, x + w - 1, yb - 1, P["crema"])
    L.rect(x + 1, y, x + w - 2, y + 2, P["humo"])          # el doblez de arriba
    L.rect(x + 1, y + 3, x + w - 2, y + 3, P["niebla"])
    L.rect(x + w - 2, y + 4, x + w - 2, yb - 2, P["niebla"])
    L.caja(x, y, x + w - 1, yb - 1, P["topo"], esquina=True)
    C.marco.rect(x + 2, yb, x + w + 1, yb, (0, 0, 0, 90))
    if valor:
        C.t((x + 8, y + 14), nombre, F15, ancla="lm")
        C.t((x + 8, y + 30), valor, F16B, VALOR_TXT, VAL_N, ancla="lm")
    else:
        C.t((x + 8, y + 22), nombre, F16, ancla="lm")


def escuadra(L, x, y):
    L.mapa(x, y, ["XXXXXX", "XXXXX.", "XXXX..", "XXX...", "XX....", "X....."], {"X": P["nogal"]})
    L.mapa(x + 1, y, ["OOOO", "OOO.", "OO..", "O..."], {"O": P["madera_osc"]})


def cojin_hunter():
    """Hunter dormido hecho bolita en su cojin (el slot que sobra)."""
    L = nuevo(46, 40)
    L.elipse(22, 33, 20, 5, ROJO[2])
    L.elipse(22, 32, 18, 3, ROJO[3])
    L.anillo(22, 33, 20, 5, ROJO[0])
    d = perro("hunter", "sleep_curl", 6)
    d = d.crop(d.getbbox())
    pegar_recortado(L.im, d, 22 - d.width // 2, 34 - d.height)
    L.px = L.im.load()
    L.mapa(36, 2, ["XXX", "..X", ".X.", "XXX"], {"X": P["blanco"]})
    L.mapa(41, 8, ["XX", ".X", "XX"], {"X": P["blanco"]})
    return L.im


def repisa(W, n, columnas=2, tocado=None, valores=None, volteando=None):
    slots = len(OPCIONES) + 1
    filas = (slots + columnas - 1) // columnas
    ARRIBA = 40
    PISO = 34
    H = ARRIBA + filas * SLOT_H + PISO
    C = Capa(W, H)
    C.fondo = M.pared(W, n, H)
    L = C.marco
    # marco de madera alrededor de la pared
    tabla(L, 0, 0, W - 1, 6, veta=False)
    tabla(L, 0, H - 7, W - 1, H - 1, veta=False)
    tabla(L, 0, 0, 6, H - 1, veta=False)
    tabla(L, W - 7, 0, W - 1, H - 1, veta=False)
    # placa "Opciones" colgada bajo los foquitos
    pw = 120
    px0 = W // 2 - pw // 2
    L.linea(W // 2, 8, px0 + 10, 17, P["tinta"])
    L.linea(W // 2, 8, px0 + pw - 11, 17, P["tinta"])
    L.rect(W // 2 - 1, 7, W // 2, 8, P["carbon"])
    tabla(L, px0, 16, px0 + pw - 1, 35, veta=False)
    C.t((W // 2, 26), "Opciones", F14B, (250, 238, 210, 255), (200, 196, 210, 255), ancla="mm")
    ancho_s = (W - 16) // columnas
    for r in range(filas):
        yb = ARRIBA + r * SLOT_H + TABLA_Y
        tabla(L, 7, yb, W - 8, yb + 6, veta=False)
        L.rect(8, yb + 7, W - 9, yb + 8, (0, 0, 0, 70))
        for c in range(columnas):
            escuadra(L, 7 + c * ancho_s + 14, yb + 7)
    for i in range(slots):
        r, c = divmod(i, columnas)
        x = 8 + c * ancho_s
        yb = ARRIBA + r * SLOT_H + TABLA_Y
        if i == len(OPCIONES):
            hu = cojin_hunter()
            C.encima.append((hu, x + 30, yb - hu.height + 1, "perro"))
            continue
        etq, val, _, fobj = OPCIONES[i]
        v = (valores or {}).get(i, {})
        val = v.get("valor", val)
        o = v.get("objeto") or fobj()
        sube = 4 if tocado == i else 0
        ox = x + 8 + (36 - o.width) // 2
        oy = yb - o.height - sube
        if sube:
            L.rect(ox + 4, yb - 1, ox + o.width - 5, yb - 1, (0, 0, 0, 110))
        pegar_recortado(L.im, o, ox, oy)
        L.px = L.im.load()
        tarjeta_parada(C, x + 50, yb, ancho_s - 60, etq, val, volteando=(volteando == i))
    # Tango en el piso mirando hacia arriba (de la familia del cuarto, a 1x)
    t = perro("tango", "look_up", 2)
    t = t.crop(t.getbbox())
    C.encima.append((t, W - 70, H - 9 - t.height, "perro"))
    return C


def pantalla_opciones_b(n, horiz=False, tocado=None, valores=None, volteando=None):
    scr = pantalla(n, horiz)
    velo(scr)
    if horiz:
        W, x0, y0, col = 992, 16, 66, 3
    else:
        W, x0, y0, col = 568, 16, 68, 2
    C = repisa(W, n, columnas=col, tocado=tocado, valores=valores, volteando=volteando)
    scr.alpha_composite(C.render(n), (x0, y0))
    boton(scr, "opc", 1, n, horiz)
    return scr


# ---------------------------------------------------------------------------------------------------------------
# Aparatos (los de la casa). Grandes para la charola y la libreta; chiquitos (16 px) para el radio.
# ---------------------------------------------------------------------------------------------------------------

def ap_pc(pantalla_on=True):
    """Monitor con la letra en pantalla, teclado y raton. 60 x 52."""
    L = nuevo(60, 52)
    # pie
    L.rect(26, 34, 33, 40, P["gris"])
    L.rect(20, 40, 39, 42, P["carbon"])
    L.caja(19, 39, 40, 43, P["negro"], esquina=True)
    # monitor
    L.rect(2, 1, 57, 34, P["carbon"])
    L.rect(3, 2, 56, 2, P["gris"])
    L.caja(1, 0, 58, 35, P["negro"], esquina=True)
    L.rect(5, 4, 54, 30, h("1e2433") if pantalla_on else P["tinta"])
    if pantalla_on:
        # renglones de letra, el de en medio resaltado
        for k, (y, w, c) in enumerate(((8, 30, P["gris"]), (13, 38, P["perla"]), (18, 42, P["blanco"]),
                                       (23, 26, P["perla"]), (27, 34, P["gris"]))):
            L.rect(9, y, 9 + w, y + 1, c)
    L.p(52, 32, P["verde"])
    # teclado y raton
    L.rect(6, 45, 45, 49, P["humo"])
    for x in range(8, 44, 3):
        L.p(x, 46, P["perla"])
        L.p(x + 1, 48, P["perla"])
    L.caja(5, 44, 46, 50, P["gris"], esquina=True)
    L.rect(51, 45, 55, 49, P["humo"])
    L.caja(50, 44, 56, 50, P["gris"], esquina=True)
    L.p(53, 45, P["gris"])
    return L.im


def ap_cel():
    """Celular parado en su base de madera. 26 x 46."""
    L = nuevo(26, 46)
    L.rect(4, 1, 21, 37, P["tinta"])
    L.caja(3, 0, 22, 38, P["negro"], esquina=True)
    L.rect(5, 4, 20, 33, h("2b2f45"))
    # portada (rosa como la etiqueta del disco) y barrita
    L.rect(7, 7, 18, 18, M.PORTADA)
    L.rect(8, 8, 17, 8, h("f07aa0"))
    L.rect(7, 22, 18, 22, P["perla"])
    L.rect(7, 22, 12, 22, P["verde"])
    L.mapa(10, 26, ["X...X.X", "XX..X.X", "XXX.X.X", "XX..X.X", "X...X.X"], {"X": P["crema"]})
    L.rect(11, 35, 14, 35, P["gris"])
    tabla(L, 0, 36, 25, 45, veta=False)
    return L.im


def ap_bocina():
    """Bocina de piso de madera, dos conos. 32 x 56."""
    L = nuevo(32, 56)
    tabla(L, 1, 0, 30, 53)
    L.rect(4, 3, 27, 50, P["madera"])
    for (cy, r) in ((15, 7), (36, 10)):
        L.elipse(16, cy, r + 1, r + 1, P["contorno_mad"])
        L.elipse(16, cy, r, r, P["tinta"])
        L.elipse(16, cy, r - 3, r - 3, P["carbon"])
        L.elipse(16, cy, 2, 2, P["gris"])
        L.p(14, cy - 2, P["gris"])
    L.p(16, 5, P["verde"])
    L.rect(4, 54, 7, 55, P["negro"])
    L.rect(24, 54, 27, 55, P["negro"])
    return L.im


def ap_tele():
    """Tele vieja de madera con antenas y perillas. 66 x 56."""
    L = nuevo(66, 56)
    # antenas
    L.linea(33, 12, 18, 0, P["gris"])
    L.linea(33, 12, 50, 1, P["gris"])
    L.rect(17, 0, 18, 0, P["perla"])
    L.rect(50, 0, 51, 1, P["perla"])
    L.elipse(33, 12, 3, 2, P["carbon"])
    # caja
    tabla(L, 1, 13, 64, 51)
    L.rect(5, 17, 47, 47, P["negro"])
    L.rect(6, 18, 46, 46, h("2a3b54"))
    for y in range(19, 46):
        L.rect(7, y, 45, y, M.mezcla(h("4a86c0"), h("a8d4e6"), (y - 19) / 26))
    # brillito y esquinas redondas de la pantalla
    for k in range(5):
        L.p(10 + k, 22 - k // 2, P["blanco"]) if k < 4 else None
    for (x, y) in ((6, 18), (46, 18), (6, 46), (46, 46)):
        L.p(x, y, P["negro"])
    # panel de perillas
    L.rect(50, 17, 60, 47, P["madera_med"])
    L.caja(49, 16, 61, 48, P["madera_osc"])
    for cy in (23, 33):
        L.elipse(55, cy, 3, 3, P["tinta"])
        L.elipse(55, cy, 2, 2, P["perla"])
        L.p(55, cy - 2, P["tinta"])
    for y in range(40, 46, 2):
        L.rect(52, y, 58, y, P["madera_osc"])
    L.rect(6, 52, 10, 55, P["nogal"])
    L.rect(55, 52, 59, 55, P["nogal"])
    return L.im


ICO16 = {
    "PC": ["................", ".XXXXXXXXXXXXXX.", ".XSSSSSSSSSSSSX.", ".XSLLLLLLSSSSSX.", ".XSSSSSSSSSSSSX.",
           ".XSLLLLLLLLLSSX.", ".XSSSSSSSSSSSSX.", ".XSLLLLLSSSSSSX.", ".XSSSSSSSSSSSSX.", ".XXXXXXXXXXXXXX.",
           "......XXXX......", "......XXXX......", "....XXXXXXXX....", "................", "................",
           "................"],
    "celular": ["......XXXXX.....", ".....XXXXXXX....", ".....XSSSSSX....", ".....XPPPPSX....", ".....XPPPPSX....",
                ".....XPPPPSX....", ".....XSSSSSX....", ".....XSLLLSX....", ".....XSSSSSX....", ".....XSSSSSX....",
                ".....XSSSSSX....", ".....XXXXXXX....", "......XXXXX.....", "................", "................",
                "................"],
    "bocina": [".....MMMMMM.....", "....MWWWWWWM....", "....MWXXXXWM....", "....MWXDDXWM....", "....MWXXXXWM....",
               "....MWWWWWWM....", "....MWXXXXWM....", "....MXXXXXXM....", "....MXXDDXXM....", "....MXXDDXXM....",
               "....MWXXXXWM....", "....MWWWWWWM....", ".....MMMMMM.....", "................", "................",
               "................"],
    "TV": ["...X.......X....", "....X.....X.....", ".....X...X......", "......XXX.......", ".MMMMMMMMMMMMMM.",
           ".MXXXXXXXXXWWWM.", ".MXCCCCCCCXWDWM.", ".MXCCCCCCCXWWWM.", ".MXCCCCCCCXWDWM.", ".MXCCCCCCCXWWWM.",
           ".MXXXXXXXXXWWWM.", ".MMMMMMMMMMMMMM.", "..MM.......MM...", "................", "................",
           "................"],
}
ICO_COL = {"X": P["tinta"], "S": h("2b2f45"), "L": P["perla"], "P": M.PORTADA, "M": P["contorno_mad"],
           "W": P["madera"], "D": P["carbon"], "C": h("6fa8d8")}


def icono(tipo):
    L = nuevo(16, 16)
    L.mapa(0, 0, ICO16[tipo], ICO_COL)
    return L.im


APARATOS = [("PC del estudio", "PC", ap_pc), ("Galaxy S24", "celular", ap_cel), ("Bocina sala", "bocina", ap_bocina),
            ("Tele", "TV", ap_tele)]


# ---------------------------------------------------------------------------------------------------------------
# Ayuda: el texto real (strings.xml help_text) partido en secciones, y un dibujito por seccion
# ---------------------------------------------------------------------------------------------------------------

def leer_ayuda():
    import re
    xml = open(os.path.join(M.RES, "values", "strings.xml"), encoding="utf-8").read()
    s = re.search(r'<string name="help_text">(.*?)</string>', xml, re.S).group(1)
    s = s.replace("\\n", "\n")
    return [p.strip() for p in re.split(r"\n\s*\n", s) if p.strip()]


TITULOS = ["Botones de arriba", "Con música, abajo", "Toques", "Fondos", "Protector de pantalla", "Reproducir en…",
           "Traducción", "Modo tranquilo", "Un secreto"]


def secciones():
    out = []
    for i, p in enumerate(leer_ayuda()):
        t = TITULOS[i] if i < len(TITULOS) else "Más"
        # si el parrafo empieza con su titulo ("Fondos: ..."), se quita para no repetirlo
        for pref in (t + ": ", t + ", "):
            if p.startswith(pref):
                p = p[len(pref):]
                p = p[0].upper() + p[1:]
        out.append((t, p))
    return out


def mano():
    """Mano con el dedo tocando y las ondas del toque (doble toque)."""
    L = nuevo(40, 44)
    for r in (6, 10):
        L.anillo(12, 9, r, r - 3, P["naranja"] if r == 6 else P["oro"])
    filas = [
        "...........KK.........",
        "..........KCCK........",
        "..........KCCK........",
        "..........KCCK........",
        "..........KCCKKK......",
        "..........KCCKCCKKK...",
        "......KK..KCCKCCKCCKK.",
        ".....KCCK.KCCKCCKCCKCK",
        ".....KCCCKKCCCCCCCCKCK",
        "......KCCCKCCCCCCCCCCK",
        ".......KCCCCCCCCCCCCCK",
        "........KCCCCCCCCCCCSK",
        "........KCCCCCCCCCCSK.",
        ".........KCCCCCCCCSSK.",
        "..........KCCCCCCSSK..",
        "...........KCCCCCSK...",
        "...........KBBBBBBK...",
        "...........KBBBBBBK...",
    ]
    L.mapa(2, 6, filas, {"K": P["contorno_mad"], "C": P["durazno"], "S": P["teja"], "B": P["teal"]})
    return L.im


def grupo(piezas, sep=10, alto=None):
    """Pone varias imagenes en fila, alineadas abajo."""
    w = sum(p.width for p in piezas) + sep * (len(piezas) - 1)
    hh = alto or max(p.height for p in piezas)
    out = Image.new("RGBA", (w, hh), (0, 0, 0, 0))
    x = 0
    for p in piezas:
        out.alpha_composite(p, (x, hh - p.height))
        x += p.width + sep
    return out


def recortar(im):
    return im.crop(im.getbbox())


def dibujo_seccion(i):
    """El dibujito de cada hoja: el control del que habla, con el arte de verdad."""
    if i == 0:
        v = ventanita(mini(1, 36, 36))
        return grupo([png("boton_ayuda_0"), v, png("boton_opciones_0")], 16)
    if i == 1:
        return grupo([png("tocadiscos_mueble"), png("corneta"), recortar(png("barra_foco_grande"))], 8)
    if i == 2:
        return grupo([mano(), recortar(perro("hunter", "look_up", 3))], 8)
    if i == 3:
        a = M.postal(1, 0.0, True)
        b = M.postal(3, 0.0, False)
        out = Image.new("RGBA", (a.width + 70, a.height + 8), (0, 0, 0, 0))
        out.alpha_composite(b, (70, 8))
        out.alpha_composite(a, (0, 0))
        L = Lienzo(out)
        M.chinche(L, a.width // 2 - 3, 0)
        return out
    if i == 4:
        return grupo([obj_protector(), obj_luna(True)], 6)
    if i == 5:
        return grupo([ap_pc(), ap_bocina(), ap_cel()], 10)
    if i == 6:
        return grupo([obj_libro("ES"), flecha_doble(), obj_libro("EN")], 4)
    if i == 7:
        a = recortar(perro("hunter", "sleep_curl", 6))
        b = recortar(perro("tango", "sleep_side", 6))
        return grupo([obj_luna(True), a, b], 6)
    if i == 8:
        a = recortar(perro("hunter", "sit", 0))
        b = recortar(perro("tango", "sit", 0))
        g = grupo([a, b], 4)
        out = Image.new("RGBA", (g.width, g.height + 14), (0, 0, 0, 0))
        out.alpha_composite(g, (0, 14))
        L = Lienzo(out)
        L.mapa(g.width // 2 - 3, 0, ["XXXXX.", "X...XX", "...XX.", "..XX..", "......", "..XX.."], {"X": P["naranja"]})
        return out
    return Image.new("RGBA", (10, 10))


def flecha_doble():
    L = nuevo(20, 12)
    L.mapa(0, 0, ["...X........X...", "..XX........XX..", ".XXXXXXXXXXXXXX.", "XXXXXXXXXXXXXXXX",
                  ".XXXXXXXXXXXXXX.", "..XX........XX..", "...X........X..."], {"X": P["topo"]})
    return L.im


def tiza(im, c=None, solo_orilla=False):
    """Version en gis: orilla del dibujo + sus lineas oscuras, con huecos de gis; sin rellenos."""
    c = c or (236, 236, 222, 255)
    src = im.load()
    w, hh = im.size
    out = Image.new("RGBA", (w, hh), (0, 0, 0, 0))
    o = out.load()
    for y in range(hh):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 128:
                continue
            orilla = any(not (0 <= x + dx < w and 0 <= y + dy < hh) or src[x + dx, y + dy][3] < 128
                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            oscuro_ = (r * 3 + g * 6 + b) / 10 < 70 and not solo_orilla
            if (orilla or oscuro_) and ruido(x, y) % 9:
                o[x, y] = c
    return out


# ---------------------------------------------------------------------------------------------------------------
# Ayuda A: la libretita de espiral. Una seccion por hoja, con el dibujito pegado con cinta; pestanas de colores
# a la derecha (una por seccion) para brincar; la esquina doblada pasa la hoja.
# ---------------------------------------------------------------------------------------------------------------

PEST_COL = [P["naranja"], P["teal"], P["oro"], h("d23b5f"), P["lila"], P["verde"], P["azulgris"], P["teal_osc"],
            P["madera_luz"]]
RENGLON = 22


def cinta(L, x, y, w=16, hh=7):
    """Cinta de papel (masking) medio transparente."""
    for yy in range(y, y + hh):
        for xx in range(x, x + w):
            if (xx == x or xx == x + w - 1) and (yy - y) % 2:
                continue
            r, g, b, a = L.px[xx, yy] if 0 <= xx < L.w and 0 <= yy < L.h else (0, 0, 0, 0)
            L.p(xx, yy, (236, 222, 170, 200) if a == 0 else M.mezcla((r, g, b, 255), (236, 222, 170, 255), 0.6))


def pegar_dibujo(C, d, cx, y):
    """Pega el dibujito centrado en cx con una 'foto' de papel blanco y dos cintas."""
    L = C.papel
    pad = 6
    x0 = cx - d.width // 2 - pad
    y0 = y
    x1 = x0 + d.width + 2 * pad
    y1 = y0 + d.height + 2 * pad
    sombra(L, x0 + 2, y0 + 2, x1 + 2, y1 + 2, 0.85)
    L.rect(x0, y0, x1, y1, P["blanco"])
    L.caja(x0, y0, x1, y1, P["niebla"])
    pegar_recortado(L.im, d, x0 + pad, y0 + pad)
    L.px = L.im.load()
    cinta(L, x0 - 6, y0 - 2, 18, 7)
    cinta(L, x1 - 11, y1 - 4, 18, 7)
    return y1


def hoja(C, x0, y0, x1, y1, margen=True, renglones_desde=None):
    """Hoja de cuaderno: papel crema, renglones azules y margen rojo."""
    L = C.papel
    L.rect(x0, y0, x1, y1, P["crema"])
    L.rect(x1, y0 + 1, x1, y1, P["niebla"])
    if renglones_desde is not None:
        for y in range(renglones_desde, y1 - 4, RENGLON):
            L.rect(x0 + 2, y, x1 - 2, y, h("b9d3e6"))
    if margen:
        L.rect(x0 + 34, y0, x0 + 34, y1, h("e9a0a0"))
        L.rect(x0 + 36, y0, x0 + 36, y1, h("f1c4c4"))
    L.caja(x0, y0, x1, y1, P["piedra"])


def espiral_h(L, x0, x1, y):
    """Espiral arriba: argollas cada 14 px que entran por hoyitos."""
    for x in range(x0, x1, 14):
        L.rect(x + 2, y + 9, x + 4, y + 11, P["tinta"])         # hoyito
        L.rect(x + 1, y, x + 2, y + 10, P["gris"])
        L.rect(x + 3, y, x + 4, y + 10, P["perla"])
        L.p(x + 3, y, P["blanco"])
        L.caja(x, y - 1, x + 5, y + 10, P["tinta"], esquina=True)


def espiral_v(L, x, y0, y1):
    for y in range(y0, y1, 14):
        L.rect(x - 8, y + 2, x + 8, y + 3, P["perla"])
        L.rect(x - 8, y + 4, x + 8, y + 4, P["gris"])
        L.caja(x - 9, y + 1, x + 9, y + 5, P["tinta"], esquina=True)
        L.rect(x - 12, y + 2, x - 10, y + 4, P["tinta"])
        L.rect(x + 10, y + 2, x + 12, y + 4, P["tinta"])


def pestanas(C, x, y0, alto, actual, total):
    """Separadores de colores en la orilla derecha: uno por seccion; el actual sale mas."""
    L = C.marco
    ph = alto // total
    for k in range(total):
        y = y0 + k * ph
        sale = 16 if k == actual else 11
        c = PEST_COL[k % len(PEST_COL)]
        L.rect(x, y + 2, x + sale, y + ph - 3, c)
        L.rect(x, y + 2, x + sale - 1, y + 2, M.mezcla(c, P["blanco"], 0.4))
        L.caja(x - 1, y + 1, x + sale, y + ph - 2, P["tinta"], esquina=True)
        # numerito de la seccion
        digitos(L, x + sale - 6, y + ph // 2 - 3, str(k + 1), P["crema"] if k != 2 else P["nogal"])


def esquina_doblada(L, x1, y1, t=18):
    """Esquina de abajo a la derecha doblada (pasa a la hoja siguiente)."""
    for k in range(t):
        for j in range(t - k):
            L.p(x1 - k, y1 - j, (0, 0, 0, 0))
    for k in range(t):
        L.rect(x1 - t + 1 + k, y1 - t + 1 + k, x1 - t + 1 + k, y1, P["humo"] if k < t - 1 else P["piedra"])
        L.p(x1 - t + 1 + k, y1 - t + 1 + k, P["piedra"])
    L.linea(x1 - t + 1, y1, x1, y1 - t + 1, P["piedra"])
    L.mapa(x1 - t + 6, y1 - 7, ["X..", "XX.", "XXX", "XX.", "X.."], {"X": P["madera_osc"]})


def puntos_hoja(L, cx, y, actual, total):
    x = cx - (total * 10) // 2
    for k in range(total):
        if k == actual:
            L.elipse(x + 4, y, 3, 3, P["naranja"])
            L.anillo(x + 4, y, 3, 3, P["madera_osc"])
        else:
            L.anillo(x + 4, y, 2, 2, P["piedra"])
        x += 10


def boton_pixel(C, x, y, w, texto_):
    """Boton de madera en pixel con texto del sistema (para 'Abrir ajustes del protector')."""
    B = nuevo(w, 37)
    tabla(B, 0, 0, w - 1, 33, veta=False)
    B.rect(1, 34, w - 2, 35, (0, 0, 0, 90))
    C.encima.append((B.im, x, y, "obj"))
    C.t((x + w // 2, y + 16), texto_, F15, (252, 240, 214, 255), (220, 214, 226, 255), ancla="mm")


def texto_renglones(C, x, y, ancho, s, f=F16):
    lineas = envolver(s, ancho, f)
    for k, ln in enumerate(lineas):
        C.t((x, y + k * RENGLON), ln, f, ancla="ls")
    return y + len(lineas) * RENGLON


def libreta(seccion, horiz=False):
    """La libreta abierta en la hoja 'seccion' (0..8)."""
    S = secciones()
    total = len(S)
    titulo, parrafo = S[seccion]
    dib = dibujo_seccion(seccion)
    # los dibujitos chicos se pegan a 2x (como los perros grandes de la pantalla)
    if dib.width * 2 <= (300 if not horiz else 360) and dib.height * 2 <= 130:
        dib = x2(dib)
    if not horiz:
        W, H = 552, 476
        C = Capa(W + 6, H + 6)
        L = C.marco
        # tapa de atras (se ve alrededor) y el grueso de las hojas abajo
        L.rect(0, 8, W - 18, H - 1, P["teal_osc"])
        L.caja(0, 8, W - 18, H - 1, P["tinta"], esquina=True)
        L.rect(1, H - 3, W - 19, H - 2, h("1d4538"))
        pestanas(C, W - 18, 40, H - 90, seccion, total)
        for k in range(3):
            C.papel.rect(5 + k, H - 9 + k * 2, W - 24 + k, H - 9 + k * 2, P["humo"] if k % 2 == 0 else P["niebla"])
        x0, y0, x1, y1 = 5, 16, W - 24, H - 12
        hoja(C, x0, y0, x1, y1, renglones_desde=None)
        espiral_h(C.marco, x0 + 12, x1 - 6, 8)
        # dibujo pegado
        yd = pegar_dibujo(C, dib, (x0 + x1) // 2 + 14, y0 + 26)
        # titulo y parrafo sobre renglones
        yt = max(yd + 34, y0 + 150)
        C.t((x0 + 46, yt), titulo, F18B, (40, 60, 92, 255), (30, 40, 70, 255), ancla="ls")
        yr = yt + RENGLON
        for y in range(yt + 4, y1 - 44, RENGLON):
            C.papel.rect(x0 + 2, y, x1 - 2, y, h("b9d3e6"))
        C.papel.rect(x0 + 34, y0, x0 + 34, y1, h("e9a0a0"))
        yfin = texto_renglones(C, x0 + 46, yr, x1 - x0 - 66, parrafo)
        if seccion == 4:
            boton_pixel(C, x0 + 46, yfin + 4, 200, "Abrir ajustes del protector")
        # pie: hoja anterior, puntitos, esquina doblada
        C.papel.mapa(x0 + 12, y1 - 22, ["..X", ".XX", "XXX", ".XX", "..X"], {"X": P["madera_osc"]})
        puntos_hoja(C.papel, (x0 + x1) // 2, y1 - 20, seccion, total)
        C.t(((x0 + x1) // 2, y1 - 36), "%d de %d" % (seccion + 1, total), F14, (120, 110, 100, 255), (60, 56, 70, 255), ancla="mm")
        esquina_doblada(C.papel, x1, y1, 22)
        # mordida de Tango en la esquina de abajo a la izquierda de la tapa
        for (mx, my) in ((0, H - 12), (0, H - 8), (2, H - 10), (1, H - 4)):
            L.rect(mx, my, mx + 2, my + 1, (0, 0, 0, 0))
        return C
    # horizontal: libreta abierta, dos hojas (izquierda el dibujo, derecha el texto), espiral en medio
    W, H = 820, 452
    C = Capa(W + 24, H + 6)
    L = C.marco
    L.rect(0, 0, W - 1, H - 1, P["teal_osc"])
    L.caja(0, 0, W - 1, H - 1, P["tinta"], esquina=True)
    pestanas(C, W - 1, 30, H - 60, seccion, total)
    mid = W // 2
    hoja(C, 8, 8, mid - 10, H - 12, margen=False)
    hoja(C, mid + 10, 8, W - 9, H - 12, margen=True)
    for k in range(2):
        C.papel.rect(8, H - 10 + k * 2, W - 9, H - 10 + k * 2, P["humo"])
    espiral_v(C.marco, mid, 16, H - 20)
    d = dib
    pegar_dibujo(C, d, (8 + mid - 10) // 2, max(30, (H - d.height) // 2 - 40))
    C.t(((8 + mid - 10) // 2, H - 60), titulo, F18B, (40, 60, 92, 255), (30, 40, 70, 255), ancla="mm")
    x0 = mid + 10
    for y in range(44, H - 40, RENGLON):
        C.papel.rect(x0 + 2, y, W - 11, y, h("b9d3e6"))
    C.papel.rect(x0 + 34, 8, x0 + 34, H - 12, h("e9a0a0"))
    yfin = texto_renglones(C, x0 + 46, 40, W - 9 - x0 - 64, parrafo)
    if seccion == 4:
        boton_pixel(C, x0 + 46, yfin + 6, 200, "Abrir ajustes del protector")
    C.papel.mapa(20, H - 34, ["..X", ".XX", "XXX", ".XX", "..X"], {"X": P["madera_osc"]})
    puntos_hoja(C.papel, x0 + (W - 9 - x0) // 2, H - 28, seccion, total)
    esquina_doblada(C.papel, W - 9, H - 12, 22)
    return C


def pantalla_ayuda_a(n, seccion=1, horiz=False):
    scr = pantalla(n, horiz)
    velo(scr)
    C = libreta(seccion, horiz)
    art = C.render(n)
    if horiz:
        x0, y0 = 90, 74
    else:
        x0, y0 = 26, 74
    scr.alpha_composite(art, (x0, y0))
    # Tango le muerde la esquina a la libreta (a 2x, como los perros grandes)
    t = tango_hueso(4)
    t = t if n < 0.5 else noche(t)
    if horiz:
        scr.alpha_composite(t, (x0 - 40, y0 + art.height - t.height - 4))
    else:
        scr.alpha_composite(t, (x0 - 22, y0 + art.height - t.height + 6))
    boton(scr, "ayuda", 1, n, horiz)
    return scr


# ---------------------------------------------------------------------------------------------------------------
# Ayuda B: el letrero se voltea y es un pizarron. Todo el texto en una sola hoja que se desliza; a la izquierda de
# cada parrafo su dibujito en gis. Abajo el borrador y los gises; Hunter sentado en la repisita.
# ---------------------------------------------------------------------------------------------------------------

GIS = (238, 236, 220, 255)
GIS_N = (214, 214, 206, 255)


def pizarra(W, H, n, scroll=0, columnas=1):
    C = Capa(W, H + 40)
    L = C.marco
    # marco
    tabla(L, 0, 0, W - 1, H - 1)
    # pizarra verde con manchas de borrador
    bx0, by0, bx1, by1 = 10, 10, W - 11, H - 11
    for y in range(by0, by1 + 1):
        for x in range(bx0, bx1 + 1):
            r = ruido(x // 3, y // 2)
            L.p(x, y, h("2f5a43") if r % 11 else h("386a50"))
    for k in range(6):
        ex, ey = 40 + (ruido(k, 7) % (W - 120)), 30 + (ruido(7, k) % (H - 80))
        for y in range(ey, ey + 10):
            for x in range(ex, ex + 36):
                if ruido(x, y) % 3 == 0:
                    L.p(x, y, h("3e7158"))
    L.caja(bx0 - 1, by0 - 1, bx1 + 1, by1 + 1, P["contorno_mad"])
    # repisita de abajo con gises y borrador
    tabla(L, -0, H - 4, W - 1, H + 6, veta=False)
    L.rect(60, H - 8, 75, H - 5, P["blanco"])
    L.caja(59, H - 9, 76, H - 4, P["perla"])
    L.rect(84, H - 7, 95, H - 5, h("f5aac0"))
    L.caja(83, H - 8, 96, H - 4, h("c07a90"))
    L.rect(W - 120, H - 13, W - 80, H - 5, P["madera_luz"])
    L.rect(W - 120, H - 8, W - 80, H - 5, P["tinta"])
    L.caja(W - 121, H - 14, W - 79, H - 4, P["contorno_mad"], esquina=True)
    # titulo en gis
    C.t((W // 2, by0 + 20), "¿Cómo se usa?", F18B, GIS, GIS_N, ancla="mm")
    L.linea(W // 2 - 70, by0 + 33, W // 2 + 70, by0 + 34, (220, 220, 205, 200))
    # contenido: secciones una tras otra; se recortan al area visible (scroll)
    S = secciones()
    area_y0, area_y1 = by0 + 44, by1 - 6
    capa_txt = []
    col_w = (bx1 - bx0 - 30 - (columnas - 1) * 24) // columnas
    xcol = [bx0 + 16 + c * (col_w + 24) for c in range(columnas)]
    ycol = [area_y0 - scroll] * columnas
    for i, (titulo, parrafo) in enumerate(S):
        c = ycol.index(min(ycol)) if columnas > 1 else 0
        x, y = xcol[c], ycol[c]
        d = tiza(dibujo_seccion(i), solo_orilla=(i == 3))
        if d.width > col_w - 10:
            d = d.crop((0, 0, col_w - 10, d.height))
        if y + d.height > area_y0 and y < area_y1:
            dd = d.crop((0, max(0, area_y0 - y), d.width, min(d.height, area_y1 - y)))
            pegar_recortado(L.im, dd, x, max(y, area_y0))
            L.px = L.im.load()
        y += d.height + 18
        lineas = [(titulo, F16B)] + [(ln, F16) for ln in envolver(parrafo, col_w, F16)]
        for ln, f in lineas:
            if area_y0 + 12 <= y <= area_y1:
                capa_txt.append(((x, y), ln, f))
            y += 21
        ycol[c] = y + 14
    for xy, s, f in capa_txt:
        C.t(xy, s, f, GIS, GIS_N, ancla="ls")
    # barra de scroll en gis
    total = max(ycol) + scroll - area_y0
    vis = area_y1 - area_y0
    if total > vis:
        bh = max(30, vis * vis // total)
        by = area_y0 + (vis - bh) * scroll // max(1, total - vis)
        for y in range(area_y0, area_y1, 3):
            L.p(bx1 - 6, y, (200, 210, 200, 120))
        L.rect(bx1 - 8, by, bx1 - 5, by + bh, GIS)
    return C


def pantalla_ayuda_b(n, scroll=0, horiz=False):
    scr = pantalla(n, horiz)
    velo(scr)
    if horiz:
        W, H, x0, y0, col = 920, 440, 52, 76, 2
    else:
        W, H, x0, y0, col = 560, 600, 20, 76, 1
    C = pizarra(W, H, n, scroll, columnas=col)
    scr.alpha_composite(C.render(n), (x0, y0))
    # el clavo y el hilo: el letrero volteado ahora cuelga grande
    Lc = Lienzo(scr)
    bx, by = BTN["h" if horiz else "v"]["ayuda"]
    # Hunter sentado en la repisita de los gises, viendo la pizarra
    hu = perro("hunter", "sit", 0)
    hu = recortar(hu)
    hu = x2(hu)
    hu = hu if n < 0.5 else noche(hu)
    scr.alpha_composite(hu, (x0 + W - 70 - hu.width, y0 + H - hu.height - 2))
    boton(scr, "ayuda", 2, n, horiz)
    return scr


# ---------------------------------------------------------------------------------------------------------------
# Reproducir en... A: la charola sube con un pedazo del cuarto: un mueble largo con los aparatos de la casa encima.
# El que suena echa notitas y Hunter esta echado junto a el con sus audifonos. Al tocar otro, las notitas viajan
# en arco y Hunter corre para alla.
# ---------------------------------------------------------------------------------------------------------------

def placa(C, x, y, w, nombre, tipo, activa, hundida=False):
    """Placa de laton (activa) o de lamina (las demas) en el frente del mueble."""
    L = C.papel
    hh = 40
    if hundida:
        y += 2
    luz, med, osc = (P["amarillo"], P["oro"], P["naranja"]) if activa else (P["humo"], P["niebla"], P["perla"])
    L.rect(x, y, x + w - 1, y + hh - 1, med)
    L.rect(x + 1, y + 1, x + w - 2, y + 1, luz)
    L.rect(x + 1, y + hh - 2, x + w - 2, y + hh - 2, osc)
    L.caja(x, y, x + w - 1, y + hh - 1, P["madera_osc"] if activa else P["topo"], esquina=True)
    for tx_ in (x + 3, x + w - 4):
        L.p(tx_, y + hh // 2, P["madera_osc"] if activa else P["piedra"])
    col = (70, 34, 8, 255) if activa else TINTA_TXT
    C.t((x + w // 2, y + 13), nombre, F16B, col, (40, 24, 20, 255), ancla="mm")
    C.t((x + w // 2, y + 29), tipo, F15, col, (40, 24, 20, 255), ancla="mm")


def notas_saliendo(L, x, y, k=0):
    for j, (dx, dy, c, g) in enumerate(((0, 0, P["naranja"], True), (13, -11, P["teal"], False),
                                        (4, -23, h("d23b5f"), True), (17, -32, P["oro"], False))):
        L.mapa(x + dx + (k + j) % 2 + 1, y + dy + 1, M.G_NOTA2 if g else M.G_NOTA, {"X": P["contorno_mad"]})
        nota(L, x + dx + (k + j) % 2, y + dy, c, g)


def mueble_aparatos(W, n, activo=0, viajando_a=None):
    H = 182
    C = Capa(W, H + 4)
    C.fondo = Image.new("RGBA", (W, H + 4), (0, 0, 0, 0))
    C.fondo.paste(M.pared(W, n, H), (0, 4))
    L = C.marco
    # marco y agarradera (igual que la charola de postales)
    L.caja(0, 3, W - 1, H + 3, P["contorno_mad"])
    L.rect(1, 4, W - 2, 4, P["madera_luz"])
    L.rect(W // 2 - 22, 0, W // 2 + 21, 5, P["madera"])
    L.rect(W // 2 - 21, 0, W // 2 + 20, 0, P["madera_luz"])
    L.caja(W // 2 - 23, -1, W // 2 + 22, 5, P["contorno_mad"], esquina=True)
    L.rect(W // 2 - 12, 2, W // 2 + 11, 2, P["nogal"])
    # letrero en la pared
    lw = 176
    L.linea(20 + lw // 2, 14, 30, 22, P["tinta"])
    L.linea(20 + lw // 2, 14, 20 + lw - 11, 22, P["tinta"])
    L.rect(20 + lw // 2 - 1, 13, 20 + lw // 2, 14, P["carbon"])
    tabla(L, 20, 21, 20 + lw - 1, 44, veta=False)
    C.t((20 + lw // 2, 32), "¿Dónde reproducir?", F16B, (252, 240, 214, 255), (220, 214, 226, 255), ancla="mm")
    # mueble: tapa, frente con placas, patas (piso de la pared: ultimas 12 filas)
    piso = H + 4 - 12
    fy0 = piso - 54
    tabla(L, 10, fy0 - 7, W - 11, fy0, veta=False, med=P["madera_luz"], luz=P["durazno"], osc=P["madera"])
    tabla(L, 14, fy0, W - 15, piso - 4)
    for xx in (20, W // 2 - 2, W - 26):
        L.rect(xx, piso - 4, xx + 5, piso + 1, P["nogal"])
        L.caja(xx - 1, piso - 4, xx + 6, piso + 2, P["contorno_mad"])
    L.rect(16, piso + 2, W - 17, piso + 3, (0, 0, 0, 70))
    n_ap = len(APARATOS)
    slot = (W - 28) // n_ap
    sup = fy0 - 7           # donde se paran los aparatos
    xs = []
    for i, (nombre, tipo, fn) in enumerate(APARATOS):
        sx = 14 + i * slot
        a = fn()
        ax = sx + (slot - a.width) // 2 - (14 if i == activo and viajando_a is None else 0)
        ay = sup - a.height + 1
        L.rect(ax + 3, sup, ax + a.width - 4, sup, (0, 0, 0, 90))
        pegar_recortado(L.im, a, ax, ay)
        L.px = L.im.load()
        xs.append((ax, ay, a.width, a.height))
        placa(C, sx + 6, fy0 + 7, slot - 12, nombre, tipo, activa=(i == activo), hundida=(i == viajando_a))
    ax, ay, aw, ah = xs[activo]
    if viajando_a is None:
        notas_saliendo(L, ax + aw - 6, ay - 2)
        # Hunter echado con sus audifonos junto al que suena
        hu = recortar(perro("hunter", "listen_lie", 6))
        C.encima.append((hu, ax + aw - 2, sup - hu.height + 1, "perro"))
    else:
        # las notitas viajan en arco del que sonaba al nuevo; Hunter corre para alla
        bx, by, bw, bh = xs[viajando_a]
        x0, y0 = ax + aw // 2, ay - 4
        x1, y1 = bx + bw // 2, by - 4
        import math
        for k in range(1, 9):
            t = k / 9
            x = int(x0 + (x1 - x0) * t)
            y = int(y0 + (y1 - y0) * t - math.sin(t * math.pi) * 46)
            if k % 2:
                nota(L, x, y, [P["naranja"], P["teal"], h("d23b5f")][k % 3])
            else:
                L.rect(x + 2, y + 4, x + 3, y + 5, P["crema"])
        notas_saliendo(L, bx + bw - 6, by - 2, 1)
        hu = recortar(perro("hunter", "run", 2))
        if x1 < x0:
            hu = M.espejo(hu)
        C.encima.append((hu, (x0 + x1) // 2 - hu.width // 2 + 20, sup - hu.height + 1, "perro"))
        for k in range(4):
            L.rect((x0 + x1) // 2 - 16 - k * 9, sup - 3 - (k % 2), (x0 + x1) // 2 - 13 - k * 9, sup - 3 - (k % 2),
                   P["perla"])
    return C


def pantalla_aparatos_a(n, horiz=False, activo=0, viajando_a=None):
    scr = pantalla(n, horiz)
    velo(scr)
    if horiz:
        W = 760
        x0 = (1024 - W) // 2
    else:
        W = 568
        x0 = 16
    C = mueble_aparatos(W, n, activo, viajando_a)
    art = C.render(n)
    scr.alpha_composite(art, (x0, scr.height - art.height))
    return scr


# ---------------------------------------------------------------------------------------------------------------
# Reproducir en... B: el radio. Cada aparato es una estacion en el cuadrante; la aguja roja esta en el que suena.
# Tocar otra estacion: la aguja se desliza (con estatica), la perilla de sintonia gira. De noche el cuadrante brilla.
# ---------------------------------------------------------------------------------------------------------------

def radio(W, n, activo=0, moviendo_a=None):
    H = 206
    C = Capa(W, H + 40)
    L = C.marco
    top = 40                       # arriba: Hunter echado en el radio
    # gabinete con esquinas de arriba redondeadas
    x0, y0, x1, y1 = 0, top, W - 1, top + H - 1
    tabla(L, x0, y0, x1, y1)
    for k, c in enumerate((12, 8, 5, 3, 2, 1, 1)):
        for xx in range(c):
            L.p(x0 + xx, y0 + k, (0, 0, 0, 0))
            L.p(x1 - xx, y0 + k, (0, 0, 0, 0))
        L.p(x0 + c, y0 + k, P["contorno_mad"])
        L.p(x1 - c, y0 + k, P["contorno_mad"])
    L.rect(14, y0, W - 15, y0, P["contorno_mad"])
    # moldura
    L.rect(8, y0 + 18, W - 9, y0 + 19, P["madera_osc"])
    L.rect(8, y0 + 20, W - 9, y0 + 20, P["madera_luz"])
    # insignia de laton con el titulo
    bw = 196
    bx = W // 2 - bw // 2
    L.rect(bx, y0 + 4, bx + bw - 1, y0 + 15, P["oro"])
    L.rect(bx + 1, y0 + 5, bx + bw - 2, y0 + 5, P["amarillo"])
    L.caja(bx, y0 + 4, bx + bw - 1, y0 + 15, P["madera_osc"], esquina=True)
    C.t((W // 2, y0 + 10), "¿DÓNDE REPRODUCIR?", F14B, (90, 40, 10, 255), (50, 30, 20, 255), ancla="mm")
    # bocina de tela (izquierda)
    gx0, gy0, gx1, gy1 = 14, y0 + 30, 104, y1 - 16
    for y in range(gy0, gy1 + 1):
        for x in range(gx0, gx1 + 1):
            L.p(x, y, P["piedra"] if (x + y) % 2 else P["perla"])
    for x in range(gx0 + 6, gx1, 12):
        L.rect(x, gy0, x + 2, gy1, P["madera"])
        L.rect(x + 2, gy0, x + 2, gy1, P["madera_osc"])
    L.caja(gx0 - 1, gy0 - 1, gx1 + 1, gy1 + 1, P["contorno_mad"], esquina=True)
    # cuadrante (vidrio crema con escala)
    dx0, dy0, dx1, dy1 = 118, y0 + 30, W - 16, y0 + 128
    ventana = nuevo(dx1 - dx0 + 1, dy1 - dy0 + 1)
    V = ventana
    vw, vh = V.w, V.h
    vid = P["crema"] if n < 0.5 else h("ffd98a")
    V.rect(0, 0, vw - 1, vh - 1, vid)
    V.rect(0, 0, vw - 1, 2, P["humo"] if n < 0.5 else h("ffe7b0"))
    n_ap = len(APARATOS)
    est = (vw - 20) // n_ap
    # escala: rayitas
    for x in range(8, vw - 8, 4):
        V.rect(x, 8, x, 12 if x % 20 else 16, P["topo"])
    V.rect(6, 7, vw - 7, 7, P["topo"])
    cx_est = []
    for i, (nombre, tipo, _) in enumerate(APARATOS):
        cx = 10 + i * est + est // 2
        cx_est.append(cx)
        ic = icono(tipo)
        pegar_recortado(V.im, ic, cx - 8, 22)
        V.px = V.im.load()
    # aguja
    ia = activo if moviendo_a is None else None
    if moviendo_a is None:
        ax = cx_est[activo]
    else:
        ax = (cx_est[activo] * 2 + cx_est[moviendo_a] * 3) // 5
    V.rect(ax, 4, ax, 44, ROJO[3])
    V.rect(ax + 1, 4, ax + 1, 44, ROJO[1])
    V.mapa(ax - 2, 45, ["..X..", ".XXX."], {"X": ROJO[3]})
    V.mapa(ax - 2, 2, ["XXXXX", ".XXX.", "..X.."], {"X": ROJO[3]})
    if moviendo_a is not None:
        # estatica: rayitas en zigzag a los lados de la aguja
        for k in range(3):
            yy = 6 + k * 12
            for j in range(10):
                V.p(ax + 5 + j, yy + (j % 3), P["topo"])
                V.p(ax - 14 + j, yy + 5 + (j % 3), P["topo"])
    V.caja(0, 0, vw - 1, vh - 1, P["tinta"], esquina=True)
    C.luz = getattr(C, "luz", [])
    C.luz.append((V.im, dx0, dy0))
    L.caja(dx0 - 2, dy0 - 2, dx1 + 2, dy1 + 2, P["oro"], esquina=True)
    L.caja(dx0 - 3, dy0 - 3, dx1 + 3, dy1 + 3, P["contorno_mad"], esquina=True)
    for i, (nombre, tipo, _) in enumerate(APARATOS):
        cx = dx0 + cx_est[i]
        c_on = i == activo and moviendo_a is None
        col = (150, 30, 20, 255) if c_on else TINTA_TXT
        C.t((cx, dy0 + 62), nombre, F16B if c_on else F16, col, (80, 30, 10, 255) if c_on else (60, 40, 20, 255), ancla="mm")
        C.t((cx, dy0 + 82), tipo, F15, col, (80, 30, 10, 255) if c_on else (60, 40, 20, 255), ancla="mm")
    # perillas: volumen y sintonia
    for k, (px_, etq) in enumerate(((dx0 + 70, "volumen"), (dx1 - 70, "sintonía"))):
        import math
        cy = dy1 + 34
        L.elipse(px_, cy + 1, 15, 15, P["contorno_mad"])
        L.elipse(px_, cy, 15, 15, P["nogal"])
        L.elipse(px_, cy, 13, 13, P["madera_osc"])
        L.elipse(px_ - 1, cy - 1, 10, 10, P["madera_med"])
        L.elipse(px_, cy, 8, 8, P["madera_osc"])
        ang = -60 if k == 0 else (-150 + 300 * ((ax - 10) / max(1, vw - 20)))
        r = math.radians(ang - 90 + 90)
        ex, ey = int(round(px_ + 11 * math.cos(math.radians(ang - 90)))), int(round(cy + 11 * math.sin(math.radians(ang - 90))))
        L.linea(px_, cy, ex, ey, P["crema"])
    # foquito de encendido
    L.elipse(W // 2 + 30, dy1 + 34, 3, 3, P["verde"])
    L.p(W // 2 + 29, dy1 + 33, P["crema"])
    # patas
    for xx in (20, W - 34):
        L.rect(xx, y1 + 1, xx + 13, y1 + 4, P["nogal"])
        L.caja(xx - 1, y1, xx + 14, y1 + 5, P["contorno_mad"])
    # Hunter echado arriba del radio con sus audifonos; Tango sentado al lado, oyendo
    hu = recortar(perro("hunter", "listen_lie", 6))
    C.encima.append((hu, W - 150, top - hu.height + 1, "perro"))
    return C


def render_con_luz(C, n):
    return C.render(n)


def pantalla_aparatos_b(n, horiz=False, activo=0, moviendo_a=None):
    scr = pantalla(n, horiz)
    velo(scr)
    if horiz:
        W = 700
        x0 = (1024 - W) // 2
    else:
        W = 568
        x0 = 16
    C = radio(W, n, activo, moviendo_a)
    art = render_con_luz(C, n)
    scr.alpha_composite(art, (x0, scr.height - art.height - 8))
    return scr


# ---------------------------------------------------------------------------------------------------------------
# Laminas
# ---------------------------------------------------------------------------------------------------------------

AMBAR = (250, 200, 120, 255)
GRIS_T = (170, 172, 185, 255)
COLW = 624


def hoy_aparatos():
    """No hay captura de 'Reproducir en...': se recrea el AlertDialog de opcion unica sobre la captura de opciones,
    con el mismo gris y la misma letra (se dice en la lamina que es recreado)."""
    scr = CAP["hoy_opc"].copy()
    L = Lienzo(scr)
    gris = scr.getpixel((300, 400))
    L.rect(26, 157, 573, 818, gris)
    L.rect(26, 300, 573, 818, (0, 0, 0, 0)) if False else None
    tx(scr, (50, 192), "¿Dónde reproducir?", _f("segoeuib.ttf", 19), (240, 240, 240, 255), "lm")
    for k, (nombre, tipo, _) in enumerate(APARATOS):
        y = 250 + k * 48
        cx = 62
        on = k == 0
        d = ImageDraw.Draw(scr)
        d.ellipse((cx - 9, y - 9, cx + 9, y + 9), outline=(128, 203, 196, 255) if on else (200, 200, 200, 255), width=2)
        if on:
            d.ellipse((cx - 4, y - 4, cx + 4, y + 4), fill=(128, 203, 196, 255))
        tx(scr, (92, y), "%s · %s" % (nombre, tipo), F16, (235, 235, 235, 255), "lm")
    tx(scr, (550, 783), "CERRAR", F14B, (128, 203, 196, 255), "rm")
    # el dialogo de verdad es mas chico: debajo de "CERRAR" va el fondo atenuado (color de la orilla, fila por fila)
    alto = 250 + 4 * 48
    L = Lienzo(scr)
    for yy in range(alto + 56, 820):
        L.rect(26, yy, 573, yy, scr.getpixel((12, yy)))
    L.rect(26, alto, 573, alto + 55, gris)
    tx(scr, (550, alto + 30), "CERRAR", F14B, (128, 203, 196, 255), "rm")
    out = scr
    return out


def lamina(nombre, titulo, hoy, hoy_etq, a_nombre, b_nombre, fa, fb, extra_a, extra_b, detalles, notas):
    """hoy: imagen 600x1024. fa/fb(n, horiz) -> pantalla. detalles: lista de (titulo, imagen). notas: renglones."""
    W = 24 + 5 * COLW + 140
    y_fila2 = 76 + 1024 + 40
    H = y_fila2 + 40 + 600 + 40 + 24 * len(notas) + 20
    lam = Image.new("RGBA", (W, H), M.FONDO_LAM)
    M.texto(lam, (24, 14), titulo, M.F_TIT)
    cols = [(hoy_etq, hoy), ("%s · día" % a_nombre, fa(0.0, False)), ("%s · noche" % a_nombre, fa(1.0, False)),
            ("%s · día" % b_nombre, fb(0.0, False)), ("%s · noche" % b_nombre, fb(1.0, False))]
    for k, (etq, im) in enumerate(cols):
        x = 24 + k * COLW
        M.texto(lam, (x, 50), etq, M.F_ETQ, AMBAR if k else GRIS_T)
        lam.alpha_composite(im, (x, 76))
    # fila 2: horizontales + detalles
    x = 24
    for etq, im in (("%s · horizontal (1024 × 600)" % a_nombre, fa(0.0, True)),
                    ("%s · horizontal (1024 × 600)" % b_nombre, fb(0.0, True))):
        M.texto(lam, (x, y_fila2), etq, M.F_ETQ, AMBAR)
        lam.alpha_composite(im, (x, y_fila2 + 26))
        x += 1024 + 24
    xd = x
    yd = y_fila2
    ancho_col = 0
    for etq, im in detalles:
        if yd + 22 + im.height > y_fila2 + 640:
            # siguiente columna de detalles
            xd += ancho_col + 20
            yd = y_fila2
            ancho_col = 0
        ancho_col = max(ancho_col, im.width)
        M.texto(lam, (xd, yd), etq, M.F_PEQ, GRIS_T)
        lam.alpha_composite(im, (xd, yd + 18))
        yd += im.height + 30
    yn = y_fila2 + 26 + 600 + 24
    for k, s in enumerate(notas):
        M.texto(lam, (24, yn + k * 24), s, M.F_ETQ if k == 0 else M.F_PEQ, AMBAR if k == 0 else (215, 215, 225, 255))
    guardar(lam, nombre)


def guardar(im, nombre):
    os.makedirs(SALIDA, exist_ok=True)
    im = im.convert("RGB")
    im.save(os.path.join(SALIDA, nombre + ".png"))
    im.resize((im.width * 2, im.height * 2), Image.NEAREST).save(os.path.join(SALIDA, nombre + "-x2.png"))
    print("ok", nombre, im.size)


def zoom(im, k=2):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def lamina_opciones():
    V_MUCHO = {3: {"valor": "mucho", "control": ("perilla", 3, 2), "objeto": obj_persiana(2)}}
    V_OFF = {4: {"valor": "apagado", "control": ("switch", False), "objeto": obj_luna(False)}}
    V_EN = {5: {"valor": "inglés", "control": ("perilla", 3, 1), "objeto": obj_libro("EN")}}
    # recortes de las fichas (escala real y x2)
    a_antes = pantalla_opciones_a(0.0).crop((30, 222, 586, 278))
    a_toque = pantalla_opciones_a(0.0, levantada=3, valores=V_MUCHO).crop((30, 222, 586, 278))
    a_off = pantalla_opciones_a(0.0, valores=V_OFF).crop((30, 270, 586, 326))
    a_en = pantalla_opciones_a(0.0, valores=V_EN).crop((30, 317, 586, 373))
    b_antes = pantalla_opciones_b(0.0).crop((300, 166, 590, 226))
    b_toque = pantalla_opciones_b(0.0, tocado=3, valores=V_MUCHO).crop((300, 166, 590, 226))
    b_volt = pantalla_opciones_b(0.0, tocado=3, volteando=3, valores=V_MUCHO).crop((300, 166, 590, 226))
    det = [
        ("A · tocar «Oscurecer»: antes (x1)", a_antes),
        ("A · al tocar: la ficha se levanta, la perilla da un clic y la persiana baja (x1)", a_toque),
        ("A · otros valores: Modo tranquilo apagado (luna gris, interruptor abajo)", a_off),
        ("A · Traducir a: inglés (el libro cambia de tapa, perilla 2.º clic)", a_en),
        ("B · antes / el objeto brinca y la tarjetita se voltea / valor nuevo (x2)", zoom(grupo([b_antes, b_volt], 8))),
        ("", zoom(b_toque)),
    ]
    notas = [
        "Recomiendo A, el cajón con fichas: se lee como una lista (un renglón por opción, 44 px), cada control dice qué hace "
        "(perilla = varios valores, interruptor = sí/no, pestañita = abre otra cosa) y es la comodita de verdad abierta.",
        "A: el cajón sale de la comodita (botón en su estado «abierto») y cae hasta abajo; tocar el frente o fuera lo cierra. "
        "Fieltro verde azulado, fichas de biblioteca con su renglón rojo, objeto a la izquierda que cambia con el valor.",
        "A: Tango se echa en el hueco que deja «Cerrar sesión» mordiendo el hueso del cajón. En horizontal van 2 columnas "
        "y cabe sin scroll (351 px de alto). En vertical mide 593 px: no tapa la píldora.",
        "B: la repisa del cuarto (papel tapiz y foquitos de la cápsula): cada opción es un objeto en la tabla con su tarjetita "
        "doblada; tocar el objeto lo cambia (brinca, la tarjeta se voltea). Más escenita, pero sin un control que diga "
        "«esto gira» y con 2 columnas en vertical: se lee menos rápido.",
        "Texto del sistema: Segoe UI aquí (en la tablet sería Roboto), 16 px nombre y 16 px negrita el valor; el arte "
        "(marco, objetos, perillas) es pixel a 1x. Noche: madera con el filtro de los objetos y papel con uno más suave.",
    ]
    lamina("maqueta-opciones", "Menú de opciones en pixel  (escala real, 600 × 1024 y 1024 × 600, sobre capturas reales)",
           CAP["hoy_opc"], "Hoy (captura real)", "A  El cajón de la comodita", "B  La repisa del cuarto",
           lambda n, hz: pantalla_opciones_a(n, hz), lambda n, hz: pantalla_opciones_b(n, hz), None, None, det, notas)


def lamina_ayuda():
    hojas = [pantalla_ayuda_a(0.0, i).crop((26, 74, 590, 300)) for i in (0, 3, 8)]
    det = [("A · otras hojas: 1 Botones de arriba", hojas[0]), ("A · 4 Fondos", hojas[1]),
           ("A · 9 Un secreto", hojas[2]),
           ("B · deslizado hacia abajo (la barra de gis baja)", pantalla_ayuda_b(0.0, scroll=520).crop((20, 76, 580, 420)))]
    notas = [
        "Recomiendo A, la libretita: una sección por hoja se lee en un vistazo, el dibujito es el control de verdad "
        "(el tocadiscos, la comodita, la postal…) y las pestañas de colores dejan brincar a cualquier sección.",
        "A: se pasa la hoja deslizando o con la esquina doblada (abajo a la derecha) y la flechita de la izquierda; "
        "«2 de 9» y los puntitos dicen dónde vas. En la hoja 5 va el botón «Abrir ajustes del protector» (hoy botón del diálogo).",
        "A: Tango muerde la esquina de la libreta (la tapa tiene la mordida). En horizontal la libreta se abre: dibujo a la "
        "izquierda, texto a la derecha, espiral en medio. Los dibujitos chicos se pegan a 2x, como los perros grandes.",
        "B: el letrero «?» se voltea y es un pizarrón: todo el texto en gis, cada párrafo con su dibujito en gis (sacado "
        "del contorno del arte de verdad), se desliza; abajo gises y borrador, Hunter sentado en la repisita.",
        "B es más bonito de ver pero es un muro de texto con scroll (lo mismo que hoy, con otro marco). El texto es el "
        "help_text real de strings.xml partido en sus 9 párrafos; los títulos de sección los puse yo.",
    ]
    lamina("maqueta-ayuda", "Ayuda («Cómo se usa») en pixel  (escala real, sobre capturas reales)",
           CAP["hoy_ayuda"], "Hoy (captura real)", "A  La libretita", "B  El pizarrón",
           lambda n, hz: pantalla_ayuda_a(n, 1 if n < 0.5 else 7, hz) if not hz else pantalla_ayuda_a(n, 4, hz),
           lambda n, hz: pantalla_ayuda_b(n, 0, hz), None, None, det, notas)


def lamina_aparatos():
    det = [
        ("A · al tocar «Bocina sala»: la placa se hunde, las notitas viajan en arco y Hunter corre (día)",
         pantalla_aparatos_a(0.0, viajando_a=2).crop((16, 1024 - 192, 584, 1024))),
        ("A · lo mismo de noche", pantalla_aparatos_a(1.0, viajando_a=2).crop((16, 1024 - 192, 584, 1024))),
        ("B · al tocar «Bocina sala»: la aguja se desliza con estática y la perilla gira",
         pantalla_aparatos_b(0.0, moviendo_a=2).crop((16, 1024 - 260, 584, 1024))),
    ]
    notas = [
        "Recomiendo A, el mueble con los aparatos: se reconoce cada aparato de un vistazo, la placa de latón dice cuál "
        "suena y el «viaje» (notitas en arco, Hunter corriendo) cuenta lo que pasa mientras Spotify contesta.",
        "A: sube desde abajo como la charola de postales (misma agarradera y mismo marco), con un pedazo del cuarto. "
        "El que suena: placa de latón, notitas saliendo y Hunter echado junto a él con sus audífonos.",
        "A: los aparatos se dibujan por tipo (PC, celular, bocina, TV, tablet; otro tipo → una bocina). Con más de 4, el "
        "mueble se desliza de lado como las postales (el quinto se asoma).",
        "B: un radio viejo; cada aparato es una estación del cuadrante y la aguja roja está en el que suena. De noche el "
        "cuadrante brilla ámbar. Es muy de juguete, pero con 5+ aparatos el cuadrante se aprieta y los iconos son de 16 px.",
        "Hoy no tengo captura de este diálogo: la columna «Hoy» está recreada con el mismo gris y la misma letra que el de "
        "Opciones (AlertDialog con lista de opción única).",
    ]
    lamina("maqueta-aparatos", "«Reproducir en…» en pixel  (escala real, sobre capturas reales)",
           hoy_aparatos(), "Hoy (RECREADO, no hay captura)", "A  El mueble con los aparatos", "B  El radio",
           lambda n, hz: pantalla_aparatos_a(n, hz), lambda n, hz: pantalla_aparatos_b(n, hz), None, None, det, notas)


if __name__ == "__main__":
    lamina_opciones()
    lamina_ayuda()
    lamina_aparatos()
