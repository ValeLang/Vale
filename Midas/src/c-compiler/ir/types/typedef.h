/** Handling for typedef declaration nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef typedef_h
#define typedef_h

// Generic variable declaration node
typedef struct TypedefNode {
    ITypeNodeHdr;
    Name *namesym;
    INode *typeval;
} TypedefNode;

// Create a new typedef node
TypedefNode *newTypedefNode(Name *namesym);

void typedefPrint(TypedefNode *var);

// Name resolution
void typedefNameRes(NameResState *pstate, TypedefNode *var);

// Type check generic variable declaration
void typedefTypeCheck(TypeCheckState *pstate, TypedefNode *var);

#endif
