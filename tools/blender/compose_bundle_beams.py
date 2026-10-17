"""Compose labelled 3x2 sheets of the 2x2 middle-beam concept renders with local ffmpeg."""
import argparse
import json
from pathlib import Path
import struct

from compose_beam_comparison import run

CAPTION = set('ABCDEFGHIJKLMNOPQRSTUVWXYZ /0123456789%')


def png_size(path):
    width, height = struct.unpack('>II', path.read_bytes()[16:24])
    return width, height


def grid(titles, width, cell, columns=3, seconds=1):
    """Labelled tiles in rows of `columns`; a short last row is filled with blank tiles `cell` pixels in size."""
    filters = []
    for i, title in enumerate(titles):
        if set(title) - CAPTION:
            raise ValueError(title)
        filters.append(f"[{i}:v]scale={width}:-2,drawtext=text='{title}':expansion=none:fontcolor=white:fontsize=24:"
                       f"x=18:y=14,pad=iw+6:ih+6:3:3:color=0x10141c[v{i}]")
    rows = (len(titles) + columns - 1) // columns
    for i in range(len(titles), rows * columns):
        filters.append(f'color=c=0x10141c:s={cell[0]}x{cell[1]}:r=10:d={seconds}[v{i}]')
    for row in range(rows):
        cells = ''.join(f'[v{i}]' for i in range(row * columns, (row + 1) * columns))
        filters.append(f'{cells}hstack=inputs={columns}[r{row}]')
    filters.append(''.join(f'[r{i}]' for i in range(rows)) + f'vstack=inputs={rows}[out]')
    return ';'.join(filters)


def main(directory):
    report = json.loads((directory / 'bundle-report.json').read_text())
    profiles = report['profiles']
    percent = {name: round(load * 100) for name, load in report['loads'].items()}
    for view, spec in report['views'].items():
        for load in spec['loads']:
            inputs, titles = [], []
            for key, profile in profiles.items():
                inputs += ['-i', str(directory / f'{key}-{load}-{view}.png')]
                titles.append(f"{profile['title']} {percent[load]}%")
            width = 640 if view != 'end' else 520
            w, h = png_size(directory / f'{key}-{load}-{view}.png')
            cell = (width + 6, round(h * width / w / 2) * 2 + 6)
            run([*inputs, '-filter_complex', grid(titles, width, cell), '-map', '[out]', '-frames:v', '1',
                 str(directory / f'sheet-{view}-{load}.png')])
    frames = report.get('animation_frames', 0)
    if frames:
        inputs, titles = [], []
        for key, profile in profiles.items():
            inputs += ['-framerate', '10', '-i', str(directory / key / 'frames/%03d.png')]
            titles.append(f"{profile['title']} {percent['medium']}%")
            # Each concept on its own, larger, for a close look.
            run(['-framerate', '10', '-i', str(directory / key / 'frames/%03d.png'), '-filter_complex',
                 '[0:v]split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a',
                 '-loop', '0', str(directory / f'{key}-loop.gif')])
        w, h = png_size(directory / key / 'frames/000.png')
        cell = (480 + 6, round(h * 480 / w / 2) * 2 + 6)
        movie = directory / 'all-concepts.mp4'
        run([*inputs, '-filter_complex', grid(titles, 480, cell, seconds=frames / 10), '-map', '[out]',
             '-frames:v', str(frames), '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p',
             '-movflags', '+faststart', str(movie)])
        # Loop the movie a few times so a short clip is easy to watch.
        run(['-stream_loop', '3', '-i', str(movie), '-c', 'copy', str(directory / 'all-concepts-x4.mp4')])
        run(['-i', str(movie), '-filter_complex',
             '[0:v]scale=1200:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a',
             '-loop', '0', str(directory / 'all-concepts.gif')])
    print('BUNDLE_MEDIA_OK:', directory)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parents[2] / 'build/bundle-beam-check')
    main(parser.parse_args().directory)
