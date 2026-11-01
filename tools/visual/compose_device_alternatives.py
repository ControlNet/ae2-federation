"""Label Blender alternatives and connection comparisons; run in the visual pixi environment."""
import argparse
from pathlib import Path
from compose_device_study import sheet

LASERS={'L1':('SPLIT RAIL','Open rails / three collimation stages'),
        'L2':('OCTAGONAL BARREL','Stepped barrel / projecting laser muzzle'),
        'L3':('CRYSTAL RESONATOR','Glass cavity / visible resonator crystal'),
        'L4':('REFLECTOR FOCUS','Broad reflector / exposed focus / exit lens')}
QUANTUM={'Q1':('OPEN EDGE FRAME','No face panels or central cross supports'),
         'Q2':('GLASS CONTAINMENT','Slim edge frame / original cable glass'),
         'Q3':('CORNER GLASS','Corner anchors / no complete edge rails')}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('directory',type=Path)
    root=parser.parse_args().directory
    sheet(root,'laser-options.png','BEAM CONVERTER / LASER EMITTER OPTIONS',
          'Four distinct optical structures. Same 2x2x4 envelope, camera and original cable palette.',
          [(f'{k}-hero.png',f'{k} / {v[0]}',v[1]) for k,v in LASERS.items()],2,600)
    sheet(root,'quantum-options.png','QUANTUM CUBE / OPEN + GLASS OPTIONS',
          'Same 4x4x4 body. No central face cages. Glass samples the current cable texture, including its alpha.',
          [(f'{k}-hero.png',f'{k} / {v[0]}',v[1]) for k,v in QUANTUM.items()],3,500)
    for family,options in (('laser',LASERS),('quantum',QUANTUM)):
        sheet(root,f'{family}-connections.png',f'{family.upper()} / ORDINARY + BUNDLED CABLES',
              'Columns: ordinary 1x1, bundle 2x2, bundle 3x3. Device scale follows each actual supported size.',
              [(f'{k}-cable-{n}.png',f'{k} / {n}x{n} CABLE',
                'Ordinary Java shell / 10-pixel device arm' if n==1 else 'Original artist ring / bundle shell texel layout')
               for k in options for n in (1,2,3)],3,440)
    sheet(root,'connection-highlights.png','MATERIAL + INTERFACE / CONNECTION STUDY',
          'Current cable textures and original bundle rings. Shared core radii continue across each device interface.',
          [(f'{k}-cable-{n}.png',f'{k} / {n}x{n} CABLE',
            'Ordinary Federation cable' if n==1 else f'{n}x{n} Federation bundle')
           for k in ('L3','Q1') for n in (1,2,3)],3,500)
    print('DEVICE_ALTERNATIVE_SHEETS_OK',root)


if __name__=='__main__':main()
