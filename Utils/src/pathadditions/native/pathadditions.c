
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>
#include <sys/stat.h>
#include <errno.h>
#include <limits.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#include <dirent.h>
#endif

#include "valecutils/RemoveFileExtern.h"
#include "valecutils/RemoveDirExtern.h"
#include "valecutils/CreateDirExtern.h"
#include "valecutils/IsSymLinkExtern.h"
#include "valecutils/RenameExtern.h"

static ValeInt RemoveFile(const char* path) {
  if (remove(path) != 0) {
    switch (errno) {
      case ENOENT:
        return 1;
      // DO NOT SUBMIT, remaining errors
      default:
        fprintf(stderr, "valecutils_RemoveFile: Unknown error code %d\n", errno);
        exit(1);
    }
  }
  return 0;
}

static ValeInt RemoveDir(const char* path) {
  if (rmdir(path) != 0) {
    switch (errno) {
      case ENOENT:
        return 1;
      // DO NOT SUBMIT, remaining errors
      default:
        fprintf(stderr, "valecutils_RemoveDir: Unknown error code %d\n", errno);
        exit(1);
    }
  }
  return 0;
}

static int8_t IsSymLink(const char* path) {
#ifdef _WIN32
  return 0;
#else
  struct stat path_stat;
  if (stat(path, &path_stat) != 0) {
    perror("IsSymLink stat failed! ");
    exit(1);
  }
  return S_ISLNK(path_stat.st_mode);
#endif
}

static int8_t CreateDir(char* path, char allow_already_existing) {
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

static ValeInt Rename(char* src, char* dest) {
  if (rename(src, dest) != 0) {
    perror("Rename failed! ");
    exit(1);
  }

  // // From https://stackoverflow.com/a/38426851
  // // TODO:
  // // "Of course it's not complete, it is a sketch to give the OP a head start, not more and,
  // // hopefully, not less. The original implementation in e.g.: coreutils-8.24 hase 375 loc in
  // // coreutils-8.24/src/mv.c alone. OP's question has been answered at the beginning (the problem
  // // lies within the details of rename()), the rest is just an example."
  // struct stat statbuf_src, statbuf_dest;
  // if (stat(src, &statbuf_src) != 0) {
  //   perror("Rename stat failed! ");
  //   exit(1);
  // }
  // if (stat(dest, &statbuf_dest) != 0) {
  //   if (errno == ENOENT) {
  //     // that's fine, continue
  //   } else {
  //     perror("Rename stat failed! ");
  //     exit(1);
  //   }
  // }
  //
  // // if that is not set you have to do it by hand:
  // // climb up the tree, concatenating names until the inodes are the same
  // char* current_directory = getenv("PWD");
  //
  // // I'm pretty sure it can be done in a much more elegant way
  // char* new_src = malloc(strlen(src) + 1 + strlen(current_directory) + 1);
  // strcpy(new_src, current_directory);
  // strcat(new_src, "/");
  // strcat(new_src, src);
  //
  // char* new_dest = malloc(strlen(dest) + 1 + strlen(current_directory) + 1 + strlen(src) + 1);
  // strcpy(new_dest, current_directory);
  // strcat(new_dest, "/");
  // strcat(new_dest, dest);
  // strcat(new_dest, "/");
  // strcat(new_dest, src);
  //
  // if(rename(new_src,new_dest) != 0){
  //   int error = errno;
  //   perror("Rename failed! ");
  //   exit(1);
  // }
  //
  // free(new_src);
  // free(new_dest);
  // free(src);
  // free(dest);

  return 0;
}

extern int8_t valecutils_CreateDirExtern(ValeStr* path, ValeInt allow_already_existing) {
  int8_t result = CreateDir(path->chars, !!allow_already_existing);
  free(path);
  return result;
}

extern ValeInt valecutils_RemoveFileExtern(ValeStr* path) {
  ValeInt result = RemoveFile(path->chars);
  free(path);
  return result;
}

extern ValeInt valecutils_RemoveDirExtern(ValeStr* path) {
  ValeInt result = RemoveDir(path->chars);
  free(path);
  return result;
}

extern int8_t valecutils_IsSymLinkExtern(ValeStr* path) {
  int8_t result = IsSymLink(path->chars);
  free(path);
  return result;
}

extern ValeInt valecutils_RenameExtern(ValeStr* path, ValeStr* destination) {
  ValeInt result = Rename(path->chars, destination->chars);
  free(path);
  free(destination);
  return result;
}
