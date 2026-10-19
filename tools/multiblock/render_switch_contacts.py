"""Render the focused correction: interior contacts matching the authored edges."""
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
    output = ROOT/'build/multiblock-devices/switch-contacts-v3'
    output.mkdir(parents=True,exist_ok=True)
    paths = [ASSETS/'models/block/switch.json',ASSETS/'textures/block/switch/switch.png']
    def hashes():
        return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    before = hashes()
    mats = {'switch':material('switch')}
    scene = scene_for('Switch contact correction',10.8,1800,1120)
    text(scene,'SWITCH / CONTACT GEOMETRY',-4.9,2.95,.25)
    text(scene,'Same layout and infill. Interior contacts now reuse the original edge parts.',-4.9,2.55,.135)
    for x,style,title,subtitle in [(-2.65,'infill','V2 / FLAT TRACES','Previous shallow surface treatment'),
                                  (2.65,'edge-matched','V3 / ORIGINAL EDGE PARTS','2-unit sockets / 1-unit recess / original UVs')]:
        quads = add_device(scene,mats,'switch',(3,2,2),x,switch_style=style)
        text(scene,title,x,-1.98,.19,center=True)
        text(scene,subtitle,x,-2.3,.12,center=True)
        if style=='edge-matched':
            (output/'edge-matched-mesh.json').write_text(json.dumps(quads,separators=(',',':'))+'\n')
    text(scene,'Focused revision / Existing artwork and outer frame retained / Offline render',-4.9,-3,.115)
    render(scene,output,'switch-contact-comparison')

    scene = scene_for('Switch contact profile closeup',7.8,1800,1180)
    target = Vector((0,0,.6))
    scene.camera.location = target+Vector((4,-12,5))
    scene.camera.rotation_euler = (target-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    text(scene,'EDGE AND INTERIOR / SAME PART FAMILY',-3.55,2.16,.19)
    text(scene,'Original cyan contacts, recessed side pieces and visible socket walls',-3.55,1.83,.108)
    add_device(scene,mats,'switch',(1,1,1),-2.15,connected=False)
    add_device(scene,mats,'switch',(2,1,2),1.35,switch_style='edge-matched')
    text(scene,'ORIGINAL BLOCK',-2.15,-1.38,.16,center=True)
    text(scene,'CONNECTED / 2 x 1 x 2',1.35,-1.38,.16,center=True)
    text(scene,'Same source contact mesh and texture coordinates',-3.55,-2.13,.115)
    render(scene,output,'switch-contact-closeup')

    # A grazing view of the top directly reveals socket openings and sidewalls.
    scene = scene_for('Switch socket grazing view',5.6,1600,1100)
    target = Vector((0,0,.95))
    scene.camera.location = target+Vector((5,-9,3.7))
    scene.camera.rotation_euler = (target-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    add_device(scene,mats,'switch',(2,1,2),0,switch_style='edge-matched')
    text(scene,'RECESSED CONTACT / LOW-ANGLE DETAIL',-2.55,1.53,.13)
    text(scene,'Real sidewalls and stepped geometry; original cyan texture',-2.55,1.28,.078)
    text(scene,'2 x 1 x 2 / V3',0,-1.32,.12,center=True)
    render(scene,output,'switch-contact-grazing')

    assert before==hashes()
    report={'source_assets_unchanged':True,'source_sha256':before,
            'source_contact_elements':[15,16,17],'source_depths_model_units':[0,1,2],
            'scope':'Offline geometry; original layout retained, interior contacts corrected',
            'renders':['switch-contact-comparison.png','switch-contact-closeup.png','switch-contact-grazing.png']}
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    bpy.context.window.scene=bpy.data.scenes['Switch contact correction']
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'switch-contacts-v3.blend'))
    print('SWITCH_CONTACTS_OK '+str(output),flush=True)


if __name__=='__main__':
    main()
