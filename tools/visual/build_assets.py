"""Write the Federation Cable's placeholder models and blockstate.
The cable's geometry is AE2's dense cable, built in code by FederationCableBuilder; the block and item models here
only give it display transforms and a particle texture. Every texture, the cable's and its flow renderer's included,
is drawn by the artist; this script leaves them alone.
"""
from pathlib import Path
import json
import argparse
REPO = Path(__file__).resolve().parents[2]
ROOT = REPO / 'common/src/main/resources'
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=ROOT)
ROOT = parser.parse_args().output
NS = 'ae2federation'
BASE = ROOT / 'assets' / NS
MODELS = BASE / 'models/block'
MODELS.mkdir(parents=True, exist_ok=True)

item=BASE/'models'/'item';item.mkdir(exist_ok=True)
(item/'cable.json').write_text(json.dumps({'parent':NS+':block/cable'},indent=2)+'\n')
(MODELS/'cable.json').write_text(json.dumps({'parent':'minecraft:block/block','textures':{'particle':NS+':block/armor'}},indent=2)+'\n')
states=BASE/'blockstates';states.mkdir(exist_ok=True)
(states/'cable.json').write_text(json.dumps({'variants':{'':{'model':NS+':block/cable'}}},indent=2)+'\n')
print('Generated Federation Cable assets for ae2federation (placeholder models; geometry is built in code).')
