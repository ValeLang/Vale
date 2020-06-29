/** Parser
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
#include "../shared/fileio.h"
#include "../ir/nametbl.h"
#include "../coneopts.h"
#include "lexer.h"

#include <stdio.h>
#include <string.h>

// Skip to next statement for error recovery
void parseSkipToNextStmt() {
    // Ensure we are always moving forwards, line by line
    if (lexIsEndOfLine() && !lexIsToken(SemiToken) && !lexIsToken(EofToken) && !lexIsToken(RCurlyToken))
        lexNextToken();
    while (1) {
        // Consume semicolon as end-of-statement
        if (lexIsToken(SemiToken)) {
            lexNextToken();
            return;
        }
        // Treat end-of-line, end-of-file, or '}' as end-of-statement
        // (clearly end-of-line might *not* be end-of-statement)
        if (lexIsEndOfLine() || lexIsToken(EofToken) || lexIsToken(RCurlyToken))
            return;

        lexNextToken();
    }
}

// Is this end-of-statement? if ';', '}', or end-of-file
int parseIsEndOfStatement() {
    return (lex->toktype == SemiToken || lex->toktype == RCurlyToken || lex->toktype == EofToken
        || lexIsStmtBreak());
}

// We expect optional semicolon since statement has run its course
void parseEndOfStatement() {
    // Consume semicolon as end-of-statement signifier, if found
    if (lex->toktype == SemiToken) {
        lexNextToken();
        return;
    }
    // If no semi-colon specified, we expect to be at end-of-line,
    // unless next token is '}' or end-of-file
    if (!lexIsEndOfLine() && lex->toktype != RCurlyToken && lex->toktype != EofToken)
        errorMsgLex(ErrorNoSemi, "Statement finished? Expected semicolon or end of line.");
}

// Return true on '{' or ':'
int parseHasBlock() {
    return (lex->toktype == LCurlyToken || lex->toktype == ColonToken);
}

// Expect a block to start, consume its token and set lexer mode
void parseBlockStart() {
    if (lex->toktype == LCurlyToken) {
        lexNextToken();
        lexBlockStart(FreeFormBlock);
        return;
    }
    else if (lex->toktype == ColonToken) {
        lexNextToken();
        lexBlockStart(lexIsEndOfLine() ? SigIndentBlock : SameStmtBlock);
        return;
    }

    // Generate error and try to recover
    errorMsgLex(ErrorNoLCurly, "Expected ':' or '{' to start a block");
    if (lexIsEndOfLine() && lex->curindent > lex->stmtindent) {
        lexBlockStart(SigIndentBlock);
        return;
    }
    // Skip forward to find something we can use
    while (1) {
        if (lexIsToken(LCurlyToken) || lexIsToken(ColonToken)) {
            parseBlockStart();
            return;
        }
        if (lexIsToken(EofToken))
            break;
        lexNextToken();
    }
}

// Are we at end of block yet? If so, consume token and reset lexer mode
int parseBlockEnd() {
    if (lexIsToken(RCurlyToken) && lex->blkStack[lex->blkStackLvl].blkmode == FreeFormBlock) {
        lexNextToken();
        lexBlockEnd();
        return 1;
    }
    if (lexIsBlockEnd()) {
        lexBlockEnd();
        return 1;
    }
    if (lexIsToken(EofToken)) {
        errorMsgLex(ErrorNoRCurly, "Expected end of block (e.g., '}')");
        return 1;
    }
    return 0;
}

// Expect closing token (e.g., right parenthesis). If not found, search for it or '}' or ';'
void parseCloseTok(uint16_t closetok) {
    if (!lexIsToken(closetok))
        errorMsgLex(ErrorNoRParen, "Expected right parenthesis - skipping forward to find it");
    while (!lexIsToken(closetok)) {
        if (lexIsToken(EofToken) || lexIsToken(SemiToken) || lexIsToken(RCurlyToken))
            return;
        lexNextToken();
    }
    lexNextToken();
    lexDecrParens();
}

// Parse a function block
INode *parseFn(ParseState *parse, uint16_t nodeflags, uint16_t mayflags) {
    GenericNode *genericnode = NULL;
    FnDclNode *fnnode = newFnDclNode(NULL, nodeflags, NULL, NULL);

    // Skip past the 'fn'
    lexNextToken();

    // Process function name, if provided
    if (lexIsToken(IdentToken)) {
        if (!(mayflags&ParseMayName))
            errorMsgLex(WarnName, "Unnecessary function name is ignored");
        fnnode->namesym = lex->val.ident;
        fnnode->genname = &fnnode->namesym->namestr;
        lexNextToken();
        if (lexIsToken(LBracketToken)) {
            genericnode = newGenericDclNode(fnnode->namesym);
            parseGenericVars(parse, genericnode);
            genericnode->body = (INode*)fnnode;
        }
    }
    else {
        if (!(mayflags&ParseMayAnon))
            errorMsgLex(ErrorNoName, "Function declarations must be named");
    }

    // Process the function's signature info.
    fnnode->vtype = parseFnSig(parse);

    // Process statements block that implements function, if provided
    if (parseHasBlock()) {
        if (!(mayflags&ParseMayImpl))
            errorMsgNode((INode*)fnnode, ErrorBadImpl, "Function/method implementation is not allowed here.");
        fnnode->value = parseExprBlock(parse);
    }
    else {
        if (!(mayflags&ParseMaySig))
            errorMsgNode((INode*)fnnode, ErrorNoImpl, "Function/method must be implemented.");
        if (!(mayflags&ParseEmbedded))
            parseEndOfStatement();
    }

    return genericnode? (INode*)genericnode : (INode*) fnnode;
}

// Parse source filename/path as identifier or string literal
char *parseFile() {
    char *filename;
    switch (lex->toktype) {
    case IdentToken:
        filename = &lex->val.ident->namestr;
        lexNextToken();
        break;
    case StringLitToken:
        filename = lex->val.strlit;
        lexNextToken();
        break;
    default:
        errorExit(ExitNF, "Invalid source file; expected identifier or string");
        filename = NULL;
    }
    return filename;
}

void parseGlobalStmts(ParseState *parse, ModuleNode *mod);

// Parse include statement
void parseInclude(ParseState *parse) {
    char *filename;
    lexNextToken();
    filename = parseFile();
    parseEndOfStatement();

    lexInjectFile(filename);
    parseGlobalStmts(parse, parse->mod);
    if (lex->toktype != EofToken) {
        errorMsgLex(ErrorNoEof, "Expected end-of-file");
    }
    lexPop();
}

char *stdiolib =
"extern {fn printStr(str &[]u8); fn printFloat(a f64); fn printInt(a i64); fn printChar(code u64);}\n"
"struct IOStream{"
"  fd i32;"
"  fn `<-`(str &[]u8) {printStr(str)}"
"  fn `<-`(i i64) {printInt(i)}"
"  fn `<-`(n f64) {printFloat(n)}"
"  fn `<-`(ch u64) {printChar(ch)}"
"}"
"mut print = IOStream[0]"
;

// Parse import statement: This is a hacked-up version for now
void parseImport(ParseState *parse) {
    char *filename;
    lexNextToken();
    filename = parseFile();
    parseEndOfStatement();

    if (strcmp(filename, "stdio") == 0) {
        lexInject("stdio", stdiolib);
        parseGlobalStmts(parse, parse->mod);
        if (lex->toktype != EofToken) {
            errorMsgLex(ErrorNoEof, "Expected end-of-file");
        }
        lexPop();
    }
}

// Parse function or variable, as it may be preceded by a qualifier
// Return NULL if not either
void parseFnOrVar(ParseState *parse, uint16_t flags) {

    if (lexIsToken(FnToken)) {
        FnDclNode *node = (FnDclNode*)parseFn(parse, 0, (flags&FlagExtern)? (ParseMayName | ParseMaySig) : (ParseMayName | ParseMayImpl));
        node->flags |= flags;
        nameGenVarName((VarDclNode *)node, parse->gennamePrefix);
        modAddNode(parse->mod, node->namesym, (INode*)node);
        return;
    }

    // A global variable declaration, if it begins with a permission
    else if lexIsToken(PermToken) {
        VarDclNode *node = parseVarDcl(parse, immPerm, ParseMayConst | ((flags&FlagExtern) ? ParseMaySig : ParseMayImpl | ParseMaySig));
        node->flags |= flags;
        parseEndOfStatement();
        nameGenVarName((VarDclNode *)node, parse->gennamePrefix);
        modAddNode(parse->mod, node->namesym, (INode*)node);
    }
    else {
        errorMsgLex(ErrorBadGloStmt, "Expected function or variable declaration");
        parseSkipToNextStmt();
        return;
    }
}

// Parse a list of generic variables and add to the genericnode
void parseGenericVars(ParseState *parse, GenericNode *genericnode) {
    lexNextToken(); // Go past left square bracket
    while (lexIsToken(IdentToken)) {
        GenVarDclNode *parm = newGVarDclNode(lex->val.ident);
        nodesAdd(&genericnode->parms, (INode*)parm);
        lexNextToken();
        if (lexIsToken(CommaToken))
            lexNextToken();
    }
    if (lexIsToken(RBracketToken))
        lexNextToken();
    else
        errorMsgLex(ErrorBadTok, "Expected list of macro parameter names ending with square bracket.");
}

// Parse a macro declaration
GenericNode *parseMacro(ParseState *parse) {
    lexNextToken();
    if (!lexIsToken(IdentToken)) {
        errorMsgLex(ErrorBadTok, "Expected a macro name");
        return newMacroDclNode(anonName);
    }
    GenericNode *macro = newMacroDclNode(lex->val.ident);
    lexNextToken();
    if (lexIsToken(LBracketToken)) {
        parseGenericVars(parse, macro);
    }
    macro->body = parseExprBlock(parse);
    return macro;
}

ModuleNode *parseModule(ParseState *parse);

// Parse a global area statement (within a module)
// modAddNode adds node to module, as needed, including error message for dupes
void parseGlobalStmts(ParseState *parse, ModuleNode *mod) {
    // Create and populate a Module node for the program
    while (lex->toktype!=EofToken && !parseBlockEnd()) {
        lexStmtStart();
        switch (lex->toktype) {

        case IncludeToken:
            parseInclude(parse);
            break;

        case ImportToken:
            parseImport(parse);
            break;

        case ModToken: {
            ModuleNode *newmod = parseModule(parse);
            modAddNode(mod, newmod->namesym, (INode*)newmod);
            break;
        }

        case TypedefToken: {
            TypedefNode *newnode = parseTypedef(parse);
            modAddNode(mod, newnode->namesym, (INode*)newnode);
            break;
        }

        // 'struct'-style type definition
        case StructToken: {
            INode *node = parseStruct(parse, 0);
            modAddNode(mod, inodeGetName(node), node);
            break;
        }

        // 'trait' type definition
        case TraitToken: {
            INode *node = parseStruct(parse, TraitType);
            modAddNode(mod, inodeGetName(node), node);
            break;
        }

        // 'macro'
        case MacroToken: {
            GenericNode *macro = parseMacro(parse);
            modAddNode(mod, macro->namesym, (INode*)macro);
            break;
        }

        // 'extern' qualifier in front of fn or var (block)
        case ExternToken:
        {
            lexNextToken();
            uint16_t extflag = FlagExtern;
            if (lexIsToken(IdentToken)) {
                if (strcmp(&lex->val.ident->namestr, "system")==0)
                    extflag |= FlagSystem;
                lexNextToken();
            }
            if (lexIsToken(ColonToken) || lexIsToken(LCurlyToken)) {
                parseBlockStart();
                while (!parseBlockEnd()) {
                    lexStmtStart();
                    if (lexIsToken(FnToken) || lexIsToken(PermToken))
                        parseFnOrVar(parse, extflag);
                    else {
                        errorMsgLex(ErrorNoSemi, "Extern expects only functions and variables");
                        parseSkipToNextStmt();
                    }
                }
            }
            else
                parseFnOrVar(parse, extflag);
        }
            break;

        // Function or variable
        case FnToken:
        case PermToken:
            parseFnOrVar(parse, 0);
            break;

        default:
            errorMsgLex(ErrorBadGloStmt, "Invalid global area statement");
            lexNextToken();
            parseSkipToNextStmt();
            break;
        }
    }
}

// Parse a module's global statement block
ModuleNode *parseModuleBlk(ParseState *parse, ModuleNode *mod) {
    ModuleNode *oldmod = parse->mod;
    parse->mod = mod;
    modHook(oldmod, mod);
    parseGlobalStmts(parse, mod);
    modHook(mod, oldmod);
    parse->mod = oldmod;
    return mod;
}

// Parse a submodule within a program
ModuleNode *parseModule(ParseState *parse) {
    char *svprefix = parse->gennamePrefix;
    ModuleNode *mod;
    char *filename, *modname;

    // Parse enough to know what we are dealing with
    lexNextToken();
    filename = parseFile();
    modname = fileName(filename);
    mod = newModuleNode();
    mod->namesym = nametblFind(modname, strlen(modname));
    nameConcatPrefix(&parse->gennamePrefix, modname);

    // Check if module's block has been specified
    if (parseHasBlock()) {
        // Inline module
        parseBlockStart();
        parseModuleBlk(parse, mod);
        // parseBlockEnd() will have happened
    }
    else {
        // If no block, get and inject the module file
        parseEndOfStatement();
        lexInjectFile(filename);
        parseModuleBlk(parse, mod);
        lexPop();
    }

    parse->gennamePrefix = svprefix;
    return mod;
}

// Parse a program = the main module
ModuleNode *parsePgm(ConeOptions *opt) {
    // Initialize name table and lexer
    nametblInit();
    lexInit();

    // Initialize parser state
    ParseState parse;
    parse.mod = NULL;
    parse.typenode = NULL;
    parse.gennamePrefix = "";

    // Parse core library
    // Note: we bypass module hooking here, because we want core lib types
    // to be always visible across all other modules
    ModuleNode *coremod = newModuleNode();
    parse.pgmmod = coremod;
    lexInject("corelib", stdlibInit(opt->ptrsize));
    parseGlobalStmts(&parse, coremod);

    // Parse main source file
    ModuleNode *mod = newModuleNode();
    modAddNode(mod, NULL, (INode*)coremod); // Make sure it gets passes
    parse.pgmmod = mod;
    lexInjectFile(opt->srcpath);
    return parseModuleBlk(&parse, mod);
}
