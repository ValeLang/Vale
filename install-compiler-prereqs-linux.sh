# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

if [ "$1" == "" ]; then
  echo "First arg must be path to unzip LLVM to."
fi

if [ "$2" == "" ]; then
  echo "Second arg must be path to unzip a bootstrapping stable Vale compiler to."
fi

# Install misc dependencies
sudo apt install -y curl git clang cmake zlib1g-dev

# Install Java
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
sudo apt update
sudo apt install -y adoptopenjdk-11-hotspot # Java 11 / HotSpot VM

# Install SBT
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt update
sudo apt install -y sbt

# Install stable valec, for the .vale parts of the compiler
curl -L https://vale.dev/releases/ValeCompiler-0.1.3.3-Ubuntu.zip
unzip ValeCompiler-0.1.3.3-Ubuntu.zip -d $2
# Doesnt work, see https://github.com/ValeLang/Vale/issues/306
# echo 'export PATH=$PATH:~/ValeCompiler-0.1.3.3-Ubuntu' >> ~/.bashrc

# Install LLVM 11.1.0 (from https://github.com/llvm/llvm-project/releases/tag/llvmorg-11.1.0)
curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-11.1.0/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10.tar.xz --output /tmp/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10.tar.xz
mkdir -p $1
tar xf /tmp/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10.tar.xz -C $1
# Later, we'll need to feed this to a cmake command so it knows where the LLVM libraries are.
