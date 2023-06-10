#include "subprocess.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "ValeBuiltins.h"
#include "valecutils/StrArray.h"
#include <fcntl.h>
#include <unistd.h>

long read_into_buffer(char* buffer, ValeInt bytes, FILE* stream){
  //printf("read_into_buffer start\n");
  long i = 0;
  for(i = 0; i < bytes; i++){
    //printf("read_into_buffer a\n");
    // feof comes after fgetc but before its result is used, taken from example at
    // https://www.tutorialspoint.com/c_standard_library/c_function_fgetc.htm
    int c = fgetc(stream);
    //printf("read_into_buffer b\n");
    if(feof(stream)) {
      //printf("read_into_buffer c\n");
      break;
    }
    //printf("read_into_buffer d\n");
    buffer[i] = c;
  }
    //printf("read_into_buffer end\n");
  return i;
}

ValeStr* valecutils_get_env_var(ValeStr* var_name) {
  const char* env_var = getenv((const char*)&var_name->chars);
  unsigned long length = strlen(env_var);
  ValeStr* out = malloc(sizeof(ValeStr) + length + 1);
  out->length = length; 
  strcpy(out->chars, env_var);
  free(var_name);
  return out;
}

char** valecutils_vale_to_char_arr(valecutils_StrArray* chains) {
  char** args = malloc(chains->length*sizeof(char*)+sizeof(char*));
  for(unsigned long i = 0; i < chains->length; i++) {
    args[i] = &chains->elements[i]->chars[0];
  }
  args[chains->length] = 0;
  return args; 
}

int64_t valecutils_launch_command(valecutils_StrArray* chain, ValeStr* cwd_str) {
  int64_t out = 0;
  char** args = (char**)valecutils_vale_to_char_arr(chain);
  // printf("args:\n");
  // for (int i = 0; args[i]; i++) {
  //   printf("arg %d: %s\n", i, args[i]);
  // }

  const char* cwd_or_null = cwd_str->length ? cwd_str->chars : SUBPROCESS_NULL;

  struct subprocess_s* subproc = malloc(sizeof(struct subprocess_s));
  if(subprocess_create_ex((const char**)args, subprocess_option_inherit_environment, SUBPROCESS_NULL, cwd_or_null, subproc) != 0){
    perror("command creation failed");
    return 0;
  }

  out = (unsigned long long)subproc;
  free(args);
  free(chain);
  return out;
}

ValeStr* valecutils_read_stdout(int64_t cmd, long bytes) {
  //printf("valecutils_read_stdout start\n");
  ValeStr* out = ValeStrNew(bytes+1); 
  //printf("valecutils_read_stdout a\n");
  FILE* stdout_handle = subprocess_stdout((struct subprocess_s*)cmd); 

  //printf("valecutils_read_stdout b\n");
  long read_len = read_into_buffer(out->chars, bytes, stdout_handle);
  //printf("valecutils_read_stdout c\n");
  out->chars[bytes] = '\0'; 
  out->length = read_len;
  //fclose(stdout_handle);
  //printf("valecutils_read_stdout end\n");
  return out;
}

ValeStr* valecutils_read_stderr(int64_t cmd, long bytes) {
  ValeStr* out = ValeStrNew(bytes+1);
  FILE* stderr_handle = subprocess_stderr((struct subprocess_s*)cmd); 
  long read_len = read_into_buffer(out->chars, bytes, stderr_handle);
  out->chars[bytes] = '\0'; 
  out->length = read_len;
  //fclose(stderr_handle);
  return out;
}

void valecutils_write_stdin(int64_t cmd, ValeStr* contents) {
  FILE* stdin_handle = subprocess_stdin((struct subprocess_s*)cmd); 
  for (int i = 0; i < contents->length; i++) {
    fputc(contents->chars[i], stdin_handle);
  }
  free(contents);
}

void valecutils_close_stdin(int64_t handle){
  struct subprocess_s* subprocess = (struct subprocess_s*)handle;
  FILE* stdin_handle = subprocess_stdin(subprocess);
  if (stdin_handle) {
    int success = fclose(stdin_handle);
    if (success != 0) {
      perror("Couldn't close subprocess stdin");
      exit(1);
    }
    subprocess->stdin_file = SUBPROCESS_NULL;
  }
}

long valecutils_join(int64_t handle){
  //printf("valecutils_join start\n");
  int result = 0;
  int success = subprocess_join((struct subprocess_s*)handle, &result);
  if (success != 0) {
    fprintf(stderr, "Couldn't join subprocess, error {success}\n");
    exit(1);
  }
  //printf("valecutils_join end\n");
  return result;  
}

long valecutils_destroy(int64_t handle){
  int success = subprocess_destroy((struct subprocess_s*)handle);
  if (success != 0) {
    fprintf(stderr, "Couldn't destroy subprocess, error {success}\n");
    exit(1);
  }
  return 0;
}

int8_t valecutils_alive(int64_t handle){
  return subprocess_alive((struct subprocess_s*)handle);
}
