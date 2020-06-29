/** Handling for block nodes
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef block_h
#define block_h

// Block is a ordered sequence of executable statements within a function.
// It is also a local execution context/namespace owning local variables.
// Local variables are uniquely named and cannot be forward referenced.
typedef struct BlockNode {
    IExpNodeHdr;
    Nodes *stmts;
    uint16_t scope;
} BlockNode;

BlockNode *newBlockNode();

// Clone block
INode *cloneBlockNode(CloneState *cstate, BlockNode *node);

void blockPrint(BlockNode *blk);

// Handle name resolution and control structure compliance for a block
// - push and pop a namespace context for hooking local vars in global name table
// - Ensure return/continue/break only appear as last statement in block
void blockNameRes(NameResState *pstate, BlockNode *blk);

// Handle type-checking for a block's statements. 
// Note: When coerced by iexpCoerces, vtype of the block will be specified
void blockTypeCheck(TypeCheckState *pstate, BlockNode *blkvz, INode *expectType);

// Ensure this particular block does not end with break/continue
// Used by regular and loop blocks, but not by 'if' based blocks
void blockNoBreak(BlockNode *blk);

// Bidirectional type inference
void blockBiTypeInfer(INode **totypep, BlockNode *blk);

// Perform data flow analysis
void blockFlow(FlowState *fstate, BlockNode **blknode);

#endif
