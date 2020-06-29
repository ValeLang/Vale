/** Handling for return nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new return statement node
ReturnNode *newReturnNode() {
    ReturnNode *node;
    newNode(node, ReturnNode, ReturnTag);
    node->exp = noValue;
    node->dealias = NULL;
    return node;
}

// Clone return
INode *cloneReturnNode(CloneState *cstate, ReturnNode *node) {
    ReturnNode *newnode;
    newnode = memAllocBlk(sizeof(ReturnNode));
    memcpy(newnode, node, sizeof(ReturnNode));
    newnode->exp = cloneNode(cstate, node->exp);
    return (INode *)newnode;
}

// Serialize a return statement
void returnPrint(ReturnNode *node) {
    inodeFprint(node->tag == BlockRetTag? "blockret " : "return ");
    inodePrintNode(node->exp);
}

// Name resolution for return
void returnNameRes(NameResState *pstate, ReturnNode *node) {
    inodeNameRes(pstate, &node->exp);
}

// Type check for return statement
// Related analysis for return elsewhere:
// - Block ensures that return can only appear at end of block
// - NameDcl turns fn block's final expression into an implicit return
void returnTypeCheck(TypeCheckState *pstate, ReturnNode *node) {
    // If we are returning the value from an 'if', recursively strip out any of its path's redundant 'return's
    if (node->exp->tag == IfTag)
        ifRemoveReturns((IfNode*)(node->exp));

    // Ensure the vtype of the expression can be coerced to the function's declared return type
    // while processing the exp nodes
    if (pstate->fnsig->rettype->tag == TTupleTag) {
        if (node->exp->tag != VTupleTag) {
            errorMsgNode(node->exp, ErrorBadTerm, "Not enough return values");
            return;
        }
        Nodes *retnodes = ((TupleNode*)node->exp)->elems;
        Nodes *rettypes = ((TupleNode*)pstate->fnsig->rettype)->elems;
        if (rettypes->used > retnodes->used) {
            errorMsgNode(node->exp, ErrorBadTerm, "Not enough return values");
            return;
        }
        uint32_t retcnt;
        INode **rettypesp;
        INode **retnodesp = &nodesGet(retnodes, 0);
        for (nodesFor(rettypes, retcnt, rettypesp)) {
            if (!iexpTypeCheckCoerce(pstate, *rettypesp, retnodesp++))
                errorMsgNode(*(retnodesp-1), ErrorInvType, "Return value's type does not match fn return type");
        }
        // Establish the type of the tuple (from the expected return value types)
        ((TupleNode *)node->exp)->vtype = pstate->fnsig->rettype;
    }
    else if (!iexpTypeCheckCoerce(pstate, pstate->fnsig->rettype, &node->exp)) {
        errorMsgNode((INode*)node, ErrorInvType, "Return expression type does not match return type on function");
        errorMsgNode((INode*)pstate->fnsig->rettype, ErrorInvType, "This is the declared function's return type");
    }
}
