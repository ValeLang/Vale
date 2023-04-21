# Can be run with: podman build --build-arg branch=stable .
# Will produce a Vale compiler.
# Special thanks to Keith Shook for the initial version!

# Like running: podman run -it docker.io/library/ubuntu:22.04
FROM ubuntu:22.04
ARG branch=stable

WORKDIR /Outer

# Install basic dependencies needed to download compiler sources
RUN apt-get update
RUN apt-get -y upgrade
RUN apt-get -y install git
RUN apt-get -y install sudo
RUN apt-get -y install curl

# Download compiler sources
RUN git clone https://github.com/Verdagon/Vale --single-branch --branch stable --depth=1


WORKDIR /Outer/Vale

# Install dependencies needed to build the compiler sources
RUN ./scripts/ubuntu/install-compiler-prereqs.sh -d -j -s -l /Outer/LLVMForVale -b /Outer/BootstrappingValeCompiler

ENTRYPOINT [ "sh", "entrypoint.sh", $branch]
