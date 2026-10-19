"""Create animated geometric or optical beam studies inside the original shell."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

import bpy

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
import future_beams
from beam_variants import geometry_digest
from build_scene import camera


def linear(value):
    return value/12.92 if value<=.04045 else ((value+.055)/1.055)**2.4


def material(index, palette):
    r,g,b,alpha=palette[index]
    mat=bpy.data.materials.new('Future beam palette '+str(index)); mat.use_nodes=True
    nodes=mat.node_tree.nodes; nodes.clear(); links=mat.node_tree.links
    emission=nodes.new('ShaderNodeEmission'); emission.inputs['Color'].default_value=(linear(r),linear(g),linear(b),1)
    emission.inputs['Strength'].default_value=1
    transparent=nodes.new('ShaderNodeBsdfTransparent')
    mix=nodes.new('ShaderNodeMixShader'); mix.inputs[0].default_value=alpha
    links.new(transparent.outputs[0],mix.inputs[1]); links.new(emission.outputs[0],mix.inputs[2])
    out=nodes.new('ShaderNodeOutputMaterial'); links.new(mix.outputs[0],out.inputs['Surface'])
    mat.surface_render_method='DITHERED'
    return mat


def mesh_for(profile,configs,cycle,generate):
    vertices=[]; faces=[]; materials=[]
    for mask,world,origin in configs:
        generated=generate(profile,mask,world,origin,cycle)
        offset=len(vertices); vertices.extend(generated.vertices)
        faces.extend(tuple(i+offset for i in face) for face in generated.faces)
        materials.extend(generated.materials)
    return vertices,faces,materials


def main(args):
    study=future_beams
    if args.study=='optical':
        import optical_beams
        study=optical_beams
    elif args.study=='load':
        import beam_load
        study=beam_load
    elif args.study=='m-load':
        import beam_load_m
        study=beam_load_m
    profiles,palette_data=study.PROFILES,study.PALETTE
    output=(args.output or HERE/'build'/f'{args.study}-beams').resolve()
    output.mkdir(parents=True,exist_ok=True)
    template=bpy.data.scenes['Cable Beam Inspection']
    repo=Path(template['ae2f_repo'])
    asset_root=repo/'common/src/main/resources/assets/ae2federation'
    def asset_hashes():
        return {str(p.relative_to(asset_root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in asset_root.rglob('*') if p.is_file()}
    before=asset_hashes()
    shell={o.name:geometry_digest(o) for o in template.objects if o.type=='MESH' and '/ Flow' not in o.name}
    configs=[]
    for obj in template.objects:
        if '/ Flow' not in obj.name: continue
        match=re.search(r'Network / x=(-?\d+)',obj.name)
        if match:
            x=int(match.group(1)); configs.append((19 if x==0 else 3,(x,0,0),tuple(obj.location)))
        elif 'south branch' in obj.name: configs.append((32,(0,0,1),tuple(obj.location)))
        else: raise ValueError('Unknown production flow placement: '+obj.name)
    assert len(configs)==10
    scenes=[]; close_camera=None
    report={'profiles':{},'study':args.study,'shell_sha256':shell,'source_sha256':hashlib.sha256(Path(study.__file__).read_bytes()).hexdigest(),
            'source_assets_unchanged':False,'port_status':'Blender geometric prototypes; new Java vertex generation is required.',
            'frame':25,'animation_frames':32 if args.animate else 0,'palette':palette_data,'configs':configs}
    for key,(title,description) in profiles.items():
        colors=study.palette_for(key) if hasattr(study,'palette_for') else palette_data
        palette=[material(i,colors) for i in range(len(colors))]
        if hasattr(study,'emission_for'):
            for mat in palette:
                shader=next(n for n in mat.node_tree.nodes if n.type=='EMISSION')
                for frame in range(1,98,3):
                    shader.inputs['Strength'].default_value=study.emission_for(key,(frame-1)/96)
                    shader.inputs['Strength'].keyframe_insert('default_value',frame=frame)
        scene=bpy.data.scenes.new(title); scene['ae2f_future_beam']=key
        scene.world=template.world; scene.camera=template.camera
        scene.render.engine='CYCLES'; scene.cycles.device='CPU'; scene.cycles.samples=48
        scene.cycles.use_denoising=True; scene.cycles.transparent_max_bounces=24; scene.cycles.seed=41
        scene.view_settings.view_transform=template.view_settings.view_transform
        scene.view_settings.look=template.view_settings.look
        scene.render.fps=30; scene.frame_start=1; scene.frame_end=96
        scene.render.resolution_percentage=100
        for obj in template.objects:
            if '/ Flow' not in obj.name: scene.collection.objects.link(obj)
        if close_camera is None:
            close_camera=camera(scene,'Future Beam Closeup',(1,-11,2.2),(-1.6,-3.5,.5),4.4)
        else: scene.collection.objects.link(close_camera)
        vertices,faces,indices=mesh_for(key,configs,0,study.generate)
        mesh=bpy.data.meshes.new(title+' / Geometry'); mesh.from_pydata(vertices,[],faces); mesh.update()
        for mat in palette: mesh.materials.append(mat)
        for face,index in zip(mesh.polygons,indices):face.material_index=index
        obj=bpy.data.objects.new(title+' / Internal Renderer',mesh); scene.collection.objects.link(obj)
        obj['prototype_port_required']=True
        obj.shape_key_add(name='Basis')
        for frame in range(1,97,3):
            current,current_faces,current_indices=mesh_for(key,configs,(frame-1)/96,study.generate)
            assert current_faces==faces and current_indices==indices,(key,frame,'Unstable topology')
            shape=obj.shape_key_add(name=f'Frame {frame:03d}')
            shape.data.foreach_set('co',[c for point in current for c in point])
            for at,value in [(max(0,frame-3),0),(frame,1),(frame+3,0)]:
                shape.value=value; shape.keyframe_insert('value',frame=at)
        action=mesh.shape_keys.animation_data.action
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for point in curve.keyframe_points:point.interpolation='CONSTANT'
        scenes.append(scene)
        bpy.context.window.scene=scene; scene.frame_set(25)
        for name,digest in shell.items():
            assert scene.objects[name] is template.objects[name]
            assert geometry_digest(scene.objects[name])==digest
        for kind,cam,width,height in [('wide',template.camera,1500,440),('close',close_camera,1280,600)]:
            scene.camera=cam; scene.render.resolution_x=width; scene.render.resolution_y=height
            scene.render.filepath=str(output/f'{key}-{kind}.png')
            bpy.ops.render.render(write_still=True,scene=scene.name)
        report['profiles'][key]={'title':title,'description':description,'vertices':len(vertices),'polygons':len(faces)}
        if hasattr(study,'parameters_for'):
            report['profiles'][key]['parameters']=study.parameters_for(key)
            report['profiles'][key]['palette']=colors
            report['profiles'][key]['emission_frame16']=study.emission_for(key,15/96)
            scene['preview_load_ratio']=study.parameters_for(key)['ratio']
        sample=mesh_for(key,configs,.25,study.generate)
        (output/(key+'-mesh.json')).write_text(json.dumps({'frame':25,'positions':sample[0],'faces':sample[1],'palette_indices':sample[2]},separators=(',',':')))
        print('FUTURE_PROFILE_RENDERED '+key,flush=True)
    bpy.context.window.scene=scenes[0]
    for scene in scenes:
        scene.camera=close_camera; scene.frame_set(25)
    bpy.ops.wm.save_as_mainfile(filepath=str(output/f'{args.study}-beams.blend'))
    if args.animate:
        for (key,_),scene in zip(profiles.items(),scenes):
            directory=output/key/'frames'; directory.mkdir(parents=True,exist_ok=True)
            bpy.context.window.scene=scene
            scene.render.resolution_x=720; scene.render.resolution_y=338; scene.cycles.samples=20
            for index,frame in enumerate(range(1,97,3)):
                scene.frame_set(frame); scene.render.filepath=str(directory/f'{index:03d}.png')
                bpy.ops.render.render(write_still=True,scene=scene.name)
            print('FUTURE_PROFILE_ANIMATED '+key,flush=True)
    assert before==asset_hashes(),'Production assets were modified'
    report['source_assets_unchanged']=True
    (output/'future-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'FUTURE_BEAMS_OK: {len(profiles)} {args.study} profiles; original shells and assets unchanged',flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--animate',action='store_true')
    parser.add_argument('--study',choices=('future','optical','load','m-load'),default='future')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    main(args)
