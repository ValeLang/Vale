# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

LLVM_DIR="$1"
if [ "$LLVM_DIR" == "" ]; then
  echo "First arg must be path to unzip LLVM to."
  echo "Example: ~/clang+llvm-13.0.0-x86_64-linux-gnu-ubuntu-20.10"
  exit
fi

BOOTSTRAPPING_VALEC_DIR="$2"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "Second arg must be path to unzip a bootstrapping stable Vale compiler to."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit
fi


# Install misc dependencies
echo "Installing dependencies..."
apt install sudo
sudo apt update -y
sudo apt install -y software-properties-common curl git clang cmake zlib1g-dev zip unzip wget

# Install Java
echo "Installing java..."
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
sudo apt update
sudo apt install -y adoptopenjdk-11-hotspot # Java 11 / HotSpot VM

# Install SBT
echo "Installing sbt..."
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt update
sudo apt install -y sbt

echo "Downloading and unzipping stable bootstrapping valec to $BOOTSTRAPPING_VALEC_DIR..."
# Install stable valec, for the .vale parts of the compiler
curl -L https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Ubuntu-0.2.0.11.zip -o /tmp/BootstrappingValeCompiler.zip
unzip /tmp/BootstrappingValeCompiler.zip -d $BOOTSTRAPPING_VALEC_DIR
# Doesnt work, see https://github.com/ValeLang/Vale/issues/306
# echo 'export PATH=$PATH:~/ValeCompiler-0.1.3.3-Ubuntu' >> ~/.bashrc

echo "Downloading and unzipping LLVM to $LLVM_DIR..."
# Install LLVM 13.0.0 (from https://github.com/llvm/llvm-project/releases/tag/llvmorg-13.0.0)
curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-13.0.1/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04.tar.xz --output /tmp/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04.tar.xz
mkdir -p $LLVM_DIR
tar xf /tmp/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04.tar.xz -C $LLVM_DIR
# Later, we'll need to feed this to a cmake command so it knows where the LLVM libraries are.
