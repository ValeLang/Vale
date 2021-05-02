# Building the Vale Compiler (Mac)

# This script shows how to build the Vale compiler.
# This can be used from a Mac VM. It would be run with sudo.
# However, we don't recommend running scripts from the internet with sudo access,
# so best just read the file and follow the instructions in the comments.

## Prerequisites

# Installs Xcode if you haven't already.
xcode-select --install
# (press ok)

# Installs brew, like said on https://brew.sh/
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
# (press return)

# Install Java.
curl -L https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz --output adoptopenjdk.tar.gz
tar xzf adoptopenjdk.tar.gz
# Add vars to .zshrc (or .bashrc)
echo 'export PATH=~/jdk-11.0.10+9-jre/Contents/Home/bin:$PATH' >> ~/.zshrc

# Install dependencies
brew install llvm@11 sbt cmake clang-11
# Add vars to .zshrc (or .bashrc)
echo 'export LLVM_DIR=/usr/local/Cellar/llvm/11.0.0_1/lib/cmake' >> ~/.zshrc
echo 'export PATH="/usr/local/opt/llvm/bin:$PATH"' >> ~/.zshrc
echo 'export LDFLAGS="-L/usr/local/opt/llvm/lib"' >> ~/.zshrc
echo 'export CPPFLAGS="-I/usr/local/opt/llvm/include"' >> ~/.zshrc

# Load from .zshrc (or .bashrc) so the current env picks up the above vars.
source ~/.zshrc

## Building the Vale Compiler

# Download the Vale code.
# git clone https://github.com/ValeLang/Vale
# or the following, which reads command line arguments.
git clone --single-branch --branch ${2:-master} ${1:-https://github.com/ValeLang/Vale}

cd Vale/Valestrom

sbt assembly

cd ../Midas

cmake -D CMAKE_CXX_COMPILER=/usr/local/opt/llvm/bin/clang++ -B build

cd build

make

cd ..

python3 -m unittest -f -k assist

cd ../scripts

./package-unix.sh

cd ../release-unix

zip ValeCompiler.zip *
