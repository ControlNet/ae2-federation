"""Build asset-backed Blender scenes. Run with Blender's bundled Python, not pip bpy."""
import argparse
import json
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from assets import Assets, dependency_packs, model_quads, animation_schedule
from materials import Materials


def mesh_object(name,quads,collection,materials,origin=(0,0,0),flow=False,phase_coordinates=(0,0,0),cutout=False):
    vertices=[]; faces=[]; uvs=[]; alpha=[]; slots=[]; assigned=[]
    for q in quads:
        start=len(vertices)
        vertices.extend((p[0],-p[2],p[1]) for p in q['positions'])
        faces.append(tuple(range(start,start+4)))
        uvs.extend((u,1-v) for u,v in q['uv'])
        alpha.extend(q.get('alpha',[1]*4))
        phase=phase_coordinates[q['axis']]%4 if q.get('layer')=='FLOW' else None
        mat=materials.get(q['texture'],flow=flow,phase=phase,cutout=cutout)
        if mat not in slots: slots.append(mat)
        assigned.append(slots.index(mat))
    if not faces:
        raise ValueError(f'No geometry for {name}')
    mesh=bpy.data.meshes.new(name); mesh.from_pydata(vertices,[],faces); mesh.update()
    for mat in slots: mesh.materials.append(mat)
    uv=mesh.uv_layers.new(name='MinecraftUV')
    colors=mesh.color_attributes.new(name='FlowAlpha',type='FLOAT_COLOR',domain='CORNER')
    for polygon,index in zip(mesh.polygons,assigned):
        polygon.material_index=index
        for loop in polygon.loop_indices:
            uv.data[loop].uv=uvs[loop]
            # Minecraft sends normalized 8-bit vertex alpha to the shader.
            colors.data[loop].color=(1,1,1,round(alpha[loop]*255)/255)
    obj=bpy.data.objects.new(name,mesh); collection.objects.link(obj); obj.location=origin
    return obj


def flow_quads(data,mask):
    result=[]
    for ribbon in data['flow'][mask]:
        layer=ribbon['layer']
        result.append({'texture':'ae2federation:entity/cable_flow','axis':ribbon['axis'],'layer':layer,
            'positions':[v[:3] for v in ribbon['vertices']],
            'uv':[(v[3] if layer in ('FLOW','HUB') else 0.03125,v[4]*0.5+(0.5 if layer=='HUB' else 0)) for v in ribbon['vertices']],
            'alpha':[v[5] for v in ribbon['vertices']]})
    return result


def collection(scene,name):
    value=bpy.data.collections.new(name); value['ae2f_generated']=True
    scene.collection.children.link(value); return value


def flat_material(name,color):
    material=bpy.data.materials.new(name); material.diffuse_color=(*color,1); material.use_nodes=True
    material['ae2f_generated']=True
    shader=material.node_tree.nodes.get('Principled BSDF'); shader.inputs['Base Color'].default_value=(*color,1)
    shader.inputs['Roughness'].default_value=0.85
    return material


def label(text,position,coll,size=0.22):
    curve=bpy.data.curves.new(text,'FONT'); curve.body=text; curve.size=size; curve.align_x='CENTER'
    obj=bpy.data.objects.new(text,curve); coll.objects.link(obj); obj.location=position
    curve.materials.append(flat_material('Label / '+text,(0.55,0.75,0.82)))


def camera(scene,name,position,target,scale):
    data=bpy.data.cameras.new(name); data.type='ORTHO'; data.ortho_scale=scale
    obj=bpy.data.objects.new(name,data); scene.collection.objects.link(obj); obj.location=position
    obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
    return obj


def setup_scene(name,scale=19):
    scene=bpy.data.scenes.new(name)
    scene['ae2f_generated']=True
    scene.render.engine='CYCLES'; scene.cycles.device='CPU'; scene.cycles.samples=24
    scene.cycles.use_denoising=True; scene.cycles.transparent_max_bounces=24
    scene.render.resolution_x=1400; scene.render.resolution_y=1000; scene.render.resolution_percentage=100
    scene.render.fps=30; scene.frame_start=1; scene.frame_end=240
    scene.view_settings.view_transform='Standard'
    scene.world=bpy.data.worlds.new(name+' / World'); scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(0.08,0.11,0.16,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=0.45
    stage=collection(scene,'Stage / '+name)
    mesh=bpy.data.meshes.new('Stage'); mesh.from_pydata([(-40,-40,-0.035),(40,-40,-0.035),(40,40,-0.035),(-40,40,-0.035)],[],[(0,1,2,3)])
    obj=bpy.data.objects.new('Stage',mesh); stage.objects.link(obj); mesh.materials.append(flat_material('Stage',(0.025,0.038,0.052)))
    for title,position,power,size in [('Key',(-5,-6,12),2300,8),('Fill',(7,3,9),1700,7)]:
        data=bpy.data.lights.new(title,'AREA'); data.energy=power; data.shape='DISK'; data.size=size
        obj=bpy.data.objects.new(title,data); stage.objects.link(obj); obj.location=position
        obj.rotation_euler=(-obj.location).to_track_quat('-Z','Y').to_euler()
    scene.camera=camera(scene,name+' / Camera',(10,-18,21),(0,0,0),scale)
    return scene


def generated_item(assets,model,materials):
    texture=assets.texture_id('#layer0',model)
    image,animation=materials.load_image(texture)
    fw,fh,schedule=animation_schedule(animation,*image.size)
    w,h=image.size; pixels=list(image.pixels)
    frame=schedule[0][0]; ox=frame%(w//fw)*fw; oy=h-(frame//(w//fw)+1)*fh
    def opaque(x,y):
        return 0<=x<fw and 0<=y<fh and pixels[((oy+y)*w+ox+x)*4+3]>0.1
    quads=[]
    for x in range(fw):
        for y in range(fh):
            if not opaque(x,y): continue
            a,b=x/fw,y/fh; A,B=(x+1)/fw,(y+1)/fh
            uv=[(a,1-B),(a,1-b),(A,1-b),(A,1-B)]
            for z,reverse in [(0.46875,True),(0.53125,False)]:
                p=[(a,B,z),(a,b,z),(A,b,z),(A,B,z)]
                quads.append({'texture':texture,'positions':p[::-1] if reverse else p,'uv':uv[::-1] if reverse else uv})
            for dx,dy,p in [(-1,0,[(a,B,.46875),(a,b,.46875),(a,b,.53125),(a,B,.53125)]),
                            (1,0,[(A,B,.53125),(A,b,.53125),(A,b,.46875),(A,B,.46875)]),
                            (0,-1,[(a,b,.53125),(a,b,.46875),(A,b,.46875),(A,b,.53125)]),
                            (0,1,[(a,B,.46875),(a,B,.53125),(A,B,.53125),(A,B,.46875)])]:
                if not opaque(x+dx,y+dy):
                    quads.append({'texture':texture,'positions':p,'uv':[((a+A)/2,1-(b+B)/2)]*4})
    return quads


def build(args):
    repo=args.repo.resolve(); output=args.output.resolve(); output.mkdir(parents=True,exist_ok=True)
    data=json.loads((output/'cable.json').read_text())
    assets=Assets(repo,output/'resources',[*args.resource_pack,*dependency_packs(repo)])
    materials=Materials(assets,data['phases'])
    # Refresh only tool-owned scenes. User-created scenes and objects are not touched.
    for old in list(bpy.data.scenes):
        if old.get('ae2f_generated'):
            for obj in list(old.objects):
                if any(not linked.get('ae2f_generated') for linked in obj.users_scene):
                    continue
                data_block=obj.data
                kind=obj.type
                bpy.data.objects.remove(obj,do_unlink=True)
                if data_block and data_block.users==0:
                    table={'MESH':bpy.data.meshes,'FONT':bpy.data.curves,'CAMERA':bpy.data.cameras,'LIGHT':bpy.data.lights}
                    if kind in table: table[kind].remove(data_block)
            bpy.data.scenes.remove(old)
    for datablocks in (bpy.data.collections,bpy.data.materials,bpy.data.images):
        for block in list(datablocks):
            if block.get('ae2f_generated') and block.users==0: datablocks.remove(block)
    showcase=setup_scene('Federation Showcase',18)
    devices=collection(showcase,'Blocks / live JSON')
    parts=collection(showcase,'Parts / inherited AE2 models')
    items=collection(showcase,'Items / live JSON')
    shells=collection(showcase,'Cable Shells / production Java')
    flows=collection(showcase,'Cable Flow / production Java')
    def cable(name,connections,position,shell_collection=shells,flow_collection=flows,world=(0,0,0)):
        mask=sum(1<<b for b in range(6) if connections>>(2*b)&3)
        if shell_collection is not None: mesh_object(name+' / Shell',data['shells'][str(connections)],shell_collection,materials,position)
        mesh_object(name+' / Flow',flow_quads(data,mask),flow_collection,materials,position,True,world)
    states={}
    for file in sorted((assets.root/'ae2federation/blockstates').glob('*.json')):
        raw=assets.json(f'assets/ae2federation/blockstates/{file.name}')
        if 'multipart' in raw: raise ValueError(f'Multipart blockstate requires explicit support: {file}')
        states[file.stem]=raw['variants']
    for i,(name,variants) in enumerate(states.items()):
        origin=((i-(len(states)-1)/2)*2.5,3,0)
        if name=='cable': cable('Cable / isolated',0,origin)
        else:
            variant=variants.get('facing=south',next(iter(variants.values())))
            if isinstance(variant,list): variant=variant[0]
            model=assets.model(variant['model'])
            mesh_object(name,model_quads(assets,model,variant),devices,materials,origin,cutout=model.get('render_type')=='cutout')
        label(name.replace('_',' ').upper(),(origin[0]+.5,1.5,.01),devices,0.18)
    for i,name in enumerate(['bridge','p2p_tunnel_federation']):
        model=assets.model('ae2federation:part/'+name)
        mesh_object(name,model_quads(assets,model),parts,materials,(-5+i*2.1,0,0),cutout=True)
        label(name.replace('_',' ').upper(),(-4.5+i*2.1,-.95,.01),parts,.16)
    generated=[]
    # Resolve every item model as well, including inherited AE2 resources.
    for file in sorted((assets.root/'ae2federation/models/item').glob('*.json')):
        model=assets.model('ae2federation:item/'+file.stem)
        if model.get('generated'): generated.append((file.stem,model))
        elif file.stem!='cable':
            for quad in model_quads(assets,model): assets.texture(quad['texture'])
    for i,(name,model) in enumerate(generated):
        pos=(-.8+i*1.6,0,0)
        mesh_object(name,generated_item(assets,model,materials),items,materials,pos,cutout=True)
        label(name.replace('_',' ').upper(),(pos[0]+.5,-.95,.01),items,.13)
    # Connected main run. Coordinates drive production phase, independent of presentation offsets.
    for x in range(-4,5):
        c=15
        if x==-4: c=7  # Cable east, dense machine west.
        if x==4: c=13  # Dense machine east, cable west.
        if x==0: c |= 3<<(4*2)  # South branch.
        cable(f'Network / x={x}',c,(x,-3,0),world=(x,0,0))
    for x,name in [(-5,'router'),(5,'pattern_provider')]:
        variant=states[name]['facing=west'] if x==5 else {}
        mesh_object('Network / '+name,model_quads(assets,assets.model('ae2federation:block/'+name),variant),devices,materials,(x,-3,0),cutout=True)
    cable('Network / south branch',3<<(5*2), (0,-4,0),world=(0,0,1))
    label('FEDERATION CABLE  /  CONTINUOUS RUN + DENSE MACHINE PORTS',(0,-5,.01),devices,.25)
    label('AE2 FEDERATION   /   ASSET GALLERY',(0,5.2,.01),devices,.4)
    detail=camera(showcase,'Cable Detail',(5,-17,3),(.5,-3.5,.5),12)
    # A high studio angle projects the center beam behind the shell's opaque upper rail.
    # Keep a low-angle, uncluttered inspection scene with the same meshes and emission.
    inspection=setup_scene('Cable Beam Inspection',12)
    inspection.camera.location=detail.location
    inspection.camera.rotation_euler=detail.rotation_euler
    inspection.render.resolution_x=1500; inspection.render.resolution_y=650
    inspection.cycles.samples=64
    inspection.world.node_tree.nodes['Background'].inputs[1].default_value=.2
    inspection_network=collection(inspection,'Inspection / actual network geometry')
    for obj in list(showcase.objects):
        if obj.name.startswith('Network /'):
            copied=obj.copy(); inspection_network.objects.link(copied)
    for obj in inspection.objects:
        if obj.type=='LIGHT': obj.data.energy *= .55
    lab=setup_scene('Cable Connection Atlas',24)
    lab_shell=collection(lab,'All 64 cable masks / shells'); lab_flow=collection(lab,'All 64 cable masks / flow')
    for mask in range(64):
        c=sum(3<<(bit*2) for bit in range(6) if mask&(1<<bit))
        pos=((mask%8-3.5)*2,(mask//8-3.5)*2,0)
        cable(f'Mask {mask:02d}',c,pos,lab_shell,lab_flow)
        label(f'{mask:02d} / {mask:06b}',(pos[0]+.5,pos[1]-.6,.01),lab_shell,.15)
    label('CABLE CONNECTION ATLAS / E W U D S N bits 0..5',(0,9,.01),lab_shell,.28)
    study=setup_scene('Cable Flow Study',15)
    study_flow=collection(study,'Flow / shell hidden for inspection')
    for i,mask in enumerate([0,3,5,17,19,51,63]):
        c=sum(3<<(bit*2) for bit in range(6) if mask&(1<<bit))
        pos=((i%4-1.5)*3,(i//4-.5)*3,0.7)
        cable(f'Flow mask {mask}',c,pos,None,study_flow)
        label(f'FLOW / MASK {mask}',(pos[0]+.5,pos[1]-.7,.01),study_flow,.23)
    state_scene=setup_scene('Block Orientation Atlas',21)
    state_collection=collection(state_scene,'Blockstate variants / live JSON')
    index=0
    for name,variants in states.items():
        if name=='cable': continue
        for key,variant in variants.items():
            if isinstance(variant,list): variant=variant[0]
            pos=((index%6-2.5)*2.6,(index//6-1)*3,0)
            model=assets.model(variant['model'])
            mesh_object(name+' / '+key,model_quads(assets,model,variant),state_collection,materials,pos,cutout=True)
            label(name.replace('_',' ')+' / '+(key or 'default'),(pos[0]+.5,pos[1]-.7,.01),state_collection,.16)
            index+=1
    report={'blocks':list(states),'parts':['bridge','p2p_tunnel_federation'],'generated_items':[n for n,_ in generated],
            'blockstate_variants':index,'cable_connection_masks':64,'exported_shells':len(data['shells']),
            'source_sha256':data['source_sha256'],'asset_sha256':assets.used,
            'omitted_occluded_faces':assets.omitted_internal_faces,
            'scenes':[s.name for s in (showcase,inspection,lab,study,state_scene)],
            'limitations':['Blender transparency, lighting and emission are not Minecraft shader validation.',
                           'Weighted blockstate lists select their first model; multipart and uvlock fail explicitly.',
                           'Generated item silhouettes use the first animation frame.',
                           'Vanilla geometry-free root display transforms are omitted for world presentation.']}
    (output/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    for scene in (showcase,inspection,lab,study,state_scene): scene.frame_set(1)
    for scene in (showcase,inspection,lab,study,state_scene):
        scene['ae2f_repo']=str(repo)
        scene['ae2f_output']=str(output)
        scene['ae2f_resource_packs']=json.dumps([str(p.resolve()) for p in args.resource_pack])
    if bpy.context.window: bpy.context.window.scene=showcase
    # Open the saved file in a useful camera view, with textures visible immediately.
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=='VIEW_3D':
                area.spaces.active.region_3d.view_perspective='CAMERA'
                area.spaces.active.shading.type='MATERIAL'
    reload_text=bpy.data.texts.get('Reload Federation Assets.py') or bpy.data.texts.new('Reload Federation Assets.py')
    reload_text.clear()
    reload_text.write('import sys, runpy, subprocess\nfrom pathlib import Path\n'
        +f'root = Path({str(repo)!r})\nout = Path({str(output)!r})\n'
        +'subprocess.run(["python3", str(root / "tools/blender/export_cable.py"), "--repo", str(root), "--output", str(out / "cable.json")], check=True)\n'
        +'sys.argv = ["blender", "--", "--repo", str(root), "--output", str(out), "--replace"]\n'
        +'runpy.run_path(str(root / "tools/blender/build_scene.py"), run_name="__main__")\n')
    bpy.ops.wm.save_as_mainfile(filepath=str(output/'federation.blend'))
    if args.render:
        for scene,cam,filename in [(showcase,showcase.camera,'showcase.png'),(inspection,inspection.camera,'cable-detail.png'),(study,study.camera,'flow-study.png')]:
            original=scene.camera; scene.camera=cam
            scene.render.filepath=str(output/filename)
            bpy.ops.render.render(write_still=True,scene=scene.name)
            scene.camera=original
    print('FEDERATION_IMPORT_OK '+json.dumps({k:report[k] for k in ['blocks','blockstate_variants','cable_connection_masks','scenes']}))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo',type=Path,default=HERE.parents[1])
    parser.add_argument('--output',type=Path,default=HERE/'build')
    parser.add_argument('--resource-pack',type=Path,action='append',default=[])
    parser.add_argument('--render',action='store_true')
    parser.add_argument('--replace',action='store_true',help='Replace the generated .blend at the output path')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    if (args.output/'federation.blend').exists() and not args.replace:
        parser.error('Generated .blend exists. Choose another --output or explicitly pass --replace.')
    build(args)
