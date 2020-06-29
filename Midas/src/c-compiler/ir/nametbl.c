/** Names and Global Name Table.
 * @file
 *
 * The lexer generates symbolic, hashed tokens to speed up parsing matches (== vs. strcmp).
 * Every name is immutable and uniquely hashed based on its string characters.
 *
 * All names are hashed and stored in the global name table.
 * The name's table entry points to an allocated block that holds its current "value", computed hash and c-string.
 *
 * Dan Bernstein's djb algorithm is the hash function for strings, being fast and effective
 * The name table uses open addressing (vs. chaining) with quadratic probing (no Robin Hood).
 * The name table starts out large, but will double in size whenever it gets close to full.
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "nametbl.h"
#include "memory.h"

#include <stdio.h>
#include <assert.h>
#include <string.h>

// Public globals
size_t gNameTblInitSize = 16384;    // Initial maximum number of unique names (must be power of 2)
unsigned int gNameTblUtil = 50;     // % utilization that triggers doubling of table

// Private globals
static Name **gNameTable = NULL;           // The name table array
static size_t gNameTblAvail = 0;           // Number of allocated name table slots (power of 2)
static size_t gNameTblCeil = 0;            // Ceiling that triggers table growth
static size_t gNameTblUsed = 0;            // Number of name table slots used

/** String hash function (djb: Dan Bernstein)
 * Ref: http://www.cse.yorku.ca/~oz/hash.html
 * Ref: http://www.partow.net/programming/hashfunctions/#AvailableHashFunctions
 * Would this help?: hash ^= hash >> 11; // Extra randomness for long names?
 */
#define nameHashFn(hash, strp, strl) \
{ \
    char *p; \
    size_t len = strl; \
    p = strp; \
    hash = 5381; \
    while (len--) \
        hash = ((hash << 5) + hash) ^ ((size_t)*p++); \
}

/** Modulo operation that calculates primary table entry from name's hash.
 * 'size' is always a power of 2 */
#define nameHashMod(hash, size) \
    (assert(((size)&((size)-1))==0), (size_t) ((hash) & ((size)-1)) )

/** Calculate index into name table for a name using linear probing
 * The table's slot at index is either empty or matches the provided name/hash
 */
#define nametblFindSlot(tblp, hash, strp, strl) \
{ \
    size_t tbli; \
    for (tbli = nameHashMod(hash, gNameTblAvail);;) { \
        Name *slot; \
        slot = gNameTable[tbli]; \
        if (slot==NULL || (slot->hash == hash && slot->namesz == strl && strncmp(strp, &slot->namestr, strl)==0)) \
            break; \
        tbli = nameHashMod(tbli + 1, gNameTblAvail); \
    } \
    tblp = &gNameTable[tbli]; \
}

/** Grow the name table, by either creating it or doubling its size */
void nametblGrow() {
    size_t oldTblAvail;
    Name **oldTable;
    size_t newTblMem;
    size_t oldslot;

    // Preserve old table info
    oldTable = gNameTable;
    oldTblAvail = gNameTblAvail;

    // Allocate and initialize new name table
    gNameTblAvail = oldTblAvail==0? gNameTblInitSize : oldTblAvail<<1;
    gNameTblCeil = (gNameTblUtil * gNameTblAvail) / 100;
    newTblMem = gNameTblAvail * sizeof(Name*);
    gNameTable = (Name**) memAllocBlk(newTblMem);
    memset(gNameTable, 0, newTblMem); // Fill with NULL pointers & 0s

    // Copy existing name slots to re-hashed positions in new table
    for (oldslot=0; oldslot < oldTblAvail; oldslot++) {
        Name **oldslotp, **newslotp;
        oldslotp = &oldTable[oldslot];
        if (*oldslotp) {
            char *strp;
            size_t strl, hash;
            Name *oldnamep = *oldslotp;
            strp = &oldnamep->namestr;
            strl = oldnamep->namesz;
            hash = oldnamep->hash;
            nametblFindSlot(newslotp, hash, strp, strl);
            *newslotp = *oldslotp;
        }
    }
    // memFreeBlk(oldTable);
}

/** Get pointer to interned Name in Global Name Table matching string. 
 * For unknown name, this allocates memory for the string and adds it to name table. */
Name *nametblFind(char *strp, size_t strl) {
    size_t hash;
    Name **slotp;

    // Hash provide string into table
    nameHashFn(hash, strp, strl);
    nametblFindSlot(slotp, hash, strp, strl);

    // If not already a name, allocate memory for string and add to table
    if (*slotp == NULL) {
        Name *newname;
        // Double table if it has gotten too full
        if (++gNameTblUsed >= gNameTblCeil) {
            nametblGrow();
            nametblFindSlot(slotp, hash, strp, strl);
        }

        // Allocate and populate name info
        *slotp = newname = memAllocBlk(sizeof(Name) + strl);
        memcpy(&newname->namestr, strp, strl);
        (&newname->namestr)[strl] = '\0';
        newname->hash = hash;
        newname->namesz = (unsigned char)strl;
        newname->node = NULL;        // Node not yet known
    }
    return *slotp;
}

// Return size of unused space for name table
size_t nametblUnused() {
    return (gNameTblAvail-gNameTblUsed)*sizeof(Name*);
}

// Initialize name table
void nametblInit() {
    nametblGrow();

    // Populate common symbols/names (see name.h)
    anonName = nametblFind("_", 1);
    selfName = nametblFind("self", 4);
    selfTypeName = nametblFind("Self", 4);
    thisName = nametblFind("this", 4);
    cloneName = nametblFind("clone", 5);
    finalName = nametblFind("final", 5);

    plusEqName = nametblFind("+=", 2);
    minusEqName = nametblFind("-=", 2);
    multEqName = nametblFind("*=", 2);
    divEqName = nametblFind("/=", 2);
    remEqName = nametblFind("%=", 2);
    orEqName = nametblFind("|=", 2);
    andEqName = nametblFind("&=", 2);
    xorEqName = nametblFind("^=", 2);
    shlEqName = nametblFind("<<=", 3);
    shrEqName = nametblFind(">>=", 3);
    lessDashName = nametblFind("<-", 2);

    plusName = nametblFind("+", 1);
    minusName = nametblFind("-", 1);
    istrueName = nametblFind("isTrue", 6);
    multName = nametblFind("*", 1);
    divName = nametblFind("/", 1);
    remName = nametblFind("%", 1);
    orName = nametblFind("|", 1);
    andName = nametblFind("&", 1);
    xorName = nametblFind("^", 1);
    shlName = nametblFind("<<", 2);
    shrName = nametblFind(">>", 2);

    incrName = nametblFind("++", 2);
    decrName = nametblFind("--", 2);
    incrPostName = nametblFind("+++", 3);
    decrPostName = nametblFind("---", 3);

    eqName = nametblFind("==", 2);
    neName = nametblFind("!=", 2);
    leName = nametblFind("<=", 2);
    ltName = nametblFind("<", 1);
    geName = nametblFind(">=", 2);
    gtName = nametblFind(">", 1);

    parensName = nametblFind("()", 2);
    indexName = nametblFind("[]", 2);
    refIndexName = nametblFind("&[]", 3);

    optionName = nametblFind("Option", 6);
}


// ************************ Name table hook *******************************

// Resolving name references to their scope-correct name definition can get complicated.
// One approach involves the equivalent of a search path, walking backwards through
// lexically-nested namespaces to find the first name that matches. This approach
// can take time, as it may involve a fair amount of linear searching.
// Using tries can improve performance over linear searching, to some degree.
//
// Name hooking is a performant alternative to tries or search paths. When a namespace
// context comes into being, its names are "hooked" into the global symbol table,
// replacing the appropriate IR node with the current one for that name.
// Since all names are memoized symbols, there is no search (linear or otherwise).
// The node you want is already attached to the name. When the scope ends,
// the name's node is unhooked and replaced with the saved node from an earlier scope.
//
// Name hooking is particularly valuable for name resolution.
// However, it is also used during parsing for modules, who need
// to resolve permissions and allocators for correct syntactic decoding.
//
// Hooking uses a LIFO stack of hook tables that preserve the old name/node pairs
// for later restoration when unhooking. Hook tables are reused (for performance)
// and will grow as needed.

// An entry for preserving the node that was in global name table for the name
typedef struct {
    INode *node;         // The previous node to restore on pop
    Name *name;          // The name the node was indexed as
} HookTableEntry;

typedef struct {
    HookTableEntry *hooktbl;
    uint32_t size;
    uint32_t alloc;
} HookTable;

HookTable *gHookTables = NULL;
int gHookTablePos = -1;
int gHookTableSize = 0;

// Create a new hooked context for name/node associations
void nametblHookPush() {

    ++gHookTablePos;

    // Ensure we have a large enough area for HookTable pointers
    if (gHookTableSize == 0) {
        gHookTableSize = 32;
        gHookTables = (HookTable*)memAllocBlk(gHookTableSize * sizeof(HookTable));
        memset(gHookTables, 0, gHookTableSize * sizeof(HookTable));
        gHookTablePos = 0;
    }
    else if (gHookTablePos >= gHookTableSize) {
        // Double table size, copying over old data
        HookTable *oldtable = gHookTables;
        int oldsize = gHookTableSize;
        gHookTableSize <<= 1;
        gHookTables = (HookTable*)memAllocBlk(gHookTableSize * sizeof(HookTable));
        memset(gHookTables, 0, gHookTableSize * sizeof(HookTable));
        memcpy(gHookTables, oldtable, oldsize * sizeof(HookTable));
    }

    HookTable *table = &gHookTables[gHookTablePos];

    // Allocate a new HookTable, if we don't have one allocated yet
    if (table->alloc == 0) {
        table->alloc = gHookTablePos == 0 ? 128 : 32;
        table->hooktbl = (HookTableEntry *)memAllocBlk(table->alloc * sizeof(HookTableEntry));
        memset(table->hooktbl, 0, table->alloc * sizeof(HookTableEntry));
    }
    // Let's re-use the one we have
    else
        table->size = 0;
}

// Double the size of the current hook table
void nametblHookGrow() {
    HookTable *tablemeta = &gHookTables[gHookTablePos];
    HookTableEntry *oldtable = tablemeta->hooktbl;
    int oldsize = tablemeta->alloc;
    tablemeta->alloc <<= 1;
    tablemeta->hooktbl = (HookTableEntry *)memAllocBlk(tablemeta->alloc * sizeof(HookTableEntry));
    memset(tablemeta->hooktbl, 0, tablemeta->alloc * sizeof(HookTableEntry));
    memcpy(tablemeta->hooktbl, oldtable, oldsize * sizeof(HookTableEntry));
}

// Hook a name + node in the current hooktable
void nametblHookNode(Name *name, INode *node) {
    // Only hook if we have a hook table (we don't for core lib)
    if (gHookTableSize > 0) {
        HookTable *tablemeta = &gHookTables[gHookTablePos];
        if (tablemeta->size + 1 >= tablemeta->alloc)
            nametblHookGrow();
        HookTableEntry *entry = &tablemeta->hooktbl[tablemeta->size++];
        entry->node = name->node; // Save previous node
        entry->name = name;
    }
    name->node = node; // Plug in new node
}

// Hook all of a namespace's names and nodes in the current hooktable
void nametblHookNamespace(Namespace *ns) {
    namespaceFor(ns) {
        NameNode *nn = &ns->namenodes[__i];
        if (nn->name == NULL)
            continue;
        nametblHookNode(nn->name, nn->node);
    }
}

// Unhook all names in current hooktable, then revert to the prior hooktable
void nametblHookPop() {
    HookTable *tablemeta = &gHookTables[gHookTablePos];
    HookTableEntry *entry = tablemeta->hooktbl;
    int cnt = tablemeta->size;
    while (cnt--) {
        entry->name->node = entry->node;
        ++entry;
    }
    --gHookTablePos;
}
