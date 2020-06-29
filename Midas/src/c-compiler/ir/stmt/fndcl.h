/** Handling for function/method declaration nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef fndcl_h
#define fndcl_h

// Function/method declaration node
typedef struct FnDclNode {
    IExpNodeHdr;                // 'vtype': type of this name's value
    Name *namesym;
    INode *value;                 // Block or intrinsic code nodes (NULL if no code)
    LLVMValueRef llvmvar;         // LLVM's handle for a declared variable (for generation)
    char *genname;                // Name of the function as known to the linker
    struct FnDclNode *nextnode;   // Link to next overloaded method with the same name (or NULL)
    uint16_t vtblidx;             // Method ptr's index in the type's vtable
} FnDclNode;

// Create a new function declaraction node
FnDclNode *newFnDclNode(Name *namesym, uint16_t tag, INode *sig, INode *val);

// Return a clone of a function/method declaration
INode *cloneFnDclNode(CloneState *cstate, FnDclNode *oldfn);

void fnDclPrint(FnDclNode *fn);

/// Resolve all names in a function
void fnDclNameRes(NameResState *pstate, FnDclNode *name);

// Type checking a function's logic, does more than you might think:
// - Turn implicit returns into explicit returns
// - Perform type checking for all statements
// - Perform data flow analysis on variables and references
void fnDclTypeCheck(TypeCheckState *pstate, FnDclNode *fnnode);

#endif
