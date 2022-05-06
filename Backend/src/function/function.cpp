#include <iostream>
#include <utils/definefunction.h>
#include "expressions/shared/shared.h"
#include "../region/linear/linear.h"

#include "../translatetype.h"

#include "function.h"
#include "expression.h"
#include "boundary.h"

LLVMValueRef declareFunction(
    GlobalState* globalState,
    Function* functionM) {

  auto valeParamTypesL = translateTypes(globalState, functionM->prototype->params);
  auto valeReturnTypeL =
      globalState->getRegion(functionM->prototype->returnType)
          ->translateType(functionM->prototype->returnType);
  LLVMTypeRef valeFunctionTypeL =
      LLVMFunctionType(valeReturnTypeL, valeParamTypesL.data(), valeParamTypesL.size(), 0);

  auto valeFunctionNameL = functionM->prototype->name->name;

  LLVMValueRef valeFunctionL = LLVMAddFunction(globalState->mod, valeFunctionNameL.c_str(), valeFunctionTypeL);

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
    exportParamTypesL.push_back(
        LLVMPointerType(
            globalState->getRegion(functionM->prototype->returnType)->getExternalType(functionM->prototype->returnType),
            0));
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

  auto exportName = std::string("vale_abi_") + package->getFunctionExportName(functionM->prototype);

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

  FunctionState functionState(exportName, exportFunctionL, exportReturnLT, localsBuilder);
  BlockState initialBlockState(globalState->addressNumberer, nullptr, std::nullopt);
  buildFlare(FL(), globalState, &functionState, builder, "Calling export function ", functionState.containingFuncName, " from native");

  std::vector<Ref> argsToActualFunction;

  for (int logicalParamIndex = 0; logicalParamIndex < functionM->prototype->params.size(); logicalParamIndex++) {
    auto cParamIndex = logicalParamIndex + (usingReturnOutParam ? 1 : 0);

    auto valeParamMT = functionM->prototype->params[logicalParamIndex];
    auto hostParamMT =
        (valeParamMT->ownership == Ownership::SHARE ?
         globalState->linearRegion->linearizeReference(valeParamMT) :
         valeParamMT);
    auto cArgLE = LLVMGetParam(exportFunctionL, cParamIndex);;
    LLVMValueRef hostArgRefLE = nullptr;
    if (typeNeedsPointerParameter(globalState, valeParamMT)) {
      hostArgRefLE = LLVMBuildLoad(builder, cArgLE, "arg");
    } else {
      hostArgRefLE = cArgLE;
    }

    auto valeRef =
        receiveHostObjectIntoVale(
            globalState, &functionState, builder, hostParamMT, valeParamMT, hostArgRefLE);

    argsToActualFunction.push_back(valeRef);
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
        (valeReturnMT->ownership == Ownership::SHARE ?
         globalState->linearRegion->linearizeReference(valeReturnMT) :
         valeReturnMT);

    auto [hostReturnRefLE, hostReturnSizeLE] =
        sendValeObjectIntoHost(
            globalState, &functionState, builder, valeReturnMT, hostReturnMT, valeReturnRef);

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

LLVMValueRef declareExternFunction(
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

  LLVMTypeRef functionTypeL =
      LLVMFunctionType(externReturnLT, externParamTypesL.data(), externParamTypesL.size(), 0);
  LLVMValueRef functionL = LLVMAddFunction(globalState->mod, abiFuncNameL.c_str(), functionTypeL);

  assert(globalState->externFunctions.count(prototypeM->name->name) == 0);
  globalState->externFunctions.emplace(prototypeM->name->name, functionL);

  return functionL;
}

void translateFunction(
    GlobalState* globalState,
    Function* functionM) {

  auto functionL = globalState->getFunction(functionM->prototype->name);
  auto returnTypeL = globalState->getRegion(functionM->prototype->returnType)->translateType(functionM->prototype->returnType);

  auto localsBlockName = std::string("localsBlock");
  auto localsBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMBasicBlockRef localsBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, localsBlockName.c_str());
  LLVMPositionBuilderAtEnd(localsBuilder, localsBlockL);

  auto firstBlockName = std::string("codeStartBlock");
  LLVMBasicBlockRef firstBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, firstBlockName.c_str());
  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);

  FunctionState functionState(
      functionM->prototype->name->name, functionL, returnTypeL, localsBuilder);

  // There are other builders made elsewhere for various blocks in the function,
  // but this is the one for the top level.
  // It's not always pointed at firstBlockL, it can be re-pointed to other
  // blocks at the top level.
  //
  // For example, in:
  //   fn main() {
  //     x! = 5;
  //     if (true && true) {
  //       mut x = 7;
  //     } else {
  //       mut x = 8;
  //     }
  //     println(x);
  //   }
  // There are four blocks:
  // - 1: contains `x! = 5` and `true && true`
  // - 2: contains `mut x = 7;`
  // - 3: contains `mut x = 8;`
  // - 4: contains `println(x)`
  //
  // When it's done making block 1, we'll make block 4 and `bodyTopLevelBuilder`
  // will point at that.
  //
  // The point is, this builder can change to point at other blocks on the same
  // level.
  //
  // All builders work like this, at whatever level theyre on.

  BlockState initialBlockState(globalState->addressNumberer, nullptr, std::nullopt);

  buildFlare(FL(), globalState, &functionState, bodyTopLevelBuilder, "Inside function ", functionM->prototype->name->name);

  // Translate the body of the function. Can ignore the result because it's a
  // Never, because Valestrom guarantees we end function bodies in a ret.
  auto resultLE =
      translateExpression(
          globalState, &functionState, &initialBlockState, bodyTopLevelBuilder, functionM->block);

  initialBlockState.checkAllIntroducedLocalsWereUnstackified();

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

//  // This is a total hack, to try and appease LLVM to say that yes, we're sure
//  // we'll never reach this statement.
//  // In .ll we can call a noreturn function and then put an unreachable block,
//  // but I can't figure out how to specify noreturn with the LLVM C API.
//  if (dynamic_cast<Never*>(functionM->prototype->returnType->kind)) {
//    LLVMBuildRet(bodyTopLevelBuilder, LLVMGetUndef(region->translateType(functionM->prototype->returnType)));
//  }

  LLVMDisposeBuilder(bodyTopLevelBuilder);
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

  auto functionLT = LLVMFunctionType(returnTypeLT, paramsLT.data(), paramsLT.size(), false);
  auto functionL = LLVMAddFunction(globalState->mod, llvmName.c_str(), functionLT);
  // Don't define it yet, we're just declaring them right now.
  globalState->extraFunctions.emplace(std::make_pair(prototype, functionL));
}

void defineFunctionBodyV(
    GlobalState* globalState,
    Prototype* prototype,
    std::function<void(FunctionState*, LLVMBuilderRef)> definer) {
  auto functionL = globalState->lookupFunction(prototype);
  auto retType = globalState->getRegion(prototype->returnType)->translateType(prototype->returnType);
  defineFunctionBody(
      globalState,
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
