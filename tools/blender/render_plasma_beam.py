"""Render R / PLASMA as approved and as the game can draw it, on a run, a branch and a turn, at eye level.

Run with Blender's bundled Python. Shells take their texels from the dense core texture, rings are the artist's
straight and corner bbmodels; the joints' own shells are left out. Nothing in the mod's assets is written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bundle_beams
from plasma_beam import SCENE_SIZE, VARIANTS, generate, quads_per_block, ring_quads, run_paths, shell_tiles
from build_scene import camera
from render_bundle_beams import (beam_material, build_object, lit_material, ring_material, scene_setup, to_blender)

LOADS = {'idle': 0.0, 'medium': .6, 'full': 1.0, 'overload': 1.25}
# (scene, variant, load) for stills; the shipping soft variant also runs one loop on each scene.
STILLS = [('straight', 'approved', 'medium'), ('straight', 'ship_soft', 'idle'), ('straight', 'ship_soft', 'medium'),
          ('straight', 'ship_soft', 'full'), ('straight', 'ship_soft', 'overload'), ('straight', 'ship_six', 'medium'),
          ('branch', 'ship_soft', 'medium'), ('branch', 'ship_soft', 'overload'), ('turn', 'ship_soft', 'medium')]
LOOPS = [('straight', 'ship_soft', 'medium'), ('branch', 'ship_soft', 'medium'), ('turn', 'ship_soft', 'medium')]
FRAMES = 32
# Pixels per block at eye level, the same for every scene so sizes compare.
SCALE = 230


def fixed_pieces(scene, core_image, ring_image):
    pixels = list(core_image.pixels)
    width, height = core_image.size

    def colour(u, v):
        at = ((height - 1 - v) * width + u) * 4
        return tuple(round(c * 255) / 255 for c in pixels[at:at + 4])

    collection = bpy.data.collections.new('Fixed / ' + scene)
    tiles = shell_tiles(scene)
    keys = sorted({t for _, t in tiles})
    shell = build_object('Shell / ' + scene, [q for q, _ in tiles], collection,
                         [lit_material('texel %d,%d' % key, colour(*key)) for key in keys],
                         [keys.index(t) for _, t in tiles])
    quads = ring_quads(scene)
    rings = build_object('Rings / ' + scene, [q for q, _ in quads], collection, [ring_material(ring_image)],
                         [0] * len(quads), [uv for _, uv in quads])
    return collection, [shell, rings]


def beam(scene_name, variant, load, cycle, collection, materials=None):
    mesh = generate(variant, LOADS[load], cycle, run_paths(variant, scene_name))
    materials = materials or [beam_material('%s %s %s' % (variant, load, m[0]), *m[1:]) for m in mesh.materials]
    obj = build_object('R %s %s %s' % (scene_name, variant, load), [[mesh.vertices[i] for i in f] for f in mesh.faces],
                       collection, materials, mesh.indices)
    return obj, mesh, materials


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    asset_root = bundle_beams.ASSETS

    def asset_hashes():
        return {str(p.relative_to(asset_root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in asset_root.rglob('*') if p.is_file()}
    before = asset_hashes()
    scene = scene_setup()
    scene.name = 'R Plasma Beam'
    core_image = bpy.data.images.load(str(bundle_beams.CORE_TEXTURE))
    ring_image = bpy.data.images.load(str(bundle_beams.RING_TEXTURE))
    pieces, cameras = {}, {}
    for name, (width, height) in SCENE_SIZE.items():
        collection, objects = fixed_pieces(name, core_image, ring_image)
        scene.collection.children.link(collection)
        pieces[name] = objects
        middle = Vector(to_blender((width / 2, height / 2, 1)))
        # Square to the run at beam height, from the south (+Z), as a player beside the cable sees it.
        cameras[name] = (camera(scene, 'Camera / ' + name, tuple(middle + Vector((0, -14, 0))), tuple(middle),
                                max(width, height) + .6), width, height)

    def frame(name):
        for other, objects in pieces.items():
            for obj in objects:
                obj.hide_render = other != name
        cam, width, height = cameras[name]
        scene.camera = cam
        # The orthographic scale spans the longer side, so equal pixels per block on both.
        scene.render.resolution_x = round((width + .6) * SCALE)
        scene.render.resolution_y = round((height + .6) * SCALE)

    report = {'loads': LOADS, 'variants': {k: {'title': v[0], 'sides': v[1], 'samples_per_block': v[2],
                                               'sheaths': v[3], 'soft': v[4], 'quads_per_block': quads_per_block(k)}
                                           for k, v in VARIANTS.items()}, 'stills': [], 'loops': []}
    beams = bpy.data.collections.new('Beams')
    scene.collection.children.link(beams)
    for name, variant, load in STILLS:
        frame(name)
        obj, mesh, _ = beam(name, variant, load, .25, beams)
        scene.render.filepath = str(output / ('%s-%s-%s.png' % (name, variant, load)))
        bpy.ops.render.render(write_still=True, scene=scene.name)
        report['stills'].append({'scene': name, 'variant': variant, 'load': load, 'polygons': len(mesh.faces)})
        print('PLASMA_RENDERED %s %s %s' % (name, variant, load), flush=True)
        # Only the run at 60% stays shown in the saved file; every other beam is hidden before the next render.
        obj.hide_render = True
        obj.hide_viewport = not (name == 'straight' and variant == 'ship_soft' and load == 'medium')
    if args.animate:
        for obj in beams.objects:
            obj.hide_render = True
        scene.cycles.samples = 16
        for name, variant, load in LOOPS:
            frame(name)
            scene.render.resolution_x = scene.render.resolution_x * 3 // 4 // 2 * 2
            scene.render.resolution_y = scene.render.resolution_y * 3 // 4 // 2 * 2
            frames = output / ('%s-%s-%s' % (name, variant, load)) / 'frames'
            frames.mkdir(parents=True, exist_ok=True)
            materials = None
            for index in range(FRAMES):
                obj, _, materials = beam(name, variant, load, index / FRAMES, scene.collection, materials)
                scene.render.filepath = str(frames / ('%03d.png' % index))
                bpy.ops.render.render(write_still=True, scene=scene.name)
                data = obj.data
                bpy.data.objects.remove(obj)
                bpy.data.meshes.remove(data)
            report['loops'].append({'scene': name, 'variant': variant, 'load': load, 'frames': FRAMES})
            print('PLASMA_ANIMATED %s' % name, flush=True)
    frame('straight')
    for obj in beams.objects:
        obj.hide_render = obj.hide_viewport
    bpy.ops.wm.save_as_mainfile(filepath=str(output / 'plasma-beam.blend'))
    assert before == asset_hashes(), 'Production assets were modified'
    (output / 'plasma-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('PLASMA_BEAM_OK: %d stills, %d loops; assets unchanged' % (len(STILLS), len(report['loops'])), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=HERE.parents[1] / 'build/bundle-beam-check/plasma')
    parser.add_argument('--animate', action='store_true', help='also render one loop of each scene')
    main(parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []))
