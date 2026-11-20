"""Side-by-side: v3.2 reference images (analysis only, never shipped) vs Robolectric renders."""
from PIL import Image, ImageDraw, ImageFont

try:
    F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 24)
except OSError:
    F = ImageFont.load_default()

def pair(ref_path, ours_path, out_path, label):
    ref = Image.open(ref_path).convert("RGB")
    ours = Image.open(ours_path).convert("RGB").resize(ref.size, Image.LANCZOS)
    W, H = ref.size
    out = Image.new("RGB", (W * 2 + 24, H + 52), (24, 22, 20))
    out.paste(ref, (0, 52)); out.paste(ours, (W + 24, 52))
    d = ImageDraw.Draw(out)
    d.text((14, 12), "Reference (analysis only)", fill=(230, 225, 215), font=F)
    d.text((W + 38, 12), label, fill=(230, 225, 215), font=F)
    out.save(out_path)
    print(out_path, out.size)

pair("ref/r2/portrait_keyboard.jpg", "screenshots/v3_2_portrait_keyboard.png", "screenshots/v3_2_compare_portrait.png", "Platen 3.2 (889x1280 mdpi, keyboard stand-in)")
pair("ref/r2/landscape_morse_page.jpg", "screenshots/v3_2_landscape.png", "screenshots/v3_2_compare_landscape.png", "Platen 3.2 (1280x889 mdpi)")
pair("ref/r2/landscape_new_sheet.jpg", "screenshots/v3_2_landscape_full_sheet.png", "screenshots/v3_2_compare_full_sheet.png", "Platen 3.2 fresh sheet")

# Reference strike frames (from video2.mp4, analysis only) next to our strip, for the report.
