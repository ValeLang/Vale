/** Handling for record-based types with fields
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef struct_h
#define struct_h

typedef struct ModuleNode ModuleNode;

// Describes how some struct implements a virtual reference's vtable
typedef struct {
    INode *structdcl;          // struct that implements
    Nodes *methfld;            // specific methods and fields in same order as vtable
    char *name;                // generated name for the implemented vtable
    LLVMValueRef llvmvtablep;  // generates a pointer to the implemented vtable
} VtableImpl;

// Describes the virtual interface supported by some trait/struct
typedef struct {
    Nodes *methfld;            // list of public methods and then fields
    Nodes *impl;               // list of VtableImpl, for structs using this virtref
    char *name;                // generated name for the vtable type
    LLVMTypeRef llvmvtable;    // for the vtable
    LLVMTypeRef llvmreftype;   // For the virtual reference, not the vtable
    LLVMValueRef llvmvtables;  // List of vtables
} Vtable;

// Field-containing types (e.g., struct, trait, etc.)
// - fields holds all owned and trait-inherited fields
// - nodelist holds owned methods and static functions and variables
// - namespace is the dictionary of all owned and inherited named nodes
typedef struct StructNode {
    INsTypeNodeHdr;
    ModuleNode *mod;        // Owning module, to check if struct defined in same mod as trait
    INode *basetrait;       // Which trait has fields embedded at start of this trait/struct
    Nodes *derived;         // If a closed, base trait, this lists all structs derived from it
    NodeList fields;        // Ordered list of all fields
    Vtable *vtable;         // Pointer to vtable info (may be NULL)
    uint32_t tagnbr;        // If a tagged struct, this is the number in the tag field
} StructNode;

typedef struct FieldDclNode FieldDclNode;

StructNode *newStructNode(Name *namesym);

// Clone struct
INode *cloneStructNode(CloneState *cstate, StructNode *node);

// Add a field node to a struct type
void structAddField(StructNode *type, FieldDclNode *node);

void structPrint(StructNode *node);

// Name resolution of a struct type
void structNameRes(NameResState *pstate, StructNode *node);

// Get bottom-most base trait for some trait/struct, or NULL if there is not one
StructNode *structGetBaseTrait(StructNode *node);

// Type check a struct type
void structTypeCheck(TypeCheckState *pstate, StructNode *name);

// Populate the vtable for this struct
void structMakeVtable(StructNode *node);

// Populate the vtable implementation info for a struct ref being coerced to some trait
TypeCompare structVirtRefMatches(StructNode *trait, StructNode *strnode);

// Will from-type coerce to to-struct (we know they are not the same)
// We can only do this for a same-sized trait supertype
TypeCompare structMatches(StructNode *to, INode *fromdcl, SubtypeConstraint constraint);

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *structFindSuper(INode *type1, INode *type2);

// Return a type that is the supertype of both type nodes, or NULL if none found
// This is used by reference types, where same-sized is no longer a requirement
INode *structRefFindSuper(INode *type1, INode *type2);

#endif
