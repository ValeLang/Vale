
VALEC_DIR="$1"
if [ "$VALEC_DIR" == "" ]; then
  echo "Please supply the valec directory."
  echo "Example: ~/TheValeCompiler"
  exit
fi
shift;

echo "Building command test..."
rm -rf build
$VALEC_DIR/valec build stdlib=src vtest=src/command/test || exit 1
echo "Running command test..."
build/main || exit 1
echo "Done testing!"

echo "Building path test..."
rm -rf build
$VALEC_DIR/valec build stdlib=src vtest=src/path/test || exit 1
echo "Running path test..."
build/main || exit 1
echo "Done testing!"

echo "Building collections test..."
rm -rf build
$VALEC_DIR/valec build stdlib=src vtest=src/collections/test || exit 1
echo "Running collections test..."
build/main || exit 1
echo "Done testing!"

echo "Building top level test..."
rm -rf build
$VALEC_DIR/valec build stdlib=src vtest=src/test $@ || exit 1
echo "Running top level test..."
build/main || exit 1
echo "Done testing!"

echo "Building stringutils test..."
rm -rf build
$VALEC_DIR/valec build stdlib=src vtest=src/stringutils/test || exit 1
echo "Running stringutils test..."
build/main || exit 1
echo "Done testing!"
