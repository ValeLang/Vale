/** Generic Type node handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"

#include <string.h>
#include <assert.h>

// Return node's type's declaration node
// (Note: only use after it has been type-checked)
INode *itypeGetTypeDcl(INode *type) {
    while (1) {
        switch (type->tag) {
        case TypeNameUseTag:
            type = ((NameUseNode *)type)->dclnode;
            break;
        case TypedefTag:
            type = ((TypedefNode *)type)->typeval;
        default:
            return type;
        }
    }
}

// Return node's type's declaration node (or vtexp if a ref or ptr)
INode *itypeGetDerefTypeDcl(INode *node) {
    INode *typnode = itypeGetTypeDcl(node);
    if (typnode->tag == RefTag || typnode->tag == VirtRefTag)
        return itypeGetTypeDcl(((RefNode*)node)->vtexp);
    else if (node->tag == PtrTag)
        return itypeGetTypeDcl(((StarNode*)node)->vtexp);
    return typnode;
}

// Look for named field/method in type
INode *iTypeFindFnField(INode *type, Name *name) {
    switch (type->tag) {
    case StructTag:
    case UintNbrTag:
    case IntNbrTag:
    case FloatNbrTag:
        return iNsTypeFindFnField((INsTypeNode*)type, name);
    case PtrTag:
        return iNsTypeFindFnField(ptrType, name);
    default:
        return NULL;
    }
}

// Type check node, expecting it to be a type. Give error and return 0, if not.
int itypeTypeCheck(TypeCheckState *pstate, INode **node) {
    inodeTypeCheckAny(pstate, node);
    if (!isTypeNode(*node)) {
        errorMsgNode(*node, ErrorNotTyped, "Expected a type.");
        return 0;
    }
    return 1;
}

// Return 1 if nominally (or structurally) identical, 0 otherwise
// Nodes must both be types, but may be name use or declare nodes
int itypeIsSame(INode *node1, INode *node2) {

    node1 = itypeGetTypeDcl(node1);
    node2 = itypeGetTypeDcl(node2);

    // If they are the same type name, types match
    if (node1 == node2)
        return 1;
    if (node1->tag != node2->tag)
        return 0;

    // For non-named types, equality is determined structurally
    // because they specify the same typed parts
    switch (node1->tag) {
    case FnSigTag:
        return fnSigEqual((FnSigNode*)node1, (FnSigNode*)node2);
    case RefTag: 
        return refEqual((RefNode*)node1, (RefNode*)node2);
    case ArrayRefTag:
        return arrayRefEqual((RefNode*)node1, (RefNode*)node2);
    case VirtRefTag:
        return refEqual((RefNode*)node1, (RefNode*)node2);
    case PtrTag:
        return ptrEqual((StarNode*)node1, (StarNode*)node2);
    case ArrayTag:
        return arrayEqual((ArrayNode*)node1, (ArrayNode*)node2);
    case TTupleTag:
        return ttupleEqual((TupleNode*)node1, (TupleNode*)node2);
    case VoidTag:
        return 1;
    default:
        return 0;
    }
}

// Is totype equivalent or a subtype of fromtype
TypeCompare itypeMatches(INode *totype, INode *fromtype, SubtypeConstraint constraint) {
    fromtype = itypeGetTypeDcl(fromtype);
    totype = itypeGetTypeDcl(totype);

    // If they are the same value type info, types match
    if (totype == fromtype)
        return EqMatch;

    // Type-specific matching logic
    switch (totype->tag) {

    case UintNbrTag:
    case IntNbrTag:
    case FloatNbrTag:
        return nbrMatches(totype, fromtype, constraint);

    case StructTag:
        return structMatches((StructNode*)totype, fromtype, constraint);

    case TTupleTag:
        if (fromtype->tag == TTupleTag)
            return itypeIsSame(totype, fromtype) ? EqMatch : NoMatch;
        return NoMatch;

    case ArrayTag:
        if (fromtype->tag == ArrayTag)
            return arrayMatches((ArrayNode*)totype, (ArrayNode*)fromtype, constraint);
        return NoMatch;

    case FnSigTag:
        if (fromtype->tag == FnSigTag)
            return fnSigMatches((FnSigNode*)totype, (FnSigNode*)fromtype, constraint);
        return NoMatch;

    case RefTag:
        if (fromtype->tag == RefTag)
            return refMatches((RefNode*)totype, (RefNode*)fromtype, constraint);
        return NoMatch;

    case VirtRefTag:
        if (fromtype->tag == VirtRefTag)
            return refvirtMatches((RefNode*)totype, (RefNode*)fromtype, constraint);
        else if (fromtype->tag == RefTag)
            return refvirtMatchesRef((RefNode*)totype, (RefNode*)fromtype, constraint);
        return NoMatch;

    case ArrayRefTag:
        if (fromtype->tag == ArrayRefTag)
            return arrayRefMatches((RefNode*)totype, (RefNode*)fromtype, constraint);
        else if (fromtype->tag == RefTag)
            return arrayRefMatchesRef((RefNode*)totype, (RefNode*)fromtype, constraint);
        return NoMatch;

    case PtrTag:
        if (fromtype->tag == RefTag || fromtype->tag == ArrayRefTag)
            return itypeIsSame(((RefNode*)fromtype)->vtexp, ((StarNode*)totype)->vtexp) ? ConvSubtype : NoMatch;
        if (fromtype->tag == PtrTag)
            return ptrMatches((StarNode*)totype, (StarNode*)fromtype, constraint);
        return NoMatch;

    case VoidTag:
        return fromtype->tag == VoidTag ? EqMatch : NoMatch;

    default:
        return itypeIsSame(totype, fromtype) ? EqMatch : NoMatch;
    }
}

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *itypeFindSuper(INode *type1, INode *type2) {
    INode *typ1 = itypeGetTypeDcl(type1);
    INode *typ2 = itypeGetTypeDcl(type2);

    if (typ1->tag != typ2->tag)
        return NULL;
    if (itypeIsSame(typ1, typ2))
        return type1;
    switch (typ1->tag) {
    case UintNbrTag:
    case IntNbrTag:
    case FloatNbrTag:
        return nbrFindSuper(type1, type2);

    case StructTag:
        return structFindSuper(type1, type2);

    case RefTag:
    case VirtRefTag:
        return refFindSuper(type1, type2);

    default:
        return NULL;
    }
}

// Add type mangle info to buffer
char *itypeMangle(char *bufp, INode *vtype) {
    switch (vtype->tag) {
    case NameUseTag:
    case TypeNameUseTag:
    {
        strcpy(bufp, &((INsTypeNode*)((NameUseNode *)vtype)->dclnode)->namesym->namestr);
        break;
    }
    case RefTag:
    case ArrayRefTag:
    case VirtRefTag:
    {
        RefNode *reftype = (RefNode *)vtype;
        *bufp++ = vtype->tag==VirtRefTag? '<' : ArrayRefTag? '+' : '&';
        if (permIsSame(reftype->perm, (INode*)constPerm)) {
            bufp = itypeMangle(bufp, reftype->perm);
            *bufp++ = ' ';
        }
        bufp = itypeMangle(bufp, reftype->vtexp);
        break;
    }
    case PtrTag:
    {
        StarNode *vtexp = (StarNode *)vtype;
        *bufp++ = '*';
        bufp = itypeMangle(bufp, vtexp->vtexp);
        break;
    }
    case UintNbrTag:
        *bufp++ = 'u'; break;
    case IntNbrTag:
        *bufp++ = 'i'; break;
    case FloatNbrTag:
        *bufp++ = 'f'; break;

    default:
        assert(0 && "unknown type for parameter type mangling");
    }
    return bufp + strlen(bufp);
}

// Return true if type has a concrete and instantiable. 
// Opaque (field-less) structs, traits, functions, void will be false.
int itypeIsConcrete(INode *type) {
    if (!isTypeNode(type))
        return 0;
    INode *dcltype = itypeGetTypeDcl(type);
    return !(dcltype->flags & OpaqueType);
}
