package dev.vale.typing

import dev.vale.postparsing.{IRuneS, IntegerTemplataType, MutabilityTemplataType, VariabilityTemplataType}
import dev.vale.typing.ast.{FunctionExportT, FunctionExternT, FunctionDefinitionT, ImplT, KindExportT, KindExternT, PrototypeT, SignatureT, getFunctionLastName}
import dev.vale.typing.env.{CitizenEnvironmentT, FunctionEnvironmentT, IInDenizenEnvironmentT}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.names._
import dev.vale.typing.types._
import dev.vale.{CodeLocationS, Collector, FileCoordinate, PackageCoordinate, RangeS, StrI, vassert, vassertOne, vassertSome, vfail, vimpl, vpass, vwat}
import dev.vale.typing.ast._
import dev.vale.typing.templata.{CoordTemplataT, ITemplataT, MutabilityTemplataT, PrototypeTemplataT}
import dev.vale.typing.types.InterfaceTT

import scala.collection.immutable.{List, Map}
import scala.collection.mutable


case class DeferredEvaluatingFunctionBody(
  prototypeT: PrototypeT,
  call: (CompilerOutputs) => Unit)

case class DeferredEvaluatingFunction(
  name: IdT[INameT],
  call: (CompilerOutputs) => Unit)


case class CompilerOutputs() {
  // Not all signatures/banners will have a return type here, it might not have been processed yet.
  private val returnTypesBySignature: mutable.HashMap[SignatureT, CoordT] = mutable.HashMap()

  // Not all signatures/banners or even return types will have a function here, it might not have
  // been processed yet.
  private val signatureToFunction: mutable.HashMap[SignatureT, FunctionDefinitionT] = mutable.HashMap()
//  private val functionsByPrototype: mutable.HashMap[PrototypeT, FunctionT] = mutable.HashMap()
  private val envByFunctionSignature: mutable.HashMap[SignatureT, FunctionEnvironmentT] = mutable.HashMap()

  // declaredNames is the structs that we're currently in the process of defining
  // Things will appear here before they appear in structTemplateNameToDefinition/interfaceTemplateNameToDefinition
  // This is to prevent infinite recursion / stack overflow when typingpassing recursive types
  // This will be the instantiated name, not just the template name, see UINIT.
  private val functionDeclaredNames: mutable.HashMap[IdT[INameT], RangeS] = mutable.HashMap()
  // Outer env is the env that contains the template.
  // This will be the instantiated name, not just the template name, see UINIT.
  private val functionNameToOuterEnv: mutable.HashMap[IdT[IFunctionTemplateNameT], IInDenizenEnvironmentT] = mutable.HashMap()
  // Inner env is the env that contains the solved rules for the declaration, given placeholders.
  // This will be the instantiated name, not just the template name, see UINIT.
  private val functionNameToInnerEnv: mutable.HashMap[IdT[INameT], IInDenizenEnvironmentT] = mutable.HashMap()


  // declaredNames is the structs that we're currently in the process of defining
  // Things will appear here before they appear in structTemplateNameToDefinition/interfaceTemplateNameToDefinition
  // This is to prevent infinite recursion / stack overflow when typingpassing recursive types
  private val typeDeclaredNames: mutable.HashSet[IdT[ITemplateNameT]] = mutable.HashSet()
  // Outer env is the env that contains the template.
  private val typeNameToOuterEnv: mutable.HashMap[IdT[ITemplateNameT], IInDenizenEnvironmentT] = mutable.HashMap()
  // Inner env is the env that contains the solved rules for the declaration, given placeholders.
  // We can key by template name here because there's only one inner env per template. This is the env
  // that has placeholders and stuff.
  // Also, if it's keyed by template name, we can access it earlier, before the definition is even made.
  // This is important for when we want to be compiling a struct/interface and one of its internal methods
  // wants to look in its inner env to get some bounds.
  private val typeNameToInnerEnv: mutable.HashMap[IdT[ITemplateNameT], IInDenizenEnvironmentT] = mutable.HashMap()
  // One must fill this in when putting things into declaredNames.
  private val typeNameToMutability: mutable.HashMap[IdT[ITemplateNameT], ITemplataT[MutabilityTemplataType]] = mutable.HashMap()
  // One must fill this in when putting things into declaredNames.
  private val interfaceNameToSealed: mutable.HashMap[IdT[IInterfaceTemplateNameT], Boolean] = mutable.HashMap()


  private val structTemplateNameToDefinition: mutable.HashMap[IdT[IStructTemplateNameT], StructDefinitionT] = mutable.HashMap()
  private val interfaceTemplateNameToDefinition: mutable.HashMap[IdT[IInterfaceTemplateNameT], InterfaceDefinitionT] = mutable.HashMap()

  private val allImpls: mutable.HashMap[IdT[IImplTemplateNameT], ImplT] = mutable.HashMap()
  private val subCitizenTemplateToImpls: mutable.HashMap[IdT[ICitizenTemplateNameT], Vector[ImplT]] = mutable.HashMap()
  private val superInterfaceTemplateToImpls: mutable.HashMap[IdT[IInterfaceTemplateNameT], Vector[ImplT]] = mutable.HashMap()

  private val kindExports: mutable.ArrayBuffer[KindExportT] = mutable.ArrayBuffer()
  private val functionExports: mutable.ArrayBuffer[FunctionExportT] = mutable.ArrayBuffer()
  private val kindExterns: mutable.ArrayBuffer[KindExternT] = mutable.ArrayBuffer()
  private val functionExterns: mutable.ArrayBuffer[FunctionExternT] = mutable.ArrayBuffer()

  // When we call a function, for example this one:
  //   abstract func drop<T>(virtual opt Opt<T>) where func drop(T)void;
  // and we instantiate it, drop<int>(Opt<int>), we need to figure out the bounds, ensure that
  // drop(int) exists. Then we have to remember it for the instantiator.
  // This map is how we remember it.
  // Here, we'd remember: [drop<int>(Opt<int>), [Rune1337, drop(int)]].
  // We also do this for structs and interfaces too.
  private val instantiationNameToInstantiationBounds: mutable.HashMap[IdT[IInstantiationNameT], InstantiationBoundArguments] =
    mutable.HashMap[IdT[IInstantiationNameT], InstantiationBoundArguments]()

//  // Only ArrayCompiler can make an RawArrayT2.
//  private val staticSizedArrayTypes:
//    mutable.HashMap[(ITemplata[IntegerTemplataType], ITemplata[MutabilityTemplataType], ITemplata[VariabilityTemplataType], CoordT), StaticSizedArrayTT] =
//    mutable.HashMap()
//  // Only ArrayCompiler can make an RawArrayT2.
//  private val runtimeSizedArrayTypes: mutable.HashMap[(ITemplata[MutabilityTemplataType], CoordT), RuntimeSizedArrayTT] = mutable.HashMap()

  // A queue of functions that our code uses, but we don't need to compile them right away.
  // We can compile them later. Perhaps in parallel, someday!
  private val deferredFunctionBodyCompiles: mutable.LinkedHashMap[PrototypeT, DeferredEvaluatingFunctionBody] = mutable.LinkedHashMap()
  private val finishedDeferredFunctionBodyCompiles: mutable.LinkedHashSet[PrototypeT] = mutable.LinkedHashSet()

  private val deferredFunctionCompiles: mutable.LinkedHashMap[IdT[INameT], DeferredEvaluatingFunction] = mutable.LinkedHashMap()
  private val finishedDeferredFunctionCompiles: mutable.LinkedHashSet[IdT[INameT]] = mutable.LinkedHashSet()

  def countDenizens(): Int = {
//    staticSizedArrayTypes.size +
//      runtimeSizedArrayTypes.size +
      signatureToFunction.size +
      structTemplateNameToDefinition.size +
      interfaceTemplateNameToDefinition.size
  }

  def peekNextDeferredFunctionBodyCompile(): Option[DeferredEvaluatingFunctionBody] = {
    deferredFunctionBodyCompiles.headOption.map(_._2)
  }
  def markDeferredFunctionBodyCompiled(prototypeT: PrototypeT): Unit = {
    vassert(prototypeT == vassertSome(deferredFunctionBodyCompiles.headOption)._1)
    finishedDeferredFunctionBodyCompiles += prototypeT
    deferredFunctionBodyCompiles -= prototypeT
  }

  def peekNextDeferredFunctionCompile(): Option[DeferredEvaluatingFunction] = {
    deferredFunctionCompiles.headOption.map(_._2)
  }
  def markDeferredFunctionCompiled(name: IdT[INameT]): Unit = {
    vassert(name == vassertSome(deferredFunctionCompiles.headOption)._1)
    finishedDeferredFunctionCompiles += name
    deferredFunctionCompiles -= name
  }

  def getInstantiationNameToFunctionBoundToRune(): Map[IdT[IInstantiationNameT], InstantiationBoundArguments] = {
    instantiationNameToInstantiationBounds.toMap
  }

  def lookupFunction(signature: SignatureT): Option[FunctionDefinitionT] = {
    signatureToFunction.get(signature)
  }

  def getInstantiationBounds(
    instantiationId: IdT[IInstantiationNameT]):
  Option[InstantiationBoundArguments] = {
    instantiationNameToInstantiationBounds.get(instantiationId)
  }

  def addInstantiationBounds(
    instantiationId: IdT[IInstantiationNameT],
    functionBoundToRune: InstantiationBoundArguments):
  Unit = {
    // We'll do this when we can cache instantiations from StructTemplar etc.
    // // We should only add instantiation bounds in exactly one place: the place that makes the
    // // PrototypeT/StructTT/InterfaceTT.
    // vassert(!instantiationNameToInstantiationBounds.contains(instantiationFullName))
    instantiationNameToInstantiationBounds.get(instantiationId) match {
      case Some(existing) => {
        // Make Sure Bound Args Match For Instantiation (MSBAMFI)
        // Theres some ambiguities or something here. sometimes when we evaluate
        // the same thing twice we get different results.
        // It's gonna be especially tricky because we get each function bounds from the overload
        // resolver which only returns one. We might need to make it not arbitrarily choose a
        // function to call. Perhaps it should tiebreak and choose the first bound that works.
        // vassert(existing == functionBoundToRune)
      }
      case None =>
    }

    instantiationNameToInstantiationBounds.put(instantiationId, functionBoundToRune)
  }

//  // This means we've at least started to evaluate this function's body.
//  // We use this to cut short any infinite looping that might happen when,
//  // for example, there's a recursive function call.
//  def declareFunctionSignature(range: RangeS, signature: SignatureT, maybeEnv: Option[FunctionEnvironment]): Unit = {
//    // The only difference between this and declareNonGlobalFunctionSignature is
//    // that we put an environment in here.
//
//    // This should have been checked outside
//    vassert(!declaredSignatures.contains(signature))
//
//    declaredSignatures += signature -> range
//    envByFunctionSignature ++= maybeEnv.map(env => Map(signature -> env)).getOrElse(Map())
//    this
//  }

  def declareFunctionReturnType(signature: SignatureT, returnType2: CoordT): Unit = {
    returnTypesBySignature.get(signature) match {
      case None =>
      case Some(existingReturnType2) => vassert(existingReturnType2 == returnType2)
    }
//    if (!declaredSignatures.contains(signature)) {
//      vfail("wot")
//    }
    returnTypesBySignature += (signature -> returnType2)
  }

  def addFunction(function: FunctionDefinitionT): Unit = {
//    vassert(declaredSignatures.contains(function.header.toSignature))
    vassert(
      function.body.result.coord.kind == NeverT(false) ||
      function.body.result.coord == function.header.returnType)

//    if (!useOptimization) {
//      Collector.all(function, {
//        case ReturnTE(innerExpr) => {
//          vassert(
//            innerExpr.result.reference.kind == NeverT(false) ||
//              innerExpr.result.reference == function.header.returnType)
//        }
//      })
//    }

//    if (functionsByPrototype.contains(function.header.toPrototype)) {
//      vfail("wot")
//    }
    if (signatureToFunction.contains(function.header.toSignature)) {
      vfail("wot")
    }

    signatureToFunction.put(function.header.toSignature, function)
//    functionsByPrototype.put(function.header.toPrototype, function)
  }

  def declareFunction(callRanges: List[RangeS], name: IdT[IFunctionNameT]): Unit = {
    functionDeclaredNames.get(name) match {
      case Some(oldFunctionRange) => {
        throw CompileErrorExceptionT(FunctionAlreadyExists(oldFunctionRange, callRanges.head, name))
      }
      case None =>
    }
    functionDeclaredNames.put(name, callRanges.head)
  }

  // We can't declare the struct at the same time as we declare its mutability or environment,
  // see MFDBRE.
  def declareType(templateName: IdT[ITemplateNameT]): Unit = {
    templateName match {
      case IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector()), LambdaCitizenTemplateNameT(_), LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(_,Vector(CoordT(ShareT,StructTT(IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector())),LambdaCitizenNameT(LambdaCitizenTemplateNameT(_))))))),Vector(),Vector(CoordT(ShareT,StructTT(IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector())),LambdaCitizenNameT(LambdaCitizenTemplateNameT(_))))))), LambdaCitizenTemplateNameT(_)),LambdaCallFunctionTemplateNameT(_,Vector(CoordT(BorrowT,StructTT(IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector()), LambdaCitizenTemplateNameT(_), LambdaCallFunctionNameT(LambdaCallFunctionTemplateNameT(_,Vector(CoordT(ShareT,StructTT(IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector())),LambdaCitizenNameT(LambdaCitizenTemplateNameT(_))))))),Vector(),Vector(CoordT(ShareT,StructTT(IdT(_,Vector(FunctionNameT(FunctionTemplateNameT(StrI("main"),_),Vector(),Vector())),LambdaCitizenNameT(LambdaCitizenTemplateNameT(_)))))))),LambdaCitizenNameT(LambdaCitizenTemplateNameT(_))))), CoordT(ShareT,IntT(32))))) => {
        vpass()
      }
      case _ =>
    }
    vassert(!typeDeclaredNames.contains(templateName))
    typeDeclaredNames += templateName
  }

  def declareTypeMutability(
    templateName: IdT[ITemplateNameT],
    mutability: ITemplataT[MutabilityTemplataType]
  ): Unit = {
    vassert(typeDeclaredNames.contains(templateName))
    vassert(!typeNameToMutability.contains(templateName))
    typeNameToMutability += (templateName -> mutability)
  }

  def declareTypeSealed(
    templateName: IdT[IInterfaceTemplateNameT],
    seealed: Boolean
  ): Unit = {
    vassert(typeDeclaredNames.contains(templateName))
    vassert(!interfaceNameToSealed.contains(templateName))
    interfaceNameToSealed += (templateName -> seealed)
  }

  def declareFunctionInnerEnv(
    nameT: IdT[IFunctionNameT],
    env: IInDenizenEnvironmentT,
  ): Unit = {
    vassert(functionDeclaredNames.contains(nameT))
    // One should declare the outer env first
    vassert(!functionNameToInnerEnv.contains(nameT))
//    vassert(nameT == env.fullName)
    functionNameToInnerEnv += (nameT -> env)
  }

  def declareFunctionOuterEnv(
    nameT: IdT[IFunctionTemplateNameT],
    env: IInDenizenEnvironmentT,
  ): Unit = {
    vassert(!functionNameToOuterEnv.contains(nameT))
    //    vassert(nameT == env.fullName)
    functionNameToOuterEnv += (nameT -> env)
  }

  def declareTypeOuterEnv(
    nameT: IdT[ITemplateNameT],
    env: IInDenizenEnvironmentT,
  ): Unit = {
    vassert(typeDeclaredNames.contains(nameT))
    vassert(!typeNameToOuterEnv.contains(nameT))
    vassert(nameT == env.id)
    typeNameToOuterEnv += (nameT -> env)
  }

  def declareTypeInnerEnv(
    templateId: IdT[ITemplateNameT],
    env: IInDenizenEnvironmentT,
  ): Unit = {
//    val templateFullName = TemplataCompiler.getTemplate(nameT)
    vassert(typeDeclaredNames.contains(templateId))
    // One should declare the outer env first
    vassert(typeNameToOuterEnv.contains(templateId))
    vassert(!typeNameToInnerEnv.contains(templateId))
    //    vassert(nameT == env.fullName)
    typeNameToInnerEnv += (templateId -> env)
  }

  def addStruct(structDef: StructDefinitionT): Unit = {
    if (structDef.mutability == MutabilityTemplataT(ImmutableT)) {
      structDef.members.foreach({
        case NormalStructMemberT(name, variability, AddressMemberTypeT(reference)) => {
          vwat() // Immutable structs cant contain address members
        }
        case NormalStructMemberT(name, variability, ReferenceMemberTypeT(reference)) => {
          if (reference.ownership != ShareT) {
            vfail("ImmutableP contains a non-immutable!")
          }
        }
        case VariadicStructMemberT(name, tyype) => {
          vimpl() // We dont yet have immutable structs with variadic members
        }
      })
    }
    vassert(typeNameToMutability.contains(structDef.templateName))
    vassert(!structTemplateNameToDefinition.contains(structDef.templateName))
    structTemplateNameToDefinition += (structDef.templateName -> structDef)
  }

  def addInterface(interfaceDef: InterfaceDefinitionT): Unit = {
    vassert(typeNameToMutability.contains(interfaceDef.templateName))
    vassert(interfaceNameToSealed.contains(interfaceDef.templateName))
    vassert(!interfaceTemplateNameToDefinition.contains(interfaceDef.templateName))
    interfaceTemplateNameToDefinition += (interfaceDef.templateName -> interfaceDef)
  }

//  def addStaticSizedArray(ssaTT: StaticSizedArrayTT): Unit = {
//    val contentsStaticSizedArrayTT(size, elementType, mutability, variability) = ssaTT
//    staticSizedArrayTypes += ((size, elementType, mutability, variability) -> ssaTT)
//  }
//
//  def addRuntimeSizedArray(rsaTT: RuntimeSizedArrayTT): Unit = {
//    val contentsRuntimeSizedArrayTT(elementType, mutability) = rsaTT
//    runtimeSizedArrayTypes += ((elementType, mutability) -> rsaTT)
//  }

  def addImpl(impl: ImplT): Unit = {
    vassert(!allImpls.contains(impl.templateId))
    allImpls.put(impl.templateId, impl)
    subCitizenTemplateToImpls.put(
      impl.subCitizenTemplateId,
      subCitizenTemplateToImpls.getOrElse(impl.subCitizenTemplateId, Vector()) :+ impl)
    superInterfaceTemplateToImpls.put(
      impl.superInterfaceTemplateId,
      superInterfaceTemplateToImpls.getOrElse(impl.superInterfaceTemplateId, Vector()) :+ impl)
  }

  def getParentImplsForSubCitizenTemplate(subCitizenTemplate: IdT[ICitizenTemplateNameT]): Vector[ImplT] = {
    subCitizenTemplateToImpls.getOrElse(subCitizenTemplate, Vector[ImplT]())
  }
  def getChildImplsForSuperInterfaceTemplate(superInterfaceTemplate: IdT[IInterfaceTemplateNameT]): Vector[ImplT] = {
    superInterfaceTemplateToImpls.getOrElse(superInterfaceTemplate, Vector[ImplT]())
  }

  def addKindExport(range: RangeS, kind: KindT, packageCoord: PackageCoordinate, exportedName: StrI): Unit = {
    kindExports += KindExportT(range, kind, packageCoord, exportedName)
  }

  def addFunctionExport(range: RangeS, function: PrototypeT, packageCoord: PackageCoordinate, exportedName: StrI): Unit = {
    vassert(getInstantiationBounds(function.id).nonEmpty)
    functionExports += FunctionExportT(range, function, packageCoord, exportedName)
  }

  def addKindExtern(kind: KindT, packageCoord: PackageCoordinate, exportedName: StrI): Unit = {
    kindExterns += KindExternT(kind, packageCoord, exportedName)
  }

  def addFunctionExtern(range: RangeS, function: PrototypeT, packageCoord: PackageCoordinate, exportedName: StrI): Unit = {
    functionExterns += FunctionExternT(range, function, packageCoord, exportedName)
  }

  def deferEvaluatingFunctionBody(devf: DeferredEvaluatingFunctionBody): Unit = {
    deferredFunctionBodyCompiles.put(devf.prototypeT, devf)
  }

  def deferEvaluatingFunction(devf: DeferredEvaluatingFunction): Unit = {
    deferredFunctionCompiles.put(devf.name, devf)
  }

  def structDeclared(templateName: IdT[IStructTemplateNameT]): Boolean = {
    // This is the only place besides StructDefinition2 and declareStruct thats allowed to make one of these
//    val templateName = StructTT(fullName)
    typeDeclaredNames.contains(templateName)
  }

//  def prototypeDeclared(fullName: FullNameT[IFunctionNameT]): Option[PrototypeT] = {
//    declaredSignatures.find(_._1.fullName == fullName) match {
//      case None => None
//      case Some((sig, _)) => {
//        returnTypesBySignature.get(sig) match {
//          case None => None
//          case Some(ret) => Some(ast.PrototypeT(sig.fullName, ret))
//        }
//      }
//    }
//  }

  def lookupMutability(templateName: IdT[ITemplateNameT]): ITemplataT[MutabilityTemplataType] = {
    // If it has a structTT, then we've at least started to evaluate this citizen
    typeNameToMutability.get(templateName) match {
      case None => vfail("Still figuring out mutability for struct: " + templateName) // See MFDBRE
      case Some(m) => m
    }
  }

  def lookupSealed(templateName: IdT[IInterfaceTemplateNameT]): Boolean = {
    // If it has a structTT, then we've at least started to evaluate this citizen
    interfaceNameToSealed.get(templateName) match {
      case None => vfail("Still figuring out sealed for struct: " + templateName) // See MFDBRE
      case Some(m) => m
    }
  }

//  def lookupCitizen(citizenRef: CitizenRefT): CitizenDefinitionT = {
//    citizenRef match {
//      case s @ StructTT(_) => lookupStruct(s)
//      case i @ InterfaceTT(_) => lookupInterface(i)
//    }
//  }

  def interfaceDeclared(templateName: IdT[ITemplateNameT]): Boolean = {
    // This is the only place besides InterfaceDefinition2 and declareInterface thats allowed to make one of these
    typeDeclaredNames.contains(templateName)
  }

  def lookupStruct(structTT: IdT[IStructNameT]): StructDefinitionT = {
    lookupStructTemplate(TemplataCompiler.getStructTemplate(structTT))
  }
  def lookupStructTemplate(templateName: IdT[IStructTemplateNameT]): StructDefinitionT = {
    vassertSome(structTemplateNameToDefinition.get(templateName))
  }
  def lookupInterface(interfaceTT: InterfaceTT): InterfaceDefinitionT = {
    lookupInterface(TemplataCompiler.getInterfaceTemplate(interfaceTT.id))
  }
  def lookupInterface(templateName: IdT[IInterfaceTemplateNameT]): InterfaceDefinitionT = {
    vassertSome(interfaceTemplateNameToDefinition.get(templateName))
  }
  def lookupCitizen(templateName: IdT[ICitizenTemplateNameT]): CitizenDefinitionT = {
    val IdT(packageCoord, initSteps, last) = templateName
    last match {
      case s @ AnonymousSubstructTemplateNameT(_) => lookupStructTemplate(IdT(packageCoord, initSteps, s))
      case s @ StructTemplateNameT(_) => lookupStructTemplate(IdT(packageCoord, initSteps, s))
      case s @ InterfaceTemplateNameT(_) => lookupInterface(IdT(packageCoord, initSteps, s))
    }
  }
  def lookupCitizen(citizenTT: ICitizenTT): CitizenDefinitionT = {
    citizenTT match {
      case s @ StructTT(_) => lookupStruct(s.id)
      case s @ InterfaceTT(_) => lookupInterface(s)
    }
  }

  def getAllStructs(): Iterable[StructDefinitionT] = structTemplateNameToDefinition.values
  def getAllInterfaces(): Iterable[InterfaceDefinitionT] = interfaceTemplateNameToDefinition.values
  def getAllFunctions(): Iterable[FunctionDefinitionT] = signatureToFunction.values
  def getAllImpls(): Iterable[ImplT] = allImpls.values
//  def getAllStaticSizedArrays(): Iterable[StaticSizedArrayTT] = staticSizedArrayTypes.values
//  def getAllRuntimeSizedArrays(): Iterable[RuntimeSizedArrayTT] = runtimeSizedArrayTypes.values
//  def getKindToDestructorMap(): Map[KindT, PrototypeT] = kindToDestructor.toMap

//  def getStaticSizedArrayType(size: ITemplata[IntegerTemplataType], mutability: ITemplata[MutabilityTemplataType], variability: ITemplata[VariabilityTemplataType], elementType: CoordT): Option[StaticSizedArrayTT] = {
//    staticSizedArrayTypes.get((size, mutability, variability, elementType))
//  }
  def getEnvForFunctionSignature(sig: SignatureT): FunctionEnvironmentT = {
    vassertSome(envByFunctionSignature.get(sig))
  }
  def getOuterEnvForType(range: List[RangeS], name: IdT[ITemplateNameT]): IInDenizenEnvironmentT = {
    typeNameToOuterEnv.get(name) match {
      case None => {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "No outer env for type: " + name))
      }
      case Some(x) => x
    }
  }
  def getInnerEnvForType(name: IdT[ITemplateNameT]): IInDenizenEnvironmentT = {
    vassertSome(typeNameToInnerEnv.get(name))
  }
  def getInnerEnvForFunction(name: IdT[INameT]): IInDenizenEnvironmentT = {
    vassertSome(functionNameToInnerEnv.get(name))
  }
  def getOuterEnvForFunction(name: IdT[IFunctionTemplateNameT]): IInDenizenEnvironmentT = {
    vassertSome(functionNameToOuterEnv.get(name))
  }
  def getReturnTypeForSignature(sig: SignatureT): Option[CoordT] = {
    returnTypesBySignature.get(sig)
  }
//  def getDeclaredSignatureOrigin(sig: SignatureT): Option[RangeS] = {
//    declaredSignatures.get(sig)
//  }
//  def getDeclaredSignatureOrigin(name: FullNameT[IFunctionNameT]): Option[RangeS] = {
//    declaredSignatures.get(ast.SignatureT(name))
//  }
//  def getRuntimeSizedArray(mutabilityT: ITemplata[MutabilityTemplataType], elementType: CoordT): Option[RuntimeSizedArrayTT] = {
//    runtimeSizedArrayTypes.get((mutabilityT, elementType))
//  }
  def getKindExports: Vector[KindExportT] = {
    kindExports.toVector
  }
  def getFunctionExports: Vector[FunctionExportT] = {
    functionExports.toVector
  }
  def getKindExterns: Vector[KindExternT] = {
    kindExterns.toVector
  }
  def getFunctionExterns: Vector[FunctionExternT] = {
    functionExterns.toVector
  }
}
