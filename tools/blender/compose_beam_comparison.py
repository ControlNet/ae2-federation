"""Assemble labeled comparisons from actual Blender renders using local ffmpeg.

No generative image edits, replacement geometry, or third-party Python packages.
All inputs are the matched-camera renders from beam_variants.py.
"""
import argparse
import json
from pathlib import Path
import subprocess


def run(arguments):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-filter_complex_threads','1','-y',*arguments],check=True)


def labeled_stack(profiles,width):
    filters=[]
    for index,profile in enumerate(profiles.values()):
        title=profile['title']
        if any(c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ /' for c in title):
            raise ValueError('Unexpected label characters')
        filters.append(f"[{index}:v]scale={width}:-2,drawtext=text='{title}':fontcolor=white:fontsize=24:x=24:y=14[v{index}]")
    filters.append(''.join(f'[v{i}]' for i in range(len(profiles)))+f'vstack=inputs={len(profiles)}[out]')
    return ';'.join(filters)


def main(directory):
    report=json.loads((directory/'comparison-report.json').read_text())
    profiles=report['profiles']
    for suffix,name in [('', 'comparison.png'),('-junctions','junction-comparison.png')]:
        inputs=[]
        for key in profiles: inputs+=['-i',str(directory/(key+suffix+'.png'))]
        run([*inputs,'-filter_complex',labeled_stack(profiles,1200),'-map','[out]',
             '-frames:v','1',str(directory/name)])
    if report['animation_frames']:
        inputs=[]
        for key in profiles: inputs+=['-framerate','10','-i',str(directory/key/'frames/%03d.png')]
        run([*inputs,'-filter_complex',labeled_stack(profiles,1000),'-map','[out]',
             '-c:v','libx264','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',str(directory/'comparison.mp4')])
        run(['-i',str(directory/'comparison.mp4'),'-filter_complex',
             '[0:v]scale=700:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=sierra2_4a',
             '-loop','0',str(directory/'comparison.gif')])
    print('COMPARISON_MEDIA_OK:',directory)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,default=Path(__file__).resolve().parent/'build/beam-variants')
    main(parser.parse_args().directory)
