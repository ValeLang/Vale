/** Handling for array types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new array type whose info will be filled in afterwards
ArrayNode *newArrayNode() {
    ArrayNode *anode;
    newNode(anode, ArrayNode, ArrayTag);
    anode->llvmtype = NULL;
    anode->dimens = newNodes(1);
    anode->elems = newNodes(1);
    return anode;
}

// Create a new array type of a specified size and element type
// This only works for a single dimension array type
ArrayNode *newArrayNodeTyped(INode *lexnode, size_t size, INode *elemtype) {
    ArrayNode *anode = newArrayNode();
    inodeLexCopy((INode*)anode, lexnode);
    nodesAdd(&anode->dimens, (INode*)newULitNode(size, (INode*)u64Type));
    nodesAdd(&anode->elems, elemtype);
    return anode;
}

// Return the element type of the array type
INode *arrayElemType(INode *array) {
    return nodesGet(((ArrayNode *)array)->elems, 0);
}

// Return the size of the first dimension (assuming 1-dimensional array)
uint64_t arrayDim1(INode *array) {
    ULitNode *dim1 = (ULitNode *)nodesGet(((ArrayNode *)array)->dimens, 0);
    return dim1->uintlit;
}

// Clone array
INode *cloneArrayNode(CloneState *cstate, ArrayNode *node) {
    ArrayNode *newnode = memAllocBlk(sizeof(ArrayNode));
    memcpy(newnode, node, sizeof(ArrayNode));
    newnode->elems = cloneNodes(cstate, node->elems);
    return (INode *)newnode;
}

// Serialize an array type
void arrayPrint(ArrayNode *node) {
    INode **nodesp;
    uint32_t cnt;
    inodeFprint("[");
    if (node->dimens->used > 0) {
        for (nodesFor(node->dimens, cnt, nodesp)) {
            inodePrintNode(*nodesp);
            if (cnt > 1)
                inodeFprint(", ");
        }
        inodeFprint("; ");
    }
    for (nodesFor(node->elems, cnt, nodesp)) {
        inodePrintNode(*nodesp);
        if (cnt > 1)
            inodeFprint(", ");
    }
    inodeFprint("]");
}

// Name resolution of an array type/literal
void arrayNameRes(NameResState *pstate, ArrayNode *node) {
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(node->elems, cnt, nodesp))
        inodeNameRes(pstate, nodesp);
    if (node->elems->used > 0 && !isTypeNode(nodesGet(node->elems, 0)))
        node->tag = ArrayLitTag; // We have an array literal, not array type
}

// Type check an array type
void arrayTypeCheck(TypeCheckState *pstate, ArrayNode *node) {

    // Check out dimensions: must be literal numbers
    if (node->dimens->used > 0) {
        INode **nodesp;
        uint32_t cnt;
        for (nodesFor(node->dimens, cnt, nodesp)) {
            if ((*nodesp)->tag != ULitTag)
                errorMsgNode(*nodesp, ErrorBadArray, "Integer literal must be used for array dimensions");
        }
    }
    else
        errorMsgNode((INode*)node, ErrorBadArray, "Array type must have numeric array dimensions");

    // Check out element type
    if (node->elems->used != 1) {
        errorMsgNode((INode*)node, ErrorBadArray, "Exactly one element type must be specified");
    }
    INode **elemtypep = &nodesGet(node->elems, 0);
    if (itypeTypeCheck(pstate, elemtypep) == 0) {
        errorMsgNode((INode*)node, ErrorBadArray, "Element type must be a known type");
        return;
    }
    if (!itypeIsConcrete(*elemtypep)) {
        errorMsgNode((INode*)node, ErrorInvType, "Element's type must be concrete and instantiable.");
    }
    // If the element's type if ThreadBound or Move, so is the array's type
    ITypeNode *elemtype = (ITypeNode*)itypeGetTypeDcl(*elemtypep);
    node->flags |= elemtype->flags & (ThreadBound | MoveType);
}

// Compare two array types to see if they are equivalent
int arrayEqual(ArrayNode *node1, ArrayNode *node2) {
    // Are element type and number of dimensions equivalent?
    if (!itypeIsSame(arrayElemType((INode*)node1), arrayElemType((INode*)node2))
        || node1->dimens->used != node2->dimens->used)
        return 0;

    // Now compare all the dimensions
    INode **nodes1p;
    uint32_t cnt;
    INode **nodes2p = &nodesGet(node2->dimens, 0);
    for (nodesFor(node1->dimens, cnt, nodes1p)) {
        ULitNode *dim1 = (ULitNode *)*nodes1p;
        ULitNode *dim2 = (ULitNode *)*nodes2p++;
        if (dim1->uintlit != dim2->uintlit)
            return 0;
    }
    return 1;
}

// Is from-type a subtype of to-struct (we know they are not the same)
TypeCompare arrayMatches(ArrayNode *to, ArrayNode *from, SubtypeConstraint constraint) {
    // Must have same dimensions
    if (to->dimens->used != from->dimens->used)
        return NoMatch;
    INode **nodes1p;
    uint32_t cnt;
    INode **nodes2p = &nodesGet(to->dimens, 0);
    for (nodesFor(from->dimens, cnt, nodes1p)) {
        ULitNode *dim1 = (ULitNode *)*nodes1p;
        ULitNode *dim2 = (ULitNode *)*nodes2p++;
        if (dim1->uintlit != dim2->uintlit)
            return 0;
    }

    // We can subtype on element type sometimes
    TypeCompare result = itypeMatches(arrayElemType((INode*)to), arrayElemType((INode*)from), constraint);
    switch (result) {
    case ConvSubtype:
        return (constraint == Monomorph) ? result : NoMatch;
    default:
        return result;
    }
}
