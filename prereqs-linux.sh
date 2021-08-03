# Building the Vale Compiler (Linux)

# This script shows how to build the Vale compiler.
# This can be used from a Ubuntu VM.
# WARNING: There are sudo commands below! Review all sudo commands before running them.

## Prerequisites

# Install Java
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
sudo apt update
sudo apt install adoptopenjdk-11-hotspot # Java 11 / HotSpot VM

# Install SBT
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install -y sbt

# Install LLVM 11.1.0; download the release for your OS
# from https://github.com/llvm/llvm-project/releases/tag/llvmorg-11.1.0
curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-11.1.0/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10.tar.xz --output ~/llvm11.tar.xz
tar xf ~/llvm11.tar.xz

# Install dependencies
sudo apt install curl git cmake zlib1g-dev
# Add vars to .zshrc (or .bashrc)
echo 'export LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"' >> ~/.bashrc
echo 'export CPPFLAGS="-I~/llvm11/include"' >> ~/.bashrc
echo 'export PATH=~/llvm11/bin:$PATH' >> ~/.bashrc

# Read from .bashrc to pick up the above vars.
source ~/.bashrc

