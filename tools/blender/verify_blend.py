"""Verify a reopened generated blend, including evaluated animation and packed textures."""
import json
from pathlib import Path
import bpy

root=Path(bpy.data.filepath).parent
report=json.loads((root/'manifest.json').read_text())
assert set(report['blocks'])=={'cable','router','pattern_provider','processing_endpoint','switch'}
assert report['blockstate_variants']==14
for name in report['scenes']:
    scene=bpy.data.scenes[name]
    assert scene.camera and len(scene.objects)>10,name
    for obj in scene.objects:
        if obj.type=='MESH' and obj.name!='Stage' and not obj.name.startswith('Stage.'):
            assert obj.data.uv_layers,obj.name
            assert all(slot.material for slot in obj.material_slots),obj.name
images=[image for image in bpy.data.images if image.type=='IMAGE' and image.source=='FILE']
assert len(images)>=20
assert all(image.packed_file for image in images)
scene=bpy.data.scenes['Federation Showcase']
flow=list({slot.material for obj in scene.objects if obj.type=='MESH'
           for slot in obj.material_slots if 'cable_flow / phase' in slot.material.name})
assert len(flow)>=4
material=flow[0]
shift=next(n for n in material.node_tree.nodes if n.label.startswith('Phase samples'))
scene.frame_set(1); first=shift.inputs[1].default_value[0]
scene.frame_set(49); second=shift.inputs[1].default_value[0]
assert abs(second-first+0.5)<1e-5,(first,second)
scene.frame_set(1)
assert len([o for o in bpy.data.scenes['Cable Connection Atlas'].objects if '/ Shell' in o.name])==64
inspection=bpy.data.scenes['Cable Beam Inspection']
assert inspection.camera.location.z==3
assert len([o for o in inspection.objects if '/ Flow' in o.name])==10
for m in flow:
    assert any(n.label=='Minecraft texture alpha discard (< 0.1)' for n in m.node_tree.nodes)
    shader=next(n for n in m.node_tree.nodes if n.type=='EMISSION')
    assert shader.inputs['Strength'].default_value==1
print('BLEND_VERIFY_OK: five scenes, 5 blocks, 14 orientations, 64 masks, packed images, evaluated flow phase, beam inspection')
