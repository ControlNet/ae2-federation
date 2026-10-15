"""Compare the original Switch surface with two geometric infill treatments."""
import hashlib
import json
from pathlib import Path
import sys

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from geometry import ASSETS, ROOT
from render import add_device, material, render, scene_for, text


def main():
    output = ROOT / 'build/multiblock-devices/switch-fill-v2'
    output.mkdir(parents=True, exist_ok=True)
    paths = [ASSETS/'models/block/switch.json', ASSETS/'textures/block/switch/switch.png']
    def hashes():
        return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    before = hashes()
    mats = {'switch':material('switch')}
    variants = [('plain','PREVIOUS','Broad pale gaps'),
                ('links','A / CONNECTING TRACES','Cyan links between purple modules'),
                ('infill','B / FILLED PANELS','Dark infill + cyan links + fine grooves')]
    report = {'source_sha256':before, 'status':'Offline geometric surface variants', 'variants':{}}

    scene = scene_for('Switch surface density comparison', 15.8, 2200, 1080)
    text(scene, 'SWITCH  /  FILLING THE GAPS', -7.25, 3.25, .27)
    text(scene, 'Same 3 x 2 x 2 body, frame and purple modules. Original texture palette throughout.',
         -7.25, 2.8, .16)
    for x, (style, title, subtitle) in zip([-5.15,0,5.15], variants):
        quads = add_device(scene, mats, 'switch', (3,2,2), x, switch_style=style)
        text(scene, title, x, -2.1, .22, center=True)
        text(scene, subtitle, x, -2.47, .135, center=True)
        filename = style+'-mesh.json'
        (output/filename).write_text(json.dumps(quads, separators=(',',':'))+'\n')
        report['variants'][style] = {'quads':len(quads), 'mesh':filename}
    text(scene, 'V2 surface study  /  Geometry and UV implementation  /  Offline render', -7.25, -3.4, .13)
    render(scene, output, 'switch-fill-comparison')

    scene = scene_for('Switch surface density front', 12.7, 2200, 1040, front=True)
    text(scene, 'SWITCH  /  FRONT SURFACE DETAIL', -5.9, 2.5, .22)
    text(scene, '3 x 3 x 1 blocks  /  Compare the spaces between the purple centers', -5.9, 2.14, .13)
    for x, (style, title, subtitle) in zip([-4.15,0,4.15], variants):
        add_device(scene, mats, 'switch', (3,3,1), x, vertical=-.5, switch_style=style)
        text(scene, title, x, -1.96, .19, center=True)
        text(scene, subtitle, x, -2.28, .108, center=True)
    render(scene, output, 'switch-fill-front')

    scene = scene_for('Switch filled panel thin form', 9.8, 1600, 1000)
    text(scene, 'B / FILLED PANELS  /  SHAPE CHECK', -4.5, 2.55, .23)
    text(scene, 'Connected surfaces on full and thin bodies', -4.5, 2.17, .14)
    add_device(scene, mats, 'switch', (3,2,2), -2.35, switch_style='infill')
    add_device(scene, mats, 'switch', (1,1,4), 2.35, switch_style='infill')
    text(scene, '3 x 2 x 2', -2.35, -2.12, .19, center=True)
    text(scene, '1 x 1 x 4', 2.35, -2.12, .19, center=True)
    render(scene, output, 'switch-filled-shapes')

    assert before == hashes(), 'Source Switch artwork changed'
    report['source_assets_unchanged'] = True
    report['renders'] = ['switch-fill-comparison.png','switch-fill-front.png','switch-filled-shapes.png']
    (output/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    bpy.context.window.scene = bpy.data.scenes['Switch surface density comparison']
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'switch-fill-v2.blend'))
    print('SWITCH_FILL_OK '+str(output), flush=True)


if __name__ == '__main__':
    main()
