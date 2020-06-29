/** Handling for type literals
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef list_h
#define list_h

// Serialize a type literal
void typeLitPrint(FnCallNode *node);

// Name resolution of the literal node
void typeLitNameRes(NameResState *pstate, FnCallNode *lit);

// Reorder the literal's field values to the same order as the type's fields
// Also prevent the specification of a value for a private field outside the type's methods
int typeLitStructReorder(FnCallNode *arrlit, StructNode *strnode, int private);

// Type check an array literal
void typeLitArrayCheck(TypeCheckState *pstate, ArrayNode *arrlit);

// Check the type literal node
void typeLitTypeCheck(TypeCheckState *pstate, FnCallNode *lit);

// Is the type literal actually a literal?
int typeLitIsLiteral(FnCallNode *node);

#endif
