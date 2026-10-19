"""Compose labelled sheets, GIFs and movies of the 1x1 cable beam concepts with local ffmpeg."""
import argparse
import json
from pathlib import Path

from compose_beam_comparison import run
from compose_plasma_beam import PERCENT, label, sheet


def main(directory):
    report = json.loads((directory / 'cable-beams-report.json').read_text())
    profiles = {k: v['title'] for k, v in report['profiles'].items()}
    for view in ('level', 'close'):
        for load in ('medium', 'overload'):
            sheet(directory, [(f'{p}-{load}-{view}', f'{t} {PERCENT[load]}%') for p, t in profiles.items()], 'v',
                  '1400:-2', f'sheet-concepts-{load}-{view}.png')
    for profile, title in profiles.items():
        sheet(directory, [(f'{profile}-{load}-level', f'{title} {PERCENT[load]}%') for load in PERCENT], 'v',
              '1400:-2', f'sheet-{profile}-loads.png')
    loops = [loop['profile'] for loop in report['loops']]
    for profile in loops:
        frames = str(directory / profile / 'frames/%03d.png')
        caption = label(f'{profiles[profile]} 60%')
        run(['-framerate', '10', '-i', frames, '-filter_complex', f'[0:v]{caption},split[a][b];'
             '[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a', '-loop', '0',
             str(directory / f'{profile}-loop.gif')])
    if len(loops) == 4:
        # All four side by side in one loop, two by two, so they can be compared while they move.
        inputs, filters = [], []
        for i, profile in enumerate(loops):
            inputs += ['-framerate', '10', '-i', str(directory / profile / 'frames/%03d.png')]
            filters.append(f'[{i}:v]{label(profiles[profile] + " 60%", "720:-2")}[v{i}]')
        filters.append('[v0][v1][v2][v3]xstack=inputs=4:layout=0_0|w0_0|0_h0|w0_h0,split[a][b];'
                       '[a]palettegen=max_colors=224[p];[b][p]paletteuse=dither=sierra2_4a')
        run([*inputs, '-filter_complex', ';'.join(filters), '-loop', '0', str(directory / 'all-concepts-loop.gif')])
        filters[-1] = '[v0][v1][v2][v3]xstack=inputs=4:layout=0_0|w0_0|0_h0|w0_h0,loop=loop=3:size=32:start=0'
        run([*inputs, '-filter_complex', ';'.join(filters), '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p',
             '-movflags', '+faststart', str(directory / 'all-concepts-loop-x4.mp4')])
    print('CABLE_BEAM_MEDIA_OK:', directory)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path,
                        default=Path(__file__).resolve().parents[2] / 'build/cable-beam-concepts')
    main(parser.parse_args().directory)
