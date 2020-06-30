/** Parse expressions
 * @file
 *
 * The parser translates the lexer's tokens into IR nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "parser.h"
#include "../ir/ir.h"
#include "../ir/nametbl.h"
#include "../shared/memory.h"
//#include "error.h"
#include "lexer.h"

#include <stdio.h>
#include <assert.h>

// Parse a name use, which may be qualified with module names
INode *parseNameUse(ParseState *parse) {
    NameUseNode *nameuse = newNameUseNode(NULL);

    int baseset = 0;
    if (lexIsToken(DblColonToken)) {
        nameUseBaseMod(nameuse, parse->pgmmod);
        baseset = 1;
    }
    while (1) {
        if (lexIsToken(IdentToken)) {
            Name *name = lex->val.ident;
            lexNextToken();
            // Identifier is a module qualifier
            if (lexIsToken(DblColonToken)) {
                if (!baseset)
                    nameUseBaseMod(nameuse, parse->mod); // relative to current module
                nameUseAddQual(nameuse, name);
                lexNextToken();
            }
            // Identifier is the actual name itself
            else {
                nameuse->namesym = name;
                break;
            }
        }
        // Can only get here if previous token was double quotes
        else {
            errorMsgLex(ErrorNoVar, "Missing variable name after module qualifiers");
            break;
        }
    }
    return (INode*)nameuse;
}

// Parse an array literal
INode *parseArrayLit(ParseState *parse, INode *typenode) {
    ArrayNode *array = newArrayNode();
    lexNextToken();

    // Gather comma-separated expressions that are likely elements or element type
    while (1) {
        nodesAdd(&array->elems, parseSimpleExpr(parse));
        if (!lexIsToken(CommaToken))
            break;
        lexNextToken();
    }

    // Semi-colon signals we had dimensions instead, swap and then get elements
    if (lexIsToken(SemiToken)) {
        lexNextToken();
        Nodes *elems = array->dimens;
        array->dimens = array->elems;
        while (1) {
            nodesAdd(&elems, parseSimpleExpr(parse));
            if (!lexIsToken(CommaToken))
                break;
            lexNextToken();
        };
        array->elems = elems;
    }
    parseCloseTok(RBracketToken);

    return (INode *)array;
}

// Parse a term: literal, identifier, etc.
INode *parseTerm(ParseState *parse) {
    switch (lex->toktype) {
    case trueToken:
    {
        ULitNode *node = newULitNode(1, (INode*)boolType);
        lexNextToken();
        return (INode *)node;
    }
    case falseToken:
    {
        ULitNode *node = newULitNode(0, (INode*)boolType);
        lexNextToken();
        return (INode *)node;
    }
    case IntLitToken:
        {
            ULitNode *node = newULitNode(lex->val.uintlit, lex->langtype);
            lexNextToken();
            return (INode *)node;
        }
    case FloatLitToken:
        {
            FLitNode *node = newFLitNode(lex->val.floatlit, lex->langtype);
            lexNextToken();
            return (INode *)node;
        }
    case StringLitToken:
        {
            SLitNode *node = newSLitNode(lex->val.strlit, lex->strlen);
            lexNextToken();
            return (INode *)node;
        }
    case IdentToken:
    case DblColonToken:
        return (INode*)parseNameUse(parse);
    case LParenToken:
        {
            INode *node;
            lexNextToken();
            lexIncrParens();
            node = parseAnyExpr(parse);
            parseCloseTok(RParenToken);
            return node;
        }
    case LBracketToken:
        return parseArrayLit(parse, NULL);
    default:
        errorMsgLex(ErrorBadTerm, "Invalid term: expected name, literal, etc.");
        lexNextToken(); // Avoid infinite loop
        return NULL;
    }
}

// Parse a function/method call argument
INode *parseArg(ParseState *parse) {
    INode *arg = parseSimpleExpr(parse);
    if (lexIsToken(ColonToken)) {
        if (arg->tag != NameUseTag)
            errorMsgNode((INode*)arg, ErrorNoName, "Expected a named identifier");
        arg = (INode*)newNamedValNode(arg);
        lexNextToken();
        ((NamedValNode *)arg)->val = parseSimpleExpr(parse);
    }
    return arg;
}

// Parse multiple arguments inside () or []. Return a Nodes containing all argument nodes.
Nodes *parseArgs(ParseState *parse) {
    int closetok = lex->toktype == LBracketToken ? RBracketToken : RParenToken;
    lexNextToken();
    lexIncrParens();
    Nodes *args = newNodes(8);
    if (!lexIsToken(closetok)) {
        nodesAdd(&args, parseArg(parse));
        while (lexIsToken(CommaToken)) {
            lexNextToken();
            nodesAdd(&args, parseArg(parse));
        }
    }
    parseCloseTok(closetok);
    return args;
}

// Parse a '.'-based method call/field access
INode *parseDotCall(ParseState *parse, INode *node, uint16_t flags) {
    FnCallNode *fncall = newFnCallNode(node, 0);
    fncall->flags |= flags;
    lexNextToken();

    // Get field/method name
    if (lexIsToken(IdentToken)) {
        NameUseNode *method = newNameUseNode(lex->val.ident);
        method->tag = MbrNameUseTag;
        fncall->methfld = method;
    }
    else
        errorMsgLex(ErrorNoMbr, "This should be a named field/method");
    lexNextToken();

    // Parentheses after '.' are part of same fncall operation
    if (lexIsToken(LParenToken))
        fncall->args = parseArgs(parse);
    return (INode*)fncall;
}

// This processes all suffix operators successively
// Returns a node with first suffix inner-most, last is outermost.
INode *parseSuffix(ParseState *parse, INode *node, uint16_t flags) {

    // Process as many suffixes as we have, each applying to the term before
    while (1) {
        if (lexIsToken(DotToken) && !lexIsStmtBreak()) {
            node = parseDotCall(parse, node, flags);
        }

        // Handle () suffix and enclosed arguments
        else if (lexIsToken(LParenToken) && !lexIsStmtBreak()) {
            FnCallNode *fncall = newFnCallNode(node, 0);
            fncall->flags |= flags;
            fncall->args = parseArgs(parse);
            node = (INode*)fncall;
        }

        // Handle [] indexing suffix and enclosed arguments
        else if (lexIsToken(LBracketToken) && !lexIsStmtBreak()) {
            FnCallNode *fncall = newFnCallNode(node, 0);
            fncall->flags |= flags | FlagIndex;
            fncall->args = parseArgs(parse);
            node = (INode*)fncall;
        }

        // Handle postfix ++
        else if (lexIsToken(IncrToken) && !lexIsStmtBreak()) {
            node = (INode*)newFnCallOpname(node, incrPostName, 0);
            node->flags |= FlagLvalOp;
            lexNextToken();
        }

        // Handle postfix --
        else if (lexIsToken(DecrToken) && !lexIsStmtBreak()) {
            node = (INode*)newFnCallOpname(node, decrPostName, 0);
            node->flags |= FlagLvalOp;
            lexNextToken();
        }

        // No suffix, we are done with suffixes
        else
            return node;
    }
    return node; // we never get here
}

// Parse a term wrapped by any suffixes.
// This is the normal precedence (except for borrowed references)
INode *parseSuffixTerm(ParseState *parse) {
    return parseSuffix(parse, parseTerm(parse), 0);
}

INode *parsePrefix(ParseState *parse, int noSuffix);

// Parse an "ampersand term" for a ref type or reference constructor:
// - Some reference type ('&', '&[]' or '&<')
// - Allocation node (with region)
// - Borrowed ref (including to an anonymous function or closure)
INode *parseAmper(ParseState *parse) {
    // Create appropriate RefNode, depending on ampersand operator
    RefNode *anode = NULL;
    switch (lex->toktype) {
    case AmperToken:
        anode = newRefNode(RefTag); break;
    case ArrayRefToken:
        anode = newRefNode(ArrayRefTag); break;
    case VirtRefToken:
        anode = newRefNode(VirtRefTag); break;
    }
    lexNextToken();

    // Allocated reference starts with a region
    if (lexIsToken(IdentToken)
        && lex->val.ident->node && lex->val.ident->node->tag == RegionTag) {
        anode->region = (INode*)lex->val.ident->node;
        lexNextToken();
        anode->perm = parsePerm();
        anode->vtexp = parsePrefix(parse, 0);
        return (INode *)anode;
    }

    // Without region, we have a borrowed reference
    anode->perm = parsePerm();

    // Handle borrowed reference to anonymous function/closure
    // Note: This could also be a ref to a function signature. We sort this out later.
    if (lexIsToken(FnToken)) {
        FnDclNode *fndcl = (FnDclNode*)parseFn(parse, 0, ParseMayAnon | ParseMayImpl | ParseMaySig | ParseEmbedded);
        if (fndcl->value) {
            // If we have an implemented function, we need to move it to the module so it gets generated
            // Then refer to it using a nameuse node as part of this reference node
            nodesAdd(&parse->mod->nodes, (INode*)fndcl);
            NameUseNode *fnname = newNameUseNode(anonName);
            fnname->tag = VarNameUseTag;
            fnname->dclnode = (INode*)fndcl;
            fnname->vtype = fndcl->vtype;
            anode->vtexp = (INode*)fnname;
        }
        else {
            // If no implementation, assume we have a function signature type instead
            anode->vtexp = fndcl->vtype;
        }
        return (INode *)anode;
    }

    // For a function parameter, we allow incomplete reference types
    // where the type the reference points-to can be inferred later (typically, Self)
    if (lexIsToken(CommaToken) || lexIsToken(RParenToken)) {
        anode->vtexp = unknownType;
        return (INode *)anode;
    }

    // Borrowed references handle precedence differently, consuming only a prefixed term w/o suffixes
    // Then the suffixes are appended afterwards as a chain of method calls that consume the borrowed ref
    anode->vtexp = parsePrefix(parse, 1);  // Consume prefixed-term but skip suffix parsing
    INode* node = parseSuffix(parse, (INode*)anode, FlagBorrow);
    if (node != (INode*)anode)
        anode->flags |= FlagSuffix;  // Set flag to show we ended up with suffixes
    return node;
}

// Parse a prefix operator.
// If noSuffix flag on (for borrowed refs), parse only a term next, otherwise parse term+suffix.
INode *parsePrefix(ParseState *parse, int noSuffix) {
    switch (lex->toktype) {

    // '.' sugar for: this.suffixes
    case DotToken:
    {
        INode *node = parseDotCall(parse, (INode*)newNameUseNode(thisName), 0);
        return parseSuffix(parse, node, 0);
    }

    // '*' (dereference or pointer type)
    case StarToken:
    {
        StarNode *node = newStarNode(StarTag);
        lexNextToken();
        node->vtexp = parsePrefix(parse, noSuffix);
        return (INode *)node;
    }

    // '&', '&[]', '&<' (borrow/allocate/ref type)
    case AmperToken:
    case ArrayRefToken:
    case VirtRefToken:
        return parseAmper(parse);

    // '?' (Option type)
    case QuesToken:
    {
        // Lower into 'Option[expr]'
        NameUseNode *option = newNameUseNode(optionName);
        FnCallNode *opttype = newFnCallNode((INode*)option, 1);
        lexNextToken();
        nodesAdd(&opttype->args, parsePrefix(parse, noSuffix));
        return (INode*)opttype;
    }

    // '-' (negative). Optimize for literals
    case DashToken:
    {
        FnCallNode *node = newFnCallOpname(NULL, minusName, 0);
        lexNextToken();
        INode *argnode = parsePrefix(parse, noSuffix);
        if (argnode->tag == ULitTag) {
            ((ULitNode*)argnode)->uintlit = (uint64_t)-((int64_t)((ULitNode*)argnode)->uintlit);
            return argnode;
        }
        else if (argnode->tag == FLitTag) {
            ((FLitNode*)argnode)->floatlit = -((FLitNode*)argnode)->floatlit;
            return argnode;
        }
        node->objfn = argnode;
        return (INode *)node;
    }

    // '~' (bitwise not)
    case TildeToken:
    {
        FnCallNode *node = newFnCallOp(NULL, "~", 0);
        lexNextToken();
        node->objfn = parsePrefix(parse, noSuffix);
        return (INode *)node;
    }

    // '++' (prefix increment)
    case IncrToken:
    {
        FnCallNode *node = newFnCallOpname(NULL, incrName, 0);
        node->flags |= FlagLvalOp;
        lexNextToken();
        node->objfn = parsePrefix(parse, noSuffix);
        return (INode *)node;
    }

    // '--' (prefix decrement)
    case DecrToken:
    {
        FnCallNode *node = newFnCallOpname(NULL, decrName, 0);
        node->flags |= FlagLvalOp;
        lexNextToken();
        node->objfn = parsePrefix(parse, noSuffix);
        return (INode *)node;
    }

    // No prefix operator: get term or term+suffix, depending on flag
    default:
        if (noSuffix)
            return parseTerm(parse); // save suffixes for a borrowed ref
        else
            return parseSuffixTerm(parse);  // normal precedence
    }
}

// Parse type cast
INode *parseCast(ParseState *parse) {
    INode *lhnode = parsePrefix(parse, 0);
    if (lexIsToken(AsToken)) {
        CastNode *node = newRecastNode(lhnode, unknownType);
        lexNextToken();
        node->typ = parseVtype(parse);
        return (INode*)node;
    }
    else if (lexIsToken(IntoToken)) {
        CastNode *node = newConvCastNode(lhnode, unknownType);
        lexNextToken();
        node->typ = parseVtype(parse);
        return (INode*)node;
    }
    return lhnode;
}

// Parse binary multiply, divide, rem operator
INode *parseMult(ParseState *parse) {
    INode *lhnode = parseCast(parse);
    while (1) {
        if (lexIsToken(StarToken) && !lexIsStmtBreak()) {
            FnCallNode *node = newFnCallOpname(lhnode, multName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseCast(parse));
            lhnode = (INode*)node;
        }
        else if (lexIsToken(SlashToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, divName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseCast(parse));
            lhnode = (INode*)node;
        }
        else if (lexIsToken(PercentToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, remName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseCast(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse binary add, subtract operator
INode *parseAdd(ParseState *parse) {
    INode *lhnode = parseMult(parse);
    while (1) {
        if (lexIsToken(PlusToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, plusName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseMult(parse));
            lhnode = (INode*)node;
        }
        else if (lexIsToken(DashToken) && !lexIsStmtBreak()) {
            FnCallNode *node = newFnCallOpname(lhnode, minusName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseMult(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse << and >> operators
INode *parseShift(ParseState *parse) {
    INode *lhnode;
    // Prefix '<<' or '>>' implies 'this'
    if (lexIsToken(ShlToken) || lexIsToken(ShrToken))
        lhnode = (INode *)newNameUseNode(thisName);
    else
        lhnode = parseAdd(parse);
    while (1) {
        if (lexIsToken(ShlToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, shlName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseAdd(parse));
            lhnode = (INode*)node;
        }
        else if (lexIsToken(ShrToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, shrName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseAdd(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse bitwise And
INode *parseAnd(ParseState *parse) {
    INode *lhnode = parseShift(parse);
    while (1) {
        if (lexIsToken(AmperToken) && !lexIsStmtBreak()) {
            FnCallNode *node = newFnCallOpname(lhnode, andName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseShift(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse bitwise Xor
INode *parseXor(ParseState *parse) {
    INode *lhnode = parseAnd(parse);
    while (1) {
        if (lexIsToken(CaretToken)) {
            FnCallNode *node = newFnCallOpname(lhnode, xorName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseAnd(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse bitwise or
INode *parseOr(ParseState *parse) {
    INode *lhnode = parseXor(parse);
    while (1) {
        if (lexIsToken(BarToken) && !lexIsStmtBreak()) {
            FnCallNode *node = newFnCallOpname(lhnode, orName, 2);
            lexNextToken();
            nodesAdd(&node->args, parseXor(parse));
            lhnode = (INode*)node;
        }
        else
            return lhnode;
    }
}

// Parse comparison operator
INode *parseCmp(ParseState *parse) {
    INode *lhnode = parseOr(parse);
    char *cmpop;

    switch (lex->toktype) {
    case EqToken:  cmpop = "=="; break;
    case NeToken:  cmpop = "!="; break;
    case LtToken:  cmpop = "<"; break;
    case LeToken:  cmpop = "<="; break;
    case GtToken:  cmpop = ">"; break;
    case GeToken:  cmpop = ">="; break;
    default:
        if (lexIsToken(IsToken) && !lexIsStmtBreak()) {
            CastNode *node = newIsNode(lhnode, unknownType);
            lexNextToken();
            node->typ = parseVtype(parse);
            return (INode*)node;
        }
        else
            return lhnode;
    }

    FnCallNode *node = newFnCallOp(lhnode, cmpop, 2);
    lexNextToken();
    nodesAdd(&node->args, parseOr(parse));
    return (INode*)node;
}

// Parse 'not' logical operator
INode *parseNotLogic(ParseState *parse) {
    if (lexIsToken(NotToken)) {
        LogicNode *node = newLogicNode(NotLogicTag);
        lexNextToken();
        node->lexp = parseNotLogic(parse);
        return (INode*)node;
    }
    return parseCmp(parse);
}

// Parse 'and' logical operator
INode *parseAndLogic(ParseState *parse) {
    INode *lhnode = parseNotLogic(parse);
    while (lexIsToken(AndToken)) {
        LogicNode *node = newLogicNode(AndLogicTag);
        lexNextToken();
        node->lexp = lhnode;
        node->rexp = parseNotLogic(parse);
        lhnode = (INode*)node;
    }
    return lhnode;
}

// Parse 'or' logical operator
INode *parseOrExpr(ParseState *parse) {
    INode *lhnode = parseAndLogic(parse);
    while (lexIsToken(OrToken)) {
        LogicNode *node = newLogicNode(OrLogicTag);
        lexNextToken();
        node->lexp = lhnode;
        node->rexp = parseAndLogic(parse);
        lhnode = (INode*)node;
    }
    return lhnode;
}

// This parses any kind of expression, including blocks, assignment or tuple
INode *parseSimpleExpr(ParseState *parse) {
    switch (lex->toktype) {
    case IfToken:
        return parseIf(parse);
    case MatchToken:
        return parseMatch(parse);
    case LoopToken:
        return parseLoop(parse, NULL);
    case LifetimeToken:
        return parseLifetime(parse, 0);
    case LCurlyToken:
        return parseExprBlock(parse);
    default:
        return parseOrExpr(parse);
    }
}

// Parse a comma-separated expression tuple
INode *parseTuple(ParseState *parse) {
    INode *exp = parseSimpleExpr(parse);
    if (lexIsToken(CommaToken)) {
        TupleNode *tuple = newTupleNode(4);
        nodesAdd(&tuple->elems, exp);
        while (lexIsToken(CommaToken)) {
            lexNextToken();
            nodesAdd(&tuple->elems, parseSimpleExpr(parse));
        }
        return (INode*)tuple;
    }
    else
        return exp;
}

// Parse an operator assignment
INode *parseOpEq(ParseState *parse, INode *lval, Name *opeqname) {
    FnCallNode *node = newFnCallOpname(lval, opeqname, 2);
    node->flags |= FlagOpAssgn | FlagLvalOp;
    lexNextToken();
    nodesAdd(&node->args, parseAnyExpr(parse));
    return (INode*)node;
}

// Parse the append operator (<-)
INode *parseAppend(ParseState *parse, INode *lval) {
    FnCallNode *node = newFnCallOpname(lval, lessDashName, 2);
    node->flags |= FlagOpAssgn | FlagLvalOp;
    lexNextToken();
    nodesAdd(&node->args, parseTuple(parse));  // Note: if we have a tuple, is lowered in fncall name resolve
    return (INode*)node;
}

// Parse an assignment expression
INode *parseAssign(ParseState *parse) {
    // Prefix <- operator applies to 'this'
    if (lexIsToken(LessDashToken)) {
        return parseAppend(parse, (INode*)newNameUseNode(thisName));
    }

    INode *lval = parseTuple(parse);
    switch (lex->toktype) {
    case AssgnToken:
    {
        lexNextToken();
        INode *rval = parseAnyExpr(parse);
        return (INode*)newAssignNode(NormalAssign, lval, rval);
    }

    case PlusEqToken:
        return parseOpEq(parse, lval, plusEqName);
    case MinusEqToken:
        return parseOpEq(parse, lval, minusEqName);
    case MultEqToken:
        return parseOpEq(parse, lval, multEqName);
    case DivEqToken:
        return parseOpEq(parse, lval, divEqName);
    case RemEqToken:
        return parseOpEq(parse, lval, remEqName);
    case OrEqToken:
        return parseOpEq(parse, lval, orEqName);
    case AndEqToken:
        return parseOpEq(parse, lval, andEqName);
    case XorEqToken:
        return parseOpEq(parse, lval, xorEqName);
    case ShlEqToken:
        return parseOpEq(parse, lval, shlEqName);
    case ShrEqToken:
        return parseOpEq(parse, lval, shrEqName);
    case LessDashToken:
        return parseAppend(parse, lval);
    default:
        return lval;
    }
}

// This parses any kind of expression, including blocks, assignment or tuple
INode *parseAnyExpr(ParseState *parse) {
    return parseAssign(parse);
}
