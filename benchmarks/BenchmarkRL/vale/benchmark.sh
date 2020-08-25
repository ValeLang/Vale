#!/usr/bin/bash


python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --region-override unsafe-fast
mv ./a.out unsafefast
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --region-override assist
mv ./a.out assist
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --region-override naive-rc
mv ./a.out naiverc
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --region-override resilient-v0
mv ./a.out resilientv0
python3.8 ../../../Midas/valec.py ../../../Valestrom/Samples/test/main/resources/generics/arrayutils.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/optingarraylist.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/opt.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashmap.vale ../../../Valestrom/Samples/test/main/resources/genericvirtuals/hashset.vale ../../../Valestrom/Samples/test/main/resources/utils.vale ../../../Valestrom/Samples/test/main/resources/printutils.vale ../../../Valestrom/Samples/test/main/resources/castutils.vale src/*.vale --region-override resilient-v1
mv ./a.out resilientv1

# 1. Copy this output into an editor like sublime
# 2. Sort it to group the times by mode
# 3. Remove the lowest and highest of each
# 4. Average the numbers

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"

echo "unsafe-fast: $(/usr/bin/time -f "%e" ./unsafefast 2>&1)"
echo "naive-rc: $(/usr/bin/time -f "%e" ./naiverc 2>&1)"
echo "assist: $(/usr/bin/time -f "%e" ./assist 2>&1)"
echo "resilient-v0: $(/usr/bin/time -f "%e" ./resilientv0 2>&1)"
echo "resilient-v1: $(/usr/bin/time -f "%e" ./resilientv1 2>&1)"
