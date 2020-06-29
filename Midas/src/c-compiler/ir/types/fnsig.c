/** Handling for expression nodes: Literals, Variables, etc.
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"
#include <memory.h>

// Create a new function signature node
FnSigNode *newFnSigNode() {
    FnSigNode *sig;
    newNode(sig, FnSigNode, FnSigTag);
    sig->flags |= OpaqueType;
    sig->parms = newNodes(8);
    sig->rettype = unknownType;
    return sig;
}

// Clone function signature
INode *cloneFnSigNode(CloneState *cstate, FnSigNode *node) {
    FnSigNode *newnode = memAllocBlk(sizeof(FnSigNode));
    memcpy(newnode, node, sizeof(FnSigNode));
    newnode->parms = cloneNodes(cstate, node->parms);
    newnode->rettype = cloneNode(cstate, node->rettype);
    INode **origp = &nodesGet(node->parms, 0);
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(newnode->parms, cnt, nodesp)) {
        cloneDclSetMap(*origp++, *nodesp);
    }
    return (INode *)newnode;
}

// Serialize a function signature node
void fnSigPrint(FnSigNode *sig) {
    INode **nodesp;
    uint32_t cnt;
    inodeFprint("fn(");
    for (nodesFor(sig->parms, cnt, nodesp)) {
        inodePrintNode(*nodesp);
        if (cnt > 1)
            inodeFprint(", ");
    }
    inodeFprint(") ");
    inodePrintNode(sig->rettype);
}

// Name resolution of the function signature
void fnSigNameRes(NameResState *pstate, FnSigNode *sig) {
    uint16_t svscope = pstate->scope;
    pstate->scope = 0; // Make scope 0 to avoid parameter names being hooked.
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(sig->parms, cnt, nodesp))
        inodeNameRes(pstate, nodesp);
    inodeNameRes(pstate, &sig->rettype);
    pstate->scope = svscope;
}

// Type check the function signature
void fnSigTypeCheck(TypeCheckState *pstate, FnSigNode *sig) {
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(sig->parms, cnt, nodesp))
        inodeTypeCheckAny(pstate, nodesp);
    itypeTypeCheck(pstate, &sig->rettype);
}

// Compare two function signatures to see if they are equivalent
int fnSigEqual(FnSigNode *node1, FnSigNode *node2) {
    INode **nodes1p, **nodes2p;
    uint32_t cnt;

    // Return types and number of parameters must match
    if (!itypeIsSame(node1->rettype, node2->rettype)
        || node1->parms->used != node2->parms->used)
        return 0;

    // Every parameter's type must also match
    nodes2p = &nodesGet(node2->parms, 0);
    for (nodesFor(node1->parms, cnt, nodes1p)) {
        if (!itypeIsSame(*nodes1p, *nodes2p))
            return 0;
        nodes2p++;
    }
    return 1;
}

// For virtual reference structural matches on two methods,
// compare two function signatures to see if they are equivalent,
// ignoring the first 'self' parameter (we know their types differ)
int fnSigVrefEqual(FnSigNode *node1, FnSigNode *node2) {
    INode **nodes1p, **nodes2p;
    uint32_t cnt;

    // Return types and number of parameters must match
    if (!itypeIsSame(node1->rettype, node2->rettype)
        || node1->parms->used != node2->parms->used)
        return 0;

    // Every parameter's type must also match
    nodes2p = &nodesGet(node2->parms, 0);
    for (nodesFor(node1->parms, cnt, nodes1p)) {
        if (cnt < node1->parms->used && !itypeIsSame(*nodes1p, *nodes2p))
            return 0;
        nodes2p++;
    }
    return 1;
}

// Return TypeCompare indicating whether from type matches the function signature
TypeCompare fnSigMatches(FnSigNode *to, FnSigNode *from, SubtypeConstraint constraint) {
    TypeCompare result = EqMatch;

    // Number of parameters must match
    if (to->parms->used != from->parms->used)
        return NoMatch;

    // Every parameter's type must also match
    INode **tonodesp, **fromnodesp;
    uint32_t cnt;
    fromnodesp = &nodesGet(from->parms, 0);
    for (nodesFor(to->parms, cnt, tonodesp)) {
        // Match for parameters is contravariant, switching order of to/from
        switch (itypeMatches(*fromnodesp, *tonodesp, constraint)) {
        case NoMatch:
            return NoMatch;
        case CastSubtype:
            result = result == ConvSubtype ? ConvSubtype : CastSubtype;
            break;
        case ConvSubtype:
            result = ConvSubtype;
            break;
        default:
            break;
        }
        fromnodesp++;
    }

    // Return type is covariant
    switch (itypeMatches(to->rettype, from->rettype, constraint)) {
    case NoMatch:
        return NoMatch;
    case CastSubtype:
        result = result == ConvSubtype ? ConvSubtype : CastSubtype;
        break;
    case ConvSubtype:
        result = ConvSubtype;
        break;
    default:
        break;
    }
    return result;
}

// Return true if type of from-exp matches totype
int fnSigCoerce(FnSigNode *totype, INode **fromexp) {
    return itypeMatches((INode*)totype, iexpGetTypeDcl(*fromexp), Coercion) == EqMatch;
}

// Will the function call (caller) be able to call the 'to' function
// Return 0 if not. 1 if perfect match. 2+ for every argument match requiring coercion
int fnSigMatchesCall(FnSigNode *to, Nodes *args) {
    int matchsum = 1;

    // Too many arguments is not a match
    if (args->used > to->parms->used)
        return 0;

    // Every parameter's type must also match
    INode **tonodesp;
    INode **callnodesp;
    uint32_t cnt;
    tonodesp = &nodesGet(to->parms, 0);
    for (nodesFor(args, cnt, callnodesp)) {
        switch (iexpMatches(callnodesp, ((IExpNode *)*tonodesp)->vtype, Coercion)) {
        case NoMatch: 
            return 0;
        case EqMatch: 
            break;
        default:
            ++matchsum;
        }
        tonodesp++;
    }
    // Match fails if not enough arguments & method has no default values on parms
    if (args->used != to->parms->used 
        && ((VarDclNode *)tonodesp)->value==NULL)
        return 0;

    // It is a match; return how perfect a match it is
    return matchsum;
}

// Will the method call (caller) be able to call the 'to' function
// Return 0 if not. 1 if perfect match. 2+ for every argument match requiring coercion
int fnSigMatchMethCall(FnSigNode *to, INode **self, Nodes *args) {
    uint32_t argcnt = args ? args->used + 1 : 1;

    // Too many arguments is not a match
    if (argcnt > to->parms->used)
        return 0;

    // Compare self's type to expected self parameter type
    INode **tonodesp = &nodesGet(to->parms, 0);
    INode *selftype = iexpGetTypeDcl(*self);
    int matchsum = 1;
    if (selftype->tag != VirtRefTag) {
        switch (iexpMatches(self, iexpGetTypeDcl(*tonodesp), Coercion)) {
        case NoMatch:
            return 0;
        case EqMatch: 
            break;
        default:
            ++matchsum; 
        }
    }
    else {
        // For a virtual reference, the first argument need not be type-checked
        // other than ensuring the found method expects a reference
        if (((IExpNode*)*tonodesp)->vtype->tag != RefTag)
            return 0;
    }
    if (argcnt == 1)
        return matchsum;
    ++tonodesp;

    // Every specified argument must match corresponding parameter
    INode **callnodesp;
    uint32_t cnt;
    for (nodesFor(args, cnt, callnodesp)) {
        switch (iexpMatches(callnodesp, ((IExpNode *)*tonodesp)->vtype, Coercion)) {
        case NoMatch:
            return 0;
        case EqMatch: 
            break;
        default:
            ++matchsum;
        }
        tonodesp++;
    }
    // Match fails if not enough arguments & method has no default values on parms
    if (argcnt != to->parms->used
        && ((VarDclNode *)tonodesp)->value == NULL)
        return 0;

    // It is a match; return how perfect a match it is
    return matchsum;
}
