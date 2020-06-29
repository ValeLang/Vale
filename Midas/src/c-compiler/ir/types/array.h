/** Handling for array types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef array_h
#define array_h

// Array node: Either an array type or an array literal
typedef struct {
    ITypeNodeHdr;
    Nodes *dimens;    // Dimensions of the array
    Nodes *elems;     // Either a list of elements, or the element type
} ArrayNode;

// Create a new array node
ArrayNode *newArrayNode();

// Clone array
INode *cloneArrayNode(CloneState *cstate, ArrayNode *node);

// Create a new array type of a specified size and element type
ArrayNode *newArrayNodeTyped(INode *lexnode, size_t size, INode *elemtype);

// Return the element type of the array type
INode *arrayElemType(INode *array);

// Return the size of the first dimension (assuming 1-dimensional array)
uint64_t arrayDim1(INode *array);

void arrayPrint(ArrayNode *node);

// Name resolution of an array type
void arrayNameRes(NameResState *pstate, ArrayNode *node);

// Type check an array type
void arrayTypeCheck(TypeCheckState *pstate, ArrayNode *name);

int arrayEqual(ArrayNode *node1, ArrayNode *node2);

// Is from-type a subtype of to-struct (we know they are not the same)
TypeCompare arrayMatches(ArrayNode *to, ArrayNode *from, SubtypeConstraint constraint);

#endif
