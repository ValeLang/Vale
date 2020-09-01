#!/usr/bin/bash

echo "Welcome to the benchmark!"
echo "Hold on while we compile the benchmark program in different modes..."
echo

VSTL_DEPS="../vstl/generics/arrayutils.vale ../vstl/genericvirtuals/optingarraylist.vale ../vstl/genericvirtuals/opt.vale ../vstl/genericvirtuals/hashmap.vale ../vstl/genericvirtuals/hashset.vale ../vstl/utils.vale ../vstl/printutils.vale ../vstl/castutils.vale"

python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override unsafe-fast
mv ./a.out unsafefast
python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override assist
mv ./a.out assist
python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override naive-rc
mv ./a.out naiverc
python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override resilient-v0
mv ./a.out resilientv0
python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override resilient-v1
mv ./a.out resilientv1
python3.8 ../valec.py $VSTL_DEPS *.vale --gen-heap --region-override resilient-v2
mv ./a.out resilientv2

echo
echo "Done compiling! Proceeding to benchmark."
echo "1. For more accurate measurements, don't run any other programs while this is going."
echo "2. You'll see some output below as it's going."
echo "3. Copy that output into an editor like sublime."
echo "4. Sort lines. This will group the times by mode."
echo "5. Take the best number for each."
echo "6. Compare them to unsafe-fast, which is the mode with zero overhead."
echo

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
echo "resilient-v2: $(/usr/bin/time -f "%e" ./resilientv2 2>&1)"

echo
echo "Done!"
