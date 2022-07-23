#!/usr/bin/env bash

# This script installs all the prerequisites for building the Vale compiler.

# WARNING: There are sudo commands below! Review all sudo commands before running them.

set -euo pipefail


usage() {
  echo "Usage: $(basename $0) [options]"
  echo -e "\nOptions:"
  echo " -j        install Java from JFrog.io APT repository"
  echo " -s        install SBT from scala-sbt.org APT repository"
  echo " -b <DIR>  install Vale bootstrap compiler to specified directory"
  echo " -l <DIR>  install LLVM to specified directory"
  echo " -h        display this help and exit"
}

bail() {
  usage
  exit 1
}

INSTALL_JAVA=0
INSTALL_SBT=0
INSTALL_LLVM=0
BOOTSTRAPPING_VALEC_DIR=""

while getopts ":hjslb:" opt; do
  case ${opt} in
    h )
      usage
      exit 0
      ;;
    j )
      INSTALL_JAVA=1
      ;;
    s )
      INSTALL_SBT=1
      ;;
    l )
      INSTALL_LLVM=1
      ;;
    b )
      BOOTSTRAPPING_VALEC_DIR="${OPTARG}"
      ;;
    * )
      bail
      ;;
  esac
done

if [[ $INSTALL_JAVA == 0 && $INSTALL_SBT == 0 && $INSTALL_LLVM == 0 && $BOOTSTRAPPING_VALEC_DIR == "" ]]; then
  echo "Nothing to do! Quitting."
  bail
fi

TEXT_GREEN=`tput -T xterm-256color setaf 2`
TEXT_RESET=`tput -T xterm-256color sgr0`

# Install misc dependencies
echo "${TEXT_GREEN}Installing dependencies...${TEXT_RESET}"

sudo apt update -y
sudo apt install -y software-properties-common curl git cmake zlib1g-dev zip unzip wget

# Install Java
if [[ $INSTALL_JAVA != 0 ]]; then
  echo -e "\n${TEXT_GREEN}Installing Java...${TEXT_RESET}"
  wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
  sudo add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/
  sudo apt update
  sudo apt install -y adoptopenjdk-11-hotspot # Java 11 / HotSpot VM
fi

# Install SBT
if [[ $INSTALL_SBT != 0 ]]; then
  echo -e "\n${TEXT_GREEN}Installing sbt...${TEXT_RESET}"
  echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
  echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
  curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
  sudo apt update
  sudo apt install -y sbt
fi

# Install bootstrap compiler
if [[ $BOOTSTRAPPING_VALEC_DIR != "" ]]; then
  echo -e "\n${TEXT_GREEN}Downloading and unzipping stable bootstrapping valec to $BOOTSTRAPPING_VALEC_DIR...${TEXT_RESET}"
  # Install stable valec, for the .vale parts of the compiler
  curl -L https://github.com/ValeLang/Vale/releases/download/v0.2.0/Vale-Ubuntu-0.2.0.11.zip -o /tmp/BootstrappingValeCompiler.zip
  unzip /tmp/BootstrappingValeCompiler.zip -d $BOOTSTRAPPING_VALEC_DIR
  # Doesnt work, see https://github.com/ValeLang/Vale/issues/306
  # echo 'export PATH=$PATH:~/ValeCompiler-0.1.3.3-Ubuntu' >> ~/.bashrc
fi

# Install LLVM
if [[ $INSTALL_SBT != "" ]]; then
  echo -e "\n${TEXT_GREEN}Installing LLVM...${TEXT_RESET}"
  wget https://apt.llvm.org/llvm.sh
  chmod +x llvm.sh
  sudo ./llvm.sh 14
fi
