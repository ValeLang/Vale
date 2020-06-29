/** Lifetime Annotations (Variables)
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef lifetime_h
#define lifetime_h

typedef struct NameUseNode NameUseNode;

// A lifetime is effectively two numbers from 0-255 that describe a partial order.
// - Group. Two lifetimes with different groups (>1) cannot be subtypes of each other
//     A new group is assigned for each implicit/explicit lifetime annotation on a generic
//     'static and local scopes have a group of 0.
// - Scope. A higher number is a subtype of a lower number. 0='static, 16+=local scopes
typedef uint16_t Lifetime;

// Lifetime type info
typedef struct LifetimeNode {
    INsTypeNodeHdr;
    Lifetime life;
} LifetimeNode;

// Create a new lifetime declaration node
LifetimeNode *newLifetimeDclNode(Name *namesym, Lifetime life);

// Create a copy of lifetime dcl
INode *cloneLifetimeDclNode(CloneState *cstate, LifetimeNode *node);

// Create a new lifetime use node
INode *newLifetimeUseNode(LifetimeNode *lifedcl);

// Serialize a lifetime node
void lifePrint(LifetimeNode *node);

// Are the lifetimes the same?
int lifeIsSame(INode *node1, INode *node2);

// Will 'from' lifetime coerce to the target?
int lifeMatches(INode *to, INode *from);

#endif
