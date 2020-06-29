/** Handling for variable declaration nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <string.h>
#include <assert.h>

// Create a new name declaraction node
VarDclNode *newVarDclNode(Name *namesym, uint16_t tag, INode *perm) {
    VarDclNode *name;
    newNode(name, VarDclNode, tag);
    name->vtype = unknownType;
    name->namesym = namesym;
    name->perm = perm;
    name->value = NULL;
    name->scope = 0;
    name->index = 0;
    name->llvmvar = NULL;
    name->genname = &namesym->namestr;
    name->flowflags = 0;
    name->flowtempflags = 0;
    return name;
}

// Create a new name declaraction node
VarDclNode *newVarDclFull(Name *namesym, uint16_t tag, INode *type, INode *perm, INode *val) {
    VarDclNode *name;
    newNode(name, VarDclNode, tag);
    name->vtype = type;
    name->namesym = namesym;
    name->perm = perm;
    name->value = val;
    name->scope = 0;
    name->index = 0;
    name->llvmvar = NULL;
    name->flowflags = 0;
    name->flowtempflags = 0;
    return name;
}

// Create a new variable dcl node that is a copy of an existing one
INode *cloneVarDclNode(CloneState *cstate, VarDclNode *node) {
    VarDclNode *newnode = memAllocBlk(sizeof(VarDclNode));
    memcpy(newnode, node, sizeof(VarDclNode));
    newnode->vtype = cloneNode(cstate, node->vtype);
    newnode->value = cloneNode(cstate, node->value);
    cloneDclSetMap((INode*)node, (INode*)newnode);
    return (INode*)newnode;
}

// Serialize a variable node
void varDclPrint(VarDclNode *name) {
    inodePrintNode((INode*)name->perm);
    inodeFprint(" %s ", &name->namesym->namestr);
    inodePrintNode(name->vtype);
    if (name->value) {
        inodeFprint(" = ");
        if (name->value->tag == BlockTag)
            inodePrintNL();
        inodePrintNode(name->value);
    }
}

// Enable name resolution of local variables
void varDclNameRes(NameResState *pstate, VarDclNode *name) {
    inodeNameRes(pstate, (INode**)&name->perm);
    if (name->vtype)
        inodeNameRes(pstate, &name->vtype);

    // Name resolve value before hooking the variable name (so it cannot point to itself)
    if (name->value)
        inodeNameRes(pstate, &name->value);

    // Variable declaration within a block is a local variable
    if (pstate->scope > 0) {
        if (name->namesym->node && pstate->scope == ((VarDclNode*)name->namesym->node)->scope) {
            errorMsgNode((INode *)name, ErrorDupName, "Name is already defined. Only one allowed.");
            errorMsgNode((INode*)name->namesym->node, ErrorDupName, "This is the conflicting definition for that name.");
        }
        else {
            name->scope = pstate->scope;
            // Add name to global name table (containing block will unhook it later)
            nametblHookNode(name->namesym, (INode*)name);
        }
    }
}

// Type check variable against its initial value
void varDclTypeCheck(TypeCheckState *pstate, VarDclNode *name) {
    itypeTypeCheck(pstate, (INode**)&name->perm);
    if (itypeTypeCheck(pstate, &name->vtype) == 0)
        return;

    // An initializer need not be specified, but if not, it must have a declared type
    if (name->value == NULL) {
        if (name->vtype == unknownType) {
            errorMsgNode((INode*)name, ErrorNoType, "Declared name must specify a type or value");
            return;
        }
    }
    // Type check the initialization value
    else {
        // Verify that declared type and initial value type match
        if (!iexpTypeCheckCoerce(pstate, name->vtype, &name->value))
            errorMsgNode(name->value, ErrorInvType, "Initialization value's type does not match variable's declared type");
        else if (name->vtype == unknownType)
            name->vtype = ((IExpNode *)name->value)->vtype;
        // Global variables and function parameters require literal initializers
        if (name->scope <= 1 && !litIsLiteral(name->value))
            errorMsgNode(name->value, ErrorNotLit, "Variable may only be initialized with a literal value.");
    }

    // Variables cannot hold a void or opaque struct value
    if (!itypeIsConcrete(name->vtype))
        errorMsgNode((INode*)name, ErrorInvType, "Variable's type must be concrete and instantiable.");
}

// Perform data flow analysis
void varDclFlow(FlowState *fstate, VarDclNode **vardclnode) {
    flowAddVar(*vardclnode);
    if ((*vardclnode)->value) {
        size_t svAliasPos = flowAliasPushNew(1);
        flowLoadValue(fstate, &((*vardclnode)->value));
        flowAliasPop(svAliasPos);
        (*vardclnode)->flowtempflags |= VarInitialized;
    }
}
