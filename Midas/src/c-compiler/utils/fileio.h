/** File i/o
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef fileio_h
#define fileio_h

#include <string>

//// Load a file into an allocated string, return pointer or NULL if not found
//std::string fileLoad(char *fn);

// Extract the directory for this file path.
std::string fileDirectory(std::string fn);

// Extract a filename only (no extension) from a path
std::string fileName(std::string fn);

// Concatenate folder, filename and extension into a path
std::string fileMakePath(const char *dir, const char *srcfn, const char *ext);

//// Create a new source file url relative to current, substituting new path and .cone extension
//char *fileSrcUrl(char *cururl, char *srcfn, int newfolder);

//// Load source file, where srcfn is relative to cururl
//// - Look at fn+.cone or fn+/mod.cone
//// - return full pathname for source file
//std::string fileLoadSrc(char *cururl, char *srcfn, char **fn);

#endif
