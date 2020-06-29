/** Handling for function signature
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef fnsig_h
#define fnsig_h

typedef struct FnCallNode FnCallNode;

// Function signature is a type that defines the parameters and return type for a function.
// A function signature is never named (although a ptr/ref to a fnsig may be named).
// The parameter declaration list represents a namespace of local variables.
typedef struct FnSigNode {
    INodeHdr;
    Nodes *parms;            // Declared parameter nodes w/ defaults (VarDclTag)
    INode *rettype;        // void, a single type or a type tuple
} FnSigNode;

FnSigNode *newFnSigNode();

// Clone function signature
INode *cloneFnSigNode(CloneState *cstate, FnSigNode *node);

void fnSigPrint(FnSigNode *node);
// Name resolution of the function signature
void fnSigNameRes(NameResState *pstate, FnSigNode *sig);
void fnSigTypeCheck(TypeCheckState *pstate, FnSigNode *name);
int fnSigEqual(FnSigNode *node1, FnSigNode *node2);

// For virtual reference structural matches on two methods,
// compare two function signatures to see if they are equivalent,
// ignoring the first 'self' parameter (we know their types differ)
int fnSigVrefEqual(FnSigNode *node1, FnSigNode *node2);

// Return TypeCompare indicating whether from type matches the function signature
TypeCompare fnSigMatches(FnSigNode *to, FnSigNode *from, SubtypeConstraint constraint);

// Return true if type of from-exp matches totype
int fnSigCoerce(FnSigNode *totype, INode **fromexp);

// Will the function call (caller) be able to call the 'to' function
// Return 0 if not. 1 if perfect match. 2+ for imperfect matches
int fnSigMatchesCall(FnSigNode *to, Nodes *args);

// Will the method call (caller) be able to call the 'to' function
// Return 0 if not. 1 if perfect match. 2+ for imperfect matches
// if skipfirst is on, don't type check first argument for a vref
int fnSigMatchMethCall(FnSigNode *to, INode **self, Nodes *args);

#endif
