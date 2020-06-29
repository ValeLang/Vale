/** Expression nodes that return a typed value
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef iexp_h
#define iexp_h

// Castable structure for all typed nodes
typedef struct IExpNode {
    IExpNodeHdr;
} IExpNode;

// Return node's type's declaration node
// (Note: only use after it has been type-checked)
INode *iexpGetTypeDcl(INode *node);

// Return node's type's declaration node
// and when it is a a pointer/ref, get its type declaration node
// (Note: only use after it has been type-checked)
INode *iexpGetDerefTypeDcl(INode *node);

// Type check node we expect to be an expression. Return 0 if not.
int iexpTypeCheckAny(TypeCheckState *pstate, INode **from);

// Return whether it is okay for from expression to be coerced to to-type
TypeCompare iexpMatches(INode **from, INode *totype, SubtypeConstraint constraint);

// Coerce from-node's type to 'to' expected type, if needed
// Return 1 if type "matches", 0 otherwise
int iexpCoerce(INode **from, INode *totypep);

// Perform full type check on from-node and ensure it is an expression.
// Then coerce from-node's type to 'to' expected type, if needed
// Return 1 if type "matches", 0 otherwise
int iexpTypeCheckCoerce(TypeCheckState *pstate, INode *to, INode **from);

// Used by 'if' and 'loop' to infer the type in common across all branches,
// one branch at a time. Errors on bad type match and returns Match condition.
// - expectType is the final type expected by receiver
// - maybeType is the inferred type in common
// - from is the current branch whose type is being examined
int iexpMultiInfer(INode *expectType, INode **maybeType, INode **from);

// Return true if an lval, and 0 if not.
int iexpIsLval(INode *lval);

// Ensure it is a lval, return error and 0 if not.
int iexpIsLvalError(INode *lval);

// Extract lval variable, scope and overall permission from lval
INode *iexpGetLvalInfo(INode *lval, INode **lvalperm, uint16_t *scope);

// Are types the same (no coercion)
int iexpSameType(INode *to, INode **from);

// Retrieve the permission flags for the node
uint16_t iexpGetPermFlags(INode *node);

#endif
