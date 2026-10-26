"""Compose labelled Blender render sheets using the approved visual pixi environment."""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
BOLD = Path('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')


def sheet(root, name, title, subtitle, items, columns, cell=480):
    rows = (len(items)+columns-1)//columns
    header, caption = 112, 74
    canvas = Image.new('RGB', (columns*cell, header+rows*(cell+caption)), '#171d29')
    draw = ImageDraw.Draw(canvas)
    draw.text((26, 20), title, font=ImageFont.truetype(str(BOLD), 29), fill='#edf0fa')
    draw.text((27, 67), subtitle, font=ImageFont.truetype(str(FONT), 16), fill='#abb9ce')
    for i, (file, label, detail) in enumerate(items):
        x, y = (i % columns)*cell, header+(i//columns)*(cell+caption)
        with Image.open(root/file) as image:
            canvas.paste(image.convert('RGB').resize((cell, cell), Image.Resampling.LANCZOS), (x, y))
        draw.text((x+18, y+cell+12), label, font=ImageFont.truetype(str(BOLD), 18), fill='#e6ecf7')
        draw.text((x+18, y+cell+40), detail, font=ImageFont.truetype(str(FONT), 13), fill='#a3b8cb')
    path = root/name
    if path.exists():
        raise FileExistsError(path)
    canvas.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    root = args.directory
    sheet(root, 'reference-sheet.png', 'AE2 FEDERATION / CURRENT ASSETS',
          'Fresh Blender renders from repository models and textures. Last two entries are existing temporary assets.', [
              ('reference-router.png', 'ROUTER', 'Six-face cyan terminals / white edge frame'),
              ('reference-pattern_provider.png', 'PATTERN PROVIDER', 'Cyan Federation face / violet ME surfaces'),
              ('reference-processing_endpoint.png', 'PROCESSING ENDPOINT', 'Stepped front relief / pixel material hierarchy'),
              ('reference-switch.png', 'SWITCH', 'Violet centre / cyan cardinal contacts'),
              ('reference-beam_former.png', 'BEAM FORMER / 1x1', 'Existing artist model / directional face'),
              ('reference-beam_former_huge.png', 'BEAM FORMER / 3x3', 'Existing artist model / deep mechanical relief'),
              ('reference-quantum_cube.png', 'QUANTUM / CURRENT', 'Existing temporary recoloured Router'),
              ('reference-beam_converter.png', 'CONVERTER / CURRENT', 'Existing temporary full-cube material'),
          ], 4, 400)
    sheet(root, 'quantum-overview.png', 'QUANTUM CUBE / CONVERGENCE FRAME',
          'Open ceramic frame, suspended plasma sphere and six optical axes. Editable Blender proposal.', [
              ('quantum-3.png', '3x3x3 / OPEN FRAME', 'Six central 1x1 ports / sphere diameter 1.44 blocks'),
              ('quantum-connected.png', 'SIX-AXIS CONNECTION STUDY', 'Current cable shells / proposed internal light'),
          ], 2, 640)
    sheet(root, 'size-sheet.png', 'AE2 FEDERATION / DEVICE SCALE STUDY',
          'Actual supported dimensions. Each object is framed separately; this sheet does not use a common camera scale.', [
              ('quantum-3.png', 'QUANTUM / 3x3x3', '1x1 face-centre connection'),
              ('quantum-4.png', 'QUANTUM / 4x4x4', '2x2 face-centre connection'),
              ('quantum-5.png', 'QUANTUM / 5x5x5', '3x3 face-centre connection'),
              ('converter-1.png', 'CONVERTER / 1x1x2', 'One optical terminal / two conversion cells'),
              ('converter-2.png', 'CONVERTER / 2x2x4', 'One optical terminal / four cells along the axis'),
              ('converter-3.png', 'CONVERTER / 3x3x6', 'One optical terminal / six cells along the axis'),
          ], 3)
    sheet(root, 'converter-overview.png', 'BEAM CONVERTER / CONVERSION STACK',
          'Cyan front terminal, repeated conversion cells and restrained amber FE contacts. Editable Blender proposal.', [
              ('converter-1.png', '1x1x2 / SMALL', 'Front-facing optical socket'),
              ('converter-2.png', '2x2x4 / MEDIUM', 'Longitudinal cell rhythm'),
              ('converter-3.png', '3x3x6 / LARGE', 'Same 1-block panel pitch'),
              ('converter-rear.png', '2x2x4 / REAR', 'Energy acceptor / FE contacts'),
          ], 2, 560)
    frames = sorted((root/'animation').glob('*.png'))
    if frames:
        images = []
        for path in frames:
            with Image.open(path) as image:
                images.append(image.convert('RGB'))
        images[0].save(root/'quantum-energy.gif', save_all=True, append_images=images[1:],
                       duration=[133, 133, 134]*(len(images)//3), loop=0, optimize=False)
    print('DEVICE_SHEETS_OK', root)


if __name__ == '__main__':
    main()
