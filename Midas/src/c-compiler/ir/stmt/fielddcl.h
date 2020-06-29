/** Handling for field declaration nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef fielddcl_h
#define fielddcl_h

// Field declaration node
typedef struct FieldDclNode {
    IExpNodeHdr;               // 'vtype': field's type
    Name *namesym;
    INode *value;              // Default value (NULL if not initialized)
    INode *perm;               // Permission type (often mut or imm)
    uint16_t index;            // field's index within the type
    uint16_t vtblidx;          // field's index within the type's vtable
} FieldDclNode;


FieldDclNode *newFieldDclNode(Name *namesym, INode *perm);

// Create a new field node that is a copy of an existing one
INode *cloneFieldDclNode(CloneState *cstate, FieldDclNode *node);

void fieldDclPrint(FieldDclNode *fn);

// Name resolution of field declaration
void fieldDclNameRes(NameResState *pstate, FieldDclNode *node);

// Type check field declaration
void fieldDclTypeCheck(TypeCheckState *pstate, FieldDclNode *node);

#endif
