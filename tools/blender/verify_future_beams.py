"""Check the saved eight-concept study, shell identity and evaluated mesh animation."""
import json
from pathlib import Path
import sys

import bpy

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from beam_variants import geometry_digest

output=Path(bpy.data.filepath).parent
report=json.loads((output/'future-report.json').read_text())
expected={'optical':4,'load':6,'m-load':6}.get(report.get('study'),8)
assert len(report['profiles'])==expected
assert report['source_assets_unchanged']
original=bpy.data.scenes['Cable Beam Inspection']
for key,profile in report['profiles'].items():
    scene=bpy.data.scenes[profile['title']]
    bpy.context.window.scene=scene
    for name,digest in report['shell_sha256'].items():
        assert scene.objects[name] is original.objects[name]
        assert geometry_digest(scene.objects[name])==digest
    obj=next(o for o in scene.objects if o.get('prototype_port_required'))
    assert len(obj.data.vertices)==profile['vertices']
    assert len(obj.data.polygons)==profile['polygons']
    assert len(obj.data.shape_keys.key_blocks)==33
    def positions(frame):
        scene.frame_set(frame); bpy.context.view_layer.update()
        evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
        return [tuple(v.co) for v in evaluated.data.vertices]
    first=positions(1); later=positions(16)
    assert any(abs(a-b)>1e-5 for p,q in zip(first,later) for a,b in zip(p,q)),key
    for material in obj.data.materials:
        shader=next(n for n in material.node_tree.nodes if n.type=='EMISSION')
        assert abs(shader.inputs['Strength'].default_value-profile.get('emission_frame16',1))<1e-5
    mesh=json.loads((output/(key+'-mesh.json')).read_text())
    assert len(mesh['positions'])==profile['vertices']
    if report['animation_frames']:
        frames=sorted((output/key/'frames').glob('*.png'))
        assert len(frames)==32
        assert frames[0].read_bytes()!=frames[7].read_bytes()
print(f'FUTURE_VERIFY_OK: {expected} distinct mesh concepts; unchanged shared shells; saved shape-key animation; real render frames')
