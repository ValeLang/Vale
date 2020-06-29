/** Handling for generic variable declaration nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef genvardcl_h
#define genvardcl_h

// Generic variable declaration node
typedef struct GenVarDclNode {
    IExpNodeHdr;             // 'vtype': type of this name's value
    Name *namesym;
} GenVarDclNode;

// Create a new generic variable declaraction node
GenVarDclNode *newGVarDclNode(Name *namesym);

void gVarDclPrint(GenVarDclNode *var);

// Name resolution
void gVarDclNameRes(NameResState *pstate, GenVarDclNode *var);

// Type check generic variable declaration
void gVarDclTypeCheck(TypeCheckState *pstate, GenVarDclNode *var);

#endif
