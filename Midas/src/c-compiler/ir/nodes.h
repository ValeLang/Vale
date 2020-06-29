/** Node List
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef nodes_h
#define nodes_h

typedef struct INode INode;
typedef struct OwnerNode OwnerNode;
typedef struct Name Name;

#include <stdint.h>

// *** Nodes: Dynamically-sized array of Nodes ***

// Header for a variable-sized structure holding a list of Nodes
// The nodes immediately follow the header
typedef struct Nodes {
    uint32_t used;
    uint32_t avail;
} Nodes;

// Helper Functions
Nodes *newNodes(int size);

// Make a deep clone of a Nodes structure
typedef struct CloneState CloneState;
Nodes *cloneNodes(CloneState *cstate, Nodes *oldnodes);

void nodesAdd(Nodes **nodesp, INode *node);
void nodesInsert(Nodes **nodesp, INode *node, size_t index);
// Move an element at index 'to' to index 'from', shifting nodes in between
void nodesMove(Nodes *nodes, size_t to, size_t from);

// Open up space for amt new nodes beginning at pos.
// If amt is negative, delete that number of nodes
void nodesMakeSpace(Nodes **nodesp, size_t pos, int32_t amt);

#define nodesNodes(nodes) ((INode**)((nodes)+1))
#define nodesFor(nodes, cnt, nodesp) nodesp = (INode**)((nodes)+1), cnt = (nodes)->used; cnt; cnt--, nodesp++
#define nodesGet(nodes, index) ((INode**)((nodes)+1))[index]
#define nodesLast(nodes) nodesGet(nodes, nodes->used-1)

#endif
