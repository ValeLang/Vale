/** Handling for structs
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir.h"
#include <string.h>
#include <assert.h>

// Create a new struct type whose info will be filled in afterwards
StructNode *newStructNode(Name *namesym) {
    StructNode *snode;
    newNode(snode, StructNode, StructTag);
    snode->namesym = namesym;
    snode->llvmtype = NULL;
    iNsTypeInit((INsTypeNode*)snode, 8);
    nodelistInit(&snode->fields, 8);
    snode->mod = NULL;
    snode->basetrait = NULL;
    snode->derived = NULL;
    snode->vtable = NULL;
    snode->tagnbr = 0;
    return snode;
}

// Clone struct
INode *cloneStructNode(CloneState *cstate, StructNode *node) {
    StructNode *newnode = memAllocBlk(sizeof(StructNode));
    memcpy(newnode, node, sizeof(StructNode));
    newnode->basetrait = cloneNode(cstate, node->basetrait);
    // Fields like derived, vtable, tagnbr do not yet have useful data to clone

    // Recreate clones of fields/mixins and methods, sequentially and in namespace dictionary
    namespaceInit(&newnode->namespace, node->namespace.avail);
    INode **newnodesp = (INode**)memAllocBlk(node->fields.avail * sizeof(INode *));
    newnode->fields.nodes = newnodesp;
    INode **nodesp;
    uint32_t cnt;
    for (nodelistFor(&node->fields, cnt, nodesp)) {
        *newnodesp = cloneNode(cstate, *nodesp);
        if ((*newnodesp)->tag == FieldDclTag) {
            FieldDclNode *newfld = (FieldDclNode*)*newnodesp;
            namespaceAdd(&newnode->namespace, newfld->namesym, (INode*)newfld);
        }
        ++newnodesp;
    }
    newnodesp = (INode**)memAllocBlk(node->nodelist.avail * sizeof(INode *));
    newnode->nodelist.nodes = newnodesp;
    for (nodelistFor(&node->nodelist, cnt, nodesp)) {
        iNsTypeAddFn((INsTypeNode*)newnode, (FnDclNode*)(*newnodesp++ = cloneNode(cstate, *nodesp)));
    }

    return (INode *)newnode;
}

// Add a field
void structAddField(StructNode *type, FieldDclNode *fnode) {
    INode *foundnode = namespaceAdd(&type->namespace, fnode->namesym, (INode*)fnode);
    if (foundnode) {
        errorMsgNode((INode*)fnode, ErrorDupName, "Duplicate name %s: Only methods can be overloaded.", &fnode->namesym->namestr);
        return;
    }
    nodelistAdd(&type->fields, (INode*)fnode);
}

// Serialize a struct type
void structPrint(StructNode *node) {
    inodeFprint(node->tag == StructTag? "struct %s {}" : "alloc %s {}", &node->namesym->namestr);
}

// Name resolution of a struct type
void structNameRes(NameResState *pstate, StructNode *node) {
    INode *svtypenode = pstate->typenode;
    pstate->typenode = (INode*)node;
    nametblHookPush();
    namespaceAdd(&node->namespace, selfTypeName, (INode*)node);
    nametblHookNamespace(&node->namespace);
    if (node->basetrait)
        inodeNameRes(pstate, &node->basetrait);
    INode **nodesp;
    uint32_t cnt;
    for (nodelistFor(&node->fields, cnt, nodesp)) {
        inodeNameRes(pstate, (INode**)nodesp);
    }
    for (nodelistFor(&node->nodelist, cnt, nodesp)) {
        inodeNameRes(pstate, (INode**)nodesp);
    }
    nametblHookPop();
    pstate->typenode = svtypenode;
}

// Get bottom-most base trait for some trait/struct, or NULL if there is not one
StructNode *structGetBaseTrait(StructNode *node) {
    if (node->basetrait == NULL)
        return (node->flags & TraitType) ? node : NULL;
    return structGetBaseTrait((StructNode*)itypeGetTypeDcl(node->basetrait));
}

// Type check when a type specifies a base trait that has a closed number of variants
void structTypeCheckBaseTrait(StructNode *node) {
    // Get bottom-most base trait
    StructNode *basetrait = structGetBaseTrait(node);

    // We only need special handling when base trait has a closed number of variants
    // i.e., it uses a tag or is fixed-size
    uint16_t isClosedFlags = basetrait->flags & (SameSize | HasTagField);
    if (isClosedFlags == 0)
        return;
    node->flags |= isClosedFlags;  // mark this derived type as having these closed properties

    // A derived type of a closed trait must be declared in the same module
    if (basetrait->mod != node->mod) {
        errorMsgNode((INode*)node, ErrorInvType, "This type must be declared in the same module as the trait");
        return;
    }

    // If a derived type is a struct, capture its tag number 
    // and add it to the bottom-most trait's list of derived structs
    if (!(node->flags & TraitType)) {
        if (basetrait->derived == NULL)
            basetrait->derived = newNodes(4);
        node->tagnbr = basetrait->derived->used;
        nodesAdd(&basetrait->derived, (INode*)node);
    }
}

// Add method at end of strnode's method chain for this name
void structInheritMethod(StructNode *strnode, FnDclNode *traitmeth, StructNode *trait, CloneState *cstate) {
    // Add default method, so long as it implements logic
    if (traitmeth->value == NULL) {
        errorMsgNode((INode*)strnode, ErrorInvType, "Type must implement %s method, as required by %s",
            &traitmeth->namesym->namestr, &trait->namesym->namestr);
    }
    iNsTypeAddFn((INsTypeNode *)strnode, (FnDclNode*)cloneNode(cstate, (INode*)traitmeth));
}

// Type check a struct type
void structTypeCheck(TypeCheckState *pstate, StructNode *node) {
    INode *svtypenode = pstate->typenode;
    pstate->typenode = (INode*)node;

    INode **nodesp;
    uint32_t cnt;

    // Ensure traits keep a list of derived structs/traits
    if ((node->flags & TraitType) && node->derived == NULL)
        node->derived = newNodes(2);

    // Handle when a base trait is specified
    if (node->basetrait) {
        if (itypeTypeCheck(pstate, &node->basetrait) == 0)
            return;
        StructNode *basetrait = (StructNode*)itypeGetTypeDcl(node->basetrait);
        if (basetrait->tag != StructTag || !(basetrait->flags & TraitType)) {
            errorMsgNode(node->basetrait, ErrorInvType, "Base trait must be a trait");
        }
        else {
            // Do type-check with bottom-most trait
            // For closed types, the trait and derived node need info from each other
            structTypeCheckBaseTrait(node);

            // Insert "mixin" field for basetrait at start, to trigger mixin logic below
            FieldDclNode *mixin = newFieldDclNode(basetrait->namesym, (INode*)immPerm);
            mixin->flags |= IsMixin;
            mixin->vtype = node->basetrait;
            nodelistInsert(&node->fields, 0, (INode*)mixin);
        }
    }

    // Iterate backwards through all fields to type check and handle trait/field inheritance
    // We go backwards to prevent index invalidation and to ensure method inheritance stays in correct order.
    // When done, all trait mixins are replaced with the trait's fields
    // And trait/field inheritance are appropriately added to the dictionary in the correct order
    CloneState cstate;
    clonePushState(&cstate, (INode*)node, (INode*)node, 0, NULL, NULL);
    int32_t fldpos = node->fields.used - 1;
    INode **fldnodesp = &nodelistGet(&node->fields, fldpos);
    while (fldpos >= 0) {
        FieldDclNode *field = (FieldDclNode*)*fldnodesp;
        if (field->flags & IsMixin) {
            if (itypeTypeCheck(pstate, &field->vtype) == 0)
                return;
            // A dummy mixin field requesting we mixin its fields and methods
            StructNode *trait = (StructNode*)itypeGetTypeDcl(field->vtype);
            if (trait->tag != StructTag || !(trait->flags & TraitType)) {
                errorMsgNode(field->vtype, ErrorInvType, "mixin must be a trait");
            }
            else {
                // Replace the "mixin" field with all the trait's fields
                nodelistMakeSpace(&node->fields, fldpos, trait->fields.used - 1);
                INode **insertp = fldnodesp;
                for (nodelistFor(&trait->fields, cnt, nodesp)) {
                    FieldDclNode *newfld = (FieldDclNode*)cloneNode(&cstate, *nodesp);
                    *insertp++ = (INode *)newfld;
                    if (namespaceAdd(&node->namespace, newfld->namesym, (INode*)newfld)) {
                        errorMsgNode((INode*)newfld, ErrorDupName, "Trait may not mix in a duplicate field name");
                    }
                }
                // Fold in trait's default methods
                for (nodelistFor(&trait->nodelist, cnt, nodesp)) {
                    if ((*nodesp)->tag != FnDclTag)
                        continue;
                    FnDclNode *traitmeth = (FnDclNode*)*nodesp;
                    FnDclNode *structmeth = (FnDclNode*)iNsTypeFindFnField((INsTypeNode *)node, traitmeth->namesym);
                    if (structmeth == NULL)
                        // Inherit default method
                        structInheritMethod(node, traitmeth, trait, &cstate);
                    else {
                        // If no exact match, add it
                        if (iNsTypeFindVrefMethod(structmeth, traitmeth) == NULL)
                            structInheritMethod(node, traitmeth, trait, &cstate);
                    }
                }
            }
        }
        // Type check a normal field
        else
            inodeTypeCheckAny(pstate, fldnodesp);

        --fldpos; --fldnodesp;
    }
    clonePopState();

    // Go through all fields to index them and calculate infection flags for ThreadBound/MoveType
    uint16_t infectFlag = 0;
    uint16_t index = 0;
    for (nodelistFor(&node->fields, cnt, nodesp)) {
        // Number field indexes to reflect their possibly altered position
        ((FieldDclNode*)*nodesp)->index = index++;
        // Notice if a field's threadbound or movetype infects the struct
        ITypeNode *fldtype = (ITypeNode*)itypeGetTypeDcl(((IExpNode*)(*nodesp))->vtype);
        infectFlag |= fldtype->flags & (ThreadBound | MoveType);

        if (fldtype->tag == EnumTag && !((*nodesp)->flags & IsTagField)) {
            if ((node->flags & TraitType) && !(node->flags & HasTagField) && node->basetrait == NULL) {
                node->flags |= HasTagField;
                (*nodesp)->flags |= IsTagField;
            }
            else {
                errorMsgNode(*nodesp, ErrorInvType, "Empty enum type only allowed once in a base trait");
            }
            if (((FieldDclNode*)(*nodesp))->namesym != anonName)
                errorMsgNode(*nodesp, ErrorInvType, "The tag discriminant field name should be '_'");
        }
    }
    // Use inference rules to decide if struct is ThreadBound or a MoveType
    // based on whether its fields are, and whether it supports the .final or .clone method
    if (namespaceFind(&node->namespace, finalName))
        infectFlag |= MoveType;           // Let's not make copies of finalized objects
    if (namespaceFind(&node->namespace, cloneName))
        infectFlag &= 0xFFFF - MoveType;  // 'clone' means we can make copies anyway

    // Populate infection flags in this struct/trait, and recursively to all inherited traits
    if (infectFlag) {
        node->flags |= infectFlag;
        StructNode *trait = (StructNode *)node->basetrait;
        while (trait) {
            trait->flags |= infectFlag;
            trait = (StructNode *)node->basetrait;
        }
    }

    // A 0-size (no field) struct is opaque. Cannot be instantiated.
    if (node->fields.used == 0)
        node->flags |= OpaqueType;

    // We can say we know enough about the type at this point to
    // declare it fully checked, so as to not trigger type recursion warnings.
    node->flags |= TypeChecked;

    // Type check all methods, etc.
    for (nodelistFor(&node->nodelist, cnt, nodesp)) {
        inodeTypeCheckAny(pstate, (INode**)nodesp);
    }

    pstate->typenode = svtypenode;
}

// Add a vtable implementation to a base struct's vtable
// Return 1 if it successfully type matches, 0 if not
int structAddVtableImpl(StructNode *basenode, StructNode *strnode) {
    Vtable *vtable = basenode->vtable;

    // Create Vtable impl data structure and populate
    VtableImpl *impl = memAllocBlk(sizeof(VtableImpl));
    impl->llvmvtablep = NULL;
    impl->structdcl = (INode*)strnode;

    // Construct a global name for this vtable implementation
    size_t strsize = strnode->namesym->namesz + strlen(vtable->name) + 3;
    impl->name = memAllocStr(&strnode->namesym->namestr, strsize);
    strcat(impl->name, "->");
    strcat(impl->name, vtable->name);

    // For every field/method in the vtable, find its matching one in strnode
    impl->methfld = newNodes(vtable->methfld->used);
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(vtable->methfld, cnt, nodesp)) {
        if ((*nodesp)->tag == FnDclTag) {
            // Locate the corresponding method with matching name and vtype
            // Note, we need to be flexible in matching the self parameter
            FnDclNode *meth = (FnDclNode *)*nodesp;
            FnDclNode *strmeth = (FnDclNode *)namespaceFind(&strnode->namespace, meth->namesym);
            if ((strmeth = iNsTypeFindVrefMethod(strmeth, meth)) == NULL)
                return 0;
            // it matches, add the method to the implementation
            nodesAdd(&impl->methfld, (INode*)strmeth);
        }
        else {
            // Find the corresponding field with matching name and vtype
            FieldDclNode *fld = (FieldDclNode *)*nodesp;
            INode *strfld = namespaceFind(&strnode->namespace, fld->namesym);
            if (strfld == NULL || strfld->tag != FieldDclTag) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to a %s virtual reference. Missing field %s.",
                //    &strnode->namesym->namestr, &trait->namesym->namestr, &fld->namesym->namestr);
                return 0;
            }
            TypeCompare match = iexpMatches(&strfld, fld->vtype, Coercion);
            if (match != EqMatch && match != CastSubtype) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to a %s virtual reference. Incompatible type for field %s.",
                //    &strnode->namesym->namestr, &trait->namesym->namestr, &fld->namesym->namestr);
                return 0;
            }
            // it matches, add the corresponding field to the implementation
            nodesAdd(&impl->methfld, (INode*)strfld);
        }
    }

    // We accomplished a successful mapping - add it
    nodesAdd(&vtable->impl, (INode*)impl);
    return 1;
}

// Populate the vtable for this struct
void structMakeVtable(StructNode *node) {
    if (node->vtable)
        return;
    Vtable *vtable = memAllocBlk(sizeof(Vtable));
    node->vtable = vtable;
    vtable->llvmreftype = NULL;
    vtable->llvmvtable = NULL;
    vtable->impl = newNodes(4);

    vtable->name = memAllocStr(&node->namesym->namestr, node->namesym->namesz + 8);
    strcat(vtable->name, ":Vtable");

    // Populate methfld with all public methods and then fields in trait
    vtable->methfld = newNodes(node->fields.used + node->nodelist.used);
    uint32_t vtblidx = 0;  // track the index position for each added vtable field
    INode **nodesp;
    uint32_t cnt;
    for (nodelistFor(&node->nodelist, cnt, nodesp)) {
        FnDclNode *meth = (FnDclNode *)*nodesp;
        if (meth->namesym->namestr != '_') {
            meth->vtblidx = vtblidx++;
            nodesAdd(&vtable->methfld, *nodesp);
        }
    }
    for (nodelistFor(&node->fields, cnt, nodesp)) {
        FieldDclNode *field = (FieldDclNode *)*nodesp;
        INode *fieldtyp = itypeGetTypeDcl(field->vtype);
        if (field->namesym->namestr != '_' && fieldtyp->tag != EnumTag) {
            field->vtblidx = vtblidx++;
            nodesAdd(&vtable->methfld, *nodesp);
        }
    }
    
    // Prewire the implementation of any known derived structs.
    // This is particularly important for keeping tag order correctness
    // when building a virtual ref out of a regular ref to the trait
    // as it uses the tag value to index into a list of vtables
    for (nodesFor(node->derived, cnt, nodesp)) {
        structAddVtableImpl(node, (StructNode *)*nodesp);
    }
}

// Populate the vtable implementation info for a struct ref being coerced to some trait
TypeCompare structVirtRefMatches(StructNode *trait, StructNode *strnode) {

    if (trait->vtable == NULL)
        structMakeVtable(trait);
    Vtable *vtable = trait->vtable;

    // No need to build VtableImpl for this struct if it has already been done earlier
    INode **nodesp;
    uint32_t cnt;
    for (nodesFor(vtable->impl, cnt, nodesp)) {
        VtableImpl *impl = (VtableImpl*)*nodesp;
        if (impl->structdcl == (INode*)strnode)
            return ConvSubtype;
    }

    return structAddVtableImpl(trait, strnode)? ConvSubtype : NoMatch;
}

// Is from-type a subtype of to-struct (we know they are not the same)
// Subtyping is complex on structs for coercions, because we want to avoid the memory management
// messiness of converting struct values from one type to another due to depth/width polymorphism.
//
// The passed constraint establishes the additional subtyping constraints between structs
// - Monomorph accepts any valid subtype, since monomorphization performs no value conversions
// - VirtRef demands that no field require a runtime conversion
// - RegRef demands that supertype's fields appear at start of subtype in order, with no conversion
// - Coercion demands all of the above, plus no added fields
TypeCompare structMatches(StructNode *to, INode *fromdcl, SubtypeConstraint constraint) {
    assert((StructNode*)fromdcl != to);  // We know the types are not equivalent

    // Only a struct may be a subtype of a trait supertype
    if (fromdcl->tag != StructTag || !(to->flags & TraitType))
        return NoMatch;
    StructNode *from = (StructNode*)fromdcl;

    // The virtual reference compare has extra logic for building vtables info
    // So, we just handle it separately
    /*
    if (constraint == Virtref)
        return structVirtRefMatches(to, from);
    */

    // Do the easier check first: Is to-type a base trait of from-type?
    // If so, we know from-type has to-type at its start, making it a great subtype nearly always
    if (to->flags & SameSize) {
        StructNode *super = (StructNode *)fromdcl;
        while (super->basetrait) {
            StructNode *base = (StructNode*)itypeGetTypeDcl(super->basetrait);
            // If it is a valid supertype trait, indicate that it requires coercion
            if (to == base) {
                // With most constraints, a found base trait means we have a valid subtype.
                // However, a non-reference (struct) coercion requires subtype & supertype be the same size
                return constraint == Coercion && !(to->flags & SameSize) ? NoMatch : CastSubtype;
            }
            super = base;
        }
    }
    // If the above test fails, non-ref coercion is not valid
    if (constraint == Coercion)
        return NoMatch;

    // For the Monomorphization & RegRef, we try the slower test
    // of ensuring all methods and fields in supertype are also in subtype
    INode **nodesp;
    uint32_t cnt;
    for (nodelistFor(&to->nodelist, cnt, nodesp)) {
        // Locate the corresponding method with matching name and vtype
        // Note, we need to be flexible in matching the self parameter
        FnDclNode *meth = (FnDclNode *)*nodesp;
        FnDclNode *strmeth = (FnDclNode *)namespaceFind(&from->namespace, meth->namesym);
        if ((strmeth = iNsTypeFindVrefMethod(strmeth, meth)) == NULL)
            return NoMatch;
    }
    // Technique for comparing fields varies ...
    if (constraint == Monomorph) {
        // Monomorphization: Field order is irrelevant. Depth and width subtyping are ok
        for (nodelistFor(&to->fields, cnt, nodesp)) {
            // Find the corresponding field with matching name and vtype
            FieldDclNode *tofld = (FieldDclNode *)*nodesp;
            INode *fromfld = namespaceFind(&from->namespace, tofld->namesym);
            if (fromfld == NULL || fromfld->tag != FieldDclTag) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to %s. Missing field %s.",
                //    &to->namesym->namestr, &to->namesym->namestr, &fld->namesym->namestr);
                return NoMatch;
            }
            TypeCompare match = iexpMatches(&fromfld, tofld->vtype, Monomorph);
            if (match != EqMatch && match != ConvSubtype && match != CastSubtype) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to %s. Incompatible type for field %s.",
                //    &to->namesym->namestr, &to->namesym->namestr, &fld->namesym->namestr);
                return NoMatch;
            }
        }
    }
    else {
        // Regular reference: we need all supertype fields at start of subtype.
        // Width subtyping ok. Depth subtyping only if no field conversions required.
        if (to->fields.used > from->fields.used)
            return NoMatch;
        INode **frmnodesp = &nodelistGet(&from->fields, 0);
        for (nodelistFor(&to->fields, cnt, nodesp)) {
            // Each field of from/to should match (in order) name and vtype
            FieldDclNode *tofld = (FieldDclNode *)*nodesp;
            INode *fromfld = *frmnodesp++;
            if (fromfld == NULL || fromfld->tag != FieldDclTag || ((FieldDclNode*)fromfld)->namesym != tofld->namesym) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to %s. Missing field %s.",
                //    &to->namesym->namestr, &to->namesym->namestr, &fld->namesym->namestr);
                return NoMatch;
            }
            TypeCompare match = iexpMatches(&fromfld, tofld->vtype, Coercion);
            if (match != EqMatch && match != CastSubtype) {
                //errorMsgNode(errnode, ErrorInvType, "%s cannot be coerced to %s. Incompatible type for field %s.",
                //    &to->namesym->namestr, &to->namesym->namestr, &fld->namesym->namestr);
                return NoMatch;
            }
        }
    }

    return CastSubtype;
}

// Return a type that is the supertype of both type nodes, or NULL if none found
INode *structFindSuper(INode *type1, INode *type2) {
    StructNode *typ1 = (StructNode *)itypeGetTypeDcl(type1);
    StructNode *typ2 = (StructNode *)itypeGetTypeDcl(type2);

    // The only supertype supported with structs is they both use the same, same-sized base trait
    if (typ1->basetrait && typ2->basetrait 
        && structGetBaseTrait((StructNode*)itypeGetTypeDcl(typ1->basetrait)) == structGetBaseTrait((StructNode*)itypeGetTypeDcl(typ2->basetrait))
        && (typ1->flags & SameSize))
        return typ1->basetrait;
    return NULL;
}

// Return a type that is the supertype of both type nodes, or NULL if none found
// This is used by reference types, where same-sized is no longer a requirement
INode *structRefFindSuper(INode *type1, INode *type2) {
    StructNode *typ1 = (StructNode *)itypeGetTypeDcl(type1);
    StructNode *typ2 = (StructNode *)itypeGetTypeDcl(type2);

    // The only supertype supported with structs is they both use the same base trait
    if (typ1->basetrait && typ2->basetrait
        && structGetBaseTrait((StructNode*)itypeGetTypeDcl(typ1->basetrait)) == structGetBaseTrait((StructNode*)itypeGetTypeDcl(typ2->basetrait)))
        return typ1->basetrait;
    return NULL;
}
