/** The Data Flow analysis pass
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"

#include <assert.h>
#include <memory.h>

void flowHandleMove(INode *node) {
    uint16_t moveflag = itypeGetTypeDcl(((IExpNode *)node)->vtype)->flags & MoveType;
    if (moveflag)
        errorMsgNode(node, WarnCopy, "No current support for move. Be sure this is safe!");
}

// If needed, inject an alias node for rc/own references
void flowInjectAliasNode(INode **nodep, int16_t rvalcount) {
    int16_t count;
    int16_t *counts = NULL;
    INode *vtype = ((IExpNode*)*nodep)->vtype;
    if (vtype->tag != TTupleTag) {
        // No need for injected node if we are not dealing with rc/own references and if alias calc = 0
        RefNode *reftype = (RefNode *)itypeGetTypeDcl(vtype);
        if (reftype->tag != RefTag || !(reftype->region == (INode*)rcRegion || reftype->region == (INode*)soRegion))
            return;
        count = flowAliasGet(0) + rvalcount;
        if (count == 0 || (reftype->region == (INode*)soRegion && count > 0))
            return;
    }
    else {
        // First, decide if we need an alias node.
        // It is needed only if any returned value in tuple is rc/own with non-zero alias count
        // Note: aliasing count values are updated in case we want them
        TupleNode *tuple = (TupleNode *)vtype;
        int needaliasnode = 0;
        INode **nodesp;
        uint32_t cnt;
        size_t index = 0;
        flowAliasSize(count = tuple->elems->used);
        for (nodesFor(tuple->elems, cnt, nodesp)) {
            RefNode *reftype = (RefNode *)itypeGetTypeDcl(*nodesp);
            if (reftype->tag != RefTag || !(reftype->region == (INode*)rcRegion || reftype->region == (INode*)soRegion)) {
                flowAliasPut(index++, 0);
                continue;
            }
            int16_t tcount = flowAliasGet(index) + rvalcount;
            if (reftype->region == (INode*)soRegion && tcount > 0)
                tcount = 0;
            flowAliasPut(index++, tcount);
            if (tcount != 0)
                needaliasnode = 1;
        }
        if (!needaliasnode)
            return;

        // Allocate and fill memory segment containing alias counters
        int16_t *countp = counts = (int16_t *)memAllocBlk(count * sizeof(int16_t));
        int16_t pos = 0;
        while (pos < count)
            *countp++ = flowAliasGet(pos++);
    }

    // Inject alias count node
    AliasNode *aliasnode;
    newNode(aliasnode, AliasNode, AliasTag);
    aliasnode->exp = *nodep;
    aliasnode->vtype = vtype;
    aliasnode->aliasamt = count;
    aliasnode->counts = counts;
    *nodep = (INode*)aliasnode;
}


// Perform data flow analysis on a node whose value we intend to load
// At minimum, we check that it is a valid, readable value
void flowLoadValue(FlowState *fstate, INode **nodep) {
    // Handle specific nodes here - lvals (read check) + literals + fncall
    // fncall + literals? do not need copy check - it can return
    switch ((*nodep)->tag) {
    case BlockTag:
        blockFlow(fstate, (BlockNode **)nodep); break;
    case IfTag:
        ifFlow(fstate, (IfNode **)nodep); break;
    case LoopTag:
        loopFlow(fstate, (LoopNode **)nodep); break;
    case AssignTag:
        assignFlow(fstate, (AssignNode **)nodep); break;
    case FnCallTag:
        fnCallFlow(fstate, (FnCallNode**)nodep);
        flowInjectAliasNode(nodep, -1);
        break;
    case ArrayBorrowTag:
    case BorrowTag:
        borrowFlow(fstate, (RefNode **)nodep);
        break;
    case ArrayAllocTag:
    case AllocateTag:
        allocateFlow(fstate, (RefNode **)nodep);
        flowInjectAliasNode(nodep, -1);
        break;
    case VTupleTag:
    {
        INode **nodesp;
        uint32_t cnt;
        uint32_t index = 0;
        flowAliasSize(((TupleNode *)*nodep)->elems->used);
        for (nodesFor(((TupleNode *)*nodep)->elems, cnt, nodesp)) {
            // pull out specific alias counter for resolution
            size_t svAliasPos = flowAliasPushNew(flowAliasGet(index++));
            flowLoadValue(fstate, nodesp);
            flowAliasPop(svAliasPos);
        }
        break;
    }
    case VarNameUseTag:
    case DerefTag:
    case ArrIndexTag:
    case FldAccessTag:
        flowInjectAliasNode(nodep, 0);
        if (flowAliasGet(0) > 0) {
            flowHandleMove(*nodep);
        }
        break;
    case CastTag: case IsTag:
        flowLoadValue(fstate, &((CastNode *)*nodep)->exp);
        break;
    case NotLogicTag:
        flowLoadValue(fstate, &((LogicNode *)*nodep)->lexp);
        break;
    case OrLogicTag: case AndLogicTag:
    {
        LogicNode *lnode = (LogicNode*)*nodep;
        flowLoadValue(fstate, &lnode->lexp);
        flowLoadValue(fstate, &lnode->rexp);
        break;
    }

    case ULitTag:
    case FLitTag:
    case StringLitTag:
    case TypeLitTag:
    case ArrayLitTag:
    case AbsenceTag:
    case UnknownTag:
        break;
    default:
        assert(0);
    }
}

// *********************
// Variable Info stack for data flow analysis
//
// As we traverse the IR nodes, this tracks what we know about a variable in each block:
// - Has it been initialized (and used)?
// - Has it been moved and has it not been moved?
// *********************

// An entry for a local declared name, in which we preserve its flow flags
typedef struct {
    VarDclNode *node;    // The variable declaration node
    int16_t flags;       // The preserved flow flags
} VarFlowInfo;

VarFlowInfo *gVarFlowStackp = NULL;
size_t gVarFlowStackSz = 0;
size_t gVarFlowStackPos = 0;

// Add a just declared variable to the data flow stack
void flowAddVar(VarDclNode *varnode) {
    // Ensure we have room for another variable
    if (gVarFlowStackPos >= gVarFlowStackSz) {
        if (gVarFlowStackSz == 0) {
            gVarFlowStackSz = 1024;
            gVarFlowStackp = (VarFlowInfo*)memAllocBlk(gVarFlowStackSz * sizeof(VarFlowInfo));
            memset(gVarFlowStackp, 0, gVarFlowStackSz * sizeof(VarFlowInfo));
            gVarFlowStackPos = 0;
        }
        else {
            // Double table size, copying over old data
            VarFlowInfo *oldtable = gVarFlowStackp;
            int oldsize = gVarFlowStackSz;
            gVarFlowStackSz <<= 1;
            gVarFlowStackp = (VarFlowInfo*)memAllocBlk(gVarFlowStackSz * sizeof(VarFlowInfo));
            memset(gVarFlowStackp, 0, gVarFlowStackSz * sizeof(VarFlowInfo));
            memcpy(gVarFlowStackp, oldtable, oldsize * sizeof(VarFlowInfo));
        }
    }
    VarFlowInfo *stackp = &gVarFlowStackp[gVarFlowStackPos++];
    stackp->node = varnode;
    stackp->flags = 0;
}

// Start a new scope
size_t flowScopePush() {
    return gVarFlowStackPos;
}

// Create de-alias list of all own/rc reference variables (except single retexp name)
// As a simple optimization: returns 0 if retexp name was not de-aliased
int flowScopeDealias(size_t startpos, Nodes **varlist, INode *retexp) {
    int doalias = 1;
    size_t pos = gVarFlowStackPos;
    while (pos > startpos) {
        VarFlowInfo *avar = &gVarFlowStackp[--pos];
        RefNode *reftype = (RefNode*)avar->node->vtype;
        if (reftype->tag == RefTag && (reftype->region == (INode*)rcRegion || reftype->region == (INode*)soRegion)) {
            if (retexp->tag != VarNameUseTag || ((NameUseNode *)retexp)->namesym != avar->node->namesym) {
                if (*varlist == NULL)
                    *varlist = newNodes(4);
                nodesAdd(varlist, (INode*)avar->node);
            }
            else
                doalias = 0;
        }
    }
    return doalias;
}

// Back out of current scope
void flowScopePop(size_t startpos) {
    gVarFlowStackPos = startpos;
}

// *********************
// Aliasing stack for data flow analysis
//
// As we traverse the IR nodes, this tracks expression "aliasing" in each block.
// Aliasing is when we copy a value. This matters with rc and own references.
// *********************

int16_t *gFlowAliasStackp = NULL;
size_t gFlowAliasStackSz = 0;
size_t gFlowAliasStackPos = 0;
int16_t gFlowAliasFocusPos = 0;

// Ensure enough room for alias stack
void flowAliasRoom(size_t highpos) {
    if (highpos >= gFlowAliasStackSz) {
        if (gFlowAliasStackSz == 0) {
            gFlowAliasStackSz = 1024;
            gFlowAliasStackp = (int16_t*)memAllocBlk(gFlowAliasStackSz * sizeof(int16_t));
            memset(gFlowAliasStackp, 0, gFlowAliasStackSz * sizeof(int16_t));
            gFlowAliasStackPos = 0;
        }
        else {
            // Double table size, copying over old data
            int16_t *oldtable = gFlowAliasStackp;
            int oldsize = gFlowAliasStackSz;
            gFlowAliasStackSz <<= 1;
            gFlowAliasStackp = (int16_t*)memAllocBlk(gFlowAliasStackSz * sizeof(int16_t));
            memset(gFlowAliasStackp, 0, gFlowAliasStackSz * sizeof(int16_t));
            memcpy(gFlowAliasStackp, oldtable, oldsize * sizeof(int16_t));
        }
    }
}

// Initialize a function's alias stack
void flowAliasInit() {
    flowAliasRoom(3);
    gFlowAliasStackp[0] = 1;  // current frame's # of aliasing values
    gFlowAliasStackp[1] = 0;  // current frame's start aliasing count
    gFlowAliasStackp[2] = 0;  // Alias count of first value
}

// Start a new frame on alias stack
size_t flowAliasPushNew(int16_t init) {
    size_t svpos = gFlowAliasStackPos;
    int16_t oldstacksz = gFlowAliasStackp[svpos];
    flowAliasRoom(5 + oldstacksz);
    gFlowAliasStackPos += 2 + oldstacksz;
    gFlowAliasStackp[gFlowAliasStackPos] = 1;       // current frame's # of aliasing values
    gFlowAliasStackp[gFlowAliasStackPos+1] = init;  // current frame's start aliasing count
    gFlowAliasStackp[gFlowAliasStackPos + 2] = init; // First value's alias count
    return svpos;
}

// Restore previous stack
void flowAliasPop(size_t oldpos) {
    gFlowAliasStackPos = oldpos;
    gFlowAliasFocusPos = 0;
}

// Reset current frame (to one value initialized to init value)
void flowAliasReset() {
    gFlowAliasStackp[gFlowAliasStackPos] = 1;
    gFlowAliasStackp[gFlowAliasStackPos + 2] = gFlowAliasStackp[gFlowAliasStackPos + 1];
}

// Ensure frame has enough initialized alias counts for 'size' values
void flowAliasSize(int16_t size) {
    int16_t stacksz = gFlowAliasStackp[gFlowAliasStackPos];
    if (stacksz >= size)
        return;
    int16_t init = gFlowAliasStackp[gFlowAliasStackPos + 1];
    while (stacksz < size)
        gFlowAliasStackp[gFlowAliasStackPos + 2 + stacksz++] = init;
    gFlowAliasStackp[gFlowAliasStackPos] = size;
}

// Set the focus position for lval aliasing
void flowAliasFocus(int16_t pos) {
    gFlowAliasFocusPos = pos;
}

// Increment aliasing count at frame's position
void flowAliasIncr() {
    ++gFlowAliasStackp[gFlowAliasStackPos + 2 + gFlowAliasFocusPos];
}

// Get aliasing count at frame's position
int16_t flowAliasGet(size_t pos) {
    return gFlowAliasStackp[gFlowAliasStackPos + 2 + pos];
}

// Store an aliasing count at frame's position
void flowAliasPut(size_t pos, int16_t count) {
    gFlowAliasStackp[gFlowAliasStackPos + 2 + pos] = count;
}
