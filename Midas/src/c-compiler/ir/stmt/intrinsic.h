/** Handling for intrinsic nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef intrinsic_h
#define intrinsic_h

// The various intrinsic functions supported by IntrinsicNode
enum IntrinsicFn {
    // Arithmetic
    NegIntrinsic,
    IsTrueIntrinsic,
    AddIntrinsic,
    SubIntrinsic,
    MulIntrinsic,
    DivIntrinsic,
    RemIntrinsic,
    IncrIntrinsic,
    DecrIntrinsic,
    IncrPostIntrinsic,
    DecrPostIntrinsic,
    DiffIntrinsic,  // subtract two pointers
    AddEqIntrinsic,
    SubEqIntrinsic,

    // Comparison
    EqIntrinsic,
    NeIntrinsic,
    LtIntrinsic,
    LeIntrinsic,
    GtIntrinsic,
    GeIntrinsic,

    // Bitwise
    NotIntrinsic,
    AndIntrinsic,
    OrIntrinsic,
    XorIntrinsic,
    ShlIntrinsic,
    ShrIntrinsic,

    // Reference methods
    CountIntrinsic,

    // Intrinsic functions
    SqrtIntrinsic,
    SinIntrinsic,
    CosIntrinsic
};

// An internal operation (e.g., add). 
// Used as an alternative to FnDcl->value = Block within a function declaration.
typedef struct IntrinsicNode {
    INodeHdr;
    int16_t intrinsicFn;
} IntrinsicNode;

IntrinsicNode *newIntrinsicNode(int16_t intrinsicFn);

#endif
