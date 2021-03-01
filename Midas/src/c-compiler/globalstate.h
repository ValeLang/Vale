#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include <metal/metalcache.h>
#include <region/common/defaultlayout/structs.h>

#include "metal/ast.h"
#include "metal/instructions.h"
#include "valeopts.h"
#include "addresshasher.h"

class IRegion;
class IReferendStructsSource;
class IWeakRefStructsSource;
class ControlBlock;
class Linear;
class RCImm;

constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_GEN = 0;
constexpr int LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE = 1;

class GlobalState {
public:
  GlobalState(AddressNumberer* addressNumberer);

  AddressNumberer* addressNumberer;

  LLVMTargetMachineRef machine = nullptr;
  LLVMContextRef context = nullptr;
  LLVMDIBuilderRef dibuilder = nullptr;
  LLVMMetadataRef compileUnit = nullptr;
  LLVMMetadataRef difile = nullptr;

  ValeOptions *opt = nullptr;

  LLVMTargetDataRef dataLayout = nullptr;
  LLVMModuleRef mod = nullptr;
  int ptrSize = 0;

  MetalCache* metalCache = nullptr;

  LLVMTypeRef ram64Struct = nullptr;

  Program* program = nullptr;

  LLVMValueRef numMainArgs = nullptr;
  LLVMValueRef mainArgs = nullptr;

  LLVMValueRef objIdCounter = nullptr;
  LLVMValueRef liveHeapObjCounter = nullptr;
  LLVMValueRef derefCounter = nullptr;
  LLVMValueRef mutRcAdjustCounter = nullptr;
  LLVMValueRef livenessCheckCounter = nullptr;
  // an i64 pointer to null.
  LLVMValueRef ram64 = nullptr;
  LLVMValueRef writeOnlyGlobal = nullptr;
  LLVMValueRef crashGlobal = nullptr;
  // Initialized to &writeOnlyGlobal / 8 in main.
  // We can use this to easily write an i64 into NULL or the write only global at runtime.
  LLVMValueRef ram64IndexToWriteOnlyGlobal = nullptr;
  LLVMValueRef malloc = nullptr, free = nullptr, assert = nullptr, exit = nullptr,
      assertI64Eq = nullptr, printCStr = nullptr,
      getch = nullptr, printInt = nullptr,
      strlen = nullptr, censusContains = nullptr, censusAdd = nullptr, censusRemove = nullptr;


  LLVMTypeRef wrcTableStructLT = nullptr;
  LLVMValueRef expandWrcTable = nullptr, checkWrci = nullptr, getNumWrcs = nullptr;

  LLVMTypeRef lgtTableStructLT, lgtEntryStructLT = nullptr; // contains generation and next free
  LLVMValueRef expandLgt = nullptr, checkLgti = nullptr, getNumLiveLgtEntries = nullptr;

  LLVMValueRef strncpy = nullptr;
  LLVMValueRef memcpy = nullptr;

  LLVMValueRef genMalloc = nullptr, genFree = nullptr;


  // This is a global, we can return this when we want to return never. It should never actually be
  // used as an input to any expression in any function though.
  LLVMValueRef neverPtr = nullptr;

  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  std::unordered_map<Edge*, LLVMValueRef, AddressHasher<Edge*>> interfaceTablePtrs;

  std::unordered_map<std::string, LLVMValueRef> functions;
  std::unordered_map<std::string, LLVMValueRef> externFunctions;

  // This is temporary, Valestrom should soon embed mutability and region into the referend for us
  // so we won't have to do this.
  std::unordered_map<Referend*, RegionId*, AddressHasher<Referend*>> regionIdByReferend;

  // These contain the extra interface methods that Midas adds to particular interfaces.
  // For example, for every immutable, Midas needs to add a serialize() method that
  // adds it to an outgoing linear buffer.
  std::unordered_map<Prototype*, LLVMValueRef, AddressHasher<Prototype*>> extraFunctions;
  std::unordered_map<
      InterfaceReferend*,
      std::vector<InterfaceMethod*>,
          AddressHasher<InterfaceReferend*>> interfaceExtraMethods;

  using OverridesBySubstructMap =
      std::unordered_map<
          StructReferend*,
          std::vector<std::pair<InterfaceMethod*, Prototype*>>,
          AddressHasher<StructReferend*>>;
  using OverridesBySubstructByInterfaceMap =
      std::unordered_map<
          InterfaceReferend*,
          OverridesBySubstructMap,
          AddressHasher<InterfaceReferend*>>;
  OverridesBySubstructByInterfaceMap overridesBySubstructByInterface;
  // This keeps us from adding more edges or interfaces after we've already started compiling them.
  bool interfacesOpen = true;

  void addInterfaceExtraMethod(InterfaceReferend* interfaceReferend, InterfaceMethod* method) {
    assert(interfacesOpen);
    interfaceExtraMethods[interfaceReferend].push_back(method);
  }
  void addEdgeExtraMethod(InterfaceReferend* interfaceMT, StructReferend* structMT, InterfaceMethod* interfaceMethod, Prototype* function) {
    auto interfaceExtraMethodsI = interfaceExtraMethods.find(interfaceMT);
    assert(interfaceExtraMethodsI != interfaceExtraMethods.end());

    assert(interfacesOpen);
    auto iter = overridesBySubstructByInterface.find(interfaceMT);
    if (iter == overridesBySubstructByInterface.end()) {
      iter =
          overridesBySubstructByInterface.emplace(
              interfaceMT,
              OverridesBySubstructMap{0, addressNumberer->makeHasher<StructReferend*>()})
              .first;
    }
    int index = iter->second[structMT].size();

    assert(interfaceExtraMethodsI->second[index] == interfaceMethod);
    iter->second[structMT].push_back(std::make_pair(interfaceMethod, function));
  }

//  std::unordered_map<Name*, StructDefinition*> extraStructs;
//  std::unordered_map<Name*, InterfaceDefinition*> extraInterfaces;

  StructDefinition* lookupStruct(Name* name) {
//    auto structI = extraStructs.find(name);
//    if (structI != extraStructs.end()) {
//      return structI->second;
//    }
    return program->getStruct(name);
  }

  InterfaceDefinition* lookupInterface(Name* name) {
//    auto interfaceI = extraInterfaces.find(name);
//    if (interfaceI != extraInterfaces.end()) {
//      return interfaceI->second;
//    }
    return program->getInterface(name);
  }

  LLVMValueRef lookupFunction(Prototype* prototype) {
    auto iter = extraFunctions.find(prototype);
    if (iter != extraFunctions.end()) {
      return iter->second;
    }
    auto funcIter = functions.find(prototype->name->name);
    assert(funcIter != functions.end());
    return funcIter->second;
  }

  int getInterfaceMethodIndex(InterfaceReferend* interfaceReferendM, Prototype* prototype) {
    int numMetalMethods = 0;
    auto interfaceDefMIter = program->interfaces.find(interfaceReferendM->fullName->name);
    if (interfaceDefMIter != program->interfaces.end()) {
      auto interfaceDefM = interfaceDefMIter->second;
      for (int i = 0; i < interfaceDefM->methods.size(); i++) {
        if (interfaceDefM->methods[i]->prototype == prototype) {
          return i;
        }
      }
      numMetalMethods = interfaceDefM->methods.size();
    }
    auto iter = interfaceExtraMethods.find(interfaceReferendM);
    assert(iter != interfaceExtraMethods.end());
    auto extraMethods = iter->second;
    for (int i = 0; i < extraMethods.size(); i++) {
      if (extraMethods[i]->prototype == prototype) {
        return numMetalMethods + i;
      }
    }
    assert(false);
  }


  Weakability getReferendWeakability(Referend* referend);

  Ref constI64(int64_t x);
  Ref constI1(bool b);
  Ref buildAdd(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildMod(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildMultiply(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildDivide(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);

  Name* getReferendName(Referend* referend);

  template<typename T>
  AddressHasher<T> makeAddressHasher() {
    return addressNumberer->makeHasher<T>();
  }

  Name* serializeName = nullptr;
  Name* serializeThunkName = nullptr;
  Name* unserializeName = nullptr;
  Name* unserializeThunkName = nullptr;

  LLVMBuilderRef valeMainBuilder = nullptr;

  RCImm* rcImm = nullptr;
  IRegion* mutRegion = nullptr;
  IRegion* unsafeRegion = nullptr;
  IRegion* assistRegion = nullptr;
  IRegion* naiveRcRegion = nullptr;
  IRegion* resilientV3Region = nullptr;
  IRegion* resilientV2Region = nullptr;
  IRegion* resilientV1Region = nullptr;
  Linear* linearRegion = nullptr;
  std::unordered_map<RegionId*, IRegion*, AddressHasher<RegionId*>> regions;


  std::vector<LLVMValueRef> getEdgeFunctions(Edge* edge);

  std::vector<LLVMTypeRef> getInterfaceFunctionTypes(InterfaceReferend* referend);


  IRegion* getRegion(Reference* referenceM);
  IRegion* getRegion(Referend* referendM);
  IRegion* getRegion(RegionId* regionId);
  LLVMValueRef getFunction(Name* name);
  LLVMValueRef getInterfaceTablePtr(Edge* edge);
  LLVMValueRef getOrMakeStringConstant(const std::string& str);
};

#endif
