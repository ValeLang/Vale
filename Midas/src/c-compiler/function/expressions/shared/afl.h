#ifndef FUNCTION_EXPRESSIONS_SHARED_AFL_H_
#define FUNCTION_EXPRESSIONS_SHARED_AFL_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <functional>
#include <string>

//#include "metal/ast.h"
//#include "metal/instructions.h"
//#include "globalstate.h"
//#include "function/function.h"
//#include "utils/fileio.h"

struct AreaAndFileAndLine {
  std::string area;
  std::string file;
  int line;
};

// File and Line
#define FL() (AreaAndFileAndLine{ "", __FILE__, __LINE__ })
// Area and File and Line
#define AFL(area) (AreaAndFileAndLine{ (area), __FILE__, __LINE__ })

#endif
