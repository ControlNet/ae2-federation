"""Verify complete study delivery, animation timing, source preservation and labelled media."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zlib

from beam_halo import CONCEPTS

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / 'tools/blender/build/beam-halo'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pixels(path):
    raw = path.read_bytes()
    at, chunks = 8, []
    while at < len(raw):
        length = struct.unpack('>I', raw[at:at+4])[0]
        if raw[at+4:at+8] == b'IDAT':
            chunks.append(raw[at+8:at+8+length])
        at += length + 12
    return hashlib.sha256(zlib.decompress(b''.join(chunks))).hexdigest()


def main():
    report_path = DIRECTORY / 'report-BASE.json'
    if not report_path.exists():
        report_path = DIRECTORY / 'report.json'
    report = json.loads(report_path.read_text())
    for relative, expected in report['source_hashes'].items():
        assert digest(ROOT / relative) == expected, relative
    stills = movies = gifs = frames = 0
    for key in CONCEPTS:
        folder = DIRECTORY / key
        for load in ('idle', 'medium', 'full', 'overload', 'unlinked'):
            for view in ('level', 'oblique', 'close', 'pair'):
                for light in ('day', 'dark'):
                    path = folder / 'labelled' / f'{load}-{view}-{light}.png'
                    assert struct.unpack('>II', path.read_bytes()[16:24]) == (960, 778), path.name
                    stills += 1
        for load in ('idle', 'medium', 'full'):
            samples = [folder / load / f'{i:03d}.png' for i in range(32)]
            assert all(p.is_file() for p in samples)
            assert all(struct.unpack('>II', p.read_bytes()[16:24]) == (960,720) for p in samples)
            assert len({pixels(p) for p in samples}) > 8, (key, load, 'frozen loop')
            frames += len(samples)
            movie = folder / f'{load}-loop.mp4'
            probe = json.loads(subprocess.check_output([
                'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
                'stream=nb_frames,r_frame_rate:format=duration', '-of', 'json', str(movie)]))
            assert probe['streams'][0]['nb_frames'] == '32'
            assert probe['streams'][0]['r_frame_rate'] == '10/1'
            assert abs(float(probe['format']['duration']) - 3.2) < .01
            movies += 1
            gif = folder / f'{load}-loop.gif'
            raw = gif.read_bytes()
            assert raw[:6] == b'GIF89a'
            assert struct.unpack('<HH',raw[6:10]) == (960,778)
            gif_probe = json.loads(subprocess.check_output([
                'ffprobe','-v','error','-count_frames','-select_streams','v:0','-show_entries',
                'stream=nb_read_frames:format=duration','-of','json',str(gif)]))
            assert gif_probe['streams'][0]['nb_read_frames'] == '32'
            assert abs(float(gif_probe['format']['duration'])-3.2) < .01
            gifs += 1
    for key in CONCEPTS:
        if key != 'BASE':
            assert pixels(DIRECTORY / key / 'medium-level-day.png') != pixels(DIRECTORY / 'BASE/medium-level-day.png')
        for view in ('level', 'oblique', 'close', 'pair'):
            for light in ('day', 'dark'):
                name = f'unlinked-{view}-{light}.png'
                assert pixels(DIRECTORY / key / name) == pixels(DIRECTORY / 'BASE' / name), (key, name, 'camera or lighting mismatch')
    for name in ('contact-level-day.png', 'contact-level-dark.png', 'contact-close-day.png',
                 'contact-pair-day.png', 'contact-oblique-day.png', 'calibration.png', 'recommended-states.png', 'all-concepts-idle.mp4',
                 'all-concepts-medium.mp4', 'all-concepts-full.mp4', 'beam-halo.blend'):
        assert (DIRECTORY / name).stat().st_size > 1000, name
    for view in ('level', 'oblique', 'close', 'pair'):
        for light in ('day', 'dark'):
            sheet = DIRECTORY / f'contact-{view}-{light}.png'
            assert struct.unpack('>II', sheet.read_bytes()[16:24]) == (2898, 2352)
    for load in ('idle', 'medium', 'full'):
        movie = DIRECTORY / f'all-concepts-{load}.mp4'
        probe = json.loads(subprocess.check_output([
            'ffprobe', '-v', 'error', '-select_streams', 'v:0', '-show_entries',
            'stream=nb_frames,r_frame_rate:format=duration', '-of', 'json', str(movie)]))
        assert probe['streams'][0]['nb_frames'] == '32'
        assert probe['streams'][0]['r_frame_rate'] == '10/1'
        assert abs(float(probe['format']['duration']) - 3.2) < .01
    assert not any((DIRECTORY / key).exists() for key in ('A', 'C', 'E'))
    assert not list(DIRECTORY.rglob('*eye*.png'))
    result = {'labelled_stills': stills, 'animation_frames': frames, 'concept_mp4': movies,
              'concept_gif': gifs, 'source_hashes_unchanged': True, 'timing': '32 frames at 10 fps / 3.2 seconds'}
    (DIRECTORY / 'verification.json').write_text(json.dumps(result, indent=2) + '\n')
    print('BEAM_HALO_VERIFY_OK', json.dumps(result))


if __name__ == '__main__':
    main()
