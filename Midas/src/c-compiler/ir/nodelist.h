/** Shared logic for namespace-based types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef nodelist_h
#define nodelist_h

// Variable-sized structure holding an ordered list of Nodes
typedef struct NodeList {
    uint32_t used;
    uint32_t avail;
    INode **nodes;
} NodeList;

// Initialize methnodes metadata in a method type node
void nodelistInit(NodeList *mnodes, uint32_t size);

// Iterate through the set of NodeList 
#define nodelistFor(mnodes, cnt, nodesp) nodesp = (mnodes)->nodes, cnt = (mnodes)->used; cnt; cnt--, nodesp++

// Point to the indexth node in an NodeList
#define nodelistGet(mnodes, index) (mnodes)->nodes[index]

// Point to the last node in an NodeList
#define nodelistLast(mnodes) nodelistGet(mnodes, mnodes->used-1)

// Add an INode to the end of a NodeList, growing it if full (changing its memory location)
void nodelistAdd(NodeList *mnodes, INode *node);

// Insert a node into a nodelist at pos
void nodelistInsert(NodeList *mnodes, uint32_t pos, INode *node);

// Insert some list into another list, beginning at index
void nodelistInsertList(NodeList *mnodes, NodeList *fromnodes, uint32_t index);

// Open up space for amt new nodes beginning at pos.
// If amt is negative, delete that number of nodes
void nodelistMakeSpace(NodeList *nodes, size_t pos, int32_t amt);


#endif
