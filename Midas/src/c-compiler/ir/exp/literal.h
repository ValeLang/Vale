/** Handling for literals
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef literal_h
#define literal_h

// Unsigned integer literal
typedef struct {
    IExpNodeHdr;
    uint64_t uintlit;
} ULitNode;

// Float literal
typedef struct {
    IExpNodeHdr;
    double floatlit;
} FLitNode;

// String literal
typedef struct {
    IExpNodeHdr;
    char *strlit;
    uint32_t strlen;
} SLitNode;

// The null literal
typedef struct {
    IExpNodeHdr;
} NullNode;

ULitNode *newULitNode(uint64_t nbr, INode *type);

// Create a new unsigned literal node (after name resolution)
ULitNode *newULitNodeTC(uint64_t nbr, INode *type);

// Clone literal
INode *cloneULitNode(CloneState *cstate, ULitNode *lit);

void ulitPrint(ULitNode *node);

FLitNode *newFLitNode(double nbr, INode *type);
void flitPrint(FLitNode *node);

// Clone literal
INode *cloneFLitNode(CloneState *cstate, FLitNode *lit);

// Name resolution of lit node
void litNameRes(NameResState* pstate, IExpNode *node);

// Type check lit node
void litTypeCheck(TypeCheckState* pstate, IExpNode *node, INode *expectType);

SLitNode *newSLitNode(char *str, uint32_t strlen);

// Clone literal
INode *cloneSLitNode(SLitNode *lit);

void slitPrint(SLitNode *node);

// Type check string literal node
void slitTypeCheck(TypeCheckState *pstate, SLitNode *node);

int litIsLiteral(INode* node);

#endif
