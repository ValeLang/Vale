/** Handling for borrow expression nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef borrow_h
#define borrow_h

// Uses RefNode defined in reference.h

// Inject a borrow mutable node on some node (expected to be an lval)
void borrowMutRef(INode **node, INode* type, INode *perm);

// Auto-inject a borrow note in front of 'from', to create totypedcl type
void borrowAuto(INode **from, INode *totypedcl);

// Can we safely auto-borrow to match expected type?
// Note: totype has already done GetTypeDcl
int borrowAutoMatches(INode *from, RefNode *totype);

void borrowPrint(RefNode *node);

// Type check borrow node
void borrowTypeCheck(TypeCheckState *pstate, RefNode **node);

// Perform data flow analysis on addr node
void borrowFlow(FlowState *fstate, RefNode **nodep);

#endif
