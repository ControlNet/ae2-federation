"""Reopen the path-sanitized Blender artifact and check its imported scene and packed assets."""
from pathlib import Path
import sys
sys.dont_write_bytecode = True
import bpy

DIRECTORY = Path('tools/blender/build/beam-halo')
bpy.ops.wm.open_mainfile(filepath=str(DIRECTORY / 'beam-halo.blend'))
scene = bpy.data.scenes['Bundle Beam Concepts']
# Evaluate the study scene, not the factory scene that was active when saved.
bpy.context.window.scene = scene
bpy.context.view_layer.update()
formers = [obj for obj in scene.objects if obj.name.startswith('Former ')]
assert len(formers) == 8
assert len([obj for obj in scene.objects if obj.type == 'CAMERA']) == 4
images = [image for image in bpy.data.images if image.packed_file]
assert images and all(image.size[0] > 0 and image.size[1] > 0 for image in images)
beam = scene.objects['Shipped R plasma']
assert abs(min(v.co.y for v in beam.data.vertices) + 7) < 1e-5
assert abs(max(v.co.y for v in beam.data.vertices) + 1) < 1e-5
assert scene.objects['Proposed halo B']
assert scene.cycles.samples == 32 and scene.cycles.use_denoising
assert scene.view_settings.view_transform == 'Standard'
assert abs(scene.view_settings.exposure + .25) < 1e-6
from mathutils import Vector
expected_cameras = {
    'Level beside beam': ((2.5,-3.875,1.60),37),
    'Oblique 45 degrees': ((3.82842712474619,-3.70342712474619,1.9),40),
    'Close 30 degrees': ((2,-2.607050807568877,1),18),
}
for name, (position, lens) in expected_cameras.items():
    obj = scene.objects[name]
    assert (obj.location-Vector(position)).length < 1e-5
    assert obj.data.type == 'PERSP' and obj.data.lens == lens
    assert obj.data.shift_x == 0 and obj.data.shift_y == 0
    direction = obj.rotation_euler.to_quaternion() @ Vector((0,0,-1))
    target = (Vector((1,-.875,1))-obj.location).normalized()
    assert direction.dot(target) > .99999
assert abs(scene.objects['Pair three quarter'].data.ortho_scale-8.7) < 1e-5
from bpy_extras.object_utils import world_to_camera_view
for name in ('Close 30 degrees', 'Oblique 45 degrees'):
    camera = scene.objects[name]
    for obj in formers:
        if obj.name.startswith('Former 0'):
            for vertex in obj.data.vertices:
                projected = world_to_camera_view(scene,camera,obj.matrix_world @ vertex.co)
                assert 0 < projected.x < 1 and 0 < projected.y < 1, (name,tuple(projected))
for mat in beam.data.materials:
    emission = next(node for node in mat.node_tree.nodes if node.type == 'EMISSION')
    strength = emission.inputs['Strength']
    if strength.is_linked:
        multiplier = strength.links[0].from_node
        assert multiplier.operation == 'MULTIPLY'
        assert abs(multiplier.inputs[1].default_value-.65) < 1e-6
    elif mat.name.endswith('core'):
        assert abs(strength.default_value-.65) < 1e-6
# Render after reopening to exercise packed textures and unchanged fixed-size Blender blocks.
scene.render.filepath = str(DIRECTORY / 'reopened-pair.png')
bpy.ops.render.render(write_still=True, scene=scene.name)
image = bpy.data.images.load(str(DIRECTORY / 'reopened-pair.png'))
assert tuple(image.size) == (960, 720)
print('BEAM_HALO_SCENE_OK: 8 real model members, 4 cameras, packed textures, six-block plasma; reopen render passed')
