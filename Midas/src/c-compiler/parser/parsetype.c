/** Parse type signatures
 * @file
 *
 * The parser translates the lexer's tokens into IR nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "parser.h"
#include "../ir/ir.h"
#include "../shared/memory.h"
#include "../shared/error.h"
#include "../ir/nametbl.h"
#include "lexer.h"

#include <stdio.h>
#include <assert.h>

// Parse a permission, return reference to defperm if not found
INode *parsePerm() {
    if (lexIsToken(PermToken)) {
        INode *perm = newPermUseNode((PermNode*)lex->val.ident->node);
        lexNextToken();
        return perm;
    }
    return unknownType;
}

// Parse an allocator + permission for a reference type
void parseAllocPerm(RefNode *refnode) {
    if (lexIsToken(IdentToken)
        && lex->val.ident->node && lex->val.ident->node->tag == RegionTag) {
        refnode->region = (INode*)lex->val.ident->node;
        lexNextToken();
    }
    else {
        refnode->region = borrowRef;
    }
    refnode->perm = parsePerm();
}

// Parse a variable declaration
VarDclNode *parseVarDcl(ParseState *parse, PermNode *defperm, uint16_t flags) {
    VarDclNode *varnode;
    INode *perm;

    // Grab the permission type
    perm = parsePerm();
    INode *permdcl = perm==unknownType? unknownType : itypeGetTypeDcl(perm);
    if (permdcl == (INode*)mut1Perm || permdcl == (INode*)uniPerm || permdcl == (INode*)opaqPerm
        || (permdcl == (INode*)constPerm && !(flags & ParseMayConst)))
        errorMsgNode(perm, ErrorInvType, "Permission not valid for variable/field declaration");

    // Obtain variable's name
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorNoIdent, "Expected variable name for declaration");
        return newVarDclFull(anonName, VarDclTag, unknownType, perm, NULL);
    }
    varnode = newVarDclNode(lex->val.ident, VarDclTag, perm);
    lexNextToken();

    // Get value type, if provided
    varnode->vtype = parseVtype(parse);

    // Get initialization value after '=', if provided
    if (lexIsToken(AssgnToken)) {
        if (!(flags&ParseMayImpl))
            errorMsgLex(ErrorBadImpl, "A default/initial value may not be specified here.");
        lexNextToken();
        varnode->value = parseAnyExpr(parse);
    }
    else {
        if (!(flags&ParseMaySig))
            errorMsgLex(ErrorNoInit, "Must specify default/initial value.");
    }

    return varnode;
}

INode *parseTypeName(ParseState *parse) {
    INode *node = parseNameUse(parse);
    if (lexIsToken(LBracketToken)) {
        FnCallNode *fncall = newFnCallNode(node, 8);
        fncall->flags |= FlagIndex;
        lexNextToken();
        lexIncrParens();
        if (!lexIsToken(RBracketToken)) {
            nodesAdd(&fncall->args, parseVtype(parse));
            while (lexIsToken(CommaToken)) {
                lexNextToken();
                nodesAdd(&fncall->args, parseVtype(parse));
            }
        }
        parseCloseTok(RBracketToken);
        node = (INode *)fncall;
    }
    return node;
}

// Parse an enum type
INode* parseEnum(ParseState *parse) {
    EnumNode *node = newEnumNode();
    lexNextToken();
    return (INode*)node;
}

// Parse a field declaration
FieldDclNode *parseFieldDcl(ParseState *parse, PermNode *defperm) {
    FieldDclNode *fldnode;
    INode *vtype;
    INode *perm;

    // Grab the permission type
    perm = parsePerm();
    INode *permdcl = perm == unknownType? unknownType : itypeGetTypeDcl(perm);
    if (permdcl != (INode*)mutPerm && permdcl == (INode*)immPerm)
        errorMsgNode(perm, ErrorInvType, "Permission not valid for field declaration");

    // Obtain variable's name
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorNoIdent, "Expected field name for declaration");
        return newFieldDclNode(anonName, perm);
    }
    fldnode = newFieldDclNode(lex->val.ident, perm);
    lexNextToken();

    // Get value type, if provided
    if (lexIsToken(EnumToken))
        fldnode->vtype = parseEnum(parse);
    else if ((vtype = parseVtype(parse)))
        fldnode->vtype = vtype;

    // Get initialization value after '=', if provided
    if (lexIsToken(AssgnToken)) {
        lexNextToken();
        fldnode->value = parseAnyExpr(parse);
    }

    return fldnode;
}

// Parse a struct
INode *parseStruct(ParseState *parse, uint16_t strflags) {
    char *svprefix = parse->gennamePrefix;
    INsTypeNode *svtype = parse->typenode;
    GenericNode *genericnode = NULL;
    StructNode *strnode;
    uint16_t fieldnbr = 0;

    // Capture the kind of type, then get next token (name)
    uint16_t tag = StructTag;
    lexNextToken();

    // Handle attributes
    if (lex->toktype == SamesizeToken) {
        if (strflags & TraitType)
            strflags |= SameSize;
        else
            errorMsgLex(ErrorNoIdent, "@samesize attribute only allowed on traits.");
        lexNextToken();
    }
    // A non-same sized trait is essentially opaque
    if ((strflags & TraitType) && !(strflags & SameSize))
        strflags |= OpaqueType;

    // Process struct type name, if provided
    if (lexIsToken(IdentToken)) {
        strnode = newStructNode(lex->val.ident);
        strnode->tag = tag;
        strnode->flags |= strflags;
        strnode->mod = parse->mod;
        nameConcatPrefix(&parse->gennamePrefix, &strnode->namesym->namestr);
        parse->typenode = (INsTypeNode *)strnode;
        lexNextToken();
    }
    else {
        errorMsgLex(ErrorNoIdent, "Expected a name for the type");
        return NULL;
    }

    uint16_t methflags = ParseMayName | ParseMayImpl;
    if (strnode->flags & TraitType)
        methflags |= ParseMaySig;

    // Handle if generic parameters are found
    if (lexIsToken(LBracketToken)) {
        genericnode = newGenericDclNode(strnode->namesym);
        parseGenericVars(parse, genericnode);
        genericnode->body = (INode*)strnode;
    }

    // Obtain base trait, if specified
    if (lexIsToken(ExtendsToken)) {
        lexNextToken();
        strnode->basetrait = parseTypeName(parse);  // Type could be a qualified name or generic
    }

    // If block has been provided, process field or method definitions
    // If not, we have an opaque struct!
    if (parseHasBlock()) {
        parseBlockStart();
        while (!parseBlockEnd()) {
            lexStmtStart();
            if (lexIsToken(SetToken)) {
                lexNextToken();
                if (!lexIsToken(FnToken))
                    errorMsgLex(ErrorNotFn, "Expected fn declaration");
                else {
                    FnDclNode *fn = (FnDclNode*)parseFn(parse, FlagMethFld, methflags);
                    if (fn && isNamedNode(fn)) {
                        fn->flags |= FlagSetMethod;
                        nameGenFnName(fn, parse->gennamePrefix);
                        iNsTypeAddFn((INsTypeNode*)strnode, fn);
                    }
                }
            }
            else if (lexIsToken(FnToken)) {
                FnDclNode *fn = (FnDclNode*)parseFn(parse, FlagMethFld, methflags);
                if (fn && isNamedNode(fn)) {
                    nameGenFnName(fn, parse->gennamePrefix);
                    iNsTypeAddFn((INsTypeNode*)strnode, fn);
                }
            }
            else if (lexIsToken(MixinToken)) {
                // Handle a trait mixin, capturing it in a field-like node
                FieldDclNode *field = newFieldDclNode(anonName, (INode*)immPerm);
                field->flags |= IsMixin | FlagMethFld;
                lexNextToken();
                INode *vtype;
                if ((vtype = parseVtype(parse)))
                    field->vtype = vtype;
                structAddField(strnode, field);
                parseEndOfStatement();
            }
            else if (lexIsToken(PermToken) || lexIsToken(IdentToken)) {
                FieldDclNode *field = parseFieldDcl(parse, mutPerm);
                field->index = fieldnbr++;
                field->flags |= FlagMethFld;
                structAddField(strnode, field);
                parseEndOfStatement();
            }
            else {
                errorMsgLex(ErrorNoSemi, "Unknown struct statement.");
                parseSkipToNextStmt();
            }
        }
    }
    else
        parseEndOfStatement();

    parse->typenode = svtype;
    parse->gennamePrefix = svprefix;
    return genericnode? (INode*)genericnode : (INode*)strnode;
}

void parseInjectSelf(FnSigNode *fnsig) {
    NameUseNode *selftype = newNameUseNode(selfTypeName);
    VarDclNode *selfparm = newVarDclFull(nametblFind("self", 4), VarDclTag, (INode*)selftype, newPermUseNode(constPerm), NULL);
    selfparm->scope = 1;
    selfparm->index = 0;
    nodesAdd(&fnsig->parms, (INode*)selfparm);
}

// Parse a function's type signature
INode *parseFnSig(ParseState *parse) {
    FnSigNode *fnsig;
    uint16_t parmnbr = 0;
    uint16_t parseflags = ParseMaySig | ParseMayImpl;

    // Set up memory block for the function's type signature
    fnsig = newFnSigNode();

    // Process parameter declarations
    if (lexIsToken(LParenToken)) {
        lexNextToken();
        // A type's method with no parameters should still define self
        if (lexIsToken(RParenToken) && parse->typenode)
            parseInjectSelf(fnsig);
        while (lexIsToken(PermToken) || lexIsToken(IdentToken)) {
            VarDclNode *parm = parseVarDcl(parse, immPerm, parseflags);
            // Do special inference if function is a type's method
            if (parse->typenode) {
                // Create default self parm, if 'self' was not specified
                if (parmnbr == 0 && parm->namesym != selfName) {
                    parseInjectSelf(fnsig);
                    ++parmnbr;
                }
                // Infer value type of a parameter (or its reference) if unspecified
                if (parm->vtype == unknownType) {
                    parm->vtype = (INode*)newNameUseNode(selfTypeName);
                }
                else if (parm->vtype->tag == RefTag) {
                    RefNode *refnode = (RefNode *)parm->vtype;
                    if (refnode->vtexp == unknownType) {
                        refnode->vtexp = (INode*)newNameUseNode(selfTypeName);
                    }
                }
            }
            // Add parameter to function's parm list
            parm->scope = 1;
            parm->index = parmnbr++;
            if (parm->value)
                parseflags = ParseMayImpl; // force remaining parms to specify default
            nodesAdd(&fnsig->parms, (INode*)parm);
            if (!lexIsToken(CommaToken))
                break;
            lexNextToken();
        }
        parseCloseTok(RParenToken);
    }
    else
        errorMsgLex(ErrorNoLParen, "Expected left parenthesis for parameter declarations");

    // Parse return type info - turn into void if none specified
    if ((fnsig->rettype = parseVtype(parse)) != unknownType) {
        // Handle multiple return types
        if (lexIsToken(CommaToken)) {
            TupleNode *rettype = newTupleNode(4);
            nodesAdd(&rettype->elems, fnsig->rettype);
            while (lexIsToken(CommaToken)) {
                lexNextToken();
                nodesAdd(&rettype->elems, parseVtype(parse));
            }
            fnsig->rettype = (INode*)rettype;
        }
    }
    else {
        fnsig->rettype = (INode*)newVoidNode();
        inodeLexCopy(fnsig->rettype, (INode*)fnsig);  // Make invisible void show up in error msg
    }

    return (INode*)fnsig;
}

// Parse a typedef statement
TypedefNode *parseTypedef(ParseState *parse) {
    lexNextToken();
    // Process struct type name, if provided
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorNoIdent, "Expected a name for the type");
        return NULL;
    }
    TypedefNode *newnode = newTypedefNode(lex->val.ident);
    lexNextToken();
    newnode->typeval = parseVtype(parse);
    parseEndOfStatement();
    return newnode;
}

// Parse a type expression. Return unknownType if none found.
INode* parseVtype(ParseState *parse) {
    // This is a placeholder since parser converges type and value expression parsing
    switch (lex->toktype) {
    case QuesToken:
    case AmperToken:
    case ArrayRefToken:
    case VirtRefToken:
    case StarToken:
    case LBracketToken:
    case IdentToken:
        return parsePrefix(parse, 0);
    default:
        return unknownType;
    }
}
