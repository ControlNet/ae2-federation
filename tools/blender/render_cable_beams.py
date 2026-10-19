"""Render the 1x1 cable beam concepts inside the production cable shells, from matched cameras.

Run with Blender's bundled Python on a scene built by build_scene.py, e.g.

    blender --background tools/blender/build/cable-r/federation.blend --python tools/blender/render_cable_beams.py \\
        -- --output build/cable-beam-concepts --animate

The shells (core, arms and collars, from production FederationCableBuilder through export_cable.py) are the 'Cable
Beam Inspection' scene's; its old flow objects are left out and each concept's beam takes their place. Nothing in the
mod's assets is written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bundle_beams
from cable_beams import LOADS, PROFILES, WINDOW, generate, glow_reach, quads_per_block, solid_reach
from build_scene import camera
from render_bundle_beams import beam_material

FRAMES = 32
# Camera: (position, target, orthographic scale, width, height), in Blender axes. The run lies along X at y -3.5,
# z .5, its joint at x .5 with a branch toward -Y.
CAMERAS = {
    # Square to the run at beam height, as a player beside the cable sees it.
    'level': ((.5, -15.5, .5), (.5, -3.5, .5), 5.2, 1500, 460),
    'close': ((1, -11, 2.2), (-1.6, -3.5, .5), 4.4, 1280, 600),
}


def beam_object(name, mesh, scene, materials):
    data = bpy.data.meshes.new(name)
    data.from_pydata(mesh.vertices, [], mesh.faces)
    data.update()
    for material in materials:
        data.materials.append(material)
    for polygon, index in zip(data.polygons, mesh.indices):
        polygon.material_index = index
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    return obj


def remove(obj):
    data = obj.data
    bpy.data.objects.remove(obj)
    bpy.data.meshes.remove(data)


def cells_of(template):
    """(mask, world block, Blender origin) of each production flow placement in the template."""
    cells = []
    for obj in template.objects:
        if '/ Flow' not in obj.name:
            continue
        match = re.search(r'Network / x=(-?\d+)', obj.name)
        if match:
            x = int(match.group(1))
            cells.append((19 if x == 0 else 3, (x, 0, 0), tuple(obj.location)))
        elif 'south branch' in obj.name:
            cells.append((32, (0, 0, 1), tuple(obj.location)))
        else:
            raise ValueError('Unknown production flow placement: ' + obj.name)
    assert len(cells) == 10, cells
    return cells


def main(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    asset_root = bundle_beams.ASSETS

    def asset_hashes():
        return {str(p.relative_to(asset_root)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in asset_root.rglob('*') if p.is_file()}
    before = asset_hashes()
    template = bpy.data.scenes['Cable Beam Inspection']
    cells = cells_of(template)
    scene = bpy.data.scenes.new('Cable Beam Concepts')
    scene.world = template.world
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.samples = 40
    scene.cycles.use_denoising = True
    scene.cycles.transparent_max_bounces = 48
    scene.cycles.seed = 41
    scene.view_settings.view_transform = template.view_settings.view_transform
    scene.view_settings.look = template.view_settings.look
    scene.render.resolution_percentage = 100
    for obj in template.objects:
        if '/ Flow' not in obj.name and obj.type != 'CAMERA':
            scene.collection.objects.link(obj)
    cameras = {key: (camera(scene, 'Camera / ' + key, position, target, scale), width, height)
               for key, (position, target, scale, width, height) in CAMERAS.items()}

    def aim(key):
        cam, width, height = cameras[key]
        scene.camera = cam
        scene.render.resolution_x, scene.render.resolution_y = width, height

    report = {'loads': LOADS, 'window': WINDOW, 'cells': cells, 'profiles': {}, 'stills': [], 'loops': []}
    for profile, (title, description, scale, shells, sides) in PROFILES.items():
        if args.profiles and profile not in args.profiles:
            continue
        sample = generate(profile, LOADS['full'], .25, cells)
        report['profiles'][profile] = {'title': title, 'description': description, 'radius_scale': scale,
                                       'sheaths': shells, 'sides': sides, 'quads_per_block': quads_per_block(profile),
                                       'solid_reach_full': solid_reach(sample, cells),
                                       'glow_reach_full': glow_reach(sample, cells)}
        for load in args.loads or LOADS:
            mesh = generate(profile, LOADS[load], .25, cells)
            materials = [beam_material('%s %s %s' % (profile, load, m[0]), *m[1:]) for m in mesh.materials]
            obj = beam_object('%s / %s' % (title, load), mesh, scene, materials)
            for key in CAMERAS:
                aim(key)
                scene.render.filepath = str(output / ('%s-%s-%s.png' % (profile, load, key)))
                bpy.ops.render.render(write_still=True, scene=scene.name)
                report['stills'].append({'profile': profile, 'load': load, 'camera': key,
                                         'polygons': len(mesh.faces)})
            print('CABLE_BEAM_RENDERED %s %s' % (profile, load), flush=True)
            # Only the 60% beam of the first concept stays shown in the saved file.
            obj.hide_render = True
            obj.hide_viewport = not (profile == next(iter(PROFILES)) and load == 'medium')
    if args.animate:
        aim('close')
        scene.render.resolution_x, scene.render.resolution_y = 960, 450
        scene.cycles.samples = 16
        for profile in args.profiles or PROFILES:
            frames = output / profile / 'frames'
            frames.mkdir(parents=True, exist_ok=True)
            first = generate(profile, LOADS['medium'], 0, cells)
            materials = [beam_material('%s loop %s' % (profile, m[0]), *m[1:]) for m in first.materials]
            for index in range(FRAMES):
                mesh = generate(profile, LOADS['medium'], index / FRAMES, cells)
                assert mesh.faces == first.faces and mesh.materials == first.materials, (profile, index)
                obj = beam_object('%s / loop' % profile, mesh, scene, materials)
                scene.render.filepath = str(frames / ('%03d.png' % index))
                bpy.ops.render.render(write_still=True, scene=scene.name)
                remove(obj)
            report['loops'].append({'profile': profile, 'load': 'medium', 'camera': 'close', 'frames': FRAMES})
            print('CABLE_BEAM_ANIMATED %s' % profile, flush=True)
    aim('close')
    bpy.ops.wm.save_as_mainfile(filepath=str(output / 'cable-beams.blend'))
    assert before == asset_hashes(), 'Production assets were modified'
    (output / 'cable-beams-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('CABLE_BEAMS_OK: %d stills, %d loops; assets unchanged' % (len(report['stills']), len(report['loops'])),
          flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=HERE.parents[1] / 'build/cable-beam-concepts')
    parser.add_argument('--animate', action='store_true', help='also render one 60% loop of each concept')
    parser.add_argument('--profiles', nargs='*', choices=PROFILES, help='only these concepts')
    parser.add_argument('--loads', nargs='*', choices=LOADS, help='only these loads')
    main(parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []))
