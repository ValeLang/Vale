/** Handling for loop nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new loop node
LoopNode *newLoopNode() {
    LoopNode *node;
    newNode(node, LoopNode, LoopTag);
    node->vtype = unknownType;  // This will be overridden with loop-as-expr
    node->blk = NULL;
    node->life = NULL;
    node->breaks = newNodes(2);
    return node;
}

// Clone loop
INode *cloneLoopNode(CloneState *cstate, LoopNode *node) {
    uint32_t dclpos = cloneDclPush();
    LoopNode *newnode;
    newnode = memAllocBlk(sizeof(LoopNode));
    memcpy(newnode, node, sizeof(LoopNode));
    newnode->breaks = cloneNodes(cstate, node->breaks);
    newnode->life = (LifetimeNode*)cloneNode(cstate, (INode*)node->life);
    newnode->blk = cloneNode(cstate, node->blk);
    cloneDclPop(dclpos);
    return (INode *)newnode;
}

// Serialize a loop node
void loopPrint(LoopNode *node) {
    inodeFprint("loop");
    inodePrintNL();
    inodePrintNode(node->blk);
}

// loop block name resolution
void loopNameRes(NameResState *pstate, LoopNode *node) {
    // If loop declares a lifetime declaration, hook into name table for name res
    LifetimeNode *lifenode = node->life;
    if (pstate->scope > 1 && lifenode) {
        if (lifenode->namesym->node) {
            errorMsgNode((INode *)lifenode, ErrorDupName, "Lifetime is already defined. Only one allowed.");
            errorMsgNode((INode*)lifenode->namesym->node, ErrorDupName, "This is the conflicting definition for that name.");
        }
        else {
            lifenode->life = pstate->scope;
            // Add name to global name table (containing block will unhook it later)
            nametblHookNode(lifenode->namesym, (INode*)lifenode);
        }
    }
    inodeNameRes(pstate, &node->blk);
}

// Type check the loop block, and set up for type check of breaks
// All breaks must resolve to either the expected type or the same inferred supertype
// Coercion is performed on breaks as needed to accomplish this, or errors result
void loopTypeCheck(TypeCheckState *pstate, LoopNode *node, INode *expectType) {

    // Push loop node on loop stack for use by break type check
    if (pstate->loopcnt >= TypeCheckLoopMax) {
        errorMsgNode((INode*)node, ErrorBadArray, "Overflowing fixed-size loop stack.");
        errorExit(100, "Unrecoverable error!");
    }
    pstate->loopstack[pstate->loopcnt++] = node;

    // This will ensure that every break is registered to the loop
    inodeTypeCheck(pstate, &node->blk, noCareType);
    if (node->breaks->used == 0)
        errorMsgNode((INode*)node, WarnLoop, "Loop may never stop without a break.");
    if (node->blk->tag == BlockTag)
        blockNoBreak((BlockNode*)node->blk);

    --pstate->loopcnt;

    // Do inference on all registered breaks to ensure they all return the expected type

    INode *maybeType = unknownType;
    TypeCompare match = EqMatch;
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(node->breaks, cnt, nodesp)) {
        INode **breakexp = &((BreakNode *)*nodesp)->exp;
        if (*breakexp == noValue && expectType != noCareType) {
            errorMsgNode(*nodesp, ErrorInvType, "Loop expected a typed expression");
            match = NoMatch;
            continue;
        }
        if (!iexpTypeCheckCoerce(pstate, expectType, breakexp)) {
            errorMsgNode(*breakexp, ErrorInvType, "Expression does not match expected type.");
            match = NoMatch;
        }
        else if (!isExpNode(*breakexp)) {
            match = NoMatch;
        }
        else {
            switch (iexpMultiInfer(expectType, &maybeType, breakexp)) {
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

    // When expectType specified, all branches have been coerced (or not w/ errors)
    if (expectType != unknownType) {
        node->vtype = expectType;
        return;
    }

    // If expected type is unknown, set the inferred type
    node->vtype = maybeType;

    //  If coercion is needed for some blocks, perform them as needed
    if (match == ConvSubtype || match == CastSubtype) {
        for (nodesFor(node->breaks, cnt, nodesp)) {
            INode **breakexp = &((BreakNode *)*nodesp)->exp;
            iexpCoerce(breakexp, maybeType);
        }
    }
}

// Bidirectional type inference
// Perform data flow analysis on a loop expression
void loopFlow(FlowState *fstate, LoopNode **nodep) {
    LoopNode *node = *nodep;
    blockFlow(fstate, (BlockNode**)&node->blk);
}
