/** Handling for assignment nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <assert.h>

// Create a new assignment node
AssignNode *newAssignNode(int16_t assigntype, INode *lval, INode *rval) {
    AssignNode *node;
    newNode(node, AssignNode, AssignTag);
    node->assignType = assigntype;
    node->lval = lval;
    node->rval = rval;
    return node;
}

// Clone assign
INode *cloneAssignNode(CloneState *cstate, AssignNode *node) {
    AssignNode *newnode;
    newnode = memAllocBlk(sizeof(AssignNode));
    memcpy(newnode, node, sizeof(AssignNode));
    newnode->lval = cloneNode(cstate, node->lval);
    newnode->rval = cloneNode(cstate, node->rval);
    return (INode *)newnode;
}

// Serialize assignment node
void assignPrint(AssignNode *node) {
    inodeFprint("(=, ");
    inodePrintNode(node->lval);
    inodeFprint(", ");
    inodePrintNode(node->rval);
    inodeFprint(")");
}

// Name resolution for assignment node
void assignNameRes(NameResState *pstate, AssignNode *node) {
    inodeNameRes(pstate, &node->lval);
    inodeNameRes(pstate, &node->rval);
}

// Type check a single matched assignment between lval and rval
// - lval must be a lval
// - rval's type must coerce to lval's type
void assignSingleCheck(TypeCheckState *pstate, INode *lval, INode **rval) {
    // '_' named lval need not be checked. It is a placeholder that just swallows a value
    if (lval->tag == VarNameUseTag && ((NameUseNode*)lval)->namesym == anonName)
        return;

    if (iexpIsLvalError(lval) == 0) {
        return;
    }
    if (iexpTypeCheckCoerce(pstate, ((IExpNode*)lval)->vtype, rval) == 0) {
        errorMsgNode(*rval, ErrorInvType, "Expression's type does not match lval's type");
        return;
    }
}

// Handle parallel assignment (multiple values on both sides)
void assignParaCheck(TypeCheckState *pstate, TupleNode *lval, TupleNode *rval) {
    Nodes *lnodes = lval->elems;
    Nodes *rnodes = rval->elems;
    if (lnodes->used > rnodes->used) {
        errorMsgNode((INode*)rval, ErrorBadTerm, "Not enough tuple values given to lvals");
        return;
    }
    uint32_t lcnt;
    INode **lnodesp;
    INode **rnodesp = &nodesGet(rnodes, 0);
    uint32_t rcnt = rnodes->used;
    for (nodesFor(lnodes, lcnt, lnodesp)) {
        assignSingleCheck(pstate, *lnodesp, rnodesp++);
        rcnt--;
    }
    rval->vtype = lval->vtype;
}

// Handle when single function/expression returns to multiple lval
void assignMultRetCheck(TypeCheckState *pstate, TupleNode *lval, INode **rval) {
    if (iexpTypeCheckAny(pstate, rval) == 0)
        return;;
    INode *rtype = ((IExpNode *)*rval)->vtype;
    if (rtype->tag != TTupleTag) {
        errorMsgNode(*rval, ErrorBadTerm, "Not enough values for lvals");
        return;
    }
    Nodes *lnodes = lval->elems;
    Nodes *rtypes = ((TupleNode*)((IExpNode *)*rval)->vtype)->elems;
    if (lnodes->used > rtypes->used) {
        errorMsgNode(*rval, ErrorBadTerm, "Not enough tuple values for lvals");
        return;
    }
    uint32_t lcnt;
    INode **lnodesp;
    INode **rtypep = &nodesGet(rtypes, 0);
    for (nodesFor(lnodes, lcnt, lnodesp)) {
        if (iexpIsLvalError(*lnodesp) == 0)
            continue;
        if (itypeIsSame(((IExpNode *)*lnodesp)->vtype, *rtypep++) == 0)
            errorMsgNode(*lnodesp, ErrorInvType, "Return value's type does not match lval's type");
    }
}

// Handle when multiple expressions assigned to single lval
void assignToOneCheck(TypeCheckState *pstate, INode *lval, TupleNode *rval) {
    Nodes *rnodes = rval->elems;
    INode **rnodesp = &nodesGet(rnodes, 0);
    uint32_t rcnt = rnodes->used;
    assignSingleCheck(pstate, lval, rnodesp++);
}

// Type checking for assignment node
void assignTypeCheck(TypeCheckState *pstate, AssignNode *node) {
    if (iexpTypeCheckAny(pstate, &node->lval) == 0)
        return;

    // Handle tuple decomposition for parallel assignment
    INode *lval = node->lval;
    if (lval->tag == VTupleTag) {
        if (node->rval->tag == VTupleTag)
            assignParaCheck(pstate, (TupleNode*)node->lval, (TupleNode*)node->rval);
        else
            assignMultRetCheck(pstate, (TupleNode*)node->lval, &node->rval);
    }
    else {
        if (node->rval->tag == VTupleTag)
            assignToOneCheck(pstate, node->lval, (TupleNode*)node->rval);
        else
            assignSingleCheck(pstate, node->lval, &node->rval);
    }
    node->vtype = ((IExpNode*)node->rval)->vtype;
}

// Perform data flow analysis between two single assignment nodes:
// - Lval is mutable
// - Borrowed reference lifetime is greater than its container
void assignSingleFlow(INode *lval, INode **rval) {
    // '_' named lval is a placeholder that swallows (maybe drops) a value
    if (lval->tag == VarNameUseTag && ((NameUseNode*)lval)->namesym == anonName) {
        // When lval = '_' and this is an own reference, we may have a problem
        // If this assignment is supposed to return a reference, it cannot
        if (flowAliasGet(0) > 0) {
            RefNode *reftype = (RefNode *)((IExpNode*)*rval)->vtype;
            if (reftype->tag == RefTag && reftype->region == (INode*)soRegion)
                errorMsgNode((INode*)lval, ErrorMove, "This frees reference. The reference is inaccessible for use.");
        }
    }

    // Non-anonymous lval increments alias counter
    flowAliasIncr();

    uint16_t lvalscope;
    INode *lvalperm;
    INode *lvalvar = iexpGetLvalInfo(lval, &lvalperm, &lvalscope);
    if (lval->tag == NameUseTag)
        ((VarDclNode*)lvalvar)->flowtempflags |= VarInitialized;
    if (!(MayWrite & permGetFlags(lvalperm))) {
        errorMsgNode(lval, ErrorNoMut, "You do not have permission to modify lval");
        return;
    }

    RefNode* rvaltype = (RefNode *)((IExpNode*)*rval)->vtype;
    RefNode* lvaltype = (RefNode *)((IExpNode*)lval)->vtype;
    if (rvaltype->tag == RefTag && lvaltype->tag == RefTag && lvaltype->region == borrowRef) {
        if (lvalscope < rvaltype->scope) {
            errorMsgNode(lval, ErrorInvType, "lval outlives the borrowed reference you are storing");
            return;
        }
    }
}

// Handle parallel assignment (multiple values on both sides)
void assignParaFlow(TupleNode *lval, TupleNode *rval) {
    Nodes *lnodes = lval->elems;
    Nodes *rnodes = rval->elems;
    uint32_t lcnt;
    INode **lnodesp;
    INode **rnodesp = &nodesGet(rnodes, 0);
    uint32_t rcnt = rnodes->used;
    int16_t aliasfocus = 0;
    flowAliasSize(lnodes->used);
    for (nodesFor(lnodes, lcnt, lnodesp)) {
        flowAliasFocus(aliasfocus++);
        assignSingleFlow(*lnodesp, rnodesp++);
        rcnt--;
    }
}

// Handle when single function/expression returns to multiple lval
void assignMultRetFlow(TupleNode *lval, INode **rval) {
    Nodes *lnodes = lval->elems;
    INode *rtype = ((IExpNode *)*rval)->vtype;
    Nodes *rtypes = ((TupleNode*)((IExpNode *)*rval)->vtype)->elems;
    uint32_t lcnt;
    INode **lnodesp;
    INode **rtypep = &nodesGet(rtypes, 0);
    for (nodesFor(lnodes, lcnt, lnodesp)) {
        // Need mutability check and borrowed lifetime check
    }
}

// Handle when multiple expressions assigned to single lval
void assignToOneFlow(INode *lval, TupleNode *rval) {
    Nodes *rnodes = rval->elems;
    INode **rnodesp = &nodesGet(rnodes, 0);
    uint32_t rcnt = rnodes->used;
    assignSingleFlow(lval, rnodesp++);
}

// Perform data flow analysis on assignment node
// - lval needs to be mutable.
// - borrowed reference lifetimes must exceed lifetime of lval
void assignFlow(FlowState *fstate, AssignNode **nodep) {
    AssignNode *node = *nodep;

    // Handle tuple decomposition for parallel assignment
    INode *lval = node->lval;
    if (lval->tag == VTupleTag) {
        if (node->rval->tag == VTupleTag)
            assignParaFlow((TupleNode*)node->lval, (TupleNode*)node->rval);
        else
            assignMultRetFlow((TupleNode*)node->lval, &node->rval);
    }
    else {
        if (node->rval->tag == VTupleTag)
            assignToOneFlow(node->lval, (TupleNode*)node->rval);
        else {
            assignSingleFlow(node->lval, &node->rval);
        }
    }

    flowLoadValue(fstate, &node->rval);
}
