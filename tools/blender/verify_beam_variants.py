"""Verify the saved candidate study without changing shell geometry or materials."""
import hashlib
import json
from pathlib import Path

import bpy

output=Path(bpy.data.filepath).parent
report=json.loads((output/'comparison-report.json').read_text())
assert report['source_assets_unchanged'] is True
assert report['emission_strength']==1
original=bpy.data.scenes['Cable Beam Inspection']
assert len(report['profiles'])==4
for key,profile in report['profiles'].items():
    scene=bpy.data.scenes[profile['title']]
    assert scene.camera is original.camera
    assert scene.world is original.world
    for obj in original.objects:
        if '/ Flow' not in obj.name:
            assert scene.objects.get(obj.name) is obj,(key,obj.name)
    flow=[o for o in scene.objects if '/ Flow' in o.name]
    assert len(flow)==10
    original_flow=[o for o in original.objects if '/ Flow' in o.name]
    def coordinates(obj):
        return (tuple(obj.location),tuple(tuple(v.co) for v in obj.data.vertices),
                tuple(tuple(uv.uv) for uv in obj.data.uv_layers.active.data))
    assert sorted(map(coordinates,flow))==sorted(map(coordinates,original_flow))
    for obj in flow:
        for slot in obj.material_slots:
            nodes=slot.material.node_tree.nodes
            shader=next(n for n in nodes if n.type=='EMISSION')
            assert shader.inputs['Strength'].default_value==1
            assert any(n.label=='Minecraft texture alpha discard (< 0.1)' for n in nodes)
    if key!='a_baseline':
        texture=output/key/'assets/ae2federation/textures/entity/cable_flow.png'
        assert hashlib.sha256(texture.read_bytes()).hexdigest()==profile['texture_sha256']
        image=bpy.data.images.load(str(texture),check_existing=False)
        assert tuple(image.size)==(64,128)
        assert json.loads((output/key/'pack.mcmeta').read_text())['pack']['pack_format']==34
    if report['animation_frames']:
        frames=list((output/key/'frames').glob('*.png'))
        assert len(frames)==32
        assert (output/key/'frames/000.png').read_bytes()!=(output/key/'frames/016.png').read_bytes()
print('BEAM_VARIANTS_VERIFY_OK: same shells, geometry, UVs, camera, world, emission; 3 real texture packs; animated frames differ')
