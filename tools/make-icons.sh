#!/usr/bin/env bash
# 由一张方形源图生成 Android 自适应图标（adaptive icon）。
#
#   bash tools/make-icons.sh [源图] [trimfill|direct|fullbleed|cutout]
#   源图默认取 assets/app-icon.png
#
# 四种模式，对应不同风格的 logo：
#
#   trimfill  —— 先裁掉四周「整行整列全透明」的外框，再把图形铺满画布。
#                图形本身一个像素都不动，只是去掉多余留白，图标更饱满。
#                满版圆角插画推荐用这个。
#
#   direct    —— 原图不做任何处理，直接铺满画布（留白原样保留）。
#
#   fullbleed —— 保留原图构图，用上下边缘主色生成竖向渐变补上透明的四角。
#
#   cutout    —— 图形浮在纯色底上。从四角泛洪去掉底色（只删与边缘连通的部分，
#                不会误伤图形内部的浅色区域），裁掉留白，图形缩到画布 66% 居中。
#
# 背景层统一取源图四边中点的平均色，这样圆角处的透明区域在方形蒙版下
# 也不会露出突兀的底色。所有模式都保证图形落在自适应图标的安全区内。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${1:-$ROOT/assets/app-icon.png}"
MODE="${2:-trimfill}"
OUT="$ROOT/app/src/main/res"
TMP="$ROOT/.toolchain/logo"

[ -f "$SRC" ] || { echo "找不到源图：$SRC" >&2; exit 1; }
mkdir -p "$TMP"

# cutout 模式用的底色与容差
CUTOUT_BG="#FEFDFB"
CUTOUT_FUZZ="4%"

declare -A DENSITIES=([mdpi]=108 [hdpi]=162 [xhdpi]=216 [xxhdpi]=324 [xxxhdpi]=432)

# 取源图四边中点的平均色，用作背景层
edge_average_color() {
  local src="$1" w h
  w=$(magick identify -format "%w" "$src")
  h=$(magick identify -format "%h" "$src")
  # 注意用 -clone 0：+clone 克隆的是序列里最后一张（上一段裁剪结果），会取错位置
  magick "$src" -alpha off \
    \( -clone 0 -crop "1x1+$((w / 2))+2" +repage \) \
    \( -clone 0 -crop "1x1+$((w / 2))+$((h - 3))" +repage \) \
    \( -clone 0 -crop "1x1+2+$((h / 2))" +repage \) \
    \( -clone 0 -crop "1x1+$((w - 3))+$((h / 2))" +repage \) \
    -delete 0 -evaluate-sequence mean -depth 8 \
    -format "#%[hex:p{0,0}]" info:
}

case "$MODE" in
  trimfill)
    echo "==> trimfill：裁掉四周透明边距后铺满画布"
    magick "$SRC" -trim +repage "$TMP/art.png"
    echo "    原图 $(magick identify -format '%wx%h' "$SRC") -> 裁后 $(magick identify -format '%wx%h' "$TMP/art.png")"
    BG_LAYER=$(edge_average_color "$TMP/art.png")
    FILL_RATIO="1.0"
    ;;

  direct)
    echo "==> direct：原图直接铺满画布，不做任何处理"
    magick "$SRC" -alpha off "$TMP/art.png"
    BG_LAYER=$(edge_average_color "$TMP/art.png")
    FILL_RATIO="1.0"
    ;;

  fullbleed)
    echo "==> fullbleed：保留构图，用上下边缘主色渐变补角"
    W=$(magick identify -format "%w" "$SRC")
    H=$(magick identify -format "%h" "$SRC")
    TOP_HEX=$(magick "$SRC" -format "%[hex:p{$((W / 2)),4}]" info: | cut -c1-6)
    BOTTOM_HEX=$(magick "$SRC" -format "%[hex:p{$((W / 2)),$((H - 5))}]" info: | cut -c1-6)
    echo "    上边缘 #$TOP_HEX / 下边缘 #$BOTTOM_HEX"
    magick -size "${W}x${H}" gradient:"#${TOP_HEX}-#${BOTTOM_HEX}" "$TMP/bg.png"
    magick "$TMP/bg.png" "$SRC" -composite -alpha off "$TMP/art.png"
    # 中间色：先压成 1x1 再取（必须先 -alpha off 且定成 8 位，否则会拿到 16 位 RGBA）
    BG_LAYER="#$(magick "$TMP/art.png" -alpha off -resize '1x1!' -depth 8 \
      -format "%[hex:p{0,0}]" info: | cut -c1-6)"
    FILL_RATIO="1.0"
    ;;

  cutout)
    echo "==> cutout：泛洪去背景并裁边"
    magick "$SRC" -alpha set -fuzz "$CUTOUT_FUZZ" -fill none \
      -floodfill +0+0 "$CUTOUT_BG" -trim +repage "$TMP/art.png"
    BG_LAYER="$CUTOUT_BG"
    FILL_RATIO="0.66"
    ;;

  *)
    echo "未知模式：$MODE（可选 trimfill / direct / fullbleed / cutout）" >&2
    exit 1
    ;;
esac

echo "    背景层 $BG_LAYER"

echo "==> 生成各密度前景（占画布 $FILL_RATIO）"
for density in "${!DENSITIES[@]}"; do
  canvas="${DENSITIES[$density]}"
  art=$(awk -v c="$canvas" -v r="$FILL_RATIO" 'BEGIN { printf "%d", c * r + 0.5 }')
  dir="$OUT/drawable-$density"
  mkdir -p "$dir"
  magick "$TMP/art.png" \
    -resize "${art}x${art}" \
    -background none -gravity center -extent "${canvas}x${canvas}" \
    "$dir/ic_launcher_foreground.png"
  printf '  %-9s %spx 画布，图形 %spx\n' "$density" "$canvas" "$art"
done

cat > "$OUT/drawable/ic_launcher_background.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<!--
  自适应图标的背景层。由 tools/make-icons.sh 生成（模式 $MODE），
  颜色取自源图四边中点的平均色。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="$BG_LAYER"
        android:pathData="M0,0h108v108h-108z" />
</vector>
XML

rm -f "$OUT/drawable/ic_launcher_foreground.xml"
echo "==> 完成。前景 $OUT/drawable-*/ic_launcher_foreground.png，背景 $BG_LAYER"
