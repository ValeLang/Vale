
#ifndef fileio_h
#define fileio_h

#include <string>

// Extract the directory for this file path.
std::string fileDirectory(std::string fn);

// Extract a filename only from a path.
std::string getFileName(std::string fn);

// Extract a filename only (no extension) from a path.
std::string getFileNameNoExt(std::string fn);

// Concatenate folder, filename and extension into a path
std::string fileMakePath(const char *dir, const char *srcfn, const char *ext);

#endif
