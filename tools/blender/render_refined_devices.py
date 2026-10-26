"""Build fully UV-textured device samples and a layered, fragmented teal Quantum core."""
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(HERE))
from refined_device_geometry import laser_parts, quantum_parts, FACES
from assets import Assets, dependency_packs, model_quads
from materials import Materials
from build_scene import setup_scene, collection, mesh_object, camera
from device_study import configure, render, resource_hashes
from device_cable_reference import cable, carrier
from export_cable import export


class PaintedMaterials:
    def __init__(self,folder):
        self.folder=folder;self.cache={};self.images={}
        self.manifest=json.loads((folder/'textures.json').read_text())
    def image(self,name,mask=False):
        key=name+('-emission' if mask else '')
        if key not in self.images:
            im=bpy.data.images.load(str(self.folder/(key+'.png')),check_existing=False)
            if mask:im.colorspace_settings.name='Non-Color'
            im.pack();self.images[key]=im
        return self.images[key]
    def get(self,role,w,h):
        name=f'{role}-{w}x{h}'
        if name in self.cache:return self.cache[name]
        mat=bpy.data.materials.new('Painted / '+name);mat.use_nodes=True
        nodes,links=mat.node_tree.nodes,mat.node_tree.links
        shader=nodes.get('Principled BSDF');shader.inputs['Roughness'].default_value=.88
        shader.inputs['Specular IOR Level'].default_value=.18
        uv=nodes.new('ShaderNodeTexCoord')
        tex=nodes.new('ShaderNodeTexImage');tex.image=self.image(name);tex.interpolation='Closest';tex.extension='EXTEND'
        mask=nodes.new('ShaderNodeTexImage');mask.image=self.image(name,True);mask.interpolation='Closest';mask.extension='EXTEND'
        links.new(uv.outputs['UV'],tex.inputs['Vector']);links.new(uv.outputs['UV'],mask.inputs['Vector'])
        links.new(tex.outputs['Color'],shader.inputs['Base Color'])
        links.new(tex.outputs['Color'],shader.inputs['Emission Color'])
        power=nodes.new('ShaderNodeMath');power.operation='MULTIPLY'
        power.inputs[1].default_value={'shard':.55,'shard_inner':1.4,'core':2.0,'gain':.7}.get(role,.18)
        links.new(mask.outputs['Color'],power.inputs[0]);links.new(power.outputs[0],shader.inputs['Emission Strength'])
        mat['painted_texture']=name;mat['texture_pixels']=[w,h];mat['role']=role
        self.cache[name]=mat;return mat


def build_parts(pieces,coll,painted,offset=(0,0,0)):
    objects=[]
    for piece in pieces:
        size=piece['size']
        verts=[tuple(p[i]*size[i]/16 for i in range(3)) for p in itertools.product((-.5,.5),repeat=3)]
        mesh=bpy.data.meshes.new(piece['name']);mesh.from_pydata(verts,[],[f[0] for f in FACES]);mesh.update()
        uv=mesh.uv_layers.new(name='PixelUV')
        for index,(poly,(_,axes,face)) in enumerate(zip(mesh.polygons,FACES)):
            w,h=(max(1,round(size[a])) for a in axes)
            role=piece['face_roles'].get(face,piece['role'])
            mesh.materials.append(painted.get(role,w,h));poly.material_index=index
            for loop in poly.loop_indices:
                vertex=verts[mesh.loops[loop].vertex_index]
                uv.data[loop].uv=tuple(vertex[a]/(size[a]/16)+.5 for a in axes)
        obj=bpy.data.objects.new(piece['name'],mesh);coll.objects.link(obj)
        obj.location=Vector(tuple(c/16 for c in piece['center']))+Vector(offset)
        obj.rotation_euler.y=math.radians(piece['rotation_y'])
        obj['mechanical_texture']=True;obj['voxel_dimensions']=size
        objects.append(obj)
    return objects


def teal_emission(name,hex_color,strength):
    rgb=[int(hex_color[i:i+2],16)/255 for i in (1,3,5)]
    rgb=[c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in rgb]
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    nodes=mat.node_tree.nodes;nodes.clear();links=mat.node_tree.links
    emit=nodes.new('ShaderNodeEmission');emit.inputs[0].default_value=(*rgb,1);emit.inputs[1].default_value=strength
    out=nodes.new('ShaderNodeOutputMaterial');links.new(emit.outputs[0],out.inputs['Surface'])
    return mat


def curved_fragment(coll,painted,parent,radius,lat0,lat1,lon0,lon1,index,inner=False):
    # A solid curved fragment: outer/inner faces and visible cut edges, with explicit UVs.
    nu,nv=4,3;vertices=[]
    def point(r,u,v):
        lat=math.radians(lat0+(lat1-lat0)*v);lon=math.radians(lon0+(lon1-lon0)*u)
        return (r*math.cos(lat)*math.cos(lon),r*math.cos(lat)*math.sin(lon),r*math.sin(lat))
    for r in (radius,radius-.045):
        for j in range(nv+1):
            for i in range(nu+1):vertices.append(point(r,i/nu,j/nv))
    count=(nu+1)*(nv+1);faces=[];coords=[];slots=[]
    for j in range(nv):
        for i in range(nu):
            a=j*(nu+1)+i;b=a+1;c=b+nu+1;d=a+nu+1
            quad=(a,b,c,d);st=[(i/nu,j/nv),((i+1)/nu,j/nv),((i+1)/nu,(j+1)/nv),(i/nu,(j+1)/nv)]
            faces.extend([quad,tuple(count+x for x in quad[::-1])]);coords.extend([st,st[::-1]]);slots.extend([0,1])
    boundary=list(range(nu+1))+[j*(nu+1)+nu for j in range(1,nv+1)]
    boundary+=list(range(count-2,count-nu-2,-1))+[j*(nu+1) for j in range(nv-1,0,-1)]
    for k,a in enumerate(boundary):
        b=boundary[(k+1)%len(boundary)]
        edge_length=(Vector(vertices[b])-Vector(vertices[a])).length
        faces.append((a,a+count,b+count,b))
        coords.append([(0,15/16),(0,1),(edge_length,1),(edge_length,15/16)])
        slots.append(1)
    mesh=bpy.data.meshes.new('Curved energy fragment');mesh.from_pydata(vertices,[],faces);mesh.update()
    mesh.materials.append(painted.get('shard_inner' if inner else 'shard',12,12))
    mesh.materials.append(painted.get('core',16,16))
    uv=mesh.uv_layers.new(name='FragmentUV')
    for polygon,st,slot in zip(mesh.polygons,coords,slots):
        polygon.material_index=slot
        for loop,xy in zip(polygon.loop_indices,st):uv.data[loop].uv=xy
    obj=bpy.data.objects.new(f'Core / {"inner" if inner else "outer"} fragment {index:02d}',mesh)
    coll.objects.link(obj);obj.parent=parent;obj['fragment_layer']='inner' if inner else 'outer'
    # Discontinuous luminous rims make the separated plates legible, without whitening the surface.
    curve=bpy.data.curves.new('Fragment edge light','CURVE');curve.dimensions='3D';curve.bevel_depth=.006;curve.bevel_resolution=0
    spline=curve.splines.new('POLY');spline.points.add(6)
    for j,p in enumerate(spline.points):p.co=(*point(radius+.004,.10+.8*j/6,1),1)
    rim=bpy.data.objects.new('Core / teal fragment rim',curve);coll.objects.link(rim);rim.parent=parent
    curve.materials.append(bpy.data.materials['Energy / source teal'])
    return obj


def fragmented_core(scene,painted,center,radius):
    coll=collection(scene,'Core / layered teal fragments')
    for layer,scale,segments,bands in (
        ('outer',1,8,[(-62,-27),(-18,18),(27,62)]),
        ('inner',.63,6,[(-58,-10),(10,58)])):
        root=bpy.data.objects.new('Core / '+layer+' rotation',None);coll.objects.link(root);root.location=center
        root.rotation_euler=(.12 if layer=='outer' else -.25, .12, 0)
        root.keyframe_insert('rotation_euler',frame=1)
        root.rotation_euler.z=math.tau if layer=='outer' else -math.tau
        root.keyframe_insert('rotation_euler',frame=121)
        for b,(lo,hi) in enumerate(bands):
            for j in range(segments):
                # Stagger the gaps and leave two wider apertures into the inner layers.
                if layer=='outer' and (b,j) in ((1,1),(1,5)):continue
                step=360/segments;start=j*step+(b%2)*step/2+(15 if layer=='inner' else 0)
                curved_fragment(coll,painted,root,radius*scale,lo,hi,start+6,start+step-6,
                                b*segments+j,layer=='inner')
    bpy.ops.mesh.primitive_uv_sphere_add(segments=12,ring_count=6,radius=radius*.30,location=center)
    obj=bpy.context.object;obj.name='Core / faceted dark nucleus'
    for c in list(obj.users_collection):c.objects.unlink(obj)
    coll.objects.link(obj);obj.data.materials.append(painted.get('core',16,16))
    # Six small teal nodes provide a readable centre through the shell gaps.
    for axis in range(3):
        for sign in (-1,1):
            p=Vector(center);p[axis]+=sign*radius*.30
            bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=.055,location=p)
            node=bpy.context.object;node.name='Core / convergence node'
            for old in list(node.users_collection):old.objects.unlink(node)
            coll.objects.link(node);node.data.materials.append(bpy.data.materials['Energy / source teal'])
    scene['core_center']=list(center);scene['core_radius']=radius
    light_data=bpy.data.lights.new('Core / teal interior light','POINT')
    light_data.energy=65*radius*radius;light_data.color=(108/255,174/255,187/255)
    light_data.shadow_soft_size=.2*radius
    light=bpy.data.objects.new(light_data.name,light_data);coll.objects.link(light);light.location=center
    return coll


def make_scene(name,args,target,scale):
    scene=setup_scene(name);configure(scene,args,scale,target)
    scene.cycles.samples=48
    scene.camera.location=Vector(target)+Vector((7,-11,6.5))
    scene.camera.rotation_euler=(Vector(target)-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    scene.view_settings.exposure=-.6
    return scene


def laser(scene,painted,n,variant,offset=(0,0,0)):
    coll=collection(scene,'Laser / complete pixel UV structure')
    build_parts(laser_parts(n,variant),coll,painted,offset)
    optics=collection(scene,'Laser / original cable light')
    beam=carrier(optics,Vector(offset)+Vector((0,-n,n/2)),(0,1,0),n,1.75*n)
    beam.name='Laser / carrier from cable interface'
    tint_device_carrier(beam)
    scene['section']=n;scene['device']='laser';scene['variant']=variant


def tint_device_carrier(obj):
    """Keep carrier geometry, using the owner's Federation teal for newly designed device light."""
    for i,mat in enumerate(list(obj.data.materials)):
        mat=mat.copy();obj.data.materials[i]=mat
        rgb=(162,220,226) if i in obj['beam_core_slots'] else (108,174,187)
        values=[c/255 for c in rgb]
        values=[c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4 for c in values]
        for node in mat.node_tree.nodes:
            if node.type=='EMISSION':node.inputs['Color'].default_value=(*values,1)
        mat['device_light_rgb']=list(rgb)


def quantum(scene,painted,n):
    coll=collection(scene,'Quantum / full pixel UV frame')
    build_parts(quantum_parts(n),coll,painted)
    h=(n+2)/2
    fragmented_core(scene,painted,(0,0,h),.25*(n+2))
    scene['section']=n;scene['device']='quantum'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=HERE/'build/refined-devices-final')
    parser.add_argument('--device',choices=('optix','cuda','cpu'),default='optix')
    parser.add_argument('--resolution',type=int,default=1100)
    parser.add_argument('--preview',action='store_true')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    args.output=args.output.resolve();args.output.mkdir(parents=True,exist_ok=True)
    if (args.output/'refined.blend').exists():raise FileExistsError('Use a fresh output directory')
    before=resource_hashes();painted=PaintedMaterials(args.output/'textures')
    assets=Assets(ROOT,args.output/'resources',dependency_packs(ROOT));imported=Materials(assets,[])
    export(ROOT,args.output/'cable.json');data=json.loads((args.output/'cable.json').read_text())
    teal_emission('Energy / source teal','#6caebb',2.2)
    scenes=[]
    for variant in ('open','shroud'):
        scene=make_scene('Refined laser / '+variant,args,(0,0,1),5.8)
        laser(scene,painted,2,variant);scene.frame_set(19)
        render(scene,args.output/f'laser-{variant}.png');scenes.append(scene)
    scene=make_scene('Refined Quantum / open frame',args,(0,0,2),6.5)
    quantum(scene,painted,2);scene.frame_set(19);render(scene,args.output/'quantum-frame.png');scenes.append(scene)
    close=make_scene('Refined Quantum / core detail',args,(0,0,1.15),3.0)
    fragmented_core(close,painted,(0,0,1.15),1.0);close.frame_set(19)
    render(close,args.output/'core-detail.png');scenes.append(close)
    if not args.preview:
        for n in (1,2,3):
            for device in ('laser','quantum'):
                h=n/2 if device=='laser' else (n+2)/2
                target=(0,-1,n/2) if device=='laser' else (-.6,.6,h)
                scale=max(5.1,2.8*n+1.2) if device=='laser' else n+7
                scene=make_scene(f'Connected / {device} / {n}',args,target,scale)
                refs=collection(scene,'Current ordinary and bundled cable references')
                if device=='laser':
                    laser(scene,painted,n,'open')
                    cable(refs,(0,-n,h),(0,-1,0),n,3,assets,imported,data)
                else:
                    quantum(scene,painted,n)
                    for start,direction in (((-h,0,h),(-1,0,0)),((0,h,h),(0,1,0))):
                        cable(refs,start,direction,n,3,assets,imported,data)
                        light=carrier(refs,start,tuple(-x for x in direction),n,h)
                        light.name='Quantum / cable convergence'
                        tint_device_carrier(light)
                scene.frame_set(19);render(scene,args.output/f'{device}-cable-{n}.png');scenes.append(scene)
        # Compare native artist geometry and the new device at one physical scale.
        scene=make_scene('Comparison / artist reference and new laser',args,(0,0,1),8.7)
        coll=collection(scene,'Current artist Beam Former / original geometry and UV')
        for x in range(2):
            for y in range(2):
                model=assets.model(f'ae2federation:block/beam_former_large_{x}{y}')
                mesh_object('Current Beam Former',model_quads(assets,model),coll,imported,(-4+x,.5,y),cutout=True)
        laser(scene,painted,2,'open',offset=(1.25,0,0))
        render(scene,args.output/'artist-comparison.png');scenes.append(scene)
        scene=make_scene('Detail / mechanical pixels',args,(.72,-1.05,1.70),2.1)
        laser(scene,painted,2,'open')
        render(scene,args.output/'laser-detail.png');scenes.append(scene)
    for scene in scenes:scene.frame_end=120
    bpy.context.window.scene=scenes[0]
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=='VIEW_3D':
                area.spaces.active.region_3d.view_perspective='CAMERA'
                area.spaces.active.shading.type='MATERIAL'
    bpy.ops.wm.save_as_mainfile(filepath=str(args.output/'refined.blend'))
    assert resource_hashes()==before
    (args.output/'manifest.json').write_text(json.dumps({'production_sha256':before,'reference_assets':assets.used,
        'cable_sources':data['source_sha256'],'scenes':[s.name for s in scenes],
        'painted_textures':painted.manifest,'accent':'Federation teal from original collar, no violet energy',
        'core':'22 outer curved fragments, 12 inner curved fragments, faceted nucleus'},indent=2)+'\n')
    print('REFINED_DEVICES_OK',len(scenes),args.output)


if __name__=='__main__':main()
