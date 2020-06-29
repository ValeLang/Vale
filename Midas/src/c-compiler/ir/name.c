/** Name handling
 *
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "ir.h"
#include "../shared/memory.h"

#include <stdint.h>
#include <string.h>

Name *anonName;
Name *selfName;
Name *selfTypeName;
Name *thisName;
Name *cloneName;
Name *finalName;
Name *plusEqName;
Name *minusEqName;
Name *multEqName;
Name *divEqName;
Name *remEqName;
Name *orEqName;
Name *andEqName;
Name *xorEqName;
Name *shlEqName;
Name *shrEqName;
Name *lessDashName;
Name *plusName;
Name *minusName;
Name *istrueName;
Name *multName;
Name *divName;
Name *remName;
Name *orName;
Name *andName;
Name *xorName;
Name *shlName;
Name *shrName;
Name *incrName;
Name *decrName;
Name *incrPostName;
Name *decrPostName;
Name *eqName;
Name *neName;
Name *leName;
Name *ltName;
Name *geName;
Name *gtName;
Name *parensName;
Name *indexName;
Name *refIndexName;
Name *optionName;

void nameConcatPrefix(char **prefix, char *name) {
    size_t size = strlen(*prefix) + strlen(name) + 1;
    char *newprefix = memAllocStr(*prefix, size);
    strcat(newprefix, name);
    strcat(newprefix, "_");
    *prefix = newprefix;
}

// Create globally unique variable name, prefixed by module/type name
void nameGenVarName(VarDclNode *node, char *prefix) {
    // Known name is fine if extern or in main module
    if (*prefix == '\0' || (node->flags & FlagExtern))
        return;

    char *namestr = node->genname;
    size_t size = strlen(prefix) + strlen(namestr);
    node->genname = memAllocStr(prefix, size);
    strcat(node->genname, namestr);
}

// Create globally unique function name, prefixed by module/type name
// Note: Any necessary mangling will happen at gen time
void nameGenFnName(FnDclNode *node, char *prefix) {
    // Known name is fine if extern or in main module
    if (*prefix == '\0' || (node->flags & FlagExtern))
        return;

    char *namestr = node->genname;
    size_t size = strlen(prefix) + strlen(namestr);
    node->genname = memAllocStr(prefix, size);
    strcat(node->genname, namestr);
}
