"""Exporta los sprites del clima (C2 "el patio") y de la bateria (B1 "el plato de croquetas") a la app.

Uso:  python arte/controles/armar_clima_bateria.py
Sale en android/app/src/main/res/drawable-nodpi/ (clima_*.png y plato_*.png), a 1 px de arte = 1 px; la app los
dibuja a 1x o 2x sin suavizado (WeatherView.kt, BatteryView.kt). El dibujo sale de maquetas_clima_bateria.py.

  clima_<escena>_<dia|noche>.png  168 x 64  el hueco de la capsula: cielo, ciudad y pasto (sin perro, sin letrero
                                           y sin gotas: eso lo pone la app)
  clima_rayo.png                           el rayo de la tormenta
  clima_xl.png      "0123456789-°"         numeros de la temperatura (celdas de 16 x 25, glifo en (1, 1))
  clima_chico.png   "0123456789°%-^vg"     max/min y lluvia (celdas de 7 x 10); ^ v = flechas, g = gota
  plato_digitos.png "0123456789%"          el porcentaje (celdas de 12 x 19), 3 filas: normal, ambar, rojo
  plato_0..4.png    50 x 30                el plato: 0 = tres croquetas ... 4 = copeton
  plato_costal.png, plato_globo.png        el costal que lo rellena al cargar; el globito con el plato vacio
"""

import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas_clima_bateria as C  # noqa: E402
from maquetas import P, h, Lienzo, nuevo, noche_obj, mezcla  # noqa: E402

NODPI = os.path.join(C.M.RAIZ, "android", "app", "src", "main", "res", "drawable-nodpi")

ESCENAS = ["despejado", "parcial", "nublado", "niebla", "lluvia", "tormenta", "nieve", "calor", "frio"]
W, H = 168, 64
# la fuente de la temperatura (MEDIANO al doble) con el signo de menos
XL = dict(C.XL)
XL["-"] = ["." * 10] * 9 + ["X" * 10] * 4 + ["." * 10] * 9

NOCHES = {
    "despejado": ["101536", "151b44", "1b2352", "222b60", "2b356e"],
    "nublado": ["161a2c", "1c2136", "232940", "2b314a", "343a55"],
    "lluvia": ["12151f", "171b28", "1d2231", "23293b", "2a3045"],
    "tormenta": ["0e1018", "131620", "191c29", "1f2332", "262a3b"],
    "niebla": ["1d2230", "252a38", "2e3341", "383d4b", "434856"],
    "nieve": ["171c33", "1d233d", "242a47", "2c3252", "353b5d"],
}
DIAS_EXTRA = {
    "niebla": ["98a1ab", "a5adb6", "b2b9c1", "bfc5cc", "ccd1d7"],
    "nieve": ["8f9db0", "9eabbc", "aeb9c8", "bec7d3", "cfd6df"],
}


def bandas(L, colores):
    cs = [h(c) for c in colores]
    for y in range(L.h):
        t = y / L.h * len(cs)
        i = min(int(t), len(cs) - 1)
        f = t - int(t)
        for x in range(L.w):
            c = cs[i]
            if f > 0.82 and i + 1 < len(cs) and (x + y) % 2 == 0:
                c = cs[i + 1]
            L.p(x, y, c)


def niebla(L, y0, y1, c, paso=5):
    """Franjas de niebla tramadas (sin transparencia)."""
    for k, y in enumerate(range(y0, y1, paso)):
        for x in range((k * 3) % 4, L.w, 2):
            L.p(x, y, c)
            if x % 6 == 0:
                L.p(x + 1, y + 1, c)


def escena(nombre, noche):
    alto_cielo = H - 7
    L = nuevo(W, alto_cielo)
    rnd = random.Random(9)
    if noche:
        clave = nombre if nombre in NOCHES else "despejado"
        bandas(L, NOCHES[clave])
        despejado = nombre in ("despejado", "parcial", "calor", "frio")
        if despejado:
            for _ in range(W * alto_cielo // 40):
                C.estrella(L, rnd.randrange(W), rnd.randrange(int(alto_cielo * 0.7)),
                           P["crema"] if rnd.random() < 0.5 else P["azulgris"])
            for _ in range(2):
                C.estrella(L, rnd.randrange(4, W - 4), rnd.randrange(3, int(alto_cielo * 0.5)), P["crema"], cruz=True)
        if despejado:
            C.luna(L, 26, 14, 5)
        if nombre == "parcial":
            C.nube(L, 46, 6, 26, C.NUBE_NOCHE)
        if nombre in ("nublado", "lluvia", "tormenta", "nieve"):
            C.nube(L, -8, 1, 34, C.NUBE_NOCHE)
            C.nube(L, 70, 5, 32, C.NUBE_NOCHE)
        C.ciudad(L, W, alto_cielo, "noche", rnd)
        if nombre == "niebla":
            niebla(L, 18, alto_cielo, h("4a4f5e"))
    else:
        if nombre in DIAS_EXTRA:
            bandas(L, DIAS_EXTRA[nombre])
            if nombre == "nieve":
                C.nube(L, -6, 2, 34, C.NUBE_GRIS)
                C.nube(L, 72, 6, 32, C.NUBE_CLARA)
            C.ciudad(L, W, alto_cielo, "nublado", rnd)
            if nombre == "niebla":
                niebla(L, 14, alto_cielo, h("dde1e5"))
        elif nombre in ("lluvia", "tormenta"):
            # sin gotas ni rayo: los pone la app (caen solo mientras se ve)
            bandas(L, C.CIELOS[nombre])
            C.nube(L, -8, 1, 34, C.NUBE_OSCURA if nombre == "tormenta" else C.NUBE_GRIS)
            C.nube(L, 70, 5, 32, C.NUBE_OSCURA)
            C.ciudad(L, W, alto_cielo, nombre, rnd)
        else:
            base = {"parcial": "despejado"}.get(nombre, nombre)
            cielo = C.cielo(W, alto_cielo, base, semilla=9, sol_en=(26, 14))
            L.im.alpha_composite(cielo)
            L.px = L.im.load()
            if nombre == "parcial":
                C.nube(L, 34, 10, 28, C.NUBE_CLARA)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    out.alpha_composite(L.im)
    # el pasto (de noche, por el mismo filtro que los objetos)
    S = nuevo(W, H)
    tipo = {"tormenta": "tormenta", "lluvia": "lluvia", "nublado": "nublado", "niebla": "nublado", "calor": "calor",
            "frio": "frio"}.get(nombre, "")
    C.suelo(S, W, H, tipo)
    if nombre == "nieve":  # pasto nevado
        y0 = H - 9
        S.rect(0, y0, W - 1, H - 4, h("eef3f8"))
        S.rect(0, y0 + 3, W - 1, H - 4, h("d6dfe9"))
        for x in range(0, W, 3):
            S.p(x, y0 - 1, h("ffffff"))
            S.p(x + 1, y0 + 4 + (x % 2), h("bcc8d6"))
    if nombre in ("lluvia", "tormenta"):  # charco
        cx, cy = 40, H - 6
        C.forma(S, {(x - cx, y - cy) for (x, y) in C.elipse_pts(cx, cy, 12, 2)}, h("5d7ea0"), h("4a6a8c"), h("8fb6d6"),
                h("3d5571"), cx, cy)
    out.alpha_composite(noche_obj(S.im) if noche else S.im)
    return out


def tira(glifos, fuente, celda, colores, contorno):
    """Una fila por juego de colores; cada glifo en su celda, empezando en (1, 1)."""
    cw, ch = celda
    out = nuevo(cw * len(glifos), ch * len(colores))
    for fila, (c, s) in enumerate(colores):
        for i, g in enumerate(glifos):
            pts, w, hh = C.mascara_mapa(fuente[g])
            alto = len(fuente["0"])
            C.pintar_mascara(out, i * cw + 1, fila * ch + 1, pts, c, s if g not in "°" else None, alto, contorno)
    return out.im


def tira_chica():
    glifos = "0123456789°%-^vg"
    cw, ch = 7, 10
    out = nuevo(cw * len(glifos), ch)
    extra = {"-": ["....."] * 3 + ["XXXXX"] + ["....."] * 3, "^": C.FLECHA_ARRIBA, "v": C.FLECHA_ABAJO, "g": C.GOTA}
    colores = {"^": P["durazno"], "v": P["azulgris"], "g": h("6cc3f0")}
    for i, g in enumerate(glifos):
        filas = extra.get(g) or C.CHICO[g]
        pts, w, hh = C.mascara_mapa(filas)
        dy = 2 if g in "^v" else 0
        C.pintar_mascara(out, i * cw + 1, 1 + dy, pts, colores.get(g, P["crema"]), contorno=C.SOMBRA_TXT)
    return out.im


def plato(nivel):
    p = [12, 30, 55, 80, 100][nivel]
    L = nuevo(50, 30)
    cx, base = 25, 27
    for x in range(cx - 20, cx + 21):
        L.p(x, base + 2, (0, 0, 0, 70))
    C.plato(L, cx, base, p)
    return L.im


def costal():
    L = nuevo(19, 14)
    L.mapa(0, 0, C.COSTAL, {"o": P["contorno_mad"], "L": P["rosa"], "c": h("c9a774"), "s": h("a3814f"),
                            "y": P["amarillo"], "M": P["nogal"]})
    return L.im


def globo():
    L = nuevo(16, 9)
    L.mapa(0, 0, [
        ".oooooooooooooo.",
        "oHHHHHHHHHHHHHHo",
        "oHHrrrrrrrrrrHHo",
        "oHHHRRRRRRRRHHHo",
        "oHHHHrrrrrrHHHHo",
        "oHHHHHHHHHHHHHHo",
        ".oooooHHoooooooo",
        "......oHo.......",
        ".......o........",
    ], {"o": P["tinta"], "H": P["crema"], "r": C.ROJO_PLATO[1], "R": C.ROJO_PLATO[3]})
    return L.im


def rayo():
    L = nuevo(7, 10)
    C.rayo(L, 1, 1)
    return L.im


def guardar(im, nombre):
    ruta = os.path.join(NODPI, nombre + ".png")
    im.save(ruta)
    print("ok", nombre, im.size)


def main():
    for nombre in ESCENAS:
        for noche in (False, True):
            guardar(escena(nombre, noche), f"clima_{nombre}_{'noche' if noche else 'dia'}")
    guardar(rayo(), "clima_rayo")
    guardar(tira("0123456789-°", XL, (16, 25), [(P["crema"], P["rosa"])], P["nogal"]), "clima_xl")
    guardar(tira_chica(), "clima_chico")
    colores = [(P["crema"], P["rosa"]), (h("ffc45c"), h("d08a2a")), (h("ff7a6b"), h("c8463a"))]
    guardar(tira("0123456789%", C.GRANDE, (12, 19), colores, C.CONTORNO), "plato_digitos")
    for n in range(5):
        guardar(plato(n), f"plato_{n}")
    guardar(costal(), "plato_costal")
    guardar(globo(), "plato_globo")
    # hoja de revision (no va a la app)
    revision()


def revision():
    hoja = Image.new("RGBA", (2 * (W + 8) + 8, len(ESCENAS) * (H + 8) + 8), (27, 28, 36, 255))
    for i, nombre in enumerate(ESCENAS):
        for k, noche in enumerate((False, True)):
            hoja.alpha_composite(escena(nombre, noche), (8 + k * (W + 8), 8 + i * (H + 8)))
    destino = os.environ.get("REVISION")
    if destino:
        hoja.resize((hoja.width * 2, hoja.height * 2), Image.NEAREST).save(destino)


if __name__ == "__main__":
    main()
