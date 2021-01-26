
BRANCH=working2
GITHUB=https://github.com/Verdagon/Vale

apt install -y curl git cmake clang-11 zlib1g-dev
source ~/.bashrc # (or restart terminal so later cmake can see clang-11)

wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
apt update
apt install -y adoptopenjdk-11-hotspot # Java 11 / HotSpot VM

echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add
apt-get update
apt-get install -y sbt

# From https://github.com/llvm/llvm-project/releases/tag/llvmorg-11.0.1 download the release for your OS.
curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-11.0.1/clang+llvm-11.0.1-x86_64-linux-gnu-ubuntu-20.10.tar.xz --output llvm11.tar.xz

tar xf llvm11.tar.xz


echo 'export LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"' >> ~/.bashrc
echo 'export CPPFLAGS="-I~/llvm11/include"' >> ~/.bashrc
echo 'export PATH=~/llvm11/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

git clone --single-branch --branch $BRANCH $GITHUB

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
zip ValeCompiler.zip *

