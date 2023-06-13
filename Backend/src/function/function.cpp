#include <iostream>
#include <utils/definefunction.h>
#include "expressions/shared/shared.h"
#include "../region/linear/linear.h"

#include "../translatetype.h"

#include "function.h"
#include "expression.h"
#include "boundary.h"
#include "utils/randomgeneration.h"
#include <region/common/migration.h>
#include <utils/counters.h>

ValeFuncPtrLE declareFunction(
    GlobalState* globalState,
    Function* functionM) {

  auto valeParamTypesL = translateTypes(globalState, functionM->prototype->params);
  auto valeReturnTypeL =
      globalState->getRegion(functionM->prototype->returnType)
          ->translateType(functionM->prototype->returnType);

  auto valeFunctionNameL = functionM->prototype->name->name;
  if (valeFunctionNameL == "main") {
    // Otherwise we conflict with the main that we create for setting up things like replaying
    valeFunctionNameL = ":main";
  }
  auto valeFunctionL =
      addValeFunction(globalState, valeFunctionNameL.c_str(), valeReturnTypeL, valeParamTypesL);

  assert(globalState->functions.count(functionM->prototype->name->name) == 0);
  globalState->functions.emplace(functionM->prototype->name->name, valeFunctionL);

  return valeFunctionL;
}

bool translatesToCVoid(GlobalState* globalState, Reference* returnMT) {
  if (returnMT == globalState->metalCache->neverRef) {
    return true;
  } else if (returnMT == globalState->metalCache->voidRef) {
    return true;
  } else {
    return false;
  }
}
bool typeNeedsPointerParameter(GlobalState* globalState, Reference* returnMT) {
  if (translatesToCVoid(globalState, returnMT)) {
    return false;
  }
  auto logicalReturnTypeL = globalState->getRegion(returnMT)->getExternalType(returnMT);
  if (LLVMGetTypeKind(logicalReturnTypeL) == LLVMStructTypeKind) {
    return true;
  } else {
    return false;
  }
}

LLVMTypeRef translateExternReturnType(GlobalState* globalState, Reference* returnMT) {
  if (returnMT == globalState->metalCache->neverRef) {
    return LLVMVoidTypeInContext(globalState->context);
  } else if (returnMT == globalState->metalCache->voidRef) {
    return LLVMVoidTypeInContext(globalState->context);
  } else {
    auto logicalReturnTypeL = globalState->getRegion(returnMT)->getExternalType(returnMT);
    if (LLVMGetTypeKind(logicalReturnTypeL) == LLVMStructTypeKind) {
      // We'll have an out-parameter for it instead.
      return LLVMVoidTypeInContext(globalState->context);
    } else {
      return logicalReturnTypeL;
    }
  }
}

void exportFunction(GlobalState* globalState, Package* package, Function* functionM) {
  LLVMTypeRef exportReturnLT = translateExternReturnType(globalState, functionM->prototype->returnType);

  bool usingReturnOutParam = typeNeedsPointerParameter(globalState, functionM->prototype->returnType);
  std::vector<LLVMTypeRef> exportParamTypesL;
  if (usingReturnOutParam) {
    auto exportParamLT =
        globalState->getRegion(functionM->prototype->returnType)->getExternalType(functionM->prototype->returnType);
    exportParamTypesL.push_back(LLVMPointerType(exportParamLT, 0));
  }
  // We may have added an out-parameter above for the return.
  // Now add the actual parameters.
  for (auto valeParamRefMT : functionM->prototype->params) {
    auto hostParamRefLT = globalState->getRegion(valeParamRefMT)->getExternalType(valeParamRefMT);
    if (typeNeedsPointerParameter(globalState, valeParamRefMT)) {
      exportParamTypesL.push_back(LLVMPointerType(hostParamRefLT, 0));
    } else {
      exportParamTypesL.push_back(hostParamRefLT);
    }
  }

  LLVMTypeRef exportFunctionTypeL =
      LLVMFunctionType(exportReturnLT, exportParamTypesL.data(), exportParamTypesL.size(), 0);

  auto unprefixedExportName = package->getFunctionExportName(functionM->prototype);
  auto exportName = std::string("vale_abi_") + unprefixedExportName;

  // The full name should end in _0, _1, etc. The exported name shouldnt.
  assert(exportName != functionM->prototype->name->name);
  // This is a thunk function that correctly aliases the objects that come in from the
  // outside world, and dealiases the object that we're returning to the outside world.
  LLVMValueRef exportFunctionL = LLVMAddFunction(globalState->mod, exportName.c_str(), exportFunctionTypeL);
  LLVMSetLinkage(exportFunctionL, LLVMExternalLinkage);

  LLVMBasicBlockRef block = LLVMAppendBasicBlockInContext(globalState->context, exportFunctionL, "entry");
  LLVMBuilderRef builder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(builder, block);
  // This is unusual because normally we have a separate localsBuilder which points to a separate
  // block at the beginning. This is a simple function which should require no locals, so this
  // should be fine.
  LLVMBuilderRef localsBuilder = builder;

  // Exported functions cant have their nextGen pointer passed in from the outside, so we need to
  // grab it from the global. We add a prime to the global too, so that we get different generations
  // each call.
  auto genLT = LLVMIntTypeInContext(globalState->context, globalState->opt->generationSize);
  auto newGenLE =
      adjustCounterReturnOld(
          builder,
          genLT,
          globalState->nextGenThreadGlobalIntLE,
          getRandomGenerationAddend(globalState->nextGenerationAddend++));
  auto nextGenLocalPtrLE = LLVMBuildAlloca(localsBuilder, genLT, "nextGenLocalPtr");
  LLVMBuildStore(builder, newGenLE, nextGenLocalPtrLE);

  FunctionState functionState(exportName, exportFunctionL, exportReturnLT, localsBuilder, nextGenLocalPtrLE);
  BlockState initialBlockState(globalState->addressNumberer, nullptr, std::nullopt);
  buildFlare(FL(), globalState, &functionState, builder, "Calling export function ", functionState.containingFuncName, " from native");

  std::vector<Ref> argsToActualFunction;

  for (int logicalParamIndex = 0; logicalParamIndex < functionM->prototype->params.size(); logicalParamIndex++) {
    auto cParamIndex = logicalParamIndex + (usingReturnOutParam ? 1 : 0);

    auto valeParamRefMT = functionM->prototype->params[logicalParamIndex];
    auto hostParamMT =
        ((valeParamRefMT->ownership == Ownership::MUTABLE_SHARE || valeParamRefMT->ownership == Ownership::IMMUTABLE_SHARE) ?
         globalState->linearRegion->linearizeReference(valeParamRefMT, true) :
         valeParamRefMT);
    // Doesn't include the pointifying, this is just the pointee. It's what we'll have after the
    // below if-statement.
    auto hostParamRefLT = globalState->getRegion(valeParamRefMT)->getExternalType(valeParamRefMT);

    // TODO: find a way to not rely on LLVMGetParam directly
    auto cArgLE = LLVMGetParam(exportFunctionL, cParamIndex);
    LLVMValueRef hostArgRefLE = nullptr;
    if (typeNeedsPointerParameter(globalState, valeParamRefMT)) {
      hostArgRefLE = LLVMBuildLoad2(builder, hostParamRefLT, cArgLE, "arg");
    } else {
      hostArgRefLE = cArgLE;
    }

    auto valeRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(valeParamRefMT)->createRegionInstanceLocal(&functionState, builder);
    auto hostRegionInstanceRef =
        globalState->linearRegion->createRegionInstanceLocal(
            &functionState, builder, constI1LE(globalState, 0), constI64LE(globalState, 0));

    auto valeRef =
        receiveHostObjectIntoVale(
            globalState, &functionState, builder, hostRegionInstanceRef, valeRegionInstanceRef, hostParamMT, valeParamRefMT, hostArgRefLE);

    argsToActualFunction.push_back(valeRef);

    // dont we have to free here
  }

  buildFlare(FL(), globalState, &functionState, builder, "Suspending export function ", functionState.containingFuncName);
  buildFlare(FL(), globalState, &functionState, builder, "Calling vale function ", functionM->prototype->name->name);
  auto valeReturnRefOrVoid =
      buildCallV(globalState, &functionState, builder, functionM->prototype, argsToActualFunction);
  buildFlare(FL(), globalState, &functionState, builder, "Done calling vale function ", functionM->prototype->name->name);
  buildFlare(FL(), globalState, &functionState, builder, "Resuming export function ", functionState.containingFuncName);

  if (functionM->prototype->returnType == globalState->metalCache->voidRef) {
    LLVMBuildRetVoid(builder);
  } else {
    auto valeReturnRef = valeReturnRefOrVoid;

    auto valeReturnMT = functionM->prototype->returnType;
    auto hostReturnMT =
        ((valeReturnMT->ownership == Ownership::MUTABLE_SHARE || valeReturnMT->ownership == Ownership::IMMUTABLE_SHARE) ?
         globalState->linearRegion->linearizeReference(valeReturnMT, true) :
         valeReturnMT);

    auto valeRegionInstanceRef =
        // At some point, look up the actual region instance, perhaps from the FunctionState?
        globalState->getRegion(valeReturnMT)->createRegionInstanceLocal(&functionState, builder);
    auto hostRegionInstanceRef =
        globalState->linearRegion->createRegionInstanceLocal(
            &functionState, builder, constI1LE(globalState, 0), constI64LE(globalState, 0));
    auto [hostReturnRefLE, hostReturnSizeLE] =
    sendValeObjectIntoHostAndDealias(
        globalState, &functionState, builder, valeRegionInstanceRef, hostRegionInstanceRef, valeReturnMT, hostReturnMT,
        valeReturnRef);

    buildFlare(FL(), globalState, &functionState, builder, "Done calling export function ", functionState.containingFuncName, " from native");

    if (usingReturnOutParam) {
      LLVMBuildStore(builder, hostReturnRefLE, LLVMGetParam(exportFunctionL, 0));
      LLVMBuildRetVoid(builder);
    } else {
      LLVMBuildRet(builder, hostReturnRefLE);
    }
  }

  LLVMDisposeBuilder(builder);
}

RawFuncPtrLE declareExternFunction(
    GlobalState* globalState,
    Package* package,
    Prototype* prototypeM) {
  LLVMTypeRef externReturnLT = translateExternReturnType(globalState, prototypeM->returnType);

  bool usingReturnOutParam = typeNeedsPointerParameter(globalState, prototypeM->returnType);
  std::vector<LLVMTypeRef> externParamTypesL;
  if (usingReturnOutParam) {
    externParamTypesL.push_back(
        LLVMPointerType(
            globalState->getRegion(prototypeM->returnType)->getExternalType(prototypeM->returnType),
            0));
  }
  // We may have added an out-parameter above for the return.
  // Now add the actual parameters.
  for (auto valeParamRefMT : prototypeM->params) {
    auto hostParamRefLT = globalState->getRegion(valeParamRefMT)->getExternalType(valeParamRefMT);
    if (typeNeedsPointerParameter(globalState, valeParamRefMT)) {
      externParamTypesL.push_back(LLVMPointerType(hostParamRefLT, 0));
    } else {
      externParamTypesL.push_back(hostParamRefLT);
    }
  }

  for (int i = 0; i < prototypeM->params.size(); i++) {
    if (includeSizeParam(globalState, prototypeM, i)) {
      externParamTypesL.push_back(LLVMInt32TypeInContext(globalState->context));
    }
  }

  auto userFuncNameL = package->getFunctionExternName(prototypeM);
  auto abiFuncNameL = std::string("vale_abi_") + userFuncNameL;

  RawFuncPtrLE functionL =
      addRawFunction(globalState->mod, abiFuncNameL.c_str(), externReturnLT, externParamTypesL);

  assert(globalState->externFunctions.count(prototypeM->name->name) == 0);
  globalState->externFunctions.emplace(prototypeM->name->name, functionL);

  return functionL;
}

void translateFunction(
    GlobalState* globalState,
    Function* functionM) {

  auto functionL = globalState->getFunction(functionM->prototype);
  auto returnTypeL =
      globalState->getRegion(functionM->prototype->returnType)->translateType(functionM->prototype->returnType);

  defineValeFunctionBody(
      globalState->context,
      functionL,
      returnTypeL,
      functionM->prototype->name->name,
      [globalState, functionM](FunctionState* functionState, LLVMBuilderRef builder) {
        BlockState initialBlockState(globalState->addressNumberer, nullptr, std::nullopt);

        // Translate the body of the function. Can ignore the result because it's a
        // Never, because Valestrom guarantees we end function bodies in a ret.
        auto resultLE =
            translateExpression(
                globalState, functionState, &initialBlockState, builder, functionM->block);

        initialBlockState.checkAllIntroducedLocalsWereUnstackified();
      });
}

void declareExtraFunction(
    GlobalState* globalState,
    Prototype* prototype,
    std::string llvmName) {
  auto returnTypeLT =
      globalState->getRegion(prototype->returnType)->translateType(prototype->returnType);

  std::vector<LLVMTypeRef> paramsLT;
  for (int i = 0; i < prototype->params.size(); i++) {
    auto paramMT = prototype->params[i];
    paramsLT.push_back(globalState->getRegion(paramMT)->translateType(paramMT));
  }

  auto functionL = addValeFunction(globalState, llvmName.c_str(), returnTypeLT, paramsLT);
  // Don't define it yet, we're just declaring them right now.
  globalState->extraFunctions.emplace(std::make_pair(prototype, functionL));
}

void defineFunctionBodyV(
    GlobalState* globalState,
    Prototype* prototype,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {
  auto functionL = globalState->lookupFunction(prototype);
  auto retType = globalState->getRegion(prototype->returnType)->translateType(prototype->returnType);
  defineValeFunctionBody(
      globalState->context,
      functionL,
      retType,
      prototype->name->name,
      definer);
}

void declareAndDefineExtraFunction(
    GlobalState* globalState,
    Prototype* prototype,
    std::string llvmName,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {
  declareExtraFunction(globalState, prototype, llvmName);
  defineFunctionBodyV(globalState, prototype, definer);
}


LLVMValueRef FunctionState::getParam(UserArgIndex userArgIndex) {
  if (nextGenPtrLE.has_value()) {
    return LLVMGetParam(containingFuncL, userArgIndex.userArgIndex + 1);
  } else {
    return LLVMGetParam(containingFuncL, userArgIndex.userArgIndex);
  }
}