/** Handling for generic variable declaration nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <string.h>
#include <assert.h>

// Create a new generic variable declaraction node
GenVarDclNode *newGVarDclNode(Name *namesym) {
    GenVarDclNode *var;
    newNode(var, GenVarDclNode, GenVarDclTag);
    var->vtype = NULL;
    var->namesym = namesym;
    return var;
}

// Serialize a generic variable node
void gVarDclPrint(GenVarDclNode *name) {
    inodeFprint("%s", &name->namesym->namestr);
}

// Perform name resolution
void gVarDclNameRes(NameResState *pstate, GenVarDclNode *var) {
    nametblHookNode(var->namesym, (INode*)var);
}

// Type check 
void gVarDclTypeCheck(TypeCheckState *pstate, GenVarDclNode *var) {
}
