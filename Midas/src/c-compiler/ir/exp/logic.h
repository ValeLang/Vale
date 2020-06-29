/** Handling for logic nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef logic_h
#define logic_h

// Logic operator: not, or, and
typedef struct LogicNode {
    IExpNodeHdr;
    INode *lexp;
    INode *rexp;
} LogicNode;

LogicNode *newLogicNode(int16_t typ);

// Clone logic node
INode *cloneLogicNode(CloneState *cstate, LogicNode *node);

void logicPrint(LogicNode *node);

// Name resolution of not logic node
void logicNotNameRes(NameResState *pstate, LogicNode *node);

// Type check not logic node
void logicNotTypeCheck(TypeCheckState *pstate, LogicNode *node);

// Name resolution of logic node
void logicNameRes(NameResState *pstate, LogicNode *node);

// Type check logic node
void logicTypeCheck(TypeCheckState *pstate, LogicNode *node);

#endif
