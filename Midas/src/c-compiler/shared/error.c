/** Error Handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "error.h"
#include "timer.h"

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

int errors = 0;
int warnings = 0;

// Send an error message to stderr
void errorExit(int exitcode, const char *msg, ...) {
    // Do a formatted output, passing along all args
    va_list argptr;
    va_start(argptr, msg);
    vfprintf(stderr, msg, argptr);
    va_end(argptr);
    fputs("\n", stderr);

    // Exit with return code
#ifdef _DEBUG
    getchar();    // Hack for VS debugging
#endif
    exit(exitcode);
}

// Send an error message to stderr
void errorOut(int code, const char *msg, va_list args) {
    // Prefix for error message
    if (code < WarnCode) {
        errors++;
        fprintf(stderr, "Error %d: ", code);
    }
    else if (code < Uncounted) {
        warnings++;
        fprintf(stderr, "Warning %d: ", code);
    }

    // Do a formatted output of message, passing along all args
    vfprintf(stderr, msg, args);
    fputs("\n", stderr);
}

// Send an error message plus code context to stderr
void errorOutCode(char *tokp, uint32_t linenbr, char *linep, char *url, int code, const char *msg, va_list args) {
    char *srcp;
    int pos, spaces;

    // Send out the error message and count
    errorOut(code, msg, args);

    // Reflect the source code line
    fputs(" --> ", stderr);
    srcp = linep;
    while (*srcp && *srcp!='\n')
        fputc(*srcp++, stderr);
    fputc('\n', stderr);

    // Depict where error message applies along with source file/pos info
    fprintf(stderr, "     ");
    pos = (spaces = tokp - linep) + 1;
    srcp = linep;
    while (spaces--) {
        fputc(*srcp++ == '\t'? '\t' : ' ', stderr);
    }
    fprintf(stderr, "^--- %s:%d:%d\n", url, linenbr, pos);
}
// Send an error message to stderr
void errorMsg(int code, const char *msg, ...) {
    va_list argptr;
    va_start(argptr, msg);
    errorOut(code, msg, argptr);
    va_end(argptr);
}

// Generate final message for a compile
void errorSummary() {
    if (errors > 0)
        errorExit(ExitError, "Unsuccessful compile: %d errors, %d warnings", errors, warnings);
    fprintf(stderr, "Compile finished in %.6g sec (%lu kb). %d warnings detected\n", timerSummary(), memUsed()/1024, warnings);
}
