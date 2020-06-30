/** Intermediate Representation (IR) structures and macros
*
* The IR, together with the name table, is the skeleton of the compiler.
* It connects together every stage:
*
* - The parser transforms programs into IR
* - The semantic analysis walks the IR nodes over multiple passes
* - Macro and template expansion happens via IR cloning
* - Generation transforms IR into LLVM IR
*
* The IR is comprised of heterogeneous nodes that share common INodeHdr info.
* In some cases, it is possible for several distinct node types to share an 
* identical data structure (e.g., statement expression and return).
*
* This include file will pull in the include files for all node types, including types.
* It also defines the structures needed for semantic analysis passes.
*
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef ir_h
#define ir_h

#include <llvm-c/Core.h>

#include <stdint.h>

#include "../shared/memory.h"
#include "nodes.h"
#include "nodelist.h"
#include "namespace.h"
typedef struct Name Name;        // ../nametbl.h
typedef struct Lexer Lexer;        // ../../parser/lexer.h
typedef struct NameResState NameResState;
typedef struct TypeCheckState TypeCheckState;

// Interfaces & headers shared across nodes
#include "inode.h"
#include "name.h"
#include "itype.h"
#include "iexp.h"
#include "instype.h"
#include "clone.h"
#include "flow.h"

// These includes are needed by all node handling
#include "../parser/lexer.h"
//#include "error.h"
#include "nametbl.h"
#include "../shared/memory.h"

#include "types/typedef.h"
#include "types/ttuple.h"
#include "types/permission.h"
#include "types/enum.h"
#include "types/lifetime.h"
#include "types/fnsig.h"
#include "types/number.h"
#include "types/reference.h"
#include "types/arrayref.h"
#include "types/pointer.h"
#include "types/struct.h"
#include "types/array.h"
#include "types/void.h"
typedef struct AllocNode {
    INsTypeNodeHdr;
} AllocNode;

#include "stmt/module.h"
#include "stmt/break.h"
#include "stmt/continue.h"
#include "stmt/fielddcl.h"
#include "stmt/fndcl.h"
#include "stmt/return.h"
#include "stmt/intrinsic.h"
#include "stmt/vardcl.h"

#include "exp/borrow.h"
#include "exp/allocate.h"
#include "exp/assign.h"
#include "exp/cast.h"
#include "exp/deref.h"
#include "exp/nameuse.h"
#include "exp/block.h"
#include "exp/fncall.h"
#include "exp/if.h"
#include "exp/literal.h"
#include "exp/loop.h"
#include "exp/namedval.h"
#include "exp/typelit.h"
#include "exp/logic.h"
#include "exp/sizeof.h"
#include "exp/vtuple.h"

#include "meta/genvardcl.h"
#include "meta/generic.h"

#include "../corelib/corelib.h"

// Context used for name resolution pass
typedef struct NameResState {
    ModuleNode *mod;        // Current module
    INode *typenode;        // Current type (e.g., struct)
    uint16_t scope;         // The current block scope (0=global, 1=fnsig, 2+=blocks)
    uint16_t flags;         // e.g., PassWithinWhile
} NameResState;

#define TypeCheckLoopMax 256

// Context used for type check pass
typedef struct TypeCheckState {
    FnSigNode *fnsig;         // The type signature of the function we are within
    INode *typenode;          // Current type (e.g., struct)
    LoopNode **loopstack;     // Stack of active loops
    uint32_t loopcnt;         // How many currently in the loop stack
    uint16_t scope;           // Current block scope level
} TypeCheckState;

#endif
