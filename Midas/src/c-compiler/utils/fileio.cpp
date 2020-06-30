/** File I/O
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "fileio.h"
#include "shared/memory.h"

#include <stdio.h>
#include <string.h>
#include <stddef.h>

///** Load a file into an allocated string, return pointer or NULL if not found */
//std::string fileLoad(char *fn) {
//    FILE *file;
//    size_t filesize;
//    char *fileBytes;
//
//    // Open the file - return null on failure
//    if (!(file = fopen(fn, "rb")))
//        return NULL;
//
//    // Determine the file length (so we can accurately allocate memory)
//    fseek(file, 0, SEEK_END);
//    filesize=ftell(file);
//    fseek(file, 0, SEEK_SET);
//
//    // Load the data into an allocated string buffer and close file
//    fileBytes = new char[filesize];
//    fread(fileBytes, 1, filesize, file);
//    fileBytes[filesize]='\0';
//    fclose(file);
//    return std::string(fileBytes, filesize);
//}

/** Extract a filename only (no extension) from a path */
std::string fileName(std::string fn) {
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

/** Extract a filename only (no extension) from a path */
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

// Get number of characters in string up to file name
size_t fileFolder(char *fn) {
    char *fnp = &fn[strlen(fn) - 1];

    // Look backwards for '/' If not found, we are done
    while (fnp != fn && *fnp != '/' && *fnp != '\\')
        --fnp;
    if (fnp == fn)
        return 0;
    return fnp - fn + 1;
}

// Return position of last period (after last slash)
char *fileExtPos(char *fn) {
    char *dotpos = strrchr(fn, '.');
    return dotpos && dotpos > strrchr(fn, '/') ? dotpos : 0;
}

//// Create a new source file url relative to current, substituting new path and .cone extension
//char *fileSrcUrl(char *cururl, char *srcfn, int newfolder) {
//    char *outnm;
//    if (cururl == NULL)
//        cururl = "";
//    outnm = memAllocStr("", strlen(cururl) + strlen(srcfn) + (newfolder ? 10 : 6));
//    if (cururl && srcfn[0]!='/')
//        strncat(outnm, cururl, fileFolder(cururl));
//    strcat(outnm, srcfn);
//    if (newfolder)
//        strcat(outnm, "/mod.cone");
//    else if (!fileExtPos(outnm))
//        strcat(outnm, ".cone");
//    return outnm;
//}

//// Load source file, where srcfn is relative to cururl
//// - Look at fn+.cone or fn+/mod.cone
//// - return full pathname for source file
//std::string fileLoadSrc(char *cururl, char *srcfn, char **fn) {
//    *fn = fileSrcUrl(cururl, srcfn, 0);
//    auto src = fileLoad(*fn);
//    if (!src.empty())
//        return src;
//    *fn = fileSrcUrl(cururl, srcfn, 1);
//    return fileLoad(*fn);
//}
