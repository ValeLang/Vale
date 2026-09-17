
VALEC_DIR="$1"
if [ "$VALEC_DIR" == "" ]; then
  echo "Please supply the valec directory."
  echo "Example: ~/TheValeCompiler"
  exit
fi
shift;

echo "Building command2 test..."
rm -rf build
$VALEC_DIR/valec build valecutils=src vtest=src/command2/test || exit 1
echo "Running command2 test..."
build/main || exit 1
echo "Done testing!"
