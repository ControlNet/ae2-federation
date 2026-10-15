"""Compose labeled contact sheets and synchronized movies from actual future-beam renders."""
import argparse
import json
from pathlib import Path

from compose_beam_comparison import run


def grid(profiles,width):
    filters=[]
    for i,profile in enumerate(profiles.values()):
        title=profile['title']
        if any(c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ /0123456789%' for c in title):raise ValueError(title)
        filters.append(f"[{i}:v]scale={width}:-2,drawtext=text='{title}':expansion=none:fontcolor=white:fontsize=22:x=18:y=14[v{i}]")
    count=len(profiles)//2
    for row in range(count):filters.append(f'[v{row*2}][v{row*2+1}]hstack=inputs=2[r{row}]')
    filters.append(''.join(f'[r{i}]' for i in range(count))+f'vstack=inputs={count}[out]')
    return ';'.join(filters)


def main(directory):
    report=json.loads((directory/'future-report.json').read_text()); profiles=report['profiles']
    sheets=[
        ('close','all-designs.png',profiles,800),
        ('wide','all-designs-wide.png',profiles,1000),
    ]
    if len(profiles)==8:
        sheets += [('close','designs-e-h.png',dict(list(profiles.items())[:4]),800),
                   ('close','designs-i-l.png',dict(list(profiles.items())[4:]),800)]
    for suffix,name,selection,width in sheets:
        inputs=[]
        for key in selection:inputs+=['-i',str(directory/f'{key}-{suffix}.png')]
        run([*inputs,'-filter_complex',grid(selection,width),'-map','[out]','-frames:v','1',str(directory/name)])
    if report['animation_frames']:
        inputs=[]
        for key in profiles:inputs+=['-framerate','10','-i',str(directory/key/'frames/%03d.png')]
        run([*inputs,'-filter_complex',grid(profiles,720),'-map','[out]','-c:v','libx264','-crf','18',
             '-pix_fmt','yuv420p','-movflags','+faststart',str(directory/'all-designs.mp4')])
        run(['-i',str(directory/'all-designs.mp4'),'-filter_complex',
             '[0:v]scale=900:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=160[p];[b][p]paletteuse=dither=sierra2_4a',
             '-loop','0',str(directory/'all-designs.gif')])
    print('FUTURE_MEDIA_OK:',directory)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,default=Path(__file__).resolve().parent/'build/future-beams')
    main(parser.parse_args().directory)
