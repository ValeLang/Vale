
#include "fileio.h"

#include <stdio.h>
#include <string.h>
#include <stddef.h>

/** Extract a filename only from a path */
std::string getFileName(std::string fn) {
    char *fnp = &fn[fn.length()-1];
    char *dotp = &fn[0] + fn.length();

    // Look backwards for slash
    while (fnp != fn && *fnp != '/' && *fnp != '\\')
        --fnp;
    if (fnp != fn)
        ++fnp;

    // Create string to hold filename and return
    return std::string(fnp, dotp-fnp);
}

/** Extract a filename only (no extension) from a path */
std::string getFileNameNoExt(std::string fn) {
  char *dotp;
  char *fnp = &fn[fn.length()-1];

  // Look backwards for '.' If not found, we are done
  while (fnp != fn && *fnp != '.' && *fnp != '/' && *fnp != '\\')
    --fnp;
  if (fnp == fn)
    return fn;
  if (*fnp == '/' || *fnp == '\\')
    return fnp + 1;

  // Look backwards for slash
  dotp = fnp;
  while (fnp != fn && *fnp != '/' && *fnp != '\\')
    --fnp;
  if (fnp != fn)
    ++fnp;

  // Create string to hold filename and return
  return std::string(fnp, dotp-fnp);
}

std::string fileDirectory(std::string fn) {
  char *fnp = &fn[fn.length()-1];

  // Look backwards for '.' If not found, we are done
  while (fnp != fn && *fnp != '.' && *fnp != '/' && *fnp != '\\')
    --fnp;
  if (fnp == fn)
    return fn;
  if (*fnp == '/' || *fnp == '\\')
    return fnp + 1;

  // Create string to hold filename and return
  return std::string(fn.c_str(), fnp-fn.c_str());
}

/** Concatenate folder, filename and extension into a path */
std::string fileMakePath(const char *dir, const char *srcfn, const char *ext) {
    if (dir == NULL)
        dir = "";
    std::string result(dir, strlen(dir));
    if (strlen(dir) && result[strlen(dir) - 1] != '/' && result[strlen(dir) - 1] != '\\')
        result += "/";
    result += srcfn;
    result += ".";
    result += ext;
    return result;
}
