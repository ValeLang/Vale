/** Main program file
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include <cstdlib>
#include "error.h"

#include "coneopts.h"
#include "utils/fileio.h"
#include "genllvm.h"


int main(int argc, char **argv) {
    ConeOptions coneopt;

    // Get compiler's options from passed arguments
    int ok = coneOptSet(&coneopt, &argc, argv);
    if (ok <= 0) {
      exit((int)(ok == 0 ? ExitCode::Success : ExitCode::BadOpts));
    }
    if (argc < 2)
        errorExit(ExitCode::BadOpts, "Specify a Cone program to compile.");
    coneopt.srcpath = argv[1];
    new (&coneopt.srcDir) std::string(fileDirectory(coneopt.srcpath));
    new (&coneopt.srcNameNoExt) std::string(getFileName(coneopt.srcpath));
    new (&coneopt.srcDirAndNameNoExt) std::string(coneopt.srcDir + coneopt.srcNameNoExt);

    // We set up generation early because we need target info, e.g.: pointer size
    GenState gen;
    genSetup(&gen, &coneopt);

    // Parse source file, do semantic analysis, and generate code
//    ModuleNode *modnode = NULL;
//    if (!errors)
    genMod(&gen);

    genClose(&gen);
//    errorSummary();
}
