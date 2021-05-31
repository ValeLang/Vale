
#include <function/expressions/shared/shared.h>
#include <function/expressions/expressions.h>
#include "globalstate.h"
#include "translatetype.h"
#include "region/linear/linear.h"
#include "region/rcimm/rcimm.h"

GlobalState::GlobalState(AddressNumberer* addressNumberer_) :
    addressNumberer(addressNumberer_),
    interfaceTablePtrs(0, addressNumberer->makeHasher<Edge*>()),
    interfaceExtraMethods(0, addressNumberer->makeHasher<InterfaceReferend*>()),
    overridesBySubstructByInterface(0, addressNumberer->makeHasher<InterfaceReferend*>()),
    extraFunctions(0, addressNumberer->makeHasher<Prototype*>()),
    regions(0, addressNumberer->makeHasher<RegionId*>()),
    regionIdByReferend(0, addressNumberer->makeHasher<Referend*>())
{}

std::vector<LLVMTypeRef> GlobalState::getInterfaceFunctionTypes(InterfaceReferend* referend) {
  auto interfaceDefMIter = program->interfaces.find(referend->fullName->name);
  std::vector<LLVMTypeRef> interfaceFunctionsLT;
  if (interfaceDefMIter != program->interfaces.end()) {
    auto interfaceDefM = interfaceDefMIter->second;
    for (auto method : interfaceDefM->methods) {
      auto interfaceFunctionLT = translateInterfaceMethodToFunctionType(this, method);
      interfaceFunctionsLT.push_back(LLVMPointerType(interfaceFunctionLT, 0));
    }
  }
  for (auto interfaceExtraMethod : interfaceExtraMethods[referend]) {
    auto interfaceFunctionLT =
        translateInterfaceMethodToFunctionType(this, interfaceExtraMethod);
    interfaceFunctionsLT.push_back(LLVMPointerType(interfaceFunctionLT, 0));
  }

  return interfaceFunctionsLT;
}

std::vector<LLVMValueRef> GlobalState::getEdgeFunctions(Edge* edge) {
  auto interfaceM = program->getInterface(edge->interfaceName->fullName);

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
  return getRegion(referenceM->referend);
}

IRegion* GlobalState::getRegion(Referend* referendM) {
  if (auto innt = dynamic_cast<Int*>(referendM)) {
    return getRegion(innt->regionId);
  } else if (auto boool = dynamic_cast<Bool*>(referendM)) {
    return getRegion(boool->regionId);
  } else if (auto flooat = dynamic_cast<Float*>(referendM)) {
    return getRegion(flooat->regionId);
  } else if (auto never = dynamic_cast<Never*>(referendM)) {
    return getRegion(never->regionId);
  } else if (auto str = dynamic_cast<Str*>(referendM)) {
    return getRegion(str->regionId);
  } else {
    auto iter = regionIdByReferend.find(referendM);
    assert(iter != regionIdByReferend.end());
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
  return wrap(getRegion(metalCache->intRef), metalCache->intRef, constI64LE(this, x));
}
Ref GlobalState::constI1(bool b) {
  return wrap(getRegion(metalCache->boolRef), metalCache->boolRef, constI1LE(this, b));
}
Ref GlobalState::buildAdd(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->intRef;
  auto addPrototype = metalCache->getPrototype(metalCache->getName("__addIntInt"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}
Ref GlobalState::buildMod(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->intRef;
  auto addPrototype = metalCache->getPrototype(metalCache->getName("__mod"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}
Ref GlobalState::buildDivide(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->intRef;
  auto addPrototype = metalCache->getPrototype(metalCache->getName("__divideIntInt"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}

Ref GlobalState::buildMultiply(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b) {
  auto intMT = metalCache->intRef;
  auto addPrototype = metalCache->getPrototype(metalCache->getName("__multiplyIntInt"), intMT, {intMT, intMT});
  return buildExternCall(this, functionState, builder, addPrototype, { a, b });
}

Name* GlobalState::getReferendName(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return structReferend->fullName;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return interfaceReferend->fullName;
  } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(referend)) {
    return ssaMT->name;
  } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(referend)) {
    return rsaMT->name;
  } else assert(false);
  return nullptr;
}

Weakability GlobalState::getReferendWeakability(Referend* referend) {
  return getRegion(referend)->getReferendWeakability(referend);
}
