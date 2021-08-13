# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

BOOTSTRAPPING_VALEC_DIR="$1"
if [ "$BOOTSTRAPPING_VALEC_DIR" == "" ]; then
  echo "First arg must be path to unzip a bootstrapping stable Vale compiler to."
  echo "Example: ~/ValeCompiler-0.1.3.3-Ubuntu"
  exit
fi

# Installs Xcode if you haven't already.
echo Installing Xcode...
xcode-select --install
# (press ok)

# Install Java.
echo Installing Java...
curl -L https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz --output adoptopenjdk.tar.gz
tar xzf adoptopenjdk.tar.gz
# Add vars to .zshrc (or .bashrc)
echo 'export PATH=~/jdk-11.0.10+9-jre/Contents/Home/bin:$PATH' >> ~/.zshrc

# Installs brew, like said on https://brew.sh/
echo Installing brew...
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
# (press return)

# Install stable valec, for the .vale parts of the compiler
echo "Downloading and unzipping stable bootstrapping valec to $BOOTSTRAPPING_VALEC_DIR..."
curl -L https://vale.dev/releases/ValeCompiler-0.1.3.3-Mac.zip
unzip ValeCompiler-0.1.3.3-Mac.zip
echo 'export PATH=$PATH:~/ValeCompiler-0.1.3.3-Mac' >> ~/.zshrc

# Install misc dependencies
echo "Downloading and unzipping depdendencies and LLVM..."
brew install llvm@11 sbt cmake
echo 'export PATH=$PATH:/usr/local/Cellar/llvm@11/11.1.0_2/bin' >> ~/.zshrc
