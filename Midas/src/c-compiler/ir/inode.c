/** Generic node handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"
#include "../parser/lexer.h"
#include "../shared/fileio.h"
#include "../shared/error.h"

#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <assert.h>

// Copy lexer info over
void inodeLexCopy(INode *new, INode *old) {
    new->lexer = old->lexer;
    new->linenbr = old->linenbr;
    new->linep = old->linep;
    new->srcp = old->srcp;
}

// State for inodePrint
FILE *irfile;
int irIndent=0;
int irIsNL = 1;

// Output a string to irfile
void inodeFprint(char *str, ...) {
    va_list argptr;
    va_start(argptr, str);
    vfprintf(irfile, str, argptr);
    va_end(argptr);
    irIsNL = 0;
}

// Print new line character
void inodePrintNL() {
    if (!irIsNL)
        fputc('\n', irfile);
    irIsNL = 1;
}

// Output a line's beginning indentation
void inodePrintIndent() {
    int cnt;
    for (cnt = 0; cnt<irIndent; cnt++)
        fprintf(irfile, (cnt & 3) == 0 ? "| " : "  ");
    irIsNL = 0;
}

// Increment indentation
void inodePrintIncr() {
    irIndent++;
}

// Decrement indentation
void inodePrintDecr() {
    irIndent--;
}

// Serialize a specific node
void inodePrintNode(INode *node) {
    switch (node->tag) {
    case ModuleTag:
        modPrint((ModuleNode *)node); break;
    case NameUseTag:
    case VarNameUseTag:
    case TypeNameUseTag:
    case MbrNameUseTag:
        nameUsePrint((NameUseNode *)node); break;
    case FnDclTag:
        fnDclPrint((FnDclNode *)node); break;
    case VarDclTag:
        varDclPrint((VarDclNode *)node); break;
    case FieldDclTag:
        fieldDclPrint((FieldDclNode *)node); break;
    case BlockTag:
        blockPrint((BlockNode *)node); break;
    case IfTag:
        ifPrint((IfNode *)node); break;
    case LoopTag:
        loopPrint((LoopNode *)node); break;
    case BreakTag:
        inodeFprint("break"); break;
    case ContinueTag:
        inodeFprint("continue"); break;
    case BlockRetTag:
    case ReturnTag:
        returnPrint((ReturnNode *)node); break;
    case AssignTag:
        assignPrint((AssignNode *)node); break;
    case VTupleTag:
        vtuplePrint((TupleNode *)node); break;
    case FnCallTag:
    case FldAccessTag:
    case ArrIndexTag:
        fnCallPrint((FnCallNode *)node); break;
    case SizeofTag:
        sizeofPrint((SizeofNode *)node); break;
    case CastTag:  case IsTag:
        castPrint((CastNode *)node); break;
    case DerefTag:
        derefPrint((StarNode *)node); break;
    case ArrayBorrowTag:
    case BorrowTag:
        borrowPrint((RefNode *)node); break;
    case ArrayAllocTag:
    case AllocateTag:
        allocatePrint((RefNode *)node); break;
    case NotLogicTag: case OrLogicTag: case AndLogicTag:
        logicPrint((LogicNode *)node); break;
    case ULitTag:
        ulitPrint((ULitNode *)node); break;
    case FLitTag:
        flitPrint((FLitNode *)node); break;
    case TypeLitTag:
        typeLitPrint((FnCallNode *)node); break;
    case StringLitTag:
        slitPrint((SLitNode *)node); break;
    case TypedefTag:
        typedefPrint((TypedefNode *)node); break;
    case FnSigTag:
        fnSigPrint((FnSigNode *)node); break;
    case RefTag: case VirtRefTag:
        refPrint((RefNode *)node); break;
    case ArrayRefTag:
        arrayRefPrint((RefNode *)node); break;
    case PtrTag:
        ptrPrint((StarNode *)node); break;
    case StructTag:
    case RegionTag:
        structPrint((StructNode *)node); break;
    case EnumTag:
        enumPrint((EnumNode *)node); break;
    case ArrayTag:
        arrayPrint((ArrayNode *)node); break;
    case IntNbrTag: case UintNbrTag: case FloatNbrTag:
        nbrTypePrint((NbrNode *)node); break;
    case PermTag:
        permPrint((PermNode *)node); break;
    case LifetimeTag:
        lifePrint((LifetimeNode *)node); break;
    case TTupleTag:
        ttuplePrint((TupleNode *)node); break;
    case AbsenceTag:
    case UnknownTag:
    case VoidTag:
        voidPrint((VoidTypeNode *)node); break;
    case NamedValTag:
        namedValPrint((NamedValNode *)node); break;
    case AliasTag:
    {
        AliasNode *anode = (AliasNode *)node;
        inodeFprint("(alias ");
        if (anode->counts == NULL)
            inodeFprint("%d ", (int)anode->aliasamt);
        else {
            int16_t count = anode->aliasamt;
            int16_t *countp = anode->counts;
            while (count--)
                inodeFprint("%d ", (int)*countp++);
        }
        inodePrintNode(anode->exp);
        inodeFprint(")");
        break;
    }
    case MacroDclTag:
    case GenericDclTag:
        genericPrint((GenericNode *)node); break;
    case GenVarDclTag:
        gVarDclPrint((GenVarDclNode *)node); break;

    default:
        inodeFprint("**** UNKNOWN NODE ****");
    }
}

// Serialize the program's IR to dir+srcfn
void inodePrint(char *dir, char *srcfn, INode *pgmnode) {
    irfile = fopen(fileMakePath(dir, pgmnode->lexer->fname, "ast"), "wb");
    inodePrintNode(pgmnode);
    fclose(irfile);
}

// Dispatch a node walk for the current semantic analysis pass
// - pstate is helpful state info for node traversal
// - node is a pointer to pointer so that a node can be replaced
void inodeNameRes(NameResState *pstate, INode **node) {
    switch ((*node)->tag) {
    case ModuleTag:
        modNameRes(pstate, (ModuleNode*)*node); break;
    case FnDclTag:
        fnDclNameRes(pstate, (FnDclNode *)*node); break;
    case VarDclTag:
        varDclNameRes(pstate, (VarDclNode *)*node); break;
    case FieldDclTag:
        fieldDclNameRes(pstate, (FieldDclNode *)*node); break;
    case NameUseTag:
    case VarNameUseTag:
    case TypeNameUseTag:
    case MacroNameTag:
    case GenericNameTag:
        nameUseNameRes(pstate, (NameUseNode **)node); break;
    case TypeLitTag:
        typeLitNameRes(pstate, (FnCallNode *)*node); break;
    case BlockTag:
        blockNameRes(pstate, (BlockNode *)*node); break;
    case IfTag:
        ifNameRes(pstate, (IfNode *)*node); break;
    case LoopTag:
        loopNameRes(pstate, (LoopNode *)*node); break;
    case BreakTag:
        breakNameRes(pstate, (BreakNode *)*node); break;
    case ContinueTag:
        continueNameRes(pstate, (ContinueNode *)*node); break;
    case ReturnTag:
        returnNameRes(pstate, (ReturnNode *)*node); break;
    case AssignTag:
        assignNameRes(pstate, (AssignNode *)*node); break;
    case FnCallTag:
        fnCallNameRes(pstate, (FnCallNode **)node); break;
    case SizeofTag:
        sizeofNameRes(pstate, (SizeofNode *)*node); break;
    case CastTag:  case IsTag:
        castNameRes(pstate, (CastNode *)*node); break;
    case NotLogicTag:
        logicNotNameRes(pstate, (LogicNode *)*node); break;
    case OrLogicTag: case AndLogicTag:
        logicNameRes(pstate, (LogicNode *)*node); break;
    case NamedValTag:
        namedValNameRes(pstate, (NamedValNode *)*node); break;
    case ULitTag:
    case FLitTag:
    case StringLitTag:
        litNameRes(pstate, (IExpNode *)*node); break;

    case TypedefTag:
        typedefNameRes(pstate, (TypedefNode *)*node); break;
    case FnSigTag:
        fnSigNameRes(pstate, (FnSigNode *)*node); break;
    case RefTag:
    case VirtRefTag:
        refNameRes(pstate, (RefNode *)*node); break;
    case ArrayRefTag:
        arrayRefNameRes(pstate, (RefNode *)*node); break;
    case StarTag:
        ptrNameRes(pstate, (StarNode *)*node); break;
    case StructTag:
        structNameRes(pstate, (StructNode *)*node); break;
    case EnumTag:
        enumNameRes(pstate, (EnumNode *)*node); break;
    case ArrayTag:
        arrayNameRes(pstate, (ArrayNode *)*node); break;
    case TupleTag:
        ttupleNameRes(pstate, (TupleNode *)*node); break;
    case BorrowRegTag:
    case RegionTag:
        break;

    case MacroDclTag:
    case GenericDclTag:
        genericNameRes(pstate, (GenericNode *)*node); break;
    case GenVarDclTag:
        gVarDclNameRes(pstate, (GenVarDclNode *)*node); break;

    case MbrNameUseTag:
    case IntNbrTag: case UintNbrTag: case FloatNbrTag:
    case PermTag:
    case AbsenceTag:
    case UnknownTag:
    case VoidTag:
        break;
    default:
        assert(0 && "**** ERROR **** Attempting to name resolve an unknown node");
    }
}

// Dispatch a node walk for the type check pass
// - pstate is helpful state info for node traversal
// - node is a pointer to pointer so that a node can be replaced
// - expectType is the type expected of an expression node (or unknownType/noCareType)
void inodeTypeCheck(TypeCheckState *pstate, INode **node, INode *expectType) {

    // Type nodes are fully checked the first time they are referenced,
    // so that we know everything we need to know about correctly managing their values
    // (because of needing to infer infectious type constraints based on their composed fields).
    // However, type checking of a type node should only be done once
    if (isTypeNode(*node)) {
        if ((*node)->flags & TypeChecked)
            return;
        if ((*node)->flags & TypeChecking) {
            errorMsgNode(*node, ErrorRecurse, "Recursive types are not supported for now.");
            return;
        }
        else
            (*node)->flags |= TypeChecking;
    }

    switch ((*node)->tag) {
    case ModuleTag:
        modTypeCheck(pstate, (ModuleNode*)*node); break;
    case FnDclTag:
        fnDclTypeCheck(pstate, (FnDclNode *)*node); break;
    case VarDclTag:
        varDclTypeCheck(pstate, (VarDclNode *)*node); break;
    case FieldDclTag:
        fieldDclTypeCheck(pstate, (FieldDclNode *)*node); break;
    case VarNameUseTag:
        nameUseTypeCheck(pstate, (NameUseNode **)node); break;
    case ArrayLitTag:
        typeLitArrayCheck(pstate, (ArrayNode *)*node); break;
    case BlockTag:
        blockTypeCheck(pstate, (BlockNode *)*node, expectType); break;
    case IfTag:
        ifTypeCheck(pstate, (IfNode *)*node, expectType); break;
    case LoopTag:
        loopTypeCheck(pstate, (LoopNode *)*node, expectType); break;
    case BreakTag:
        breakTypeCheck(pstate, (BreakNode *)*node); break;
    case ContinueTag:
        continueTypeCheck(pstate, (ContinueNode *)*node); break;
    case ReturnTag:
        returnTypeCheck(pstate, (ReturnNode *)*node); break;
    case AssignTag:
        assignTypeCheck(pstate, (AssignNode *)*node); break;
    case VTupleTag:
        vtupleTypeCheck(pstate, (TupleNode *)*node); break;
    case FnCallTag:
        fnCallTypeCheck(pstate, (FnCallNode **)node); break;
    case SizeofTag:
        sizeofTypeCheck(pstate, (SizeofNode *)*node); break;
    case CastTag:
        castTypeCheck(pstate, (CastNode *)*node); break;
    case DerefTag:
        derefTypeCheck(pstate, (StarNode *)*node); break;
    case ArrayBorrowTag:
    case BorrowTag:
        borrowTypeCheck(pstate, (RefNode **)node); break;
    case AllocateTag:
        allocateTypeCheck(pstate, (RefNode **)node); break;
    case NotLogicTag:
        logicNotTypeCheck(pstate, (LogicNode *)*node); break;
    case OrLogicTag: case AndLogicTag:
        logicTypeCheck(pstate, (LogicNode *)*node); break;
    case IsTag:
        castIsTypeCheck(pstate, (CastNode *)*node); break;
    case NamedValTag:
        namedValTypeCheck(pstate, (NamedValNode *)*node); break;
    case ULitTag:
    case FLitTag:
        litTypeCheck(pstate, (IExpNode*)*node, expectType); break;

    case TypeNameUseTag:
        nameUseTypeCheckType(pstate, (NameUseNode **)node); break;
    case TypedefTag:
        typedefTypeCheck(pstate, (TypedefNode *)*node); break;
    case FnSigTag:
        fnSigTypeCheck(pstate, (FnSigNode *)*node); break;
    case RefTag:
        refTypeCheck(pstate, (RefNode *)*node); break;
    case VirtRefTag:
        refvirtTypeCheck(pstate, (RefNode *)*node); break;
    case ArrayRefTag:
        arrayRefTypeCheck(pstate, (RefNode *)*node); break;
    case PtrTag:
        ptrTypeCheck(pstate, (StarNode *)*node); break;
    case StructTag:
        structTypeCheck(pstate, (StructNode *)*node); break;
    case EnumTag:
        enumTypeCheck(pstate, (EnumNode *)*node); break;
    case ArrayTag:
        arrayTypeCheck(pstate, (ArrayNode *)*node); break;
    case TTupleTag:
        ttupleTypeCheck(pstate, (TupleNode *)*node); break;
    case BorrowRegTag:
    case RegionTag:
    case PermTag:
        break;

    case MacroDclTag:
    case GenericDclTag:
        genericTypeCheck(pstate, (GenericNode *)*node); break;
    case MacroNameTag:
    case GenericNameTag:
        genericNameTypeCheck(pstate, (NameUseNode **)node); break;
    case GenVarDclTag:
        gVarDclTypeCheck(pstate, (GenVarDclNode *)*node); break;

    case StringLitTag:
        slitTypeCheck(pstate, (SLitNode*)*node); break;

    case MbrNameUseTag:
    case IntNbrTag: case UintNbrTag: case FloatNbrTag:
    case AbsenceTag:
    case UnknownTag:
    case VoidTag:
        break;
    default:
        assert(0 && "**** ERROR **** Attempting to check an unknown node");
    }

    // Confirm when a type node has been checked
    if (isTypeNode(*node)) {
        (*node)->flags |= TypeChecked;
    }
}


// Perform a node walk for the current semantic analysis pass (w/ no type expected)
// - pstate is helpful state info for node traversal
// - node is a pointer to pointer so that a node can be replaced
void inodeTypeCheckAny(TypeCheckState *pstate, INode **pgm) {
    inodeTypeCheck(pstate, pgm, unknownType);
}

// Obtain name from a named node
Name *inodeGetName(INode *node) {
    switch (node->tag) {
    case GenericDclTag:
        return ((GenericNode *)node)->namesym;
    case StructTag:
        return ((StructNode *)node)->namesym;
    default:
        assert(0 && "Unknown node to get name from");
        return NULL;
    }
}
