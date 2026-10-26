"""Author complete pixel textures for refined devices in the approved visual environment.

Structured, deterministic pixels: seams, insets, edge highlights and functional motifs.
No random noise, baked studio lighting or constant-texel material replacement.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/blender'))
from refined_device_geometry import texture_requirements

SOURCE=ROOT/'common/src/main/resources/assets/ae2federation/textures'
COLLAR=SOURCE/'part/cable/dense/collar.png'
CORE=SOURCE/'part/cable/dense/core.png'
RING=SOURCE/'block/large_cable/large_cable.png'


def colors():
    a=Image.open(COLLAR).convert('RGB');b=Image.open(CORE).convert('RGB')
    c=Image.open(RING).convert('RGB')
    return {'ice':a.getpixel((4,6)), 'teal':a.getpixel((3,6)), 'teal_dark':a.getpixel((3,11)),
            'white':b.getpixel((4,6)), 'silver':b.getpixel((2,6)), 'dark':b.getpixel((3,6)),
            'steel':c.getpixel((6,14)), 'pale':(179,182,198), 'deep':(47,48,65),
            'abyss':tuple(round(x*.32) for x in a.getpixel((3,11))),
            'deep_teal':tuple(round(x*.55) for x in a.getpixel((3,11)))}


def paint(role,w,h,p):
    # Long details run along the longer face dimension. Rotate back for the actual UV rectangle.
    rotate=w>h
    W,H=(h,w) if rotate else (w,h)
    base={'rail':'silver','ceramic':'pale','collar':'teal_dark','metal':'steel','engine':'deep',
          'terminal':'dark','gain':'deep_teal','shroud':'pale','anchor':'steel',
          'shard':'deep_teal','shard_inner':'teal_dark','core':'abyss'}[role]
    im=Image.new('RGBA',(W,H),(*p[base],255));d=ImageDraw.Draw(im)
    mask=Image.new('L',(W,H),0);m=ImageDraw.Draw(mask)
    def rect(bounds,c):
        x0,y0,x1,y1=bounds
        if x1>=x0 and y1>=y0:d.rectangle(bounds,fill=(*p[c],255))
    def frame(x0,y0,x1,y1,light='white',shadow='dark'):
        if x1<x0 or y1<y0:return
        d.line([(x0,y1),(x0,y0),(x1,y0)],fill=(*p[light],255))
        d.line([(x1,y0+1),(x1,y1),(x0,y1)],fill=(*p[shadow],255))
    def lit(bounds,c='teal',strength=110):
        if bounds[2]>=bounds[0] and bounds[3]>=bounds[1]:
            rect(bounds,c);m.rectangle(bounds,fill=strength)
    if role in ('rail','ceramic','shroud'):
        frame(0,0,W-1,H-1)
        if W>=4:
            rect((1,1,1,H-2),'silver')
            rect((W-2,2,W-2,H-3),'steel')
        if role=='rail':
            if W>=4:rect((W//2,2,W//2,H-3),'dark')
            for y in range(5,H-2,12):
                rect((1,y,W-2,y+1),'deep')
                lit((max(1,W//2-1),y,min(W-2,W//2),y),'teal',80)
                rect((1,y+2,W-2,y+2),'white')
        else:
            for y in range(5,H-3,12):
                end=min(H-3,y+7)
                if W>=7:
                    rect((2,y,W-3,end),'steel');frame(2,y,W-3,end,'silver','dark')
                    rect((3,y+2,W-4,end-1),'pale')
                    d.line([(3,end-1),(3,y+2),(max(3,W//2),y+2)],fill=(*p['dark'],255))
                    if role=='shroud':lit((W-4,y+2,W-4,end-2),'teal',80)
                else:rect((1,y,W-2,y),'steel')
            if W>=10 and H>=10:
                rect((W-4,2,W-3,4),'deep');rect((W-3,2,W-3,3),'white')
    elif role in ('collar','metal'):
        frame(0,0,W-1,H-1,'ice' if role=='collar' else 'pale','deep')
        if W>=4 and H>=4:
            rect((1,1,W-2,H-2),'teal' if role=='collar' else 'steel')
            rect((W-2,2,W-2,H-2),'teal_dark' if role=='collar' else 'dark')
        for y in range(3,H-1,6):
            rect((1,y,W-2,y),'teal_dark' if role=='collar' else 'dark')
            rect((1,y+1,max(1,W//2),y+1),'ice' if role=='collar' else 'silver')
        if W>=6:rect((W//2,2,W//2,H-3),'teal_dark' if role=='collar' else 'deep')
    elif role in ('terminal','anchor','engine'):
        frame(0,0,W-1,H-1,'pale','deep')
        if W>=6 and H>=6:frame(2,2,W-3,H-3,'steel','deep')
        # A central optical/energy glyph, plus short paths instead of a flat colour field.
        if min(W,H)>=6:
            cx,cy=W//2,H//2;r=max(1,min(W,H)//4)
            rect((cx-r,cy-r,cx+r,cy+r),'teal_dark')
            frame(cx-r,cy-r,cx+r,cy+r,'ice','deep_teal')
            lit((cx-r+1,cy-r+1,cx+r-1,cy+r-1),'teal',60)
            rect((cx,cy,cx,cy),'ice')
            for sign in (-1,1):
                y=cy+sign*(r+2)
                if 0<y<H-1:d.line([(cx,y),(cx+sign*max(1,r-1),y)],fill=(*p['steel'],255))
        if role=='engine':
            for y in range(5,H-4,8):
                for x in (3,W-6):
                    rect((x,y,x+2,y+3),'steel');rect((x+1,y+1,x+2,y+2),'deep')
            for x in range(5,W-4,8):
                rect((x,3,x+2,3),'pale');rect((x,H-5,x+2,H-5),'dark')
    elif role=='gain':
        frame(0,0,W-1,H-1,'teal','deep_teal')
        for y in range(1,H-1):
            width=1 if y%8<3 else max(1,W//3)
            lit((max(0,W//2-width),y,min(W-1,W//2+width),y),'teal' if y%8<6 else 'ice',95)
            if y%8==0:rect((0,y,W-1,y),'teal_dark')
    elif role in ('shard','shard_inner','core'):
        # A low-light plate with stepped strata and a sparse fracture path.
        for y in range(H):
            offset=(y//3)%3
            rect((offset,y,min(W-1,offset+2),y),'abyss')
            rect((max(0,W-4-offset),y,W-2,y),'teal_dark' if role!='core' else 'deep_teal')
        frame(0,0,W-1,H-1,'teal_dark','abyss')
        for y in range(2,H-2):
            x=W//2+((y//3)%3)-1
            lit((x,y,x,y),'teal' if y%4 else 'ice',100 if role=='shard' else 145)
            if y%4==0:lit((x-1,y,min(W-2,x+2),y),'teal',90)
        if role=='shard_inner':
            lit((2,1,W-3,1),'ice',125)
    if rotate:
        im=im.transpose(Image.Transpose.ROTATE_90);mask=mask.transpose(Image.Transpose.ROTATE_90)
    assert im.size==(w,h)
    return im,mask


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('output',type=Path)
    args=parser.parse_args();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
    if (out/'textures.json').exists():raise FileExistsError('Choose a fresh texture output directory')
    p=colors();textures={}
    for role,w,h in texture_requirements():
        name=f'{role}-{w}x{h}';im,mask=paint(role,w,h,p)
        im.save(out/(name+'.png'));mask.save(out/(name+'-emission.png'))
        textures[name]={'role':role,'width':w,'height':h,'sha256':hashlib.sha256((out/(name+'.png')).read_bytes()).hexdigest()}
    (out/'textures.json').write_text(json.dumps({'palette':p,'textures':textures,
        'source_sha256':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (COLLAR,CORE,RING)},
        'texel_density':'One texture pixel per model voxel on mechanical faces'},indent=2)+'\n')
    # A readable inspection sheet, not used as a model texture.
    selected=[('rail',4,52),('anchor',8,8),('engine',24,24),('ceramic',6,20),('collar',6,4),
              ('gain',6,46),('shroud',18,8),('shard',12,12),('core',16,16)]
    sheet=Image.new('RGB',(1080,720),'#202735');draw=ImageDraw.Draw(sheet)
    for i,(role,w,h) in enumerate(selected):
        im,_=paint(role,w,h,p);factor=min(300//w,195//h)
        x=(i%3)*360;y=(i//3)*240
        sheet.paste(im.resize((w*factor,h*factor),Image.Resampling.NEAREST),(x+20,y+32))
        draw.text((x+20,y+10),f'{role.upper()} / {w} x {h} pixels',fill='white')
    sheet.save(out/'texture-detail-sheet.png')
    print('REFINED_TEXTURES_OK',len(textures),out)


if __name__=='__main__':main()
