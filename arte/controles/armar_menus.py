"""Arma los sprites de los menus en pixel que usa la app: el cajon de opciones, la libretita de ayuda y el mueble
de "Reproducir en...". El dibujo es el de las maquetas (maquetas_menus.py).

Uso:  python arte/controles/armar_menus.py

Salen en android/app/src/main/res/drawable-nodpi/ con prefijo menu_, libreta_ y aparato_. Todo a escala 1 (en la
tablet, densidad 1.0, 1 px de arte = 1 px de pantalla). La madera, el papel de las fichas y las hojas, y el texto los
dibuja la app (rectangulos y texto del sistema); aqui solo va lo que es dibujo. De noche la app les pone su filtro
(el de los objetos para lo de madera, uno mas suave para lo que va sobre papel), como al resto.

El orden de las celdas de cada tira es el que espera Menus.kt (MenuSprites): si se cambia aqui, se cambia alla.
"""

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import maquetas as M  # noqa: E402
import maquetas_menus as K  # noqa: E402
from maquetas import P, nuevo  # noqa: E402

DRAW = os.path.join(M.RES, "drawable-nodpi")
CELDA = 36
# En la app los objetos no llevan la miniatura de "Lofi" (es una foto de las carpetas de Javier): llevan el cielo.
K.mini = lambda i, w, hh, n=0.0: M.mini(0 if i == 1 else i, w, hh, n)


def guardar(im, nombre):
    im.save(os.path.join(DRAW, nombre + ".png"))
    print("ok", nombre, im.size)


def tira(piezas, cw, ch):
    """Una fila de celdas cw x ch; cada pieza centrada (abajo: pegada al piso de la celda si es mas chica)."""
    out = Image.new("RGBA", (cw * len(piezas), ch), (0, 0, 0, 0))
    for i, p in enumerate(piezas):
        if p.width > cw or p.height > ch:
            raise SystemExit("pieza %d de %dx%d no cabe en %dx%d" % (i, p.width, p.height, cw, ch))
        out.alpha_composite(p, (i * cw + (cw - p.width) // 2, (ch - p.height) // 2))
    return out


def fondo_cielo():
    """La postal del objeto "Fondo" con el cielo (el fondo real cambia; la postal es solo el simbolo)."""
    L = nuevo(32, 28)
    L.rect(0, 2, 31, 27, P["crema"])
    L.rect(1, 27, 31, 27, P["niebla"])
    L.rect(31, 3, 31, 27, P["niebla"])
    L.im.paste(M.mini_cielo(26, 18, 0.0), (3, 5))
    L.px = L.im.load()
    L.caja(2, 4, 29, 23, P["piedra"])
    L.rect(22, 6, 27, 11, P["crema"])
    L.rect(23, 7, 26, 10, P["naranja"])
    M.borde_dentado(L, 0, 2, 31, 27)
    M.chinche(L, 13, 0)
    return L.im


def objetos():
    """menu_objetos: 21 celdas de 36 x 36 (ver MenuSprites.OBJ_* en Menus.kt)."""
    return [
        fondo_cielo(),                                         # 0 fondo
        K.obj_arte(True), K.obj_arte(False),                   # 1 completo, 2 llenar
        K.obj_reloj(0), K.obj_reloj(5), K.obj_reloj(15), K.obj_reloj(60),   # 3..6 cada cancion / 5 / 15 / 60
        K.obj_persiana(0), K.obj_persiana(1), K.obj_persiana(2),            # 7..9 poco / medio / mucho
        K.obj_luna(True), K.obj_luna(False),                   # 10 automatico, 11 apagado
        K.obj_libro("ES"), K.obj_libro("EN"), K.obj_libro("--"),            # 12..14 es / en / no
        K.obj_siguiente(), K.obj_protector(), K.obj_ayuda(), K.obj_bocina(),  # 15..18
        K.obj_llaves(),                                        # 19 cerrar sesion
        conectar(),                                            # 20 conectar con Spotify
    ]


def conectar():
    """Llave entrando a la cerradura (Conectar con Spotify)."""
    L = nuevo(30, 34)
    K.tabla(L, 2, 2, 27, 33, veta=False)
    L.elipse(15, 14, 4, 4, P["oro"])
    L.anillo(15, 14, 4, 4, P["madera_osc"])
    L.rect(14, 15, 16, 24, P["tinta"])
    L.p(15, 13, P["tinta"])
    return L.im


def perillas():
    """menu_perillas: 9 celdas de 32 x 32 en el orden (2,0) (2,1) (3,0) (3,1) (3,2) (4,0) (4,1) (4,2) (4,3)."""
    out = []
    for n in (2, 3, 4):
        for i in range(n):
            L = nuevo(32, 32)
            K.perilla(L, 16, 17, n, i)
            out.append(L.im)
    return out


def interruptores():
    out = []
    for on in (True, False):
        L = nuevo(40, 18)
        K.interruptor(L, 0, 0, on)
        out.append(L.im)
    return out


def flechita():
    L = nuevo(16, 18)
    K.flechita(L, 0, 0)
    return L.im


def fieltro():
    """Mosaico de 16 x 16 del fieltro del cajon."""
    L = nuevo(16, 16)
    for y in range(16):
        for x in range(16):
            L.p(x, y, P["teal_osc"] if K.ruido(x + 3, y + 5) % 7 else M.h("3b7563"))
    return L.im


def ap_tablet():
    """Tablet parada en su base. 46 x 40."""
    L = nuevo(46, 40)
    L.rect(2, 1, 43, 29, P["tinta"])
    L.caja(1, 0, 44, 30, P["negro"], esquina=True)
    L.rect(4, 3, 41, 27, M.h("27304a"))
    for (x, y) in ((8, 7), (16, 20), (36, 6), (30, 18)):
        L.p(x, y, P["crema"])
    L.mapa(26, 9, [".XX", "X..", "X..", ".XX"], {"X": P["amarillo"]})
    L.rect(8, 13, 20, 13, P["perla"])
    L.rect(8, 16, 17, 16, P["gris"])
    L.rect(18, 31, 27, 33, P["gris"])
    L.rect(12, 34, 33, 36, P["carbon"])
    L.caja(11, 33, 34, 37, P["negro"], esquina=True)
    return L.im


def pared(n):
    """Mosaico de la pared del cuarto para el mueble: 64 de ancho (tira y su espejo) x 210 de alto."""
    return M.pared(64, n, 210)


def dibujos():
    """libreta_dibujo_0..8: el dibujito de cada hoja; los chicos van a 2x (como los perros grandes)."""
    for i in range(9):
        d = K.dibujo_seccion(i)
        if i == 3:
            # las postales con el cielo y Hunter y Tango (no con la foto de Lofi, que es de Javier y cambia)
            a = M.postal(0, 0.0, True)
            b = M.postal(3, 0.0, False)
            d = Image.new("RGBA", (a.width + 70, a.height + 8), (0, 0, 0, 0))
            d.alpha_composite(b, (70, 8))
            d.alpha_composite(a, (0, 0))
            M.chinche(M.Lienzo(d), a.width // 2 - 3, 0)
        if d.width * 2 <= 300 and d.height * 2 <= 130:
            d = K.x2(d)
        guardar(d, "libreta_dibujo_%d" % i)


if __name__ == "__main__":
    guardar(tira(objetos(), CELDA, CELDA), "menu_objetos")
    guardar(tira(perillas(), 32, 32), "menu_perillas")
    guardar(tira(interruptores(), 40, 18), "menu_interruptor")
    guardar(flechita(), "menu_flechita")
    guardar(fieltro(), "menu_fieltro")
    dibujos()
    guardar(K.ap_pc(), "aparato_pc")
    guardar(K.ap_cel(), "aparato_celular")
    guardar(K.ap_bocina(), "aparato_bocina")
    guardar(K.ap_tele(), "aparato_tele")
    guardar(ap_tablet(), "aparato_tablet")
    guardar(pared(0.0), "aparato_pared_dia")
    guardar(pared(1.0), "aparato_pared_noche")
