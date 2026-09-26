"""Generate Minecraft 1.21.1 / NeoForge assets with approved Blockbench V07 art.
Geometry adapters originate in the website v07 generator; approved art is frozen.
"""
from pathlib import Path
import json
import argparse
import hashlib
import shutil
REPO = Path(__file__).resolve().parents[2]
APPROVED = REPO / 'tools/blockbench/versions/v07-isolated-cable'
ROOT = REPO / 'common/src/main/resources'
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT)
ROOT = parser.parse_args().output
NS = 'ae2federation'
BASE = ROOT / 'assets' / NS
MODELS = BASE / 'models/block'
TEX = BASE / 'textures/block'
MODELS.mkdir(parents=True, exist_ok=True)
TEX.mkdir(parents=True, exist_ok=True)
# The approved native Blockbench snapshot is the texture authority. Geometry
# remains generated here to retain all masks, layer routing and AE2 Part axes.
manifest = json.loads((APPROVED / 'manifest.json').read_text())
for source in sorted((APPROVED / 'textures').iterdir()):
    name = source.relative_to(APPROVED).as_posix()
    if hashlib.sha256(source.read_bytes()).hexdigest() != manifest['files'][name]:
        raise ValueError(f'Approved texture changed: {name}; create a new version')
    shutil.copyfile(source, TEX / source.name)
DIRS=['east','west','up','down','south','north']
NORMAL={'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0),'south':(0,0,1),'north':(0,0,-1)}
def uv(a,b,f):
 x,y,z=a;X,Y,Z=b
 return {'east':[16-Z,16-Y,16-z,16-y],'west':[z,16-Y,Z,16-y],'up':[x,z,X,Z],'down':[x,16-Z,X,16-z],'south':[x,16-Y,X,16-y],'north':[16-X,16-Y,16-x,16-y]}[f]
def el(a,b,tex='armor',bright=False,face_names=None):
 faces={}
 for f in face_names or DIRS:
  t=tex[f] if isinstance(tex,dict) else tex
  faces[f]={'uv':uv(a,b,f),'texture':'#'+t}
 e={'from':a,'to':b,'faces':faces}
 if bright:e.update(shade=False,neoforge_data={'block_light':15,'sky_light':15,'ambient_occlusion':False})
 return e

def prune(elements):
 # Remove faces fully covered by neighboring solid voxels, without inventing thin overlay geometry.
 occ=set()
 for e in elements:
  a,b=e['from'],e['to']
  for x in range(a[0],b[0]):
   for y in range(a[1],b[1]):
    for z in range(a[2],b[2]):occ.add((x,y,z))
 for e in elements:
  a,b=e['from'],e['to']
  for f in list(e['faces']):
   n=NORMAL[f];axis=next(i for i,v in enumerate(n) if v);side=b[axis] if n[axis]>0 else a[axis]-1
   axes=[j for j in range(3) if j!=axis];hidden=True
   for p in range(a[axes[0]],b[axes[0]]):
    for q in range(a[axes[1]],b[axes[1]]):
     v=[0,0,0];v[axis]=side;v[axes[0]]=p;v[axes[1]]=q
     if tuple(v) not in occ:hidden=False;break
    if not hidden:break
   if hidden:del e['faces'][f]
 return [e for e in elements if e['faces']]

def cage(lo,hi,axis_only=None,along=None):
 es=[]
 if axis_only is not None:
  ax=axis_only;others=[j for j in range(3) if j!=ax];start,end=along
  for k in range(2):
   for s in [lo,hi-1]:
    a=[lo]*3;b=[hi]*3;a[ax]=start;b[ax]=end;a[others[k]]=s;b[others[k]]=s+1
    if k==1:a[others[0]]=lo+1;b[others[0]]=hi-1
    es.append(el(a,b))
 else:
  for x in [lo,hi-1]:
   for z in [lo,hi-1]:es.append(el([x,lo,z],[x+1,hi,z+1]))
  for y in [lo,hi-1]:
   for z in [lo,hi-1]:es.append(el([lo+1,y,z],[hi-1,y+1,z+1]))
   for x in [lo,hi-1]:es.append(el([x,y,lo+1],[x+1,y+1,hi-1]))
 return es

def write_model(name,es,layer='solid'):
 refs=sorted({f['texture'][1:] for e in es for f in e['faces'].values()})
 obj={'parent':'minecraft:block/block','ambientocclusion':True,'render_type':'minecraft:'+layer,'textures':{t:NS+':block/'+t for t in refs},'elements':es}
 obj['textures']['particle']=NS+':block/armor'
 p=MODELS/(name+'.json');p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,indent=2)+'\n')
 return NS+':block/'+name

models={}
for kind in ['router','processing_endpoint','pattern_provider']:
 faces={f:'router' for f in DIRS} if kind=='router' else {'east':'me_side_east','west':'me_side_west','up':'me_side_up','down':'me_side_down','south':kind,'north':'me_port'}
 es=[el([0,0,0],[16,16,16],faces)]
 models[kind]=write_model(kind,es)
# South-mounted: rear contact starts at Z=10, outward ME contact ends exactly at Z=16.
faces={f:'bridge_'+f if f in ['east','west','up','down'] else 'bridge_end' for f in DIRS}
es=[el([4,4,11],[12,12,15],faces),el([6,6,10],[10,10,11],'me_context'),el([6,6,15],[10,10,16],'me_context')]
es+=cage(5,11,2,(10,11))+cage(5,11,2,(15,16))
models['bridge']=write_model('bridge_south',prune(es))
# All 64 connection masks: bits east,west,up,down,south,north.
def region(axis,lo,hi,crosslo,crosshi):
 a=[crosslo]*3;b=[crosshi]*3;a[axis]=lo;b[axis]=hi;return a,b

def add_collar(solid, rings):
 # Orthogonal collars can meet at corners. Partition their union without overlaps.
 for ring in rings:
  pieces=[(ring['from'],ring['to'])]
  for old in solid:
   result=[]
   for a,b in pieces:
    lo=[max(a[i],old['from'][i]) for i in range(3)]
    hi=[min(b[i],old['to'][i]) for i in range(3)]
    if any(lo[i]>=hi[i] for i in range(3)):
     result.append((a,b));continue
    a=a.copy();b=b.copy()
    for axis in range(3):
     if a[axis]<lo[axis]:
      end=b.copy();end[axis]=lo[axis];result.append((a.copy(),end));a[axis]=lo[axis]
     if b[axis]>hi[axis]:
      start=a.copy();start[axis]=hi[axis];result.append((start,b.copy()));b[axis]=hi[axis]
   pieces=result
  solid.extend(el(a,b) for a,b in pieces)

def cable(mask):
 solid=[];glass=[];streams=[]
 # A single continuous animated volume, partitioned into a hub and arms.
 def flow(a,b,axis):
  ft={}
  for f in DIRS:
   u_axis=2 if f in ['east','west'] else 0
   ft[f]='stream_'+('u' if axis==u_axis else 'v')
  return el(a,b,ft,True)
 streams=[flow([6,6,6],[10,10,10],1)]
 glass=[el([5,5,5],[11,11,11],'glass')]
 for bit,f in enumerate(DIRS):
  if mask&(1<<bit):
   n=NORMAL[f];ax=next(i for i,v in enumerate(n) if v);positive=n[ax]>0
   streams.append(flow(*region(ax,10 if positive else 0,16 if positive else 6,6,10),ax))
   glass.append(el(*region(ax,11 if positive else 0,16 if positive else 5,5,11),'glass'))
   add_collar(solid,cage(4,12,ax,(15,16) if positive else (0,1)))
 if mask in [3,12,48]:
  ax={3:0,12:1,48:2}[mask];streams=[flow(*region(ax,0,16,6,10),ax)]
 # Mask zero retains the same closed central glass/core as connected cable.
 for e in solid:
  for face in e['faces'].values():
   if face['texture']=='#armor':face['texture']='#collar'
 # Connected block-boundary faces are open, including translucent glass ends.
 for es in [solid,glass,streams]:
  for e in es:
   for bit,f in enumerate(DIRS):
    if not mask&(1<<bit):continue
    n=NORMAL[f];ax=next(i for i,v in enumerate(n) if v)
    if (n[ax]>0 and e['to'][ax]==16) or (n[ax]<0 and e['from'][ax]==0):e['faces'].pop(f,None)
 # Internal glass seam quads are omitted; no coplanar overlapping faces.
 refs={'solid':write_model(f'cable/{mask:02d}_solid',prune(solid)),'glass':write_model(f'cable/{mask:02d}_glass',prune(glass),'translucent')}
 if streams:refs['stream']=write_model(f'cable/{mask:02d}_stream',prune(streams),'cutout')
 obj={'parent':'minecraft:block/block','loader':'neoforge:composite','textures':{'particle':NS+':block/armor'},'children':{k:{'parent':r} for k,r in refs.items()}}
 (MODELS/'cable'/f'{mask:02d}.json').write_text(json.dumps(obj,indent=2)+'\n')
 return NS+f':block/cable/{mask:02d}'
cm={str(m):cable(m) for m in range(64)};models['cable']=cm['3']

# Production adapters. AE2 Part quads are NORTH-oriented; collision boxes are SOUTH-oriented.
bridge=json.loads((MODELS/'bridge_south.json').read_text())
for e in bridge['elements']:
 a,b=e['from'],e['to']
 e['from']=[16-b[0],a[1],16-b[2]];e['to']=[16-a[0],b[1],16-a[2]]
 e['faces']={dict(east='west',west='east',north='south',south='north',up='up',down='down')[f]:v for f,v in e['faces'].items()}
 for f in ['up','down']:
  if f in e['faces']:e['faces'][f]['rotation']=180
part=BASE/'models'/'part';part.mkdir(exist_ok=True)
(part/'bridge.json').write_text(json.dumps(bridge,indent=2)+'\n')
# The item is centered for inventory, hand and dropped rendering.
bridge_item=json.loads(json.dumps(bridge))
for e in bridge_item['elements']:
 e['from'][2]+=5;e['to'][2]+=5
item=BASE/'models'/'item';item.mkdir(exist_ok=True)
(item/'bridge.json').write_text(json.dumps(bridge_item,indent=2)+'\n')
for name in ['router','processing_endpoint','pattern_provider','cable']:
 parent='block/cable/00' if name=='cable' else 'block/'+name
 obj={'parent':NS+':'+parent}
 if name in ['processing_endpoint','pattern_provider']:
  obj=json.loads((MODELS/(name+'.json')).read_text())
  # Default block item transforms show NORTH; keep the functional glyph visible in every item context.
  for e in obj['elements']:
   e['faces']={dict(east='west',west='east',north='south',south='north',up='up',down='down')[f]:v for f,v in e['faces'].items()}
   for f in ['up','down']:e['faces'][f]['rotation']=180
 (item/(name+'.json')).write_text(json.dumps(obj,indent=2)+'\n')
(MODELS/'cable.json').write_text(json.dumps({'parent':NS+':block/cable/00'},indent=2)+'\n')
states=BASE/'blockstates';states.mkdir(exist_ok=True)
for name in ['router','cable','processing_endpoint']:
 variant={'model':NS+':block/'+name}
 if name=='processing_endpoint':variant['y']=270
 (states/(name+'.json')).write_text(json.dumps({'variants':{'':variant}},indent=2)+'\n')
variants={}
for face,rotation in [('south',{}),('west',{'y':90}),('north',{'y':180}),('east',{'y':270}),('up',{'x':90}),('down',{'x':270})]:
 variants['facing='+face]={'model':NS+':block/pattern_provider',**rotation}
(states/'pattern_provider.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
print('Generated production assets for ae2federation (64 cable masks).')
