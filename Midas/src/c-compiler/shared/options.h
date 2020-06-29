/** Option handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef options_h
#define options_h

#include <stdint.h>

// Option flag: Is extra info required, optional, or none needed?
#define OPT_ARG_REQUIRED 1
#define OPT_ARG_OPTIONAL 2
#define OPT_ARG_NONE     4

// Structure for a describing an option argument
typedef struct opt_arg_t
{
    char *long_opt;
    char short_opt;
    uint32_t flag;
    uint32_t id;
} opt_arg_t;

// Place this at end of option argument definition list
#define OPT_ARGS_FINISH {NULL, 0, UINT32_MAX, UINT32_MAX}

// State of the option decoder
typedef struct opt_state_t
{
    opt_arg_t* args;

    int *argc;
    char **argv;
    char *arg_val;

    // working state
    char *opt_start;
    char *opt_end;
    int match_type;
    int idx;
    int remove;
} opt_state_t;

// Initialize the option handling state
void optInit(opt_arg_t *args, opt_state_t *s, int *argc, char **argv);

// Retrieve the next valid and parsed option
// If return is -1, we are done - no more to process
// If return is -2, we found a badly formed option
// Otherwise return is a match id, with arg_val containing the argument
int optNext(opt_state_t *s);

#endif
