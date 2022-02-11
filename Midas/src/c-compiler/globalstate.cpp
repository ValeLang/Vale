
#include <function/expressions/shared/shared.h>
#include <function/expressions/expressions.h>
#include "globalstate.h"
#include "translatetype.h"
#include "region/linear/linear.h"
#include "region/rcimm/rcimm.h"

GlobalState::GlobalState(AddressNumberer* addressNumberer_) :
    addressNumberer(addressNumberer_),
    interfaceTablePtrs(0, addressNumberer->makeHasher<Edge*>()),
    interfaceExtraMethods(0, addressNumberer->makeHasher<InterfaceKind*>()),
    overridesBySubstructByInterface(0, addressNumberer->makeHasher<InterfaceKind*>()),
    extraFunctions(0, addressNumberer->makeHasher<Prototype*>()),
    regions(0, addressNumberer->makeHasher<RegionId*>()),
    regionIdByKind(0, addressNumberer->makeHasher<Kind*>())
{}

std::vector<LLVMTypeRef> GlobalState::getInterfaceFunctionTypes(InterfaceKind* kind) {
  std::vector<LLVMTypeRef> interfaceFunctionsLT;
  if (auto maybeInterfaceDefM = program->getMaybeInterface(kind)) {
    auto interfaceDefM = *maybeInterfaceDefM;
    for (auto method : interfaceDefM->methods) {
      auto interfaceFunctionLT = translateInterfaceMethodToFunctionType(this, method);
      interfaceFunctionsLT.push_back(LLVMPointerType(interfaceFunctionLT, 0));
    }
  }
  for (auto interfaceExtraMethod : interfaceExtraMethods[kind]) {
    auto interfaceFunctionLT =
        translateInterfaceMethodToFunctionType(this, interfaceExtraMethod);
    interfaceFunctionsLT.push_back(LLVMPointerType(interfaceFunctionLT, 0));
  }

  return interfaceFunctionsLT;
}

std::vector<LLVMValueRef> GlobalState::getEdgeFunctions(Edge* edge) {
  auto interfaceM = program->getInterface(edge->interfaceName);

  assert(edge->structPrototypesByInterfaceMethod.size() == interfaceM->methods.size());

  std::vector<LLVMValueRef> edgeFunctionsL;

  {
    auto overridesBySubstructI = overridesBySubstructByInterface.find(edge->interfaceName);
    if (overridesBySubstructI != overridesBySubstructByInterface.end()) {
      auto overridesI = overridesBySubstructI->second.find(edge->structName);
      if (overridesI != overridesBySubstructI->second.end()) {
        auto overrides = overridesI->second;
        assert(overrides.size() == interfaceExtraMethods[edge->interfaceName].size());
      }
    }
  }

  for (int i = 0; i < edge->structPrototypesByInterfaceMethod.size(); i++) {
    assert(edge->structPrototypesByInterfaceMethod[i].first == interfaceM->methods[i]);

    auto funcName = edge->structPrototypesByInterfaceMethod[i].second->name;
    auto edgeFunctionL = getFunction(funcName);
    edgeFunctionsL.push_back(edgeFunctionL);
  }

  {
    auto &extraInterfaceMethods = interfaceExtraMethods[edge->interfaceName];
    auto overridesBySubstructI = overridesBySubstructByInterface.find(edge->interfaceName);
    if (overridesBySubstructI != overridesBySubstructByInterface.end()) {
      auto overridesBySubstruct = overridesBySubstructI->second;
      auto overridesI = overridesBySubstruct.find(edge->structName);
      if (overridesI != overridesBySubstruct.end()) {
        auto &extraOverrides = overridesI->second;
        assert(extraInterfaceMethods.size() == extraOverrides.size());
        for (int i = 0; i < extraInterfaceMethods.size(); i++) {
          assert(extraOverrides[i].first == extraInterfaceMethods[i]);

          auto prototype = extraOverrides[i].second;
          auto edgeFunctionL = extraFunctions.find(prototype)->second;
          edgeFunctionsL.push_back(edgeFunctionL);
        }
      }
    }
  }

  return edgeFunctionsL;
}

IRegion* GlobalState::getRegion(Reference* referenceM) {
  return getRegion(referenceM->kind);
}

IRegion* GlobalState::getRegion(Kind* kindM) {
  if (auto innt = dynamic_cast<Int*>(kindM)) {
    return getRegion(innt->regionId);
  } else if (auto vooid = dynamic_cast<Void*>(kindM)) {
    return getRegion(vooid->regionId);
  } else if (auto boool = dynamic_cast<Bool*>(kindM)) {
    return getRegion(boool->regionId);
  } else if (auto flooat = dynamic_cast<Float*>(kindM)) {
    return getRegion(flooat->regionId);
  } else if (auto never = dynamic_cast<Never*>(kindM)) {
    return getRegion(never->regionId);
  } else if (auto str = dynamic_cast<Str*>(kindM)) {
    return getRegion(str->regionId);
  } else {
    auto iter = regionIdByKind.find(kindM);
    if (iter == regionIdByKind.end()) {
      std::cerr << "Couldn't find region for: " << typeid(*kindM).name() << std::endl;
      exit(1);
    }
    auto regionId = iter->second;
    return getRegion(regionId);
  }
}

IRegion* GlobalState::getRegion(RegionId* regionId) {
  if (regionId == metalCache->rcImmRegionId) {
    return rcImm;
  } else if (regionId == metalCache->linearRegionId) {
    return linearRegion;
  } else if (regionId == metalCache->unsafeRegionId) {
    return unsafeRegion;
  } else if (regionId == metalCache->assistRegionId) {
    return assistRegion;
  } else if (regionId == metalCache->naiveRcRegionId) {
    return naiveRcRegion;
  } else if (regionId == metalCache->resilientV3RegionId) {
    return resilientV3Region;
  } else if (regionId == metalCache->resilientV4RegionId) {
    return resilientV4Region;
  } else {
    assert(false);
  }
}

LLVMValueRef GlobalState::getFunction(Name* name) {
  auto functionIter = functions.find(name->name);
  assert(functionIter != functions.end());
  return functionIter->second;
}

LLVMValueRef GlobalState::getInterfaceTablePtr(Edge* edge) {
  auto iter = interfaceTablePtrs.find(edge);
  assert(iter != interfaceTablePtrs.end());
  return iter->second;
}
LLVMValueRef GlobalState::getOrMakeStringConstant(const std::string& str) {
  auto iter = stringConstants.find(str);
  if (iter == stringConstants.end()) {

    iter =
        stringConstants.emplace(
                str,
                LLVMBuildGlobalStringPtr(
                    stringConstantBuilder,
                    str.c_str(),
                    (std::string("conststr") + std::to_string(stringConstants.size())).c_str()))
            .first;
  }
  return iter->second;
}

Ref GlobalState::constI64(int64_t x) {
  return wrap(getRegion(metalCache->i64Ref), metalCache->i64Ref, constI64LE(this, x));
}
Ref GlobalState::constI32(int32_t x) {
  return wrap(getRegion(metalCache->i32Ref), metalCache->i32Ref, constI32LE(this, x));
}
Ref GlobalState::constI1(bool b) {
  return wrap(getRegion(metalCache->boolRef), metalCache->boolRef, constI1LE(this, b));
}
Ref GlobalState::buildAdd(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->i32Ref;
  auto addPrototype = metalCache->getPrototype(metalCache->getName(metalCache->builtinPackageCoord, "__addI32"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}
Ref GlobalState::buildMod(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->i32Ref;
  auto addPrototype = metalCache->getPrototype(metalCache->getName(metalCache->builtinPackageCoord, "__mod"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}
Ref GlobalState::buildDivide(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->i32Ref;
  auto addPrototype = metalCache->getPrototype(metalCache->getName(metalCache->builtinPackageCoord, "__divideI32"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}

Ref GlobalState::buildMultiply(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->i32Ref;
  auto addPrototype = metalCache->getPrototype(metalCache->getName(metalCache->builtinPackageCoord, "__multiplyIntInt"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}

Name* GlobalState::getKindName(Kind* kind) {
  if (auto structKind = dynamic_cast<StructKind*>(kind)) {
    return structKind->fullName;
  } else if (auto interfaceKind = dynamic_cast<InterfaceKind*>(kind)) {
    return interfaceKind->fullName;
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
    return ssaMT->name;
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
    return rsaMT->name;
  } else assert(false);
  return nullptr;
}

Weakability GlobalState::getKindWeakability(Kind* kind) {
  return getRegion(kind)->getKindWeakability(kind);
}
