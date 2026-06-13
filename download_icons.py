import urllib.request
import os

icons = {
    'ic_notice.png': 'https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/bullhorn.svg',
    'ic_version.png': 'https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/code-branch.svg',
    'ic_launch.png': 'https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/play.svg',
    'ic_settings.png': 'https://raw.githubusercontent.com/FortAwesome/Font-Awesome/6.x/svgs/solid/gear.svg'
}

for name, url in icons.items():
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    svg_data = urllib.request.urlopen(req).read().decode('utf-8')
    svg_data = svg_data.replace('<path ', '<path fill="#ffffff" ')
    
    # Save as svg first
    svg_path = 'd:/OpenZen-master/native/loader/res/' + name.replace('.png', '.svg')
    with open(svg_path, 'w', encoding='utf-8') as f:
        f.write(svg_data)
        
    print(f"Saved {svg_path}")

# Note: We need a way to convert SVG to PNG since we can't use QtSvg.
# Let's use cairosvg or just draw simple shapes with PIL.
# Wait, actually, let's just use PyQt6 to convert them since it was pip installed!
from PyQt6.QtWidgets import QApplication
from PyQt6.QtSvg import QSvgRenderer
from PyQt6.QtGui import QImage, QPainter, QColor
from PyQt6.QtCore import Qt, QSize
import sys

app = QApplication(sys.argv)
for name in icons.keys():
    svg_path = 'd:/OpenZen-master/native/loader/res/' + name.replace('.png', '.svg')
    png_path = 'd:/OpenZen-master/native/loader/res/' + name
    
    renderer = QSvgRenderer(svg_path)
    img = QImage(64, 64, QImage.Format.Format_ARGB32)
    img.fill(QColor(0, 0, 0, 0)) # transparent background
    
    painter = QPainter(img)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    renderer.render(painter)
    painter.end()
    
    img.save(png_path)
    print(f"Converted {png_path}")
