#!/usr/bin/env python3
"""Export launcher.svg to Android vectors, lossless fallbacks, and a review sheet.

Requires Python 3 and ImageMagick 7 with its librsvg delegate.
"""

from copy import deepcopy
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "design/launcher-icon"
RES = ROOT / "app/src/main/res"
SVG = "http://www.w3.org/2000/svg"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("", SVG)
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
master = ET.parse(DESIGN / "launcher.svg").getroot()
gradients = {g.attrib["id"]: g for g in master.find(f"{{{SVG}}}defs")}
groups = {g.attrib["id"]: g for g in master.findall(f"{{{SVG}}}g")}


def android(name):
    return f"{{{ANDROID}}}{name}"


def vector(group, monochrome=False):
    root = ET.Element("vector", {
        android("width"): "108dp", android("height"): "108dp",
        android("viewportWidth"): "108", android("viewportHeight"): "108",
    })
    for source in groups[group]:
        path = ET.SubElement(root, "path", {android("pathData"): source.attrib["d"]})
        for prop, attr in (("fill", "fillColor"), ("stroke", "strokeColor")):
            value = source.attrib.get(prop, "none")
            if value == "none":
                path.set(android(attr), "#00000000")
            elif monochrome:
                path.set(android(attr), "#FFFFFFFF")
            elif value.startswith("url(#"):
                gradient = gradients[value[5:-1]]
                wrapper = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": f"android:{attr}"})
                node = ET.SubElement(wrapper, "gradient", {
                    android("type"): "linear",
                    **{android(k): gradient.attrib[v] for k, v in (
                        ("startX", "x1"), ("startY", "y1"), ("endX", "x2"), ("endY", "y2"))},
                })
                for stop in gradient:
                    ET.SubElement(node, "item", {
                        android("offset"): stop.attrib["offset"],
                        android("color"): stop.attrib["stop-color"],
                    })
            else:
                path.set(android(attr), value)
        for prop, attr in (("stroke-width", "strokeWidth"),
                           ("stroke-linecap", "strokeLineCap"),
                           ("stroke-linejoin", "strokeLineJoin")):
            if prop in source.attrib:
                path.set(android(attr), source.attrib[prop])
    ET.indent(root, space="    ")
    return ('<!-- Generated from design/launcher-icon/launcher.svg. '
            'Run scripts/generate-launcher-icons.py. -->\n'
            + ET.tostring(root, encoding="unicode") + "\n")


def svg_document(*, mono=False, shape=None):
    doc = deepcopy(master)
    # The adaptive layer is 108dp. Flattened icons show the central 72dp viewport.
    doc.set("viewBox", "18 18 72 72")
    doc.set("width", "1024")
    doc.set("height", "1024")
    if mono:
        for path in doc.findall(f".//{{{SVG}}}path"):
            for prop in ("fill", "stroke"):
                if path.attrib.get(prop, "none") != "none":
                    path.set(prop, "#D8ECC3" if path.attrib.get("d") != "M0,0H108V108H0Z" else "#263522")
    if shape:
        defs = doc.find(f"{{{SVG}}}defs")
        clip = ET.SubElement(defs, f"{{{SVG}}}clipPath", {"id": "mask"})
        if shape == "circle":
            ET.SubElement(clip, f"{{{SVG}}}circle", {"cx": "54", "cy": "54", "r": "36"})
        else:
            ET.SubElement(clip, f"{{{SVG}}}rect", {
                "x": "18", "y": "18", "width": "72", "height": "72",
                "rx": "16" if shape == "rounded" else "24",
            })
        wrapper = ET.SubElement(doc, f"{{{SVG}}}g", {"clip-path": "url(#mask)"})
        for group in list(doc.findall(f"{{{SVG}}}g")):
            if group is not wrapper:
                doc.remove(group)
                wrapper.append(group)
    for element in doc.iter():
        if element.text and not element.text.strip():
            element.text = None
        if element.tail and not element.tail.strip():
            element.tail = None
    return ET.tostring(doc, encoding="unicode")


def render(svg, output, size):
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".svg", mode="w") as source:
        source.write(svg)
        source.flush()
        subprocess.run(["magick", "-background", "none", "-size", f"{size}x{size}",
                        source.name, "-strip", "-define", "webp:lossless=true", str(output)], check=True)


for group, filename, mono in (
    ("background", "ic_launcher_background.xml", False),
    ("foreground", "ic_launcher_adaptive_foreground.xml", False),
    ("foreground", "ic_launcher_monochrome.xml", True),
):
    (RES / "drawable" / filename).write_text(vector(group, mono))

render(svg_document(), RES / "drawable-nodpi/agile_launcher_icon.png", 1024)
for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    for suffix, shape in (("", "rounded"), ("_round", "circle")):
        render(svg_document(shape=shape), RES / f"mipmap-{density}/ic_launcher{suffix}.webp", size)

# Contact sheet uses the same 72dp crop as launcher masks, including small Wear sizes.
sheet = ET.Element(f"{{{SVG}}}svg", {"width": "1200", "height": "700", "viewBox": "0 0 1200 700"})
ET.SubElement(sheet, f"{{{SVG}}}rect", {"width": "1200", "height": "700", "fill": "#EDEFEA"})


def label(x, y, text, size=18):
    ET.SubElement(sheet, f"{{{SVG}}}text", {
        "x": str(x), "y": str(y), "fill": "#263522", "font-family": "sans-serif", "font-size": str(size),
    }).text = text


label(40, 46, "Agile Prices · balanced bolt + clock", 28)
for i, (title, shape, mono) in enumerate((
    ("Circle / Wear OS", "circle", False), ("Rounded square", "rounded", False),
    ("Squircle-like mask", "squircle", False), ("Themed", "circle", True),
)):
    x = 40 + i * 295
    doc = ET.fromstring(svg_document(mono=mono, shape=shape))
    # Give each nested viewport its own gradient and clip IDs.
    markup = ET.tostring(doc, encoding="unicode")
    for name in ("lime", "ink", "mask", "background", "foreground"):
        markup = markup.replace(f'"{name}"', f'"{name}{i}"').replace(f"#{name})", f"#{name}{i})")
    doc = ET.fromstring(markup)
    doc.attrib.update({"x": str(x), "y": "85", "width": "240", "height": "240"})
    sheet.append(doc)
    label(x, 363, title)
    for j, size in enumerate((32, 48, 64)):
        small_markup = ET.tostring(doc, encoding="unicode")
        for name in ("lime", "ink", "mask", "background", "foreground"):
            small_markup = small_markup.replace(f'"{name}{i}"', f'"{name}{i}_{size}"').replace(
                f"#{name}{i})", f"#{name}{i}_{size})")
        small = ET.fromstring(small_markup)
        small.attrib.update({"x": str(x + j * 82), "y": "410", "width": str(size), "height": str(size)})
        sheet.append(small)
        label(x + j * 82, 505, f"{size}px", 14)
label(40, 585, "Shared geometry in full colour and monochrome. Scalable Android vector layers; lossless density fallbacks.")
label(40, 625, "Foreground fits the central 66dp safe circle. High-resolution export has a full-bleed background.")
preview = ET.tostring(sheet, encoding="unicode")
(DESIGN / "preview.svg").write_text(preview + "\n")
render(preview, DESIGN / "preview.png", 1200)
print("Generated Android layers, five fallback densities, 1024px artwork and mask previews.")
