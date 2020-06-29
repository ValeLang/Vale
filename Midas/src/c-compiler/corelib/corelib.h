/** Standard library initialiation
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef stdlib_h
#define stdlib_h

// Unique (unclonable) nodes representing the absence of value or type
extern INode *unknownType;   // Unknown/unspecified type
extern INode *noCareType;    // When the receiver does not care what type is returned
extern INode *elseCond;   // node representing the 'else' condition for an 'if' node
extern INode *borrowRef;  // When a reference's region is unspecified, as it is borrowed
extern INode *noValue;    // For return and break nodes that do not "return" a value

// Built-in permission types - for implicit (non-declared but known) permissions
extern PermNode *uniPerm;
extern PermNode *mutPerm;
extern PermNode *immPerm;
extern PermNode *constPerm;
extern PermNode *mut1Perm;
extern PermNode *opaqPerm;

// Built-in lifetimes
extern LifetimeNode *staticLifetimeNode;

// Built-in allocator types
extern AllocNode *soRegion;
extern AllocNode *rcRegion;

// Primitive numeric types - for implicit (nondeclared but known) types
extern NbrNode *boolType;    // i1
extern NbrNode *i8Type;
extern NbrNode *i16Type;
extern NbrNode *i32Type;
extern NbrNode *i64Type;
extern NbrNode *isizeType;
extern NbrNode *u8Type;
extern NbrNode *u16Type;
extern NbrNode *u32Type;
extern NbrNode *u64Type;
extern NbrNode *usizeType;
extern NbrNode *f32Type;
extern NbrNode *f64Type;

extern INsTypeNode *ptrType;
extern INsTypeNode *refType;
extern INsTypeNode *arrayRefType;

char *stdlibInit(int ptrsize);
void keywordInit();
void stdNbrInit(int ptrsize);

#endif
