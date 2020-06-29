/** Enumerated values/types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef enum_h
#define enum_h

// Lifetime type info
typedef struct EnumNode {
    INsTypeNodeHdr;
    uint8_t bytes;
} EnumNode;

// Create a new enum declaration node
EnumNode *newEnumNode();

// Serialize an enum node
void enumPrint(EnumNode *node);

// Name resolution of an enum type
void enumNameRes(NameResState *pstate, EnumNode *node);

// Type check an enum type
void enumTypeCheck(TypeCheckState *pstate, EnumNode *node);

#endif
