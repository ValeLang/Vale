BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Please supply the bootstrapping valec directory."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit
fi


$BOOTSTRAPPING_VALEC_DIR/valec build tester=src stdlib=$BOOTSTRAPPING_VALEC_DIR/stdlib/src valecutils=../Utils/src --output_dir build -o testvalec || { echo 'build failed' ; exit 1; }
