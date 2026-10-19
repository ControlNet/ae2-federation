"""Render original-white Switch backing with the existing recessed contacts."""
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
    output = ROOT/'build/multiblock-devices/switch-white-v4'
    output.mkdir(parents=True,exist_ok=True)
    paths = [ASSETS/'models/block/switch.json',ASSETS/'textures/block/switch/switch.png']
    def hashes():
        return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    before = hashes()
    mats = {'switch':material('switch')}
    scene = scene_for('Switch white backing correction',10.8,1800,1120)
    text(scene,'SWITCH / ORIGINAL WHITE BACKING',-4.9,2.95,.25)
    text(scene,'Same geometry and lighting. Restore the original pale body texture between modules.',-4.9,2.55,.135)
    for x,style,title,subtitle in [(-2.65,'edge-matched','V3 / DARK BACKING','Previous backing / recessed contacts'),
                                  (2.65,'white-backed','V4 / ORIGINAL WHITE','Source body white / same recessed contacts')]:
        quads = add_device(scene,mats,'switch',(3,2,2),x,switch_style=style)
        text(scene,title,x,-1.98,.19,center=True)
        text(scene,subtitle,x,-2.3,.12,center=True)
        if style=='white-backed':
            (output/'white-backed-mesh.json').write_text(json.dumps(quads,separators=(',',':'))+'\n')
    text(scene,'Focused revision / Existing artwork and outer frame retained / Offline render',-4.9,-3,.115)
    render(scene,output,'switch-white-comparison')

    scene = scene_for('Switch contact profile closeup',7.8,1800,1180)
    target = Vector((0,0,.6))
    scene.camera.location = target+Vector((4,-12,5))
    scene.camera.rotation_euler = (target-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    text(scene,'ORIGINAL BLOCK / WHITE MULTIBLOCK',-3.55,2.16,.19)
    text(scene,'Original white body palette, purple modules and recessed cyan contacts',-3.55,1.83,.108)
    add_device(scene,mats,'switch',(1,1,1),-2.15,connected=False)
    add_device(scene,mats,'switch',(2,1,2),1.35,switch_style='white-backed')
    text(scene,'ORIGINAL BLOCK',-2.15,-1.38,.16,center=True)
    text(scene,'CONNECTED / 2 x 1 x 2',1.35,-1.38,.16,center=True)
    text(scene,'Backing uses original body pixel (2, 2): RGB 242 / 242 / 242',-3.55,-2.13,.115)
    render(scene,output,'switch-white-closeup')

    # A grazing view of the top directly reveals socket openings and sidewalls.
    scene = scene_for('Switch socket grazing view',5.6,1600,1100)
    target = Vector((0,0,.95))
    scene.camera.location = target+Vector((5,-9,3.7))
    scene.camera.rotation_euler = (target-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    add_device(scene,mats,'switch',(2,1,2),0,switch_style='white-backed')
    text(scene,'RECESSED CONTACT / LOW-ANGLE DETAIL',-2.55,1.53,.13)
    text(scene,'Real sidewalls and stepped geometry; original cyan texture',-2.55,1.28,.078)
    text(scene,'2 x 1 x 2 / V4',0,-1.32,.12,center=True)
    render(scene,output,'switch-white-grazing')

    assert before==hashes()
    report={'source_assets_unchanged':True,'source_sha256':before,
            'backing_source_pixel':[2,2], 'source_contact_elements':[15,16,17],'source_depths_model_units':[0,1,2],
            'scope':'Offline geometry; original white backing restored; contact geometry retained',
            'renders':['switch-white-comparison.png','switch-white-closeup.png','switch-white-grazing.png']}
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    bpy.context.window.scene=bpy.data.scenes['Switch white backing correction']
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'switch-white-v4.blend'))
    print('SWITCH_WHITE_OK '+str(output),flush=True)


if __name__=='__main__':
    main()
