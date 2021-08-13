#ifndef READ_JSON_H_
#define READ_JSON_H_

#include "json.hpp"

#include "metal/types.h"
#include "metal/ast.h"
#include "instructions.h"
#include "metalcache.h"

//Program* readProgram(MetalCache* cache, const nlohmann::json& program);
Package* readPackage(MetalCache* cache, const nlohmann::json& program);

#endif
