"""Copy the Federation Cable's approved Blockbench V07 textures and write its placeholder models.
The cable's geometry is AE2's dense cable, built in code by FederationCableBuilder; the block and item models here
only give it display transforms and a particle texture. The Router, Pattern Provider,
Processing Endpoint and Bridge models and textures are drawn by hand in Blockbench; this script leaves them alone.
"""
from pathlib import Path
import json
import argparse
import hashlib
import shutil
from cable_flow_texture import write_texture
REPO = Path(__file__).resolve().parents[2]
APPROVED = REPO / 'tools/blockbench/versions/v07-isolated-cable'
ROOT = REPO / 'common/src/main/resources'
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT)
ROOT = parser.parse_args().output
NS = 'ae2federation'
BASE = ROOT / 'assets' / NS
MODELS = BASE / 'models/block'
TEX = BASE / 'textures/block'
MODELS.mkdir(parents=True, exist_ok=True)
TEX.mkdir(parents=True, exist_ok=True)
write_texture(BASE / 'textures/entity/cable_flow.png')
# The approved native Blockbench snapshot is the cable's texture authority. Its textures stay in use only until the
# artist's dense cable textures replace them (TEMPORARY).
CABLE_TEXTURES = {'armor.png', 'collar.png', 'glass.png', 'stream_u.png', 'stream_u.png.mcmeta',
                  'stream_v.png', 'stream_v.png.mcmeta'}
manifest = json.loads((APPROVED / 'manifest.json').read_text())
for source in sorted((APPROVED / 'textures').iterdir()):
    name = source.relative_to(APPROVED).as_posix()
    if hashlib.sha256(source.read_bytes()).hexdigest() != manifest['files'][name]:
        raise ValueError(f'Approved texture changed: {name}; create a new version')
    if source.name in CABLE_TEXTURES:
        shutil.copyfile(source, TEX / source.name)

item=BASE/'models'/'item';item.mkdir(exist_ok=True)
(item/'cable.json').write_text(json.dumps({'parent':NS+':block/cable'},indent=2)+'\n')
(MODELS/'cable.json').write_text(json.dumps({'parent':'minecraft:block/block','textures':{'particle':NS+':block/armor'}},indent=2)+'\n')
states=BASE/'blockstates';states.mkdir(exist_ok=True)
(states/'cable.json').write_text(json.dumps({'variants':{'':{'model':NS+':block/cable'}}},indent=2)+'\n')
print('Generated Federation Cable assets for ae2federation (placeholder models; geometry is built in code).')
