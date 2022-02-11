
VALEC_DIR="$1"
if [ "$VALEC_DIR" == "" ]; then
  echo "Please supply the valec directory."
  echo "Example: ~/TheValeCompiler"
  exit
fi
shift;

echo "Building path test..."
rm -rf build
$VALEC_DIR/valec build stdlib=$VALEC_DIR/stdlib/src valecutils=src vtest=src/pathadditions/test || exit 1
echo "Running path test..."
build/main || exit 1
echo "Done testing!"
