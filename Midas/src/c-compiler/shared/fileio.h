/** File i/o
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef fileio_h
#define fileio_h

// Load a file into an allocated string, return pointer or NULL if not found
char *fileLoad(char *fn);

// Extract a filename only (no extension) from a path
char *fileName(char *fn);

// Concatenate folder, filename and extension into a path
char *fileMakePath(char *dir, char *srcfn, char *ext);

// Create a new source file url relative to current, substituting new path and .cone extension
char *fileSrcUrl(char *cururl, char *srcfn, int newfolder);

// Load source file, where srcfn is relative to cururl
// - Look at fn+.cone or fn+/mod.cone
// - return full pathname for source file
char *fileLoadSrc(char *cururl, char *srcfn, char **fn);

#endif
