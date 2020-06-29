/** Handling for generic nodes (also used for macros)
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef generic_h
#define generic_h

// Generic declaration node
typedef struct GenericNode {
    IExpNodeHdr;             // 'vtype': type of this name's value
    Name *namesym;
    Nodes *parms;            // Declared parameter nodes w/ defaults (GenVarTag)
    INode *body;             // The body of the generic
    Nodes *memonodes;        // Pairs of memoized generic calls and cloned bodies
} GenericNode;

// Create a new macro declaraction node
GenericNode *newMacroDclNode(Name *namesym);

// Create a new generic declaration node
GenericNode *newGenericDclNode(Name *namesym);

void genericPrint(GenericNode *fn);

// Name resolution
void genericNameRes(NameResState *pstate, GenericNode *node);

// Type check generic
void genericTypeCheck(TypeCheckState *pstate, GenericNode *node);

// Type check generic name use
void genericNameTypeCheck(TypeCheckState *pstate, NameUseNode **macro);

// Instantiate a generic using passed arguments
void genericCallTypeCheck(TypeCheckState *pstate, FnCallNode **nodep);

// Instantiate a generic function node whose type parameters are inferred from the function call arguments
int genericInferVars(TypeCheckState *pstate, FnCallNode **nodep);

#endif
