#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>

void __vprintCStr(const char* str) {
  printf("%s", str);
}

void __vprintI64(int64_t x) {
  printf("%ld", x);
}

void __vprintBool(int8_t x) {
  if (x) {
    printf("true");
  } else {
    printf("false");
  }
}
//
//void printStr(char *p, size_t len) {
//	fwrite(p, len, 1, stdout);
//	assert(false);
//}
//
//void printInt(int64_t nbr) {
//	printf("%"PRId64, nbr);
//}
//
//void printFloat(double nbr) {
//	printf("%g", nbr);
//}
//
//void printChar(uint64_t code) {
//	char result[6];
//	char *p = &result[0];
//
//	if (code<0x80)
//		*p++ = (unsigned char) code;
//	else if (code<0x800) {
//		*p++ = 0xC0 | (unsigned char)(code >> 6);
//		*p++ = 0x80 | (code & 0x3f);
//	}
//	else if (code<0x10000) {
//		*p++ = 0xE0 | (unsigned char)(code >> 12);
//		*p++ = 0x80 | ((code >> 6) & 0x3F);
//		*p++ = 0x80 | (code & 0x3f);
//	}
//	else if (code<0x110000) {
//		*p++ = 0xF0 | (unsigned char)(code >> 18);
//		*p++ = 0x80 | ((code >> 12) & 0x3F);
//		*p++ = 0x80 | ((code >> 6) & 0x3F);
//		*p++ = 0x80 | (code & 0x3f);
//	}
//	*p = '\0';
//	printf("%s", result);
//}