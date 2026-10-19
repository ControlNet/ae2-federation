"""Render real-asset multiblock studies with Blender's bundled Python.

Run: blender --background --factory-startup --python tools/multiblock/render.py
All generated artifacts stay in the repository-root build directory.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from geometry import ASSETS, ROOT, build, cuboid


def material(device):
    mat = bpy.data.materials.new(device + ' / original artist texture')
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    shader = nodes.get('Principled BSDF')
    shader.inputs['Roughness'].default_value = .82
    shader.inputs['Specular IOR Level'].default_value = .18
    texture = nodes.new('ShaderNodeTexImage')
    texture.image = bpy.data.images.load(str(ASSETS / 'textures/block' / device / (device+'.png')))
    texture.image.pack()
    texture.interpolation = 'Closest'
    mat.node_tree.links.new(texture.outputs['Color'], shader.inputs['Base Color'])
    return mat


def mesh(name, quads, mat, scene, offset):
    vertices, faces, uvs = [], [], []
    for q in quads:
        start = len(vertices)
        vertices.extend((x, -z, y) for x,y,z in q['positions'])
        faces.append(tuple(range(start, start+4)))
        uvs.extend((u, 1-v) for u,v in q['uv'])
    data = bpy.data.meshes.new(name)
    data.from_pydata(vertices, [], faces)
    data.update()
    data.materials.append(mat)
    layer = data.uv_layers.new(name='Source texture UV')
    for i, uv in enumerate(uvs):
        layer.data[i].uv = uv
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.location = offset
    return obj


def plain_material(name, color, emission=False):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*color, 1)
    shader.inputs['Roughness'].default_value = .9
    if emission:
        shader.inputs['Emission Color'].default_value = (*color, 1)
        shader.inputs['Emission Strength'].default_value = 1
    return mat


def scene_for(name, scale, width=1600, height=1050, front=False):
    scene = bpy.data.scenes.new(name)
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 32
    scene.cycles.use_denoising = True
    scene.cycles.seed = 17
    scene.render.resolution_x, scene.render.resolution_y = width, height
    scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = 'Standard'
    scene.world = bpy.data.worlds.new(name + ' / world')
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (.14,.18,.24,1)
    bg.inputs['Strength'].default_value = .6
    data = bpy.data.cameras.new(name + ' / camera')
    data.type = 'ORTHO'
    data.ortho_scale = scale
    camera = bpy.data.objects.new(data.name, data)
    scene.collection.objects.link(camera)
    target = Vector((0,0,1))
    camera.location = target + Vector((0,-18,0) if front else (8,-14,10))
    camera.rotation_euler = (target-camera.location).to_track_quat('-Z','Y').to_euler()
    scene.camera = camera
    floor = bpy.data.meshes.new(name + ' / floor')
    floor.from_pydata([(-80,-80,-.02),(80,-80,-.02),(80,80,-.02),(-80,80,-.02)], [], [(0,1,2,3)])
    floor.materials.append(plain_material(name+' / floor', (.025,.033,.05)))
    obj = bpy.data.objects.new(floor.name, floor)
    scene.collection.objects.link(obj)
    obj.hide_render = front
    for title, position, energy, size in [('Key',(-3,-6,10),1400,7), ('Fill',(7,3,7),1000,6)]:
        light = bpy.data.lights.new(name + ' / ' + title, 'AREA')
        light.energy, light.size = energy, size
        obj = bpy.data.objects.new(light.name, light)
        scene.collection.objects.link(obj)
        obj.location = position
        obj.rotation_euler = (-obj.location).to_track_quat('-Z','Y').to_euler()
    return scene


def text(scene, body, x, y, size, color=(.67,.79,.9), center=False):
    """Camera-space typography, independent of object perspective and lighting."""
    curve = bpy.data.curves.new(body, 'FONT')
    curve.body, curve.size = body, size
    curve.align_x = 'CENTER' if center else 'LEFT'
    obj = bpy.data.objects.new(body, curve)
    scene.collection.objects.link(obj)
    obj.rotation_euler = scene.camera.rotation_euler
    obj.location = scene.camera.location + scene.camera.rotation_euler.to_matrix() @ Vector((x,y,-5))
    curve.materials.append(plain_material(body, color, emission=True))
    if hasattr(obj, 'visible_shadow'):
        obj.visible_shadow = False


def add_device(scene, mats, device, size, horizontal, connected=True, vertical=0, switch_style='plain', router_style='plain'):
    quads = build(device, cuboid(size), connected, switch_style, router_style)
    right = scene.camera.rotation_euler.to_matrix() @ Vector((1,0,0))
    offset = right*horizontal + Vector((-size[0]/2, size[2]/2, vertical))
    obj = mesh(device + (' / connected' if connected else ' / stacked') + str(size), quads,
               mats[device], scene, offset)
    obj['connected_preview'] = connected
    obj['dimensions_xyz'] = size
    return quads


def render(scene, output, name, only=None):
    if only is not None and only != name:
        return
    scene.render.image_settings.file_format = 'PNG'
    scene.render.filepath = str(output / (name+'.png'))
    bpy.context.window.scene = scene
    bpy.ops.render.render(write_still=True, scene=scene.name)
    print('MULTIBLOCK_RENDERED ' + name, flush=True)


def run(args):
    output = (ROOT / 'build/multiblock-devices').resolve()
    output.mkdir(parents=True, exist_ok=True)
    source_files = [ASSETS / folder / (device+'.json' if folder == Path('models/block') else device+'/'+device+'.png')
                    for folder in [Path('models/block'), Path('textures/block')] for device in ['router','switch']]
    def hashes():
        return {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}
    before = hashes()
    mats = {device: material(device) for device in ['router','switch']}
    report = {'status':'Offline geometry implementation; no Minecraft runtime assembly integration',
              'source_sha256':before, 'renders':[], 'meshes':{}}
    for device in ['router','switch']:
        scene = scene_for(device.title()+' comparison', 10.8)
        text(scene, 'AE2 FEDERATION  /  '+device.upper(), -4.9, 2.95, .25)
        text(scene, 'CONNECTED MULTIBLOCK STUDY  /  3 x 2 x 2', -4.9, 2.55, .14)
        for x, connected, label in [(-2.65, False, 'ORIGINAL BLOCKS'),(2.65, True, 'CONNECTED CHASSIS')]:
            quads = add_device(scene, mats, device, (3,2,2), x, connected)
            text(scene, label, x, -1.95, .22, center=True)
            text(scene, 'Repeated block frames' if not connected else 'One outer frame / repeating panels',
                 x, -2.28, .125, center=True)
            if connected:
                filename = device+'-3x2x2-mesh.json'
                (output/filename).write_text(json.dumps(quads, separators=(',',':'))+'\n')
                report['meshes'][device] = {'quads':len(quads), 'file':filename}
        text(scene, 'Actual project textures  |  Offline geometry preview  |  No replacement artwork',
             -4.9, -3.05, .115)
        render(scene, output, device+'-comparison', args.only)
        report['renders'].append(device+'-comparison.png')

    # Flat projection makes the continuous frame and removed inner borders easy to inspect.
    scene = scene_for('Front face inspection', 8.8, 1600, 1000, front=True)
    text(scene, 'FRONT FACE  /  OUTER FRAME + INNER PANELS', -4, 2.38, .2)
    text(scene, 'Each device is 3 x 3 x 1 blocks. Both use the same assembly rules.', -4, 2.03, .115)
    for x, device in [(-2.05,'router'),(2.05,'switch')]:
        add_device(scene, mats, device, (3,3,1), x, vertical=-.5)
        text(scene, device.upper()+'  /  3 x 3 x 1', x, -1.93, .18, center=True)
    render(scene, output, 'front-panel-detail', args.only)
    report['renders'].append('front-panel-detail.png')

    scene = scene_for('Thin structure inspection', 10.8, 1600, 1000)
    text(scene, 'THIN STRUCTURES  /  CONTINUOUS EDGES', -4.9, 2.8, .22)
    text(scene, '1 x 1 x 4 connected devices; isolated source blocks shown beside them.', -4.9, 2.43, .12)
    for x, device in [(-2.6,'router'),(2.6,'switch')]:
        add_device(scene, mats, device, (1,1,4), x)
        add_device(scene, mats, device, (1,1,1), x-1.35, connected=False)
        text(scene, device.upper(), x, -2.3, .2, center=True)
    render(scene, output, 'thin-structures', args.only)
    report['renders'].append('thin-structures.png')

    assert before == hashes(), 'Source art changed during rendering'
    report['source_assets_unchanged'] = True
    (output/'report.json').write_text(json.dumps(report, indent=2)+'\n')
    bpy.context.window.scene = bpy.data.scenes['Router comparison']
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'multiblock-devices.blend'))
    print('MULTIBLOCK_OK '+str(output), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=['router-comparison', 'switch-comparison',
                                         'front-panel-detail', 'thin-structures'])
    run(parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []))
