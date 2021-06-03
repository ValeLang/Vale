# This docker image is a quick adaptaion of the Vale build-linux.sh script
FROM ubuntu:20.10

WORKDIR /vale

RUN apt-get update
RUN apt-get -y upgrade
RUN apt-get -y install wget unzip
RUN apt-get -y install python3
RUN apt-get -y install default-jre
RUN apt-get -y install clang

# Download/extract Vale Compiler
RUN wget https://vale.dev/releases/ValeCompiler-0.1.2.2-Linux.zip
RUN unzip ValeCompiler-0.1.2.2-Linux.zip -d . && rm ValeCompiler-0.1.2.2-Linux.zip
# Remove unneeded files
RUN rm -r BenchmarkRL samples README.txt 

COPY entrypoint.sh .
# Copy user's source code into the docker image
COPY src/*.vale src

ENTRYPOINT [ "sh", "entrypoint.sh"]