/** Handling for primitive numbers
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Clone number node
INode *cloneNbrNode(CloneState *cstate, NbrNode *node) {
    NbrNode *newnode = memAllocBlk(sizeof(NbrNode));
    memcpy(newnode, node, sizeof(NbrNode));
    return (INode *)newnode;
}

// Serialize a numeric literal node
void nbrTypePrint(NbrNode *node) {
    if (node == i8Type)
        inodeFprint("i8");
    else if (node == i16Type)
        inodeFprint("i16");
    else if (node == i32Type)
        inodeFprint("i32");
    else if (node == i64Type)
        inodeFprint("i64");
    else if (node == u8Type)
        inodeFprint("u8");
    else if (node == u16Type)
        inodeFprint("u16");
    else if (node == u32Type)
        inodeFprint("u32");
    else if (node == u64Type)
        inodeFprint("u64");
    else if (node == f32Type)
        inodeFprint("f32");
    else if (node == f64Type)
        inodeFprint("f64");
    else if (node == boolType)
        inodeFprint("Bool");
}

// Is a number-typed node
int isNbr(INode *node) {
    return (node->tag == IntNbrTag || node->tag == UintNbrTag || node->tag == FloatNbrTag);
}

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *nbrFindSuper(INode *type1, INode *type2) {
    NbrNode *typ1 = (NbrNode *)itypeGetTypeDcl(type1);
    NbrNode *typ2 = (NbrNode *)itypeGetTypeDcl(type2);

    return typ1->bits >= typ2->bits ? type1 : type2;
}

// Is from-type a subtype of to-struct (we know they are not the same)
TypeCompare nbrMatches(INode *totype, INode *fromtype, SubtypeConstraint constraint) {
    // If coming from a ref, we cannot support type conversion
    if (constraint != Monomorph && constraint != Coercion)
        return NoMatch;

    // Bool is handled as a special case (also see iexpMatches)
    if (totype == (INode*)boolType)
        return NoMatch;

    if (totype->tag != fromtype->tag)
        return NoMatch;
    if (((NbrNode *)totype)->bits == ((NbrNode *)fromtype)->bits)
        return EqMatch;
    return ((NbrNode *)totype)->bits > ((NbrNode *)fromtype)->bits ? ConvSubtype : NoMatch;
}
