#!/usr/bin/bash


python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override unsafe-fast
mv ./a.out unsafefast
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override assist
mv ./a.out assist
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override naive-rc
mv ./a.out naiverc
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override resilient-v0
mv ./a.out resilientv0
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override resilient-v1
mv ./a.out resilientv1
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --gen-heap --region-override resilient-v2
mv ./a.out resilientv2

# 1. Copy this output into an editor like sublime
# 2. Sort it to group the times by mode
# 3. Take the best number for each

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
