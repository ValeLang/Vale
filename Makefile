build-linux:
	@echo Building Vale Compiler for Linux...
	sh ./build-linux.sh

build-mac:
	@echo Building Vale Compiler for Mac...
	sh ./build-mac.sh

build-windows:
	@echo Building Vale Compiler for Windows...
	@echo Please read build-windows.md for more information

build-docker:
	@echo Building Vale Compiler docker image...
	@docker rmi vale_compiler:latest vale_compiler:1.0.0 || true
	@docker build -t vale_compiler:latest -t vale_compiler:1.0.0 .

run-docker:
	@echo Running Vale docker image...
	@docker stop testValeCompiler > /dev/null 2>&1 && docker rm testValeCompiler > /dev/null 2>&1 || true
	@docker run --name testValeCompiler -it vale_compiler:latest

# Explore a docker container (running or not) - this is useful for debuggin when docker image hits the fan...
# 1. create image (snapshot) from container filesystem
# 2. explore this filesystem using bash (for example)
# 3. cleanup
explore-docker:
	@docker rmi test_vale_snapshot > /dev/null 2>&1 | true
	@docker commit testValeCompiler test_vale_snapshot > /dev/null
	@docker run -it --rm test_vale_snapshot && docker rmi test_vale_snapshot