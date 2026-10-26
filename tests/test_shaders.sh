#!/usr/bin/env bash
# Validates every GLSL ES 3.00 shader with glslangValidator.
set -e
cd "$(dirname "$0")/.."
fail=0
for f in app/src/main/assets/shaders/*.vert app/src/main/assets/shaders/*.frag; do
  if out=$(glslangValidator -S "${f##*.}" "$f" 2>&1); then echo "OK   $f"; else echo "FAIL $f"; echo "$out"; fail=1; fi
done
exit $fail
