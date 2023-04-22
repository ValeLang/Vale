
echo git pull origin $VALE_BRANCH
git pull origin $VALE_BRANCH

echo ./scripts/ubuntu/build-compiler.sh /Outer/LLVMForVale/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04 /Outer/BootstrappingValeCompiler --test=all ./scripts/VERSION
./scripts/ubuntu/build-compiler.sh /Outer/LLVMForVale/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04 /Outer/BootstrappingValeCompiler --test=all ./scripts/VERSION

echo Success!
echo Vale compiler can be found at: /Outer/Vale/release-ubuntu/Vale-Ubuntu-0.zip
