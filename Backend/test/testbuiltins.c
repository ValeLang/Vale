#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#ifdef _WIN32
#include <io.h>
#include <fcntl.h>
#include <sys\types.h>
#include <sys\stat.h>
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
  printf("In increment %d\n", __LINE__);
  printf("Filename: %s\n", filename);

#ifdef _WIN32
  _invalid_parameter_handler oldHandler, newHandler;
  newHandler = myInvalidParameterHandler;
  oldHandler = _set_invalid_parameter_handler(newHandler);
#endif

  printf("In increment %d\n", __LINE__);

#ifdef _WIN32
  int descriptor = _open(filename, _O_BINARY | _O_CREAT | _O_RDWR, _S_IREAD | _S_IWRITE);
  assert(descriptor >= 0);
  FILE* file = fdopen(descriptor, "rb+");
#else
  FILE* file = fopen(filename, "ab+");
#endif

  printf("In increment %d\n", __LINE__);

  assert(file);

  printf("In increment %d\n", __LINE__);

  int initialSeekResult = fseek(file, 0, SEEK_END);
  assert(file);

  printf("In increment %d\n", __LINE__);

  int64_t num = 0;

  printf("In increment %d\n", __LINE__);

  int pos = ftell(file);

  printf("In increment %d\n", __LINE__);

  if (pos != 0) {
    printf("In increment %d\n", __LINE__);
    // If we get here, the file already existed.

    // Read the int that was already there.
    int seekResult = fseek(file, 0, SEEK_SET);
    assert(seekResult == 0);
    int readResult = fread(&num, sizeof(int64_t), 1, file);
    assert(readResult);
    printf("Opened file %s that already had int in it: %d\n", filename, num);
  } else {
    printf("Opened file %s that didn't exist.\n", filename);
  }

  printf("In increment %d\n", __LINE__);

  num++;

  printf("In increment %d\n", __LINE__);

  // Write added number to the file.
  int seekResult = fseek(file, 0, SEEK_SET);
  assert(seekResult == 0);
  int writeResult = fwrite(&num, sizeof(int64_t), 1, file);
  assert(writeResult);

  printf("Wrote %d to file %s.\n", num, filename);

  int closeResult = fclose(file);
  assert(closeResult == 0);

  printf("In increment %d\n", __LINE__);

#ifdef _WIN32
  _set_invalid_parameter_handler(oldHandler);
#endif

  printf("In increment %d\n", __LINE__);

  return num;
}

