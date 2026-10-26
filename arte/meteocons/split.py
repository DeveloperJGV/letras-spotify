"""Parte los íconos de Meteocons (fill) en capas: lo fijo y lo que la app anima (rayos, nubes, gotas, rayo)."""
import copy
import os
import sys
import xml.etree.ElementTree as ET

SRC = sys.argv[1]   # carpeta fill/
OUT = sys.argv[2]   # carpeta de svg partidos
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)

# escena: (archivo, {capa: ('only'|'except', [ids])})
SCENES = {
    'clear_day': ('clear-day', {'base': ('except', ['Rays']), 'rays': ('only', ['Rays'])}),
    'partly_day': ('partly-cloudy-day', {'base': ('except', ['Rays', 'Clouds']), 'rays': ('only', ['Rays']), 'cloud': ('only', ['Clouds'])}),
    'clear_night': ('clear-night', {'base': ('except', [])}),
    'partly_night': ('partly-cloudy-night', {'base': ('except', ['Clouds']), 'cloud': ('only', ['Clouds'])}),
    'overcast': ('overcast', {'back': ('only', ['Secondary Cloud']), 'cloud': ('except', ['Secondary Cloud'])}),
    'fog': ('fog', {'cloud': ('except', ['Precipitation']), 'fx1': ('only', ['Line 1']), 'fx2': ('only', ['Line 2'])}),
    'drizzle': ('drizzle', {'cloud': ('except', ['Precipitation']), 'fx1': ('only', ['Raindrop 1']), 'fx2': ('only', ['Raindrop 2']), 'fx3': ('only', ['Raindrop 3'])}),
    'rain': ('rain', {'cloud': ('except', ['Precipitation']), 'fx1': ('only', ['Raindrop 1']), 'fx2': ('only', ['Raindrop 2']), 'fx3': ('only', ['Raindrop 3'])}),
    'snow': ('snow', {'cloud': ('except', ['Precipitation']), 'fx1': ('only', ['Snowflake 1']), 'fx2': ('only', ['Snowflake 2']), 'fx3': ('only', ['Snowflake 3'])}),
    'storm': ('thunderstorms-rain', {'cloud': ('except', ['Precipitation', 'Lightning']), 'fx1': ('only', ['Raindrop 1']), 'fx2': ('only', ['Raindrop 2']), 'fx3': ('only', ['Raindrop 3']), 'bolt': ('only', ['Lightning'])}),
}

KEEP = {f'{{{NS}}}defs', f'{{{NS}}}clipPath'}


def parents(root):
    return {c: p for p in root.iter() for c in p}


def find(root, gid):
    for e in root.iter():
        if e.get('id') == gid:
            return e
    raise SystemExit(f'no hay {gid}')


def only(root, ids):
    """Deja solo los grupos [ids] (y sus ancestros, y los defs)."""
    par = parents(root)
    keep = set()
    for gid in ids:
        e = find(root, gid)
        keep.update(e.iter())
        while e in par:
            e = par[e]
            keep.add(e)
    keep.add(root)

    def prune(e):
        for c in list(e):
            if c.tag in KEEP:
                continue
            if c not in keep:
                e.remove(c)
            elif not any(c is find(root, g) for g in ids):
                prune(c)
    prune(root)


def without(root, ids):
    par = parents(root)
    for gid in ids:
        e = find(root, gid)
        par[e].remove(e)


os.makedirs(OUT, exist_ok=True)
for scene, (name, layers) in SCENES.items():
    src = ET.parse(os.path.join(SRC, name + '.svg')).getroot()
    for layer, (mode, ids) in layers.items():
        root = copy.deepcopy(src)
        (only if mode == 'only' else without)(root, ids)
        # Las gotas empiezan invisibles (su animación las prende): aquí se dibujan quietas y la app las mueve.
        for e in root.iter():
            if e.get('opacity') == '0':
                del e.attrib['opacity']
        ET.ElementTree(root).write(os.path.join(OUT, f'wx_{scene}_{layer}.svg'), encoding='unicode')
print('ok', len(os.listdir(OUT)))
