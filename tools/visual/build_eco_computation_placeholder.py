"""Write the temporary art of the Federation computation system, until the artist draws it: Neo ECO's computation
controller textures with their AE purple (hues 210-300, such as the chips) turned Federation green, the hue of the
Automation Interface (user, 2026-10-20), and its models and blockstates pointed at them. The communication interface is
Neo ECO's computation interface turned green the same way, its blue lights too; formed, it is a plate just outside the
formed controller's side face, which covers the cell. The artist of both mods allowed the recolour. Tier colours (L4 blue, L6 orange, L9
purple) live in textures this leaves to Neo ECO, so the tiers still read apart. The network switch variants map to
the plain ones, since the Federation system refuses a switch. Reads from the Neo ECO jar Gradle resolved for the
build (run `./gradlew :neoforge-1.21.1:compileJava` first).
"""
from pathlib import Path
import colorsys
import io
import json
import zipfile
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / 'common/src/main/resources/assets/ae2federation'
OUT = ASSETS / 'textures/block/eco_computation'
JAR = 'maven.modrinth/udZtKfzP/p9oqETs4'
SOURCE = 'assets/neoecoae/textures/block/compute/'
TEXTURES = ['controller/screen_off', 'controller_formed/controller_formed', 'controller_formed/controller_formed_a',
            'controller_formed/controller_formed_switch']
GREEN = 130 / 360
INTERFACE = {'me_bus': 'interface', 'me_bus_light': 'interface_light'}


def jar():
    found = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1' / JAR).glob('*/*.jar'))
    if not found:
        raise SystemExit('Neo ECO jar not in the Gradle cache; build the neoforge project first')
    return found[0]


def recolour(image, low=210 / 360):
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if a == 0 or s < 0.15 or not low <= h <= 300 / 360:
                continue
            r2, g2, b2 = colorsys.hls_to_rgb(GREEN, l, s)
            pixels[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return out


OUT.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(jar()) as archive:
    for name in TEXTURES:
        image = Image.open(io.BytesIO(archive.read(SOURCE + name + '.png'))).convert('RGBA')
        recolour(image).save(OUT / (name.split('/')[-1] + '.png'))
    for tier in ('l4', 'l6', 'l9'):
        state = json.loads(archive.read(f'assets/neoecoae/blockstates/computation_system_{tier}.json'))
        names = set()
        for variant in state['variants'].values():
            name = variant['model'].rsplit('/', 1)[-1].replace('_power_network_switch', '').replace(
                '_network_switch', '')
            variant['model'] = f'ae2federation:block/eco_computation/{name}'
            names.add(name)
        (ASSETS / 'blockstates' / f'eco_computation_system_{tier}.json').write_text(json.dumps(state, indent=2) + '\n')
        for name in sorted(names):
            text = archive.read(f'assets/neoecoae/models/block/computation_controller/{name}.json').decode()
            for texture in TEXTURES:
                text = text.replace(f'"neoecoae:block/compute/{texture}"',
                                    f'"ae2federation:block/eco_computation/{texture.split("/")[-1]}"')
            model = json.loads(text)
            base = model.get('children', {}).get('base')
            if base:
                # The base model's own texture is Neo ECO's; ours replaces it.
                parent = json.loads(archive.read('assets/neoecoae/models/block/computation_controller/'
                                                 + base['parent'].rsplit('/', 1)[-1] + '.json'))
                own = parent['textures']['base'].removeprefix('neoecoae:block/compute/controller_formed/')
                base['textures']['base'] = f'ae2federation:block/eco_computation/{own}'
            else:
                model['textures']['screen'] = 'ae2federation:block/eco_computation/screen_off'
            folder = ASSETS / 'models/block/eco_computation'
            folder.mkdir(parents=True, exist_ok=True)
            (folder / f'{name}.json').write_text(json.dumps(model, indent=2) + '\n')
        (ASSETS / 'models/item' / f'eco_computation_system_{tier}.json').write_text(json.dumps(
            {'parent': f'ae2federation:block/eco_computation/controller_{tier}_off'}, indent=2) + '\n')
    for name, own in INTERFACE.items():
        image = Image.open(io.BytesIO(archive.read(SOURCE + name + '.png'))).convert('RGBA')
        recolour(image, low=180 / 360).save(OUT / (own + '.png'))
    model = json.loads(archive.read('assets/neoecoae/models/block/computation_interface.json'))
    model['textures'] = {'particle': 'ae2federation:block/eco_computation/interface',
                         'block': 'ae2federation:block/eco_computation/interface',
                         'lights': 'ae2federation:block/eco_computation/interface_light'}
    folder = ASSETS / 'models/block/eco_computation'
    (folder / 'interface.json').write_text(json.dumps(model, indent=2) + '\n')
    # Formed, the controller's model covers the side face: the interface is drawn as Neo ECO draws its own, both
    # layers, on a plate a hair outside its north face, turned to face out.
    plate = []
    for element in model['elements']:
        faces = {'north': element['faces']['north']}
        plate.append({'from': [0, 0, -0.02 * (len(plate) + 1)], 'to': [16, 16, -0.02 * (len(plate) + 1)],
                      **({'shade': element['shade']} if 'shade' in element else {}), 'faces': faces})
    formed = {'parent': 'block/block', 'render_type': model['render_type'], 'textures': model['textures'],
              'elements': list(reversed(plate))}
    (folder / 'interface_formed.json').write_text(json.dumps(formed, indent=2) + '\n')
    variants = {'formed=false': {'model': 'ae2federation:block/eco_computation/interface'}}
    for facing, turn in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variants[f'facing={facing},formed=true'] = {'model': 'ae2federation:block/eco_computation/interface_formed',
                                                    **({'y': turn} if turn else {})}
    (ASSETS / 'blockstates/eco_computation_interface.json').write_text(json.dumps({'variants': variants}, indent=2)
                                                                       + '\n')
    (ASSETS / 'models/item/eco_computation_interface.json').write_text(json.dumps(
        {'parent': 'ae2federation:block/eco_computation/interface'}, indent=2) + '\n')
print('Wrote temporary Federation computation system textures, models and blockstates.')
