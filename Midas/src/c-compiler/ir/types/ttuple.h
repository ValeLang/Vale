/** Handling for type tuple nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef ttuple_h
#define ttuple_h

// A tuple is a comma-separated list of elements, each different.
// This structure supports both type tuples and tuple literals.
typedef struct {
    ITypeNodeHdr;
    Nodes *elems;
} TupleNode;

// Create a new type tuple node
TupleNode *newTupleNode(int cnt);

// Clone tuple
INode *cloneTupleNode(CloneState *cstate, TupleNode *node);

// Serialize a type tuple node
void ttuplePrint(TupleNode *tuple);

// Name resolution of type tuple node
void ttupleNameRes(NameResState *pstate, TupleNode *node);

// Type check type tuple node
void ttupleTypeCheck(TypeCheckState *pstate, TupleNode *node);

// Compare that two tuples are equivalent
int ttupleEqual(TupleNode *totype, TupleNode *fromtype);

#endif
