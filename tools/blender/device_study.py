"""Render current assets and editable Quantum Cube / Beam Converter design studies.

Run with Blender's bundled Python. Outputs are isolated from production resources.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
from assets import Assets, dependency_packs, model_quads
from materials import Materials
from build_scene import camera, collection, mesh_object, setup_scene


def resource_hashes():
    root = ROOT / 'common/src/main/resources/assets/ae2federation'
    return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(root.rglob('*')) if p.is_file()}


def configure(scene, args, scale=3.6, target=(0, 0, .5)):
    scene.render.resolution_x = scene.render.resolution_y = args.resolution
    scene.cycles.samples = 32
    scene.view_settings.exposure = -.45
    if args.device != 'cpu':
        prefs = bpy.context.preferences.addons['cycles'].preferences
        prefs.compute_device_type = args.device.upper()
        prefs.get_devices()
        for device in prefs.devices:
            device.use = device.type == args.device.upper()
        if not any(d.use for d in prefs.devices):
            raise RuntimeError('Requested Cycles device is unavailable')
        scene.cycles.device = 'GPU'
    scene.camera.location = Vector(target) + Vector((6, -9, 6))
    scene.camera.rotation_euler = (Vector(target) - scene.camera.location).to_track_quat('-Z', 'Y').to_euler()
    scene.camera.data.ortho_scale = scale


def render(scene, path):
    if path.exists():
        raise FileExistsError(f'Choose a new output directory; render already exists: {path}')
    bpy.context.window.scene = scene
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def reference(args, assets, materials):
    refs = [('router', 'Router'), ('pattern_provider', 'Pattern Provider'),
            ('processing_endpoint', 'Processing Endpoint'), ('switch', 'Switch'),
            ('beam_former', 'Beam Former 1x1'), ('beam_former_huge', 'Beam Former 3x3'),
            ('quantum_cube', 'Quantum Cube - current temporary asset'),
            ('beam_converter', 'Beam Converter - current temporary asset')]
    scenes = []
    for key, title in refs:
        scene = setup_scene('Reference / ' + title)
        coll = collection(scene, title + ' / current production geometry')
        if key == 'beam_former_huge':
            for x in range(3):
                for y in range(3):
                    model = assets.model(f'ae2federation:block/beam_former_huge_{x}{y}')
                    mesh_object(f'Former member {x},{y}', model_quads(assets, model), coll,
                                materials, (x-1.5, .5, y), cutout=True)
            configure(scene, args, 4.9, (0, 0, 1.5))
        else:
            model = assets.model('ae2federation:block/' + key)
            mesh_object(title, model_quads(assets, model), coll, materials, (-.5, .5, 0), cutout=True)
            configure(scene, args, 1.9)
        scenes.append(scene)
        render(scene, args.output / ('reference-' + key + '.png'))
    return scenes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=HERE / 'build/device-study-v2')
    parser.add_argument('--phase', choices=('reference', 'concepts', 'all'), default='all')
    parser.add_argument('--device', choices=('cpu', 'optix', 'cuda'), default='optix')
    parser.add_argument('--resolution', type=int, default=800)
    args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    blend = args.output / (args.phase + '.blend')
    if blend.exists():
        raise FileExistsError(f'Choose a new --output directory to preserve manual work: {blend}')
    before = resource_hashes()
    assets = Assets(ROOT, args.output / 'resources', dependency_packs(ROOT))
    materials = Materials(assets, [])
    scenes = reference(args, assets, materials) if args.phase in ('reference', 'all') else []
    if args.phase in ('concepts', 'all'):
        from export_cable import export
        export(ROOT, args.output / 'cable.json')
        from device_designs import concepts
        scenes.extend(concepts(args, assets, materials, configure, render))
    bpy.context.window.scene = next((s for s in scenes if s.name.startswith('Design / Quantum')), scenes[0])
    for scene in scenes:
        scene.frame_end = 120
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == 'VIEW_3D':
                area.spaces.active.region_3d.view_perspective = 'CAMERA'
                area.spaces.active.shading.type = 'MATERIAL'
    bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    assert before == resource_hashes(), 'Production resources changed during study'
    (args.output / (args.phase + '-manifest.json')).write_text(json.dumps({
        'production_unchanged': True, 'sources': assets.used,
        'production_sha256': before, 'scenes': [s.name for s in scenes],
        'blender': bpy.app.version_string, 'scope': 'Editable design study; no runtime integration',
        'geometry_sources': json.loads((args.output / 'cable.json').read_text())['source_sha256']
        if (args.output / 'cable.json').exists() else {},
    }, indent=2) + '\n')
    print('DEVICE_STUDY_OK', blend)


if __name__ == '__main__':
    main()
