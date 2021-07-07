# This docker image is a quick adaptaion of the Vale build-linux.sh script
FROM ubuntu:20.10 AS builder

WORKDIR /build

RUN apt-get update
RUN apt-get -y upgrade

RUN apt-get -y install software-properties-common
RUN apt-get -y install git gnupg2 cmake curl
RUN apt-get -y install clang-11
RUN apt-get -y install zlib1g-dev

# Needed for multiprocessing unit tests later
RUN apt-get -y install python3-pip

# Add Java repository
RUN bash -c "curl -s https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -"
RUN add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/

# Add SBT repository
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add

RUN apt-get update
RUN apt-get install -y adoptopenjdk-11-hotspot
RUN apt-get install -y sbt

# Copy all of the source into the docker image (except for files mentioned in .dockerignore)
COPY Valestrom ./Valestrom
RUN bash -c "cd Valestrom && sbt assembly"

# Install LLVM 11
# from https://github.com/llvm/llvm-project/releases/tag/llvmorg-11.0.1
RUN curl -SL https://github.com/llvm/llvm-project/releases/download/llvmorg-11.1.0/clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10.tar.xz \
 | tar -xJC . && \
 mv clang+llvm-11.1.0-x86_64-linux-gnu-ubuntu-20.10 llvm11

ENV PATH=/build/llvm11/bin:${PATH}’
ENV LD_LIBRARY_PATH=/build/llvm11/lib:${LD_LIBRARY_PATH}’
ENV LDFLAGS="-L~/llvm11/lib -Wl,-rpath,~/llvm11/lib"
ENV CPPFLAGS="-I~/llvm11/include"
ENV PATH="~/llvm11/bin:${PATH}"

# Copy needed-files from host into docker image
COPY Midas ./Midas
COPY scripts/package-unix.sh scripts/
RUN bash -c "cd Midas && cmake -D CMAKE_CXX_COMPILER=clang++-11 -B build"
RUN bash -c "cd Midas/build && make"
RUN bash -c "cd Midas && python3 -m unittest -f -k assist"
RUN bash -c "cd scripts && ./package-unix.sh"
# RUN bash -c "cd release-unix && zip -r ValeCompiler.zip *"