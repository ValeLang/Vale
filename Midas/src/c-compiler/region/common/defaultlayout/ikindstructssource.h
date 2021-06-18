#ifndef REGION_COMMON_DEFAULTLAYOUT_IREFERENDSTRUCTSSOURCE_H_
#define REGION_COMMON_DEFAULTLAYOUT_IREFERENDSTRUCTSSOURCE_H_
//
//class KindStructs {
//public:
//  virtual ~KindStructs() = default;
//  virtual ControlBlock* getControlBlock(Kind* kind) = 0;
//  virtual LLVMTypeRef getInnerStruct(StructKind* structKind) = 0;
//  virtual LLVMTypeRef getWrapperStruct(StructKind* structKind) = 0;
//  virtual LLVMTypeRef getStaticSizedArrayWrapperStruct(StaticSizedArrayT* ssaMT) = 0;
//  virtual LLVMTypeRef getRuntimeSizedArrayWrapperStruct(RuntimeSizedArrayT* rsaMT) = 0;
//  virtual LLVMTypeRef getStringWrapperStruct() = 0;
//  virtual LLVMTypeRef getInterfaceRefStruct(InterfaceKind* interfaceKind) = 0;
//  virtual LLVMTypeRef getInterfaceTableStruct(InterfaceKind* interfaceKind) = 0;
//  virtual void defineStruct(StructKind* structM, std::vector<LLVMTypeRef> membersLT) = 0;
//  virtual void declareStruct(StructKind* structM) = 0;
//  virtual void declareEdge(Edge* edge) = 0;
//  virtual void defineEdge(
//      Edge* edge,
//      std::vector<LLVMTypeRef> interfaceFunctionsLT,
//      std::vector<LLVMValueRef> functions) = 0;
//  virtual void declareInterface(InterfaceDefinition* interface) = 0;
//  virtual void defineInterface(InterfaceDefinition* interface, std::vector<LLVMTypeRef> interfaceMethodTypesL) = 0;
//  virtual void declareStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT) = 0;
//  virtual void declareRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT) = 0;
//  virtual void defineRuntimeSizedArray(RuntimeSizedArrayDefinitionT* runtimeSizedArrayMT, LLVMTypeRef elementLT) = 0;
//  virtual void defineStaticSizedArray(StaticSizedArrayDefinitionT* staticSizedArrayMT, LLVMTypeRef elementLT) = 0;
//
//  virtual ControlBlockPtrLE getConcreteControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* reference,
//      WrapperPtrLE wrapperPtrLE) = 0;
//
//  virtual WrapperPtrLE makeWrapperPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM,
//      LLVMValueRef ptrLE) = 0;
//
//  virtual InterfaceFatPtrLE makeInterfaceFatPtr(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM_,
//      LLVMValueRef ptrLE) = 0;
//
//  // Skips the check that the object exists. Useful for weak interface refs.
//  virtual InterfaceFatPtrLE makeInterfaceFatPtrWithoutChecking(
//      AreaAndFileAndLine checkerAFL,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* referenceM_,
//      LLVMValueRef ptrLE) = 0;
//
////  virtual ControlBlockPtrLE makeControlBlockPtr(
////      AreaAndFileAndLine checkerAFL,
////      FunctionState* functionState,
////      LLVMBuilderRef builder,
////      Kind* kindM,
////      LLVMValueRef controlBlockPtrLE) = 0;
//
//  virtual LLVMValueRef getStringBytesPtr(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      WrapperPtrLE ptrLE) = 0;
//
//  virtual LLVMValueRef getStringLen(
//      FunctionState* functionState, LLVMBuilderRef builder, WrapperPtrLE ptrLE) = 0;
//
//
//  virtual ControlBlockPtrLE getControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      InterfaceFatPtrLE interfaceFatPtrLE) = 0;
//
//  virtual ControlBlockPtrLE getControlBlockPtrWithoutChecking(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      InterfaceFatPtrLE interfaceFatPtrLE) = 0;
//
//  virtual ControlBlockPtrLE getControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      Ref ref,
//      Reference* referenceM) = 0;
//
//  virtual ControlBlockPtrLE getControlBlockPtr(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      LLVMValueRef ref,
//      Reference* referenceM) = 0;
//
//  virtual ControlBlockPtrLE getControlBlockPtrWithoutChecking(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      // This will be a pointer if a mutable struct, or a fat ref if an interface.
//      LLVMValueRef ref,
//      Reference* referenceM) = 0;
//
//  virtual LLVMValueRef getStructContentsPtr(
//      LLVMBuilderRef builder,
//      Kind* kind,
//      WrapperPtrLE wrapperPtrLE) = 0;
//
//  virtual LLVMValueRef getVoidPtrFromInterfacePtr(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* virtualParamMT,
//      InterfaceFatPtrLE virtualArgLE) = 0;
//
//  virtual LLVMValueRef getObjIdFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Kind* kindM,
//      ControlBlockPtrLE controlBlockPtr) = 0;
//
//  // See CRCISFAORC for why we don't take in a mutability.
//  // Strong means owning or borrow or shared; things that control the lifetime.
//  virtual LLVMValueRef getStrongRcPtrFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Reference* refM,
//      ControlBlockPtrLE controlBlockPtr) = 0;
//
//  // See CRCISFAORC for why we don't take in a mutability.
//  // Strong means owning or borrow or shared; things that control the lifetime.
//  virtual LLVMValueRef getStrongRcFromControlBlockPtr(
//      LLVMBuilderRef builder,
//      Reference* refM,
//      ControlBlockPtrLE controlBlockPtr) = 0;
//
//  virtual LLVMValueRef downcastPtr(LLVMBuilderRef builder, Reference* resultStructRefMT, LLVMValueRef unknownPossibilityPtrLE) = 0;
//};

#endif
