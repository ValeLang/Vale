/** Enumerated values/types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

// Create a new enum declaration node
EnumNode *newEnumNode() {
    EnumNode *node;
    newNode(node, EnumNode, EnumTag);
    node->bytes = 1;
    node->namesym = anonName;
    node->llvmtype = NULL;
    iNsTypeInit((INsTypeNode*)node, 8);
    return node;
}

// Serialize a lifetime node
void enumPrint(EnumNode *node) {
    inodeFprint("enum ");
}

// Name resolution of an enum type
void enumNameRes(NameResState *pstate, EnumNode *node) {
}

// Type check an enum type
void enumTypeCheck(TypeCheckState *pstate, EnumNode *node) {
}
