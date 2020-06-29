/** Handling for if nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new If node
IfNode *newIfNode() {
    IfNode *ifnode;
    newNode(ifnode, IfNode, IfTag);
    ifnode->condblk = newNodes(4);
    ifnode->vtype = unknownType;
    return ifnode;
}

// Clone if
INode *cloneIfNode(CloneState *cstate, IfNode *node) {
    IfNode *newnode;
    newnode = memAllocBlk(sizeof(IfNode));
    memcpy(newnode, node, sizeof(IfNode));
    newnode->condblk = cloneNodes(cstate, node->condblk);
    return (INode *)newnode;
}

// Serialize an if statement
void ifPrint(IfNode *ifnode) {
    INode **nodesp;
    uint32_t cnt;
    int firstflag = 1;

    for (nodesFor(ifnode->condblk, cnt, nodesp)) {
        if (firstflag) {
            inodeFprint("if ");
            firstflag = 0;
            inodePrintNode(*nodesp);
        }
        else {
            inodePrintIndent();
            if (*nodesp == elseCond)
                inodeFprint("else");
            else {
                inodeFprint("elif ");
                inodePrintNode(*nodesp);
            }
        }
        inodePrintNL();
        inodePrintNode(*(++nodesp));
        cnt--;
    }
}

// Recursively strip 'returns' out of all block-ends in 'if' (see returnPass)
void ifRemoveReturns(IfNode *ifnode) {
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(ifnode->condblk, cnt, nodesp)) {
        INode **laststmt;
        cnt--; nodesp++;
        laststmt = &nodesLast(((BlockNode*)*nodesp)->stmts);
        if ((*laststmt)->tag == ReturnTag)
            *laststmt = ((ReturnNode*)*laststmt)->exp;
        if ((*laststmt)->tag == IfTag)
            ifRemoveReturns((IfNode*)*laststmt);
    }
}

// if node name resolution
void ifNameRes(NameResState *pstate, IfNode *ifnode) {
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(ifnode->condblk, cnt, nodesp)) {
        inodeNameRes(pstate, nodesp);
    }
}

// Detect when all closed variants are matched, and turn last match into 'else'
// We know the condition is an 'is' node
void ifExhaustCheck(IfNode *ifnode, CastNode *condition) {
    StructNode *strnode = (StructNode*)iexpGetDerefTypeDcl(condition->exp);

    // Exhaustiveness check only on closed variants against a base trait
    if (strnode->tag != StructTag || !(strnode->flags & TraitType) ||
        strnode->basetrait != NULL || 0 == (strnode->flags & (HasTagField | SameSize)))
        return;

    // We need to detect that all possible variants are accounted for
    uint32_t lowestcnt = ifnode->condblk->used;
    INode **varnodesp;
    uint32_t varcnt;
    for (nodesFor(strnode->derived, varcnt, varnodesp)) {
        // Look for 'is' check on same value against *varnodesp
        int found = 0;
        INode **nodesp;
        uint32_t cnt;
        for (nodesFor(ifnode->condblk, cnt, nodesp)) {
            if ((*nodesp)->tag == IsTag) {
                CastNode *isnode = (CastNode*)*nodesp;
                if (isnode->exp == condition->exp && itypeGetDerefTypeDcl(isnode->typ) == *varnodesp) {
                    found = 1;
                    if (cnt < lowestcnt)
                        lowestcnt = cnt;
                    break;
                }
            }
            ++nodesp; --cnt;
        }
        if (found == 0)
            return;
    }
    // Turn last 'is' condition into 'else'
    nodesGet(ifnode->condblk, ifnode->condblk->used - lowestcnt) = elseCond;
}

// Type check the if statement node
// - Every conditional expression must be a bool
// - else can only be last
// All branches must resolve to either the expected type or the same inferred supertype
// Coercion is performed on branches as needed to accomplish this, or errors result
void ifTypeCheck(TypeCheckState *pstate, IfNode *ifnode, INode *expectType) {
    int hasElse = 0;
    TypeCompare match = EqMatch;
    INode *maybeType = unknownType;
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(ifnode->condblk, cnt, nodesp)) {

        // Validate conditional node
        inodeTypeCheckAny(pstate, nodesp);
        // Detect when all closed variants are matched, and turn last match into 'else'
        if ((*nodesp)->tag == IsTag)
            ifExhaustCheck(ifnode, (CastNode*)*nodesp);

        if (*nodesp != elseCond) {
            if (0 == iexpCoerce(nodesp, (INode*)boolType))
                errorMsgNode(*nodesp, ErrorInvType, "Conditional expression must be coercible to boolean value.");
        }
        else {
            hasElse = 1;
            if (cnt > 2) {
                errorMsgNode(*(nodesp + 1), ErrorInvType, "match on everything should be last.");
            }
        }

        // Validate the node performed when previous condition is true
        ++nodesp; --cnt;
        if (!iexpTypeCheckCoerce(pstate, expectType, nodesp)) {
            errorMsgNode(*nodesp, ErrorInvType, "Expression does not match expected type.");
            match = NoMatch;
        }
        else if (!isExpNode(*nodesp)) {
            match = NoMatch;
        }
        else {
            switch (iexpMultiInfer(expectType, &maybeType, nodesp)) {
            case NoMatch:
                match = NoMatch;
                break;
            case CastSubtype:
                if (match != NoMatch)
                    match = CastSubtype;
                break;
            case ConvSubtype:
                if (match != NoMatch)
                    match = ConvSubtype;
                break;
            default:
                break;
            }
        }
    }

    if (expectType == noCareType)
        return;

    // All paths must return a typed value. Absence of an 'else' clause makes that impossible
    if (!hasElse) {
        errorMsgNode((INode*)ifnode, ErrorInvType, "if requires an 'else' clause (or exhaustive matches) to return a value");
        return;
    }

    // When expectType specified, all branches have been coerced (or not w/ errors)
    if (expectType != unknownType) {
        ifnode->vtype = expectType;
        return;
    }

    // If expected type is unknown, set the inferred type
    ifnode->vtype = maybeType;

    //  If coercion is needed for some blocks, perform them as needed
    if (match == ConvSubtype || match == CastSubtype) {
        for (nodesFor(ifnode->condblk, cnt, nodesp)) {
            ++nodesp; --cnt;
            // Since generation requires this node to be a block,
            // perform coercion on the last statement
            BlockNode *blk = (BlockNode *)*nodesp;
            iexpCoerce(&nodesLast(blk->stmts), maybeType);
        }
    }
}

// Perform data flow analysis on an if expression
void ifFlow(FlowState *fstate, IfNode **ifnodep) {
    IfNode *ifnode = *ifnodep;
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(ifnode->condblk, cnt, nodesp)) {
        if (*nodesp != elseCond)
            flowLoadValue(fstate, nodesp);
        nodesp++; cnt--;
        blockFlow(fstate, (BlockNode**)nodesp);
        flowAliasReset();
    }
}
