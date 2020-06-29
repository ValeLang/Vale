/** Expression nodes that return a typed value
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"

#include <string.h>
#include <assert.h>

// Return expression node's "declared" type
INode *iexpGetTypeDcl(INode *node) {
    if (isExpNode(node) || (node)->tag == VarDclTag || (node)->tag == FnDclTag || (node)->tag == FieldDclTag) {
        return itypeGetTypeDcl(((IExpNode *)node)->vtype);
    }
    else {
        //if (isTypeNode(node)) // caller should be using itypeGetTypeDcl()
        //    return node;
        assert(0 && "Cannot obtain a type from this non-expression node");
        return unknownType;
    }
}

// Return type (or de-referenced type if ptr/ref)
INode *iexpGetDerefTypeDcl(INode *node) {
    return itypeGetDerefTypeDcl(iexpGetTypeDcl(node));
}

// Type check node we expect to be an expression. Return 0 if not.
int iexpTypeCheckAny(TypeCheckState *pstate, INode **from) {
    inodeTypeCheckAny(pstate, from);
    // From should be a typed expression node
    if (!isExpNode(*from)) {
        errorMsgNode(*from, ErrorNotTyped, "Expected a typed expression.");
        return 0;
    }
    return 1;
}

// Return whether it is okay for from expression to be coerced to to-type
TypeCompare iexpMatches(INode **from, INode *totype, SubtypeConstraint constraint) {
    INode *fromtype = iexpGetTypeDcl(*from);

    // Is totype a supertype of (or equivalent to) from's type?
    TypeCompare result = itypeMatches(totype, fromtype, constraint);
    if (result != NoMatch)
        return result;

    // Handle implicit coercion to boolean, if type supports "isTrue" method
    INode *foundnode;
    if (totype == (INode*)boolType) {
        if ((foundnode = iTypeFindFnField(fromtype, istrueName)) && foundnode->tag == FnDclTag)
            return ConvByMeth;
        else
            return NoMatch;
    }

    // Handle implicit conversion of untyped literal integer to any number type
    INode *totyp = itypeGetTypeDcl(totype);
    if ((*from)->tag == ULitTag && ((*from)->flags & FlagUnkType)
        && (totyp->tag == UintNbrTag || totyp->tag == IntNbrTag || totyp->tag == FloatNbrTag)) {
        return ConvSubtype;  // For literals, we do not care if to a supertype (for user convenience)
    }

    // Can we auto-borrow to match on the expected type?
    if (borrowAutoMatches(*from, (RefNode*)totyp)) {
        return ConvBorrow;    // Auto-borrow
    }

    return NoMatch;
}

// Coerce from-node's type to 'to' expected type, if needed
// Return 1 if type "matches", 0 otherwise
int iexpCoerce(INode **from, INode *totype) {
    // From should be a typed expression node
    assert(isExpNode(*from));
    IExpNode *fromnode = (IExpNode *)*from;

    // No need to do coercion, if no expected type
    if (totype == unknownType || totype == noCareType)
        return 1;

    // Are types equivalent, or is 'to' a subtype of fromtypedcl?
    INode *totypedcl = itypeGetTypeDcl(totype);
    switch (iexpMatches(from, totypedcl, Coercion)) {
    case NoMatch:
        return 0;
    case EqMatch:
        return 1;
    case CastSubtype:
        *from = (INode*)newRecastNode(*from, totypedcl);
        return 1;
    case ConvSubtype:
        *from = (INode*)newConvCastNode(*from, totypedcl);
        return 1;
    case ConvByMeth: 
    {
        FnCallNode *istrue = newFnCallNode(*from, 1);
        istrue->methfld = newNameUseNode(istrueName);
        int success;
        if (iexpGetTypeDcl(*from)->tag == PtrTag)
            success = fnCallLowerPtrMethod(istrue, ptrType);
        else
            success = fnCallLowerMethod(istrue);
        if (success) {
            *from = (INode*)istrue;
            return 1;
        }
        else
            return 0;
    }
    case ConvBorrow:
    {
        borrowAuto(from, totypedcl);
        return 1;
    }
    default: {
        return 0;
    }
    }
}

// Perform full type check on from-node and ensure it is an expression.
// Then coerce from-node's type to 'to' expected type, if needed
// Return 1 if type "matches", 0 otherwise
int iexpTypeCheckCoerce(TypeCheckState *pstate, INode *totype, INode **from) {
    inodeTypeCheck(pstate, from, totype);
    if (totype == noCareType)
        return 1;
    if (!isExpNode(*from)) {
        errorMsgNode(*from, ErrorNotTyped, "Expected a typed expression.");
        return 1; // pretend we match to not provoke additional errors
    }
    return iexpCoerce(from, totype);
}

// Used by 'if' and 'loop'/break to infer the type in common across all branches,
// one branch at a time. Errors on bad type match and returns Match condition.
// - expectType is the final type expected by receiver
// - maybeType is the inferred type in common
// - from is the current branch whose type is being examined
int iexpMultiInfer(INode *expectType, INode **maybeType, INode **from) {
    INode *fromType = iexpGetTypeDcl(*from);
    // If the type does not matter, we are good with anything
    if (expectType == noCareType)
        return EqMatch;

    // when we need a specific type, but don't care which one,
    // we need to find the type in common between this and previous branches
    if (expectType == unknownType) {
        if ((*maybeType) == unknownType) {
            *maybeType = fromType;  // First branch
            return EqMatch;
        }
        else {
            if (itypeIsSame(*maybeType, fromType))
                return EqMatch;
            // Try to find some supertype exists between the two types
            INode *superType = itypeFindSuper(*maybeType, fromType);
            if (superType) {
                *maybeType = superType;
                return ConvSubtype;
            }
            else {
                errorMsgNode(*from, ErrorInvType, "Branch's expression type inconsistent with other branches.");
                return NoMatch;
            }
        }
    }

    // When we have an expected type, ensure this branch matches
    TypeCompare match = iexpMatches(from, expectType, Coercion);
    if (match == NoMatch) {
        errorMsgNode(*from, ErrorInvType, "Expression type does not match expected type.");
        return NoMatch;
    }
    // Still figure out the type in common among branches
    if ((*maybeType) == unknownType) {
        *maybeType = fromType;  // First branch
        return match;
    }
    else {
        if (!itypeIsSame(*maybeType, fromType))
            *maybeType = expectType; // Set type-in-common as expected supertype
        return match;
    }
}

// Ensure it is a lval, return error and 0 if not.
int iexpIsLval(INode *lval) {
    switch (lval->tag) {
    case VarNameUseTag:
    case StringLitTag:
    case DerefTag:
        return 1;
    case ArrIndexTag:
        return iexpIsLval(((FnCallNode *)lval)->objfn);
    case FldAccessTag:
        return iexpIsLval(((FnCallNode *)lval)->objfn);
    default:
        return 0;
    }
}

// Ensure it is a lval, return error and 0 if not.
int iexpIsLvalError(INode *lval) {
    if (iexpIsLval(lval))
        return 1;
    errorMsgNode(lval, ErrorBadLval, "Expression must be lval");
    return 0;
}

// Extract lval variable, scope and overall permission from lval
INode *iexpGetLvalInfo(INode *lval, INode **lvalperm, uint16_t *scope) {
    switch (lval->tag) {

        // A variable or named function node
    case VarNameUseTag:
    {
        INode *lvalvar = ((NameUseNode *)lval)->dclnode;
        if (lvalvar->tag == VarDclTag) {
            *lvalperm = ((VarDclNode *)lvalvar)->perm;
            *scope = ((VarDclNode *)lvalvar)->scope;
        }
        else {
            *lvalperm = (INode*)opaqPerm; // Function
            *scope = 0;
        }
        return lvalvar;
    }

    case DerefTag:
    {
        INode *lvalvar = iexpGetLvalInfo(((StarNode *)lval)->vtexp, lvalperm, scope);
        RefNode *vtype = (RefNode*)iexpGetTypeDcl(((StarNode *)lval)->vtexp);
        if (vtype->tag == RefTag || vtype->tag == ArrayRefTag)
            *lvalperm = vtype->perm;
        else if (vtype->tag == PtrTag)
            *lvalperm = (INode*)mutPerm;
        return lvalvar;
    }

    // Array element (obj[2])
    case ArrIndexTag:
    {
        FnCallNode *element = (FnCallNode *)lval;
        // flowLoadValue(fstate, nodesFind(element->args, 0), 0);
        INode *lvalvar = iexpGetLvalInfo(element->objfn, lvalperm, scope);
        INode *objtype = iexpGetTypeDcl(element->objfn);
        if (objtype->tag == ArrayRefTag)
            *lvalperm = ((RefNode*)objtype)->perm;
        else if (objtype->tag == PtrTag)
            *lvalperm = (INode*)mutPerm;
        return lvalvar;
    }

    // Field access (obj.prop)
    case FldAccessTag:
    {
        FnCallNode *element = (FnCallNode *)lval;
        INode *lvalvar = iexpGetLvalInfo(element->objfn, lvalperm, scope);
        if (lvalvar == NULL)
            return NULL;
        RefNode *vtype = (RefNode*)iexpGetTypeDcl(((StarNode *)lval)->vtexp);
        if (vtype->tag == VirtRefTag)
            *lvalperm = vtype->perm;
        else {
            PermNode *methperm = (PermNode *)((VarDclNode*)((NameUseNode *)element->methfld)->dclnode)->perm;
            // Downgrade overall static permission if field is immutable
            if (methperm == immPerm)
                *lvalperm = (INode*)constPerm;
        }
        return lvalvar;
    }

    // No other node is an lval
    default:
        return NULL;
    }
}

// Are types the same (no coercion)
int iexpSameType(INode *to, INode **from) {
    return itypeIsSame(iexpGetTypeDcl(to), iexpGetTypeDcl(*from));
}

// Retrieve the permission flags for the node
uint16_t iexpGetPermFlags(INode *node) {
    switch (node->tag) {
    case VarNameUseTag:
        return iexpGetPermFlags((INode*)((NameUseNode*)node)->dclnode);
    case VarDclTag:
        return permGetFlags(((VarDclNode*)node)->perm);
    case FnDclTag:
        return permGetFlags((INode*)opaqPerm);
    case DerefTag:
    {
        RefNode *vtype = (RefNode*)iexpGetTypeDcl(((StarNode *)node)->vtexp);
        if (vtype->tag == RefTag)
            return permGetFlags(vtype->perm);
        else if (vtype->tag == PtrTag)
            return 0xFFFF;  // <-- In a trust block?
        assert(0 && "Should be ref or ptr");
    }
    case ArrIndexTag:
    {
        FnCallNode *fncall = (FnCallNode *)node;
        return iexpGetPermFlags(fncall->objfn);
    }
    case FldAccessTag:
    {
        FnCallNode *fncall = (FnCallNode *)node;
        // Field access. Permission is the conjunction of structure and field permissions.
        return iexpGetPermFlags(fncall->objfn) & iexpGetPermFlags((INode*)fncall->methfld);
    }
    default:
        return 0;
    }
}
