"""Generate the canonical Minecraft 1.21.1 / NeoForge asset kit.
Adapted from the v07 reference generator; v0.2 is the visual authority.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json
import argparse
ROOT=Path(__file__).resolve().parents[2]/'common'/'src'/'main'/'resources'
parser=argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT)
ROOT=parser.parse_args().output
NS='ae2federation'
BASE=ROOT/'assets'/NS
MODELS=BASE/'models'/'block'
TEX=BASE/'textures'/'block'
MODELS.mkdir(parents=True,exist_ok=True);TEX.mkdir(parents=True,exist_ok=True)
C={'white':'#eef2f4','shell':'#ccd7de','shade':'#a9bac5','dark':'#253847','black':'#14232f','steel':'#4c6574','teal':'#086b7b','cyan':'#00cada','bright':'#94f7fa','purple':'#ad77dc','violet':'#684887'}
def image(bg='dark'):
 im=Image.new('RGBA',(16,16),C.get(bg,bg));return im,ImageDraw.Draw(im)
def rect(d,x,y,w,h,c):
 d.rectangle((x,y,x+w-1,y+h-1),fill=C.get(c,c))
def frame(d,x,y,w,h,c):
 rect(d,x,y,w,1,c);rect(d,x,y+h-1,w,1,c);rect(d,x,y,1,h,c);rect(d,x+w-1,y,1,h,c)
def save(name,im):im.save(TEX/(name+'.png'))
# One face-wide atlas; the thin frame samples only the matching 1-pixel strips.
im,d=image('shell');rect(d,0,0,16,1,'white');rect(d,0,0,1,16,'white');rect(d,15,0,1,16,'shade');rect(d,0,15,16,1,'shade');rect(d,4,0,3,1,'#dce5e9');rect(d,0,9,1,3,'#dce5e9');rect(d,15,5,1,2,'#c1cdd5');rect(d,7,15,3,1,'#bfccd5');rect(d,2,3,5,3,'#d2dde3');rect(d,9,10,5,3,'#c4d1d9');save('armor',im)
im=im.copy();d=ImageDraw.Draw(im);rect(d,7,5,1,2,'cyan');rect(d,8,7,1,2,'steel');rect(d,7,9,1,2,'cyan');save('collar',im)
def shell_panel():
 im,d=image('shell')
 # Broad flush ceramic plates, small pixel steps and interrupted service seams.
 rect(d,0,0,16,1,'white');rect(d,0,1,1,14,'#dce6eb')
 rect(d,0,15,16,1,'shade');rect(d,15,1,1,14,'#b7c6cf')
 rect(d,2,2,5,2,'#dbe4e9');rect(d,10,12,4,2,'#bfced7')
 rect(d,1,7,2,1,'steel');rect(d,13,8,2,1,'shade')
 return im,d
def socket(d):
 rect(d,3,3,10,1,'shade');rect(d,3,4,10,9,'dark')
 rect(d,3,4,1,8,'black');rect(d,4,12,9,1,'#7b94a3')
 rect(d,4,13,9,1,'white');rect(d,13,4,1,9,'#dce6eb')
im,d=image('shell');frame(d,5,5,6,6,'white');frame(d,6,6,4,4,'teal');rect(d,7,7,2,2,'bright');save('cable_idle',im)
for kind in ['router','processing_endpoint','pattern_provider','me_port']:
 im,d=shell_panel()
 if kind=='router':
  rect(d,5,5,6,6,'dark');rect(d,6,6,4,4,'cyan');rect(d,7,6,2,2,'bright')
  for n in [2,3,12,13]:rect(d,7,n,2,1,'cyan');rect(d,n,7,1,2,'cyan')
 elif kind=='processing_endpoint':
  rect(d,4,5,8,6,'dark');rect(d,6,6,4,4,'teal');rect(d,6,6,4,2,'cyan')
  rect(d,7,6,2,1,'bright');rect(d,3,7,1,2,'cyan');rect(d,12,7,1,2,'cyan')
 elif kind=='pattern_provider':
  for y in [4,7,10]:
   rect(d,4,y,7,2,'teal');rect(d,4,y,6,1,'cyan');rect(d,4,y,1,2,'bright')
  rect(d,12,4,1,8,'cyan')
  for y in [4,7,10]:rect(d,10,y,2,1,'cyan')
 else:
  frame(d,4,4,8,8,'violet');frame(d,5,5,6,6,'purple');rect(d,7,6,2,4,'#dcc1f4');rect(d,6,7,4,2,'#dcc1f4')
 save(kind,im)
im,d=shell_panel()
for y in [5,8,11]:rect(d,3,y,10,2,'black');rect(d,3,y,7,1,'purple');rect(d,10,y,1,1,'#6a8fa8');rect(d,11,y,2,1,'cyan')
rect(d,6,3,4,1,'steel');save('me_side_west',im)
save('me_side_east',im.transpose(Image.Transpose.FLIP_LEFT_RIGHT))
save('me_side_up',im.transpose(Image.Transpose.ROTATE_270))
save('me_side_down',im.transpose(Image.Transpose.ROTATE_90))
# Bridge patches use native world-space UV coordinates. Every glyph texel is 1 model unit.
for kind in ['bridge_east','bridge_west','bridge_up','bridge_down','bridge_end']:
 im,d=image('dark')
 if kind=='bridge_end':
  frame(d,6,6,4,4,'violet');rect(d,7,7,2,2,'purple')
 elif kind in ['bridge_east','bridge_west']:
  start=1 if kind=='bridge_east' else 11
  rect(d,start,4,4,1,'steel');rect(d,start,11,4,1,'steel');rect(d,start,5,1,6,'cyan');rect(d,start+3,5,1,6,'cyan');rect(d,start+1,7,2,2,'bright')
 else:
  start=11 if kind=='bridge_up' else 1
  rect(d,4,start,1,4,'steel');rect(d,11,start,1,4,'steel');rect(d,5,start,6,1,'cyan');rect(d,5,start+3,6,1,'cyan');rect(d,7,start+1,2,2,'bright')
 save(kind,im)
for name,col in [('core','cyan'),('me_context','purple')]:
 im,d=image(col);rect(d,0,7,16,1,'bright' if name=='core' else '#bf99e5');save(name,im)
im,d=image((94,201,222,18));rect(d,5,5,6,1,(171,231,241,75));rect(d,5,10,6,1,(171,231,241,60));rect(d,5,6,1,4,(148,214,230,50));save('glass',im)
# 16x16 frames in a 16x256 vertical sheet. Reverse sheet travels in the opposite direction.
for axis in ['u','v']:
 for rev in [False,True]:
  name='stream_'+axis+('_reverse' if rev else '')
  sheet=Image.new('RGBA',(16,256),(0,0,0,0))
  for n in range(16):
   tile=Image.new('RGBA',(16,16),C['teal']);d=ImageDraw.Draw(tile)
   for start in [0]:
    pos=(start+(-n if rev else n))%16
    for k,col in [(0,'bright'),(1,'cyan'),(2,'cyan'),(3,'teal')]:
     a=(pos+k)%16
     if axis=='u':rect(d,a,0,1,16,col)
     else:rect(d,0,a,16,1,col)
   sheet.paste(tile,(0,n*16))
  save(name,sheet)
  (TEX/(name+'.png.mcmeta')).write_text(json.dumps({'animation':{'width':16,'height':16,'frametime':2,'interpolate':False}},indent=2)+'\n')
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
 if mask==0:
  solid=[el([5,5,5],[11,11,11],'cable_idle')];glass=[];streams=[]
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
