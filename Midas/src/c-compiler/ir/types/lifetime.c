/** Lifetime Annotations (Variables)
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"

#include <assert.h>

// Create a new lifetime declaration node
LifetimeNode *newLifetimeDclNode(Name *namesym, Lifetime life) {
    LifetimeNode *node;
    newNode(node, LifetimeNode, LifetimeTag);
    node->namesym = namesym;
    node->llvmtype = NULL;
    node->life = life;
    return node;
}

// Create a copy of lifetime dcl
INode *cloneLifetimeDclNode(CloneState *cstate, LifetimeNode *node) {
    LifetimeNode *newnode = memAllocBlk(sizeof(LifetimeNode));
    memcpy(newnode, node, sizeof(LifetimeNode));
    newnode->life = cstate->scope;
    cloneDclSetMap((INode*)node, (INode*)newnode);
    return (INode*)newnode;
}

// Create a new lifetime use node
INode *newLifetimeUseNode(LifetimeNode *lifedcl) {
    NameUseNode *life;
    newNode(life, NameUseNode, TypeNameUseTag);
    life->vtype = NULL;
    life->namesym = lifedcl->namesym;
    life->dclnode = (INode*)lifedcl;
    life->qualNames = NULL;
    return (INode*)life;
}

// Serialize a lifetime node
void lifePrint(LifetimeNode *node) {
    inodeFprint("%s ", &node->namesym->namestr);
}

// Are the lifetimes the same?
int lifeIsSame(INode *node1, INode *node2) {
    if (node1->tag == TypeNameUseTag)
        node1 = (INode*)((NameUseNode*)node1)->dclnode;
    if (node2->tag == TypeNameUseTag)
        node2 = (INode*)((NameUseNode*)node2)->dclnode;
    assert(node1->tag == LifetimeTag && node2->tag == LifetimeTag);
    return node1 == node2;
}

// Will 'from' lifetime coerce as a subtype to the target?
int lifeMatches(INode *ito, INode *ifrom) {
    LifetimeNode *from = (LifetimeNode *)((ifrom->tag == TypeNameUseTag)? (INode*)((NameUseNode*)ifrom)->dclnode : ifrom);
    LifetimeNode *to = (LifetimeNode *)((ito->tag == TypeNameUseTag)? (INode*)((NameUseNode*)ito)->dclnode : ito);
    assert(from->tag == LifetimeTag && to->tag == LifetimeTag);

    // 'static is a supertype of all lifetimes
    if (to->life == 0)
        return 1;

    uint16_t fromgrp = from->life >> 8;
    uint16_t fromscope = from->life & 0xff;
    uint16_t togrp = to->life >> 8;
    uint16_t toscope = to->life & 0xff;

    return (fromscope >= toscope && (fromgrp == 0 || fromgrp == togrp)) ? 1 : 0;
}
