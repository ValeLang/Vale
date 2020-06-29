/** Handling for if nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef if_h
#define if_h

// If statement
typedef struct IfNode {
    IExpNodeHdr;
    Nodes *condblk;
} IfNode;

IfNode *newIfNode();

// Clone if
INode *cloneIfNode(CloneState *cstate, IfNode *node);

void ifPrint(IfNode *ifnode);

// if node name resolution
void ifNameRes(NameResState *pstate, IfNode *ifnode);

// Type check the if statement node
// - Every conditional expression must be a bool
// - if's vtype is specified/checked only when coerced by iexpCoerces
void ifTypeCheck(TypeCheckState *pstate, IfNode *ifnode, INode *expectType);

void ifRemoveReturns(IfNode *ifnode);

// Perform data flow analysis on an if expression
void ifFlow(FlowState *fstate, IfNode **ifnodep);

#endif
