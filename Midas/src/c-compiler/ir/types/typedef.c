/** Handling for typedef declaration nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <string.h>
#include <assert.h>

// Create a new typedef declaraction node
TypedefNode *newTypedefNode(Name *namesym) {
    TypedefNode *var;
    newNode(var, TypedefNode, TypedefTag);
    var->typeval = NULL;
    var->namesym = namesym;
    return var;
}

// Serialize a typedef node
void typedefPrint(TypedefNode *name) {
    inodeFprint("typedef %s ", &name->namesym->namestr);
    inodePrintNode(name->typeval);
}

// Perform name resolution
void typedefNameRes(NameResState *pstate, TypedefNode *var) {
    inodeNameRes(pstate, &var->typeval);
    nametblHookNode(var->namesym, (INode*)var);
}

// Type check 
void typedefTypeCheck(TypeCheckState *pstate, TypedefNode *var) {
    itypeTypeCheck(pstate, &var->typeval);
}
