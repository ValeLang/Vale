/** Handling for return nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef return_h
#define return_h

// Return/blockret/yield expression node
typedef struct ReturnNode {
    INodeHdr;
    Nodes *dealias;
    INode *exp;
} ReturnNode;

ReturnNode *newReturnNode();

// Clone return
INode *cloneReturnNode(CloneState *cstate, ReturnNode *node);

void returnPrint(ReturnNode *node);
// Name resolution for return
void returnNameRes(NameResState *pstate, ReturnNode *node);

// Type check for return statement
// Related analysis for return elsewhere:
// - Block ensures that return can only appear at end of block
// - NameDcl turns fn block's final expression into an implicit return
void returnTypeCheck(TypeCheckState *pstate, ReturnNode *node);

#endif
