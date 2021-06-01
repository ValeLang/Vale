#!/bin/bash

# Compile Vale
python3 /vale/valec.py build src/*.vale
mkdir -p /var/log/vale
echo "Running code..."
/vale/a.out > /var/log/vale/run.log 2>/var/log/vale/err.log