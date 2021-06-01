build:
	@echo Building Vale docker image...
	@docker rmi vale:latest vale:1.0.0 || true
	@docker build -t vale:latest -t vale:1.0.0 .

run:
	@echo Running Vale docker image...
	@docker stop testVale > /dev/null 2>&1 && docker rm testVale > /dev/null 2>&1 || true
	@docker run --name testVale -it vale:latest
	@docker exec testVale tail -f /var/log/vale/run.log 2> /dev/null || docker cp testVale:/var/log/vale/run.log /tmp/vale_run.log && cat /tmp/vale_run.log && rm /tmp/vale_run.log

# Explore a docker container (running or not) - this is useful for debuggin when docker image hits the fan...
# 1. create image (snapshot) from container filesystem
# 2. explore this filesystem using bash (for example)
# 3. cleanup
explore:
	@docker rmi test_vale_snapshot > /dev/null 2>&1 | true
	@docker commit testVale test_vale_snapshot > /dev/null
	@docker run --rm -it --entrypoint=/bin/bash test_vale_snapshot
# @docker run -it --rm test_vale_snapshot && docker rmi test_vale_snapshot