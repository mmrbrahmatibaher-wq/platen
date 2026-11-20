"""Side-by-side: reference screenshot (analysis only, never shipped) vs the v3 Robolectric render."""
from PIL import Image, ImageDraw, ImageFont
ref = Image.open("ref/reference_screenshot.jpg").convert("RGB")
ours = Image.open("screenshots/v3_editor_reference.png").convert("RGB").resize(ref.size, Image.LANCZOS)
W, H = ref.size
out = Image.new("RGB", (W * 2 + 24, H + 56), (24, 22, 20))
out.paste(ref, (0, 56)); out.paste(ours, (W + 24, 56))
d = ImageDraw.Draw(out)
try:
    f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 26)
except OSError:
    f = ImageFont.load_default()
d.text((16, 14), "Reference (iPad, analysis only)", fill=(230, 225, 215), font=f)
d.text((W + 40, 14), "Platen v3 (Robolectric, 1280x889 mdpi)", fill=(230, 225, 215), font=f)
out.save("screenshots/v3_compare.png")
print(out.size)
