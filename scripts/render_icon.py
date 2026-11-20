"""Export the project's geometric SVG to a 400x400 PNG. Requires Pillow."""
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SCALE = 3


def render():
    svg = ET.parse(ROOT / 'assets/branding/icon.svg').getroot()
    width, height = int(svg.attrib['width']), int(svg.attrib['height'])
    image = Image.new('RGBA', (width * SCALE, height * SCALE))
    draw = ImageDraw.Draw(image)
    for element in svg:
        tag = element.tag.rsplit('}', 1)[-1]
        a = element.attrib
        if tag == 'title':
            continue
        if tag == 'rect':
            x, y, w, h = (int(a[k]) * SCALE for k in ('x', 'y', 'width', 'height'))
            draw.rounded_rectangle((x, y, x + w, y + h),
                                   radius=int(a.get('rx', '0')) * SCALE, fill=a['fill'])
        elif tag == 'line':
            draw.line(tuple(int(a[k]) * SCALE for k in ('x1', 'y1', 'x2', 'y2')),
                      fill=a['stroke'], width=int(a['stroke-width']) * SCALE)
        else:
            raise ValueError(f'Unsupported SVG element: {tag}')
    image = image.resize((width, height), Image.Resampling.LANCZOS)
    for target in ('assets/branding/icon.png', 'src/main/resources/logo.png'):
        image.save(ROOT / target)


if __name__ == '__main__':
    render()
