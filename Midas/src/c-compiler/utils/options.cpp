/** Option handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "options.h"

#include <string.h>
#include <stdio.h>
#include <stddef.h>

#define MATCH_LONG  1
#define MATCH_SHORT 2
#define MATCH_NONE  3

#define PARSE_ARG (OPT_ARG_REQUIRED | OPT_ARG_OPTIONAL)

// Skip past all arguments that are not options (-)
// Return 1 if we found one, otherwise 0 at end of list
static int optFindOption(opt_state_t* s) {
    while(1) {
        if (s->idx == *s->argc)
            return 0;

        if (s->argv[s->idx][0] == '-' && s->argv[s->idx][1] != '\0')
            return 1;

        s->idx++;
    }
}

// Return true if we are at end of declared option list
// e.g., at entry that specifies OPT_ARGS_FINISHED
static int optEndReached(opt_arg_t* arg) {
    return (arg->long_opt == 0) && (arg->short_opt == 0) &&
        (arg->id == UINT32_MAX) && (arg->flag == UINT32_MAX);
}

// Match the current arg to a define option. Return option info it matches
// Return NULL if none found, 1 if duplicate matches
static opt_arg_t* optFindMatch(opt_state_t* s)
{
    const char* match_name;
    size_t match_length;
    int is_long_opt;
    opt_arg_t *argp;
    opt_arg_t* match = NULL;

    // Locate option name for argument at idx
    is_long_opt = s->argv[s->idx][1] == '-';
    s->match_type = is_long_opt ? MATCH_LONG : MATCH_SHORT;
    s->opt_start = (s->argv[s->idx] + 1 + is_long_opt);
    for (s->opt_end = s->opt_start; *s->opt_end && *s->opt_end != '='; s->opt_end++);

    // Scan for this option in list of declared options
    for (argp = s->args; !optEndReached(argp); ++argp) {
        // Set up whether to match on short or long option
        if (s->match_type == MATCH_LONG) {
            match_name = argp->long_opt;
            match_length = (size_t)(s->opt_end - s->opt_start);
        } else {
            match_name = &argp->short_opt;
            match_length = 1;
        }

        // Do an exact match of provided option with argp's option name
        if (!strncmp(match_name, s->opt_start, match_length)
            && (s->match_type == MATCH_SHORT || match_length == strlen(match_name))) {
            // Return error if there are duplicate matches that are not the same
            if ((match != NULL) && (match->id != argp->id))
                return (opt_arg_t*)1;
            match = argp; // remember we had a match
        }
    }
    return match; // return match or NULL
}

// Does the option have data following the option name?
static int optHasArgument(opt_state_t* s)
{
    int short_arg = ((s->match_type == MATCH_SHORT) && (*(s->opt_start + 1) || s->idx < (*s->argc)-1));
    int long_arg = ((s->match_type == MATCH_LONG) && ((*s->opt_end == '=') || s->idx < (*s->argc)-1));
    return (short_arg | long_arg);
}

// Extract argument from long option
static void parse_long_opt_arg(opt_state_t* s) {
    if (*s->opt_end == '=') {
        s->arg_val = s->opt_end + 1;
        s->opt_start += strlen(s->opt_start);
    } else if (s->argv[s->idx + 1][0] != '-') {
        s->arg_val = s->argv[s->idx + 1];
        s->opt_start += strlen(s->opt_start);

        // Only remove if there actually was an argument
        if (s->argv[s->idx + 1][0])
            s->remove++;
    }
}

// Adjust to move past the short option
static void optParseShortOpt(opt_state_t* s) {
    // Strip out the short option, as short options may be grouped
    memmove(s->opt_start, s->opt_start + 1, strlen(s->opt_start));
    if (*s->opt_start)
        s->opt_start = s->argv[s->idx];
    else
        s->remove++;
}

// Extract argument from short option
static void optParseShortOptArg(opt_state_t* s)
{
    if (*s->opt_end) {
        s->arg_val = s->opt_end;
        s->opt_start += strlen(s->opt_start);
    } else if (*(s->opt_start) != '-') {
        s->arg_val = s->argv[s->idx + 1];
    } else {
        s->arg_val = s->opt_start + 1;
        s->opt_start += strlen(s->opt_start);
    }
    s->remove++;
}

// Modify argc/argv to remove s->remove accepted options
static void optStripAcceptedOpts(opt_state_t* s) {
    if (s->remove > 0) {
        *s->argc -= s->remove;
        memmove(&s->argv[s->idx], &s->argv[s->idx + s->remove], (*s->argc - s->idx) * sizeof(char*));
        s->idx--;
        s->remove = 0;
    }
}

static int optErrorMissingArgument(opt_state_t *s) {
    printf("%s: '%s' option requires an argument!\n", s->argv[0], s->argv[s->idx]);
    return -2;
}

// Initialize option state based on based arguments
void optInit(opt_arg_t *args, opt_state_t *s, int *argc, char** argv) {
    s->argc = argc;
    s->argv = argv;
    s->args = args;
    s->arg_val = NULL;

    s->opt_start = NULL;
    s->opt_end = NULL;
    s->match_type = 0;
    s->idx = 0;
    s->remove = 0;
}

// Return the id of a successfully identified option. -1 if none left. -2 if bad
int optNext(opt_state_t* s) {
    opt_arg_t *match;
    s->arg_val = NULL;
    match = NULL;

    while (match == NULL) {
        // Skip to a valid option in argument (starts with a dash)
        if (s->opt_start == NULL || *s->opt_start == '\0') {
            s->idx++;
            if (optFindOption(s) == 0)
                return -1;
        }

        // Match to a known argument. If it does not, ignore it
        match = optFindMatch(s);
        if (match == NULL)
            s->opt_start += strlen(s->opt_start);
    }

    if (match == (opt_arg_t*)1) {
        printf("%s: '%s' option is ambiguous!\n", s->argv[0], s->argv[s->idx]);
        return -2;
    }

    if ((match->flag == OPT_ARG_REQUIRED) && !optHasArgument(s))
        return optErrorMissingArgument(s);
 
    // Handle a long option with argument
    if (s->match_type == MATCH_LONG) {
        s->remove++;
        if ((match->flag & PARSE_ARG) && optHasArgument(s)) {
            parse_long_opt_arg(s);
            if (s->arg_val == NULL && (match->flag & OPT_ARG_REQUIRED))
                return optErrorMissingArgument(s);
        }
        s->opt_start = NULL;

    // Handle a short option with argument
    } else if (s->match_type == MATCH_SHORT) {
        optParseShortOpt(s);
        if ((match->flag & PARSE_ARG) && optHasArgument(s)) {
            optParseShortOptArg(s);
            if (s->arg_val == NULL && (match->flag & OPT_ARG_REQUIRED))
                return optErrorMissingArgument(s);
        }
    }

    optStripAcceptedOpts(s);

    return match->id;
}
