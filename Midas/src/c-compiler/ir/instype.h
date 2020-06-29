/** Shared logic for namespace-based types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef instype_h
#define instype_h

// Namespaced type that supports named nodes (e.g., methods) and traits
#define INsTypeNodeHdr \
    ITypeNodeHdr; \
    Name *namesym; \
    NodeList nodelist; \
    Namespace namespace

// Interface for a namespaced type
// -> nodes (NodeList) is the list of nodes
// -> namespace is the dictionary of names (methods, fields)
// -> subtypes (Nodes) is the list of trait/interface subtypes it implements
typedef struct INsTypeNode {
    INsTypeNodeHdr;
} INsTypeNode;

// Needed for helper functions
typedef struct FnDclNode FnDclNode;
typedef struct VarDclNode VarDclNode;

// Initialize common fields
void iNsTypeInit(INsTypeNode *type, int nodecnt);

// Add a static function or potentially overloaded method to dictionary
// If method is overloaded, add it to the link chain of same named methods
void iNsTypeAddFnDict(INsTypeNode *type, FnDclNode *fnnode);

// Add a function/method to type's dictionary and owned list
void iNsTypeAddFn(INsTypeNode *type, FnDclNode *fnnode);

// Find the named node (could be method or field)
// Return the node, if found or NULL if not found
INode *iNsTypeFindFnField(INsTypeNode *type, Name *name);

// Find method that best fits the passed arguments
// 'firstmethod' is the first method that matches the name
// We follow its forward links to find one whose parameter types best match args types
// isvref skips type checking of the 'self' parameter for virtual references
FnDclNode *iNsTypeFindBestMethod(FnDclNode *firstmethod, INode **self, Nodes *args);

// Find method whose method signature matches exactly (except for self)
// return NULL if none
FnDclNode *iNsTypeFindVrefMethod(FnDclNode *firstmeth, FnDclNode *matchmeth);

#endif
