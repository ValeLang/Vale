/** UTF-8 helper routines
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef utf8_h
#define utf8_h

#include <stdint.h>

// Evalutes non-zero if UTF8 code takes more than one byte
#define utf8IsMultibyte(src) (*(src)&0x80)

// Evaluates to how many bytes to skip over to next UTF8 character
// Note that we won't skip past end-of-string
#define utf8ByteSkip(src) (\
    (*(src) & 0xF0) == 0xF0? 4 : \
    (*(src) & 0xE0) == 0xE0? 3 : \
    (*(src) & 0xC0) == 0xC0? 2 : \
    (*(src) == '\0' || *(src) == '\x1A')? 0 : 1)

uint32_t utf8GetCode(const char *src);
int utf8IsLetter(const char* srcp);

#endif
