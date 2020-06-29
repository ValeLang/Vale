/** void type
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new Void type node
VoidTypeNode *newVoidNode() {
    VoidTypeNode *voidnode;
    newNode(voidnode, VoidTypeNode, VoidTag);
    voidnode->flags |= OpaqueType;
    return voidnode;
}

// Clone void
INode *cloneVoidNode(CloneState *cstate, VoidTypeNode *node) {
    StarNode *newnode = memAllocBlk(sizeof(VoidTypeNode));
    memcpy(newnode, node, sizeof(VoidTypeNode));
    return (INode *)newnode;
}

// Create a new Absence node
AbsenceNode *newAbsenceNode() {
    AbsenceNode *node;
    newNode(node, AbsenceNode, AbsenceTag);
    node->vtype = (INode*)newVoidNode();
    return node;
}

// Serialize the void type node
void voidPrint(VoidTypeNode *voidnode) {
    inodeFprint("void");
}
