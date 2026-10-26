"""Reopen and check the delivered Blender study, optionally render its energy animation."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--animate', action='store_true')
    args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    output = Path(bpy.data.filepath).parent
    manifest = json.loads((output / (Path(bpy.data.filepath).stem+'-manifest.json')).read_text())
    for file, digest in {**manifest['production_sha256'], **manifest['geometry_sources']}.items():
        assert hashlib.sha256((ROOT/file).read_bytes()).hexdigest() == digest, file
    assert all(name in bpy.data.scenes for name in manifest['scenes'])
    results = []
    for scene in bpy.data.scenes:
        if not scene.name.startswith('Design /'):
            continue
        bpy.context.window.scene = scene
        bpy.context.view_layer.update()
        assert scene.camera is not None
        for obj in scene.objects:
            if obj.type == 'MESH':
                assert all(math.isfinite(c) for v in obj.data.vertices for c in v.co), obj.name
                assert all(p.area > 1e-12 for p in obj.data.polygons), obj.name
        if 'quantum_size' in scene:
            center = Vector(scene['quantum_center'])
            beams = [o for o in scene.objects if 'converging beam' in o.name]
            assert len(beams) == 6, scene.name
            normals = set()
            for obj in beams:
                z = [v.co.z for v in obj.data.vertices]
                ends = [obj.matrix_world @ Vector((0, 0, h)) for h in (min(z), max(z))]
                assert min((p-center).length for p in ends) < 1e-5, obj.name
                outer = max(ends, key=lambda p: (p-center).length)
                delta = outer-center
                axis = max(range(3), key=lambda i: abs(delta[i]))
                assert abs(abs(delta[axis])-scene['quantum_size']/2) < 1e-5
                normals.add((axis, 1 if delta[axis] > 0 else -1))
            assert len(normals) == 6
            sphere = next(o for o in scene.objects if o.name.startswith('Quantum / energy sphere'))
            assert (sphere.location-center).length < 1e-6
            assert len([o for o in scene.objects if o.name.startswith('Quantum / ceramic edge rail')]) == 12
            packet = next(o for o in scene.objects if 'inward packet' in o.name)
            scene.frame_set(2)
            initial = (packet.location-center).length
            scene.frame_set(8)
            assert (packet.location-center).length < initial, 'Packet must travel inward'
            scene.frame_set(31)
        if 'converter_cross_section' in scene:
            assert scene['converter_length'] == 2*scene['converter_cross_section']
        results.append({'scene': scene.name, 'objects': len(scene.objects),
                        'polygons': sum(len(o.data.polygons) for o in scene.objects if o.type == 'MESH')})
    for image in bpy.data.images:
        if image.source == 'FILE' and image.name != 'Render Result':
            assert image.packed_file, image.name
    assert len(results) == 7, len(results)
    (output/'verification.json').write_text(json.dumps({'production_unchanged': True,
                                                       'scenes': results}, indent=2)+'\n')
    print('DEVICE_STUDY_VERIFY_OK', len(results), 'design scenes; six-axis convergence; inward motion; packed textures')
    if args.animate:
        frames = output/'animation'
        frames.mkdir(exist_ok=False)
        scene = bpy.data.scenes['Design / Quantum Cube 3x3x3']
        bpy.context.window.scene = scene
        scene.render.resolution_x = scene.render.resolution_y = 640
        scene.cycles.samples = 24
        prefs = bpy.context.preferences.addons['cycles'].preferences
        prefs.compute_device_type = 'OPTIX'
        prefs.get_devices()
        for device in prefs.devices:
            device.use = device.type == 'OPTIX'
        scene.cycles.device = 'GPU' if any(d.use for d in prefs.devices) else 'CPU'
        for index, frame in enumerate(range(1, 121, 4)):
            scene.frame_set(frame)
            scene.render.filepath = str(frames/f'{index:03d}.png')
            bpy.ops.render.render(write_still=True)
        assert hashlib.sha256((frames/'000.png').read_bytes()).digest() != hashlib.sha256((frames/'010.png').read_bytes()).digest()
        print('DEVICE_ANIMATION_OK', frames)


if __name__ == '__main__':
    main()
