#ifndef REGION_COMMON_DEFAULTLAYOUT_IREFERENDSTRUCTSSOURCE_H_
#define REGION_COMMON_DEFAULTLAYOUT_IREFERENDSTRUCTSSOURCE_H_

class IReferendStructsSource {
public:
  virtual ~IReferendStructsSource() = default;
  virtual ControlBlock* getControlBlock(Referend* referend) = 0;
  virtual LLVMTypeRef getInnerStruct(StructReferend* structReferend) = 0;
  virtual LLVMTypeRef getWrapperStruct(StructReferend* structReferend) = 0;
  virtual LLVMTypeRef getKnownSizeArrayWrapperStruct(KnownSizeArrayT* ksaMT) = 0;
  virtual LLVMTypeRef getUnknownSizeArrayWrapperStruct(UnknownSizeArrayT* usaMT) = 0;
  virtual LLVMTypeRef getStringWrapperStruct() = 0;
  virtual LLVMTypeRef getInterfaceRefStruct(InterfaceReferend* interfaceReferend) = 0;
  virtual LLVMTypeRef getInterfaceTableStruct(InterfaceReferend* interfaceReferend) = 0;
  virtual void defineStruct(StructReferend* structM, std::vector<LLVMTypeRef> membersLT) = 0;
  virtual void declareStruct(StructReferend* structM) = 0;
  virtual void declareEdge(Edge* edge) = 0;
  virtual void defineEdge(
      Edge* edge,
      std::vector<LLVMTypeRef> interfaceFunctionsLT,
      std::vector<LLVMValueRef> functions) = 0;
  virtual void declareInterface(InterfaceDefinition* interface) = 0;
  virtual void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) = 0;
  virtual void declareKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT) = 0;
  virtual void declareUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT) = 0;
  virtual void defineUnknownSizeArray(UnknownSizeArrayDefinitionT* unknownSizeArrayMT, LLVMTypeRef elementLT) = 0;
  virtual void defineKnownSizeArray(KnownSizeArrayDefinitionT* knownSizeArrayMT, LLVMTypeRef elementLT) = 0;

  virtual ControlBlockPtrLE getConcreteControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* reference,
      WrapperPtrLE wrapperPtrLE) = 0;

  virtual WrapperPtrLE makeWrapperPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      LLVMValueRef ptrLE) = 0;

  virtual InterfaceFatPtrLE makeInterfaceFatPtr(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE) = 0;

  // Skips the check that the object exists. Useful for weak interface refs.
  virtual InterfaceFatPtrLE makeInterfaceFatPtrWithoutChecking(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM_,
      LLVMValueRef ptrLE) = 0;

//  virtual ControlBlockPtrLE makeControlBlockPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Referend* referendM,
//      LLVMValueRef controlBlockPtrLE) = 0;

  virtual LLVMValueRef getStringBytesPtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WrapperPtrLE ptrLE) = 0;

  virtual LLVMValueRef getStringLen(
      FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) = 0;


  virtual ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      InterfaceFatPtrLE interfaceFatPtrLE) = 0;

  virtual ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      InterfaceFatPtrLE interfaceFatPtrLE) = 0;

  virtual ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      Ref ref,
      Reference* referenceM) = 0;

  virtual ControlBlockPtrLE getControlBlockPtr(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM) = 0;

  virtual ControlBlockPtrLE getControlBlockPtrWithoutChecking(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      // This will be a pointer if a mutable struct, or a fat ref if an interface.
      LLVMValueRef ref,
      Reference* referenceM) = 0;

  virtual LLVMValueRef getStructContentsPtr(
      LLVMBuilderRef builder,
      Referend* referend,
      WrapperPtrLE wrapperPtrLE) = 0;

  virtual LLVMValueRef getVoidPtrFromInterfacePtr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      InterfaceFatPtrLE virtualArgLE) = 0;

  virtual LLVMValueRef getObjIdFromControlBlockPtr(
      LLVMBuilderRef builder,
      Referend* referendM,
      ControlBlockPtrLE controlBlockPtr) = 0;

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  virtual LLVMValueRef getStrongRcPtrFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) = 0;

  // See CRCISFAORC for why we don't take in a mutability.
  // Strong means owning or borrow or shared; things that control the lifetime.
  virtual LLVMValueRef getStrongRcFromControlBlockPtr(
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtr) = 0;
};

#endif
