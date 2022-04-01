
BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/TheValeCompiler"
  exit
fi
shift;

$BOOTSTRAPPING_VALEC_DIR/valec build coordinator=src stdlib=$BOOTSTRAPPING_VALEC_DIR/stdlib/src valecutils=../Utils/src --output_dir build -o valec $@
