"""Maquetas (imagenes, no codigo de la app) de los controles en pixel art de Letras.

Transporte (anterior / play-pausa / siguiente) en una capsula hermana de la del volumen, y tres formas de cambiar
de fondo. Todo se dibuja a la resolucion real de la tablet (1 px de arte = 1 px de pantalla, densidad 1.0) y cada
lamina se guarda tambien ampliada x2 con vecino mas cercano.

Uso:  python arte/controles/maquetas.py
Sale en docs/capturas/controles-pixel/.

Arte que se toma del proyecto (no se baja nada):
  - cuarto_dia.png / cuarto_noche.png (PixelLab): fondo de las escenas, la pared y los colores.
  - perros_hunter.png / perros_tango.png + res/raw/perros.json: Hunter y Tango.
  - antes-0-ahora.png / antes-1-pildora-vertical.png (capturas reales): la pantalla de fondo y la miniatura "Lofi".
Lo demas (tocadiscos, casetera, teclas, postales, cuadros, cortina, miniaturas "Cielo" y "Boda") se dibuja aqui.
"""

import json
import math
import os

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
RES = os.path.join(RAIZ, "android", "app", "src", "main", "res")
NODPI = os.path.join(RES, "drawable-nodpi")
SALIDA = os.path.join(RAIZ, "docs", "capturas", "controles-pixel")


def cargar(nombre):
    return Image.open(os.path.join(NODPI, nombre)).convert("RGBA")


CUARTO = {False: cargar("cuarto_dia.png"), True: cargar("cuarto_noche.png")}
PILDORA = Image.open(os.path.join(SALIDA, "antes-1-pildora-vertical.png")).convert("RGBA")
AHORA = Image.open(os.path.join(SALIDA, "antes-0-ahora.png")).convert("RGBA")
INDICE = json.load(open(os.path.join(RES, "raw", "perros.json"), encoding="utf-8"))
HOJAS = {"hunter": cargar("perros_hunter.png"), "tango": cargar("perros_tango.png")}


def h(c):
    """'a15523' -> (r, g, b, 255)"""
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


# Paleta: sacada de cuarto_dia.png (los nombres son por uso).
P = {k: h(v) for k, v in dict(
    negro="241d1f", tinta="2e2a2b", carbon="403c3b", gris="524d4d", topo="775e5a", piedra="a28c7b",
    perla="b1a4a1", niebla="c3b7b6", hueso="dad2d2", humo="e6e1dd", crema="f8f4df", pared="eae5ca",
    madera_luz="d08039", madera="c77a36", madera_med="af622a", madera_osc="974d21", nogal="542411",
    contorno_mad="41170a", vino="5d1915", teja="cc6b2e", naranja="e16a13", oro="f3a22d", amarillo="fdc83f",
    verde="74a235", verde_osc="4c821a", bosque="0c361b", teal="5a9185", teal_luz="72a99c", teal_osc="2e6554",
    cian="35dbfb", cian_luz="98f5fb", lila="75739d", azulgris="93a3ba", rosa="e2cca9", blanco="f0fafc",
    durazno="f5aa6c", cafe="684225",
).items()}
# Rojos de la cortina (de vino 5d1915 hacia teja; no hay mas rojos en el cuarto).
ROJO = [h("3a0d0a"), h("5d1915"), h("86261b"), h("ac3a24"), h("cf5a34")]
# Etiqueta del disco = color de la portada (Chicha Libre en la captura: rosa) y la de la siguiente cancion.
PORTADA = h("e0457b")
PORTADA_SIG = P["cian"]


# ---------------------------------------------------------------------------------------------------------------
# Herramientas de dibujo a pixel (sin antialias)
# ---------------------------------------------------------------------------------------------------------------

class Lienzo:
    def __init__(self, im):
        self.im = im
        self.px = im.load()
        self.w, self.h = im.size

    def p(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        """Relleno, extremos incluidos."""
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                self.px[x, y] = c

    def caja(self, x0, y0, x1, y1, c, esquina=False):
        """Contorno de 1 px; con esquina=True se cortan las 4 esquinas."""
        a = 1 if esquina else 0
        for x in range(x0 + a, x1 + 1 - a):
            self.p(x, y0, c)
            self.p(x, y1, c)
        for y in range(y0 + a, y1 + 1 - a):
            self.p(x0, y, c)
            self.p(x1, y, c)

    def linea(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.p(x0, y0, c)
            if x0 == x1 and y0 == y1:
                return
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def elipse(self, cx, cy, a, b, c):
        """Elipse rellena centrada en el pixel (cx, cy) con semiejes a, b."""
        for y in range(int(cy - b - 1), int(cy + b + 2)):
            for x in range(int(cx - a - 1), int(cx + a + 2)):
                if ((x - cx) / (a + 0.5)) ** 2 + ((y - cy) / (b + 0.5)) ** 2 <= 1.0:
                    self.p(x, y, c)

    def anillo(self, cx, cy, a, b, c):
        """Solo la orilla (1 px) de la elipse."""
        dentro = lambda x, y: ((x - cx) / (a + 0.5)) ** 2 + ((y - cy) / (b + 0.5)) ** 2 <= 1.0
        for y in range(int(cy - b - 1), int(cy + b + 2)):
            for x in range(int(cx - a - 1), int(cx + a + 2)):
                if dentro(x, y) and not (dentro(x - 1, y) and dentro(x + 1, y) and dentro(x, y - 1) and dentro(x, y + 1)):
                    self.p(x, y, c)

    def arco(self, cx, cy, a, b, desde, hasta, c, paso=3):
        """Puntos sobre la orilla de una elipse, de 'desde' a 'hasta' grados (0 = derecha, sentido horario)."""
        for g in range(desde, hasta + 1, paso):
            r = math.radians(g)
            self.p(int(round(cx + a * math.cos(r))), int(round(cy + b * math.sin(r))), c)

    def mapa(self, x, y, filas, colores):
        """Dibuja un mapa de caracteres: cada letra es un color de 'colores', '.' es vacio."""
        for j, fila in enumerate(filas):
            for i, ch in enumerate(fila):
                if ch in colores:
                    self.p(x + i, y + j, colores[ch])

    def pegar(self, spr, x, y):
        self.im.alpha_composite(spr, (x, y)) if 0 <= x and 0 <= y and x + spr.width <= self.w and y + spr.height <= self.h \
            else pegar_recortado(self.im, spr, x, y)
        self.px = self.im.load()

    def oscurecer(self, x0, y0, x1, y1, k=0.6):
        for y in range(max(0, y0), min(self.h, y1 + 1)):
            for x in range(max(0, x0), min(self.w, x1 + 1)):
                r, g, b, a = self.px[x, y]
                self.px[x, y] = (int(r * k), int(g * k), int(b * k), a)


def pegar_recortado(dst, spr, x, y):
    """alpha_composite que aguanta que el sprite se salga del lienzo."""
    sx0, sy0 = max(0, -x), max(0, -y)
    sx1, sy1 = min(spr.width, dst.width - x), min(spr.height, dst.height - y)
    if sx1 <= sx0 or sy1 <= sy0:
        return
    dst.alpha_composite(spr.crop((sx0, sy0, sx1, sy1)), (x + sx0, y + sy0))


def nuevo(w, hh, c=(0, 0, 0, 0)):
    return Lienzo(Image.new("RGBA", (w, hh), c))


def noche(im, n=1.0):
    """El mismo filtro que ya usa la app con los perros: LightingColorFilter(k, k, k + 25) con k = 255 - 90 n."""
    k = 255 - 90 * n
    m = (k / 255, k / 255, min(255, k + 25) / 255)
    r, g, b, a = im.split()
    r = r.point(lambda v: int(v * m[0]))
    g = g.point(lambda v: int(v * m[1]))
    b = b.point(lambda v: int(v * m[2]))
    return Image.merge("RGBA", (r, g, b, a))


def noche_obj(im):
    """Para lo dibujado a mano (teclas, tocadiscos, casetera...): un filtro mas fuerte que el de los perros para que
    no brille contra el cuarto de noche. En Kotlin: LightingColorFilter(0x6E73AA, 0x000012)."""
    mul, add = (0x6E, 0x73, 0xAA), (0, 0, 0x12)
    r, g, b, a = im.split()
    r = r.point(lambda v: min(255, v * mul[0] // 255 + add[0]))
    g = g.point(lambda v: min(255, v * mul[1] // 255 + add[1]))
    b = b.point(lambda v: min(255, v * mul[2] // 255 + add[2]))
    return Image.merge("RGBA", (r, g, b, a))


def espejo(im):
    return im.transpose(Image.FLIP_LEFT_RIGHT)


# ---------------------------------------------------------------------------------------------------------------
# Perros
# ---------------------------------------------------------------------------------------------------------------

def perro(nombre, anim, cuadro=0):
    cw, ch = INDICE[nombre]["celda"]
    fila = INDICE["filas"].index(anim)
    n = INDICE[nombre]["cuadros"][fila]
    cuadro %= max(1, n)
    return HOJAS[nombre].crop((cuadro * cw, fila * ch, (cuadro + 1) * cw, (fila + 1) * ch))


def recorte_alfa(im):
    caja = im.getbbox()
    return im.crop(caja) if caja else im


# ---------------------------------------------------------------------------------------------------------------
# Glifos (en pixel, 1 = color, 2 = sombra/relieve que se pinta 1 px abajo)
# ---------------------------------------------------------------------------------------------------------------

G_ANT = [  # anterior: barra + triangulo a la izquierda (9 x 9)
    "XX.....X",
    "XX....XX",
    "XX...XXX",
    "XX..XXXX",
    "XX.XXXXX",
    "XX..XXXX",
    "XX...XXX",
    "XX....XX",
    "XX.....X",
]
G_SIG = [f[::-1] for f in G_ANT]
G_PLAY = [
    "X....",
    "XX...",
    "XXX..",
    "XXXX.",
    "XXXXX",
    "XXXX.",
    "XXX..",
    "XX...",
    "X....",
]
G_PAUSA = ["XXX.XXX"] * 9
G_PLAY7 = ["X...", "XX..", "XXX.", "XXXX", "XXX.", "XX..", "X..."]
G_PAUSA7 = ["XX.XX"] * 7
G_ATRAS7 = ["...X...X", "..XX..XX", ".XXX.XXX", "XXXXXXXX", ".XXX.XXX", "..XX..XX", "...X...X"]
G_ADEL7 = [f[::-1] for f in G_ATRAS7]
G_PLAYPAUSA7 = ["X.....X.X", "XX....X.X", "XXX...X.X", "XXXX..X.X", "XXX...X.X", "XX....X.X", "X.....X.X"]
G_NOTA = [
    "..XXX",
    "..X.X",
    "..X.X",
    "..X..",
    "XXX..",
    "XXX..",
    ".X...",
]
G_NOTA2 = [
    "..XXXX",
    "..X..X",
    "..X..X",
    "..X..X",
    "XXX.XX",
    "XXXXXX",
    ".X..X.",
]


def glifo(L, x, y, filas, c, relieve=None):
    if relieve is not None:
        L.mapa(x, y + 1, filas, {"X": relieve})
    L.mapa(x, y, filas, {"X": c})


def ancho(filas):
    return len(filas[0])


# ---------------------------------------------------------------------------------------------------------------
# Capsula: el marco igual al del volumen (degradado vertical, borde 5, sombra interna) y el hueco en forma de estadio
# ---------------------------------------------------------------------------------------------------------------

BORDE = 5
ALTO_ESCENA = 64


def mezcla(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def marco(w, hh, n):
    """Igual que VolumeCapsule.drawShell (vectorial, con antialias, como lo dibuja Android)."""
    s = 4
    big = Image.new("RGBA", (w * s, hh * s), (0, 0, 0, 0))
    arriba = mezcla(h("f4f4f8"), h("55555f"), n)
    abajo = mezcla(h("bfbfcb"), h("26262e"), n)
    grad = Image.new("RGBA", (1, hh * s))
    for y in range(hh * s):
        grad.putpixel((0, y), mezcla(arriba, abajo, y / (hh * s - 1)))
    grad = grad.resize((w * s, hh * s))
    mask = Image.new("L", (w * s, hh * s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * s - 1, hh * s - 1), radius=hh * s // 2, fill=255)
    big.paste(grad, (0, 0), mask)
    # sombra fina por dentro (2 px, negro al 27%)
    sombra = Image.new("RGBA", (w * s, hh * s), (0, 0, 0, 0))
    b = BORDE * s
    ImageDraw.Draw(sombra).rounded_rectangle((b - s, b - s, w * s - b + s - 1, hh * s - b + s - 1),
                                             radius=(hh * s - 2 * b) // 2 + s, outline=(0, 0, 0, 70), width=2 * s)
    big.alpha_composite(sombra)
    return big.resize((w, hh), Image.LANCZOS)


def mascara_estadio(w, hh):
    m = Image.new("L", (w, hh), 0)
    r = hh / 2
    px = m.load()
    for y in range(hh):
        for x in range(w):
            cx = min(max(x + 0.5, r), w - r)
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - r) ** 2 <= r * r:
                px[x, y] = 255
    return m


def capsula(escena, n, encima=None, sobra=6):
    """Marco + escena recortada al estadio. 'encima' se dibuja sin recorte (lo que sobresale, como la lupa).
    Devuelve una imagen con 'sobra' px arriba y abajo (la lupa del volumen sobresale 6)."""
    w, hh = escena.width + 2 * BORDE, escena.height + 2 * BORDE
    out = Image.new("RGBA", (w, hh + 2 * sobra), (0, 0, 0, 0))
    out.alpha_composite(marco(w, hh, n), (0, sobra))
    hueco = Image.new("RGBA", escena.size, (0, 0, 0, 0))
    hueco.paste(escena, (0, 0), mascara_estadio(*escena.size))
    out.alpha_composite(hueco, (BORDE, BORDE + sobra))
    if encima is not None:
        out.alpha_composite(encima, (0, 0))
    return out


# ---------------------------------------------------------------------------------------------------------------
# Fondo de las escenas: la pared del cuarto (columnas 93..133 de cuarto_*.png, sin muebles) repetida
# ---------------------------------------------------------------------------------------------------------------

TIRA = (96, 128)


def pared(w, n, alto=ALTO_ESCENA):
    """Tira limpia del cuarto, repetida a lo ancho. Si 'alto' > 64, la parte del papel tapiz (y 8..37) se repite
    hacia arriba para hacer una pared mas alta (para la charola de cuadros)."""
    src = CUARTO[n > 0.5]
    tira = src.crop((TIRA[0], 0, TIRA[1], 64))
    tw = tira.width
    col = Image.new("RGBA", (tw, alto))
    if alto == 64:
        col.paste(tira, (0, 0))
    else:
        extra = alto - 64
        col.paste(tira.crop((0, 0, tw, 8)), (0, 0))                     # foquitos
        papel = tira.crop((0, 8, tw, 38))
        y = 8
        while y < 8 + extra + 30:
            col.paste(papel, (0, y))
            y += 30
        col.paste(tira.crop((0, 8, tw, 64)), (0, 8 + extra))
    out = Image.new("RGBA", (w, alto))
    x = 0
    k = 0
    while x < w:
        # alternar la tira en espejo evita que se note la costura del papel tapiz
        out.paste(col if k % 2 == 0 else espejo(col), (x, 0))
        x += tw
        k += 1
    return out


def oscuro(n):
    """Colores de los objetos dibujados a mano: de noche pasan por el mismo filtro que los perros."""
    return 1.0 if n > 0.5 else 0.0


# ---------------------------------------------------------------------------------------------------------------
# Teclas en pixel (las de los extremos de T1 y las tres de T3)
# ---------------------------------------------------------------------------------------------------------------

TECLA = dict(sombra=P["tinta"], borde=P["tinta"], luz=P["crema"], cara=P["humo"], cara_osc=P["niebla"],
             filo=P["perla"], frente=P["piedra"], frente_osc=P["topo"], glifo=P["carbon"], glifo_luz=P["crema"],
             activo=P["naranja"])


def tecla(L, x, y, w, alto_cara, alto_frente, hundida, filas_glifo, glifo_en="cara", pal=TECLA, hunde=2):
    """Tecla gruesa vista de 3/4: cara arriba (clara) y frente (oscuro). Hundida = la cara baja 'hunde' px y el
    frente se achica (el fondo queda fijo). El glifo va en la cara o en el frente."""
    p = hunde if hundida else 0
    abajo = y + alto_cara + alto_frente - 1
    # sombra en el piso / pared
    for xx in range(x + 1, x + w + 1):
        L.p(xx, abajo + 1, mezcla(L.px[xx, abajo + 1] if 0 <= xx < L.w and abajo + 1 < L.h else (0, 0, 0, 255), (0, 0, 0, 255), 0.45))
    for yy in range(y + p + 1, abajo + 1):
        if x + w < L.w:
            L.p(x + w, yy, mezcla(L.px[x + w, yy], (0, 0, 0, 255), 0.35))
    top = y + p
    corte = top + alto_cara  # primera fila del frente
    L.rect(x + 1, top + 1, x + w - 2, corte - 1, pal["cara"])
    L.rect(x + 1, corte, x + w - 2, abajo - 1, pal["frente"])
    L.rect(x + 1, abajo - 1, x + w - 2, abajo - 1, pal["frente_osc"])
    # luz arriba e izquierda, sombra a la derecha de la cara
    L.rect(x + 2, top + 1, x + w - 3, top + 1, pal["luz"])
    L.rect(x + 1, top + 2, x + 1, corte - 2, pal["luz"])
    L.rect(x + w - 2, top + 2, x + w - 2, corte - 1, pal["cara_osc"])
    L.rect(x + 2, corte - 1, x + w - 3, corte - 1, pal["cara_osc"])
    L.rect(x + 1, corte, x + w - 2, corte, pal["filo"])
    L.caja(x, top, x + w - 1, abajo, pal["borde"], esquina=True)
    # esquinas de adentro redondeadas (1 px)
    L.p(x + 1, top + 1, pal["borde"])
    L.p(x + w - 2, top + 1, pal["borde"])
    L.p(x + 1, abajo - 1, pal["borde"])
    L.p(x + w - 2, abajo - 1, pal["borde"])
    if filas_glifo:
        gw, gh = len(filas_glifo[0]), len(filas_glifo)
        gx = x + (w - gw) // 2
        if glifo_en == "cara":
            gy = top + (alto_cara - gh) // 2
            glifo(L, gx, gy, filas_glifo, pal["activo"] if hundida else pal["glifo"], pal["luz"])
        else:
            gy = corte + 1 + (abajo - corte - 1 - gh) // 2
            glifo(L, gx, gy, filas_glifo, pal["activo"] if hundida else pal["glifo_luz"], pal["frente_osc"])


# ---------------------------------------------------------------------------------------------------------------
# T1 El tocadiscos
# ---------------------------------------------------------------------------------------------------------------

ANCHO_T = 200  # hueco de la capsula de transporte: 200 x 64 (capsula 210 x 74)


def disco(L, cx, cy, etiqueta, cuadro, a=19, b=6):
    """Disco negro visto de 3/4. El brillo da vuelta segun el cuadro (4 cuadros)."""
    L.elipse(cx, cy, a, b, P["negro"])
    L.anillo(cx, cy, a, b, h("120d0e"))
    L.anillo(cx, cy, a - 5, b - 2, P["tinta"])
    L.anillo(cx, cy, a - 9, b - 3, P["tinta"])
    g = cuadro * 45
    L.arco(cx, cy, a - 3, b - 1, g, g + 40, P["topo"], paso=5)
    L.arco(cx, cy, a - 3, b - 1, g + 180, g + 220, P["gris"], paso=5)
    L.elipse(cx, cy, 5, 2, etiqueta)
    # una marca en la etiqueta que tambien da vuelta
    r = math.radians(g + 90)
    L.p(int(round(cx + 3 * math.cos(r))), int(round(cy + 1.2 * math.sin(r))), mezcla(etiqueta, (255, 255, 255, 255), 0.55))
    L.p(cx, cy, P["niebla"])


def tocadiscos(L, estado, cuadro=1, etiqueta=PORTADA):
    """Mesita + tocadiscos, ~64 x 56, centrado en x=100. estado: 'sonando' | 'pausa' | 'cambio'."""
    # ---- mesita
    x0, x1 = 68, 131
    sombra = mezcla(P["madera_osc"], (0, 0, 0, 255), 0.45)
    for x in range(x0 + 1, x1 + 2):
        L.p(x, 59, sombra)
        L.p(x, 60, sombra if x % 2 else L.px[x, 60])
    # patas de atras (mas oscuras) y repisa con discos
    for xp in (75, 123):
        L.rect(xp, 44, xp + 1, 55, P["nogal"])
    L.rect(74, 51, 125, 52, P["madera_osc"])
    L.rect(74, 53, 125, 53, P["nogal"])
    lomos = [P["naranja"], P["crema"], P["teal"], P["tinta"], P["amarillo"], P["vino"], P["lila"], P["crema"],
             P["verde"], P["tinta"], P["teja"], P["cian"], P["carbon"], P["durazno"]]
    for i, xx in enumerate(range(80, 80 + len(lomos) * 2, 2)):
        alto = 6 if i % 3 else 5
        L.rect(xx, 51 - alto, xx, 50, lomos[i])
        L.p(xx, 51 - alto, mezcla(lomos[i], (255, 255, 255, 255), 0.35))
        L.rect(xx + 1, 51 - alto + 1, xx + 1, 50, mezcla(lomos[i], (0, 0, 0, 255), 0.4))
    # patas de enfrente
    for xp in (70, 127):
        L.rect(xp, 44, xp + 2, 58, P["madera_med"])
        L.rect(xp + 2, 44, xp + 2, 58, P["madera_osc"])
        L.p(xp, 44, P["madera"])
        L.caja(xp - 1, 43, xp + 3, 59, P["contorno_mad"])
    # cubierta
    L.rect(x0, 40, x1, 44, P["madera"])
    L.rect(x0 + 1, 40, x1 - 1, 40, P["madera_luz"])
    L.rect(x0, 43, x1, 44, P["madera_osc"])
    L.caja(x0 - 1, 39, x1 + 1, 45, P["contorno_mad"], esquina=True)
    for xx in range(x0 + 3, x1 - 2, 9):  # veta
        L.p(xx, 41, P["madera_med"])
        L.p(xx + 1, 41, P["madera_med"])

    # ---- tocadiscos: cara de arriba plateada y frente de nogal con la tecla de play/pausa
    tx0, tx1, ty0, ty1, fy1 = 73, 126, 14, 29, 38
    L.rect(tx0, ty0, tx1, ty1, P["hueso"])
    L.rect(tx0 + 1, ty0, tx1 - 1, ty0, P["humo"])
    L.rect(tx0, ty1, tx1, ty1, P["perla"])
    L.rect(tx0, ty1 + 1, tx1, fy1, P["madera_osc"])
    L.rect(tx0, ty1 + 1, tx1, ty1 + 1, P["madera_med"])
    L.rect(tx0, fy1, tx1, fy1, P["nogal"])
    L.caja(tx0 - 1, ty0 - 1, tx1 + 1, fy1 + 1, P["tinta"], esquina=True)
    # patitas del tocadiscos
    L.rect(tx0 + 2, fy1 + 1, tx0 + 4, fy1 + 1, P["tinta"])
    L.rect(tx1 - 4, fy1 + 1, tx1 - 2, fy1 + 1, P["tinta"])
    # tecla del frente con el glifo (pausa si suena, play si esta en pausa)
    kx0, kx1, ky0, ky1 = 92, 106, 31, 37
    L.rect(kx0, ky0, kx1, ky1, P["nogal"])
    L.caja(kx0 - 1, ky0 - 1, kx1 + 1, ky1 + 1, P["contorno_mad"], esquina=True)
    g = G_PAUSA7 if estado == "sonando" else G_PLAY7
    glifo(L, kx0 + (kx1 - kx0 + 1 - ancho(g)) // 2, ky0, g, P["crema"])
    # foco: verde cuando suena
    L.p(79, 34, P["verde"] if estado == "sonando" else P["gris"])
    L.p(80, 34, P["verde_osc"] if estado == "sonando" else P["carbon"])
    if estado == "sonando":
        L.p(79, 33, h("b6f08a"))
    # perilla
    L.elipse(119, 34, 2, 2, P["niebla"])
    L.p(118, 33, P["crema"])
    L.p(120, 35, P["topo"])

    # plato
    cx, cy = 95, 21
    L.elipse(cx, cy + 1, 20, 6, P["topo"])
    L.elipse(cx, cy, 20, 6, P["perla"])
    if estado == "cambio":
        L.elipse(cx, cy, 17, 5, P["piedra"])
        L.anillo(cx, cy, 12, 3, P["perla"])
        L.p(cx, cy, P["tinta"])
        L.p(cx, cy - 1, P["niebla"])
    else:
        disco(L, cx, cy, etiqueta, cuadro)

    # brazo: pivote atras a la derecha
    L.elipse(121, 18, 3, 2, P["gris"])
    L.elipse(121, 17, 2, 1, P["niebla"])
    L.rect(124, 24, 125, 26, P["topo"])  # descanso del brazo
    if estado == "sonando":
        L.linea(121, 17, 111, 22, P["blanco"])
        L.linea(121, 18, 111, 23, P["perla"])
        L.rect(108, 22, 110, 23, P["carbon"])
        L.p(108, 24, P["tinta"])
    else:
        # levantado: el brazo apunta arriba y deja su sombra en el plato
        L.linea(120, 15, 113, 9, P["tinta"])
        L.linea(121, 16, 114, 10, P["blanco"])
        L.linea(122, 16, 115, 10, P["perla"])
        L.linea(123, 16, 116, 10, P["tinta"])
        L.rect(110, 8, 113, 10, P["carbon"])
        L.caja(109, 7, 114, 11, P["tinta"], esquina=True)
        L.p(110, 12, P["tinta"])
        # su sombra cae en el plato
        for xx in range(108, 116):
            L.p(xx, 20 + (xx - 108) // 3, P["gris"])


def escena_t1(estado, n, cuadro=1):
    L = Lienzo(pared(ANCHO_T, n))
    objs = nuevo(ANCHO_T, ALTO_ESCENA)
    if estado == "cambio":
        # el disco viejo sale a la derecha, el nuevo entra por la izquierda, y Tango cruza corriendo
        tocadiscos(objs, "cambio")
        disco(objs, 140, 18, PORTADA, 0)
        for yy, ln in ((15, 9), (18, 13), (21, 9)):
            objs.rect(124 - ln, yy, 124, yy, P["topo"])
        disco(objs, 58, 20, PORTADA_SIG, 2)
    else:
        tocadiscos(objs, estado, cuadro)
    if estado == "sonando":
        glifo(objs, 58, 6, G_NOTA, P["oro"], P["madera_osc"])
        glifo(objs, 132, 2, G_NOTA2, P["teal_osc"], P["teal_luz"])
        glifo(objs, 141, 12, G_NOTA, P["naranja"], P["vino"])
    capa = objs.im if n < 0.5 else noche_obj(objs.im)
    L.im.alpha_composite(capa)
    L.px = L.im.load()
    if estado == "cambio":
        tango = perro("tango", "run", 3)
        if n > 0.5:
            tango = noche(tango)
        # polvito detras
        for (xx, yy) in ((40, 58), (37, 56), (34, 59), (42, 55), (31, 57)):
            L.rect(xx, yy, xx + 1, yy + 1, P["pared"] if n < 0.5 else P["piedra"])
        L.pegar(tango, 44, 61 - tango.height + 1)
    # teclas de los extremos: en su propia capa (de noche se oscurece solo la tecla, no la pared)
    teclas = Lienzo(L.im.copy())
    tecla(teclas, 10, 14, 31, 26, 7, False, G_ANT)
    tecla(teclas, ANCHO_T - 41, 14, 31, 26, 7, estado == "cambio", G_SIG)
    if n > 0.5:
        solo = noche_obj(teclas.im)
        cambio = Image.new("L", L.im.size, 0)
        a, b = L.im.load(), teclas.im.load()
        m = cambio.load()
        for y in range(L.h):
            for x in range(L.w):
                if a[x, y] != b[x, y]:
                    m[x, y] = 255
        out = L.im.copy()
        out.paste(solo, (0, 0), cambio)
        return out
    return teclas.im


# ---------------------------------------------------------------------------------------------------------------
# T2 La casetera
# ---------------------------------------------------------------------------------------------------------------

def bocina(L, cx, cy, r, sonando):
    L.elipse(cx, cy, r, r, P["tinta"])
    L.elipse(cx, cy, r - 1, r - 1, P["perla"])
    L.elipse(cx, cy - 1, r - 2, r - 2, P["humo"])
    L.elipse(cx, cy, r - 3, r - 3, P["carbon"])
    # rejilla de puntitos
    for y in range(cy - r + 4, cy + r - 3):
        for x in range(cx - r + 4, cx + r - 3):
            if (x + y) % 3 == 0 and (x - cx) ** 2 + (y - cy) ** 2 <= (r - 4) ** 2:
                L.p(x, y, P["gris"])
    c = 5 if sonando else 4
    L.elipse(cx, cy, c, c, P["tinta"])
    L.elipse(cx, cy, c - 1, c - 1, P["topo"])
    L.p(cx - 1, cy - 2, P["piedra"])
    if sonando:
        L.anillo(cx, cy, r - 6, r - 6, P["topo"])


def carrete(L, cx, cy, cuadro, rapido=False, cinta=6):
    L.elipse(cx, cy, cinta, cinta, P["cafe"])
    L.anillo(cx, cy, cinta, cinta, P["nogal"])
    L.elipse(cx, cy, 4, 4, P["crema"])
    L.anillo(cx, cy, 4, 4, P["niebla"])
    L.elipse(cx, cy, 2, 2, P["tinta"])
    if rapido:
        # girando rapido: los dientes se vuelven un anillo borroso
        L.anillo(cx, cy, 3, 3, P["perla"])
        L.p(cx, cy, P["niebla"])
    else:
        for k in range(6):
            a = math.radians(k * 60 + cuadro * 20)
            L.p(int(round(cx + 3 * math.cos(a))), int(round(cy + 3 * math.sin(a))), P["crema"])
        L.p(cx, cy, P["niebla"])


def flecha_giro(L, cx, cy, r, c):
    """Flechita circular (sentido horario) alrededor de un carrete."""
    L.arco(cx, cy, r, r, 200, 330, c, paso=4)
    a = math.radians(330)
    x, y = int(round(cx + r * math.cos(a))), int(round(cy + r * math.sin(a)))
    L.p(x + 1, y, c)
    L.p(x, y + 1, c)
    L.p(x - 1, y - 1, c)


def escena_t2(estado, n, cuadro=1):
    W = ANCHO_T
    L = nuevo(W, ALTO_ESCENA, P["teal"])
    # cuerpo de la casetera: teal del zoclo del cuarto, con luz arriba y sombra abajo
    L.rect(0, 0, W - 1, 3, P["teal_luz"])
    L.rect(0, 58, W - 1, 63, P["teal_osc"])
    for x in range(0, W, 2):
        L.p(x, 57, P["teal_osc"])
    bocina(L, 31, 33, 21, estado == "sonando")
    bocina(L, W - 32, 33, 21, estado == "sonando")
    # ranura de las teclas
    L.rect(50, 1, 149, 18, P["tinta"])
    teclas = [(52, 25, G_ATRAS7), (78, 44, G_PLAYPAUSA7), (123, 25, G_ADEL7)]
    for i, (x, w, g) in enumerate(teclas):
        hundida = (i == 1 and estado == "sonando") or (i == 2 and estado == "cambio")
        pal = dict(TECLA, cara=P["hueso"], luz=P["blanco"], cara_osc=P["perla"], frente=P["perla"], frente_osc=P["piedra"])
        tecla(L, x, 2, w, 10, 5, hundida, g, pal=pal)
    # ventana del casete
    L.rect(53, 20, 146, 61, P["tinta"])
    L.rect(54, 21, 145, 60, P["negro"])
    # el casete
    L.rect(57, 23, 142, 58, P["carbon"])
    L.caja(56, 22, 143, 59, h("120d0e"), esquina=True)
    L.rect(57, 23, 142, 23, P["gris"])
    for (sx, sy) in ((59, 25), (140, 25), (59, 56), (140, 56)):  # tornillitos
        L.p(sx, sy, P["topo"])
    # etiqueta: banda del color de la portada
    L.rect(61, 25, 138, 48, P["crema"])
    L.rect(61, 26, 138, 30, PORTADA if estado != "cambio" else PORTADA_SIG)
    L.rect(61, 31, 138, 31, P["rosa"])
    for x in range(64, 136, 2):  # renglones "escritos" a mano (sin texto)
        if (x // 6) % 3:
            L.p(x, 28, P["crema"])
    L.rect(72, 33, 127, 46, P["negro"])
    L.caja(71, 32, 128, 47, P["piedra"])
    # cinta entre carretes (se ve por la ventana)
    L.rect(84, 45, 116, 45, P["cafe"])
    rapido = estado == "cambio"
    carrete(L, 86, 39, cuadro, rapido, cinta=6 if not rapido else 5)
    carrete(L, 114, 39, cuadro + 1, rapido, cinta=5 if not rapido else 6)
    if rapido:
        flecha_giro(L, 86, 39, 8, P["amarillo"])
        flecha_giro(L, 114, 39, 8, P["amarillo"])
        for yy in (36, 40, 44):  # rayitas de velocidad hacia la derecha
            L.rect(97, yy, 101, yy, P["oro"])
    # trapecio de abajo con hoyitos
    L.rect(70, 50, 129, 57, P["gris"])
    L.caja(69, 49, 130, 58, P["tinta"], esquina=True)
    for xx in (76, 92, 106, 122):
        L.rect(xx, 53, xx + 1, 54, P["negro"])
    if estado == "sonando":
        glifo(L, 6, 4, G_NOTA, P["amarillo"], P["teal_osc"])
        glifo(L, W - 12, 2, G_NOTA2, P["crema"], P["teal_osc"])
    return L.im if n < 0.5 else noche_obj(L.im)


# ---------------------------------------------------------------------------------------------------------------
# T3 Teclas (con Hunter sobre la de en medio)
# ---------------------------------------------------------------------------------------------------------------

def escena_t3(estado, n, cuadro=2):
    W = ANCHO_T
    fondo = pared(W, n)
    L = Lienzo(Image.new("RGBA", (W, ALTO_ESCENA), (0, 0, 0, 0)))
    tecla(L, 13, 30, 40, 18, 9, False, G_ANT)
    tecla(L, W - 53, 30, 40, 18, 9, estado == "cambio", G_SIG)
    sonando = estado == "sonando"
    tecla(L, 61, 28, 78, 16, 14, False, G_PAUSA if sonando else G_PLAY, glifo_en="frente")
    capa = L.im if n < 0.5 else noche_obj(L.im)
    fondo.alpha_composite(capa)
    L = Lienzo(fondo)
    # Hunter: parado cabeceando con audifonos si suena; echado dormido en pausa; volteando si cambia
    if sonando:
        d = perro("hunter", "headbob", cuadro)
        pie = 40
    elif estado == "pausa":
        d = perro("hunter", "sleep", 8)
        pie = 41
    else:
        d = perro("hunter", "walk", 1)
        pie = 40
    if n > 0.5:
        d = noche(d)
    bb = d.getbbox()
    d = d.crop(bb)
    x = 100 - d.width // 2 + (4 if estado == "cambio" else 0)
    L.pegar(d, x, pie - d.height + 1)
    if sonando:
        glifo(L, 63, 8, G_NOTA, P["oro"], P["madera_osc"])
        glifo(L, 130, 4, G_NOTA2, P["teal_osc"], P["teal_luz"])
    elif estado == "pausa":
        # zZ de pixel
        L.mapa(122, 12, ["XXX", "..X", ".X.", "XXX"], {"X": P["azulgris"]})
        L.mapa(127, 6, ["XXXX", "...X", "..X.", ".X..", "XXXX"], {"X": P["lila"]})
    else:
        for yy, ln in ((22, 6), (26, 9), (30, 6)):  # rayitas de "voy para alla"
            L.rect(78, yy, 78 + ln, yy, P["crema"] if n < 0.5 else P["piedra"])
    return L.im


# ---------------------------------------------------------------------------------------------------------------
# El volumen real (copia de VolumeCapsule.onDraw, para comparar)
# ---------------------------------------------------------------------------------------------------------------

def capsula_volumen(n, valor=0.55, cuadro=2):
    escena = CUARTO[n > 0.5].copy()
    anims = ("headbob", "headbob") if 0.34 <= valor < 0.7 else ("listen_lie", "listen_lie")
    for i, (nom, x) in enumerate((("hunter", 146), ("tango", 178))):
        d = perro(nom, anims[i], cuadro + i * 3)
        if i == 1:
            d = espejo(d)
        if n > 0.5:
            d = noche(d)
        pegar_recortado(escena, d, x - d.width // 2, 59 - d.height)
    con_perros = escena.copy()
    W, H = 266, 74
    lensW = 46
    lens_x = BORDE + lensW / 2 + valor * (256 - lensW)
    # lo que falta de volumen, mas apagado
    tapa = Image.new("RGBA", escena.size, (8, 8, 20, int(70 + 60 * n)))
    corte = int(lens_x - BORDE)
    escena.alpha_composite(tapa.crop((corte, 0, 256, 64)), (corte, 0))
    out = capsula(escena, n)
    # la lupa (vector con antialias en la app)
    s = 4
    lh = H + 12 - 2
    big = Image.new("RGBA", (W * s, (H + 12) * s), (0, 0, 0, 0))
    x0 = int((lens_x - lensW / 2) * s)
    y0 = 1 * s
    box = (x0, y0, x0 + lensW * s, y0 + lh * s)
    D = ImageDraw.Draw(big)
    D.rounded_rectangle((box[0] + 2 * s, box[1] + 4 * s, box[2] + 2 * s, box[3] + 4 * s), radius=lensW * s // 2, fill=(0, 0, 0, 45))
    adentro = Image.new("RGBA", (lensW, lh), mezcla(h("e9e9ef"), h("2e2e38"), n))
    zoom = 1.6
    zw, zh = int(256 * zoom), int(64 * zoom)
    zimg = con_perros.resize((zw, zh), Image.NEAREST)
    cx_room = (lens_x - BORDE) * zoom
    pegar_recortado(adentro, zimg, int(lensW / 2 - cx_room), int(lh / 2 - zh / 2))
    brillo = Image.new("RGBA", (lensW, lh), (0, 0, 0, 0))
    for y in range(lh // 2):
        ImageDraw.Draw(brillo).line((0, y, lensW, y), fill=(255, 255, 255, int(110 * (1 - y / (lh / 2)))))
    adentro.alpha_composite(brillo)
    ImageDraw.Draw(adentro).ellipse((lensW * 0.18, lh * 0.08, lensW * 0.42, lh * 0.2), fill=(255, 255, 255, 150))
    m = Image.new("L", (lensW * s, lh * s), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, lensW * s - 1, lh * s - 1), radius=lensW * s // 2, fill=255)
    big.paste(adentro.resize((lensW * s, lh * s), Image.NEAREST), box[:2], m)
    r = lensW * s // 2
    D.rounded_rectangle((box[0] - 5, box[1], box[2] - 5, box[3]), radius=r, outline=(80, 220, 255, 110), width=10)
    D.rounded_rectangle((box[0] + 5, box[1], box[2] + 5, box[3]), radius=r, outline=(255, 90, 200, 110), width=10)
    D.rounded_rectangle(box, radius=r, outline=(255, 255, 255, 230), width=8)
    out.alpha_composite(big.resize((W, H + 12), Image.LANCZOS))
    return out


# ---------------------------------------------------------------------------------------------------------------
# Miniaturas de los fondos
# ---------------------------------------------------------------------------------------------------------------

def mini_cielo(w, hh, n=0.0):
    bandas = ([h("3f6fb5"), h("4f83c6"), h("6599d6"), h("80b1e3"), h("a3caee"), h("cfe4f4")] if n < 0.5 else
              [h("0e1030"), h("161a42"), h("212754"), h("2f3466"), h("464779"), h("5e5583")])
    L = nuevo(w, hh)
    for y in range(hh):
        t = y / hh * len(bandas)
        i = int(t)
        f = t - i
        for x in range(w):
            c = bandas[min(i, len(bandas) - 1)]
            # tramado en la orilla de cada banda (nada de degradado suave)
            if f > 0.75 and i + 1 < len(bandas) and (x + y) % 2 == 0:
                c = bandas[i + 1]
            L.p(x, y, c)
    if n < 0.5:
        sx, sy, r = int(w * 0.75), int(min(hh * 0.28, 160)), max(2, min(22, hh // 9))
        L.elipse(sx, sy, r, r, P["oro"])
        L.elipse(sx, sy, r - 1, r - 1, P["amarillo"])
        L.p(sx - 1, sy - 1, h("fff2b0"))
        nubes = [(int(w * 0.18), int(min(hh * 0.42, 230)), max(4, min(40, w // 9))),
                 (int(w * 0.55), int(min(hh * 0.6, 330)), max(3, min(28, w // 12)))]
        for (cx, cy, s) in nubes:
            L.elipse(cx, cy + 1, s, max(1, s // 3), P["azulgris"])
            L.elipse(cx, cy, s, max(1, s // 3), P["blanco"])
            L.elipse(cx - s // 3, cy - s // 3, s // 2, s // 3 + 1, P["blanco"])
            L.elipse(cx + s // 4, cy - s // 4, s // 3 + 1, s // 4 + 1, P["blanco"])
    else:
        mx, my, r = int(w * 0.72), int(hh * 0.3), max(2, hh // 9)
        L.elipse(mx, my, r, r, P["crema"])
        L.elipse(mx + r // 2 + 1, my - 1, r, r, mini_cielo_color(L, mx + r + 2, my))
        for k in range(max(6, w * hh // 220)):
            x, y = (k * 37 + 11) % w, (k * 23 + 5) % int(hh * 0.8)
            L.p(x, y, P["crema"] if k % 3 else P["cian_luz"])
    return L.im


def mini_cielo_color(L, x, y):
    return L.px[min(L.w - 1, x), y]


def mini_lofi(w, hh):
    """Del arte real del fondo Lofi (Waneella, en la captura antes-0-ahora.png). Grande: recorte 1:1 de la calle con
    el letrero. Chica (la ventanita): toda la imagen reducida a pocos colores, como la haria la app con un Bitmap."""
    if w >= 80:
        cx, cy = 102, 556
        return AHORA.crop((cx - w // 2, cy - hh // 2, cx - w // 2 + w, cy - hh // 2 + hh))
    zona = AHORA.crop((0, 440, 600, 1000)).convert("RGB")
    lado = min(zona.width / w, zona.height / hh)
    cw, ch = int(w * lado), int(hh * lado)
    zona = zona.crop(((zona.width - cw) // 2, (zona.height - ch) // 2, (zona.width - cw) // 2 + cw, (zona.height - ch) // 2 + ch))
    return zona.resize((w, hh), Image.BOX).quantize(14, dither=Image.Dither.NONE).convert("RGBA")


def mini_boda(w, hh):
    """Dibujada aqui (no hay arte de la boda en el repo): arco de flores y un corazon."""
    L = nuevo(w, hh, h("f6dfe0"))
    for y in range(hh * 2 // 3, hh):
        for x in range(w):
            L.p(x, y, h("cfe3b8") if (x + y) % 2 or y > hh * 3 // 4 else h("f6dfe0"))
    L.rect(0, hh * 3 // 4, w - 1, hh - 1, h("a8cf8a"))
    cx, base = w // 2, hh * 3 // 4
    ra, rb = max(6, w // 4), max(6, hh // 2)
    L.arco(cx, base, ra, rb, 180, 360, P["blanco"], paso=1)
    L.arco(cx, base, ra + 1, rb + 1, 180, 360, P["niebla"], paso=1)
    flores = [P["durazno"], P["blanco"], h("e0457b"), P["amarillo"]]
    L.arco(cx, base, ra + 2, rb + 2, 180, 360, P["verde"], paso=2)
    for k, g in enumerate(range(184, 360, 13)):
        a = math.radians(g)
        x, y = int(round(cx + (ra + 1) * math.cos(a))), int(round(base + (rb + 1) * math.sin(a)))
        L.p(x - 1, y, P["verde"])
        L.p(x, y, flores[k % 4])
        L.p(x + 1, y, flores[k % 4])
        L.p(x, y - 1, flores[k % 4])
    corazon = [".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."]
    L.mapa(cx - 3, base - rb // 2 - 2, corazon, {"X": h("d23b5f")})
    L.p(cx - 2, base - rb // 2 - 1, h("ff9cb4"))
    return L.im


def mini_hyt(w, hh, n=0.0):
    """El cuarto de Hunter y Tango con los dos (recorte 1:1 de cuarto_*.png + sus hojas)."""
    esc = CUARTO[n > 0.5].copy()
    for i, (nom, x) in enumerate((("hunter", 150), ("tango", 176))):
        d = perro(nom, "sit" if i == 0 else "idle", 0)
        if n > 0.5:
            d = noche(d)
        pegar_recortado(esc, d, x - d.width // 2, 59 - d.height)
    cx, cy = 160, 38
    x0 = max(0, min(256 - w, cx - w // 2))
    y0 = max(0, min(64 - hh, cy - hh // 2))
    if hh > 64 or w > 256:
        esc = esc.resize((esc.width * 2, esc.height * 2), Image.NEAREST)
        x0, y0 = max(0, 2 * cx - w // 2), max(0, 2 * cy - hh // 2)
    return esc.crop((x0, y0, x0 + w, y0 + hh))


FONDOS = [("Cielo", mini_cielo), ("Lofi", lambda w, hh, n=0: mini_lofi(w, hh)),
          ("Boda", lambda w, hh, n=0: mini_boda(w, hh)), ("Hunter y Tango", mini_hyt)]


def mini(i, w, hh, n=0.0):
    return FONDOS[i][1](w, hh, n)


# ---------------------------------------------------------------------------------------------------------------
# Fuentes (solo para etiquetas fuera de las escenas; y el nombre de la postal, sin antialias)
# ---------------------------------------------------------------------------------------------------------------

FUENTE = os.path.join(os.environ.get("WINDIR", "C:/Windows"), "Fonts")


def fuente(nombre, tam):
    try:
        return ImageFont.truetype(os.path.join(FUENTE, nombre), tam)
    except OSError:
        return ImageFont.load_default()


F_TIT = fuente("segoeuib.ttf", 20)
F_ETQ = fuente("segoeuib.ttf", 14)
F_PEQ = fuente("segoeui.ttf", 12)
F_PIXEL = fuente("tahoma.ttf", 11)


def texto(im, xy, s, f=F_ETQ, c=(235, 235, 240, 255)):
    ImageDraw.Draw(im).text(xy, s, font=f, fill=c)


def texto_pixel(im, xy, s, c, centro=False):
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    if centro:
        w = d.textlength(s, font=F_PIXEL)
        xy = (int(xy[0] - w / 2), xy[1])
    d.text(xy, s, font=F_PIXEL, fill=c)


# ---------------------------------------------------------------------------------------------------------------
# F: la ventanita de arriba a la derecha, las postales, los cuadros y la cortina
# ---------------------------------------------------------------------------------------------------------------

def ventanita(mini_img, cortinas=False, flecha=False, n=0.0):
    """Ventana de madera 46 x 50 (con repisa) que ensena el fondo en chiquito."""
    W, H = 46, 50
    L = nuevo(W, H)
    L.rect(1, 1, W - 2, 43, P["madera"])
    L.rect(1, 1, W - 2, 1, P["madera_luz"])
    L.rect(1, 1, 1, 43, P["madera_luz"])
    L.rect(W - 2, 2, W - 2, 43, P["madera_osc"])
    L.rect(1, 43, W - 2, 43, P["madera_osc"])
    L.caja(0, 0, W - 1, 44, P["contorno_mad"], esquina=True)
    # vidrio
    gx0, gy0, gx1, gy1 = 5, 5, W - 6, 39
    L.im.paste(mini_img.crop((0, 0, gx1 - gx0 + 1, gy1 - gy0 + 1)), (gx0, gy0))
    L.px = L.im.load()
    L.caja(gx0 - 1, gy0 - 1, gx1 + 1, gy1 + 1, P["madera_osc"])
    # cruz del marco
    mx, my = (gx0 + gx1) // 2, (gy0 + gy1) // 2
    L.rect(mx, gy0, mx + 1, gy1, P["madera"])
    L.rect(mx + 1, gy0, mx + 1, gy1, P["madera_osc"])
    L.rect(gx0, my, gx1, my, P["madera"])
    L.rect(gx0, my + 1, gx1, my + 1, P["madera_osc"])
    # brillos del vidrio
    for (bx, by) in ((gx0 + 2, gy0 + 5), (mx + 4, gy0 + 4)):
        for k in range(4):
            L.p(bx + k, by - k, P["blanco"])
        L.p(bx + 2, by, P["blanco"])
    if cortinas:
        for lado in (0, 1):
            for y in range(gy0, gy1 + 1):
                anchoc = 5 if y < my - 2 else (3 if y < my + 3 else 6)
                for k in range(anchoc):
                    x = gx0 + k if lado == 0 else gx1 - k
                    L.p(x, y, ROJO[[2, 3, 4, 3, 2, 1][k % 6]])
            yy = my + 1
            L.rect(gx0 if lado == 0 else gx1 - 3, yy, (gx0 + 3) if lado == 0 else gx1, yy, P["oro"])
        L.rect(gx0 - 1, gy0, gx1 + 1, gy0 + 1, ROJO[1])
        L.rect(gx0 - 1, gy0, gx1 + 1, gy0, P["oro"])
    # repisa
    L.rect(0, 44, W - 1, 47, P["madera"])
    L.rect(0, 44, W - 1, 44, P["madera_luz"])
    L.caja(0, 44, W - 1, 48, P["contorno_mad"], esquina=True)
    L.rect(2, 49, W - 3, 49, (0, 0, 0, 90))
    if flecha:
        L.mapa(W // 2 - 2, 44, ["X..", "XX.", "XXX", "XX.", "X.."], {"X": P["crema"]})
    return L.im if n < 0.5 else noche_obj(L.im)


def borde_dentado(L, x0, y0, x1, y1):
    """Hoyitos de estampilla en la orilla (cada 3 px)."""
    for x in range(x0 + 2, x1 - 1, 3):
        L.p(x, y0, (0, 0, 0, 0))
        L.p(x, y1, (0, 0, 0, 0))
    for y in range(y0 + 2, y1 - 1, 3):
        L.p(x0, y, (0, 0, 0, 0))
        L.p(x1, y, (0, 0, 0, 0))


def postal(i, n, actual):
    W, H = 118, 100
    L = nuevo(W, H)
    papel = P["crema"]
    L.rect(0, 0, W - 1, H - 1, papel)
    L.rect(0, H - 1, W - 1, H - 1, P["niebla"])
    L.rect(W - 1, 0, W - 1, H - 1, P["niebla"])
    L.caja(4, 4, W - 5, 73, P["piedra"])
    texto_pixel(L.im, (W // 2, 79), FONDOS[i][0], P["carbon"], centro=True)
    L.px = L.im.load()
    if actual:
        L.caja(1, 1, W - 2, H - 2, P["naranja"])
    borde_dentado(L, 0, 0, W - 1, H - 1)
    out = L.im if n < 0.5 else noche(L.im)
    # la miniatura es la imagen del fondo: no se oscurece
    out.paste(mini(i, W - 10, 68, n), (5, 5))
    # estampilla encima de la foto
    E = Lienzo(out)
    sx, sy = W - 21, 8
    E.rect(sx, sy, sx + 11, sy + 13, papel if n < 0.5 else noche(Image.new("RGBA", (1, 1), papel)).getpixel((0, 0)))
    E.rect(sx + 2, sy + 2, sx + 9, sy + 11, [P["naranja"], P["teal"], h("d23b5f"), P["oro"]][i % 4])
    E.mapa(sx + 3, sy + 4, ["..X..", ".XXX.", "XXXXX", "..X.."], {"X": P["crema"]})
    for k in range(0, 13, 2):
        E.p(sx + k, sy - 1, (0, 0, 0, 0))
        E.p(sx + k, sy + 14, (0, 0, 0, 0))
    return out


def chinche(L, x, y):
    L.mapa(x, y, ["..OOO..", ".OXXXO.", "OXXXXXO", "OXXXXXO", "OXXXXXO", ".OXXXO.", "..OOO..", "...Y...", "...Y..."],
           {"X": h("d23b5f"), "O": P["vino"], "Y": P["tinta"]})
    L.p(x + 2, y + 2, h("ff9cb4"))
    L.p(x + 3, y + 2, h("ff9cb4"))
    L.p(x + 2, y + 3, h("ff9cb4"))


def charola_postales(n, ancho=568, actual=1):
    """La charola que sube desde abajo: madera con fondo de corcho, una postal por fondo."""
    H = 136
    L = nuevo(ancho, H)
    # corcho
    corcho = [h("d3ac89"), h("bea98d"), h("c9a27a"), h("b59f88")]
    for y in range(8, H):
        for x in range(ancho):
            L.p(x, y, corcho[(x * 7 + y * 13 + (x * y) % 5) % 4] if (x * 3 + y * 5) % 7 < 2 else corcho[0])
    # marco de madera
    L.rect(0, 6, ancho - 1, 12, P["madera"])
    L.rect(0, 6, ancho - 1, 6, P["madera_luz"])
    L.rect(0, 12, ancho - 1, 12, P["madera_osc"])
    L.rect(0, 6, 5, H - 1, P["madera"])
    L.rect(ancho - 6, 6, ancho - 1, H - 1, P["madera_osc"])
    L.caja(0, 5, ancho - 1, H - 1, P["contorno_mad"], esquina=True)
    # agarradera al centro
    L.rect(ancho // 2 - 22, 0, ancho // 2 + 21, 6, P["madera"])
    L.rect(ancho // 2 - 21, 0, ancho // 2 + 20, 0, P["madera_luz"])
    L.caja(ancho // 2 - 23, -1, ancho // 2 + 22, 6, P["contorno_mad"], esquina=True)
    L.rect(ancho // 2 - 12, 3, ancho // 2 + 11, 3, P["nogal"])
    if n > 0.5:
        L = Lienzo(noche_obj(L.im))
    # postales (4 y media: la quinta se asoma, se desliza de lado)
    x = 16
    ys = [22, 18, 24, 19, 21]
    for i in range(5):
        k = i % 4
        p = postal(k, n, k == actual and i < 4)
        y = ys[i]
        L.oscurecer(x + 2, y + 2, x + p.width + 1, y + p.height + 1, 0.65)
        pegar_recortado(L.im, p, x, y)
        L.px = L.im.load()
        if k == actual and i < 4:
            chinche(L, x + p.width // 2 - 3, y - 4)
            d = perro("hunter", "sit", 0)
            d = d.crop(d.getbbox())
            if n > 0.5:
                d = noche(d)
            pegar_recortado(L.im, d, x + p.width - 12, y + p.height - d.height + 8)
            L.px = L.im.load()
        else:
            chinche(L, x + p.width // 2 - 3, y - 4) if i % 2 == 0 else None
        x += p.width + 18
    return L.im


def marco_cuadro(L, x, y, img, dorado=False):
    w, hh = img.width + 8, img.height + 8
    luz, med, osc = (P["amarillo"], P["oro"], P["madera_osc"]) if dorado else (P["madera_luz"], P["madera"], P["madera_osc"])
    # clavo e hilo
    cx = x + w // 2
    L.linea(cx, y - 10, x + 6, y, P["tinta"])
    L.linea(cx, y - 10, x + w - 7, y, P["tinta"])
    L.rect(cx - 1, y - 11, cx, y - 10, P["carbon"])
    # sombra en la pared
    L.oscurecer(x + 2, y + 2, x + w + 1, y + hh + 1, 0.75)
    L.rect(x, y, x + w - 1, y + hh - 1, med)
    L.rect(x + 1, y + 1, x + w - 2, y + 1, luz)
    L.rect(x + 1, y + 1, x + 1, y + hh - 2, luz)
    L.rect(x + w - 2, y + 2, x + w - 2, y + hh - 2, osc)
    L.rect(x + 2, y + hh - 2, x + w - 2, y + hh - 2, osc)
    L.caja(x, y, x + w - 1, y + hh - 1, P["contorno_mad"], esquina=True)
    L.caja(x + 3, y + 3, x + w - 4, y + hh - 4, osc)
    L.im.paste(img, (x + 4, y + 4))
    L.px = L.im.load()


def charola_cuadros(n, ancho=568, actual=1, cuadro_tango=1):
    H = 146
    L = Lienzo(pared(ancho, n, alto=H))
    w, hh = 104, 62
    xs = [16, 154, 292, 430]
    for i, x in enumerate(xs):
        marco_cuadro(L, x, 24 if i % 2 == 0 else 20, mini(i, w, hh, n), dorado=(i == actual))
    # placa con el nombre debajo de cada cuadro (laton, texto pixel)
    for i, x in enumerate(xs):
        y = (24 if i % 2 == 0 else 20) + hh + 11
        nombre = FONDOS[i][0]
        tw = int(ImageDraw.Draw(L.im).textlength(nombre, font=F_PIXEL)) + 8
        px0 = x + (w + 8 - tw) // 2
        L.rect(px0, y, px0 + tw - 1, y + 12, P["oro"] if i == actual else P["niebla"])
        L.rect(px0, y, px0 + tw - 1, y, P["amarillo"] if i == actual else P["humo"])
        L.caja(px0 - 1, y - 1, px0 + tw, y + 13, P["contorno_mad"] if i == actual else P["topo"], esquina=True)
        texto_pixel(L.im, (px0 + 4, y), nombre, P["nogal"] if i == actual else P["carbon"])
        L.px = L.im.load()
    # Tango camina hacia el elegido (piso: ultimas 12 filas; tierra en H-5)
    t = perro("tango", "walk", cuadro_tango)
    if n > 0.5:
        t = noche(t)
    destino = xs[actual] + (w + 8) // 2
    pegar_recortado(L.im, t, destino - 70, H - 5 - t.height + 1)
    L.px = L.im.load()
    # huellitas detras
    for k, xx in enumerate(range(destino - 150, destino - 74, 12)):
        yy = H - 7 + (k % 2) * 2
        L.rect(xx, yy, xx + 1, yy, P["nogal"])
    out = Image.new("RGBA", (ancho, H + 2), (0, 0, 0, 0))
    out.paste(L.im, (0, 2))
    Lo = Lienzo(out)
    Lo.caja(0, 1, ancho - 1, H + 1, P["contorno_mad"])
    Lo.rect(0, 0, ancho - 1, 0, P["madera_luz"])
    return out


def cortina(w, hh, cierre, n=0.0):
    """Cortina de teatro en pixel sobre un area w x hh. cierre: 0 abierta .. 1 cerrada. Devuelve RGBA."""
    L = nuevo(w, hh)
    mitad = w // 2
    tela = int(18 + (mitad - 18) * cierre)  # cada pano ocupa esto
    pliegue = [0, 1, 2, 3, 3, 4, 3, 2, 2, 1]
    for lado in (0, 1):
        for x in range(tela):
            # los pliegues se aprietan cuando la cortina esta recogida
            k = int(x * (10 if cierre > 0.3 else 6) / 10)
            c = ROJO[pliegue[k % len(pliegue)]]
            xx = x if lado == 0 else w - 1 - x
            # el borde de abajo hace onditas
            fin = hh - 1 - (2 if (x // 5) % 2 else 0)
            for y in range(0, fin + 1):
                L.p(xx, y, c)
        filo = tela - 1 if lado == 0 else w - tela
        for y in range(hh):
            L.p(filo, y, ROJO[0])
        if cierre < 0.25:
            # recogida con un cordon dorado
            yy = int(hh * 0.55)
            x0 = 0 if lado == 0 else w - tela
            L.rect(x0, yy, x0 + tela - 1, yy + 2, P["oro"])
            L.rect(x0, yy, x0 + tela - 1, yy, P["amarillo"])
            borla = filo + (2 if lado == 0 else -4)
            L.mapa(borla, yy + 2, [".X.", "XXX", "XXX", "X.X"], {"X": P["oro"]})
    # cenefa con ondas y fleco dorado
    L.rect(0, 0, w - 1, 3, P["oro"])
    L.rect(0, 0, w - 1, 0, P["amarillo"])
    L.rect(0, 4, w - 1, 17, ROJO[2])
    L.rect(0, 4, w - 1, 5, ROJO[3])
    for x in range(w):
        f = (x % 24) - 12
        prof = 18 + int(8 * (1 - (f / 12) ** 2))
        for y in range(18, prof):
            L.p(x, y, ROJO[2] if y < prof - 2 else ROJO[1])
        L.p(x, prof, P["oro"])
        if x % 2 == 0:
            L.p(x, prof + 1, P["amarillo"] if x % 4 == 0 else P["oro"])
    return L.im if n < 0.5 else noche(L.im)


# ---------------------------------------------------------------------------------------------------------------
# Laminas
# ---------------------------------------------------------------------------------------------------------------

FONDO_LAM = h("1b1c24")


def guardar(im, nombre):
    os.makedirs(SALIDA, exist_ok=True)
    im = im.convert("RGB")
    ruta = os.path.join(SALIDA, nombre + ".png")
    im.save(ruta)
    im.resize((im.width * 2, im.height * 2), Image.NEAREST).save(os.path.join(SALIDA, nombre + "-x2.png"))
    print("ok", ruta)


ESTADOS = [("sonando", "sonando"), ("pausa", "en pausa"), ("cambio", "siguiente (cuadro intermedio)")]
OPC_T = [("T1  El tocadiscos", escena_t1), ("T2  La casetera", escena_t2), ("T3  Teclas", escena_t3)]


def lamina_transporte():
    colw, rowh = 250, 104
    W = 24 + 3 * colw + 300
    bloque = 30 + 3 * rowh
    H = 56 + 2 * bloque + 20
    lam = Image.new("RGBA", (W, H), FONDO_LAM)
    texto(lam, (24, 14), "Transporte: anterior · play/pausa · siguiente  (cápsula 210 × 74, escala real)", F_TIT)
    for b, (nn, nombre_b) in enumerate(((0.0, "Día"), (1.0, "Noche"))):
        y0 = 56 + b * bloque
        texto(lam, (24, y0), nombre_b, F_ETQ, (250, 200, 120, 255))
        for c, (nombre, fn) in enumerate(OPC_T):
            x = 24 + c * colw
            texto(lam, (x, y0 + 16), nombre, F_ETQ)
            for r, (est, etq) in enumerate(ESTADOS):
                y = y0 + 38 + r * rowh
                texto(lam, (x, y), etq, F_PEQ, (170, 172, 185, 255))
                cap = capsula(fn(est, nn), nn)
                lam.alpha_composite(cap, (x, y + 16))
        # el volumen de verdad, para comparar
        x = 24 + 3 * colw + 10
        texto(lam, (x, y0 + 16), "Volumen (el que ya existe)", F_ETQ)
        lam.alpha_composite(capsula_volumen(nn), (x, y0 + 38 + 16))
    guardar(lam, "maqueta-transporte")


def bocina_icono(im, x, y):
    """El icono de bocina que ya tiene la pildora (recortado de la captura real)."""
    im.alpha_composite(PILDORA.crop((268, 752, 290, 774)), (x - 2, y - 2))


# posicion de las cosas en la pildora vertical (medido en antes-1-pildora-vertical.png)
PILDORA_CAP_Y = 726 - 6     # la capsula del volumen empieza en y=726 (sin lo que sobresale)
VOL_X = 301
T_X = 40


def pantalla_con_pildora(n, estado="sonando"):
    scr = PILDORA.copy()
    L = Lienzo(scr)
    # tapar los botones viejos con el color de la pildora (muestreado ahi mismo)
    # (fila por fila, con el color de la pildora entre la bocina y el volumen: x=294)
    for y in range(714, 812):
        c = scr.getpixel((294, y))
        L.rect(28, y, 264, y, c)
        L.rect(296, y, 574, y, c)
    cap = capsula(escena_t1(estado, n), n)
    scr.alpha_composite(cap, (T_X, PILDORA_CAP_Y))
    scr.alpha_composite(capsula_volumen(n), (VOL_X, PILDORA_CAP_Y))
    return scr


def lamina_pildora():
    W = 24 + 600 + 24 + 600 + 24
    lam = Image.new("RGBA", (W, 1024 + 92), FONDO_LAM)
    texto(lam, (24, 16), "Píldora en vertical: T1 «El tocadiscos» + volumen   (sobre la captura real, 600 × 1024)", F_TIT)
    for k, (nn, nom) in enumerate(((0.0, "Día (sonando)"), (1.0, "Noche (en pausa)"))):
        x = 24 + k * 624
        lam.alpha_composite(pantalla_con_pildora(nn, "sonando" if nn < 0.5 else "pausa"), (x, 76))
        texto(lam, (x, 52), nom, F_ETQ, (250, 200, 120, 255))
    guardar(lam, "maqueta-pildora-vertical")


def boton_ventana(scr, img):
    """Cambia el boton de la imagen (circulo en x~482..526, y~12..56) por la ventanita."""
    L = Lienzo(scr)
    # tapar el circulo viejo con lo que hay alrededor (el fondo es arte; se copia la franja de la izquierda)
    parche = scr.crop((474 - 46, 8, 474, 60))
    scr.paste(parche, (480, 8))
    scr.alpha_composite(img, (481, 10))


def pantalla_postales(n):
    scr = pantalla_con_pildora(n, "sonando")
    boton_ventana(scr, ventanita(mini(1, 36, 36, n), n=n))
    # velo detras de la charola
    velo = Image.new("RGBA", scr.size, (6, 6, 14, 120))
    scr.alpha_composite(velo)
    boton_ventana(scr, ventanita(mini(1, 36, 36, n), n=n))
    ch = charola_postales(n)
    scr.alpha_composite(ch, (16, 1024 - ch.height))
    return scr


def pantalla_cuadros(n):
    scr = pantalla_con_pildora(n, "sonando")
    velo = Image.new("RGBA", scr.size, (6, 6, 14, 120))
    scr.alpha_composite(velo)
    boton_ventana(scr, ventanita(mini(1, 36, 36, n)))
    ch = charola_cuadros(n)
    scr.alpha_composite(ch, (16, 1024 - ch.height - 8))
    return scr


def lamina_postales():
    W = 24 + 600 + 24 + 600 + 24
    lam = Image.new("RGBA", (W, 1024 + 92), FONDO_LAM)
    texto(lam, (24, 16), "F1 «Postales» abierta: la ventanita arriba a la derecha y la charola que sube   (600 × 1024)", F_TIT)
    for k, (nn, nom) in enumerate(((0.0, "Día"), (1.0, "Noche"))):
        x = 24 + k * 624
        lam.alpha_composite(pantalla_postales(nn), (x, 76))
        texto(lam, (x, 52), nom, F_ETQ, (250, 200, 120, 255))
    guardar(lam, "maqueta-fondo-postales")


def lamina_fondo():
    colw = 624
    W = 24 + 3 * colw
    H = 700
    lam = Image.new("RGBA", (W, H), FONDO_LAM)
    texto(lam, (24, 14), "Cambiar de fondo  (escala real; recortes de la pantalla vertical 600 × 1024)", F_TIT)
    texto(lam, (24, 48), "Volumen (para comparar):", F_PEQ, (170, 172, 185, 255))
    lam.alpha_composite(capsula_volumen(0.0), (190, 40))
    lam.alpha_composite(capsula_volumen(1.0), (480, 40))
    y0 = 140
    titulos = ["F1  Postales", "F2  Cuadros en la pared", "F3  Cortina"]
    for c, t in enumerate(titulos):
        texto(lam, (24 + c * colw, y0), t, F_ETQ)
    # fila 1: el boton (recorte de la barra de arriba, 300 x 70)
    yb = y0 + 26
    for c in range(3):
        x = 24 + c * colw
        scr = PILDORA.copy()
        if c < 2:
            boton_ventana(scr, ventanita(mini(1, 36, 36)))
            etq = "botón: ventanita con el fondo actual"
        else:
            boton_ventana(scr, ventanita(mini(0, 36, 36), cortinas=True, flecha=True))
            etq = "botón: ventanita con el SIGUIENTE fondo"
        texto(lam, (x, yb), etq, F_PEQ, (170, 172, 185, 255))
        lam.alpha_composite(scr.crop((300, 0, 600, 72)), (x, yb + 18))
    # fila 2: abierto
    ya = yb + 104
    texto(lam, (24, ya), "al tocarla sube la charola; la actual con chinche y Hunter", F_PEQ, (170, 172, 185, 255))
    lam.alpha_composite(pantalla_postales(0.0).crop((0, 1024 - 300, 600, 1024)), (24, ya + 18))
    texto(lam, (24 + colw, ya), "la charola es la pared del cuarto; Tango camina al elegido", F_PEQ, (170, 172, 185, 255))
    lam.alpha_composite(pantalla_cuadros(0.0).crop((0, 1024 - 300, 600, 1024)), (24 + colw, ya + 18))
    # F3: tres cuadros de la cortina (recortes de 190 x 300 del centro de la pantalla)
    x = 24 + 2 * colw
    texto(lam, (x, ya), "tocar = la cortina se cierra y abre con el siguiente", F_PEQ, (170, 172, 185, 255))
    nuevo_fondo = mini_cielo(600, 1024)
    for k, (cierre, fondo) in enumerate(((0.8, PILDORA), (1.0, PILDORA), (0.72, nuevo_fondo))):
        scr = fondo.copy()
        scr.alpha_composite(cortina(600, 1024, cierre))
        lam.alpha_composite(scr.crop((205, 0, 395, 300)), (x + k * 200, ya + 18))
        texto(lam, (x + k * 200, ya + 322), ["se cierra", "cerrada", "abre: nuevo fondo"][k], F_PEQ, (170, 172, 185, 255))
    lam = lam.crop((0, 0, W, ya + 345))
    guardar(lam, "maqueta-fondo")


if __name__ == "__main__":
    lamina_transporte()
    lamina_pildora()
    lamina_postales()
    lamina_fondo()
