/** Parse executable statements and blocks
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

INode *parseEach(ParseState *parse, INode *blk, LifetimeNode *life);

// This helper routine inserts 'break if !condexp' at beginning of block
void parseInsertWhileBreak(INode *blk, INode *condexp) {
    INode *breaknode = (INode*)newBreakNode();
    BlockNode *ifblk = newBlockNode();
    nodesAdd(&ifblk->stmts, breaknode);
    LogicNode *notiter = newLogicNode(NotLogicTag);
    notiter->lexp = condexp;
    IfNode *ifnode = newIfNode();
    nodesAdd(&ifnode->condblk, (INode *)notiter);
    nodesAdd(&ifnode->condblk, (INode *)ifblk);
    nodesInsert(&((BlockNode*)blk)->stmts, (INode*)ifnode, 0);
}

// Parse an expression statement within a function
INode *parseExpStmt(ParseState *parse) {
    INode *node = parseAnyExpr(parse);
    parseEndOfStatement();
    return node;
}

// Parse a return statement
INode *parseReturn(ParseState *parse) {
    ReturnNode *stmtnode = newReturnNode();
    lexNextToken(); // Skip past 'return'
    if (!parseIsEndOfStatement())
        stmtnode->exp = parseAnyExpr(parse);
    parseEndOfStatement();
    return (INode*)stmtnode;
}

// Parses a variable bound to a pattern match on a value
// (it looks like, and is returned as, a variable declaration)
VarDclNode *parseBindVarDcl(ParseState *parse) {
    INode *perm = parsePerm();
    INode *permdcl = perm==unknownType? unknownType : itypeGetTypeDcl(perm);
    if (permdcl != (INode*)mutPerm && permdcl != (INode*)immPerm)
        errorMsgNode(perm, ErrorInvType, "Permission not valid for pattern match binding");

    // Obtain variable's name
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorNoIdent, "Expected variable name for declaration");
        return newVarDclFull(anonName, VarDclTag, unknownType, perm, NULL);
    }
    VarDclNode *varnode = newVarDclNode(lex->val.ident, VarDclTag, perm);
    lexNextToken();

    // Get type
    INode *vtype;
    if ((vtype = parseVtype(parse)) != unknownType)
        varnode->vtype = vtype;
    else {
        errorMsgLex(ErrorInvType, "Expected type specification for pattern match binding");
        varnode->vtype = unknownType;
    }

    return varnode;
}

// De-sugar a variable bound pattern match
void parseBoundMatch(ParseState *parse, IfNode *ifnode, NameUseNode *expnamenode, VarDclNode *valnode) {
    // We will desugar a variable declaration into using a pattern match and re-cast
    CastNode *isnode = newIsNode((INode*)expnamenode, unknownType);
    CastNode *castnode = newConvCastNode((INode*)expnamenode, unknownType);

    // Parse the variable-bind into a vardcl,
    // then preserve its desired type into both the 'is' and 'cast' nodes
    VarDclNode *varnode = parseBindVarDcl(parse);
    isnode->typ = castnode->typ = castnode->vtype = varnode->vtype;
    varnode->value = (INode *)castnode;
    nodesAdd(&ifnode->condblk, (INode*)isnode);

    // If value expression is needed, obtain it also
    if (valnode != NULL) {
        if (lexIsToken(AssgnToken))
            lexNextToken();
        else {
            errorMsgLex(ErrorInvType, "Expected '=' followed by value to match against");
        }
        valnode->value = parseSimpleExpr(parse);
    }

    // Create and-then block, with vardcl injected at start
    BlockNode *blknode = (BlockNode*)parseExprBlock(parse);
    nodesInsert(&blknode->stmts, (INode*)varnode, 0); // Inject vardcl at start of block
    nodesAdd(&ifnode->condblk, (INode*)blknode);
}

// Parse if statement/expression
INode *parseIf(ParseState *parse) {
    IfNode *ifnode = newIfNode();
    INode *retnode = (INode*)ifnode;
    lexNextToken();
    // To handle bound pattern match, we need to de-sugar:
    // - 'if' is wrapped in a block, where we first capture the value in a variable
    // - The conditional turns into an 'is' check
    // - The first statement in the block actually binds the var to the re-cast value
    if (lexIsToken(PermToken)) {
        BlockNode *blknode = newBlockNode();
        VarDclNode *valnode = newVarDclFull(anonName, VarDclTag, unknownType, (INode*)immPerm, NULL);
        NameUseNode *valnamenode = newNameUseNode(anonName);
        valnamenode->tag = VarNameUseTag;
        valnamenode->dclnode = (INode*)valnode;
        nodesAdd(&blknode->stmts, (INode*)valnode);
        nodesAdd(&blknode->stmts, (INode*)ifnode);
        retnode = (INode*)blknode; // return block instead of 'if'!
        parseBoundMatch(parse, ifnode, valnamenode, valnode);
    }
    else {
        nodesAdd(&ifnode->condblk, parseSimpleExpr(parse));
        nodesAdd(&ifnode->condblk, parseExprBlock(parse));
    }
    while (1) {
        // Process final else clause and break loop
        // Note: this code makes "else if" equivalent to "elif"
        if (lexIsToken(ElseToken)) {
            lexNextToken();
            if (!lexIsToken(IfToken)) {
                nodesAdd(&ifnode->condblk, elseCond); // else distinguished by a elseCond
                nodesAdd(&ifnode->condblk, parseExprBlock(parse));
                break;
            }
        }
        else if (!lexIsToken(ElifToken))
            break;

        // Elif processing
        lexNextToken();
        // To handle bound pattern match, we need to de-sugar:
        // - 'if' is wrapped in a block, where we first capture the value in a variable
        // - The conditional turns into an 'is' check
        // - The first statement in the block actually binds the var to the re-cast value
        if (lexIsToken(PermToken)) {
            BlockNode *blknode = newBlockNode();
            nodesAdd(&ifnode->condblk, elseCond);
            nodesAdd(&ifnode->condblk, (INode*)blknode);
            VarDclNode *valnode = newVarDclFull(anonName, VarDclTag, unknownType, (INode*)immPerm, NULL);
            NameUseNode *valnamenode = newNameUseNode(anonName);
            valnamenode->tag = VarNameUseTag;
            valnamenode->dclnode = (INode*)valnode;
            nodesAdd(&blknode->stmts, (INode*)valnode);
            ifnode = newIfNode();
            nodesAdd(&blknode->stmts, (INode*)ifnode);
            parseBoundMatch(parse, ifnode, valnamenode, valnode);
        }
        else {
            nodesAdd(&ifnode->condblk, parseSimpleExpr(parse));
            nodesAdd(&ifnode->condblk, parseExprBlock(parse));
        }
    }
    return retnode;
}

// Parse match expression, which is sugar translated to an 'if' block
INode *parseMatch(ParseState *parse) {
    // 'match' is de-sugared into a block:
    // - vardcl that capture the expression in a variable
    // - if .. elif .. else sequence for all the match cases
    BlockNode *blknode = newBlockNode();
    IfNode *ifnode = newIfNode();

    // Pick up the expression in a variable, then start the block
    lexNextToken();
    VarDclNode *expdclnode = newVarDclNode(anonName, VarDclTag, (INode*)immPerm);
    NameUseNode *expnamenode = newNameUseNode(anonName);
    expnamenode->tag = VarNameUseTag;
    expnamenode->dclnode = (INode*)expdclnode;
    expdclnode->value = parseSimpleExpr(parse);

    // Parse all cases
    parseBlockStart();
    while (!parseBlockEnd()) {
        lexStmtStart();
        if (lexIsToken(PermToken)) {
            parseBoundMatch(parse, ifnode, expnamenode, NULL);
        }
        else if (lexIsToken(IsToken)) {
            CastNode *isnode = newIsNode((INode*)expnamenode, unknownType);
            lexNextToken();
            isnode->typ = parseVtype(parse);
            nodesAdd(&ifnode->condblk, (INode*)isnode);
            nodesAdd(&ifnode->condblk, parseExprBlock(parse));
        }
        else if (lexIsToken(EqToken)) {
            FnCallNode *callnode = newFnCallOp((INode*)expnamenode, "==", 2);
            lexNextToken();
            nodesAdd(&callnode->args, parseSimpleExpr(parse));
            nodesAdd(&ifnode->condblk, (INode*)callnode);
            nodesAdd(&ifnode->condblk, parseExprBlock(parse));
        }
        else if (lexIsToken(ElseToken)) {
            lexNextToken();
            nodesAdd(&ifnode->condblk, elseCond); // else distinguished by a elseCond condition
            nodesAdd(&ifnode->condblk, parseExprBlock(parse));
        }
        else {
            nodesAdd(&ifnode->condblk, parseSimpleExpr(parse));
            nodesAdd(&ifnode->condblk, parseExprBlock(parse));
        }
    }

    nodesAdd(&blknode->stmts, (INode*)expdclnode);
    nodesAdd(&blknode->stmts, (INode*)ifnode);
    return (INode *)blknode;
}

// Parse loop block
INode *parseLoop(ParseState *parse, LifetimeNode *life) {
    LoopNode *loopnode = newLoopNode();
    loopnode->life = life;
    lexNextToken();
    loopnode->blk = parseExprBlock(parse);
    return (INode *)loopnode;
}

// Parse while block
INode *parseWhile(ParseState *parse, LifetimeNode *life) {
    LoopNode *loopnode = newLoopNode();
    loopnode->life = life;
    lexNextToken();
    INode *condexp = parseSimpleExpr(parse);
    INode *blk = loopnode->blk = parseExprBlock(parse);
    parseInsertWhileBreak(blk, condexp);
    return (INode *)loopnode;
}

// Parse each block
INode *parseEach(ParseState *parse, INode *innerblk, LifetimeNode *life) {
    BlockNode *outerblk = newBlockNode();   // surrounding block scope for isolating 'each' vars
    LoopNode *loopnode = newLoopNode();
    loopnode->life = life;

    // Obtain all the parsed pieces
    lexNextToken();
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorNoVar, "Missing variable name");
        return (INode *)outerblk;
    }
    Name* elemname = lex->val.ident;
    lexNextToken();
    if (!lexIsToken(InToken)) {
        errorMsgLex(ErrorBadTok, "Missing 'in'");
        return (INode *)outerblk;
    }
    lexNextToken();
    INode *iter = parseSimpleExpr(parse);
    INode *step = NULL;
    int isrange = 0;
    if (iter->tag == FnCallTag && ((FnCallNode*)iter)->methfld) {
        Name *methodnm = ((NameUseNode*)((FnCallNode*)iter)->methfld)->namesym;
        if (methodnm == leName || methodnm == ltName)
            isrange = 1;
        else if (methodnm == geName || methodnm == gtName)
            isrange = -1;
    }
    if (isrange && lexIsToken(ByToken)) {
        lexNextToken();
        step = parseSimpleExpr(parse);
    }
    if (innerblk == NULL)
        innerblk = parseExprBlock(parse);
    loopnode->blk = innerblk;

    // Assemble logic for a range (with optional step), e.g.:
    // { mut elemname = initial; while elemname <= iterend { ... ; elemname += step}}
    if (isrange) {
        FnCallNode *itercmp = (FnCallNode *)iter;
        VarDclNode *elemdcl = newVarDclNode(elemname, VarDclTag, (INode*)mutPerm);
        elemdcl->value = itercmp->objfn;
        nodesAdd(&((BlockNode*)outerblk)->stmts, (INode*)elemdcl);
        itercmp->objfn = (INode*)newNameUseNode(elemname);
        if (step) {
            FnCallNode *pluseq = newFnCallOpname((INode*)newNameUseNode(elemname), plusEqName, 1);
            pluseq->flags |= FlagOpAssgn | FlagLvalOp;
            nodesAdd(&pluseq->args, step);
            nodesAdd(&((BlockNode*)loopnode->blk)->stmts, (INode*)pluseq);
        }
        else {
            INode *incr = (INode *)newFnCallOpname((INode *)newNameUseNode(elemname), isrange > 0 ? incrPostName : decrPostName, 0);
            incr->flags |= FlagLvalOp;
            nodesAdd(&((BlockNode*)loopnode->blk)->stmts, incr);
        }
        parseInsertWhileBreak(innerblk, iter);
        nodesAdd(&outerblk->stmts, (INode*)loopnode);
    }
    return (INode *)outerblk;
}

// Parse a lifetime variable, followed by colon and then a loop
// 'stmtflag' indicates it is a statement vs. an expression (loop)
INode *parseLifetime(ParseState *parse, int stmtflag) {
    LifetimeNode *life = newLifetimeDclNode(lex->val.ident, 0);
    lexNextToken();
    if (lexIsToken(ColonToken))
        lexNextToken();
    else
        errorMsgLex(ErrorBadTok, "Missing ':' after lifetime");

    if (lexIsToken(LoopToken))
        return parseLoop(parse, life);
    if (stmtflag) {
        if (lexIsToken(WhileToken))
            return parseWhile(parse, life);
        else if (lexIsToken(EachToken))
            return parseEach(parse, NULL, life);
    }
    errorMsgLex(ErrorBadTok, "A lifetime may only be followed by a loop/while/each");
    return NULL;
}

// Parse a 'with' block, setting 'this' to the expression at start of block
INode *parseWith(ParseState *parse) {
    lexNextToken();
    VarDclNode *this = newVarDclFull(thisName, VarDclTag, unknownType, (INode*)immPerm, NULL);
    this->value = parseSimpleExpr(parse);
    BlockNode *blk = (BlockNode*)parseExprBlock(parse);
    nodesInsert(&blk->stmts, (INode*)this, 0);
    return (INode *)blk;
}

// Parse a block of statements/expressions
INode *parseExprBlock(ParseState *parse) {
    BlockNode *blk = newBlockNode();

    parseBlockStart();

    blk->stmts = newNodes(8);
    while (!parseBlockEnd()) {
        lexStmtStart();
        switch (lex->toktype) {
        case SemiToken:
            lexNextToken();
            break;

        case RetToken:
            nodesAdd(&blk->stmts, parseReturn(parse));
            break;

        case WithToken:
            nodesAdd(&blk->stmts, parseWith(parse));
            break;

        case IfToken:
            nodesAdd(&blk->stmts, parseIf(parse));
            break;

        case MatchToken:
            nodesAdd(&blk->stmts, parseMatch(parse));
            break;

        case LoopToken:
            nodesAdd(&blk->stmts, parseLoop(parse, NULL));
            break;

        case WhileToken:
            nodesAdd(&blk->stmts, parseWhile(parse, NULL));
            break;

        case EachToken:
            nodesAdd(&blk->stmts, parseEach(parse, NULL, NULL));
            break;

        case LifetimeToken:
            nodesAdd(&blk->stmts, parseLifetime(parse, 1));
            break;

        case BreakToken:
        {
            BreakNode *node = newBreakNode();
            lexNextToken();
            if (lexIsToken(LifetimeToken)) {
                node->life = (INode*)newNameUseNode(lex->val.ident);
                lexNextToken();
            }
            if (!parseIsEndOfStatement())
                node->exp = parseAnyExpr(parse);
            parseEndOfStatement();
            nodesAdd(&blk->stmts, (INode*)node);
            break;
        }

        case ContinueToken:
        {
            ContinueNode *node = newContinueNode();
            lexNextToken();
            if (lexIsToken(LifetimeToken)) {
                node->life = (INode*)newNameUseNode(lex->val.ident);
                lexNextToken();
            }
            parseEndOfStatement();
            nodesAdd(&blk->stmts, (INode*)node);
            break;
        }

        case LCurlyToken:
            nodesAdd(&blk->stmts, parseExprBlock(parse));
            break;

        // A local variable declaration, if it begins with a permission
        case PermToken:
            nodesAdd(&blk->stmts, (INode*)parseVarDcl(parse, immPerm, ParseMayConst|ParseMaySig|ParseMayImpl));
            parseEndOfStatement();
            break;

        default:
            nodesAdd(&blk->stmts, parseExpStmt(parse));
        }
    }

    return (INode*)blk;
}
