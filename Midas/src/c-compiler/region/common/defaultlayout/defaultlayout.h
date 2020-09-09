#ifndef REGION_COMMON_DEFAULTLAYOUT_DEFAULTLAYOUT_H_
#define REGION_COMMON_DEFAULTLAYOUT_DEFAULTLAYOUT_H_

#include <globalstate.h>
#include <function/expressions/shared/weaks.h>

class DefaultLayoutter {
public:
  DefaultLayoutter(GlobalState* globalState_) : globalState(globalState_) {}

  void translateStruct(
      Name* name,
      Mutability mutability,
      Weakability weakable,
      std::vector<LLVMTypeRef> membersLT) {
    LLVMTypeRef valStructL = globalState->getInnerStruct(name);
    LLVMStructSetBody(
        valStructL, membersLT.data(), membersLT.size(), false);

    LLVMTypeRef wrapperStructL = globalState->getWrapperStruct(name);
    std::vector<LLVMTypeRef> wrapperStructMemberTypesL;

    // First member is a ref counts struct. We don't include the int directly
    // because we want fat pointers to point to this struct, so they can reach
    // into it and increment without doing any casting.
    if (mutability == Mutability::MUTABLE) {
      if (weakable == Weakability::WEAKABLE) {
        wrapperStructMemberTypesL.push_back(globalState->mutWeakableControlBlockStructL);
      } else {
        wrapperStructMemberTypesL.push_back(globalState->mutNonWeakableControlBlockStructL);
      }
    } else if (mutability == Mutability::IMMUTABLE) {
      wrapperStructMemberTypesL.push_back(globalState->immControlBlockStructL);
    } else
      assert(false);

    wrapperStructMemberTypesL.push_back(valStructL);

    LLVMStructSetBody(
        wrapperStructL, wrapperStructMemberTypesL.data(), wrapperStructMemberTypesL.size(), false);

    auto structWeakRefStructL = globalState->getStructWeakRefStruct(name);
    makeStructWeakRefStruct(globalState, structWeakRefStructL, wrapperStructL);
  }

  void declareStruct(StructDefinition* structM) {

    auto innerStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), structM->name->name.c_str());
    assert(globalState->innerStructs.count(structM->name->name) == 0);
    globalState->innerStructs.emplace(structM->name->name, innerStructL);

    auto wrapperStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (structM->name->name + "rc").c_str());
    assert(globalState->wrapperStructs.count(structM->name->name) == 0);
    globalState->wrapperStructs.emplace(structM->name->name, wrapperStructL);

    auto structWeakRefStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (structM->name->name + "w").c_str());
    assert(globalState->structWeakRefStructs.count(structM->name->name) == 0);
    globalState->structWeakRefStructs.emplace(structM->name->name, structWeakRefStructL);
  }


  void declareEdge(
      Edge* edge) {

    auto interfaceTableStructL =
        globalState->getInterfaceTableStruct(edge->interfaceName->fullName);

    auto edgeName =
        edge->structName->fullName->name + edge->interfaceName->fullName->name;
    auto itablePtr =
        LLVMAddGlobal(globalState->mod, interfaceTableStructL, edgeName.c_str());
    LLVMSetLinkage(itablePtr, LLVMExternalLinkage);

    globalState->interfaceTablePtrs.emplace(edge, itablePtr);
  }

  void translateEdge(
      Edge* edge,
      std::vector<LLVMValueRef> functions) {

    auto interfaceTableStructL =
        globalState->getInterfaceTableStruct(edge->interfaceName->fullName);
    auto builder = LLVMCreateBuilder();
    auto itableLE = LLVMGetUndef(interfaceTableStructL);
    for (int i = 0; i < functions.size(); i++) {
      itableLE = LLVMBuildInsertValue(
          builder,
          itableLE,
          functions[i],
          i,
          std::to_string(i).c_str());
    }
    LLVMDisposeBuilder(builder);

    auto itablePtr = globalState->getInterfaceTablePtr(edge);
    LLVMSetInitializer(itablePtr,  itableLE);
  }

  void declareInterface(Name* name) {

    auto interfaceRefStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), name->name.c_str());
    assert(globalState->interfaceRefStructs.count(name->name) == 0);
    globalState->interfaceRefStructs.emplace(name->name, interfaceRefStructL);

    auto interfaceTableStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (name->name + "itable").c_str());
    assert(globalState->interfaceTableStructs.count(name->name) == 0);
    globalState->interfaceTableStructs.emplace(name->name, interfaceTableStructL);

    auto interfaceWeakRefStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (name->name + "w").c_str());
    assert(globalState->interfaceWeakRefStructs.count(name->name) == 0);
    globalState->interfaceWeakRefStructs.emplace(name->name, interfaceWeakRefStructL);
  }

  void translateInterface(
      Name* name,
      Mutability mutability,
      Weakability weakability,
      std::vector<LLVMTypeRef> interfaceMethodTypesL) {
    LLVMTypeRef itableStruct =
        globalState->getInterfaceTableStruct(name);

    LLVMStructSetBody(
        itableStruct, interfaceMethodTypesL.data(), interfaceMethodTypesL.size(), false);

    LLVMTypeRef refStructL = globalState->getInterfaceRefStruct(name);
    std::vector<LLVMTypeRef> refStructMemberTypesL;

    // this points to the control block.
    // It makes it easier to increment and decrement ref counts.
    if (mutability == Mutability::MUTABLE) {
      if (weakability == Weakability::WEAKABLE) {
        refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutWeakableControlBlockStructL, 0));
      } else {
        refStructMemberTypesL.push_back(LLVMPointerType(globalState->mutNonWeakableControlBlockStructL, 0));
      }
    } else if (mutability == Mutability::IMMUTABLE) {
      refStructMemberTypesL.push_back(LLVMPointerType(globalState->immControlBlockStructL, 0));
    } else assert(false);


    refStructMemberTypesL.push_back(LLVMPointerType(itableStruct, 0));
    LLVMStructSetBody(
        refStructL,
        refStructMemberTypesL.data(),
        refStructMemberTypesL.size(),
        false);

    auto interfaceWeakRefStructL = globalState->getInterfaceWeakRefStruct(name);
    makeInterfaceWeakRefStruct(globalState, interfaceWeakRefStructL, refStructL);
  }


  void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) {

    auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), knownSizeArrayMT->name->name.c_str());
    globalState->knownSizeArrayWrapperStructs.emplace(knownSizeArrayMT->name->name, countedStruct).first;

    auto weakRefStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (knownSizeArrayMT->name->name + "w").c_str());
    assert(globalState->knownSizeArrayWeakRefStructs.count(knownSizeArrayMT->name->name) == 0);
    globalState->knownSizeArrayWeakRefStructs.emplace(knownSizeArrayMT->name->name, weakRefStructL);
  }

  void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) {
    auto countedStruct = LLVMStructCreateNamed(LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "rc").c_str());
    globalState->unknownSizeArrayWrapperStructs.emplace(unknownSizeArrayMT->name->name, countedStruct).first;

    auto weakRefStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), (unknownSizeArrayMT->name->name + "w").c_str());
    assert(globalState->unknownSizeArrayWeakRefStructs.count(unknownSizeArrayMT->name->name) == 0);
    globalState->unknownSizeArrayWeakRefStructs.emplace(unknownSizeArrayMT->name->name, weakRefStructL);
  }

  void translateUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT,
      LLVMTypeRef elementLT) {

    auto unknownSizeArrayWrapperStruct = globalState->getUnknownSizeArrayWrapperStruct(unknownSizeArrayMT->name);
    auto innerArrayLT = LLVMArrayType(elementLT, 0);

    std::vector<LLVMTypeRef> elementsL;

    if (unknownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
          globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else assert(false);
    } else if (unknownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
      elementsL.push_back(globalState->immControlBlockStructL);
    } else assert(false);

    elementsL.push_back(LLVMInt64Type());

    elementsL.push_back(innerArrayLT);

    LLVMStructSetBody(unknownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

    auto arrayWeakRefStructL = globalState->getUnknownSizeArrayWeakRefStruct(unknownSizeArrayMT->name);
    makeUnknownSizeArrayWeakRefStruct(globalState, unknownSizeArrayWrapperStruct, arrayWeakRefStructL);
  }

  void translateKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT,
      LLVMTypeRef elementLT) {
    auto knownSizeArrayWrapperStruct = globalState->getKnownSizeArrayWrapperStruct(knownSizeArrayMT->name);

    auto innerArrayLT = LLVMArrayType(elementLT, knownSizeArrayMT->size);

    std::vector<LLVMTypeRef> elementsL;

    if (knownSizeArrayMT->rawArray->mutability == Mutability::MUTABLE) {
      if (globalState->opt->regionOverride == RegionOverride::ASSIST ||
          globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::FAST) {
        elementsL.push_back(globalState->mutNonWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V0) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
        // In resilient mode, we can have weak refs to arrays
        elementsL.push_back(globalState->mutWeakableControlBlockStructL);
      } else assert(false);
    } else if (knownSizeArrayMT->rawArray->mutability == Mutability::IMMUTABLE) {
      elementsL.push_back(globalState->immControlBlockStructL);
    } else assert(false);

    elementsL.push_back(innerArrayLT);

    LLVMStructSetBody(knownSizeArrayWrapperStruct, elementsL.data(), elementsL.size(), false);

    auto arrayWeakRefStructL = globalState->getKnownSizeArrayWeakRefStruct(knownSizeArrayMT->name);
    makeKnownSizeArrayWeakRefStruct(globalState, knownSizeArrayWrapperStruct, arrayWeakRefStructL);
  }


private:
  GlobalState* globalState;
};

#endif