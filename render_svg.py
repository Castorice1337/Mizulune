import sys
from PyQt6.QtWidgets import QApplication
from PyQt6.QtGui import QImage, QPainter
from PyQt6.QtSvg import QSvgRenderer
import glob

app = QApplication(sys.argv)
svg_files = glob.glob('d:/OpenZen-master/native/loader/res/*.svg')
for f in svg_files:
    renderer = QSvgRenderer(f)
    img = QImage(48, 48, QImage.Format.Format_ARGB32)
    img.fill(0)
    painter = QPainter(img)
    renderer.render(painter)
    painter.end()
    png_file = f.replace('.svg', '.png')
    img.save(png_file)
    print("Rendered:", png_file)
