if we want c++ to call into vale code, we need to generate c functions
that call into the vale code. \...and probably inline them.

and the generated c++ classes can basically be non-virtual things which
call the c functions which call the vale functions.

in fact, we can just put non-virtual methods on the c++ classes. we can
generate c++ wrappers for like everything.

templates will be difficult though.
