#!/usr/bin/env sh

if ! brew list llvm@7 &>/dev/null; then
  echo install llvm@7 with \`brew install llvm@7\`
  exit 1
fi

printf "paste the below into Preferences -> "
printf "Build, Execution, Deployment -> CMake -> Environment: \n"
brew info llvm@7 | grep -iE "(CPPFLAGS|LDFLAGS)=" \
  | grep -iEv "export LDFLAGS" \
  | sed -E "s/export//" | sed -E "s/^[[:space:]]*//"
printf "PATH=$(brew --prefix llvm@7)/bin:"
printf "$(cat /etc/paths | tr "\n" ":" | sed -nE 's/:$// p')\n"
