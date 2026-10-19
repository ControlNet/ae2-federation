"""Compare actual M and helical load renders at identical full/overload settings."""
import json
from pathlib import Path

from compose_beam_comparison import run
from compose_future_beams import grid


def main():
    root = Path(__file__).resolve().parent / 'build'
    old, new = root / 'load-beams', root / 'm-load-beams'
    reports = [json.loads((directory / 'future-report.json').read_text()) for directory in (old, new)]
    for field in ('shell_sha256', 'configs', 'frame', 'animation_frames'):
        assert reports[0][field] == reports[1][field], field
    assert all(report['source_assets_unchanged'] for report in reports)
    panels = [(old, 'e_full', 'HELIX / FULL 100%'), (new, 'e_full', 'M / FULL 100%'),
              (old, 'f_overload', 'HELIX / OVERLOAD 125%'), (new, 'f_overload', 'M / OVERLOAD 125%')]
    profiles = {str(i): {'title': title} for i, (_, _, title) in enumerate(panels)}
    inputs = []
    for directory, key, _ in panels:
        inputs += ['-i', str(directory / f'{key}-close.png')]
    run([*inputs, '-filter_complex', grid(profiles, 800), '-map', '[out]', '-frames:v', '1',
         str(new / 'm-vs-helix.png')])
    if reports[0]['animation_frames']:
        inputs = []
        for directory, key, _ in panels:
            inputs += ['-framerate', '10', '-i', str(directory / key / 'frames/%03d.png')]
        run([*inputs, '-filter_complex', grid(profiles, 720), '-map', '[out]', '-c:v', 'libx264',
             '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(new / 'm-vs-helix.mp4')])
    print('LOAD_ALTERNATIVES_OK: matched full/overload settings and original shell')


if __name__ == '__main__':
    main()
