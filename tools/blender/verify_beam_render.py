"""Render real shell/flow A/B images and verify visibility at projected cable centers.

Run inside Blender against the generated blend. Uses actual scene geometry, materials
and production texture; does not replace assets or increase emission for the test.
"""
import json
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

output=Path(bpy.data.filepath).parent
scene=bpy.data.scenes['Cable Beam Inspection']
bpy.context.window.scene=scene
bpy.context.view_layer.update()
flow=[obj for obj in scene.objects if '/ Flow' in obj.name]
assert len(flow)==10
scene.cycles.seed=17
scene.frame_set(1)


def render(name):
    path=output/name
    scene.render.filepath=str(path)
    bpy.ops.render.render(write_still=True,scene=scene.name)
    image=bpy.data.images.load(str(path),check_existing=False)
    width,height=image.size
    pixels=list(image.pixels)
    bpy.data.images.remove(image)
    return width,height,pixels


original={obj:obj.hide_render for obj in flow}
try:
    for obj in flow: obj.hide_render=True
    width,height,without=render('cable-without-flow.png')
    for obj in flow: obj.hide_render=False
    _,_,with_flow=render('cable-detail.png')
    samples=[]
    for x in (-3,-2,2,3):
        uv=world_to_camera_view(scene,scene.camera,Vector((x+.5,-3.5,.5)))
        px,py=int(uv.x*width),int(uv.y*height)
        assert 4<=px<width-4 and 4<=py<height-4
        differences=[]
        for y in range(py-3,py+4):
            for xx in range(px-3,px+4):
                index=(y*width+xx)*4
                differences.append(sum(with_flow[index+c]-without[index+c] for c in (1,2))/2)
        samples.append({'block_x':x,'pixel':[px,py],'mean_cyan_increase':sum(differences)/len(differences)})
    assert all(s['mean_cyan_increase']>.025 for s in samples),samples
    scene.frame_set(49)
    _,_,later=render('cable-detail-frame49.png')
    changed=sum(abs(a-b)>.01 for a,b in zip(with_flow,later))
    assert changed>1000,changed
    report={'samples':samples,'changed_channels_at_frame49':changed,
            'emission_strength':1,'uses_original_texture':True}
    (output/'beam-visibility.json').write_text(json.dumps(report,indent=2)+'\n')
    print('BEAM_VISIBILITY_OK '+json.dumps(report))
finally:
    for obj,hidden in original.items(): obj.hide_render=hidden
    scene.frame_set(1)
