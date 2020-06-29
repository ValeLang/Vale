/** Handling for named value nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new named value node
NamedValNode *newNamedValNode(INode *name) {
    NamedValNode *node;
    newNode(node, NamedValNode, NamedValTag);
    node->vtype = unknownType;
    node->name = name;
    return node;
}

// Clone namedval
INode *cloneNamedValNode(CloneState *cstate, NamedValNode *node) {
    NamedValNode *newnode;
    newnode = memAllocBlk(sizeof(NamedValNode));
    memcpy(newnode, node, sizeof(NamedValNode));
    newnode->name = cloneNode(cstate, node->name);
    newnode->val = cloneNode(cstate, node->val);
    return (INode *)newnode;
}

// Serialize named value node
void namedValPrint(NamedValNode *node) {
    inodePrintNode(node->name);
    inodeFprint(": ");
    inodePrintNode(node->val);
}

// Name resolution of named value node
void namedValNameRes(NameResState *pstate, NamedValNode *node) {
    inodeNameRes(pstate, &node->val);
}

// Type check named value node
void namedValTypeCheck(TypeCheckState *pstate, NamedValNode *node) {
    if (iexpTypeCheckAny(pstate, &node->val) == 0)
        return;
    node->vtype = ((IExpNode*)node->val)->vtype;
}
