#ifndef READ_JSON_H_
#define READ_JSON_H_

#include <nlohmann/json.hpp>

#include "types.h"
#include "ast.h"

Program* readProgram(const nlohmann::json& program);

#endif
