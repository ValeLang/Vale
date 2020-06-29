/** Name and Member Use Nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef nameuse_h
#define nameuse_h

typedef struct NameList NameList;

// Name use node, which ultimately points to the applicable declaration for the name
// The node's name may optionally include module name qualifiers. Used by:
// - NameUseTag. A name token prior to name resolution pass
// - VarNameUseTag. A name use node resolved to a variable or function declaration
// - TypeNameUseTag. A name use node resolved to a type declaration
// - MbrNameUseTag. A method or field name being applied to some value
typedef struct NameUseNode {
    IExpNodeHdr;
    Name *namesym;          // Pointer to the global name table entry
    INode *dclnode;         // Node that declares this name (NULL until names are resolved)
    NameList *qualNames;    // Pointer to list of module qualifiers (NULL if none)
} NameUseNode;

NameUseNode *newNameUseNode(Name *name);

// Create a working variable for a value we intend to reuse later
// The vardcl is appended to a list of nodes, and the nameuse node to it is returned
INode *newNameUseAndDcl(Nodes **nodesp, INode *val, uint16_t scope);

// Clone NameUse
INode *cloneNameUseNode(CloneState *cstate, NameUseNode *node);

void nameUseBaseMod(NameUseNode *node, ModuleNode *basemod);
void nameUseAddQual(NameUseNode *node, Name *name);
NameUseNode *newMemberUseNode(Name *namesym);
void nameUsePrint(NameUseNode *name);
// Handle name resolution for name use references
// - Point to name declaration in other module or this one
// - If name is for a method or field, rewrite node as 'self.field'
// - If not method/field, re-tag it as either TypeNameUse or VarNameUse
void nameUseNameRes(NameResState *pstate, NameUseNode **namep);

// Handle type check for variable/function name use references
void nameUseTypeCheck(TypeCheckState *pstate, NameUseNode **name);

// Handle type check for type name use references
void nameUseTypeCheckType(TypeCheckState *pstate, NameUseNode **name);

#endif
