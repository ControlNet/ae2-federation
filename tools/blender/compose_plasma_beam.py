"""Compose labelled sheets, GIFs and movies of the R / PLASMA study with local ffmpeg."""
import argparse
import json
from pathlib import Path

from compose_beam_comparison import run
from compose_bundle_beams import CAPTION

PERCENT = {'idle': 0, 'medium': 60, 'full': 100, 'overload': 125}
SCENE_TITLES = {'straight': 'RUN', 'branch': 'BRANCH', 'turn': 'TURN'}


def label(title, scale=None):
    """A filter chain that scales (an ffmpeg `w:h`, if given), captions and frames one picture."""
    if set(title) - CAPTION:
        raise ValueError(title)
    return ((f'scale={scale},' if scale else '') + f"drawtext=text='{title}':expansion=none:fontcolor=white:"
            f"fontsize=30:x=20:y=16,pad=iw+6:ih+6:3:3:color=0x10141c")


def sheet(directory, cells, stack, scale, name):
    """`cells` are (file stem, title); `stack` is 'v' or 'h'."""
    inputs, filters = [], []
    for i, (stem, title) in enumerate(cells):
        inputs += ['-i', str(directory / (stem + '.png'))]
        filters.append(f'[{i}:v]{label(title, scale)}[v{i}]')
    joined = ''.join(f'[v{i}]' for i in range(len(cells)))
    filters.append(f'{joined}{stack}stack=inputs={len(cells)}[out]')
    run([*inputs, '-filter_complex', ';'.join(filters), '-map', '[out]', '-frames:v', '1', str(directory / name)])


def main(directory):
    report = json.loads((directory / 'plasma-report.json').read_text())
    titles = {k: v['title'] for k, v in report['variants'].items()}

    def title(scene, variant, load):
        return f'{titles[variant]} {SCENE_TITLES[scene]} {PERCENT[load]}%'
    sheet(directory, [(f'straight-{v}-medium', title('straight', v, 'medium'))
                      for v in ('approved', 'ship_soft', 'ship_six')], 'v', '1400:-2', 'sheet-approved-vs-shippable.png')
    sheet(directory, [(f'straight-ship_soft-{load}', title('straight', 'ship_soft', load)) for load in PERCENT],
          'v', '1400:-2', 'sheet-shippable-loads.png')
    sheet(directory, [('branch-ship_soft-medium', title('branch', 'ship_soft', 'medium')),
                      ('branch-ship_soft-overload', title('branch', 'ship_soft', 'overload')),
                      ('turn-ship_soft-medium', title('turn', 'ship_soft', 'medium'))], 'h', '-2:900',
          'sheet-junctions.png')
    for loop in report['loops']:
        stem = f"{loop['scene']}-{loop['variant']}-{loop['load']}"
        frames = str(directory / stem / 'frames/%03d.png')
        caption = label(title(loop['scene'], loop['variant'], loop['load']))
        run(['-framerate', '10', '-i', frames, '-filter_complex', f'[0:v]{caption},split[a][b];'
             '[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a', '-loop', '0',
             str(directory / f'{stem}-loop.gif')])
        # Four times round, so a 3.2 s loop is easy to watch.
        run(['-framerate', '10', '-i', frames, '-vf', f"loop=loop=3:size={loop['frames']}:start=0,{caption}",
             '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
             str(directory / f'{stem}-loop-x4.mp4')])
    print('PLASMA_MEDIA_OK:', directory)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path,
                        default=Path(__file__).resolve().parents[2] / 'build/bundle-beam-check/plasma')
    main(parser.parse_args().directory)
