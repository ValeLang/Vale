/** Node list
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"
#include "nametbl.h"
#include "../parser/lexer.h"

#include <stdio.h>
#include <string.h>
#include <stdarg.h>

// Allocate and initialize a new nodes block
Nodes *newNodes(int size) {
    Nodes *nodes;
    nodes = (Nodes*) memAllocBlk(sizeof(Nodes) + size*sizeof(INode*));
    nodes->avail = size;
    nodes->used = 0;
    return nodes;
}

// Make a deep clone of a Nodes structure
Nodes *cloneNodes(CloneState *cstate, Nodes *oldnodes) {
    Nodes *newnodes = newNodes(oldnodes->avail);
    newnodes->used = oldnodes->used;
    INode **nodesp;
    uint32_t cnt;
    INode **newnodesp = &nodesGet(newnodes, 0);
    for (nodesFor(oldnodes, cnt, nodesp))
        *newnodesp++ = cloneNode(cstate, *nodesp);
    return newnodes;
}

// Add an INode to the end of a Nodes, growing it if full (changing its memory location)
// This assumes a nodes can only have a single parent, whose address we point at
void nodesAdd(Nodes **nodesp, INode *node) {
    Nodes *nodes = *nodesp;
    // If full, double its size
    if (nodes->used >= nodes->avail) {
        Nodes *oldnodes;
        INode **op, **np;
        oldnodes = nodes;
        nodes = newNodes(oldnodes->avail << 1);
        op = (INode **)(oldnodes+1);
        np = (INode **)(nodes+1);
        memcpy(np, op, (nodes->used = oldnodes->used) * sizeof(INode*));
        *nodesp = nodes;
    }
    *((INode**)(nodes+1)+nodes->used) = node;
    nodes->used++;
}

// Insert an INode within a list of nodes, growing it if full (changing its memory location)
// This assumes a Nodes can only have a single parent, whose address we point at
void nodesInsert(Nodes **nodesp, INode *node, size_t index) {
    Nodes *nodes = *nodesp;
    INode **op, **np;
    // If full, double its size
    if (nodes->used >= nodes->avail) {
        Nodes *oldnodes;
        oldnodes = nodes;
        nodes = newNodes(oldnodes->avail << 1);
        op = (INode **)(oldnodes + 1);
        np = (INode **)(nodes + 1);
        memcpy(np, op, (nodes->used = oldnodes->used) * sizeof(INode*));
        *nodesp = nodes;
    }
    op = (INode **)(nodes + 1) + index;
    np = op + 1;
    size_t tomove = nodes->used - index;
    switch (tomove) {
    case 1:
        *np = *op;
        break;
    default:
        memmove(np, op, tomove * sizeof(INode*));
    }
    *op = node;
    nodes->used++;
}

// Move an element at index 'to' to index 'from', shifting nodes in between
void nodesMove(Nodes *nodes, size_t to, size_t from) {
    INode *movenode = nodesGet(nodes, from);
    if (from > to) {
        INode **moveto = &nodesGet(nodes, to);
        memmove(moveto + 1, moveto, (from - to) * sizeof(INode*));
    }
    else if (to > from) {
        INode **moveto = &nodesGet(nodes, from);
        memmove(moveto, moveto + 1, (to - from) * sizeof(INode*));
    }
    *(&nodesGet(nodes, to)) = movenode;
}

// Open up space for amt new nodes beginning at pos.
// If amt is negative, delete that number of nodes
void nodesMakeSpace(Nodes **nodesp, size_t pos, int32_t amt) {
    if (amt == 0)
        return;
    Nodes *nodes = *nodesp;
    INode **op, **np;
    // If full, double its size as needed
    if (nodes->used + amt >= nodes->avail) {
        uint32_t newsize = nodes->avail << 1;
        while (nodes->used + amt >= newsize)
            newsize <<= 1;
        Nodes *oldnodes;
        oldnodes = nodes;
        nodes = newNodes(newsize);
        op = (INode **)(oldnodes + 1);
        np = (INode **)(nodes + 1);
        memcpy(np, op, (nodes->used = oldnodes->used) * sizeof(INode*));
        *nodesp = nodes;
    }

    // Move nodes after pos up or down accordingly
    INode **posp = &nodesGet(*nodesp, pos);
    if (amt > 0)
        memmove(posp + amt, posp, (nodes->used - pos) * sizeof(INode*));
    else
        memmove(posp, posp - amt, (nodes->used - pos + amt) * sizeof(INode*));
    nodes->used += amt;
}
