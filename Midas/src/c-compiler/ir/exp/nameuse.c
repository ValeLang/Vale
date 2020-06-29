/** Name and Member Use nodes.
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <string.h>
#include <assert.h>

// A list of module names (qualifiers)
typedef struct NameList {
    uint16_t avail;     // Max. number of names allocated for
    uint16_t used;      // Number of names stored in list
    ModuleNode *basemod;  // base module (root or current) holding qualifiers
    // Name* pointers for qualifiers follow, starting here
} NameList;

// Create a new name use node
NameUseNode *newNameUseNode(Name *namesym) {
    NameUseNode *name;
    newNode(name, NameUseNode, NameUseTag);
    name->qualNames = NULL;
    name->dclnode = NULL;
    name->namesym = namesym;
    return name;
}

// Create a working variable for a value we intend to reuse later
// The vardcl is appended to a list of nodes, and the nameuse node to it is returned
INode *newNameUseAndDcl(Nodes **nodesp, INode *val, uint16_t scope) {
    VarDclNode *var = (VarDclNode*)newVarDclFull(anonName, VarDclTag, unknownType, (INode*)immPerm, val);
    var->scope = scope;
    nodesAdd(nodesp, (INode*)var);
    NameUseNode *varuse = newNameUseNode(anonName);
    varuse->tag = VarNameUseTag;
    varuse->dclnode = (INode*)var;
    return (INode*)varuse;
}

NameUseNode *newMemberUseNode(Name *namesym) {
    NameUseNode *name;
    newNode(name, NameUseNode, MbrNameUseTag);
    name->qualNames = NULL;
    name->dclnode = NULL;
    name->namesym = namesym;
    return name;
}

// Clone NameUse
INode *cloneNameUseNode(CloneState *cstate, NameUseNode *node) {
    NameUseNode *newnode;
    newnode = memAllocBlk(sizeof(NameUseNode));
    memcpy(newnode, node, sizeof(NameUseNode));
    newnode->dclnode = cloneDclFix(node->dclnode);
    return (INode *)newnode;
}

// If a NameUseNode has module name qualifiers, it will first set basemod
// (either root module or the current module scope). This allocates an area
// for qualifiers to be added.
void nameUseBaseMod(NameUseNode *node, ModuleNode *basemod) {
    node->qualNames = (NameList *)memAllocBlk(sizeof(NameList) + 4 * sizeof(Name*));
    node->qualNames->avail = 4;
    node->qualNames->used = 0;
    node->qualNames->basemod = basemod;
}

// Add a module name qualifier to the end of the list
void nameUseAddQual(NameUseNode *node, Name *name) {
    uint16_t used = node->qualNames->used;
    if (used + 1 >= node->qualNames->avail) {
        NameList *oldlist = node->qualNames;
        uint16_t newavail = oldlist->avail << 1;
        node->qualNames = (NameList *)memAllocBlk(sizeof(NameList) + newavail * sizeof(Name*));
        node->qualNames->avail = newavail;
        node->qualNames->used = used;
        node->qualNames->basemod = oldlist->basemod;
        Name **oldp = (Name**)(oldlist + 1);
        Name **newp = (Name**)(node->qualNames + 1);
        uint16_t cnt = used;
        while (cnt--)
            *newp++ = *oldp++;
    }
    Name **namep = (Name**)&(node->qualNames + 1)[used];
    *namep = name;
    ++node->qualNames->used;
}

// Serialize a name use node
void nameUsePrint(NameUseNode *name) {
    if (name->qualNames) {
        // if root: inodeFprint("::");
        uint16_t cnt = name->qualNames->used;
        Name **namep = (Name**)(name->qualNames + 1);
        while (cnt--)
            inodeFprint("%s::", &(*namep++)->namestr);
    }
    inodeFprint("%s", &name->namesym->namestr);
}

// Handle name resolution for name use references
// - Point to name declaration in other module or this one
// - If name is for a method or field, rewrite node as 'self.field'
// - If not method/field, re-tag it as either TypeNameUse or VarNameUse
void nameUseNameRes(NameResState *pstate, NameUseNode **namep) {
    NameUseNode *name = *namep;

    // If name is already "resolved", we are done.
    // This will happen with de-sugaring logic that creates pre-resolved phantom variables
    if (name->dclnode)
        return;

    // For module-qualified names, look up name in that module
    if (name->qualNames) {
        // Do iterative look ups of module qualifiers beginning with basemod
        ModuleNode *mod = name->qualNames->basemod;
        uint16_t cnt = name->qualNames->used;
        Name **namep = (Name**)(name->qualNames + 1);
        while (cnt--) {
            mod = (ModuleNode*)namespaceFind(&mod->namespace, *namep++);
            if (mod == NULL || mod->tag != ModuleTag) {
                errorMsgNode((INode*)name, ErrorUnkName, "Module %s does not exist", &(*--namep)->namestr);
                return;
            }
        }
        name->dclnode = namespaceFind(&mod->namespace, name->namesym);
    }
    else
        // For non-qualified names (current module), should already be hooked in global name table
        name->dclnode = name->namesym->node;

    if (!name->dclnode) {
        errorMsgNode((INode*)name, ErrorUnkName, "The name %s does not refer to a declared name", &name->namesym->namestr);
        return;
    }

    // If name is for a method or field, rewrite node as 'self.field'
    if (name->dclnode->tag == FieldDclTag && name->dclnode->flags & FlagMethFld) {
        // Doing this rewrite ensures we reuse existing type check and gen code for
        // properly handling field access
        NameUseNode *selfnode = newNameUseNode(selfName);
        FnCallNode *fncall = newFnCallNode((INode *)selfnode, 0);
        fncall->methfld = name;
        copyNodeLex(fncall, name); // Copy lexer info into injected node in case it has errors
        *((FnCallNode**)namep) = fncall;
        inodeNameRes(pstate, (INode **)namep);
        return;
    }

    // Distinguish whether a name is for a variable/function name vs. type
    if (name->dclnode->tag == VarDclTag || name->dclnode->tag == FnDclTag)
        name->tag = VarNameUseTag;
    else if (name->dclnode->tag == MacroDclTag)
        name->tag = MacroNameTag;
    else if (name->dclnode->tag == GenericDclTag)
        name->tag = GenericNameTag;
    else if (name->dclnode->tag == GenVarDclTag)
        name->tag = GenVarUseTag;
    else
        name->tag = TypeNameUseTag;
}

// Handle type check for variable/function name use references
void nameUseTypeCheck(TypeCheckState *pstate, NameUseNode **namep) {
    NameUseNode *name = *namep;
    name->vtype = ((IExpNode*)name->dclnode)->vtype;
}

// Handle type check for type name use references
void nameUseTypeCheckType(TypeCheckState *pstate, NameUseNode **namep) {
    // Do type check on the type declaration this refers to,
    // to ensure it is correct and knows about its infectious constraints
    // Guards are in place to ensure this only will be done once, as early as possible.
    INode **dclnode = &(*namep)->dclnode;
    if (((*dclnode)->flags & TypeChecking) && !((*dclnode)->flags & TypeChecked)) {
        errorMsgNode((INode*)*namep, ErrorRecurse, "Recursive types are not supported for now.");
        return;
    }
    else
        inodeTypeCheckAny(pstate, dclnode);
}
