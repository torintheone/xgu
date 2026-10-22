"""Halves the screenshots from scripts/screenshots.sh and saves them as small PNG files.

Usage: python shrink_screenshots.py <folder with full-size pictures> <docs/screenshots>
Needs Pillow (pip install pillow).
"""
import pathlib
import sys

from PIL import Image

source, target = (pathlib.Path(arg) for arg in sys.argv[1:3])
target.mkdir(parents=True, exist_ok=True)
for old in target.glob("*.png"):
    old.unlink()
for path in sorted(source.glob("*.png")):
    image = Image.open(path).convert("RGB")
    image = image.resize((image.width // 2, image.height // 2), Image.Resampling.LANCZOS)
    image = image.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
    image.save(target / path.name, optimize=True)
    print(f"{path.name}: {(target / path.name).stat().st_size // 1024} KB")
