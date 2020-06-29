/** Memory management
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef memory_h
#define memory_h

#include <stdlib.h>
#include <stddef.h>

// Configurable size for arenas (specify as multiples of 4096 byte pages)
extern size_t gMemBlkArenaSize;    // Default is 256 pages
extern size_t gMemStrArenaSize;    // Default is 128 pages

// Allocate memory for a block, aligned to a 16-byte boundary
void *memAllocBlk(size_t size);

// Allocate memory for a string and copy contents over, if not NULL
// Allocates extra byte for string-ending 0, appending it to copied string
char *memAllocStr(char *str, size_t size);

// Return memory allocated and used
size_t memUsed();

#endif
