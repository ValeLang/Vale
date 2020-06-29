/** Handling for intrinsic nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new intrinsic node
IntrinsicNode *newIntrinsicNode(int16_t intrinsic) {
    IntrinsicNode *intrinsicNode;
    newNode(intrinsicNode, IntrinsicNode, IntrinsicTag);
    intrinsicNode->intrinsicFn = intrinsic;
    return intrinsicNode;
}

// Serialize an intrinsic node
void intrinsicPrint(IntrinsicNode *intrinsicNode) {
    inodeFprint("intrinsic function");
}

// Check the intrinsic node
void intrinsicPass(TypeCheckState *pstate, IntrinsicNode *intrinsicNode) {
}
