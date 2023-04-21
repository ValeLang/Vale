# Can be run with: podman build --build-arg BRANCH=stable .
# Will produce a Vale compiler.
# Special thanks to Keith Shook for the initial version!

# Like running: podman run -it docker.io/library/ubuntu:22.04
FROM ubuntu:22.04
ARG BRANCH=stable

WORKDIR /Outer

# Install basic dependencies needed to download compiler sources
RUN apt-get update
RUN apt-get -y upgrade
RUN apt-get -y install git
RUN apt-get -y install sudo
RUN apt-get -y install curl

# Download compiler sources
RUN git clone https://github.com/Verdagon/Vale --single-branch --branch $BRANCH --depth=1


WORKDIR /Outer/Vale

# Install dependencies needed to build the compiler sources
RUN ./scripts/ubuntu/install-compiler-prereqs.sh -d -j -s -l /Outer/LLVMForVale -b /Outer/BootstrappingValeCompiler

# Build the compiler sources into a compiler
RUN ./scripts/ubuntu/build-compiler.sh /Outer/LLVMForVale/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04 /Outer/BootstrappingValeCompiler --test=all ./scripts/VERSION

# Create the command that will update from github and do a rebuild
ENV VALE_BRANCH=$BRANCH
COPY entrypoint.sh .
ENTRYPOINT ["sh", "entrypoint.sh"]
