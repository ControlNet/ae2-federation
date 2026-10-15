"""Show raised Router links extending the artist's original core tabs."""
import hashlib
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

sys.path.insert(0,str(Path(__file__).resolve().parent))
from geometry import ASSETS, ROOT
from render import add_device, material, render, scene_for, text


def main():
    output=ROOT/'build/multiblock-devices/router-raised-v2'
    output.mkdir(parents=True,exist_ok=True)
    paths=[ASSETS/'models/block/router.json',ASSETS/'textures/block/router/router.png']
    def hashes():
        return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    before=hashes()
    mats={'router':material('router')}
    scene=scene_for('Router raised connection comparison',10.8,1800,1120)
    text(scene,'ROUTER / RAISED BLUE CONNECTIONS',-4.9,2.95,.23)
    text(scene,'Original core tabs extended into solid bridges, one model unit above the backing.',-4.9,2.55,.13)
    for x,style,title,subtitle in [(-2.65,'plain','PREVIOUS / RECESSED BACKING','Blue pattern remains on the inset surface'),
                                  (2.65,'raised','REVISED / RAISED LINKS','Continuous top at core height / real sidewalls')]:
        quads=add_device(scene,mats,'router',(3,2,2),x,router_style=style)
        text(scene,title,x,-1.98,.18,center=True)
        text(scene,subtitle,x,-2.3,.115,center=True)
        if style=='raised':
            (output/'raised-router-mesh.json').write_text(json.dumps(quads,separators=(',',':'))+'\n')
    text(scene,'Same core, frame and original texture / Focused geometry change / Offline render',-4.9,-3,.115)
    render(scene,output,'router-raised-comparison')

    scene=scene_for('Router link height closeup',8.8,1800,1120)
    target=Vector((0,0,.6))
    scene.camera.location=target+Vector((4,-12,4.2))
    scene.camera.rotation_euler=(target-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    text(scene,'LOW-ANGLE DETAIL / CONTINUOUS RAISED LINKS',-4,2.3,.19)
    text(scene,'Compare the sidewalls and shadows along the blue connections.',-4,1.96,.12)
    for x,style,title in [(-2.15,'plain','PREVIOUS'),(2.15,'raised','RAISED / 1 MODEL UNIT')]:
        add_device(scene,mats,'router',(2,1,2),x,router_style=style)
        text(scene,title,x,-1.66,.17,center=True)
    text(scene,'2 x 1 x 2 bodies / Same lighting and view / Source artwork unchanged',-4,-2.35,.1)
    render(scene,output,'router-raised-closeup')

    assert before==hashes()
    report={'source_assets_unchanged':True,'source_sha256':before,
            'link_height_above_backing_model_units':1,
            'top_surface':'Coplanar with the original core top',
            'scope':'Offline geometry; Switch variants, Router core and frame preserved',
            'renders':['router-raised-comparison.png','router-raised-closeup.png']}
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    bpy.context.window.scene=bpy.data.scenes['Router raised connection comparison']
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'router-raised-v2.blend'))
    print('ROUTER_RAISED_OK '+str(output),flush=True)


if __name__=='__main__':
    main()
