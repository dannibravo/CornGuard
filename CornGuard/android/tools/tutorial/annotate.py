"""Builds the onboarding tutorial images from raw emulator screenshots.

    python android/tools/tutorial/annotate.py            # all slides
    python android/tools/tutorial/annotate.py --sheet    # also writes contact_sheet.png to review

Reads shots.json (crop + highlight boxes per slide) and raw/*.png, draws a green outline with a
numbered badge and a short label for each highlight, then writes WebP files into
app/src/main/res/drawable-nodpi/. Re-run after re-capturing any screen (see README.md).
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
OUT = HERE.parent.parent / "app" / "src" / "main" / "res" / "drawable-nodpi"

GREEN = (46, 125, 50)          # cg_primary #2E7D32
WHITE = (255, 255, 255)
OUT_WIDTH = 900                # px; plenty for a phone card, keeps files small
STROKE = 9
HALO = 4
FONT_SIZE = 42
BADGE_R = 30


def font(size, bold=True):
    for name in (["arialbd.ttf", "segoeuib.ttf"] if bold else ["arial.ttf", "segoeui.ttf"]):
        for base in (Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu")):
            if (base / name).exists():
                return ImageFont.truetype(str(base / name), size)
    return ImageFont.truetype("DejaVuSans-Bold.ttf", size)


def clamp(v, lo, hi):
    return max(lo, min(v, hi))


def draw_slide(spec):
    img = Image.open(RAW / spec["raw"]).convert("RGB")
    cx, cy, cw, ch = spec["crop"]
    img = img.crop((cx, cy, cx + cw, cy + ch))
    d = ImageDraw.Draw(img)
    f = font(FONT_SIZE)
    fb = font(int(BADGE_R * 1.25))

    # Outlines first, so labels and badges sit on top of every outline.
    for m in spec["marks"]:
        x, y, w, h = m["box"]
        x, y = x - cx, y - cy
        rect = [x, y, x + w, y + h]
        r = min(28, w // 3, h // 3)
        d.rounded_rectangle([rect[0] - HALO, rect[1] - HALO, rect[2] + HALO, rect[3] + HALO],
                            radius=r + HALO, outline=WHITE, width=STROKE + 2 * HALO)
        d.rounded_rectangle(rect, radius=r, outline=GREEN, width=STROKE)

    for m in spec["marks"]:
        x, y, w, h = m["box"]
        x, y = x - cx, y - cy
        # Numbered badge on the outline's top-left corner.
        bx = clamp(x, BADGE_R + 4, cw - BADGE_R - 4)
        by = clamp(y, BADGE_R + 4, ch - BADGE_R - 4)
        d.ellipse([bx - BADGE_R - 4, by - BADGE_R - 4, bx + BADGE_R + 4, by + BADGE_R + 4], fill=WHITE)
        d.ellipse([bx - BADGE_R, by - BADGE_R, bx + BADGE_R, by + BADGE_R], fill=GREEN)
        d.text((bx, by), str(m["n"]), font=fb, fill=WHITE, anchor="mm")

        # Label pill next to the outline.
        text = m["label"]
        tw = d.textlength(text, font=f)
        pw, ph = int(tw + 44), FONT_SIZE + 30
        gap = 16
        side = m.get("side", "below")
        if side == "below":
            px, py = x + w / 2 - pw / 2, y + h + gap
        elif side == "above":
            px, py = x + w / 2 - pw / 2, y - ph - gap
        elif side == "inside":   # top-right corner, inside the outline
            px, py = x + w - pw - STROKE - 14, y + STROKE + 14
        elif side == "left":
            px, py = x - pw - gap, y + h / 2 - ph / 2
        else:
            px, py = x + w + gap, y + h / 2 - ph / 2
        px = clamp(px, 8, cw - pw - 8)
        py = clamp(py, 8, ch - ph - 8)
        pill = [px, py, px + pw, py + ph]
        d.rounded_rectangle([pill[0] - 3, pill[1] - 3, pill[2] + 3, pill[3] + 3], radius=ph // 2 + 3, fill=WHITE)
        d.rounded_rectangle(pill, radius=ph // 2, fill=GREEN)
        d.text((px + pw / 2, py + ph / 2), text, font=f, fill=WHITE, anchor="mm")

    scale = OUT_WIDTH / cw
    return img.resize((OUT_WIDTH, round(ch * scale)), Image.LANCZOS)


def main():
    spec = json.loads((HERE / "shots.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    images = []
    total = 0
    for s in spec["slides"]:
        img = draw_slide(s)
        path = OUT / f"{s['out']}.webp"
        img.save(path, "WEBP", quality=80, method=6)
        size = path.stat().st_size
        total += size
        images.append((s["out"], img))
        print(f"{s['out']:28s} {img.width}x{img.height}  {size // 1024} KB")
    print(f"total {total // 1024} KB in {len(images)} images -> {OUT}")

    if "--sheet" in sys.argv:
        thumb_w = 300
        thumbs = [(n, im.resize((thumb_w, round(im.height * thumb_w / im.width)))) for n, im in images]
        cols = 4
        rows = [thumbs[i:i + cols] for i in range(0, len(thumbs), cols)]
        height = sum(max(t.height for _, t in r) + 40 for r in rows)
        sheet = Image.new("RGB", (cols * (thumb_w + 20) + 20, height + 20), (230, 230, 230))
        sd = ImageDraw.Draw(sheet)
        y = 20
        for r in rows:
            x = 20
            for n, t in r:
                sheet.paste(t, (x, y + 30))
                sd.text((x, y + 4), n, font=font(18, bold=False), fill=(0, 0, 0))
                x += thumb_w + 20
            y += max(t.height for _, t in r) + 40
        sheet.save(HERE / "contact_sheet.png")
        print("contact sheet ->", HERE / "contact_sheet.png")


if __name__ == "__main__":
    main()
