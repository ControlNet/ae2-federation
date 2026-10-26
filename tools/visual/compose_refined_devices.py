"""Compose the refined Blender device renders and native fragment animation."""
import argparse
from pathlib import Path
from PIL import Image
from compose_device_study import sheet


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('directory',type=Path)
    root=parser.parse_args().directory
    sheet(root,'laser-refinement.png','FEDERATION LASER / FULL PIXEL TEXTURES',
          'Complete face UVs at 16 pixels per block. Source teal, stepped shading, seams and optical details.',[
              ('laser-open.png','A / OPEN COLLIMATOR','Exposed focusing rings and textured gain chamber'),
              ('laser-shroud.png','B / SEGMENTED SHROUD','Directional shutters over the same optical structure')],2,600)
    sheet(root,'quantum-refinement.png','QUANTUM CUBE / LAYERED FRAGMENT CORE',
          'Open textured frame. Teal outer and inner fragments with real gaps; separate layers counter-rotate.',[
              ('quantum-frame.png','OPEN FRAME / TEXTURED FITTINGS','Pixel rails, corner plates and cable-colour sleeves'),
              ('core-detail.png','CORE / 22 OUTER + 12 INNER FRAGMENTS','Dark teal surfaces, local luminous seams, faceted nucleus')],2,600)
    sheet(root,'connection-sheet.png','CURRENT CABLES / ORDINARY + BUNDLED',
          'Columns: ordinary 1x1, bundle 2x2, bundle 3x3. Each connected scene is framed independently.',[
              (f'{device}-cable-{n}.png',f'{device.upper()} / {n}x{n} CABLE','Original cable references / matching carrier geometry')
              for device in ('laser','quantum') for n in (1,2,3)],3,460)
    frames=sorted((root/'core-animation').glob('*.png'))
    if frames:
        images=[]
        for path in frames:
            with Image.open(path) as im:images.append(im.convert('RGB'))
        images[0].save(root/'fragment-core.gif',save_all=True,append_images=images[1:],
                       duration=[133,133,134]*(len(images)//3),loop=0,optimize=False)
    print('REFINED_SHEETS_OK',root)


if __name__=='__main__':main()
