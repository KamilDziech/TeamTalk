#!/usr/bin/env bash
set -euo pipefail
RES="C:/ekotak app/TeamTalk/app/src/main/res"
GREEN="#44D62C"; INK="#080808"

BIG_C="325 370"; BIG_N=10; BIG_P=0
BIG_T='M307,224 H343 A5,5 0 0 1 348,229 V261 A5,5 0 0 1 343,266 H307 A5,5 0 0 1 302,261 V229 A5,5 0 0 1 307,224 Z'
BIG_B='M207,370 a118,118 0 1 0 236,0 a118,118 0 1 0 -236,0 Z'
BIG_H='M263,370 a62,62 0 1 0 124,0 a62,62 0 1 0 -124,0 Z'

MED_C="520 385"; MED_N=9; MED_P=20
MED_T='M508,287 H532 A4,4 0 0 1 536,291 V317 A4,4 0 0 1 532,321 H508 A4,4 0 0 1 504,317 V291 A4,4 0 0 1 508,287 Z'
MED_B='M444,385 a76,76 0 1 0 152,0 a76,76 0 1 0 -152,0 Z'
MED_H='M484,385 a36,36 0 1 0 72,0 a36,36 0 1 0 -72,0 Z'

SML_C="430 548"; SML_N=8; SML_P=0
SML_T='M421,471 H439 A4,4 0 0 1 443,475 V496 A4,4 0 0 1 439,500 H421 A4,4 0 0 1 417,496 V475 A4,4 0 0 1 421,471 Z'
SML_B='M372,548 a58,58 0 1 0 116,0 a58,58 0 1 0 -116,0 Z'
SML_H='M403,548 a27,27 0 1 0 54,0 a27,27 0 1 0 -54,0 Z'

# teeth <cx> <cy> <n> <phase> <toothPath> <extraAttrs>
teeth() {
  local cx=$1 cy=$2 n=$3 ph=$4 d=$5 attrs=$6 i a
  for ((i=0;i<n;i++)); do
    a=$(awk -v p="$ph" -v i="$i" -v n="$n" 'BEGIN{printf "%.2f", p + i*360/n}')
    printf '        <group android:rotation="%s" android:pivotX="%s" android:pivotY="%s">\n' "$a" "$cx" "$cy"
    printf '            <path android:pathData="%s" %s />\n' "$d" "$attrs"
    printf '        </group>\n'
  done
}

arcs() { # <color>
  local c=$1
  local common="android:fillColor=\"#00000000\" android:strokeColor=\"$c\" android:strokeWidth=\"26\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\""
  for d in \
    'M118.0,324.4 A292,292 0 0 1 617.0,204.6' \
    'M556.6,190.7 L617.0,204.6 L609.4,143.0' \
    'M674.4,499.9 A292,292 0 0 1 125.6,499.9' \
    'M176.4,535.5 L125.6,499.9 L109.5,559.8' \
    'M114.4,460.7 A292,292 0 0 1 109.6,369.5' \
    'M664.6,276.6 A292,292 0 0 1 689.8,364.4'
  do printf '        <path android:pathData="%s" %s />\n' "$d" "$common"; done
}

# ── warstwa kolorowa: aureola w kolorze tła + korpus w czerni ────────────────
gear_color() { # <cx> <cy> <n> <phase> <tooth> <body> <hole>
  local cx=$1 cy=$2 n=$3 ph=$4 t=$5 b=$6 h=$7
  local halo="android:fillColor=\"$GREEN\" android:strokeColor=\"$GREEN\" android:strokeWidth=\"18\" android:strokeLineJoin=\"round\""
  printf '        <path android:pathData="%s" %s />\n' "$b" "$halo"
  teeth "$cx" "$cy" "$n" "$ph" "$t" "$halo"
  printf '        <path android:pathData="%s" android:fillColor="%s" />\n' "$b" "$INK"
  teeth "$cx" "$cy" "$n" "$ph" "$t" "android:fillColor=\"$INK\""
  printf '        <path android:pathData="%s" android:fillColor="%s" />\n' "$h" "$GREEN"
}

# ── warstwa monochrome: bez aureoli, otwór wycięty regułą evenOdd ────────────
gear_mono() {
  local cx=$1 cy=$2 n=$3 ph=$4 t=$5 b=$6 h=$7
  teeth "$cx" "$cy" "$n" "$ph" "$t" 'android:fillColor="#FFFFFF"'
  printf '        <path android:pathData="%s %s" android:fillColor="#FFFFFF" android:fillType="evenOdd" />\n' "$b" "$h"
}

layer() { # <gearFn> <arcColor>
  local fn=$1 ac=$2
  cat <<XML
<?xml version="1.0" encoding="utf-8"?>
<!--
  Ikona ekotak: trzy zazębione koła w pętli dwóch strzałek. Geometria w układzie
  800x800 (ten sam plik źródłowy co web/public/ekotak-icon.svg). Grupa "safe"
  zmniejsza znak do strefy bezpiecznej adaptive icon (72/108 dp).
  WYGENEROWANE przez design/icons/gen-launcher-icon.sh — nie edytuj ręcznie.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="800"
    android:viewportHeight="800">
    <group android:scaleX="0.86" android:scaleY="0.86" android:pivotX="400" android:pivotY="400">
$(arcs "$ac")
$($fn $MED_C $MED_N $MED_P "$MED_T" "$MED_B" "$MED_H")
$($fn $BIG_C $BIG_N $BIG_P "$BIG_T" "$BIG_B" "$BIG_H")
$($fn $SML_C $SML_N $SML_P "$SML_T" "$SML_B" "$SML_H")
    </group>
</vector>
XML
}

mkdir -p "$RES/drawable"
layer gear_color "$INK"    > "$RES/drawable/ic_launcher_foreground.xml"
layer gear_mono  "#FFFFFF" > "$RES/drawable/ic_launcher_monochrome.xml"

cat > "$RES/drawable/ic_launcher_background.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<!-- Pełne pole zieleni Pantone 802 C z księgi znaku EKOTAK v1.2023. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z" android:fillColor="$GREEN" />
</vector>
XML
echo "OK"
