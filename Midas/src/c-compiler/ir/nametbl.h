/** Hashed name table
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef nametbl_h
#define nametbl_h

#include "ir.h"

#include <stddef.h>

// The Global Name Table holds a context-spacific collection of names.
// - Parse uses it to resolve:
//   - reserved keywords
//   - permission names that begin a declaration of a variable.
//   - allocator names as part of a reference type
// - Name resolution uses it (see "hook" functions) to resolve:
//   - NameUse nodes to the name declaration node that they refer to

// Global Name Table configuration variables
extern size_t gNameTblInitSize;                  // Initial maximum number of unique names (must be power of 2)
extern unsigned int gNameTblUtil;    // % utilization that triggers doubling of table

// Allocate and initialize the global name table
void nametblInit();

// Get pointer to the Name struct for the name's string in the global name table 
// For an unknown name, it allocates memory for the string and adds it to name table.
Name *nametblFind(char *strp, size_t strl);

// Return how many bytes have been allocated for global name table but not yet used
size_t nametblUnused();

// The global name hook functions help with the name resolution pass.
// Whenever we enter a namespace context, the context's names are temporarily
// added to the global name table. This way the lookup of a NameUse node
// will find the innermost scope's node that declares this name.
// Sometimes all of the namespace's names are added right away.
// However, a block's local variables are added as encountered.
// When the context ends, its names are unhooked, revealing the ones there before.
void nametblHookPush();
void nametblHookGrow();
void nametblHookNode(Name *name, INode *node);
// Hook all of a namespace's names and nodes in the current hooktable
void nametblHookNamespace(Namespace *ns);
void nametblHookPop();

#endif
