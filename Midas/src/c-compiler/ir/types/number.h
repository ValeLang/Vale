/** Handling for primitive numbers
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef number_h
#define number_h

// For primitives such as integer, unsigned integet, floats
typedef struct NbrNode {
    INsTypeNodeHdr;
    unsigned char bits;    // e.g., int32 uses 32 bits
} NbrNode;

// Clone number node
INode *cloneNbrNode(CloneState *cstate, NbrNode *node);

void nbrTypePrint(NbrNode *node);
int isNbr(INode *node);

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *nbrFindSuper(INode *type1, INode *type2);

// Is from-type a subtype of to-struct (we know they are not the same)
TypeCompare nbrMatches(INode *totype, INode *fromtype, SubtypeConstraint constraint);

#endif
