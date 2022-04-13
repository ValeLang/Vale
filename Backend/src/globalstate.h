#ifndef GLOBALSTATE_H_
#define GLOBALSTATE_H_

#include <llvm-c/Core.h>

#include <unordered_map>
#include "metal/metalcache.h"
#include "region/common/defaultlayout/structs.h"

#include "metal/ast.h"
#include "metal/instructions.h"
#include "valeopts.h"
#include "addresshasher.h"
#include "externs.h"

class IRegion;
class KindStructs;
class KindStructs;
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

  Externs* externs = nullptr;

  LLVMTargetDataRef dataLayout = nullptr;
  LLVMModuleRef mod = nullptr;
  int ptrSize = 0;

  MetalCache* metalCache = nullptr;

  Program* program = nullptr;

  LLVMValueRef numMainArgs = nullptr;
  LLVMValueRef mainArgs = nullptr;

  LLVMValueRef objIdCounter = nullptr;
  LLVMValueRef liveHeapObjCounter = nullptr;
  LLVMValueRef derefCounter = nullptr;
  LLVMValueRef mutRcAdjustCounter = nullptr;
  LLVMValueRef livenessCheckCounter = nullptr;
  LLVMValueRef writeOnlyGlobal = nullptr;
  LLVMValueRef crashGlobal = nullptr;


  LLVMTypeRef wrcTableStructLT = nullptr;
  LLVMValueRef expandWrcTable = nullptr, checkWrci = nullptr, getNumWrcs = nullptr;

  LLVMTypeRef lgtTableStructLT, lgtEntryStructLT = nullptr; // contains generation and next free
  LLVMValueRef expandLgt = nullptr, checkLgti = nullptr, getNumLiveLgtEntries = nullptr;

//  LLVMValueRef genMalloc = nullptr, genFree = nullptr;

  LLVMTypeRef concreteHandleLT = nullptr; // 24 bytes, for SSA, RSA, and structs
  LLVMTypeRef interfaceHandleLT = nullptr; // 32 bytes, for interfaces. concreteHandleLT plus 8b itable ptr.


  // This is a global, we can return this when we want to return never. It should never actually be
  // used as an input to any expression in any function though.
  LLVMValueRef neverPtr = nullptr;

  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::unordered_map<std::string, LLVMValueRef> stringConstants;

  std::unordered_map<Edge*, LLVMValueRef, AddressHasher<Edge*>> interfaceTablePtrs;

  std::unordered_map<std::string, LLVMValueRef> functions;
  std::unordered_map<std::string, LLVMValueRef> externFunctions;

  // This is temporary, Valestrom should soon embed mutability and region into the kind for us
  // so we won't have to do this.
  std::unordered_map<Kind*, RegionId*, AddressHasher<Kind*>> regionIdByKind;

  // These contain the extra interface methods that Backend adds to particular interfaces.
  // For example, for every immutable, Backend needs to add a serialize() method that
  // adds it to an outgoing linear buffer.
  std::unordered_map<Prototype*, LLVMValueRef, AddressHasher<Prototype*>> extraFunctions;
  std::unordered_map<
      InterfaceKind*,
      std::vector<InterfaceMethod*>,
          AddressHasher<InterfaceKind*>> interfaceExtraMethods;

  using OverridesBySubstructMap =
      std::unordered_map<
          StructKind*,
          std::vector<std::pair<InterfaceMethod*, Prototype*>>,
          AddressHasher<StructKind*>>;
  using OverridesBySubstructByInterfaceMap =
      std::unordered_map<
          InterfaceKind*,
          OverridesBySubstructMap,
          AddressHasher<InterfaceKind*>>;
  OverridesBySubstructByInterfaceMap overridesBySubstructByInterface;
  // This keeps us from adding more edges or interfaces after we've already started compiling them.
  bool interfacesOpen = true;

  LLVMTypeRef getConcreteHandleStruct() { return concreteHandleLT; }
  LLVMTypeRef getInterfaceHandleStruct() { return interfaceHandleLT; }

  void addInterfaceExtraMethod(InterfaceKind* interfaceKind, InterfaceMethod* method) {
    assert(interfacesOpen);
    interfaceExtraMethods[interfaceKind].push_back(method);
  }
  void addEdgeExtraMethod(InterfaceKind* interfaceMT, StructKind* structMT, InterfaceMethod* interfaceMethod, Prototype* function) {
    auto interfaceExtraMethodsI = interfaceExtraMethods.find(interfaceMT);
    assert(interfaceExtraMethodsI != interfaceExtraMethods.end());

    assert(interfacesOpen);
    auto iter = overridesBySubstructByInterface.find(interfaceMT);
    if (iter == overridesBySubstructByInterface.end()) {
      iter =
          overridesBySubstructByInterface.emplace(
              interfaceMT,
              OverridesBySubstructMap{0, addressNumberer->makeHasher<StructKind*>()})
              .first;
    }
    int index = iter->second[structMT].size();

    assert(interfaceExtraMethodsI->second[index] == interfaceMethod);
    iter->second[structMT].push_back(std::make_pair(interfaceMethod, function));
  }

  StructDefinition* lookupStruct(StructKind* structMT) {
//    auto structI = extraStructs.find(name);
//    if (structI != extraStructs.end()) {
//      return structI->second;
//    }
    return program->getStruct(structMT);
  }

  InterfaceDefinition* lookupInterface(InterfaceKind* interfaceMT) {
//    auto interfaceI = extraInterfaces.find(name);
//    if (interfaceI != extraInterfaces.end()) {
//      return interfaceI->second;
//    }
    return program->getInterface(interfaceMT);
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

  int getInterfaceMethodIndex(InterfaceKind* interfaceKindM, Prototype* prototype) {
    int numMetalMethods = 0;
    if (auto maybeInterfaceDefM = program->getMaybeInterface(interfaceKindM)) {
      auto interfaceDefM = *maybeInterfaceDefM;
      for (int i = 0; i < interfaceDefM->methods.size(); i++) {
        if (interfaceDefM->methods[i]->prototype == prototype) {
          return i;
        }
      }
      numMetalMethods = interfaceDefM->methods.size();
    }
    auto iter = interfaceExtraMethods.find(interfaceKindM);
    assert(iter != interfaceExtraMethods.end());
    auto extraMethods = iter->second;
    for (int i = 0; i < extraMethods.size(); i++) {
      if (extraMethods[i]->prototype == prototype) {
        return numMetalMethods + i;
      }
    }
    std::cerr << "Couldn't find method " << prototype->name->name << " in interface " << interfaceKindM->fullName->name << std::endl;
    assert(false);
  }


  Weakability getKindWeakability(Kind* kind);

  Ref constI64(int64_t x);
  Ref constI32(int32_t x);
  Ref constI1(bool b);
  Ref buildAdd(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildMod(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildMultiply(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);
  Ref buildDivide(FunctionState* functionState, LLVMBuilderRef builder, Ref a, Ref b);

  Name* getKindName(Kind* kind);

  template<typename T>
  AddressHasher<T> makeAddressHasher() {
    return addressNumberer->makeHasher<T>();
  }

  Name* serializeName = nullptr;
  Name* serializeThunkName = nullptr;
  Name* unserializeName = nullptr;
  Name* unserializeThunkName = nullptr;

  RCImm* rcImm = nullptr;
  IRegion* mutRegion = nullptr;
//  IRegion* unsafeRegion = nullptr;
//  IRegion* assistRegion = nullptr;
//  IRegion* naiveRcRegion = nullptr;
//  IRegion* resilientV3Region = nullptr;
//  IRegion* resilientV4Region = nullptr;
  Linear* linearRegion = nullptr;
  std::unordered_map<RegionId*, IRegion*, AddressHasher<RegionId*>> regions;


  std::vector<LLVMValueRef> getEdgeFunctions(Edge* edge);

  std::vector<LLVMTypeRef> getInterfaceFunctionTypes(InterfaceKind* kind);


  IRegion* getRegion(Reference* referenceM);
  IRegion* getRegion(Kind* kindM);
  IRegion* getRegion(RegionId* regionId);
  LLVMValueRef getFunction(Name* name);
  LLVMValueRef getInterfaceTablePtr(Edge* edge);
  LLVMValueRef getOrMakeStringConstant(const std::string& str);
};

#endif
