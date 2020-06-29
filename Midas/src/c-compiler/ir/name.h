/** Name handling - general purpose
 *
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef name_h
#define name_h

#include <stdlib.h>

// Name is an interned symbol, unique by its collection of characters (<=255)
// A name can be hashed into the global name table or a particular node's namespace.
// The struct for a name is an unmovable allocated block in memory
typedef struct Name {
    INode *node;             // Node currently assigned to name
    size_t hash;             // Name's computed hash
    unsigned char namesz;    // Number of characters in the name (<=255)
    char namestr;            // First byte of name's string (the rest follows)
} Name;

// Common symbols - see nametbl.c
extern Name *anonName;  // "_" - the absence of a name
extern Name *selfName;  // "self"
extern Name *selfTypeName; // "Self"
extern Name *thisName;  // "this"
extern Name *cloneName; // "clone" method
extern Name *finalName; // "final" method

extern Name *plusEqName;   // "+="
extern Name *minusEqName;  // "-="
extern Name *multEqName;   // "*="
extern Name *divEqName;    // "/="
extern Name *remEqName;    // "%="
extern Name *orEqName;     // "|="
extern Name *andEqName;    // "&="
extern Name *xorEqName;    // "^="
extern Name *shlEqName;    // "<<="
extern Name *shrEqName;    // ">>="
extern Name *lessDashName; // "<-"

extern Name *plusName;     // "+"
extern Name *minusName;    // "-"
extern Name *istrueName;   // "isTrue"
extern Name *multName;     // "*"
extern Name *divName;      // "/"
extern Name *remName;      // "%"
extern Name *orName;       // "|"
extern Name *andName;      // "&"
extern Name *xorName;      // "^"
extern Name *shlName;      // "<<"
extern Name *shrName;      // ">>"

extern Name *incrName;     // "++"
extern Name *decrName;     // "--"
extern Name *incrPostName; // "_++"
extern Name *decrPostName; // "_--"

extern Name *eqName;       // "=="
extern Name *neName;       // "!="
extern Name *leName;       // "<="
extern Name *ltName;       // "<"
extern Name *geName;       // ">="
extern Name *gtName;       // ">"

extern Name *parensName;   // "()"
extern Name *indexName;    // "[]"
extern Name *refIndexName; // "&[]"

extern Name *optionName;   // "Option"

typedef struct VarDclNode VarDclNode;
typedef struct FnDclNode FnDclNode;

// Create new prefix that concatenates a new name to the old prefix, followed by _
void nameConcatPrefix(char **prefix, char *name);
// Create globally unique variable name, prefixed by module/type name
void nameGenVarName(VarDclNode *node, char *prefix);
// Create globally unique mangled function name, prefixed by module/type name
void nameGenFnName(FnDclNode *node, char *prefix);


#endif
