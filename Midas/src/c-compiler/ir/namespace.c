/** Hashed named nodes (see namespace.h)
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "nametbl.h"
#include "namespace.h"
#include "memory.h"

#include <stdio.h>
#include <assert.h>
#include <string.h>

/** Modulo operation that calculates primary table entry from name's hash.
* 'size' is always a power of 2 */
#define nameHashMod(hash, size) \
    (assert(((size)&((size)-1))==0), (size_t) ((hash) & ((size)-1)) )

// Find the NameNode slot owned by a name
#define namespaceFindSlot(slot, ns, namep) \
{ \
    size_t tbli; \
    for (tbli = nameHashMod((namep)->hash, (ns)->avail);;) { \
        slot = &(ns)->namenodes[tbli]; \
        if (slot->name == NULL || slot->name == (namep)) \
            break; \
        tbli = nameHashMod(tbli + 1, (ns)->avail); \
    } \
}

// Grow the namespace, by either creating it or doubling its size
void namespaceGrow(Namespace *namespace) {
    size_t oldTblAvail;
    NameNode *oldTable;
    size_t newTblMem;
    size_t oldslot;

    // Use avail for new table, otherwise double its size
    if (namespace->used == 0) {
        oldTblAvail = 0;
    }
    else {
        oldTblAvail = namespace->avail;
        namespace->avail <<= 1;
    }

    // Allocate and initialize new name table
    oldTable = namespace->namenodes;
    newTblMem = namespace->avail * sizeof(NameNode);
    namespace->namenodes = (NameNode*)memAllocBlk(newTblMem);
    memset(namespace->namenodes, 0, newTblMem);

    // Copy existing name slots to re-hashed positions in new table
    for (oldslot = 0; oldslot < oldTblAvail; oldslot++) {
        NameNode *oldslotp, *newslotp;
        oldslotp = &oldTable[oldslot];
        if (oldslotp->name) {
            namespaceFindSlot(newslotp, namespace, oldslotp->name);
            newslotp->name = oldslotp->name;
            newslotp->node = oldslotp->node;
        }
    }
    // memFreeBlk(oldTable);
}

// Initialize a namespace with a specific number of slots
void namespaceInit(Namespace *ns, size_t avail) {
    ns->used = 0;
    ns->avail = avail;
    ns->namenodes = NULL;
    namespaceGrow(ns);
}

// Return the node for a name (or NULL if none)
INode *namespaceFind(Namespace *ns, Name *name) {
    NameNode *slotp;
    namespaceFindSlot(slotp, ns, name);
    return slotp->node;
}

// Add or change the node a name maps to
void namespaceSet(Namespace *ns, Name *name, INode *node) {
    size_t cap = (ns->avail * 100) >> 7;
    if (ns->used > cap)
        namespaceGrow(ns);
    NameNode *slotp;
    namespaceFindSlot(slotp, ns, name);
    if (slotp->node == NULL)
        ++ns->used;
    slotp->name = name;
    slotp->node = node;
}

// Add the node a name maps to if no conflict and return NULL.
// Otherwise, return the node already there
INode *namespaceAdd(Namespace *ns, Name *name, INode *node) {
    // Don't add '_' (anonymous named) nodes, but report success
    if (name == anonName)
        return NULL;

    // If a node found there, return it
    NameNode *slotp;
    namespaceFindSlot(slotp, ns, name);
    if (slotp->node != NULL)
        return slotp->node;

    // Add node, if none found
    ++ns->used;
    slotp->name = name;
    slotp->node = node;

    // Grow if required, and return NULL
    size_t cap = (ns->avail * 100) >> 7;
    if (ns->used > cap)
        namespaceGrow(ns);
    return NULL;
}
