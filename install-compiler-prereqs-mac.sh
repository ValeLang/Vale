# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

# Installs Xcode if you haven't already.
xcode-select --install
# (press ok)

# Install Java.
curl -L https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz --output adoptopenjdk.tar.gz
tar xzf adoptopenjdk.tar.gz
# Add vars to .zshrc (or .bashrc)
echo 'export PATH=~/jdk-11.0.10+9-jre/Contents/Home/bin:$PATH' >> ~/.zshrc

# Installs brew, like said on https://brew.sh/
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
# (press return)

# Install misc dependencies
brew install llvm@11 sbt cmake

# echo 'export LLVM_DIR=/usr/local/Cellar/llvm@11/11.1.0_1/lib/cmake' >> ~/.zshrc
# echo 'export PATH="/usr/local/opt/llvm/bin:$PATH"' >> ~/.zshrc
# echo 'export LDFLAGS="-L/usr/local/opt/llvm/lib"' >> ~/.zshrc
# echo 'export CPPFLAGS="-I/usr/local/opt/llvm/include"' >> ~/.zshrc