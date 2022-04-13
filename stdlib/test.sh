
VALEC_DIR="$1"
if [ "$VALEC_DIR" == "" ]; then
  echo "Please supply the valec directory."
  echo "Example: ~/TheValeCompiler"
  exit
fi
shift;

echo "Building list test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/collections/list/test || exit 1
echo "Running list test..."
build/main || exit 1
echo "Done testing!"

echo "Building command test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/command/test || exit 1
echo "Running command test..."
build/main || exit 1
echo "Done testing!"

echo "Building path test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/path/test || exit 1
echo "Running path test..."
build/main || exit 1
echo "Done testing!"

echo "Building top level test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/test $@ || exit 1
echo "Running top level test..."
build/main || exit 1
echo "Done testing!"

echo "Building stringutils test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/stringutils/test || exit 1
echo "Running stringutils test..."
build/main || exit 1
echo "Done testing!"

echo "Building hashset test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/collections/hashset/test || exit 1
echo "Running hashset test..."
build/main || exit 1
echo "Done testing!"

echo "Building hashmap test..."
$VALEC_DIR/valec build --no_std true stdlib=src vtest=src/collections/hashmap/test || exit 1
echo "Running hashmap test..."
build/main || exit 1
echo "Done testing!"
