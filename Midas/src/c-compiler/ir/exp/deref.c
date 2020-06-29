/** Handling for deref nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Inject automatic deref node, if node's type is a ref or ptr. Return 1 if dereffed.
int derefInject(INode **node) {
    INode *nodetype = iexpGetTypeDcl(*node);
    if (nodetype->tag != RefTag && nodetype->tag != PtrTag)
        return 0;
    StarNode *deref = newStarNode(DerefTag);
    deref->vtexp = *node;
    if (nodetype->tag == PtrTag)
        deref->vtype = ((StarNode*)((IExpNode *)*node)->vtype)->vtexp;
    else
        deref->vtype = ((RefNode*)((IExpNode *)*node)->vtype)->vtexp;
    *node = (INode*)deref;
    return 1;
}

// Serialize deref
void derefPrint(StarNode *node) {
    inodeFprint("*");
    inodePrintNode(node->vtexp);
}

// Type check deref node
void derefTypeCheck(TypeCheckState *pstate, StarNode *node) {
    if (iexpTypeCheckAny(pstate, &node->vtexp) == 0)
        return;

    INode *ptype = ((IExpNode *)node->vtexp)->vtype;
    if (ptype->tag == RefTag)
        node->vtype = ((RefNode*)ptype)->vtexp;
    else if (ptype->tag == PtrTag)
        node->vtype = ((StarNode*)ptype)->vtexp;
    else
        errorMsgNode((INode*)node, ErrorNotPtr, "May only de-reference a simple reference or pointer.");
}
