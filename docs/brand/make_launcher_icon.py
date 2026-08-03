"""Rebuild the Android launcher icon from `grasp-launcher-source.png`.

    python3 docs/brand/make_launcher_icon.py       (needs: pillow, numpy)

The source is flat art — two brand colours painted on a white tile on a lilac page. The tile is
what the launcher's mask replaces, so it must NOT be baked into the adaptive foreground: the mask
would clip its corners a second time and the icon would look dented. We therefore lift just the
sprout off the white and let a flat background layer stand in for the tile.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

BRAND = Path(__file__).resolve().parent
RES = BRAND.parent.parent / "app-dev" / "app" / "src" / "main" / "res"
SRC = BRAND / "grasp-launcher-source.png"

GREEN = np.array([0x1F, 0xB9, 0x80], dtype=float)
INDIGO = np.array([0x6C, 0x5C, 0xE7], dtype=float)
WHITE = np.array([255.0, 255.0, 255.0])

# An adaptive icon is 108dp; launchers show the middle 72dp and guarantee only the middle 66dp.
CANVAS, VIEWPORT = 108.0, 72.0
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def unmix(px, colour):
    """Alpha for `colour` composited over white, and how badly that explains the pixel.

    Each pixel is assumed to be P = alpha*colour + (1-alpha)*WHITE. Solving on the channel that
    separates the colour from white the most is the stable choice; keeping the error lets the
    caller pick between the two brand colours, which is what stops antialiased green edges from
    being reconstructed as muddy violet.
    """
    denom = WHITE - colour
    ch = int(np.argmax(np.abs(denom)))
    alpha = np.clip((WHITE[ch] - px[..., ch]) / denom[ch], 0.0, 1.0)
    recon = alpha[..., None] * colour + (1 - alpha[..., None]) * WHITE
    return alpha, np.abs(recon - px).sum(axis=2)


def main() -> None:
    a = np.asarray(Image.open(SRC).convert("RGB"), dtype=float)

    # The white tile is the frame the artwork was composed in, and the unit we measure against.
    ys, xs = np.nonzero((a > 250).all(axis=2))
    ty0, ty1, tx0, tx1 = ys.min(), ys.max(), xs.min(), xs.max()
    tile_h, tile_w = ty1 - ty0 + 1, tx1 - tx0 + 1

    alpha_g, err_g = unmix(a, GREEN)
    alpha_i, err_i = unmix(a, INDIGO)
    green = err_g <= err_i
    alpha = np.where(green, alpha_g, alpha_i)

    inside = np.zeros(alpha.shape, dtype=bool)
    inside[ty0:ty1 + 1, tx0:tx1 + 1] = True  # everything else is the lilac page, not artwork
    alpha = np.where(inside, alpha, 0.0)
    alpha[alpha < 0.02] = 0.0  # the faint halo the generator left on the white

    sprite = Image.fromarray(
        np.dstack([np.where(green[..., None], GREEN, INDIGO), alpha * 255.0]).astype(np.uint8),
        "RGBA",
    )

    sy, sx = np.nonzero(alpha > 0.15)
    gy0, gy1, gx0, gx1 = sy.min(), sy.max(), sx.min(), sx.max()
    glyph = sprite.crop((gx0, gy0, gx1 + 1, gy1 + 1))
    share_w, share_h = (gx1 - gx0 + 1) / tile_w, (gy1 - gy0 + 1) / tile_h
    print(f"sprout is {share_w:.3f} x {share_h:.3f} of its tile")

    # Map the tile onto the masked viewport, so the sprout keeps the proportions it was drawn
    # with rather than being resized to some number we invented.
    target_w, target_h = share_w * VIEWPORT, share_h * VIEWPORT
    assert max(target_w, target_h) <= 66, "sprout would fall outside the adaptive safe zone"

    def render(px, mono=False):
        k = px / CANVAS
        w, h = max(1, round(target_w * k)), max(1, round(target_h * k))
        layer = glyph.resize((w, h), Image.LANCZOS)
        if mono:  # themed icons are tinted by the system; only the silhouette survives
            solid = Image.new("RGBA", layer.size, (0, 0, 0, 255))
            solid.putalpha(layer.getchannel("A"))
            layer = solid
        canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        canvas.paste(layer, ((px - w) // 2, (px - h) // 2), layer)
        return canvas

    for name, px in ADAPTIVE.items():
        render(px).save(RES / f"mipmap-{name}" / "ic_launcher_foreground.png")
        render(px, mono=True).save(RES / f"mipmap-{name}" / "ic_launcher_monochrome.png")

    # Pre-adaptive fallbacks. Nothing masks these, so the tile is baked in here.
    for name, px in LEGACY.items():
        for round_icon in (False, True):
            tile = Image.new("RGBA", (px, px), (0, 0, 0, 0))
            draw = ImageDraw.Draw(tile)
            if round_icon:
                draw.ellipse((0, 0, px - 1, px - 1), fill=(255, 255, 255, 255))
            else:
                draw.rounded_rectangle((0, 0, px - 1, px - 1), radius=round(px * 0.22),
                                       fill=(255, 255, 255, 255))
            w, h = max(1, round(px * share_w)), max(1, round(px * share_h))
            art = glyph.resize((w, h), Image.LANCZOS)
            tile.paste(art, ((px - w) // 2, (px - h) // 2), art)
            tile.save(RES / f"mipmap-{name}" /
                      ("ic_launcher_round.webp" if round_icon else "ic_launcher.webp"),
                      "WEBP", lossless=True, quality=100)

    print(f"wrote launcher icons into {RES}")


if __name__ == "__main__":
    main()
