/** Hashed named nodes
 *
 * The module node is a namespace, as it contains an unordered collection
 * of potentially many named nodes: functions, variables, types, sub-modules, etc.
 *
 * These functions support managing a module's named nodes as a hash table
 * for high-performing lookup access to a specific name.
 *
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef namespace_h
#define namespace_h

// A namespace entry
typedef struct NameNode {
    Name *name;
    INode *node;
} NameNode;

// Namespace metadata
typedef struct Namespace {
    size_t avail;
    size_t used;
    NameNode *namenodes;
} Namespace;

// Initialize a module's namespace with a specific number of slots
void namespaceInit(Namespace *ns, size_t avail);

// Return the module's node for a name (or NULL if none)
INode *namespaceFind(Namespace *ns, Name *name);

// Add or change the node a modue's name maps to
void namespaceSet(Namespace *ns, Name *name, INode *node);

// Add the node a name maps to if no conflict and return NULL.
// Otherwise, return the node already there
INode *namespaceAdd(Namespace *ns, Name *name, INode *node);

// Iterate through a module's namespace
#define namespaceFor(ns) for (size_t __i = 0; __i < (ns)->avail; ++__i)

#endif
