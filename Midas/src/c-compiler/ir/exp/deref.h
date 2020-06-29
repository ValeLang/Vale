/** Handling for deref nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef deref_h
#define deref_h

// The definition of StarNode etc. is in pointer.h

void derefPrint(StarNode *node);

// Type check deref node
void derefTypeCheck(TypeCheckState *pstate, StarNode *node);

// Inject automatic deref node, if node's type is a ref or ptr. Return 1 if dereffed.
int derefInject(INode **node);


#endif
