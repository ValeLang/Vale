/** Handling for pointer types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef pointer_h
#define pointer_h

// For pointer or deref nodes
typedef struct {
    ITypeNodeHdr;
    INode *vtexp;    // Value type
} StarNode;

// Create a new "star" node (ptr type or deref exp) whose vtexp will be filled in later
StarNode *newStarNode(uint16_t tag);

// Clone ptr or deref node
INode *cloneStarNode(CloneState *cstate, StarNode *node);

// Serialize a pointer type
void ptrPrint(StarNode *node);

// Name resolution of a pointer type
void ptrNameRes(NameResState *pstate, StarNode *node);

// Type check a pointer type
void ptrTypeCheck(TypeCheckState *pstate, StarNode *name);

// Compare two pointer signatures to see if they are equivalent
int ptrEqual(StarNode *node1, StarNode *node2);

// Will from pointer coerce to a to pointer (we know they are not the same)
TypeCompare ptrMatches(StarNode *to, StarNode *from, SubtypeConstraint constraint);

#endif
