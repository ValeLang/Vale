/** Permission Types
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <assert.h>

// Create a new permission declaration node
PermNode *newPermDclNode(Name *namesym, uint16_t flags) {
    PermNode *node;
    newNode(node, PermNode, PermTag);
    node->namesym = namesym;
    node->llvmtype = NULL;
    iNsTypeInit((INsTypeNode*)node, 1); // May not need members for static types
    node->permflags = flags;
    return node;
}

// Create a new permission use node
INode *newPermUseNode(PermNode *permdcl) {
    NameUseNode *perm;
    newNode(perm, NameUseNode, TypeNameUseTag);
    perm->vtype = NULL;
    perm->namesym = permdcl->namesym;
    perm->dclnode = (INode*)permdcl;
    perm->qualNames = NULL;
    return (INode*)perm;
}

// Serialize a permission node
void permPrint(PermNode *node) {
    inodeFprint("%s ", &node->namesym->namestr);
}

// Get permission's flags
int permGetFlags(INode *perm) {
    if (perm->tag == TypeNameUseTag)
        perm = (INode*)((NameUseNode*)perm)->dclnode;
    assert(perm->tag == PermTag);
    return ((PermNode *)perm)->permflags;
}

// Are the permissions the same?
int permIsSame(INode *node1, INode *node2) {
    if (node1->tag == TypeNameUseTag)
        node1 = (INode*)((NameUseNode*)node1)->dclnode;
    if (node2->tag == TypeNameUseTag)
        node2 = (INode*)((NameUseNode*)node2)->dclnode;
    assert(node1->tag == PermTag && node2->tag == PermTag);
    return node1 == node2;
}

// Will 'from' permission coerce to the target?
int permMatches(INode *ito, INode *ifrom) {
    PermNode *from = (PermNode *)((ifrom->tag == TypeNameUseTag)? (INode*)((NameUseNode*)ifrom)->dclnode : ifrom);
    PermNode *to = (PermNode *)((ito->tag == TypeNameUseTag)? (INode*)((NameUseNode*)ito)->dclnode : ito);
    assert(from->tag == PermTag && to->tag == PermTag);
    if (to==from || to==opaqPerm)
        return EqMatch;
    if (from == uniPerm &&
        (to == constPerm || to == mutPerm || to == immPerm || to == mut1Perm))
        return EqMatch;
    if (to == constPerm &&
        (from == mutPerm || from == immPerm || from == mut1Perm))
        return EqMatch;
    return NoMatch;
}
