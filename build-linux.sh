# Building the Vale Compiler (Linux)

# This script shows how to build the Vale compiler.
# This can be used from a Ubuntu VM. It would be run with sudo.
# However, we don't recommend running scripts from the internet with sudo access,
# so best just read the file and follow the instructions in the comments.

## Prerequisites

# Install Java
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
apt update
apt install adoptopenjdk-11-hotspot # Java 11 / HotSpot VM

# Install SBT
echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add
apt-get update
apt-get install -y sbt

# Install LLVM 11; download the release for your OS
# from https://github.com/llvm/llvm-project/releases/tag/llvmorg-11.0.1
curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-11.0.1/clang+llvm-11.0.1-x86_64-linux-gnu-ubuntu-20.10.tar.xz --output llvm11.tar.xz
tar xf llvm11.tar.xz

# Install dependencies
apt install curl git cmake clang-11 zlib1g-dev
# Add vars to .zshrc (or .bashrc)
echo 'export LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"' >> ~/.bashrc
echo 'export CPPFLAGS="-I~/llvm11/include"' >> ~/.bashrc
echo 'export PATH=~/llvm11/bin:$PATH' >> ~/.bashrc

# Read from .bashrc to pick up the above vars.
source ~/.bashrc


## Building the Vale Compiler

# Download the Vale code.
# git clone https://github.com/ValeLang/Vale
# or the following, which reads command line arguments.
git clone --single-branch --branch ${2:-master} ${1:-https://github.com/ValeLang/Vale}

cd Vale/Valestrom

sbt assembly

cd ../Midas

cmake -D CMAKE_CXX_COMPILER=clang++-11 -B build

cd build

make

cd ..

python3 -m unittest -f -k assist

cd ../scripts

./package-unix.sh

cd ../release-unix

zip -r ValeCompiler.zip *
