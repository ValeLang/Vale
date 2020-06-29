/** Shared logic for namespace-based types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"

#include <stdio.h>
#include <string.h>
#include <stdarg.h>

// Initialize common fields
void iNsTypeInit(INsTypeNode *type, int nodecnt) {
    nodelistInit(&type->nodelist, nodecnt);
    namespaceInit(&type->namespace, nodecnt);
    // type->subtypes = newNodes(0);
}

// Add a function or potentially overloaded method to dictionary
// If method is overloaded, add it to the link chain of same named methods
void iNsTypeAddFnDict(INsTypeNode *type, FnDclNode *fnnode) {
    FnDclNode *foundnode = (FnDclNode*)namespaceAdd(&type->namespace, fnnode->namesym, (INode*)fnnode);
    if (foundnode) {
        if (foundnode->tag != FnDclTag
            || !(foundnode->flags & FlagMethFld) || !(fnnode->flags & FlagMethFld)) {
            errorMsgNode((INode*)fnnode, ErrorDupName, "Duplicate name %s: Only methods can be overloaded.", &fnnode->namesym->namestr);
            return;
        }
        // Append to end of linked method list
        while (foundnode->nextnode)
            foundnode = foundnode->nextnode;
        foundnode->nextnode = fnnode;
    }
}

// Add a function/method to type's dictionary and owned list
void iNsTypeAddFn(INsTypeNode *type, FnDclNode *fnnode) {
    NodeList *mnodes = &type->nodelist;
    nodelistAdd(mnodes, (INode*)fnnode);
    iNsTypeAddFnDict(type, fnnode);
}

// Find the named node (could be method or field)
// Return the node, if found or NULL if not found
INode *iNsTypeFindFnField(INsTypeNode *type, Name *name) {
    return namespaceFind(&type->namespace, name);
}

// Find method that best fits the passed arguments
FnDclNode *iNsTypeFindBestMethod(FnDclNode *firstmethod, INode **self, Nodes *args) {
    // Look for best-fit method
    FnDclNode *bestmethod = NULL;
    int bestnbr = 0x7fffffff; // ridiculously high number    
    for (FnDclNode *methnode = (FnDclNode *)firstmethod; methnode; methnode = methnode->nextnode) {
        int match;
        switch (match = fnSigMatchMethCall((FnSigNode *)methnode->vtype, self, args)) {
        case 0: continue;        // not an acceptable match
        case 1: return methnode;    // perfect match!
        default:                // imprecise match using conversions
            if (match < bestnbr) {
                // Remember this as best found so far
                bestnbr = match;
                bestmethod = methnode;
            }
        }
    }
    return bestmethod;
}

// Find method whose method signature matches exactly (except for self)
FnDclNode *iNsTypeFindVrefMethod(FnDclNode *firstmeth, FnDclNode *matchmeth) {
    if (firstmeth == NULL || firstmeth->tag != FnDclTag) {
        //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to a %s virtual reference. Missing method %s.",
        //    &strnode->namesym->namestr, &trait->namesym->namestr, &meth->namesym->namestr);
        return 0;
    }
    // Look through all overloaded methods for a match
    while (firstmeth) {
        if (fnSigVrefEqual((FnSigNode*)firstmeth->vtype, (FnSigNode*)matchmeth->vtype))
            break;
        firstmeth = firstmeth->nextnode;
    }
    if (firstmeth == NULL) {
        //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to a %s virtual reference. Incompatible type for method %s.",
        //    &strnode->namesym->namestr, &trait->namesym->namestr, &meth->namesym->namestr);
        return 0;
    }
    return firstmeth;
}
