/** Handling for break nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef break_h
#define break_h

// break/continue statement
typedef struct BreakNode {
    INodeHdr;
    INode *life;         // nullable
    INode *exp;          // nullable
    Nodes *dealias;
} BreakNode;

BreakNode *newBreakNode();

// Name resolution for break
void breakNameRes(NameResState *pstate, BreakNode *node);

// Clone break
INode *cloneBreakNode(CloneState *cstate, BreakNode *node);

void breakTypeCheck(TypeCheckState *pstate, BreakNode *node);

typedef struct LoopNode LoopNode;
LoopNode *breakFindLoopNode(TypeCheckState *pstate, INode *life);

#endif
