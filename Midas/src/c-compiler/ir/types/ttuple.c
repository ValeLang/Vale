/** Handling for type tuple nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new type tuple node
TupleNode *newTupleNode(int cnt) {
    TupleNode *tuple;
    newNode(tuple, TupleNode, TupleTag);
    tuple->elems = newNodes(cnt);
    return tuple;
}

// Clone tuple
INode *cloneTupleNode(CloneState *cstate, TupleNode *node) {
    TupleNode *newnode;
    newnode = memAllocBlk(sizeof(TupleNode));
    memcpy(newnode, node, sizeof(TupleNode));
    newnode->elems = cloneNodes(cstate, node->elems);
    return (INode *)newnode;
}

// Serialize a type tuple node
void ttuplePrint(TupleNode *tuple) {
    INode **nodesp;
    uint32_t cnt;

    for (nodesFor(tuple->elems, cnt, nodesp)) {
        inodePrintNode(*nodesp);
        if (cnt)
            inodeFprint(",");
    }
}

// Name resolution of the type tuple node
void ttupleNameRes(NameResState *pstate, TupleNode *tuple) {
    int tag = -1;
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(tuple->elems, cnt, nodesp)) {
        inodeNameRes(pstate, nodesp);
        int newtag = isTypeNode(*nodesp) ? TTupleTag : VTupleTag;
        if (tag == -1)
            tag = newtag;
        else if (tag == -2)
            ;
        else if (tag != newtag)
            tag = -2;
    }
    if (tag >= 0)
        tuple->tag = tag;
    else
        errorMsgNode((INode*)tuple, ErrorBadElems, "Elements of tuple must be all types or all values");
}

// Type check the type tuple node
void ttupleTypeCheck(TypeCheckState *pstate, TupleNode *tuple) {
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(tuple->elems, cnt, nodesp))
        itypeTypeCheck(pstate, nodesp);
}

// Compare that two tuples are equivalent
int ttupleEqual(TupleNode *totype, TupleNode *fromtype) {
    if (fromtype->tag != TTupleTag)
        return 0;
    if (totype->elems->used != fromtype->elems->used)
        return 0;

    INode **fromnodesp = &nodesGet(fromtype->elems, 0);
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(totype->elems, cnt, nodesp))
        if (!itypeIsSame(*nodesp, *fromnodesp++))
            return 0;
    return 1;
}
