/** Handling for reference types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef reference_h
#define reference_h

// Reference node: used for reference type, allocation or borrow node
typedef struct {
    ITypeNodeHdr;
    INode *vtexp;     // Value/type expression
    INode *perm;      // Permission
    INode *region;    // Region
    uint16_t scope;   // Lifetime
} RefNode;

// Create a new reference type whose info will be filled in afterwards
RefNode *newRefNode(uint16_t tag);

// Clone reference
INode *cloneRefNode(CloneState *cstate, RefNode *node);

// Create a new reference type whose info is known and analyzeable
RefNode *newRefNodeFull(uint16_t tag, INode *lexnode, INode *region, INode *perm, INode *vtype);

// Set the inferred value type of a reference
void refSetPermVtype(RefNode *refnode, INode *perm, INode *vtype);

// Create a new ArrayDerefNode from an ArrayRefNode
RefNode *newArrayDerefNodeFrom(RefNode *refnode);

// Serialize a reference type
void refPrint(RefNode *node);

// Name resolution of a reference node
void refNameRes(NameResState *pstate, RefNode *node);

// Type check a reference node
void refTypeCheck(TypeCheckState *pstate, RefNode *name);

// Type check a virtual reference node
void refvirtTypeCheck(TypeCheckState *pstate, RefNode *node);

// Compare two reference signatures to see if they are equivalent
int refEqual(RefNode *node1, RefNode *node2);

// Will from region coerce to a to region
TypeCompare regionMatches(INode *to, INode *from, SubtypeConstraint constraint);

// Will from reference coerce to a to reference (we know they are not the same)
TypeCompare refMatches(RefNode *to, RefNode *from, SubtypeConstraint constraint);

// Will from reference coerce to a virtual reference (we know they are not the same)
TypeCompare refvirtMatchesRef(RefNode *to, RefNode *from, SubtypeConstraint constraint);

// Will from reference coerce to a virtual reference (we know they are not the same)
TypeCompare refvirtMatches(RefNode *to, RefNode *from, SubtypeConstraint constraint);

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *refFindSuper(INode *type1, INode *type2);

#endif
