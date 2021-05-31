
#include <region/common/common.h>
#include <function/expressions/shared/shared.h>
#include <function/expressions/shared/string.h>
#include "linearstructs.h"


LinearStructs::LinearStructs(GlobalState* globalState_)
  : globalState(globalState_),
    interfaceRefStructsL(0, globalState->addressNumberer->makeHasher<InterfaceReferend*>()),
    structStructsL(0, globalState->addressNumberer->makeHasher<StructReferend*>()),
    staticSizedArrayStructsL(0, globalState->addressNumberer->makeHasher<StaticSizedArrayT*>()),
    runtimeSizedArrayStructsL(0, globalState->addressNumberer->makeHasher<RuntimeSizedArrayT*>()),
    orderedStructsByInterface(0, globalState_->addressNumberer->makeHasher<InterfaceReferend*>()) {

//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  stringStructLT = LLVMStructCreateNamed(globalState->context, "ValeStr");
  std::vector<LLVMTypeRef> memberTypesL;
  memberTypesL.push_back(LLVMInt64TypeInContext(globalState->context));
  memberTypesL.push_back(LLVMArrayType(LLVMInt8TypeInContext(globalState->context), 0));
  LLVMStructSetBody(stringStructLT, memberTypesL.data(), memberTypesL.size(), false);
}

LLVMTypeRef LinearStructs::getStructStruct(StructReferend* structReferend) {
  auto structIter = structStructsL.find(structReferend);
  if (structIter == structStructsL.end()) {
    std::cerr << "Don't have the struct struct for " << structReferend->fullName->name << std::endl;
    exit(1);
  }
  return structIter->second;
}

LLVMTypeRef LinearStructs::getStaticSizedArrayStruct(StaticSizedArrayT* ssaMT) {
  auto structIter = staticSizedArrayStructsL.find(ssaMT);
  assert(structIter != staticSizedArrayStructsL.end());
  return structIter->second;
}

LLVMTypeRef LinearStructs::getRuntimeSizedArrayStruct(RuntimeSizedArrayT* rsaMT) {
  auto structIter = runtimeSizedArrayStructsL.find(rsaMT);
  assert(structIter != runtimeSizedArrayStructsL.end());
  return structIter->second;
}

LLVMTypeRef LinearStructs::getInterfaceRefStruct(InterfaceReferend* interfaceReferend) {
  auto iter = interfaceRefStructsL.find(interfaceReferend);
  assert(iter != interfaceRefStructsL.end());
  return iter->second;
}

LLVMTypeRef LinearStructs::getStringStruct() {
  return stringStructLT;
}

void LinearStructs::declareStruct(StructReferend* structM) {
  auto structL =
      LLVMStructCreateNamed(
          globalState->context, structM->fullName->name.c_str());
  assert(structStructsL.count(structM) == 0);
  structStructsL.emplace(structM, structL);
}

void LinearStructs::declareEdge(StructReferend* structReferend, InterfaceReferend* interfaceReferend) {
  // There aren't edges per se, just tag numbers. That's all we have to do here.

  // Creates one if it doesnt already exist.
  auto* os = &orderedStructsByInterface[interfaceReferend];

  assert(std::count(os->begin(), os->end(), structReferend) == 0);
  os->push_back(structReferend);
}

void LinearStructs::declareInterface(InterfaceReferend* interface) {
  assert(interfaceRefStructsL.count(interface) == 0);

  auto interfaceRefStructL =
      LLVMStructCreateNamed(
          globalState->context, interface->fullName->name.c_str());

  std::vector<LLVMTypeRef> memberTypesL = {
      LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0),
      LLVMInt64TypeInContext(globalState->context),
  };
  LLVMStructSetBody(interfaceRefStructL, memberTypesL.data(), memberTypesL.size(), false);

  interfaceRefStructsL.emplace(interface, interfaceRefStructL);

  // No need to make interface table structs, there are no itables for Linear.
}

void LinearStructs::declareStaticSizedArray(
    StaticSizedArrayT* staticSizedArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(globalState->context, staticSizedArrayMT->name->name.c_str());
  staticSizedArrayStructsL.emplace(staticSizedArrayMT, countedStruct);
}

void LinearStructs::declareRuntimeSizedArray(
    RuntimeSizedArrayT* runtimeSizedArrayMT) {
  auto countedStruct = LLVMStructCreateNamed(globalState->context, (runtimeSizedArrayMT->name->name + "rc").c_str());
  runtimeSizedArrayStructsL.emplace(runtimeSizedArrayMT, countedStruct);
}

void LinearStructs::defineStruct(
    StructReferend* struuct,
    std::vector<LLVMTypeRef> membersLT) {
  LLVMTypeRef structL = getStructStruct(struuct);
  LLVMStructSetBody(structL, membersLT.data(), membersLT.size(), false);
}

void LinearStructs::defineInterface(InterfaceReferend *interface) {
}

void LinearStructs::defineEdge(
    Edge* edge,
    std::vector<LLVMTypeRef> interfaceFunctionsLT,
    std::vector<LLVMValueRef> functions) {
}

void LinearStructs::defineRuntimeSizedArray(
    RuntimeSizedArrayT* runtimeSizedArrayMT,
    LLVMTypeRef elementLT) {
  auto runtimeSizedArrayStruct = getRuntimeSizedArrayStruct(runtimeSizedArrayMT);
  std::vector<LLVMTypeRef> elementsL;
  elementsL.push_back(LLVMInt64TypeInContext(globalState->context));
  elementsL.push_back(LLVMArrayType(elementLT, 0));
  LLVMStructSetBody(runtimeSizedArrayStruct, elementsL.data(), elementsL.size(), false);
}

void LinearStructs::defineStaticSizedArray(
    StaticSizedArrayT* staticSizedArrayMT,
    int size,
    LLVMTypeRef elementLT) {
  auto staticSizedArrayStruct = getStaticSizedArrayStruct(staticSizedArrayMT);
  auto innerArrayLT = LLVMArrayType(elementLT, size);

  std::vector<LLVMTypeRef> elementsL;
  elementsL.push_back(innerArrayLT);
  LLVMStructSetBody(staticSizedArrayStruct, elementsL.data(), elementsL.size(), false);
}


InterfaceFatPtrLE LinearStructs::makeInterfaceFatPtr(
    AreaAndFileAndLine checkerAFL,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* referenceM_,
    LLVMValueRef ptrLE) {
  auto interfaceReferendM = dynamic_cast<InterfaceReferend*>(referenceM_->referend);
  assert(interfaceReferendM);
  assert(LLVMTypeOf(ptrLE) == getInterfaceRefStruct(interfaceReferendM));

  auto interfaceFatPtrLE = InterfaceFatPtrLE(referenceM_, ptrLE);

  // This is actually a tag number, not a table pointer
  auto tagLE = getTablePtrFromInterfaceRef(builder, interfaceFatPtrLE);

  return interfaceFatPtrLE;
}

LLVMValueRef LinearStructs::getStringBytesPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  auto charsArrayPtrLE = LLVMBuildStructGEP(builder, ptrLE, 1, "charsPtr");

  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0), constI64LE(globalState, 0) };
  auto firstCharPtrLE =
      LLVMBuildGEP(
          builder, charsArrayPtrLE, indices.data(), indices.size(), "elementPtr");
  assert(LLVMTypeOf(firstCharPtrLE) == LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0));
  return firstCharPtrLE;
}

LLVMValueRef LinearStructs::getRuntimeSizedArrayElementsPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  return LLVMBuildStructGEP(builder, ptrLE, 1, "elementsPtr");
}

LLVMValueRef LinearStructs::getStaticSizedArrayElementsPtr(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  return LLVMBuildStructGEP(builder, ptrLE, 0, "elementsPtr");
}

LLVMValueRef LinearStructs::getStringLen(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef ptrLE) {
  auto lenPtrLE = LLVMBuildStructGEP(builder, ptrLE, 0, "lenPtr");
  return LLVMBuildLoad(builder, lenPtrLE, "len");
}
