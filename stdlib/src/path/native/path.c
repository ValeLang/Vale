#include "list.h"
#include<stdlib.h>
#include<stdio.h>
#include <stdint.h>
#include <assert.h>
#include<string.h>
#include<sys/stat.h>
#include<errno.h>
#include <limits.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#include <dirent.h>
#endif

#include "stdlib/StrArray.h"
#include "stdlib/is_dir.h"
#include "stdlib/is_file.h"
#include "stdlib/exists.h"
#include "stdlib/iterdir.h"
#include "stdlib/makeDirectory.h"
#include "stdlib/readFileAsString.h"
#include "stdlib/writeStringToFile.h"
#include "stdlib/Path.h"
#include "stdlib/PathList.h"
#include "stdlib/AddToPathChildList.h"
#include "stdlib/CreateDirExtern.h"

// We have both is_file_internal and is_directory_internal because they aren't
// exactly inverses of each other... a symbolic link on unix will return false
// for both.

static int8_t is_file_internal(char* path) {
#ifdef _WIN32
  return (GetFileAttributes(path) & FILE_ATTRIBUTE_DIRECTORY) == 0;
#else
  struct stat path_stat;
  if (stat(path, &path_stat) != 0) {
    if (errno == ENOENT) {
      // Doesnt exist
      return 0;
    } else {
      perror("is_file_internal stat failed! ");
      exit(1);
    }
  }
  return S_ISREG(path_stat.st_mode);
#endif
}

static int8_t is_directory_internal(char* path) {
#ifdef _WIN32
  return !!(GetFileAttributes(path) & FILE_ATTRIBUTE_DIRECTORY);
#else
  struct stat path_stat;
  if (stat(path, &path_stat) != 0) {
    if (errno == ENOENT) {
      // Doesnt exist
      return 0;
    } else {
      perror("is_directory_internal stat failed! ");
      exit(1);
    }
  }
  return S_ISDIR(path_stat.st_mode);
#endif
}

static int8_t exists_internal(char* relativePath) {
#ifdef _WIN32

  char absolutePath[MAX_PATH];
  int length = GetFullPathNameA(relativePath, MAX_PATH, absolutePath, NULL);
  if (length == 0) {
    fprintf(stderr, "resolve: GetFullPathNameA failed for input \"%s\", error %ld\n", relativePath, GetLastError());
    exit(1);
  }

  WIN32_FIND_DATA FindFileData;
  HANDLE handle = FindFirstFile(absolutePath, &FindFileData) ;
  int found = handle != INVALID_HANDLE_VALUE;
  if (found) {
    //FindClose(&handle); this will crash
    FindClose(handle);
  }
  return found;
#else
  if (is_directory_internal(relativePath)) {
    DIR* dir = opendir(relativePath);
    int8_t retval = dir ? 1 : 0;
    if (retval) { closedir(dir); }
    return retval;
  } else {
    FILE* file = fopen(relativePath, "r");
    int8_t retval = file ? 1 : 0; 
    if (retval) { fclose(file); }
    return retval;
  }
#endif
}

static int8_t makeDirectory_internal(char* path, char allow_already_existing) {
  if (mkdir(path, 0700) != 0) {
    if (allow_already_existing && errno == EEXIST) {
      // fine, continue
    } else {
      perror("Couldn't make directory");
      return 0;
    }
  }
  return 1;
}

static ValeStr* readFileAsString_internal(char* filename) {
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
    fputs("Failed to read file: ", stderr);
    fputs(filename, stderr);
    fputs("\n", stderr);
    exit(1);
  }

  ValeStr* result = ValeStrNew(lSize);
  strncpy(result->chars, buffer, lSize);

  fclose(fp);
  free(buffer);

  return result;
}

static void writeStringToFile_internal(char* filename, char* contents, int contentsLen) {
  FILE *fp = fopen(filename, "wb");
  if (!fp) {
    perror(filename);
    exit(1);
  }

  if (contentsLen > 0) {
    if (1 != fwrite(contents, contentsLen, 1, fp)) {
      fclose(fp);
      fputs("Failed to write file", stderr);
      exit(1);
    }
  }

  fclose(fp);
}

static int8_t iterdir_internal(stdlib_PathRef path, char* dirPath, stdlib_PathListRef destinationList) {
  if (!exists_internal(dirPath)) {
    fprintf(stderr, "iterdir: path doesn't exist! %s\n", dirPath);
    return 0;
  }
  if (is_file_internal(dirPath)) {
    fprintf(stderr, "Called iterdir on a file, not a path! %s\n", dirPath);
    return 0;
  }

#ifdef _WIN32
  WIN32_FIND_DATA fdFile; 
  HANDLE hFind = NULL; 

  //Specify a file mask. *.* = We want everything! 
  char searchPath[2048] = { 0 };
  sprintf(searchPath, "%s\\*.*", dirPath); 

  if ((hFind = FindFirstFile(searchPath, &fdFile)) == INVALID_HANDLE_VALUE) {
    fprintf(stderr, "Path not found: [%s]\n", dirPath);
    return 0;
  } 

  do {
    //Find first file will always return "."
    //    and ".." as the first two directories. 
    if (strcmp(fdFile.cFileName, ".") != 0 &&
        strcmp(fdFile.cFileName, "..") != 0) {
      stdlib_AddToPathChildList(path, destinationList, ValeStrFrom(fdFile.cFileName));
    }
  } while (FindNextFile(hFind, &fdFile)); //Find the next file.

  FindClose(hFind); //Always, Always, clean things up!

#else
  DIR* d;
  struct dirent *dir;
  d = opendir(dirPath);
  if (d == 0) {
    fprintf(stderr, "cannot open directory: %s\n", dirPath);
    return 0;
  }

  while((dir = readdir(d)) != NULL){
    if (strcmp(".", dir->d_name) != 0 &&
        strcmp("..", dir->d_name) != 0) {
      stdlib_AddToPathChildList(path, destinationList, ValeStrFrom(dir->d_name));
    }
  }
  closedir(d); 
#endif

  return 1;
}


static int8_t CreateDir(char* path, int8_t allow_already_existing) {
  if (mkdir(path, 0700) != 0) {
    if (allow_already_existing && errno == EEXIST) {
      // fine, continue
    } else {
      perror("Couldn't make directory");
      return 0;
    }
  }
  return 1;
}

extern int8_t stdlib_CreateDirExtern(ValeStr* path, int8_t allow_already_existing) {
  int8_t result = CreateDir(path->chars, allow_already_existing);
  free(path);
  return result;
}


extern int8_t stdlib_exists(ValeStr* path) {
  long result = exists_internal(path->chars);
  free(path);
  return result;
}

// Aborts on failure, beware!
extern ValeStr* stdlib_readFileAsString(ValeStr* filenameVStr) {
  ValeStr* result = readFileAsString_internal(filenameVStr->chars);
  free(filenameVStr);
  return result;
}

extern void stdlib_writeStringToFile(ValeStr* filenameVStr, ValeStr* contentsVStr) {
  writeStringToFile_internal(filenameVStr->chars, contentsVStr->chars, contentsVStr->length);
  free(filenameVStr);
  free(contentsVStr);
}

extern int8_t stdlib_iterdir(stdlib_PathRef path, ValeStr* pathStr, stdlib_PathListRef destinationList) {
  int8_t result = iterdir_internal(path, pathStr->chars, destinationList);
  free(pathStr);
  return result;
}

extern int8_t stdlib_is_file(ValeStr* path) {
  long result = exists_internal(path->chars) && is_file_internal(path->chars);
  free(path);
  return result;
}

extern int8_t stdlib_is_dir(ValeStr* path) {
  long result = is_directory_internal(path->chars);
  free(path);
  return result;
}

extern int8_t stdlib_makeDirectory(ValeStr* path, int8_t allow_already_existing) {
  int8_t result = makeDirectory_internal(path->chars, allow_already_existing);
  free(path);
  return result;
}

extern ValeStr* stdlib_GetEnvPathSeparator() {
#ifdef _WIN32
  return ValeStrFrom(";");
#else
  return ValeStrFrom(":");
#endif
}

extern ValeStr* stdlib_GetPathSeparator() {
#ifdef _WIN32
  return ValeStrFrom("\\");
#else
  return ValeStrFrom("/");
#endif
}

extern ValeStr* stdlib_resolve(ValeStr* relative_path) {
#ifdef _WIN32
  char path[MAX_PATH];
  int length = GetFullPathNameA(relative_path->chars, MAX_PATH, path, NULL);
  if (length == 0) {
    fprintf(stderr, "resolve: GetFullPathNameA failed for input \"%s\", error %ld\n", relative_path->chars, GetLastError());
    exit(1);
  }
  ValeStr* result = ValeStrFrom(path);
  return result;
#else

  char* realpath_input = relative_path->chars;

  char relative_path_with_home_replaced[PATH_MAX];

  if (relative_path->chars[0] == '~') {
    char* home = getenv("HOME");
    if (home == NULL) {
      fprintf(stderr, "resolve: Couldn't get home directory for ~ replacement.\n");
      exit(1);
    }
    strcpy(relative_path_with_home_replaced, home);
    strcat(relative_path_with_home_replaced, relative_path->chars + 1);
    realpath_input = relative_path_with_home_replaced;
  }

  char* absolute_path = realpath(realpath_input, NULL);
  if (absolute_path == NULL) {
    fprintf(stderr, "resolve: Realpath failed for input \"%s\": ", realpath_input);
    perror("");
    exit(1);
  }

  ValeStr* result = ValeStrFrom(absolute_path);
  free(absolute_path);
  return result;
#endif
}
