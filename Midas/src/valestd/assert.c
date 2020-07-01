/** stdio - Standard library i/o
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>

void __vassert(char value) {
  printf("Running assertion!\n");
  if (!value) {
    printf("Assertion failed!\n");
    exit(1);
  }
}

void __vflare_i64(int64_t color, int64_t x) {
  printf("Flare %d: %d\n", color, x);
}
