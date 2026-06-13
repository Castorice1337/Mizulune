import cairosvg
import glob
import os

svg_files = glob.glob('d:/OpenZen-master/native/loader/res/*.svg')
for f in svg_files:
    png_file = f.replace('.svg', '.png')
    cairosvg.svg2png(url=f, write_to=png_file, output_width=48, output_height=48)
    print("Converted", f, "to", png_file)
