
git pull origin $0

./scripts/ubuntu/build-compiler.sh /Outer/LLVMForVale/clang+llvm-13.0.1-x86_64-linux-gnu-ubuntu-18.04 /Outer/BootstrappingValeCompiler --test=all ./scripts/VERSION

echo Success!
echo Vale compiler can be found at /Outer/Vale/release-ubuntu/Vale-Ubuntu-0.zip
