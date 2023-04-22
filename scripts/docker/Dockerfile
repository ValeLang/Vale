# Can be run like:
#   git clone https://github.com/Verdagon/Vale --single-branch --branch $BRANCH --depth=1
#   podman build --build-arg VALE_DIR=. --file scripts/docker/Dockerfile
# Can retrieve the built compiler with:
#   podman cp CONTAINER:/Vale/release-ubuntu/Vale-Ubuntu-0.zip Vale-Ubuntu-0.zip
# Can re-build it with a new Vale repo by doing:
#   podman build --build-arg VALE_DIR=. --file scripts/docker/Dockerfile --build-arg RECOPY_FOR_BUILD_DUMMY=`date +%s`
# Can compile a Vale program with:
#   podman cp (my dir) CONTAINER:/u/myprogramdir
#   podman run -it (the container) build myprogram=myprogramdir
# Thanks to Keith Shook for the initial version!

# Like running: podman run -it docker.io/library/ubuntu:22.04
FROM ubuntu:22.04
ARG VALE_DIR

# Install basic dependencies needed to download compiler sources
RUN apt-get update
RUN apt-get -y upgrade
RUN apt-get -y install git
RUN apt-get -y install sudo
RUN apt-get -y install curl

# Copy the install prereqs script from the host.
COPY ${VALE_DIR}/scripts/ubuntu/install-compiler-prereqs.sh /Vale/scripts/ubuntu/install-compiler-prereqs.sh

# Install dependencies needed to build the compiler sources
RUN /Vale/scripts/ubuntu/install-compiler-prereqs.sh -d -j -s -l /LLVMForVale -b /BootstrappingValeCompiler

# Copy the Vale folder from the host again, handy for debugging the build step further down.
COPY ${VALE_DIR} /Vale

WORKDIR /Vale

# Build the compiler sources into a compiler
RUN ./scripts/ubuntu/build-compiler.sh /LLVMForVale/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04 /BootstrappingValeCompiler --test=all ./scripts/VERSION

WORKDIR /u

# Make it so we can run the image to compile a Vale program.
ENTRYPOINT ["/Vale/release-ubuntu/valec"]
