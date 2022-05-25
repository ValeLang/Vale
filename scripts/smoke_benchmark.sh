
OLD_COMMAND="$1"
if [ "$OLD_COMMAND" == "" ] ; then
  echo "First arg should be old command"
  exit 1
fi
shift;

NEW_COMMAND="$1"
if [ "$NEW_COMMAND" == "" ] ; then
  echo "First arg should be old command"
  exit 1
fi
shift;

# Example:
#   ~/Vale/scripts/smoke_benchmark.sh '/Users/verdagon/Vale/release-mac-all-regions/backend --verify --output_dir build build/vast/stdlib.optutils.vast build/vast/stdlib.collections.hashset.vast build/vast/__vale.vast build/vast/stdlib.math.vast build/vast/stdlib.stringutils.vast build/vast/benchmarkrl.vast build/vast/stdlib.vast build/vast/stdlib.collections.hashmap.vast build/vast/stdlib.collections.list.vast' '/Users/verdagon/Vale/release-mac-one-region/backend --verify --output_dir build build/vast/stdlib.optutils.vast build/vast/stdlib.collections.hashset.vast build/vast/__vale.vast build/vast/stdlib.math.vast build/vast/stdlib.stringutils.vast build/vast/benchmarkrl.vast build/vast/stdlib.vast build/vast/stdlib.collections.hashmap.vast build/vast/stdlib.collections.list.vast'
# though it assumes we previously ran:
#   ~/Vale/release-mac-all-regions/valec build --run_backend false benchmarkrl=src --output_dir build

echo "Trying both first, to see if they actually work."
echo "Running old command..."
/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 || { echo "Running old command failed!" && exit 1; }
echo "Running new command..."
/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 || { echo "Running new command failed!" && exit 1; }
echo "Success, starting benchmarks!"

for i in {1..100}
do
  echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
  echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
done