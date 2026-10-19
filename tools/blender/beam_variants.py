"""Render actual beam texture candidates with identical production shells and geometry.

Run in Blender against build/federation.blend. Writes real Minecraft-compatible
texture packs and editable comparison scenes; does not modify repository assets.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys

import bpy

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from assets import Assets, dependency_packs
from materials import Materials
from build_scene import setup_scene, collection, mesh_object, flow_quads

PROFILES={
    'a_baseline': {'title':'A / CURRENT', 'description':'Current production texture, unchanged.'},
    'b_focus': {'title':'B / FOCUSED CORE', 'description':'One pale cyan core, restrained halo, a bright head and short tail.'},
    'c_dual': {'title':'C / TWIN CHANNELS', 'description':'Two thin cyan channels with staggered pulses moving at the production speed.'},
    'd_packets': {'title':'D / SPARSE PULSES', 'description':'A quiet continuous guide line with short, separated bright packets.'},
}


def smooth(a,b,x):
    t=max(0,min(1,(x-a)/(b-a)))
    return t*t*(3-2*t)


def taper(distance,inner,outer):
    return 1-smooth(inner,outer,distance)


def pulse(u,head,tail=.13,front=.022):
    """Positive-axis movement: a short leading edge and longer trailing tail."""
    delta=(u-head+.5)%1-.5
    if delta<0: return math.exp(delta/max(tail,.001)) if delta>-tail*3 else 0
    return 1-smooth(0,front,delta)


def pixel(profile,u,v,hub=False):
    if profile=='b_focus':
        distance=abs(v-.5)
        core=taper(distance,.065,.12)
        halo=taper(distance,.13,.34)*.15
        moving=0 if hub else pulse(u,.68,.055,.018)
        alpha=max(halo,core*(.69+.24*moving))
        color=(.43+.44*moving,.89+.1*moving,1)
        if core<.1: color=(.02,.65,.8)
    elif profile=='c_dual':
        channel=0 if v<.5 else 1
        center=.33 if channel==0 else .67
        distance=abs(v-center)
        core=taper(distance,.028,.065)
        halo=taper(distance,.07,.15)*.13
        moving=0 if hub else pulse(u,.3+.5*channel,.04,.014)
        alpha=max(halo,core*(.57+.34*moving))
        color=(.22+.63*moving,.87+.12*moving,1)
        if core<.1: color=(.02,.58,.75)
    elif profile=='d_packets':
        distance=abs(v-.5)
        guide=taper(distance,.03,.055)*.26
        moving=0 if hub else pulse(u,.65,.019,.01)
        packet=taper(distance,.08,.125)*moving
        alpha=max(guide,packet*.94,taper(distance,.14,.25)*moving*.15)
        color=(.12+.74*moving,.7+.29*moving,.91+.09*moving)
        if hub: alpha=taper(distance,.055,.14)*.6
    else:
        raise ValueError(profile)
    if hub:
        # Keep the existing shared hub tile's compact, non-directional behavior.
        radial=math.hypot(u-.5,v-.5)
        alpha=min(.72,taper(radial,.12,.48)*(.48 if profile=='c_dual' else .6))
        color=(.28,.84,.96)
    # Retain the game's pre-vertex-alpha discard behavior, with transparent margins.
    if alpha<.1: return (0,0,0,0)
    return (*color,min(1,alpha))


def texture_pack(output,profile):
    directory=output/profile
    path=directory/'assets/ae2federation/textures/entity/cable_flow.png'
    path.parent.mkdir(parents=True,exist_ok=True)
    size=64
    image=bpy.data.images.new(profile+' / cable_flow',width=size,height=size*2,alpha=True,float_buffer=False)
    image.colorspace_settings.name='sRGB'
    pixels=[]
    for y in range(size*2):
        top_based=size*2-1-y
        hub=top_based>=size
        v=(top_based%size+.5)/size
        for x in range(size): pixels.extend(pixel(profile,(x+.5)/size,v,hub))
    image.pixels.foreach_set(pixels)
    image.filepath_raw=str(path); image.file_format='PNG'; image.save(); image.pack()
    (directory/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':34,'description':'AE2 Federation beam study: '+PROFILES[profile]['description']}},indent=2)+'\n')
    return image,path


def clone_scene(template,name,image=None):
    """Share all original shells, lights and cameras. Duplicate only flow objects/materials."""
    scene=bpy.data.scenes.new(name)
    scene['ae2f_beam_variant']=True
    scene.render.engine='CYCLES'; scene.cycles.device='CPU'; scene.cycles.samples=48
    scene.cycles.use_denoising=True; scene.cycles.transparent_max_bounces=24; scene.cycles.seed=23
    scene.world=template.world
    scene.render.resolution_x=1500; scene.render.resolution_y=440; scene.render.resolution_percentage=100
    scene.render.fps=30; scene.frame_start=1; scene.frame_end=96
    scene.view_settings.view_transform=template.view_settings.view_transform
    scene.view_settings.look=template.view_settings.look
    scene.camera=template.camera
    materials={}
    for obj in template.objects:
        if '/ Flow' not in obj.name or image is None:
            scene.collection.objects.link(obj)
            continue
        copy=obj.copy(); copy.data=obj.data.copy()
        for slot in copy.material_slots:
            original=slot.material
            if original not in materials:
                mat=original.copy(); mat.name=name+' / '+original.name
                for node in mat.node_tree.nodes:
                    if node.type=='TEX_IMAGE': node.image=image
                materials[original]=mat
            slot.material=materials[original]
        scene.collection.objects.link(copy)
    return scene


def geometry_digest(obj):
    mesh=obj.data
    value={'vertices':[tuple(v.co) for v in mesh.vertices],
           'faces':[tuple(p.vertices) for p in mesh.polygons],
           'uv':[tuple(v.uv) for v in mesh.uv_layers.active.data] if mesh.uv_layers.active else [],
           'materials':[m.name for m in mesh.materials]}
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()


def main(args):
    output=args.output.resolve(); output.mkdir(parents=True,exist_ok=True)
    template=bpy.data.scenes['Cable Beam Inspection']
    repo=Path(template['ae2f_repo'])
    base_assets=repo/'common/src/main/resources/assets/ae2federation'
    before={str(p.relative_to(base_assets)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in base_assets.rglob('*') if p.is_file()}
    shells={o.name:geometry_digest(o) for o in template.objects if o.type=='MESH' and '/ Flow' not in o.name}
    data=json.loads((HERE/'build/cable.json').read_text())
    assets=Assets(repo,output/'resources',dependency_packs(repo))
    mats=Materials(assets,data['phases'])
    junction=setup_scene('Junction Comparison Source',9)
    junction['ae2f_beam_variant']=True
    junction.camera.location=(4,-12,4)
    from mathutils import Vector
    junction.camera.rotation_euler=(Vector((.5,-.5,.5))-junction.camera.location).to_track_quat('-Z','Y').to_euler()
    junction.world.node_tree.nodes['Background'].inputs[1].default_value=.2
    for obj in junction.objects:
        if obj.type=='LIGHT': obj.data.energy *= .55
    shell_coll=collection(junction,'Junction comparison / fixed shells')
    flow_coll=collection(junction,'Junction comparison / flow')
    for i,mask in enumerate((3,17,19,63)):
        connections=sum(3<<(bit*2) for bit in range(6) if mask&(1<<bit))
        pos=((i-1.5)*2.1,0,0)
        mesh_object(f'Mask {mask} / Shell',data['shells'][str(connections)],shell_coll,mats,pos)
        mesh_object(f'Mask {mask} / Flow',flow_quads(data,mask),flow_coll,mats,pos,flow=True)
    scenes=[]; junctions=[]; report={'frame':25,'profiles':PROFILES,'shells_sha256':shells,'source_assets_unchanged':False}
    for profile in PROFILES:
        image=None
        if profile!='a_baseline':
            image,path=texture_pack(output,profile)
            PROFILES[profile]['texture_sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
        scene=clone_scene(template,PROFILES[profile]['title'],image)
        other=clone_scene(junction,PROFILES[profile]['title']+' / Junctions',image)
        scenes.append(scene); junctions.append(other)
        # Shells are the exact original objects/data/materials, not re-created approximations.
        for name,digest in shells.items():
            assert scene.objects[name] is template.objects[name]
            assert geometry_digest(scene.objects[name])==digest
        for obj in scene.objects:
            if '/ Flow' not in obj.name: continue
            for slot in obj.material_slots:
                shader=next(n for n in slot.material.node_tree.nodes if n.type=='EMISSION')
                assert shader.inputs['Strength'].default_value==1
        for current,filename in [(scene,profile+'.png'),(other,profile+'-junctions.png')]:
            bpy.context.window.scene=current; current.frame_set(25)
            current.render.filepath=str(output/filename)
            bpy.ops.render.render(write_still=True,scene=current.name)
    bpy.context.window.scene=scenes[1]
    for scene in scenes+junctions: scene.frame_set(25)
    blend=output/'beam-comparison.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    if args.animate:
        for profile,scene in zip(PROFILES,scenes):
            directory=output/profile/'frames'; directory.mkdir(parents=True,exist_ok=True)
            scene.render.resolution_x=1000; scene.render.resolution_y=294; scene.cycles.samples=24
            bpy.context.window.scene=scene
            for index,frame in enumerate(range(1,97,3)):
                scene.frame_set(frame)
                scene.render.filepath=str(directory/f'{index:03d}.png')
                bpy.ops.render.render(write_still=True,scene=scene.name)
    after={str(p.relative_to(base_assets)):hashlib.sha256(p.read_bytes()).hexdigest()
           for p in base_assets.rglob('*') if p.is_file()}
    assert before==after,'Production assets changed during the study'
    report['source_assets_unchanged']=True
    report['animation_frames']=32 if args.animate else 0
    report['emission_strength']=1
    report['geometry']='Original exported production quads for every candidate'
    (output/'comparison-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('BEAM_VARIANTS_OK: 4 profiles; shared original shells; production geometry and speed; original assets unchanged',flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=HERE/'build/beam-variants')
    parser.add_argument('--animate',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    main(args)
