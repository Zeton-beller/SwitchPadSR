#!/usr/bin/env bash
# Validates every GLSL ES 3.00 shader with glslangValidator.
set -e
cd "$(dirname "$0")/.."
fail=0
for f in app/src/main/assets/shaders/*.vert app/src/main/assets/shaders/*.frag app/src/main/assets/shaders/*.comp; do
  if [ -e "$f" ]; then
    if out=$(glslangValidator -S "${f##*.}" "$f" 2>&1); then echo "OK   $f"; else echo "FAIL $f"; echo "$out"; fail=1; fi
  fi
done
exit $fail
