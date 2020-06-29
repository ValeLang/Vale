/** Handling for cast nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create node for recasting to a new type without conversion
CastNode *newRecastNode(INode *exp, INode *type) {
    CastNode *node;
    newNode(node, CastNode, CastTag);
    node->flags |= FlagRecast;
    node->typ = node->vtype = type;
    node->exp = exp;
    return node;
}

// Create node for converting exp to a new type
CastNode *newConvCastNode(INode *exp, INode *type) {
    CastNode *node;
    newNode(node, CastNode, CastTag);
    node->typ = node->vtype = type;
    node->exp = exp;
    return node;
}

// Clone cast
INode *cloneCastNode(CloneState *cstate, CastNode *node) {
    CastNode *newnode;
    newnode = memAllocBlk(sizeof(CastNode));
    memcpy(newnode, node, sizeof(CastNode));
    newnode->exp = cloneNode(cstate, node->exp);
    newnode->typ = cloneNode(cstate, node->typ);
    return (INode *)newnode;
}

// Create a new cast node
CastNode *newIsNode(INode *exp, INode *type) {
    CastNode *node;
    newNode(node, CastNode, IsTag);
    node->typ = type;
    node->exp = exp;
    return node;
}

// Serialize cast
void castPrint(CastNode *node) {
    inodeFprint(node->tag==CastTag? "(cast, " : "(is, ");
    inodePrintNode(node->typ);
    inodeFprint(", ");
    inodePrintNode(node->exp);
    inodeFprint(")");
}

// Name resolution of cast node
void castNameRes(NameResState *pstate, CastNode *node) {
    inodeNameRes(pstate, &node->exp);
    inodeNameRes(pstate, &node->typ);
}

#define ptrsize 10000
// Give a rough idea of comparable type size for use with type checking reinterpretation casts
uint32_t castBitsize(INode *type) {
    if (type->tag == UintNbrTag || type->tag == IntNbrTag || type->tag == FloatNbrTag) {
        if (type == (INode*)usizeType)
            return ptrsize;
        return ((NbrNode *)type)->bits;
    }
    switch (type->tag) {
    case PtrTag:
    case RefTag:
        return ptrsize;
    case ArrayRefTag:
        return ptrsize << 1;
    default:
        return 0;
    }
}

// Type check cast node:
// - reinterpret cast types must be same size
// - Ensure type can be safely converted to target type
void castTypeCheck(TypeCheckState *pstate, CastNode *node) {
    if (iexpTypeCheckAny(pstate, &node->exp) == 0)
        return;
    if (itypeTypeCheck(pstate, &node->typ) == 0)
        return;

    node->vtype = node->typ;
    INode *fromtype = iexpGetTypeDcl(node->exp);
    INode *totype = itypeGetTypeDcl(node->vtype);

    // Handle reinterpret casts, which must be same size
    if (node->flags & FlagRecast) {
        if (totype->tag != StructTag) {
            uint32_t tosize = castBitsize(totype);
            if (tosize == 0 || tosize != castBitsize(fromtype))
                errorMsgNode(node->exp, ErrorInvType, "May only reinterpret value to the same sized primitive type");
        }
        return;
    }
    else {
        // Auto-generated downcasting "conversion" may in face be a bitcast
        if (fromtype->tag == RefTag && totype->tag == RefTag) {
            node->flags |= FlagRecast;
        }
    }

    // Handle conversion to bool
    if (totype == (INode*)boolType) {
        switch (fromtype->tag) {
        case UintNbrTag:
        case IntNbrTag:
        case FloatNbrTag:
        case RefTag:
        case PtrTag:
            break;
        default:
            errorMsgNode(node->exp, ErrorInvType, "Only numbers and ref/ptr may convert to Bool");
        }
        return;
    }
    switch (totype->tag) {
    case UintNbrTag:
        if (fromtype->tag == ArrayRefTag)
            return;
        // Fall-through expected here
    case IntNbrTag:
    case FloatNbrTag:
        if (fromtype->tag == UintNbrTag || fromtype->tag == IntNbrTag || fromtype->tag == FloatNbrTag)
            return;
        break;
    case RefTag:
        if (fromtype->tag == VirtRefTag)
            return;
        // Deliberate fall-through here
    case PtrTag:
        if (fromtype->tag == RefTag || fromtype->tag == PtrTag)
            return;
        break;
    case VirtRefTag:
        break;
    case StructTag:
        if (fromtype->tag == StructTag && (fromtype->flags & SameSize))
            return;
        break;
    }
    errorMsgNode(node->vtype, ErrorInvType, "Unsupported built-in type conversion");
}

// Analyze type comparison (is) node.
// This only supports whether downcasting specialization is possible
void castIsTypeCheck(TypeCheckState *pstate, CastNode *node) {
    node->vtype = (INode*)boolType;
    iexpTypeCheckAny(pstate, &node->exp);
    itypeTypeCheck(pstate, &node->typ);
    if (!isExpNode(node->exp)) {
        errorMsgNode(node->exp, ErrorInvType, "'is' requires a typed expression to the left");
        return;
    }
    if (!isTypeNode(node->typ)) {
        errorMsgNode(node->typ, ErrorInvType, "'is' requires a type to the right");
        return;
    }

    // Downcasting type check from a virt ref to a ref, or from a ref to a ref?
    INode *totype = itypeGetTypeDcl(node->typ);
    INode *fromtype = iexpGetTypeDcl(node->exp);
    if (totype->tag == RefTag && (fromtype->tag == VirtRefTag || fromtype->tag == RefTag)) {
        RefNode *from = (RefNode*)fromtype;
        RefNode *to = (RefNode*)totype;

        // Match on region & permission
        TypeCompare result = regionMatches(from->region, to->region, Coercion);
        if (result != NoMatch)
            result = permMatches(to->perm, from->perm);
        if (result == NoMatch) {
            errorMsgNode((INode*)node, ErrorInvType, "Reference region/permission won't downcast safely this way");
            return;
        }

        // Under the refs should be a structure. Be sure to is a subtype of from
        // Note that region/permission downcast covariantly, but the structure is contravariant
        StructNode *fromstr = (StructNode*)itypeGetTypeDcl(((RefNode*)fromtype)->vtexp);
        StructNode *tostr = (StructNode*)itypeGetTypeDcl(((RefNode*)totype)->vtexp);
        if (fromstr->tag == StructTag && tostr->tag == StructTag) {
            if (from->tag == VirtRefTag) {
                if (structVirtRefMatches(fromstr, tostr))
                    return;
            }
            // Downcasting tagged ref-to-trait to ref-to-struct requires tag
            if (!(fromstr->flags & HasTagField)) {
                errorMsgNode((INode*)node, ErrorInvType, "Impossible to downcast without a tag");
                return;
            }
            if (structMatches(fromstr, (INode*)tostr, Regref))
                return;
        }
    }

    // Downcasting type check from a tagged trait to a subtype struct
    else if (fromtype->tag == StructTag && totype->tag == StructTag) {
        StructNode *from = (StructNode*)fromtype;
        if (!(from->flags & HasTagField)) {
            errorMsgNode((INode*)node, ErrorInvType, "Impossible to downcast without a tag");
            return;
        }
        if (structMatches(from, totype, Coercion))
            return;
    }

    errorMsgNode((INode*)node, ErrorInvType, "Types are not compatible for this downcast specialization");
}
