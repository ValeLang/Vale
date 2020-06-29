/** Handling for variable declaration nodes
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef vardcl_h
#define vardcl_h

// Variable declaration node (global, local, parm)
typedef struct VarDclNode {
    IExpNodeHdr;             // 'vtype': type of this name's value
    Name *namesym;
    INode *value;              // Starting value/declaration (NULL if not initialized)
    LLVMValueRef llvmvar;      // LLVM's handle for a declared variable (for generation)
    char *genname;             // Name of variable as known to the linker
    INode *perm;               // Permission type (often mut or imm)
    uint16_t scope;            // 0=global
    uint16_t index;            // index within this scope (e.g., parameter number)
    uint16_t flowflags;        // Data flow pass permanent flags
    uint16_t flowtempflags;    // Data flow pass temporary flags
} VarDclNode;

enum VarFlowTemp {
    VarInitialized = 0x0001     // Variable has been initialized
};

VarDclNode *newVarDclNode(Name *namesym, uint16_t tag, INode *perm);
VarDclNode *newVarDclFull(Name *namesym, uint16_t tag, INode *sig, INode *perm, INode *val);

// Create a new variable dcl node that is a copy of an existing one
INode *cloneVarDclNode(CloneState *cstate, VarDclNode *node);

void varDclPrint(VarDclNode *fn);

// Name resolution of vardcl
void varDclNameRes(NameResState *pstate, VarDclNode *node);

// Type check vardcl
void varDclTypeCheck(TypeCheckState *pstate, VarDclNode *node);

// Perform data flow analysis
void varDclFlow(FlowState *fstate, VarDclNode **vardclnode);

#endif
