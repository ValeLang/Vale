/** void type (e.g., function that returns no value)
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef void_h
#define void_h

// Void type - e.g., for fn with no return value
typedef struct VoidTypeNode {
    INodeHdr;
} VoidTypeNode;

// Unique, unclonable node that marks the absence of something
typedef struct AbsenceNode {
    IExpNodeHdr;
} AbsenceNode;

VoidTypeNode *newVoidNode();

// Clone void
INode *cloneVoidNode(CloneState *cstate, VoidTypeNode *node);

void voidPrint(VoidTypeNode *voidnode);

// Create a new Absence node
AbsenceNode *newAbsenceNode();

#endif
