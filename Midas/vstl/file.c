#include <stdint.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>

// These are exposed by the compiled vale .obj/.o, they're
// the start of a Vale native API.
typedef struct ValeStr ValeStr;
ValeStr* vale_newstr(int64_t length);
char* vale_getstrbytes(ValeStr* str);
int64_t vale_getstrnumbytes(ValeStr* str);



// Aborts on failure, beware!
ValeStr* readFileAsString(ValeStr* filenameVStr) {
  char* filename = vale_getstrchars(filenameVStr);

  FILE *fp = fopen(filename, "rb");
  if (!fp) {
    perror(filename);
    exit(1);
  }

  fseek(fp, 0L, SEEK_END);
  long lSize = ftell(fp);
  rewind(fp);

  /* allocate memory for entire content */
  char *buffer = malloc(lSize);
  if (!buffer) {
    fclose(fp);
    fputs("memory alloc fails", stderr);
    exit(1);
  }

  /* copy the file into the buffer */
  if (1 != fread(buffer, lSize, sizeof(char), fp)) {
    fclose(fp);
    free(buffer);
    fputs("Failed to read file", stderr);
    exit(1);
  }

  ValeStr* result = vale_newstr(lSize);
  strncpy(vale_getstrchars(result), buffer, lSize);

  fclose(fp);
  free(buffer);

  return result;
}

void writeStringToFile(ValeStr* filenameVStr, ValeStr* contentsVStr) {
  char *filename = vale_getstrchars(filenameVStr);
  char* contents = vale_getstrchars(contentsVStr);
  int contentsLen = vale_getstrnumbytes(contentsVStr);

  //printf("contents len: %d\n", contentsLen);

  FILE *fp = fopen(filename, "wb");
  if (!fp) {
    perror(filename);
    exit(1);
  }

  if (1 != fwrite(contents, contentsLen, sizeof(char), fp)) {
    fclose(fp);
    fputs("Failed to write file", stderr);
    exit(1);
  }

  fclose(fp);
}
