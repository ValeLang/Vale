#!/usr/bin/env bash
# Usage: bash Backend/test/extern_string_return.sh /path/to/valec [build flags...]
# Census only counts Vale allocations, so check the native heap as well.
set -euo pipefail
valec=$1
shift
source_dir=$(cd "$(dirname "$0")" && pwd)
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT

case "$(uname -s)" in
  Darwin) checker=(/usr/bin/leaks --atExit --) ;;
  Linux) checker=(valgrind --quiet --leak-check=full
                  --errors-for-leak-kinds=definite --error-exitcode=1) ;;
  *) echo "This test requires macOS leaks or Linux valgrind." >&2; exit 1 ;;
esac

for census in false true; do
  output="$build_dir/$census"
  "$valec" build "repro=$source_dir/extern_string_return.vale" "$@" \
    --output_dir "$output" --census "$census"
  "$output/main"
  "${checker[@]}" "$output/main"
done
