#ifndef READ_JSON_H_
#define READ_JSON_H_

#include "../json.hpp"

#include "types.h"
#include "ast.h"
#include "instructions.h"
#include "metalcache.h"

//Program* readProgram(MetalCache* cache, const nlohmann::json& program);
Package* readPackage(MetalCache* cache, const nlohmann::json& program);

#endif
