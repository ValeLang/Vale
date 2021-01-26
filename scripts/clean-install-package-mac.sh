
BRANCH=working2
GITHUB=https://github.com/Verdagon/Vale

xcode-select --install
# (press ok)

# install brew:
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
# (press return)

brew install llvm@11 sbt cmake

echo 'export LLVM_DIR=/usr/local/Cellar/llvm/11.0.0_1/lib/cmake' >> ~/.zshrc
echo 'export PATH="/usr/local/opt/llvm/bin:$PATH"' >> ~/.zshrc
echo 'export LDFLAGS="-L/usr/local/opt/llvm/lib"' >> ~/.zshrc
echo 'export CPPFLAGS="-I/usr/local/opt/llvm/include"' >> ~/.zshrc
source ~/.zshrc

curl -L https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.10%2B9/OpenJDK11U-jre_x64_mac_hotspot_11.0.10_9.tar.gz --output adoptopenjdk.tar.gz
tar xzf adoptopenjdk.tar.gz
echo 'export PATH=~/jdk-11.0.10+9-jre/Contents/Home/bin:$PATH' >> ~/.zshrc

source ~/.zshrc

git clone --single-branch --branch $BRANCH $GITHUB

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

