"""Write the temporary art of the Federation computation system, until the artist draws it: Neo ECO's computation
controller textures with their AE purple (hues 210-300, such as the chips) turned Federation green, the hue of the
Automation Interface (user, 2026-10-20), and its models and blockstates pointed at them. Neo ECO draws its ME interface
into the formed side face, in the base and in the tier lights; that cell is painted over with its mirrored neighbour,
so the side face shows only the communication interfaces actually built (user, 2026-10-21). The lights are copied
unchanged otherwise, so the tier colours stay Neo ECO's. The communication interface's four connected-face tiles
(interface_base, _corner, _side_hor, _side_ver) are the Router's, Federation teal and all (user, 2026-10-21), with the
frame's dark ring painted white, so an interface sits in the side face as a light square, as Neo ECO's own did, and
blends with the face's light rim (user asked for no dark border, 2026-10-21). The client composes them like a
Router's faces, framing a lone interface and joining a filled 2x2 square into one panel (user, 2026-10-21). The
artist replaces those four files to change the look. The artist of both mods allowed the recolour. Tier colours (L4 blue, L6 orange, L9
purple) live in textures this leaves to Neo ECO, so the tiers still read apart. ECO - N1 gets the same treatment from
Neo ECO Prototype's C1 sheets (its author allowed it as our placeholder, 2026-10-25): its chips turn green, its ME
interface cell is painted over and its green L1 lights are kept; the models stay Prototype's geometry. The network switch variants map to
the plain ones, since the Federation system refuses a switch. Reads from the Neo ECO jar Gradle resolved for the
build, and Neo ECO Prototype's likewise (run `./gradlew :neoforge-1.21.1:compileJava` first).
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
PROTOTYPE_JAR = 'maven.modrinth/3HO3APHb/YY1JqK65'
PROTOTYPE = 'neoecoprototype:block/computation_controller/'
PROTOTYPE_SOURCE = 'assets/neoecoprototype/textures/block/computation_recolor/'
SOURCE = 'assets/neoecoae/textures/block/compute/'
TEXTURES = ['controller/screen_off', 'controller_formed/controller_formed', 'controller_formed/controller_formed_a',
            'controller_formed/controller_formed_switch']
GREEN = 130 / 360
LIGHTS = [f'controller_formed/controller_formed_light_{tier}{mirror}' for tier in 'abc' for mirror in ('', '_switch')]
TILES = ('base', 'corner', 'side_hor', 'side_ver')


def jar(path=JAR):
    found = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1' / path).glob('*/*.jar'))
    if not found:
        raise SystemExit(path + ' jar not in the Gradle cache; build the neoforge project first')
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


def without_me_interface(image, name):
    """The formed side face (u 6-10, v 0-6 of 16) with Neo ECO's ME interface cell, the middle cell of its left
    column (right when mirrored), painted over with the other column's middle cell, mirrored."""
    out = image.copy()
    unit = image.width // 16
    x, cell = 6 * unit, 2 * unit
    chip, other = (x + cell, x) if name.endswith('_switch') else (x, x + cell)
    out.paste(image.crop((other, cell, other + cell, 2 * cell)).transpose(Image.FLIP_LEFT_RIGHT), (chip, cell))
    return out


ROUTER_DARK_RING = {(0x41, 0x3F, 0x54), (0x4D, 0x4D, 0x67)}
ROUTER_WHITE = (0xF2, 0xF2, 0xF2, 255)


def interface_tiles():
    """The Router's four connected-face tiles, the frame's dark ring painted white; the teal marks across it stay."""
    tiles = {}
    for name in TILES:
        image = Image.open(ASSETS / f'textures/block/router/router_{name}.png').convert('RGBA')
        if name != 'base':
            pixels = image.load()
            for y in range(image.height):
                for x in range(image.width):
                    if pixels[x, y][3] and pixels[x, y][:3] in ROUTER_DARK_RING:
                        pixels[x, y] = ROUTER_WHITE
        tiles[name] = image
    return tiles


def compose(tiles):
    """A lone block's face from the four tiles, as the client composes it: the frame laid round the base."""
    def band(tile, horizontal):
        count = 0
        while count < 8 and all(tile.getpixel((i, count) if horizontal else (count, i))[3] for i in range(16)):
            count += 1
        return count
    rows, columns = band(tiles['side_hor'], True), band(tiles['side_ver'], False)
    face = tiles['base'].copy()
    for name, us, vs in (('side_hor', range(16), [*range(rows), *range(16 - rows, 16)]),
                         ('side_ver', [*range(columns), *range(16 - columns, 16)], range(16)),
                         ('corner', [*range(columns), *range(16 - columns, 16)], [*range(rows), *range(16 - rows, 16)])):
        for v in vs:
            for u in us:
                if tiles[name].getpixel((u, v))[3]:
                    face.putpixel((u, v), tiles[name].getpixel((u, v)))
    return face


def prototype_l1(archive, l4_state):
    """N1's textures, models and blockstate from Neo ECO Prototype's C1 controller, under eco_computation/l1/."""
    out = OUT / 'l1'
    out.mkdir(parents=True, exist_ok=True)
    def read(name):
        return Image.open(io.BytesIO(archive.read(PROTOTYPE_SOURCE + name + '.png'))).convert('RGBA')
    recolour(read('controller/screen_off')).save(out / 'screen_off.png')
    for mirror in ('', '_switch'):
        without_me_interface(recolour(read('controller_formed/controller_formed' + mirror)),
                             mirror).save(out / f'controller_formed{mirror}.png')
        without_me_interface(read('controller_formed/controller_formed_light_a' + mirror),
                             mirror).save(out / f'controller_formed_light_a{mirror}.png')
    ours = 'ae2federation:block/eco_computation/l1/'
    folder = ASSETS / 'models/block/eco_computation'
    sheets = ('controller_formed', 'controller_formed_switch', 'controller_formed_light_a',
              'controller_formed_light_a_switch')
    for mirror in ('', '_mirrored'):
        text = archive.read(f'assets/neoecoprototype/models/block/computation_controller/controller_l1_formed{mirror}.json')
        text = text.decode()
        for sheet in sheets:
            text = text.replace(f'"neoecoprototype:block/computation_recolor/controller_formed/{sheet}"',
                                f'"{ours}{sheet}"')
        model = json.loads(text)
        # The base model's own sheet and the screen behind the screen light are Prototype's; ours replace them.
        base = model['children']['base']
        parent = json.loads(archive.read('assets/neoecoprototype/models/block/computation_controller/'
                                         + base['parent'].rsplit('/', 1)[-1] + '.json'))
        own = parent['textures']['base'].rsplit('/', 1)[-1]
        base['textures'].setdefault('base', ours + own)
        base['textures']['screen'] = ours + 'screen_off'
        (folder / f'controller_l1_formed{mirror}.json').write_text(json.dumps(model, indent=2) + '\n')
    (folder / 'controller_l1_off.json').write_text(json.dumps(
        {'parent': PROTOTYPE + 'controller_off_base',
         'textures': {'level': 'neoecoprototype:block/computation_recolor/controller/controller_side_layer_a',
                      'screen': ours + 'screen_off'}}, indent=2) + '\n')
    # N1 is N4's block, so it has N4's block states; L4's models become L1's.
    state = json.loads(json.dumps(l4_state).replace('/controller_l4_', '/controller_l1_'))
    (ASSETS / 'blockstates/eco_computation_system_l1.json').write_text(json.dumps(state, indent=2) + '\n')
    (ASSETS / 'models/item/eco_computation_system_l1.json').write_text(json.dumps(
        {'parent': 'ae2federation:block/eco_computation/controller_l1_off'}, indent=2) + '\n')


OUT.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(jar()) as archive:
    for name in TEXTURES:
        image = Image.open(io.BytesIO(archive.read(SOURCE + name + '.png'))).convert('RGBA')
        image = recolour(image)
        if name.startswith('controller_formed/'):
            image = without_me_interface(image, name)
        image.save(OUT / (name.split('/')[-1] + '.png'))
    for name in LIGHTS:
        image = Image.open(io.BytesIO(archive.read(SOURCE + name + '.png'))).convert('RGBA')
        without_me_interface(image, name).save(OUT / (name.split('/')[-1] + '.png'))
    for tier in ('l4', 'l6', 'l9'):
        state = json.loads(archive.read(f'assets/neoecoae/blockstates/computation_system_{tier}.json'))
        names = set()
        for variant in state['variants'].values():
            name = variant['model'].rsplit('/', 1)[-1].replace('_power_network_switch', '').replace(
                '_network_switch', '')
            variant['model'] = f'ae2federation:block/eco_computation/{name}'
            names.add(name)
        (ASSETS / 'blockstates' / f'eco_computation_system_{tier}.json').write_text(json.dumps(state, indent=2) + '\n')
        if tier == 'l4':
            l4_state = state
        for name in sorted(names):
            text = archive.read(f'assets/neoecoae/models/block/computation_controller/{name}.json').decode()
            for texture in TEXTURES + LIGHTS:
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
    tiles = interface_tiles()
    for name, tile in tiles.items():
        tile.save(OUT / f'interface_{name}.png')
    compose(tiles).save(OUT / 'interface.png')
    folder = ASSETS / 'models/block/eco_computation'
    (folder / 'interface.json').write_text(json.dumps(
        {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'ae2federation:block/eco_computation/interface'}},
        indent=2) + '\n')
    # Formed, the controller's model covers the side face, so the interface is a plate a hair outside its north face,
    # turned to face out. The client draws the composed tiles there instead; this plate shows without them.
    face = {'uv': [0, 0, 16, 16], 'texture': '#face'}
    formed = {'parent': 'block/block', 'render_type': 'cutout',
              'textures': {'particle': 'ae2federation:block/eco_computation/interface',
                           'face': 'ae2federation:block/eco_computation/interface'},
              'elements': [{'from': [0, 0, -0.04], 'to': [16, 16, -0.04], 'faces': {'north': face}}]}
    (folder / 'interface_formed.json').write_text(json.dumps(formed, indent=2) + '\n')
    variants = {'formed=false': {'model': 'ae2federation:block/eco_computation/interface'}}
    for facing, turn in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variants[f'facing={facing},formed=true'] = {'model': 'ae2federation:block/eco_computation/interface_formed',
                                                    **({'y': turn} if turn else {})}
    (ASSETS / 'blockstates/eco_computation_interface.json').write_text(json.dumps({'variants': variants}, indent=2)
                                                                       + '\n')
    (ASSETS / 'models/item/eco_computation_interface.json').write_text(json.dumps(
        {'parent': 'ae2federation:block/eco_computation/interface'}, indent=2) + '\n')
with zipfile.ZipFile(jar(PROTOTYPE_JAR)) as archive:
    prototype_l1(archive, l4_state)
print('Wrote temporary Federation computation system textures, models and blockstates.')
