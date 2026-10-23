"""Label study renders and compose synchronized review media using local ffmpeg."""
from pathlib import Path
import argparse
from compose_beam_comparison import run as ffmpeg_run
from compose_bundle_beams import grid
from beam_halo import CONCEPTS

DIRECTORY = Path('tools/blender/build/beam-halo')


def run(arguments):
    """Bound ffmpeg threads while independent Cycles workers are active."""
    ffmpeg_run(['-filter_threads', '1', *arguments[:-1], '-threads', '2', arguments[-1]])


def label(title):
    return (f"pad=iw:ih+58:0:28:color=0x10141c,drawtext=text='{title}':expansion=none:"
            "fontcolor=white:fontsize=16:x=12:y=6,drawtext=text='BLENDER BLENDING APPROXIMATION / PROPOSED HALO':"
            "expansion=none:fontcolor=0xa9bacd:fontsize=11:x=10:y=h-21")


def main(args):
    concepts = {key: CONCEPTS[key] for key in (args.only or CONCEPTS)}
    suffix = args.suffix or ('-' + '-'.join(concepts) if args.only else '')
    if suffix and (not suffix.startswith('-') or any(c not in '-_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789' for c in suffix)):
        raise ValueError('Suffix must start with a hyphen and contain only letters, digits, hyphens or underscores')
    for key, title in concepts.items():
        directory = DIRECTORY / key
        for path in sorted(directory.glob('*-day.png')) + sorted(directory.glob('*-dark.png')):
            target = directory / ('labelled' + suffix) / path.name
            target.parent.mkdir(exist_ok=True)
            run(['-i', str(path), '-vf', label(f'{key} / {title} / {path.stem.upper()}'),
                 '-frames:v', '1', str(target)])
        for load in (() if args.stills_only else ('idle', 'medium', 'full')):
            movie = directory / f'{load}-loop{suffix}.mp4'
            run(['-framerate', '10', '-i', str(directory / load / '%03d.png'),
                 '-vf', label(f'{key} / {title} / {load.upper()}'), '-frames:v', '32',
                 '-c:v', 'libx264', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(movie)])
            run(['-i', str(movie), '-filter_complex',
                 '[0:v]split[a][b];[a]palettegen=max_colors=192[p];[b][p]paletteuse=dither=sierra2_4a',
                 '-loop', '0', str(directory / f'{load}-loop{suffix}.gif')])
    for view, light in ((v, l) for v in ('level', 'oblique', 'close', 'pair') for l in ('day', 'dark')):
        inputs = []
        for key in concepts:
            inputs += ['-i', str(DIRECTORY / key / ('labelled' + suffix) / f'medium-{view}-{light}.png')]
        run([*inputs, '-filter_complex', grid(['']*len(concepts), 960, (966, 784), columns=3),
             '-map', '[out]', '-frames:v', '1', str(DIRECTORY / f'contact-{view}-{light}{suffix}.png')])
    for load in (() if args.stills_only else ('idle', 'medium', 'full')):
        inputs = []
        for key in concepts:
            inputs += ['-i', str(DIRECTORY / key / f'{load}-loop{suffix}.mp4')]
        run([*inputs, '-filter_complex', grid(['']*len(concepts), 960, (966, 784), columns=3, seconds=3.2),
             '-map', '[out]', '-frames:v', '32', '-r', '10', '-c:v', 'libx264', '-crf', '18',
             '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(DIRECTORY / f'all-concepts-{load}{suffix}.mp4')])
    # Recommended concepts: overload and unlinked comparisons are explicit, not simulated blue states.
    inputs, titles = [], []
    for key in (key for key in ('G', 'H', 'J', 'J2') if key in concepts):
        for load in ('medium', 'overload', 'unlinked'):
            inputs += ['-i', str(DIRECTORY / key / ('labelled' + suffix) / f'{load}-close-day.png')]
            titles.append('')
    if inputs:
        run([*inputs, '-filter_complex', grid(titles, 960, (966, 784), columns=3),
         '-map', '[out]', '-frames:v', '1', str(DIRECTORY / f'recommended-states{suffix}.png')])
    # Preserve the client's pixels: scale/letterbox only, with provenance distinct from Blender.
    if args.reference is None or 'BASE' not in concepts:
        print('BEAM_HALO_MEDIA_OK: selected concepts ' + ', '.join(concepts))
        return
    reference = Path(args.reference)
    calibration = DIRECTORY / f'calibration-reference{suffix}.png'
    run(['-i', str(reference), '-vf',
         "scale=960:720:force_original_aspect_ratio=decrease,pad=960:720:(ow-iw)/2:(oh-ih)/2:color=0x10141c," +
         label('REAL CLIENT / close-2x2.png').replace('BLENDER BLENDING APPROXIMATION / PROPOSED HALO',
         'REAL IN-GAME REFERENCE / UNMODIFIED COLOUR'), '-frames:v', '1', str(calibration)])
    run(['-i', str(calibration), '-i', str(DIRECTORY / 'BASE' / ('labelled' + suffix) / 'medium-level-day.png'),
         '-filter_complex', '[0:v][1:v]hstack=inputs=2[out]', '-map', '[out]', '-frames:v', '1',
         str(DIRECTORY / f'calibration{suffix}.png')])
    print('BEAM_HALO_MEDIA_OK: selected concepts ' + ', '.join(concepts))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--stills-only', action='store_true')
    parser.add_argument('--only', nargs='+', choices=tuple(CONCEPTS))
    parser.add_argument('--suffix', default='', help='Output suffix, e.g. --suffix=-portal')
    parser.add_argument('--reference', type=Path)
    main(parser.parse_args())
