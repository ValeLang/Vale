/** Generator for LLVM
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef genllvm_h
#define genllvm_h

#include "../ir/ir.h"
#include "../coneopts.h"

#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>

// An entry for each active loop block in current control flow stack
#define GenLoopMax 256
typedef struct {
    LoopNode *loop;
    LLVMBasicBlockRef loopbeg;
    LLVMBasicBlockRef loopend;
    LLVMValueRef *loopPhis;
    LLVMBasicBlockRef *loopBlks;
    uint32_t loopPhiCnt;
} GenLoopState;

typedef struct GenState {
    LLVMTargetMachineRef machine;
    LLVMTargetDataRef datalayout;
    LLVMContextRef context;
    LLVMModuleRef module;
    LLVMValueRef fn;
    LLVMValueRef allocaPoint;
    LLVMBuilderRef builder;
    LLVMBasicBlockRef block;

    LLVMDIBuilderRef dibuilder;
    LLVMMetadataRef compileUnit;
    LLVMMetadataRef difile;

    ConeOptions *opt;
    GenLoopState *loopstack;
    uint32_t loopstackcnt;
} GenState;

// Setup LLVM generation, ensuring we know intended target
void genSetup(GenState *gen, ConeOptions *opt);
void genClose(GenState *gen);
void genMod(GenState *gen, ModuleNode *mod);
void genlGloVarName(GenState *gen, VarDclNode *glovar);
void genlGloFnName(GenState *gen, FnDclNode *glofn);

// genlstmt.c
LLVMBasicBlockRef genlInsertBlock(GenState *gen, char *name);
LLVMValueRef genlBlock(GenState *gen, BlockNode *blk);
LLVMValueRef genlLoop(GenState *gen, LoopNode *wnode);

// genlexpr.c
LLVMValueRef genlExpr(GenState *gen, INode *termnode);

// genlalloc.c
// Generate code that creates an allocated ref by allocating and initializing
LLVMValueRef genlallocref(GenState *gen, RefNode *allocatenode);
// Progressively dealias or drop all declared variables in nodes list
void genlDealiasNodes(GenState *gen, Nodes *nodes);
// Add to the counter of an rc allocated reference
void genlRcCounter(GenState *gen, LLVMValueRef ref, long long amount, RefNode *refnode);
// Dealias an own allocated reference
void genlDealiasOwn(GenState *gen, LLVMValueRef ref, RefNode *refnode);
// Create an alloca (will be pushed to the entry point of the function.
LLVMValueRef genlAlloca(GenState *gen, LLVMTypeRef type, const char *name);

#endif
