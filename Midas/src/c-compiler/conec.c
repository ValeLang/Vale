/** Main program file
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "coneopts.h"
#include "shared/fileio.h"
#include "genllvm/genllvm.h"


int main(int argc, char **argv) {
    ConeOptions coneopt;

    // Get compiler's options from passed arguments
    int ok = coneOptSet(&coneopt, &argc, argv);
    if (ok <= 0)
        exit(ok == 0 ? 0 : ExitOpts);
    if (argc < 2)
        errorExit(ExitOpts, "Specify a Cone program to compile.");
    coneopt.srcpath = argv[1];
    coneopt.srcname = fileName(coneopt.srcpath);

    // We set up generation early because we need target info, e.g.: pointer size
    GenState gen;
    genSetup(&gen, &coneopt);

    // Parse source file, do semantic analysis, and generate code
    ModuleNode *modnode = NULL;
    if (!errors)
        genMod(&gen, modnode);

    genClose(&gen);
    errorSummary();
}
