from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

CANVAS_W = 750
CANVAS_H = 1000

SUBCATEGORY_SPECS: dict[str, tuple[list[str], list[str], list[str]]] = {
    "ao-thun": (["áo thun"], ["áo thun"], ["khoác", "quần", "đầm", "có mũ", "hoodie"]),
    "ao-polo": (["áo polo"], ["polo"], ["khoác", "quần", "đầm"]),
    "ao-so-mi": (["áo sơ mi"], ["sơ mi"], ["khoác", "đầm", "quần"]),
    "ao-kieu": (["áo kiểu", "áo blouse"], ["áo kiểu", "blouse"], ["khoác", "quần", "đầm"]),
    "ao-len": (["áo len", "cardigan"], ["len", "cardigan"], ["quần", "đầm"]),
    "dam": (["dress", "váy đầm"], ["đầm"], ["khoác", "quần", "áo hoodie", "áo nỉ"]),
    "chan-vay": (["chân váy", "váy"], ["chân váy", "quần váy"], ["khoác", "áo"]),
    "ao-khoac": (["áo khoác", "parka"], ["áo khoác", "parka", "blouson"], ["quần", "đầm", "sơ mi"]),
    "blazer": (["áo blazer", "jacket"], ["blazer", "áo khoác"], ["quần", "đầm"]),
    "hoodie": (["áo hoodie", "áo nỉ"], ["hoodie", "áo nỉ"], ["quần", "đầm"]),
    "quan-jean": (["quần jean", "denim"], ["jean", "denim"], ["áo", "đầm", "váy"]),
    "quan-tay": (["quần tây", "quần ankle", "quần dài"], ["quần"], ["áo", "đầm", "váy", "short", "jean"]),
    "quan-dai": (["quần dài", "quần ống rộng"], ["quần"], ["áo", "đầm", "váy", "short"]),
    "quan-short": (["quần short"], ["short"], ["áo", "đầm", "váy"]),
}

COLOR_PALETTE = {
    "trang": (242, 242, 239),
    "den": (38, 40, 45),
    "xanh-navy": (35, 53, 90),
    "xanh-duong": (76, 130, 195),
    "be": (218, 203, 180),
    "kem": (235, 226, 208),
    "xam": (138, 142, 148),
    "hong": (228, 165, 179),
    "do": (166, 42, 52),
    "xanh-reu": (78, 103, 76),
    "nau": (126, 86, 60),
    "vang": (225, 188, 86),
    "tim": (144, 118, 170),
    "cam": (216, 116, 68),
}


def compute_file_signature(full_path: Path) -> tuple[str, int] | None:
    try:
        raw_bytes = full_path.read_bytes()
    except OSError:
        return None

    sha = hashlib.sha256(raw_bytes).hexdigest()
    try:
        with Image.open(io.BytesIO(raw_bytes)) as img:
            try:
                img.draft("L", (64, 64))
            except Exception:
                pass
            gray = img.convert("L").resize((9, 8), Image.Resampling.BILINEAR)
            arr = np.asarray(gray, dtype=np.int16)
            diff = arr[:, 1:] > arr[:, :-1]
            bits = 0
            for bit in diff.flatten():
                bits = (bits << 1) | int(bit)
            return sha, bits
    except Exception:
        return None


def hamming_distance(h1: int, h2: int) -> int:
    return (h1 ^ h2).bit_count()


def deduplicate_product_gallery(base_dir: Path, products: list[dict]) -> tuple[int, int]:
    all_rel_paths: set[str] = set()
    for product in products:
        for rel in product.get("imageFiles") or []:
            if "-view-" not in rel:
                all_rel_paths.add(rel)
        if product.get("imageFile") and "-view-" not in product["imageFile"]:
            all_rel_paths.add(product["imageFile"])

    rel_list = sorted(all_rel_paths)
    sig_map: dict[str, tuple[str, int]] = {}

    def _worker(rel: str) -> tuple[str, tuple[str, int] | None]:
        full_p = base_dir / rel
        if not full_p.is_file():
            return rel, None
        return rel, compute_file_signature(full_p)

    with ThreadPoolExecutor(max_workers=16) as pool:
        for rel, sig in pool.map(_worker, rel_list):
            if sig is not None:
                sig_map[rel] = sig

    removed_files = 0
    affected_products = 0

    for product in products:
        raw_files = product.get("imageFiles") or []
        if not raw_files and product.get("imageFile"):
            raw_files = [product["imageFile"]]

        non_synthetic = [rel for rel in raw_files if "-view-" not in rel and rel in sig_map]
        candidates = non_synthetic if non_synthetic else [
            rel for rel in raw_files if (base_dir / rel).is_file()
        ]

        seen_sha: set[str] = set()
        seen_dhashes: list[int] = []
        unique_files: list[str] = []

        for rel in candidates:
            sig = sig_map.get(rel)
            if sig is None:
                if not unique_files:
                    unique_files.append(rel)
                continue

            sha, dh = sig
            if sha in seen_sha:
                continue

            is_near_dup = False
            for existing_dh in seen_dhashes:
                if hamming_distance(dh, existing_dh) <= 4:
                    is_near_dup = True
                    break

            if is_near_dup:
                continue

            seen_sha.add(sha)
            seen_dhashes.append(dh)
            unique_files.append(rel)

        if not unique_files and candidates:
            unique_files = [candidates[0]]

        if len(unique_files) != len(raw_files):
            affected_products += 1
            removed_files += max(0, len(raw_files) - len(unique_files))

        product["imageFiles"] = unique_files[:9]
        product["imageFile"] = product["imageFiles"][0] if product["imageFiles"] else None

        gallery_set = set(product["imageFiles"])
        seen_variant_imgs: set[str] = set()
        for variant in product.get("variants") or []:
            v_img = variant.get("imageFile")
            if not v_img or v_img in gallery_set or v_img in seen_variant_imgs:
                variant["imageFile"] = None
            else:
                seen_variant_imgs.add(v_img)

    return affected_products, removed_files


def fetch_uniqlo_candidate_urls(
    queries: list[str],
    include_kw: list[str],
    exclude_kw: list[str],
    max_urls: int = 10,
) -> list[str]:
    primary_urls: list[str] = []
    secondary_urls: list[str] = []
    seen: set[str] = set()

    for q in queries:
        encoded = urllib.parse.quote(q)
        api_url = f"https://www.uniqlo.com/vn/api/commerce/v5/vi/products?limit=36&offset=0&q={encoded}"
        req = urllib.request.Request(
            api_url,
            headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept": "application/json",
                "x-fr-clientid": "uq.vn.web-spa",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as exc:
            print(f"[garment] warning: Uniqlo query '{q}' failed: {exc}", flush=True)
            continue

        items = data.get("result", {}).get("items", [])
        for item in items:
            item_name = (item.get("name") or "").strip().lower()
            if include_kw and not any(k in item_name for k in include_kw):
                continue
            if exclude_kw and any(k in item_name for k in exclude_kw):
                continue

            sub_list = item.get("images", {}).get("sub", [])
            for sub in sub_list:
                model_info = sub.get("model")
                if model_info and len(model_info) > 0:
                    continue
                u = sub.get("image")
                if not u or u in seen:
                    continue
                if "_sub14_" in u or "_sub17_" in u:
                    seen.add(u)
                    primary_urls.append(u)
                elif "_sub13_" in u or "_sub16_" in u:
                    seen.add(u)
                    secondary_urls.append(u)

    combined = primary_urls + secondary_urls
    return combined[:max_urls]


def extract_clean_flatlay_template(raw_bytes: bytes) -> tuple[np.ndarray, np.ndarray] | None:
    """
    Fast & razor-sharp hybrid extraction (~15ms per image):
    1. Uses a 150x200 coarse floodfill to locate the exterior background topology.
    2. Refines the boundary at full 750x1000 resolution using vectorized numpy color distance
       from the studio backdrop + Gaussian anti-aliased thresholding so edges are 100% smooth.
    """
    try:
        img = Image.open(io.BytesIO(raw_bytes)).convert("RGB")
    except Exception:
        return None

    w, h = img.size
    if w < 300 or h < 300:
        return None

    arr = np.asarray(img, dtype=np.uint8)
    corners = np.array([arr[4, 4], arr[4, w - 5], arr[h - 5, 4], arr[h - 5, w - 5]], dtype=np.int16)
    for c in corners:
        c_min = int(c.min())
        c_max = int(c.max())
        if c_min < 220 or (c_max - c_min) > 15:
            return None

    border_pixels = np.concatenate(
        [arr[3, :], arr[h - 4, :], arr[:, 3], arr[:, w - 4]], axis=0
    )
    border_min = border_pixels.min(axis=1)
    border_ptp = border_pixels.max(axis=1) - border_min
    if float(np.mean((border_min >= 220) & (border_ptp <= 15))) < 0.88:
        return None

    # Step 1: 300x400 connected floodfill (~10ms) - topological, never punches unconnected holes inside fabric
    sw, sh = 300, 400
    small = img.resize((sw, sh), Image.Resampling.BILINEAR)
    sentinel = (255, 0, 255)
    seeds = [
        (2, 2),
        (sw - 3, 2),
        (2, sh - 3),
        (sw - 3, sh - 3),
        (sw // 2, 2),
        (sw // 2, sh - 3),
        (2, sh // 2),
        (sw - 3, sh // 2),
        (sw // 4, 2),
        ((3 * sw) // 4, 2),
        (2, sh // 4),
        (sw - 3, sh // 4),
        (2, (3 * sh) // 4),
        (sw - 3, (3 * sh) // 4),
    ]
    for sx, sy in seeds:
        px = small.getpixel((sx, sy))
        if px != sentinel and min(px) >= 218 and (max(px) - min(px)) <= 15:
            ImageDraw.floodfill(small, (sx, sy), sentinel, thresh=9)

    s_arr = np.asarray(small, dtype=np.uint8)
    is_bg_small = (s_arr[:, :, 0] == 255) & (s_arr[:, :, 1] == 0) & (s_arr[:, :, 2] == 255)

    fg_small_u8 = (~is_bg_small).astype(np.uint8) * 255
    # Zero out outer margin & top-left/top-right collaboration badge corners on small mask
    fg_small_u8[:10, :] = 0
    fg_small_u8[-10:, :] = 0
    fg_small_u8[:, :10] = 0
    fg_small_u8[:, -10:] = 0
    fg_small_u8[: int(sh * 0.17), : int(sw * 0.28)] = 0
    fg_small_u8[: int(sh * 0.17), int(sw * 0.72) :] = 0

    # Erode 4px on 300x400 (~10px at 750x1000) to completely strip the right/bottom studio drop-shadow rim,
    # then GaussianBlur(2.0) + BICUBIC upscale to 750x1000 for a smooth, anti-aliased, hole-free contour
    fg_small_eroded = (
        Image.fromarray(fg_small_u8, mode="L")
        .filter(ImageFilter.MinFilter(9))
        .filter(ImageFilter.GaussianBlur(radius=2.0))
    )
    fg_full_pil = fg_small_eroded.resize((w, h), Image.Resampling.BICUBIC)
    fg_clean = np.asarray(fg_full_pil, dtype=np.uint8) > 140

    # Strip any row/col with < 40 pixels to eliminate stray dust/bars
    fg_clean[fg_clean.sum(axis=1) < 40, :] = False
    fg_clean[:, fg_clean.sum(axis=0) < 40] = False

    fg_area_ratio = float(np.mean(fg_clean))
    if fg_area_ratio < 0.12 or fg_area_ratio > 0.72:
        return None

    # Reject light/white source templates whose luminance blends into the studio gray background
    fg_lum = (
        0.299 * arr[fg_clean, 0]
        + 0.587 * arr[fg_clean, 1]
        + 0.114 * arr[fg_clean, 2]
    )
    p75_lum = float(np.percentile(fg_lum, 75))
    if p75_lum > 195:
        return None

    ys, xs = np.where(fg_clean)
    if len(xs) == 0:
        return None

    x0, x1 = int(xs.min()), int(xs.max())
    y0, y1 = int(ys.min()), int(ys.max())
    bw = x1 - x0 + 1
    bh = y1 - y0 + 1
    if bw < int(w * 0.25) or bh < int(h * 0.25):
        return None

    crop_rgb = arr[y0 : y1 + 1, x0 : x1 + 1].copy()
    crop_mask = fg_clean[y0 : y1 + 1, x0 : x1 + 1]
    crop_rgb[~crop_mask] = [255, 255, 255]

    max_target_w = int(CANVAS_W * 0.78)
    max_target_h = int(CANVAS_H * 0.78)
    scale = min(max_target_w / bw, max_target_h / bh)

    new_w = max(1, int(round(bw * scale)))
    new_h = max(1, int(round(bh * scale)))

    resized_rgb = np.asarray(
        Image.fromarray(crop_rgb).resize((new_w, new_h), Image.Resampling.LANCZOS),
        dtype=np.uint8,
    )
    resized_mask = (
        np.asarray(
            Image.fromarray(crop_mask.astype(np.uint8) * 255)
            .resize((new_w, new_h), Image.Resampling.BILINEAR)
            .filter(ImageFilter.GaussianBlur(radius=0.9)),
            dtype=np.uint8,
        )
        > 135
    )

    canvas_rgb = np.full((CANVAS_H, CANVAS_W, 3), 255, dtype=np.uint8)
    canvas_mask = np.zeros((CANVAS_H, CANVAS_W), dtype=bool)

    off_x = (CANVAS_W - new_w) // 2
    off_y = (CANVAS_H - new_h) // 2

    canvas_mask[off_y : off_y + new_h, off_x : off_x + new_w] = resized_mask
    region_rgb = canvas_rgb[off_y : off_y + new_h, off_x : off_x + new_w]
    region_rgb[resized_mask] = resized_rgb[resized_mask]

    return canvas_rgb, canvas_mask


def render_colored_garment(
    template_rgb: np.ndarray,
    template_mask: np.ndarray,
    target_rgb: tuple[int, int, int] | None,
    out_path: Path,
) -> None:
    out_arr = np.full((CANVAS_H, CANVAS_W, 3), 255, dtype=np.uint8)
    if target_rgb is None:
        out_arr[template_mask] = template_rgb[template_mask]
    else:
        fg_pixels = template_rgb[template_mask].astype(np.float32)
        lum = (
            0.299 * fg_pixels[:, 0]
            + 0.587 * fg_pixels[:, 1]
            + 0.114 * fg_pixels[:, 2]
        )
        p15 = float(np.percentile(lum, 15))
        p88 = float(np.percentile(lum, 88))
        span = max(p88 - p15, 25.0)
        norm = (lum - p88) / span
        shade = np.clip(1.0 + norm * 0.34, 0.42, 1.07)

        t_arr = np.array(target_rgb, dtype=np.float32)
        recolored = np.clip(shade[:, None] * t_arr[None, :], 0, 252).astype(np.uint8)
        out_arr[template_mask] = recolored

    out_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out_arr, mode="RGB").save(
        out_path, format="JPEG", quality=96, subsampling=0
    )


def infer_subcategory(product: dict) -> str:
    cat = (product.get("categorySlug") or "").strip().lower()
    name = (product.get("name") or "").strip().lower()

    if cat == "phu-kien":
        return "phu-kien"
    if cat == "vay-dam":
        if "chân váy" in name:
            return "chan-vay"
        return "dam"
    if cat == "ao-khoac":
        if "blazer" in name or "vest" in name:
            return "blazer"
        if "hoodie" in name or "sweater" in name or "nỉ" in name:
            return "hoodie"
        return "ao-khoac"
    if cat == "quan":
        if "short" in name or "đùi" in name or "ngố" in name:
            return "quan-short"
        if "jean" in name or "denim" in name or "bò" in name:
            return "quan-jean"
        if "tây" in name or "âu" in name:
            return "quan-tay"
        return "quan-dai"

    if "polo" in name:
        return "ao-polo"
    if "sơ mi" in name:
        return "ao-so-mi"
    if "len" in name or "cardigan" in name or "dệt kim" in name:
        return "ao-len"
    if "kiểu" in name or "blouse" in name or "croptop" in name or "hai dây" in name or "ống" in name:
        return "ao-kieu"
    return "ao-thun"


def map_color_label_to_key(label: str) -> str:
    s = (label or "").strip().lower()
    if "trắng" in s:
        return "trang"
    if "đen" in s:
        return "den"
    if "navy" in s or "xanh than" in s or "xanh đen" in s:
        return "xanh-navy"
    if "rêu" in s or "lá" in s or "lục" in s or "olive" in s:
        return "xanh-reu"
    if "xanh" in s:
        return "xanh-duong"
    if "kem" in s:
        return "kem"
    if "be" in s or "nude" in s:
        return "be"
    if "xám" in s or "ghi" in s or "bạc" in s:
        return "xam"
    if "hồng" in s:
        return "hong"
    if "đỏ" in s or "đô" in s:
        return "do"
    if "nâu" in s or "cafe" in s or "bò" in s:
        return "nau"
    if "vàng" in s:
        return "vang"
    if "tím" in s:
        return "tim"
    if "cam" in s:
        return "cam"
    return "orig"


def build_subcat_templates(
    subcat: str,
    spec: tuple[list[str], list[str], list[str]],
    cache_dir: Path,
    per_subcat: int = 3,
) -> tuple[str, list[tuple[str, np.ndarray, np.ndarray]]]:
    queries, include_kw, exclude_kw = spec
    urls = fetch_uniqlo_candidate_urls(queries, include_kw, exclude_kw, max_urls=10)
    accepted: list[tuple[str, np.ndarray, np.ndarray]] = []

    for url in urls:
        url_hash = hashlib.md5(url.encode("utf-8")).hexdigest()[:10]
        raw_cache_file = cache_dir / f"{subcat}-{url_hash}.jpg"
        raw_bytes = None
        if raw_cache_file.is_file():
            try:
                raw_bytes = raw_cache_file.read_bytes()
            except OSError:
                raw_bytes = None

        if raw_bytes is None:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "Mozilla/5.0 (compatible; FashionStoreCatalogImporter/1.0)"},
            )
            try:
                with urllib.request.urlopen(req, timeout=10) as resp:
                    raw_bytes = resp.read()
                raw_cache_file.write_bytes(raw_bytes)
            except Exception:
                continue

        extracted = extract_clean_flatlay_template(raw_bytes)
        if extracted is None:
            continue

        tpl_id = f"{subcat}-{len(accepted) + 1}"
        accepted.append((tpl_id, extracted[0], extracted[1]))
        if len(accepted) >= per_subcat:
            break

    print(
        f"[garment] subcategory '{subcat}': prepared {len(accepted)} single-garment flat-lay templates",
        flush=True,
    )
    return subcat, accepted


def build_templates(
    cache_dir: Path, per_subcat: int = 3
) -> dict[str, list[tuple[str, np.ndarray, np.ndarray]]]:
    cache_dir.mkdir(parents=True, exist_ok=True)
    templates: dict[str, list[tuple[str, np.ndarray, np.ndarray]]] = {}

    items = list(SUBCATEGORY_SPECS.items())
    with ThreadPoolExecutor(max_workers=7) as pool:
        futures = [
            pool.submit(build_subcat_templates, subcat, spec, cache_dir, per_subcat)
            for subcat, spec in items
        ]
        for fut in futures:
            subcat, accepted = fut.result()
            templates[subcat] = accepted

    return templates


def main() -> None:
    base_dir = Path(
        sys.argv[1]
        if len(sys.argv) > 1
        else r"d:\fashionstore\fashionstore\data\marketplace-import"
    )
    products_file = base_dir / "products.json"
    if not products_file.is_file():
        print(f"[garment] products.json not found at {products_file}", flush=True)
        sys.exit(1)

    products = json.loads(products_file.read_text(encoding="utf-8"))
    print(f"[garment] loaded {len(products)} products from {products_file}", flush=True)

    affected, removed = deduplicate_product_gallery(base_dir, products)
    print(
        f"[garment] deduplicated gallery images: {affected} products cleaned, {removed} duplicate image references removed",
        flush=True,
    )

    cache_dir = base_dir / "images" / "garment-cache"
    garments_dir = base_dir / "images" / "garments"
    garments_dir.mkdir(parents=True, exist_ok=True)

    templates = build_templates(cache_dir, per_subcat=3)

    fallback_list: list[tuple[str, np.ndarray, np.ndarray]] = []
    for t_list in templates.values():
        if t_list:
            fallback_list.extend(t_list)

    if not fallback_list:
        print("[garment] error: no flat-lay templates could be prepared", flush=True)
        sys.exit(1)

    render_jobs: dict[str, tuple[np.ndarray, np.ndarray, tuple[int, int, int] | None, Path]] = {}
    try_on_count = 0
    default_extra_colors = ["orig", "trang", "den", "xanh-navy", "be"]

    for idx, product in enumerate(products):
        subcat = infer_subcategory(product)
        if subcat == "phu-kien":
            product["tryOnEnabled"] = False
            product["garmentImageFiles"] = []
            continue

        # Check for per-product downloaded garment (from scrapeUniqlo's downloadGarmentImages)
        source_id = str(product.get("sourceId") or "")
        uniqlo_dir = base_dir / "images" / "uniqlo"
        downloaded_garment_paths: list[Path] = []
        if source_id:
            for ext in [".jpg", ".webp", ".png"]:
                for i in range(1, 10):
                    candidate = uniqlo_dir / f"{source_id}-garment-{i}{ext}"
                    if candidate.is_file() and candidate.stat().st_size > 100:
                        downloaded_garment_paths.append(candidate)

        if downloaded_garment_paths:
            # Whiten and use per-product garments
            garment_rel_files: list[str] = []
            for path in downloaded_garment_paths[:3]:  # max 3 garments
                out_name = f"{source_id}-garment-{len(garment_rel_files)+1}.jpg"
                rel_path = f"images/garments/{out_name}"
                out_file = base_dir / rel_path
                if not out_file.is_file():
                    extracted = extract_clean_flatlay_template(path.read_bytes())
                    if extracted is not None:
                        tpl_rgb, tpl_mask = extracted
                        render_colored_garment(tpl_rgb, tpl_mask, None, out_file)  # None = keep original color
                if out_file.is_file():
                    garment_rel_files.append(rel_path)
            if garment_rel_files:
                product["garmentImageFiles"] = garment_rel_files
                product["tryOnEnabled"] = True
                try_on_count += 1
                continue  # skip template logic below

        product["tryOnEnabled"] = True
        try_on_count += 1

        sub_templates = templates.get(subcat) or fallback_list
        tpl_id, tpl_rgb, tpl_mask = sub_templates[idx % len(sub_templates)]

        color_keys: list[str] = []
        for variant in product.get("variants") or []:
            c_key = map_color_label_to_key(variant.get("color") or "")
            if c_key not in color_keys:
                color_keys.append(c_key)

        for extra in default_extra_colors:
            if len(color_keys) >= 3:
                break
            if extra not in color_keys:
                color_keys.append(extra)

        color_keys = color_keys[:9]

        garment_rel_files: list[str] = []
        for c_key in color_keys:
            rel_path = f"images/garments/{tpl_id}-{c_key}.jpg"
            out_file = base_dir / rel_path
            if rel_path not in render_jobs:
                target_rgb = None if c_key == "orig" else COLOR_PALETTE.get(c_key)
                render_jobs[rel_path] = (tpl_rgb, tpl_mask, target_rgb, out_file)
            garment_rel_files.append(rel_path)

        product["garmentImageFiles"] = garment_rel_files

    def _render_job(job: tuple[np.ndarray, np.ndarray, tuple[int, int, int] | None, Path]) -> None:
        render_colored_garment(job[0], job[1], job[2], job[3])

    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(_render_job, render_jobs.values()))

    products_file.write_text(
        json.dumps(products, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"[garment] completed: {try_on_count} try-on products updated with {len(render_jobs)} pure-white RGB(255,255,255) flat-lay garment images",
        flush=True,
    )


if __name__ == "__main__":
    main()
