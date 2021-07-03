package net.verdagon.vale.templar

import net.verdagon.vale.scout.{CodeLocationS, RangeS}
import net.verdagon.vale.templar.env.{FunctionEnvironment, PackageEnvironment}
import net.verdagon.vale.templar.templata.{PrototypeT, SignatureT}
import net.verdagon.vale.templar.types.{CitizenDefinitionT, CitizenRefT, CoordT, ImmutableT, InterfaceDefinitionT, InterfaceRefT, KindT, StaticSizedArrayTT, MutabilityT, NeverT, RawArrayTT, ShareT, StructDefinitionT, StructRefT, RuntimeSizedArrayTT}
import net.verdagon.vale.{PackageCoordinate, vassert, vassertSome, vfail}

import scala.collection.immutable.{List, Map}
import scala.collection.mutable

case class Temputs() {
  // Signatures that have already started to be compiled.
  // The value is a location for checking where a given function came from, which is useful
  // for detecting when the user makes two functions with identical signatures.
  private val declaredSignatures: mutable.HashMap[SignatureT, RangeS] = mutable.HashMap()

  // Not all signatures/banners will have a return type here, it might not have been processed yet.
  private val returnTypesBySignature: mutable.HashMap[SignatureT, CoordT] = mutable.HashMap()

  // Not all signatures/banners or even return types will have a function here, it might not have
  // been processed yet.
  private val functions: mutable.ArrayBuffer[FunctionT] = mutable.ArrayBuffer()
  private val envByFunctionSignature: mutable.HashMap[SignatureT, FunctionEnvironment] = mutable.HashMap()

//  // Prototypes for extern functions
//  private val packageToExternNameToExtern: mutable.HashMap[PackageCoordinate, mutable.HashMap[String, Prototype2]] = mutable.HashMap()

  // One must fill this in when putting things into declaredStructs/Interfaces.
  private val mutabilitiesByCitizenRef: mutable.HashMap[CitizenRefT, MutabilityT] = mutable.HashMap()

  // declaredStructs is the structs that we're currently in the process of defining
  // Things will appear here before they appear in structDefsByRef
  // This is to prevent infinite recursion / stack overflow when templaring recursive types
  // Not too sure about the type of declaredStructs, we might need something else
  private val declaredStructs: mutable.HashSet[StructRefT] = mutable.HashSet()
  private val structDefsByRef: mutable.HashMap[StructRefT, StructDefinitionT] = mutable.HashMap()
  private val envByStructRef: mutable.HashMap[StructRefT, PackageEnvironment[INameT]] = mutable.HashMap()
  // declaredInterfaces is the interfaces that we're currently in the process of defining
  // Things will appear here before they appear in interfaceDefsByRef
  // This is to prevent infinite recursion / stack overflow when templaring recursive types
  // Not too sure about the type of declaredInterfaces, we might need something else
  private val declaredInterfaces: mutable.HashSet[InterfaceRefT] = mutable.HashSet()
  private val interfaceDefsByRef: mutable.HashMap[InterfaceRefT, InterfaceDefinitionT] = mutable.HashMap()
  private val envByInterfaceRef: mutable.HashMap[InterfaceRefT, PackageEnvironment[INameT]] = mutable.HashMap()

  private val impls: mutable.ArrayBuffer[ImplT] = mutable.ArrayBuffer()

  private val kindExports: mutable.ArrayBuffer[KindExportT] = mutable.ArrayBuffer()
  private val functionExports: mutable.ArrayBuffer[FunctionExportT] = mutable.ArrayBuffer()
  private val kindExterns: mutable.ArrayBuffer[KindExternT] = mutable.ArrayBuffer()
  private val functionExterns: mutable.ArrayBuffer[FunctionExternT] = mutable.ArrayBuffer()

  // Only PackTemplar can make a PackT2.
  private val packTypes: mutable.HashMap[List[CoordT], StructRefT] = mutable.HashMap()
  // Only ArrayTemplar can make an RawArrayT2.
  private val staticSizedArrayTypes: mutable.HashMap[(Int, RawArrayTT), StaticSizedArrayTT] = mutable.HashMap()
  // Only ArrayTemplar can make an RawArrayT2.
  private val runtimeSizedArrayTypes: mutable.HashMap[RawArrayTT, RuntimeSizedArrayTT] = mutable.HashMap()

  private val kindToDestructor: mutable.HashMap[KindT, PrototypeT] = mutable.HashMap()

  def lookupFunction(signature2: SignatureT): Option[FunctionT] = {
    functions.find(_.header.toSignature == signature2)
  }

  // This means we've at least started to evaluate this function's body.
  // We use this to cut short any infinite looping that might happen when,
  // for example, there's a recursive function call.
  def declareFunctionSignature(range: RangeS, signature: SignatureT, maybeEnv: Option[FunctionEnvironment]): Unit = {
    // The only difference between this and declareNonGlobalFunctionSignature is
    // that we put an environment in here.

    // This should have been checked outside
    vassert(!declaredSignatures.contains(signature))

    declaredSignatures += signature -> range
    envByFunctionSignature ++= maybeEnv.map(env => Map(signature -> env)).getOrElse(Map())
    this
  }

  def declareFunctionReturnType(signature: SignatureT, returnType2: CoordT): Unit = {
    returnTypesBySignature.get(signature) match {
      case None =>
      case Some(existingReturnType2) => vassert(existingReturnType2 == returnType2)
    }
    if (!declaredSignatures.contains(signature)) {
      vfail("wot")
    }
    returnTypesBySignature += (signature -> returnType2)
  }

  def addFunction(function: FunctionT): Unit = {
    vassert(declaredSignatures.contains(function.header.toSignature))
    vassert(
      function.body.resultRegister.reference.kind == NeverT() ||
      function.body.resultRegister.reference == function.header.returnType)
    function.all({
      case ReturnTE(innerExpr) => {
        vassert(
          innerExpr.resultRegister.reference.kind == NeverT() ||
          innerExpr.resultRegister.reference == function.header.returnType)
      }
    })

    if (functions.exists(_.header == function.header)) {
      vfail("wot")
    }

    functions += function
  }

  // We can't declare the struct at the same time as we declare its mutability or environment,
  // see MFDBRE.
  def declareStruct(
    structRef: StructRefT
  ): Unit = {
    vassert(!declaredStructs.contains(structRef))
    declaredStructs += structRef
  }

  def declareStructMutability(
    structRef: StructRefT,
    mutability: MutabilityT
  ): Unit = {
    vassert(declaredStructs.contains(structRef))
    vassert(!mutabilitiesByCitizenRef.contains(structRef))
    mutabilitiesByCitizenRef += (structRef -> mutability)
  }

  def declareStructEnv(
    structRef: StructRefT,
    env: PackageEnvironment[ICitizenNameT],
  ): Unit = {
    vassert(declaredStructs.contains(structRef))
    vassert(!envByStructRef.contains(structRef))
    envByStructRef += (structRef -> env)
  }

  def declareInterface(interfaceRef: InterfaceRefT): Unit = {
    vassert(!declaredInterfaces.contains(interfaceRef))
    declaredInterfaces += interfaceRef
  }

  def declareInterfaceMutability(
    interfaceRef: InterfaceRefT,
    mutability: MutabilityT
  ): Unit = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!mutabilitiesByCitizenRef.contains(interfaceRef))
    mutabilitiesByCitizenRef += (interfaceRef -> mutability)
  }

  def declareInterfaceEnv(
    interfaceRef: InterfaceRefT,
    env: PackageEnvironment[CitizenNameT]
  ): Unit = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!envByInterfaceRef.contains(interfaceRef))
    envByInterfaceRef += (interfaceRef -> env)
  }

  def declarePack(members: List[CoordT], understructRef2: StructRefT): Unit = {
    packTypes += (members -> understructRef2)
  }

  def add(structDef: StructDefinitionT): Unit = {
    if (structDef.mutability == ImmutableT) {
      if (structDef.members.exists(_.tyype.reference.ownership != ShareT)) {
        vfail("ImmutableP contains a non-immutable!")
      }
    }
    vassert(!structDefsByRef.contains(structDef.getRef))
    structDefsByRef += (structDef.getRef -> structDef)
  }

  def add(interfaceDef: InterfaceDefinitionT): Unit = {
    vassert(!interfaceDefsByRef.contains(interfaceDef.getRef))
    interfaceDefsByRef += (interfaceDef.getRef -> interfaceDef)
  }

  def addStaticSizedArray(array2: StaticSizedArrayTT): Unit = {
    staticSizedArrayTypes += ((array2.size, array2.array) -> array2)
  }

  def addRuntimeSizedArray(array2: RuntimeSizedArrayTT): Unit = {
    runtimeSizedArrayTypes += (array2.array -> array2)
  }

  def addImpl(structRef2: StructRefT, interfaceRef2: InterfaceRefT): Unit = {
    impls += ImplT(structRef2, interfaceRef2)
  }

  def addKindExport(kind: KindT, packageCoord: PackageCoordinate, exportedName: String): Unit = {
    kindExports += KindExportT(kind, packageCoord, exportedName)
  }

  def addFunctionExport(function: PrototypeT, packageCoord: PackageCoordinate, exportedName: String): Unit = {
    functionExports += FunctionExportT(function, packageCoord, exportedName)
  }

  def addKindExtern(kind: KindT, packageCoord: PackageCoordinate, exportedName: String): Unit = {
    kindExterns += KindExternT(kind, packageCoord, exportedName)
  }

  def addFunctionExtern(function: PrototypeT, packageCoord: PackageCoordinate, exportedName: String): Unit = {
    functionExterns += FunctionExternT(function, packageCoord, exportedName)
  }

  def addDestructor(kind: KindT, destructor: PrototypeT): Unit = {
    vassert(!kindToDestructor.contains(kind))
    vassert(prototypeDeclared(destructor.fullName).nonEmpty)
    kindToDestructor.put(kind, destructor)
  }

  def getDestructor(kind: KindT): PrototypeT = {
    vassertSome(kindToDestructor.get(kind))
  }

  def structDeclared(fullName: FullNameT[ICitizenNameT]): Option[StructRefT] = {
    // This is the only place besides StructDefinition2 and declareStruct thats allowed to make one of these
    val structRef = StructRefT(fullName)
    if (declaredStructs.contains(structRef)) {
      Some(structRef)
    } else {
      None
    }
  }

  def prototypeDeclared(fullName: FullNameT[IFunctionNameT]): Option[PrototypeT] = {
    declaredSignatures.find(_._1.fullName == fullName) match {
      case None => None
      case Some((sig, _)) => {
        returnTypesBySignature.get(sig) match {
          case None => None
          case Some(ret) => Some(PrototypeT(sig.fullName, ret))
        }
      }
    }
  }

  def lookupMutability(citizenRef2: CitizenRefT): MutabilityT = {
    // If it has a structRef, then we've at least started to evaluate this citizen
    mutabilitiesByCitizenRef.get(citizenRef2) match {
      case None => vfail("Still figuring out mutability for struct: " + citizenRef2) // See MFDBRE
      case Some(m) => m
    }
  }

  def lookupStruct(structRef: StructRefT): StructDefinitionT = {
    // If it has a structRef, then we're done (or at least have started) stamping it
    // If this throws an error, then you should not use this function, you should
    // do structDefsByRef.get(structRef) yourself and handle the None case
    vassertSome(structDefsByRef.get(structRef))
  }

  def lookupCitizen(citizenRef: CitizenRefT): CitizenDefinitionT = {
    citizenRef match {
      case s @ StructRefT(_) => lookupStruct(s)
      case i @ InterfaceRefT(_) => lookupInterface(i)
    }
  }

  def interfaceDeclared(fullName: FullNameT[CitizenNameT]): Option[InterfaceRefT] = {
    // This is the only place besides InterfaceDefinition2 and declareInterface thats allowed to make one of these
    val interfaceRef = InterfaceRefT(fullName)
    if (declaredInterfaces.contains(interfaceRef)) {
      Some(interfaceRef)
    } else {
      None
    }
  }

  def lookupInterface(interfaceRef: InterfaceRefT): InterfaceDefinitionT = {
    // If it has a interfaceRef, then we're done (or at least have started) stamping it.
    // If this throws an error, then you should not use this function, you should
    // do interfaceDefsByRef.get(interfaceRef) yourself and handle the None case
    interfaceDefsByRef(interfaceRef)
  }
//
//  def functionAlreadyDeclared(rangeS: RangeS, fullName: FullName2[IFunctionName2]): Boolean = {
//    declaredSignatures.get(Signature2(fullName)) == Some(rangeS)
//  }
//
//  def functionAlreadyDeclared(rangeS: RangeS, signature: Signature2): Boolean = {
//    declaredSignatures.contains(signature) == Some(rangeS)
//  }

  //  def findFunction(name: String, paramTypes: List[Coord]): Option[FunctionHeader2] = {
  //    val matchingFunctions = functions.find(this, name, paramTypes)
  //    vassert(matchingFunctions.size < 2)
  //    matchingFunctions.headOption
  //  }

  def getAllStructs(): Iterable[StructDefinitionT] = structDefsByRef.values
  def getAllInterfaces(): Iterable[InterfaceDefinitionT] = interfaceDefsByRef.values
  def getAllFunctions(): Iterable[FunctionT] = functions
  def getAllImpls(): Iterable[ImplT] = impls
  def getAllStaticSizedArrays(): Iterable[StaticSizedArrayTT] = staticSizedArrayTypes.values
  def getAllRuntimeSizedArrays(): Iterable[RuntimeSizedArrayTT] = runtimeSizedArrayTypes.values
  def getKindToDestructorMap(): Map[KindT, PrototypeT] = kindToDestructor.toMap

  def getStaticSizedArrayType(size: Int, array: RawArrayTT): Option[StaticSizedArrayTT] = {
    staticSizedArrayTypes.get((size, array))
  }
  def getEnvForFunctionSignature(sig: SignatureT): FunctionEnvironment = {
    envByFunctionSignature(sig)
  }
  def getEnvForInterfaceRef(sr: InterfaceRefT): PackageEnvironment[INameT] = {
    envByInterfaceRef(sr)
  }
  def getEnvForStructRef(sr: StructRefT): PackageEnvironment[INameT] = {
    envByStructRef(sr)
  }
  def getInterfaceDefForRef(ir: InterfaceRefT): InterfaceDefinitionT = {
    interfaceDefsByRef(ir)
  }
  def getPackType(coords: List[CoordT]): Option[StructRefT] = {
    packTypes.get(coords)
  }
  def getReturnTypeForSignature(sig: SignatureT): Option[CoordT] = {
    returnTypesBySignature.get(sig)
  }
  def getDeclaredSignatureOrigin(sig: SignatureT): Option[RangeS] = {
    declaredSignatures.get(sig)
  }
  def getDeclaredSignatureOrigin(name: FullNameT[IFunctionNameT]): Option[RangeS] = {
    declaredSignatures.get(SignatureT(name))
  }
  def getStructDefForRef(sr: StructRefT): StructDefinitionT = {
    structDefsByRef(sr)
  }
  def getRuntimeSizedArray(array: RawArrayTT): Option[RuntimeSizedArrayTT] = {
    runtimeSizedArrayTypes.get(array)
  }
  def getKindExports: List[KindExportT] = {
    kindExports.toList
  }
  def getFunctionExports: List[FunctionExportT] = {
    functionExports.toList
  }
  def getKindExterns: List[KindExternT] = {
    kindExterns.toList
  }
  def getFunctionExterns: List[FunctionExternT] = {
    functionExterns.toList
  }
}
