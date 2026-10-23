"""Remove local-machine paths from this study's Blender file and diagnostic logs.

Blender stores file-browser state and packed-file provenance in fixed-size NUL-terminated
character fields. Replace only those strings, preserving every byte offset and block size.
Requires the local zstd command; no Python packages are installed.
"""
from pathlib import Path
import subprocess
import struct

DIRECTORY = Path('tools/blender/build/beam-halo')


def main():
    path = DIRECTORY / 'beam-halo.blend'
    raw = subprocess.check_output(['zstd', '-q', '-d', '-c', str(path)])
    private = str(Path.home()).encode()
    count = 0
    while private in raw:
        start = raw.index(private)
        end = raw.index(b'\0', start)
        old = raw[start:end]
        marker = b'/resources/'
        replacement = b'//resources/' + old.split(marker, 1)[1] if marker in old else b'//'
        assert len(replacement) <= len(old)
        raw = raw[:start] + replacement.ljust(len(old), b'\0') + raw[end:]
        count += 1
    assert private not in raw and str(Path.cwd()).encode() not in raw
    packed = subprocess.check_output(['zstd', '-q', '-c'], input=raw)
    path.write_bytes(packed)
    for log in DIRECTORY.glob('*.log'):
        original = log.read_text()
        cleaned = original.replace(str(Path.cwd()), '<worktree>').replace(str(Path.home()), '<user-directory>')
        if original != cleaned:
            log.write_text(cleaned)
    for png in DIRECTORY.rglob('*.png'):
        if 'resources' in png.relative_to(DIRECTORY).parts:
            continue
        original = png.read_bytes()
        cleaned, at = original[:8], 8
        while at < len(original):
            length = struct.unpack('>I', original[at:at+4])[0]
            if original[at+4:at+8] not in (b'tEXt', b'zTXt', b'iTXt'):
                cleaned += original[at:at+length+12]
            at += length + 12
        if cleaned != original:
            png.write_bytes(cleaned)
    print(f'BEAM_HALO_PRIVACY_OK: {count} fixed-size path fields sanitized; byte offsets preserved')


if __name__ == '__main__':
    main()
