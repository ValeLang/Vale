/** Handling for sizeof nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef sizeof_h
#define sizeof_h

// Get size of a type
typedef struct SizeofNode {
    IExpNodeHdr;
    INode *type;
} SizeofNode;

SizeofNode *newSizeofNode();

// Clone sizeof
INode *cloneSizeofNode(CloneState *cstate, SizeofNode *node);

void sizeofPrint(SizeofNode *node);

// Name resolution of sizeof node
void sizeofNameRes(NameResState *pstate, SizeofNode *node);

// Type check sizeof node
void sizeofTypeCheck(TypeCheckState *pstate, SizeofNode *node);

#endif
