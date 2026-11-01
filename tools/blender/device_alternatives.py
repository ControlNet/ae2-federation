"""Second-round Blender proposals: four laser emitters and three open Quantum Cubes.

Render with current ordinary and bundled cables. No production resources are changed.
"""
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
from assets import Assets, dependency_packs
from materials import Materials
from build_scene import camera, collection, setup_scene
from device_study import configure, render, resource_hashes
from device_designs import box, face_box, square_ring, tube, orb, energy_glow, palette, FACES
from device_cable_reference import cable, carrier, matched_palette
from export_cable import export

LASERS = {'L1': 'Split Rail', 'L2': 'Octagonal Barrel', 'L3': 'Crystal Resonator', 'L4': 'Reflector Focus'}
QUANTUM = {'Q1': 'Open Edge Frame', 'Q2': 'Glass Containment', 'Q3': 'Corner Glass'}


def annulus(name, center, outer, inner, depth, coll, mats, sides=8, phase=math.pi/8,
            back_outer=None, back_inner=None):
    """An editable faceted annular frustum, axis Y, with a genuine open bore."""
    back_outer = outer if back_outer is None else back_outer
    back_inner = inner if back_inner is None else back_inner
    verts = []
    for y, ro, ri in ((-depth/2,outer,inner),(depth/2,back_outer,back_inner)):
        for radius in (ro,ri):
            verts.extend((radius*math.cos(phase+math.tau*j/sides),y,
                          radius*math.sin(phase+math.tau*j/sides)) for j in range(sides))
    faces, indices = [], []
    for j in range(sides):
        k = (j+1)%sides
        faces.extend([(j,k,sides+k,sides+j), (2*sides+j,3*sides+j,3*sides+k,2*sides+k),
                      (j,2*sides+j,2*sides+k,k), (sides+j,sides+k,3*sides+k,3*sides+j)])
        indices.extend([0,1,min(2,len(mats)-1),1])
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update()
    for mat in mats:mesh.materials.append(mat)
    for polygon,index in zip(mesh.polygons,indices):polygon.material_index=index
    obj=bpy.data.objects.new(name,mesh);coll.objects.link(obj);obj.location=center
    obj['design_part']=True
    return obj


def tapered_rail(name, y0, y1, x0, x1, z0, z1, width, height, coll, mat):
    verts=[]
    for y,x,z in ((y0,x0,z0),(y1,x1,z1)):
        verts.extend([(x-width/2,y,z-height/2),(x+width/2,y,z-height/2),
                      (x+width/2,y,z+height/2),(x-width/2,y,z+height/2)])
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],[(0,3,2,1),(4,5,6,7),
        (0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]);mesh.materials.append(mat);mesh.update()
    obj=bpy.data.objects.new(name,mesh);coll.objects.link(obj);obj['design_part']=True
    return obj


def optical_line(coll, start, end, mats, width=.018):
    tube('Optics / core',start,end,width,mats['core'],coll)
    tube('Optics / shoulder',start,end,width*3,mats['halo'],coll)


def terminal(coll, n, mats, origin=(0,0,0)):
    """Cable contact is at Y=-n; housing sits entirely on the device side."""
    origin=Vector(origin)+Vector((0,0,n/2))
    half=.3125 if n==1 else n/2-.125
    basis=FACES['front']
    # Same silver / dark / white profile as the cable, with an open optical centre.
    for delta,depth,mat in ((.055,n-.07,mats['silver']),(.020,n-.045,mats['dark']),(-.012,n-.02,mats['white'])):
        square_ring('Terminal / cable-matched edge',origin,basis,half+delta,depth,.045,.035,mat,coll)
    for sx,sz in itertools.product((-1,1),repeat=2):
        face_box('Terminal / corner clamp',origin,basis,sx*(half-.04),sz*(half-.04),n-.055,
                 .13,.13,.11,mats['cyan'],coll)


def laser(scene,key,n,mats,connected=False):
    coll=collection(scene,f'{key} / editable laser structure')
    optics=collection(scene,f'{key} / optical cavity')
    p=lambda x,y,z:(x*n,y*n,z*n)
    b=lambda title,x,y,z,dx,dy,dz,mat:box(title,p(x,y,z),(dx*n,dy*n,dz*n),mats[mat],coll)
    ring=lambda title,y,ro,ri,d,keys=('white','dark','silver'),**kw:annulus(title,p(0,y,.5),ro*n,ri*n,d*n,
                    coll,[mats[k] for k in keys],**{k:v*n if k.startswith('back_') else v for k,v in kw.items()})
    # Rear excitation unit and a restrained FE receptacle are shared functional anchors.
    ring('Excitation / rear ceramic cap',.86,.46,.09,.26)
    b('Excitation / rear FE receptacle',0,.997,.5,.25,.006,.25,'dark')
    b('Excitation / rear FE contact',0,1.002,.5,.11,.008,.11,'ring')
    if connected:
        terminal(coll,n,mats)
    if key=='L1':
        # Four long white rails leave the complete resonator axis visible.
        for sx,sz in itertools.product((-1,1),repeat=2):
            tapered_rail('L1 / ceramic focusing rail',-.80*n,.77*n,sx*.30*n,sx*.38*n,
                         (.5+sz*.30)*n,(.5+sz*.34)*n,.13*n,.13*n,coll,mats['white'])
            tapered_rail('L1 / rail dark inlay',-.76*n,.69*n,sx*.30*n,sx*.38*n,
                         (.5+sz*.30+.068)*n,(.5+sz*.34+.068)*n,.065*n,.007*n,coll,mats['dark'])
        for y,r in ((.54,.31),(.12,.265),(-.32,.215)):
            ring('L1 / staged collimation lens',y,r,r-.045,.075,('ring','teal','silver'))
        ring('L1 / split nozzle',-.78,.39,.17,.24)
        ring('L1 / cyan aperture',-.915,.22,.155,.035,('ice','teal','cyan'))
    elif key=='L2':
        # Octagonal jacket steps down toward a visibly extended nozzle.
        ring('L2 / rear barrel jacket',.49,.455,.26,.5)
        ring('L2 / forward barrel',-.03,.35,.21,.57)
        ring('L2 / extended dark muzzle',-.49,.29,.17,.43,('ring','dark','silver'))
        ring('L2 / white muzzle lip',-.785,.34,.19,.16)
        for y,r in ((.27,.47),(-.30,.37),(-.65,.31)):
            ring('L2 / cyan separator',y,r,r-.035,.045,('cyan','teal','ice'))
        for angle in (0,math.pi/2,math.pi,math.pi*1.5):
            x,z=.397*math.cos(angle),.5+.397*math.sin(angle)
            b('L2 / longitudinal optical inlay',x,.49,z,.035,.40,.035,'cyan')
        ring('L2 / final focusing lens',-.90,.225,.16,.045,('ice','teal','cyan'))
    elif key=='L3':
        # One resonator crystal, two transparent windows and a single lower keel.
        b('L3 / ceramic keel',0,.02,.11,.56,1.52,.16,'white')
        b('L3 / dark keel inlay',0,.02,.025,.27,1.30,.012,'dark')
        for x in (-.39,.39):
            b('L3 / glass resonator wall',x,.08,.53,.005,1.24,.60,'glass')
            b('L3 / upper window edge',x,.08,.84,.055,1.33,.055,'white')
        for y in (-.49,.63):
            ring('L3 / cavity retaining ring',y,.39,.29,.075)
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=.24*n,location=p(0,.12,.5))
        crystal=bpy.context.object;crystal.name='L3 / faceted resonator crystal';crystal.scale=(.7,2.1,.7)
        for old in list(crystal.users_collection):old.objects.unlink(crystal)
        optics.objects.link(crystal);crystal.data.materials.append(mats['phase'])
        ring('L3 / optical funnel',-.64,.34,.19,.22,back_outer=.39,back_inner=.27)
        ring('L3 / focusing nozzle',-.84,.26,.14,.18,('white','teal','cyan'))
    elif key=='L4':
        # A broad concave reflector behind an exposed focus, then a narrow forward lens.
        ring('L4 / reflector ceramic rim',.46,.47,.40,.14)
        ring('L4 / concave reflector',.61,.40,.35,.25,('silver','ice','ring'),back_outer=.27,back_inner=.20)
        for sx in (-1,1):
            tapered_rail('L4 / forward optical boom',-.78*n,.43*n,sx*.26*n,sx*.43*n,
                         .28*n,.36*n,.12*n,.15*n,coll,mats['white'])
            tapered_rail('L4 / boom inlay',-.73*n,.39*n,sx*.26*n,sx*.43*n,
                         .36*n,.44*n,.055*n,.012*n,coll,mats['cyan'])
        ring('L4 / floating focus gate',-.10,.23,.16,.075,('cyan','teal','ice'))
        ring('L4 / forward lens housing',-.65,.32,.15,.33)
        ring('L4 / exit optic',-.85,.205,.14,.055,('ice','teal','cyan'))
        tube('L4 / active focus',p(0,.22,.5),p(0,.32,.5),.085*n,mats['light'],optics,sides=8)
    beam=carrier(optics,p(0,-1,.5),(0,1,0),n,1.75*n)
    beam.name='Laser / continuous cable beam'
    # Short output makes the laser axis legible even before a cable is attached.
    scene['device_kind']='laser';scene['candidate']=key;scene['section']=n
    scene['connected']=connected
    scene['front_plane']=-n;scene['optical_center']=[0,-n,n/2]
    scene['body_dimensions']=[n,2*n,n]
    return coll


def glass_face(coll,center,basis,half,depth,mats,hole=0):
    """One thin surface, split around a connected port; no stacked transparent slabs."""
    rects=[(0,0,2*half,2*half)] if not hole else [
        (0,(half+hole)/2,2*half,half-hole),(0,-(half+hole)/2,2*half,half-hole),
        ((half+hole)/2,0,half-hole,2*hole),(-(half+hole)/2,0,half-hole,2*hole)]
    for u,v,w,h in rects:
        verts=[]
        for a,b in ((u-w/2,v-h/2),(u+w/2,v-h/2),(u+w/2,v+h/2),(u-w/2,v+h/2)):
            verts.append(Vector(center)+Vector(basis[0])*a+Vector(basis[1])*b+Vector(basis[2])*depth)
        mesh=bpy.data.meshes.new('Quantum glass');mesh.from_pydata(verts,[],[(0,1,2,3)]);mesh.materials.append(mats['glass'])
        obj=bpy.data.objects.new('Quantum / cable-matched glass',mesh);coll.objects.link(obj)
        obj['source_alpha']=75/255


def quantum(scene,key,n,mats,connected=False):
    size=n+2;h=size/2;center=Vector((0,0,h))
    coll=collection(scene,key+' / sparse structure');energy=collection(scene,key+' / central energy')
    connected_faces=('left','back') if connected else ()
    for signs in itertools.product((-1,1),repeat=3):
        pos=center+Vector(tuple(s*(h-.16) for s in signs))
        box('Quantum / corner anchor',pos,(.32,)*3,mats['silver'],coll)
        for axis in range(3):
            p=pos.copy();p[axis]+=signs[axis]*.165
            dims=[.17]*3;dims[axis]=.015
            box('Quantum / corner dark inset',p,dims,mats['dark'],coll)
            p[axis]+=signs[axis]*.01;dims=[.075]*3;dims[axis]=.02
            box('Quantum / corner phase inset',p,dims,mats['phase'],coll)
    for axis in range(3):
        other=[i for i in range(3) if i!=axis]
        for a,b in itertools.product((-1,1),repeat=2):
            p=center.copy();p[other[0]]+=a*(h-.08);p[other[1]]+=b*(h-.08)
            if key!='Q3':
                dims=[.125]*3;dims[axis]=size-.40
                box('Quantum / slim edge',p,dims,mats['white'],coll)
                for j,sign in zip(other,(a,b)):
                    stripe=p.copy();stripe[j]+=sign*.064
                    d=[.035]*3;d[axis]=size-.56;d[j]=.008
                    box('Quantum / edge stripe',stripe,d,mats['dark'],coll)
            else:
                # Short corner fingers only; there is no complete edge rail.
                for sign in (-1,1):
                    p[axis]=center[axis]+sign*(h-.40)
                    dims=[.10]*3;dims[axis]=.50
                    box('Quantum / short corner finger',p,dims,mats['white'],coll)
    for name,basis in FACES.items():
        if key!='Q1':
            glass_face(coll,center,basis,h-.125,h-.125,mats,
                       hole=(n/2-.125) if name in connected_faces else 0)
        # Contacts are attached to connected cable ends only, leaving unused faces unobstructed.
        if name in connected_faces:
            half=.3125 if n==1 else n/2-.125
            square_ring('Quantum / cable-end receiver',center,basis,half,h-.025,.055,.05,mats['silver'],coll)
            square_ring('Quantum / cyan receiver inset',center,basis,half-.055,h-.045,.035,.035,mats['cyan'],coll)
            start=center+Vector(basis[2])*h
            beam=carrier(energy,start,-Vector(basis[2]),n,h)
            beam.name='Quantum / continuous cable beam / '+name
            beam['start']=list(start);beam['end']=list(center)
    orb(center,size*.24,energy,mats);energy_glow(scene)
    scene['device_kind']='quantum';scene['candidate']=key;scene['section']=n
    scene['body_dimensions']=[size]*3;scene['optical_center']=list(center)
    scene['connected_faces']=list(connected_faces)
    return coll


def shot(scene,args,name,target,scale):
    configure(scene,args,scale,target)
    # A lower camera exposes resonator depth and keeps the Quantum sphere prominent.
    scene.camera.location=Vector(target)+Vector((7,-11,6.2))
    scene.camera.rotation_euler=(Vector(target)-scene.camera.location).to_track_quat('-Z','Y').to_euler()
    scene.frame_set(31)
    render(scene,args.output/(name+'.png'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=HERE/'build/device-alternatives-final')
    parser.add_argument('--resolution',type=int,default=960)
    parser.add_argument('--device',choices=('optix','cuda','cpu'),default='optix')
    parser.add_argument('--only',nargs='+',choices=tuple(LASERS)+tuple(QUANTUM))
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    args.output=args.output.resolve();args.output.mkdir(parents=True,exist_ok=True)
    if (args.output/'alternatives.blend').exists():raise FileExistsError('Choose a fresh --output directory')
    before=resource_hashes()
    export(ROOT,args.output/'cable.json')
    exported=json.loads((args.output/'cable.json').read_text())
    assets=Assets(ROOT,args.output/'resources',dependency_packs(ROOT));imported=Materials(assets,[])
    mats=palette();mats.update(matched_palette(imported))
    scenes=[]
    keys=args.only or list(LASERS)+list(QUANTUM)
    for key in keys:
        scene=setup_scene('Proposal / '+key+' / '+(LASERS|QUANTUM)[key])
        if key in LASERS:
            laser(scene,key,2,mats);target=(0,0,1);scale=5.8
        else:
            quantum(scene,key,2,mats);target=(0,0,2);scale=6.3
        shot(scene,args,key+'-hero',target,scale);scenes.append(scene)
        for n in (1,2,3):
            scene=setup_scene(f'Connection / {key} / {n}x{n}')
            refs=collection(scene,'Current cable / reference geometry and materials')
            if key in LASERS:
                laser(scene,key,n,mats,connected=True)
                cable(refs,(0,-n,n/2),(0,-1,0),n,3,assets,imported,exported)
                target=(0,-1.1,n/2);scale=max(5.1,2.8*n+1.2)
            else:
                quantum(scene,key,n,mats,connected=True)
                h=(n+2)/2
                cable(refs,(-h,0,h),(-1,0,0),n,3,assets,imported,exported)
                cable(refs,(0,h,h),(0,1,0),n,3,assets,imported,exported)
                target=(-.6,.6,h);scale=n+7
            shot(scene,args,f'{key}-cable-{n}',target,scale);scenes.append(scene)
    for scene in scenes:scene.frame_end=120
    bpy.context.window.scene=scenes[0]
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type=='VIEW_3D':
                area.spaces.active.region_3d.view_perspective='CAMERA'
                area.spaces.active.shading.type='MATERIAL'
    bpy.ops.wm.save_as_mainfile(filepath=str(args.output/'alternatives.blend'))
    assert before==resource_hashes()
    sources={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
             [ROOT/'tools/blockbench/projects'/f'{s}_cable_straight.bbmodel' for s in ('large','huge')]}
    (args.output/'manifest.json').write_text(json.dumps({'production_sha256':before,'assets':assets.used,
        'cable_java':exported['source_sha256'],'ring_projects':sources,'scenes':[s.name for s in scenes],
        'concepts':{k:(LASERS|QUANTUM)[k] for k in keys},'glass_rgba':[204,255,255,75],
        'scope':'Blender design and straight cable material/geometry study; not in-game acceptance'},indent=2)+'\n')
    print('DEVICE_ALTERNATIVES_OK',len(scenes),args.output)


if __name__=='__main__':main()
