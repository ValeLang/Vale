/** Lexer
 * @file
 *
 * The lexer divides up the source program into tokens, producing each for the parser on demand.
 * The lexer assumes UTF-8 encoding for the source program.
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "lexer.h"
#include "../ir/ir.h"
#include "../ir/nametbl.h"
//#include "error.h"
#include "utils/fileio.h"
#include "../shared/memory.h"
#include "../shared/timer.h"
#include "../shared/utf8.h"

#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <stdio.h>
#include <stdarg.h>

// Global lexer state
Lexer *lex = NULL;        // Current lexer

// Send an error message to stderr
void errorMsgLex(int code, const char* msg, ...) {
  va_list argptr;
  va_start(argptr, msg);
  errorOutCode(lex->tokp, lex->linenbr, lex->linep, lex->url, code, msg, argptr);
  va_end(argptr);
}

// Inject a new source stream into the lexer
void lexInject(char *url, char *src) {
    Lexer *prev;

    // Obtain next lexer block via link chain or allocation
    prev = lex;
    if (lex == NULL)
        lex = (Lexer*) memAllocBlk(sizeof(Lexer));
    else if (lex->next == NULL) {
        lex->next = (Lexer*) memAllocBlk(sizeof(Lexer));
        lex = lex->next;
    }
    else
        lex = lex->next; // Re-use an old lexer block
    lex->next = NULL;
    lex->prev = prev;

    // Skip over UTF8 Byte-order mark (BOM = U+FEFF) at start of source, if there
    if (*src=='\xEF' && *(src+1)=='\xBB' && *(src+2)=='\xBF')
        src += 3;

    // Initialize lexer's source info
    lex->url = url;
    lex->fname = fileName(url);
    lex->source = src;

    // Initialize lexer context
    lex->srcp = lex->tokp = lex->linep = src;
    lex->linenbr = 1;
    lex->flags = 0;
    lex->tokPosInLine = 0;
    lex->indentch = '\0';
    lex->curindent = 0;
    lex->stmtindent = 0;
    lex->blkStackLvl = 0;
    lex->blkStack[0].blkindent = 0;
    lex->blkStack[0].paranscnt = 0;
    lex->blkStack[0].blkmode = FreeFormBlock;

    // Prime the pump with the first token
    lexNextToken();
}

// Add a reserved identifier and its node to the global name table
Name *keyAdd(char *keyword, uint16_t toktype) {
    Name *sym;
    INode *node;
    sym = nametblFind(keyword, strlen(keyword));
    sym->node = node = (INode*)memAllocBlk(sizeof(INode));
    node->tag = KeywordTag;
    node->flags = toktype;
    return sym;
}

// Populate global name table with all reserved identifiers & their nodes
void keywordInit() {
    keyAdd("include", IncludeToken);
    keyAdd("import", ImportToken);
    keyAdd("mod", ModToken);
    keyAdd("extern", ExternToken);
    keyAdd("set", SetToken);
    keyAdd("macro", MacroToken);
    keyAdd("fn", FnToken);
    keyAdd("typedef", TypedefToken),
    keyAdd("struct", StructToken);
    keyAdd("trait", TraitToken);
    keyAdd("@samesize", SamesizeToken);
    keyAdd("extends", ExtendsToken);
    keyAdd("mixin", MixinToken);
    keyAdd("enum", EnumToken);
    keyAdd("region", RegionToken);
    keyAdd("return", RetToken);
    keyAdd("with", WithToken);
    keyAdd("if", IfToken);
    keyAdd("elif", ElifToken);
    keyAdd("else", ElseToken);
    keyAdd("match", MatchToken);
    keyAdd("loop", LoopToken);
    keyAdd("while", WhileToken);
    keyAdd("each", EachToken);
    keyAdd("in", InToken);
    keyAdd("by", ByToken);
    keyAdd("break", BreakToken);
    keyAdd("continue", ContinueToken);
    keyAdd("not", NotToken);
    keyAdd("or", OrToken);
    keyAdd("and", AndToken);
    keyAdd("as", AsToken);
    keyAdd("is", IsToken);
    keyAdd("into", IntoToken);

    keyAdd("true", trueToken);
    keyAdd("false", falseToken);
}

// Initialize lexer
void lexInit() {
    lexInject("init", "");
    keywordInit();
}

// Inject a new source stream into the lexer
void lexInjectFile(char *url) {
    char *src;
    char *fn;
    timerBegin(LoadTimer);
    // Load specified source file
    src = fileLoadSrc(lex? lex->url : NULL, url, &fn);
    if (!src)
        errorExit(ExitNF, "Cannot find or read source file %s", url);

    timerBegin(ParseTimer);
    lexInject(fn, src);
}

// Restore previous lexer's stream
void lexPop() {
    if (lex)
        lex = lex->prev;
}

// ******  SIGNIFICANT WHITESPACE HANDLING ***********

// Parser indicates new block starts here, e.g., '{'
void lexBlockStart(LexBlockMode mode) {
    if (lex->blkStackLvl >= LEX_MAX_BLOCKS)
        errorExit(ExitIndent, "Too many indent levels in source file.");
    int level = ++lex->blkStackLvl;
    lex->blkStack[level].blkindent = lex->stmtindent;
    lex->blkStack[level].paranscnt = 0;
    lex->blkStack[level].blkmode = mode;
}

// Parser indicates block finishes here, e.g., '}'
void lexBlockEnd() {
    int level = lex->blkStackLvl--;
    lex->stmtindent = lex->blkStack[level].blkindent;
}

// Does block end here, based on block mode?
int lexIsBlockEnd() {
    switch (lex->blkStack[lex->blkStackLvl].blkmode) {
    case FreeFormBlock: 
        return 0;
    case SameStmtBlock:
        if (lexIsToken(EofToken))
            return 1;
        // It is not end-of-block if we are still on same line as block started on
        if (!lexIsEndOfLine()) 
            return 0;
        // Switch mode for next line, using indentation to drive end-of-block
        lex->blkStack[lex->blkStackLvl].blkmode = SigIndentBlock;
        // Deliberate fallthrough
    case SigIndentBlock:
        if (lexIsToken(EofToken))
            return 1;
        return lexIsEndOfLine() && lex->curindent <= lex->blkStack[lex->blkStackLvl].blkindent;
    }
    return 0;
}

// Decrement counter for parentheses/brackets
void lexDecrParens() {
    if (lex->blkStack[lex->blkStackLvl].paranscnt > 0)
        --lex->blkStack[lex->blkStackLvl].paranscnt;
}

// Increment counter for parentheses/brackets
void lexIncrParens() {
    ++lex->blkStack[lex->blkStackLvl].paranscnt;
}

// Is next token at start of line?
int lexIsEndOfLine() {
    return lex->tokPosInLine == 0;
}

// Parser indicates the start of a new statement
// This allows lexIsStmtBreak to know if a continuation line is indented
void lexStmtStart() {
    lex->stmtindent = lex->curindent;
}

// Return true if current token is first on a line that has not been indented
// and does not have any open parantheses or brackets
int lexIsStmtBreak() {
    return lexIsEndOfLine() && lex->curindent <= lex->stmtindent 
        && lex->blkStack[lex->blkStackLvl].paranscnt == 0;
}

// Handle new line character.
// Update lexer state, including indentation count for current line
char *lexNewLine(char *srcp) {
    srcp++;
    lex->linep = srcp;
    lex->tokPosInLine = 0;
    ++lex->linenbr;
    // Count line's indentation
    lex->curindent = 0;
    while (1) {
        if (*srcp == '\r')
            srcp++;
        else if (*srcp == ' ' || *srcp == '\t') {
            // Issue warning if inconsistent use of indentation character
            switch (lex->indentch) {
            case '\0':
                lex->indentch = *srcp;
                break;
            case ' ':
            case '\t':
                if (*srcp != lex->indentch) {
                    lex->tokp = srcp;
                    errorMsgLex(WarnIndent, "Inconsistent indentation - use either spaces or tabs, not both.");
                    lex->indentch = '*'; // Only issue warning once
                }
                break;
            default:
                break;
            }
            srcp++;
            lex->curindent++;
        }
        else
            break;
    }
    return srcp;
}

// ******  TOKEN-SPECIFIC LEXING **********

/** Return value of hex digit, or -1 if not correct */
char *lexHexDigits(int cnt, char *srcp, uint64_t *val) {
    *val = 0;
    while (cnt--) {
        *val <<= 4;
        if (*srcp>='0' && *srcp<='9')
            *val += *srcp++ - '0';
        else if (*srcp>='A' && *srcp<='F')
            *val += *srcp++ - ('A' - 10);
        else if (*srcp>='a' && *srcp<='f')
            *val += *srcp++ - ('a' - 10);
        else {
            errorMsgLex(ErrorBadTok, "Invalid hexadecimal character '%c'", *srcp);
            return srcp;
        }
    }
    return srcp;
}

/** Turn escape sequence into a single character */
char *lexScanEscape(char *srcp, uint64_t *charval) {
    switch (*++srcp) {
    case 'a': *charval = '\a'; return ++srcp;
    case 'b': *charval = '\b'; return ++srcp;
    case 'f': *charval = '\f'; return ++srcp;
    case 'n': *charval = '\n'; return ++srcp;
    case 'r': *charval = '\r'; return ++srcp;
    case 't': *charval = '\t'; return ++srcp;
    case 'v': *charval = '\v'; return ++srcp;
    case '\'': *charval = '\''; return ++srcp;
    case '\"': *charval = '\"'; return ++srcp;
    case '\\': *charval = '\\'; return ++srcp;
    case ' ': *charval = ' '; return ++srcp;
    case '\0': *charval = '\0'; return ++srcp;
    case 'x': return lexHexDigits(2, ++srcp, charval);
    case 'u': return lexHexDigits(4, ++srcp, charval);
    case 'U': return lexHexDigits(8, ++srcp, charval);
    default:
        errorMsgLex(ErrorBadTok, "Invalid escape sequence '%c'", *srcp);
        *charval = *srcp++;
        return srcp;
    }
}

/** Tokenize a lifetime annotation or character literal */
void lexScanChar(char *srcp) {
    char *srcbeg = srcp;
    lex->tokp = srcp++;

    // Assume we have a lifetime variable if it starts with a letter, not followed by close single quote
    if (isalpha(*srcp) && *(srcp+1)!='\'') {
        while (isalnum(*srcp))
            ++srcp;
        // Accept it if next char is non-single quote punctuation
        if (*srcp != '\'' && !(*srcp & 0x80)) {
            lex->val.ident = nametblFind(srcbeg, srcp - srcbeg);
            lex->toktype = LifetimeToken;
            lex->srcp = srcp;
            return;
        }
        // This is not a lifetime variable. Reset to try it as a character literal
        srcp = lex->tokp;
    }

    // Obtain a single character/unicode (possibly escaped)
    if (*srcp == '\\')
        srcp = lexScanEscape(srcp, &lex->val.uintlit);
    else
        lex->val.uintlit = *srcp++;

    // If following character is end quote, return as integer literal
    if (*srcp == '\'')
    {
        srcp++;
        if (*srcp == 'u') {
            lex->langtype = (INode*)u32Type;
            srcp++;
        }
        else
            lex->langtype = lex->val.uintlit >= 0x100 ? (INode*)u32Type : (INode*)u8Type;
        lex->toktype = IntLitToken;
        lex->srcp = srcp;
        return;
    }

    // Not a recognizable token. Skip forward, error and pretend we have a char literal anyway
    while (*srcp && *srcp != '\n') {
        if (*srcp == '\'') {
            ++srcp;
            break;
        }
        ++srcp;
    }
    errorMsgLex(ErrorBadTok, "Invalid lifetime or too-long character literal");
    lex->langtype = (INode*)u8Type;
    lex->toktype = IntLitToken;
    lex->srcp = srcp;
}

void lexScanString(char *srcp) {
    uint64_t uchar;
    lex->tokp = srcp++;

    // Conservatively count the size of the string
    uint32_t srclen = 0;
    while (*srcp && *srcp != '"') {
        srclen++;
        srcp++;
        if (*srcp == '\\' && *(srcp + 1) == '"') {
            srclen++;
            srcp += 2;
        }
    }

    // Build string literal
    char *newp = memAllocStr(NULL, srclen);
    srclen = 0;
    lex->val.strlit = newp;
    srcp = lex->tokp+1;
    while (*srcp != '"' && *srcp) {
        // discard all control chars, including spaces after new-line
        if (*srcp < ' ') {
            if (*srcp++ == '\n') {
                while (*srcp <= ' ' && *srcp)
                    ++srcp;
            }
            continue;
        }

        // Obtain next possible UTF-8 character
        if (*srcp == '\\')
            srcp = lexScanEscape(srcp, &uchar);
        else
            uchar = *srcp++;

        if (uchar < 0x80) {
            *newp++ = (unsigned char)uchar;
            srclen++;
        }
        else if (uchar<0x800) {
            *newp++ = 0xC0 | (unsigned char)(uchar >> 6);
            *newp++ = 0x80 | (uchar & 0x3f);
            srclen+=2;
        }
        else if (uchar<0x10000) {
            *newp++ = 0xE0 | (unsigned char)(uchar >> 12);
            *newp++ = 0x80 | ((uchar >> 6) & 0x3F);
            *newp++ = 0x80 | (uchar & 0x3f);
            srclen+=3;
        }
        else if (uchar<0x110000) {
            *newp++ = 0xF0 | (unsigned char)(uchar >> 18);
            *newp++ = 0x80 | ((uchar >> 12) & 0x3F);
            *newp++ = 0x80 | ((uchar >> 6) & 0x3F);
            *newp++ = 0x80 | (uchar & 0x3f);
            srclen+=4;
        }
    }
    *newp = '\0';  // Backstop with null to be careful
    if (*srcp == '"')
        srcp++;        // Move past terminating " character

    lex->strlen = srclen;  // Count of all characters of string, except final null char.
    lex->toktype = StringLitToken;
    lex->srcp = srcp;
}

/** Tokenize an integer or floating point number */
void lexScanNumber(char *srcp) {

    char *srcbeg;        // Pointer to the start of the token
    uint64_t base;        // Radix for integer (10 or 16)
    uint64_t intval;    // Calculated integer value for integer literal
    char isFloat;        // nonzero when number token is a float, 'e' when in exponent

    lex->tokp = srcbeg = srcp;

    // A leading zero may indicate a non-base 10 number
    base = 10;
    if (*srcp=='0' && (*(srcp+1)=='x' || *(srcp+1)=='X')) {
        base = 16;
        srcp += 2;
    }

    // Validate and process remaining numeric digits
    isFloat = '\0';
    intval = 0;
    while (1) {
        // Only one exponent allowed
        if (isFloat!='e' && (*srcp=='e' || *srcp=='E' || *srcp=='p' || *srcp=='P')) {
            isFloat = 'e';
            if (*++srcp == '-' || *srcp == '+')
                srcp++;
            continue;
        }
        // Handle characters in a suspected integer
        // Decimal point means it is floating point after all
        if (*srcp=='.') {
            // However, double periods is not floating point, but that subsequent token is range op
            if (*(srcp+1)=='.')
                break;
            srcp++;
            isFloat = '.';
            continue;
        }
        // Extract a number digit value from the character
        if (*srcp>='0' && *srcp<='9')
            intval = intval*base + *srcp++ - '0';
        else if (*srcp=='_')
            srcp++;
        else if (base==16) {
            if (*srcp>='A' && *srcp<='F')
                intval = (intval<<4) + *srcp++ - 'A'+10;
            else if (*srcp>='a' && *srcp<='f')
                intval = (intval<<4) + *srcp++ - 'a'+10;
            else
                break;
        }
        else
            break;
    }

    // Process number's explicit type as part of the token
    if (*srcp=='d') {
        isFloat = 'd';
        srcp++;
        lex->langtype = (INode*)f64Type;
    } else if (*srcp=='f') {
        isFloat = 'f';
        lex->langtype = (INode*)f32Type;
        if (*(++srcp)=='6' && *(srcp+1)=='4') {
            lex->langtype = (INode*)f64Type;
            srcp += 2;
        }
        else if (*srcp=='3' && *(srcp+1)=='2')
            srcp += 2;
    } else if (*srcp=='i') {
        lex->langtype = (INode*)i32Type;
        if (*(++srcp)=='8') {        
            srcp++; lex->langtype = (INode*)i8Type;
        } else if (*srcp=='1' && *(srcp+1)=='6') {
            srcp += 2; lex->langtype = (INode*)i16Type;
        } else if (*srcp=='3' && *(srcp+1)=='2') {
            srcp += 2;
        } else if (*srcp=='6' && *(srcp+1)=='4') {
            srcp += 2; lex->langtype = (INode*)i64Type;
        } else if (strncmp(srcp, "size", 4)==0) {
            srcp += 4; lex->langtype = (INode*)isizeType;
        }
    } else if (*srcp=='u') {
        lex->langtype = (INode*)u32Type;
        if (*(++srcp)=='8') {        
            srcp++; lex->langtype = (INode*)u8Type;
        } else if (*srcp=='1' && *(srcp+1)=='6') {
            srcp += 2; lex->langtype = (INode*)u16Type;
        } else if (*srcp=='3' && *(srcp+1)=='2') {
            srcp += 2;
        } else if (*srcp=='6' && *(srcp+1)=='4') {
            srcp += 2; lex->langtype = (INode*)u64Type;
        } else if (strncmp(srcp, "size", 4)==0) {
            srcp += 4; lex->langtype = (INode*)usizeType;
        }
    }
    else
        lex->langtype = isFloat ? (INode*)f32Type : unknownType;

    // Set value and type
    if (isFloat) {
        lex->val.floatlit = atof(srcbeg);
        lex->toktype = FloatLitToken;
    }
    else {
        lex->val.uintlit = intval;
        lex->toktype = IntLitToken;
    }
    lex->srcp = srcp;
}

/** Tokenize an identifier or reserved token */
void lexScanIdent(char *srcp) {
    char *srcbeg = srcp++;    // Pointer to the start of the token
    lex->tokp = srcbeg;
    while (1) {
        switch (*srcp) {

        // Allow digit, letter or underscore in token
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
        case 'a': case 'b': case 'c': case 'd': case 'e':
        case 'f': case 'g': case 'h': case 'i': case 'j':
        case 'k': case 'l': case 'm': case 'n': case 'o':
        case 'p': case 'q': case 'r': case 's': case 't':
        case 'u': case 'v': case 'w': case 'x': case 'y': case 'z':
        case 'A': case 'B': case 'C': case 'D': case 'E':
        case 'F': case 'G': case 'H': case 'I': case 'J':
        case 'K': case 'L': case 'M': case 'N': case 'O':
        case 'P': case 'Q': case 'R': case 'S': case 'T':
        case 'U': case 'V': case 'W': case 'X': case 'Y': case 'Z':
        case '_':
            srcp++;
            break;

        default:
            // Allow unicode letters in identifier name
            if (utf8IsLetter(srcp))
                srcp += utf8ByteSkip(srcp);
            else {
                INode *identNode;
                // Find identifier token in name table and preserve info about it
                // Substitute token type when identifier is a keyword
                lex->val.ident = nametblFind(srcbeg, srcp-srcbeg);
                identNode = (INode*)lex->val.ident->node;
                if (identNode && identNode->tag == KeywordTag)
                    lex->toktype = identNode->flags;
                else if (identNode && identNode->tag == PermTag)
                    lex->toktype = PermToken;
                else if (*srcbeg == '@')
                    lex->toktype = AttrIdentToken;
                else if (*srcbeg == '#')
                    lex->toktype = MetaIdentToken;
                else
                    lex->toktype = IdentToken;
                lex->srcp = srcp;
                return;
            }
        }
    }
}

/** Tokenize an identifier or reserved token */
void lexScanTickedIdent(char *srcp) {
    char *srcbeg = srcp++;    // Pointer to the start of the token
    lex->tokp = srcbeg;

    // Look for closing backtick, but not past end of line
    while (*srcp != '`' && *srcp && *srcp != '\n' && *srcp != '\x1a')
        srcp++;
    if (*srcp != '`') {
        errorMsgLex(ErrorBadTok, "Back-ticked identifier requires closing backtick");
        srcp = srcbeg + 2;
    }

    // Find identifier token in name table and preserve info about it
    lex->val.ident = nametblFind(srcbeg+1, srcp - srcbeg - 1);
    lex->toktype = IdentToken;
    lex->srcp = srcp+1;
}

// Skip over nested block comment
char *lexBlockComment(char *srcp) {
    int nest = 1;
    while (*srcp) {
        if (*srcp == '*' && *(srcp + 1) == '/') {
            if (--nest == 0)
                return srcp+2;
            ++srcp;
        }
        else if (*srcp == '/' && *(srcp + 1) == '*') {
            ++nest;
            ++srcp;
        }
        // ignore tokens inside line comment
        else if (*srcp == '/' && *(srcp + 1) == '/') {
            srcp += 2;
            while (*srcp && *srcp++ != '\n');
        }
        // ignore tokens inside string literal
        else if (*srcp == '"') {
            ++srcp;
            while (*srcp && *srcp++ != '"') {
                if (*(srcp - 1) == '\\' && *srcp == '"')
                    srcp++;
            }
        }
        ++srcp;
    }
    return srcp;
}

// Shortcut macro for return a punctuation token
#define lexReturnPuncTok(tok, skip) { \
    lex->toktype = tok; \
    lex->tokp = srcp; \
    lex->srcp = srcp + (skip); \
    return; \
}

// Decode next token from the source into new lex->token
void lexNextTokenx() {
    char *srcp;
    srcp = lex->srcp;
    ++lex->tokPosInLine;
    while (1) {
        switch (*srcp) {

        // Numeric literal (integer or float)
        case '0': case '1': case '2': case '3': case '4':
        case '5': case '6': case '7': case '8': case '9':
            lexScanNumber(srcp);
            return;

        // ' ' - single character surrounded with single quotes
        case '\'':
            lexScanChar(srcp);
            return;

        // " " - string surrounded with double quotes
        case '"':
            lexScanString(srcp);
            return;

        // Identifier
        case 'a': case 'b': case 'c': case 'd': case 'e':
        case 'f': case 'g': case 'h': case 'i': case 'j':
        case 'k': case 'l': case 'm': case 'n': case 'o':
        case 'p': case 'q': case 'r': case 's': case 't':
        case 'u': case 'v': case 'w': case 'x': case 'y': case 'z':
        case 'A': case 'B': case 'C': case 'D': case 'E':
        case 'F': case 'G': case 'H': case 'I': case 'J':
        case 'K': case 'L': case 'M': case 'N': case 'O':
        case 'P': case 'Q': case 'R': case 'S': case 'T':
        case 'U': case 'V': case 'W': case 'X': case 'Y': case 'Z':
        case '#': case '@': case '_':
            lexScanIdent(srcp);
            return;

        // backtick enclosed identifiers
        case '`':
            lexScanTickedIdent(srcp);
            return;

        case '.': lexReturnPuncTok(DotToken, 1);
        case ',': lexReturnPuncTok(CommaToken, 1);
        case '~': lexReturnPuncTok(TildeToken, 1);

        case '+': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(PlusEqToken, 2);
            }
            else if (*(srcp + 1) == '+') {
                lexReturnPuncTok(IncrToken, 2);
            }
            else {
                lexReturnPuncTok(PlusToken, 1);
            }

        case '-': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(MinusEqToken, 2);
            }
            else if (*(srcp + 1) == '-') {
                lexReturnPuncTok(DecrToken, 2);
            }
            else {
                lexReturnPuncTok(DashToken, 1);
            }

        case '*': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(MultEqToken, 2);
            }
            else {
                lexReturnPuncTok(StarToken, 1);
            }

        case '%': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(RemEqToken, 2);
            }
            else {
                lexReturnPuncTok(PercentToken, 1);
            }

        case '^': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(XorEqToken, 2);
            }
            else {
                lexReturnPuncTok(CaretToken, 1);
            }

        case '&': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(AndEqToken, 2);
            }
            else if (*(srcp + 1) == '[' && *(srcp + 2) == ']') {
                lexReturnPuncTok(ArrayRefToken, 3);
            }
            else if (*(srcp + 1) == '<') {
                lexReturnPuncTok(VirtRefToken, 2);
            }
            else {
                lexReturnPuncTok(AmperToken, 1);
            }

        case '|': 
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(OrEqToken, 2);
            }
            else {
                lexReturnPuncTok(BarToken, 1);
            }

        // '=' and '=='
        case '=':
            if (*(srcp + 1) == '=')    {
                lexReturnPuncTok(EqToken, 2);
            }
            else {
                lexReturnPuncTok(AssgnToken, 1);
            }

        // '!' and '!='
        case '!':
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(NeToken, 2);
            }
            else {
                lexReturnPuncTok(NotToken, 1);
            }

        // '<' and '<='
        case '<':
            if (*(srcp + 1) == '-') {
                lexReturnPuncTok(LessDashToken, 2);
            }
            else if (*(srcp + 1) == '=') {
                lexReturnPuncTok(LeToken, 2);
            }
            else if (*(srcp + 1) == '<') {
                if (*(srcp + 2) == '=') {
                    lexReturnPuncTok(ShlEqToken, 3);
                }
                else {
                    lexReturnPuncTok(ShlToken, 2);
                }
            }
            else {
                lexReturnPuncTok(LtToken, 1);
            }

        // '>' and '>='
        case '>':
            if (*(srcp + 1) == '=') {
                lexReturnPuncTok(GeToken, 2);
            }
            else if (*(srcp + 1) == '>') {
                if (*(srcp + 1) == '=') {
                    lexReturnPuncTok(ShrEqToken, 3);
                }
                else {
                    lexReturnPuncTok(ShrToken, 2);
                }
            }
            else {
                lexReturnPuncTok(GtToken, 1);
            }

        case '?': 
            if (*(srcp + 1) == '.') {
                lexReturnPuncTok(QuesDotToken, 2);
            }
            else
                lexReturnPuncTok(QuesToken, 1);
        case '[': 
            lexReturnPuncTok(LBracketToken, 1);
        case ']': 
            lexReturnPuncTok(RBracketToken, 1);
        case '(': 
            lexReturnPuncTok(LParenToken, 1);
        case ')': 
            lexReturnPuncTok(RParenToken, 1);

        // ':' and '::'
        case ':':
            if (*(srcp + 1) == ':') {
                lexReturnPuncTok(DblColonToken, 2);
            }
            else {
                lexReturnPuncTok(ColonToken, 1);
            }

        // ';'
        case ';':
            lexReturnPuncTok(SemiToken, 1);

        case '{': 
            lexReturnPuncTok(LCurlyToken, 1);
        case '}': 
            lexReturnPuncTok(RCurlyToken, 1);
        
        // '/' or '//' or '/*'
        case '/':
            // Line comment: '//'
            if (*(srcp+1)=='/') {
                srcp += 2;
                while (*srcp && *srcp!='\n' && *srcp!='\x1a')
                    srcp++;
            }
            // Block comment, nested: '/*'
            else if (*(srcp + 1) == '*') {
                srcp = lexBlockComment(srcp+2);
            }
            // '/' operator (e.g., division)
            else if (*(srcp + 1) == '=') {
                lexReturnPuncTok(DivEqToken, 2);
            }
            else
                lexReturnPuncTok(SlashToken, 1);
            break;

        // Ignore white space
        case ' ': case '\t':
            srcp++;
            break;

        // Ignore carriage return
        case '\r':
            srcp++;
            break;

        // Handle new line
        case '\n':
            srcp = lexNewLine(srcp);
            break;

        // End-of-file
        case '\0': case '\x1a':
            lexReturnPuncTok(EofToken, 0);

        // Bad character
        default:
            {
                lex->tokp = srcp;
                errorMsgLex(ErrorBadTok, "Bad character '%c' starting unknown token", *srcp);
                srcp += utf8ByteSkip(srcp);
            }
        }
    }
}

// Obtain next token (and time how long it takes)
void lexNextToken() {
    timerBegin(LexTimer);
    lexNextTokenx();
    timerBegin(ParseTimer);
}
