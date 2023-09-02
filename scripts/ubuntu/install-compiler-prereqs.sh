#!/usr/bin/env bash

# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

set -euo pipefail


usage() {
  echo "Usage: $(basename $0) [options]"
  echo -e "\nOptions:"
  echo " -d        install basic build tools from APT"
  echo " -j        install Java from JFrog.io APT-get repository"
  echo " -s        install SBT from scala-sbt.org APT-get repository"
  echo " -b <DIR>  install Vale bootstrap compiler to specified directory"
  echo " -l <DIR>  install LLVM to specified directory"
  echo " -h        display this help and exit"
}

bail() {
  usage
  exit 1
}

CLANG_VERSION="16.0.0"
CLANG_UBUNTU_VERSION="18.04"

INSTALL_DEBS=0
INSTALL_JAVA=0
INSTALL_SBT=0
LLVM_DIR=""
BOOTSTRAPPING_VALEC_DIR=""

while getopts ":hdjsb:l:" opt; do
  case ${opt} in
    h )
      usage
      exit 0
      ;;
    d )
      INSTALL_DEBS=1
      ;;
    j )
      INSTALL_JAVA=1
      ;;
    s )
      INSTALL_SBT=1
      ;;
    l )
      LLVM_DIR="${OPTARG}"
      ;;
    b )
      BOOTSTRAPPING_VALEC_DIR="${OPTARG}"
      ;;
    * )
      bail
      ;;
  esac
done

if [[ $INSTALL_JAVA == 0 && $INSTALL_SBT == 0 && $BOOTSTRAPPING_VALEC_DIR == "" && $LLVM_DIR == "" ]]; then
  echo "Nothing to do! Quitting."
  bail
fi

TEXT_GREEN=`tput -T xterm-256color setaf 2`
TEXT_RESET=`tput -T xterm-256color sgr0`

# Install misc dependencies
echo "${TEXT_GREEN}Installing dependencies...${TEXT_RESET}"

if [[ $INSTALL_DEBS != 0 ]]; then
  sudo apt-get --fix-missing update -y
  sudo apt-get install -y software-properties-common curl git clang cmake zlib1g-dev zip unzip wget
fi

# Install Java
if [[ $INSTALL_JAVA != 0 ]]; then
  echo -e "\n${TEXT_GREEN}Installing Java...${TEXT_RESET}"
  sudo apt-get install -y wget apt-transport-https gnupg
  mkdir -p /etc/apt/keyrings
  wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/keyrings/adoptium.asc
  echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
  sudo apt-get update
  sudo apt-get install -y temurin-20-jdk
fi

# Install SBT
if [[ $INSTALL_SBT != 0 ]]; then
  echo -e "\n${TEXT_GREEN}Installing sbt...${TEXT_RESET}"
  sudo apt-get update
  sudo apt-get install -y apt-transport-https curl gnupg -yqq
  echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
  echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
  curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo -H gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import
  sudo chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg
  sudo apt-get update
  sudo apt-get install -y sbt
fi

# Install bootstrap compiler
if [[ $BOOTSTRAPPING_VALEC_DIR != "" ]]; then
  echo -e "\n${TEXT_GREEN}Downloading and unzipping stable bootstrapping valec to $BOOTSTRAPPING_VALEC_DIR...${TEXT_RESET}"
  sudo apt-get update
  sudo apt-get install -y unzip
  # Install stable valec, for the .vale parts of the compiler
  curl -L https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Ubuntu-0.2.0.27.zip -o /tmp/BootstrappingValeCompiler.zip
  unzip /tmp/BootstrappingValeCompiler.zip -d $BOOTSTRAPPING_VALEC_DIR
  # Doesnt work, see https://github.com/ValeLang/Vale/issues/306
  # echo 'export PATH=$PATH:~/ValeCompiler-0.1.3.3-Ubuntu' >> ~/.bashrc
fi

# Install LLVM
if [[ $LLVM_DIR != "" ]]; then
  echo -e "\n${TEXT_GREEN}Downloading and unzipping LLVM to $LLVM_DIR...${TEXT_RESET}"
  sudo apt-get update
  sudo apt-get install -y tar xz-utils
  curl -L https://github.com/llvm/llvm-project/releases/download/llvmorg-$CLANG_VERSION/clang+llvm-$CLANG_VERSION-x86_64-linux-gnu-ubuntu-$CLANG_UBUNTU_VERSION.tar.xz --output /tmp/clang+llvm-$CLANG_VERSION-x86_64-linux-gnu-ubuntu-$CLANG_UBUNTU_VERSION.tar.xz
  mkdir -p $LLVM_DIR
  tar xf /tmp/clang+llvm-$CLANG_VERSION-x86_64-linux-gnu-ubuntu-$CLANG_UBUNTU_VERSION.tar.xz -C $LLVM_DIR
  # Later, we'll need to feed this to a cmake command so it knows where the LLVM libraries are.
fi


