
OLD_COMMAND="$1"
if [ "$OLD_COMMAND" == "" ] ; then
  echo "First arg should be old command"
  exit 1
fi
shift;

NEW_COMMAND="$1"
if [ "$NEW_COMMAND" == "" ] ; then
  echo "First arg should be old command"
  exit 1
fi
shift;

echo "Trying both first, to see if they actually work."
echo "Running old command..."
/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 || { echo "Running old command failed!" && exit 1; }
echo "Running new command..."
/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 || { echo "Running new command failed!" && exit 1; }
echo "Success, starting benchmarks!"

echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
echo "Old: $(/usr/local/bin/gtime -f "%e" $OLD_COMMAND $@ 2>&1 > /dev/null)"
echo "New: $(/usr/local/bin/gtime -f "%e" $NEW_COMMAND $@ 2>&1 > /dev/null)"
