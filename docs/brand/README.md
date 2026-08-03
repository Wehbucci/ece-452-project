# Grasp brand assets

The app's mark, rebuilt as vector art for slides, the final report, and anywhere else the icon
is needed outside the app itself.

## What the mark is

A sprout: a green stem with two indigo leaves set at **different heights** (left high, right low).
The stagger is deliberate — a symmetric pair reads as wings or a moustache, and it is the offset
that makes the mark read as something growing.

It is drawn in code by `SproutGlyph` in
[`ui/components/GameGlyphs.kt`](../../app-dev/app/src/main/java/com/example/grasp/ui/components/GameGlyphs.kt),
and appears on the Login screen and the About screen, sitting in a white rounded tile.
The SVGs here reproduce that geometry exactly — same proportions, same colours, same draw order.

## Files

| File | Use |
|---|---|
| `grasp-icon.svg` + `grasp-icon-{1024,512,256,128}.png` | The icon as it appears in the app: white tile, faint border. Best on coloured or lilac backgrounds. |
| `grasp-icon-indigo.svg` + `grasp-icon-indigo-{1024,512,256}.png` | Same mark on the app's indigo. For dark slides, title pages, or anywhere the white tile would vanish. |
| `grasp-mark.svg` + `grasp-mark-512.png` | The sprout alone, transparent background. For slide corners and report headers. |

The SVGs are the source. Re-render at any size with:

```
rsvg-convert -w 2048 -h 2048 grasp-icon.svg -o grasp-icon-2048.png
```

## Colours

Taken from [`ui/theme/Color.kt`](../../app-dev/app/src/main/java/com/example/grasp/ui/theme/Color.kt);
don't sample them from a screenshot, use these.

| Token | Hex | Where |
|---|---|---|
| `PathNodeDone` | `#1FB980` | stem |
| `PathNodeCurrent` | `#6C5CE7` | leaves, indigo background |
| `PathCard` | `#FFFFFF` | tile |
| `PathScreenBg` | `#F3F2FB` | the page the tile sits on |
| `PathNodeOpenBevel` | `#E4E1F0` | tile border |
| `PathInk` | `#211D3B` | the "Grasp" wordmark, set in Fredoka Bold |

## The launcher icon

`grasp-launcher-source.png` is the artwork the home-screen icon was built from: the same sprout
idea, drawn with a curved stem, a base foot, and pointed leaves.

It is installed in `app/src/main/res/` as an Android **adaptive icon**, which is two layers the
launcher masks into whatever shape the device uses (circle, squircle, teardrop):

| Layer | Resource |
|---|---|
| background | `drawable/ic_launcher_background.xml` — a flat `PathScreenBg` (`#F3F2FB`) fill, standing in for the tile |
| foreground | `mipmap-*/ic_launcher_foreground.png` — the sprout alone, on transparency |
| monochrome | `mipmap-*/ic_launcher_monochrome.png` — black silhouette, for Android 13+ themed icons |

The tile is deliberately **not** baked into the foreground. The launcher already draws that
shape via the mask, so a tile in the artwork would get its corners clipped a second time and the
icon would look dented. `mipmap-*/ic_launcher{,_round}.webp` keep a white tile baked in because
they are the pre-adaptive fallback and nothing masks those.

The sprout occupies the same share of its tile as in the source art (0.61 wide, 0.66 tall), which
puts it at roughly 44×48 in the 108-unit adaptive canvas — inside the 66-unit safe zone, so no
mask shape can clip it.

Replace `grasp-launcher-source.png` and re-run to regenerate every layer and density:

```
python3 docs/brand/make_launcher_icon.py     # needs pillow + numpy
```

## Known inconsistency

The launcher icon and the in-app mark are now two different drawings of the same idea — the
launcher's stem curves and has a foot, `SproutGlyph`'s is straight with oval leaves. Worth
reconciling if the app is ever polished for release.
