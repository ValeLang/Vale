/** Handling for named value nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef namedval_h
#define namedval_h

// Cast to another type
typedef struct NamedValNode {
    IExpNodeHdr;
    INode *name;
    INode *val;
} NamedValNode;

NamedValNode *newNamedValNode(INode *name);

// Clone namedval
INode *cloneNamedValNode(CloneState *cstate, NamedValNode *node);

void namedValPrint(NamedValNode *node);

// Name resolution of named value node
void namedValNameRes(NameResState *pstate, NamedValNode *node);

// Type check named value node
void namedValTypeCheck(TypeCheckState *pstate, NamedValNode *node);

#endif
