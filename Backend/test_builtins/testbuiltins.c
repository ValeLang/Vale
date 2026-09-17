#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#ifdef _WIN32
#include <io.h>
#elif defined(__linux)
#endif

#ifdef _WIN32
void myInvalidParameterHandler(const wchar_t* expression,
                               const wchar_t* function,
                               const wchar_t* file,
                               unsigned int line,
                               uintptr_t pReserved)
{
  wprintf(L"Invalid parameter detected in function %s."
          L" File: %s Line: %d\n", function, file, line);
  wprintf(L"Expression: %s\n", expression);
  abort();
}
#endif

int64_t incrementIntFile(const char* filename) {
  fprintf(stderr, "In increment %d\n", __LINE__);
  fprintf(stderr, "Filename: %s\n", filename);

#ifdef _WIN32
  _invalid_parameter_handler oldHandler, newHandler;
  newHandler = myInvalidParameterHandler;
  oldHandler = _set_invalid_parameter_handler(newHandler);
#endif

  fprintf(stderr, "In increment %d\n", __LINE__);

#ifdef _WIN32
  int descriptor = _open(filename, _O_BINARY | _O_CREAT | _O_RDWR, _S_IREAD | _S_IWRITE);
  assert(descriptor >= 0);
  FILE* file = fdopen(descriptor, "rb+");
#else
  int descriptor = open(filename, O_CREAT | O_RDWR, S_IREAD | S_IWRITE);
  assert(descriptor >= 0);
  FILE* file = fdopen(descriptor, "rb+");
#endif

  fprintf(stderr, "In increment %d\n", __LINE__);

  assert(file);

  fprintf(stderr, "In increment %d\n", __LINE__);

  int initialSeekResult = fseek(file, 0, SEEK_END);
  assert(file);

  fprintf(stderr, "In increment %d\n", __LINE__);

  int64_t num = 0;

  fprintf(stderr, "In increment %d\n", __LINE__);

  int pos = ftell(file);

  fprintf(stderr, "In increment %d\n", __LINE__);

  if (pos != 0) {
    fprintf(stderr, "In increment %d\n", __LINE__);
    // If we get here, the file already existed.

    // Read the int that was already there.
    int seekResult = fseek(file, 0, SEEK_SET);
    assert(seekResult == 0);
    int readResult = fread(&num, sizeof(int64_t), 1, file);
    assert(readResult);
    fprintf(stderr, "Opened file %s that already had int in it: %d\n", filename, num);
  } else {
    fprintf(stderr, "Opened file %s that didn't exist.\n", filename);
  }

  fprintf(stderr, "In increment %d\n", __LINE__);

  num++;

  fprintf(stderr, "In increment %d\n", __LINE__);

  // Write added number to the file.
  int seekResult = fseek(file, 0, SEEK_SET);
  assert(seekResult == 0);
  int writeResult = fwrite(&num, sizeof(int64_t), 1, file);
  assert(writeResult);

  fprintf(stderr, "Wrote %d to file %s.\n", num, filename);

  int closeResult = fclose(file);
  assert(closeResult == 0);

  fprintf(stderr, "In increment %d\n", __LINE__);

#ifdef _WIN32
  _set_invalid_parameter_handler(oldHandler);
#endif

  fprintf(stderr, "In increment %d\n", __LINE__);

  return num;
}

