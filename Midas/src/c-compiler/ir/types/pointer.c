/** Handling for pointers
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"
#include <memory.h>

// Create a new "star" node (ptr type or deref exp) whose vtexp will be filled in later
StarNode *newStarNode(uint16_t tag) {
    StarNode *node;
    newNode(node, StarNode, tag);
    node->vtype = unknownType;
    return node;
}

// Clone ptr or deref node
INode *cloneStarNode(CloneState *cstate, StarNode *node) {
    StarNode *newnode = memAllocBlk(sizeof(StarNode));
    memcpy(newnode, node, sizeof(StarNode));
    newnode->vtexp = cloneNode(cstate, node->vtexp);
    return (INode *)newnode;
}

// Serialize a pointer type
void ptrPrint(StarNode *node) {
    inodeFprint("*");
    inodePrintNode(node->vtexp);
}

// Name resolution of a pointer type
void ptrNameRes(NameResState *pstate, StarNode *node) {
    inodeNameRes(pstate, &node->vtexp);
    node->tag = isTypeNode(node->vtexp) ? PtrTag : DerefTag;
}

// Type check a pointer type
void ptrTypeCheck(TypeCheckState *pstate, StarNode *node) {
    if (itypeTypeCheck(pstate, &node->vtexp) == 0)
        return;
}

// Compare two pointer signatures to see if they are equivalent
int ptrEqual(StarNode *node1, StarNode *node2) {
    return itypeIsSame(node1->vtexp, node2->vtexp);
}

// Will from pointer coerce to a to pointer
TypeCompare ptrMatches(StarNode *to, StarNode *from, SubtypeConstraint constraint) {
    // Since pointers support both read and write permissions, value type invariance is expected
    return itypeIsSame(to->vtexp, from->vtexp)? EqMatch : NoMatch;
}
