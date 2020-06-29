/** Shared logic for namespace-based types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"

#include <stdio.h>
#include <string.h>
#include <stdarg.h>

// Initialize methnodes metadata
void nodelistInit(NodeList *mnodes, uint32_t size) {
    mnodes->avail = size;
    mnodes->used = 0;
    mnodes->nodes = (INode **)memAllocBlk(size * sizeof(INode **));
}

// Double size, if full
void nodelistGrow(NodeList *mnodes) {
    INode **oldnodes;
    oldnodes = mnodes->nodes;
    mnodes->avail <<= 1;
    mnodes->nodes = (INode **)memAllocBlk(mnodes->avail * sizeof(INode **));
    memcpy(mnodes->nodes, oldnodes, mnodes->used * sizeof(INode **));
}

// Add an INode to the end of a NodeList, growing it if full (changing its memory location)
void nodelistAdd(NodeList *mnodes, INode *node) {
    if (mnodes->used >= mnodes->avail)
        nodelistGrow(mnodes);
    mnodes->nodes[mnodes->used++] = node;
}

// Insert a node into a nodelist at pos
void nodelistInsert(NodeList *mnodes, uint32_t pos, INode *node) {
    while (mnodes->used + 1 >= mnodes->avail)
        nodelistGrow(mnodes);
    memmove(&mnodes->nodes[pos + 1], &mnodes->nodes[pos], (mnodes->used - pos) * sizeof(INode *));
    mnodes->nodes[pos] = node;
    mnodes->used += 1;
}

// Insert some list into another list, beginning at index
void nodelistInsertList(NodeList *mnodes, NodeList *fromnodes, uint32_t index) {
    while (mnodes->used + fromnodes->used >= mnodes->avail)
        nodelistGrow(mnodes);
    uint32_t amt = fromnodes->used;
    memmove(&mnodes->nodes[amt + index], &mnodes->nodes[index], (mnodes->used-index) * sizeof(INode *));
    memcpy(&mnodes->nodes[index], fromnodes->nodes, amt * sizeof(INode **));
    mnodes->used += fromnodes->used;
}

// Open up space for amt new nodes beginning at pos.
// If amt is negative, delete that number of nodes
void nodelistMakeSpace(NodeList *nodes, size_t pos, int32_t amt) {
    if (amt == 0)
        return;
    // If full, double its size as much as needed
    if (nodes->used + amt >= nodes->avail) {
        uint32_t newsize = nodes->avail << 1;
        while (nodes->used + amt >= newsize)
            newsize <<= 1;
        INode **oldnodes = nodes->nodes;
        nodes->nodes = memAllocBlk(newsize);
        memcpy(nodes->nodes, oldnodes, (nodes->used) * sizeof(INode*));
    }

    // Move nodes after pos up or down accordingly
    INode **posp = &nodes->nodes[pos];
    if (amt > 0)
        memmove(posp + amt, posp, (nodes->used - pos) * sizeof(INode*));
    else
        memmove(posp, posp - amt, (nodes->used - pos + amt) * sizeof(INode*));
    nodes->used += amt;
}
