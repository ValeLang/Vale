/** UTF8 Helper routines
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "utf8.h"
#include <ctype.h>

/** Return the current unicode character whose UTF-8 bytes start at lex->bytepos */
uint32_t utf8GetCode(const char *src) {
    int nbytes;
    uint32_t chr;

    // Get info from first UTF-8 byte
    if ((*src&0xF0) == 0xF0) {nbytes=4; chr = *src&0x07;}
    else if ((*src&0xE0) == 0xE0) {nbytes=3; chr = *src&0x0F;}
    else if ((*src&0xC0) == 0xC0) {nbytes=2; chr = *src&0x1F;}
    else if ((*src&0x80) == 0x00) {nbytes=1; chr = *src&0x7F;}
    else {nbytes=1; chr = 0;} // error

    // Obtain remaining bytes
    while (--nbytes) {
        src++;
        if ((*src&0xC0)==0x80)
            chr = (chr<<6) + (*src&0x3F);
    }
    return chr;
}

// Return true if unicode is a letter
int utf8IsLetter(const char* srcp) {
    return !utf8IsMultibyte(srcp) && isalpha(*srcp);
}
