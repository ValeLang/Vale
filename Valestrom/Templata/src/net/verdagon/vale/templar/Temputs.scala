package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vassertSome, vfail, vwat}

import scala.collection.immutable._

// We won't always have a return type for a banner... it might have not specified its return
// type, so we're currently evaluating the entire body for it right now.
// If we ever find ourselves wanting the return type for a banner, we need to:
// - Check if it's in the returnTypesByBanner map. If so, good.
// - If not, then check if the banner is in declaredBanners. If so, then we're currently in
//   the process of evaluating the entire body. In this case, throw an error because we're
//   about to infinite loop. Hopefully this is a user error, they need to specify a return
//   type to avoid a cyclical definition.
// - If not in declared banners, then tell FunctionTemplar to start evaluating it.

case class Impl2(
  struct: StructRef2,
  interface: InterfaceRef2
) extends Queriable2 {
  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    struct.all(func) ++ interface.all(func)
  }
}

case class Temputs(
    functionGeneratorByName: Map[String, IFunctionGenerator],

    // Signatures that have already started to be compiled.
    declaredSignatures: Set[Signature2],

    // Not all signatures/banners will have a return type here, it might not have been processed yet.
    returnTypesBySignature: Map[Signature2, Coord],

    // Not all signatures/banners or even return types will have a function here, it might not have
    // been processed yet.
    functions: List[Function2],
    envByFunctionSignature: ListMap[Signature2, FunctionEnvironment],

    // One must fill this in when putting things into declaredStructs/Interfaces.
    mutabilitiesByCitizenRef: Map[CitizenRef2, Mutability],

    // declaredStructs is the structs that we're currently in the process of defining
    // Things will appear here before they appear in structDefsByRef
    // This is to prevent infinite recursion / stack overflow when templaring recursive types
    // Not too sure about the type of declaredStructs, we might need something else
    declaredStructs: Set[StructRef2],
    structDefsByRef: ListMap[StructRef2, StructDefinition2],
    envByStructRef: Map[StructRef2, NamespaceEnvironment[IName2]],
    // declaredInterfaces is the interfaces that we're currently in the process of defining
    // Things will appear here before they appear in interfaceDefsByRef
    // This is to prevent infinite recursion / stack overflow when templaring recursive types
    // Not too sure about the type of declaredInterfaces, we might need something else
    declaredInterfaces: Set[InterfaceRef2],
    interfaceDefsByRef: ListMap[InterfaceRef2, InterfaceDefinition2],
    envByInterfaceRef: Map[InterfaceRef2, NamespaceEnvironment[IName2]],

    impls: List[Impl2],

  // Only PackTemplar can make a PackT2.
    packTypes: Map[List[Coord], StructRef2],
    // Only ArrayTemplar can make an RawArrayT2.
    arraySequenceTypes: Map[(Int, RawArrayT2), ArraySequenceT2],
    // Only ArrayTemplar can make an RawArrayT2.
    unknownSizeArrayTypes: Map[RawArrayT2, UnknownSizeArrayT2]

  // Consider breaking the Temputs into smaller substructs, like
  // CitizenTemputs, FunctionTemputs, etc.

) {
  def lookupFunction(signature2: Signature2): Option[Function2] = {
    functions.find(_.header.toSignature == signature2)
  }

  // This means we've at least started to evaluate this function's body.
  // We use this to cut short any infinite looping that might happen when,
  // for example, there's a recursive function call.
  def declareFunctionSignature(signature: Signature2, maybeEnv: Option[FunctionEnvironment]): Temputs = {
    // The only difference between this and declareNonGlobalFunctionSignature is
    // that we put an environment in here.

    if (declaredSignatures.contains(signature)) {
      vfail("wat")
    }
    Temputs(
      functionGeneratorByName,
      declaredSignatures + signature,
      returnTypesBySignature,
      functions,
      envByFunctionSignature ++ maybeEnv.map(env => Map(signature -> env)).getOrElse(Map()),
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def declareFunctionReturnType(signature: Signature2, returnType2: Coord): Temputs = {
    returnTypesBySignature.get(signature) match {
      case None =>
      case Some(existingReturnType2) => vassert(existingReturnType2 == returnType2)
    }
    if (!declaredSignatures.contains(signature)) {
      vfail("wot")
    }

    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature + (signature -> returnType2),
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def addFunction(function: Function2): Temputs = {
    vassert(declaredSignatures.contains(function.header.toSignature))

    if (functions.exists(_.header == function.header)) {
      vfail("wot")
    }

    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      function :: functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  // We can't declare the struct at the same time as we declare its mutability or environment,
  // see MFDBRE.
  def declareStruct(
      structRef: StructRef2
  ): Temputs = {
    vassert(!declaredStructs.contains(structRef))
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs + structRef,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def declareStructMutability(
    structRef: StructRef2,
    mutability: Mutability
  ): Temputs = {
    vassert(declaredStructs.contains(structRef))
    vassert(!mutabilitiesByCitizenRef.contains(structRef))
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef + (structRef -> mutability),
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def declareStructEnv(
    structRef: StructRef2,
    env: NamespaceEnvironment[IName2],
  ): Temputs = {
    vassert(declaredStructs.contains(structRef))
    vassert(!envByStructRef.contains(structRef))
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef + (structRef -> env),
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def declareInterface(interfaceRef: InterfaceRef2): Temputs = {
    vassert(!declaredInterfaces.contains(interfaceRef))
    val newTemputs =
      Temputs(
        functionGeneratorByName,
        declaredSignatures,
        returnTypesBySignature,
        functions,
        envByFunctionSignature,
        mutabilitiesByCitizenRef,
        declaredStructs,
        structDefsByRef,
        envByStructRef,
        declaredInterfaces + interfaceRef,
        interfaceDefsByRef,
        envByInterfaceRef,
        impls,
        packTypes,
        arraySequenceTypes,
        unknownSizeArrayTypes)
    newTemputs
  }

  def declareInterfaceMutability(
    interfaceRef: InterfaceRef2,
    mutability: Mutability
  ): Temputs = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!mutabilitiesByCitizenRef.contains(interfaceRef))
    val newTemputs =
      Temputs(
        functionGeneratorByName,
        declaredSignatures,
        returnTypesBySignature,
        functions,
        envByFunctionSignature,
        mutabilitiesByCitizenRef + (interfaceRef -> mutability),
        declaredStructs,
        structDefsByRef,
        envByStructRef,
        declaredInterfaces,
        interfaceDefsByRef,
        envByInterfaceRef,
        impls,
        packTypes,
        arraySequenceTypes,
        unknownSizeArrayTypes)
    newTemputs
  }

  def declareInterfaceEnv(
    interfaceRef: InterfaceRef2,
    env: NamespaceEnvironment[CitizenName2]
  ): Temputs = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!envByInterfaceRef.contains(interfaceRef))
    val newTemputs =
      Temputs(
        functionGeneratorByName,
        declaredSignatures,
        returnTypesBySignature,
        functions,
        envByFunctionSignature,
        mutabilitiesByCitizenRef,
        declaredStructs,
        structDefsByRef,
        envByStructRef,
        declaredInterfaces,
        interfaceDefsByRef,
        envByInterfaceRef + (interfaceRef -> env),
        impls,
        packTypes,
        arraySequenceTypes,
        unknownSizeArrayTypes)
    newTemputs
  }

  def declarePack(members: List[Coord], understructRef2: StructRef2):
  Temputs = {
    val newTemputs =
      Temputs(
        functionGeneratorByName,
        declaredSignatures,
        returnTypesBySignature,
        functions,
        envByFunctionSignature,
        mutabilitiesByCitizenRef,
        declaredStructs,
        structDefsByRef,
        envByStructRef,
        declaredInterfaces,
        interfaceDefsByRef,
        envByInterfaceRef,
        impls,
        packTypes + (members -> understructRef2),
        arraySequenceTypes,
        unknownSizeArrayTypes)
    newTemputs
  }

  def add(structDef: StructDefinition2): Temputs = {
    if (structDef.mutability == Immutable) {
      if (structDef.members.exists(_.tyype.reference.ownership != Share)) {
        vfail("ImmutableP contains a non-immutable!")
      }
    }
    vassert(!structDefsByRef.contains(structDef.getRef))

    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef + (structDef.getRef -> structDef),
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def add(interfaceDef: InterfaceDefinition2):
  Temputs = {
    vassert(!interfaceDefsByRef.contains(interfaceDef.getRef))

    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef + (interfaceDef.getRef -> interfaceDef),
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def addArraySequence(size: Int, array2: ArraySequenceT2): Temputs = {
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes + ((size, array2.array) -> array2),
      unknownSizeArrayTypes)
  }

  def addUnknownSizeArray(array2: UnknownSizeArrayT2): Temputs = {
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes + (array2.array -> array2))
  }

  def addImpl(structRef2: StructRef2, interfaceRef2: InterfaceRef2): Temputs = {
    Temputs(
      functionGeneratorByName,
      declaredSignatures,
      returnTypesBySignature,
      functions,
      envByFunctionSignature,
      mutabilitiesByCitizenRef,
      declaredStructs,
      structDefsByRef,
      envByStructRef,
      declaredInterfaces,
      interfaceDefsByRef,
      envByInterfaceRef,
      Impl2(structRef2, interfaceRef2) :: impls,
      packTypes,
      arraySequenceTypes,
      unknownSizeArrayTypes)
  }

  def structDeclared(fullName: FullName2[ICitizenName2]): Option[StructRef2] = {
    // This is the only place besides StructDefinition2 and declareStruct thats allowed to make one of these
    val structRef = StructRef2(fullName)
    if (declaredStructs.contains(structRef)) {
      Some(structRef)
    } else {
      None
    }
  }

  def prototypeDeclared(fullName: FullName2[IFunctionName2]): Option[Prototype2] = {
    declaredSignatures.find(_.fullName == fullName) match {
      case None => None
      case Some(sig) => {
        returnTypesBySignature.get(sig) match {
          case None => None
          case Some(ret) => Some(Prototype2(sig.fullName, ret))
        }
      }
    }
  }

  def lookupMutability(citizenRef2: CitizenRef2): Mutability = {
    // If it has a structRef, then we've at least started to evaluate this citizen
    mutabilitiesByCitizenRef.get(citizenRef2) match {
      case None => vfail("Still figuring out mutability for struct: " + citizenRef2) // See MFDBRE
      case Some(m) => m
    }
  }

  def lookupStruct(structRef: StructRef2): StructDefinition2 = {
    // If it has a structRef, then we're done (or at least have started) stamping it
    // If this throws an error, then you should not use this function, you should
    // do structDefsByRef.get(structRef) yourself and handle the None case
    vassertSome(structDefsByRef.get(structRef))
  }

  def lookupCitizen(citizenRef: CitizenRef2): CitizenDefinition2 = {
    citizenRef match {
      case s @ StructRef2(_) => lookupStruct(s)
      case i @ InterfaceRef2(_) => lookupInterface(i)
    }
  }

  def interfaceDeclared(fullName: FullName2[CitizenName2]): Option[InterfaceRef2] = {
    // This is the only place besides InterfaceDefinition2 and declareInterface thats allowed to make one of these
    val interfaceRef = InterfaceRef2(fullName)
    if (declaredInterfaces.contains(interfaceRef)) {
      Some(interfaceRef)
    } else {
      None
    }
  }

  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2 = {
    // If it has a interfaceRef, then we're done (or at least have started) stamping it.
    // If this throws an error, then you should not use this function, you should
    // do interfaceDefsByRef.get(interfaceRef) yourself and handle the None case
    interfaceDefsByRef(interfaceRef)
  }

  def exactDeclaredSignatureExists(fullName: FullName2[IFunctionName2]): Boolean = {
    declaredSignatures.contains(Signature2(fullName))
  }

  def exactDeclaredSignatureExists(signature: Signature2): Boolean = {
    declaredSignatures.contains(signature)
  }

//  def findFunction(name: String, paramTypes: List[Coord]): Option[FunctionHeader2] = {
//    val matchingFunctions = functions.find(this, name, paramTypes)
//    vassert(matchingFunctions.size < 2)
//    matchingFunctions.headOption
//  }

  def getAllStructs(): List[StructDefinition2] = structDefsByRef.values.toList

  def getAllInterfaces(): List[InterfaceDefinition2] = interfaceDefsByRef.values.toList

  def getAllFunctions(): List[Function2] = functions


  def getAllNonExternFunctions: List[Function2] = {
    functions.filter(!_.header.isExtern)
  }
  def getAllUserFunctions: List[Function2] = {
    functions.filter(_.header.isUserFunction)
  }

  def lookupUserFunction(humanName: String): Function2 = {
    val matches =
      functions
        .filter(function => simpleName.unapply(function.header.fullName).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupFunction(humanName: String): Function2 = {
    val matches = functions.filter(f => {
      f.header.fullName.last match {
        case FunctionName2(n, _, _) if n == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Function \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def nameIsLambdaIn(name: FullName2[IFunctionName2], needleFunctionHumanName: String): Boolean = {
    val lastThree = name.steps.slice(name.steps.size - 3, name.steps.size)
    lastThree match {
      case List(
      FunctionName2(functionHumanName, _, _),
      LambdaCitizenName2(_),
      FunctionName2("__call", _, _)) if functionHumanName == needleFunctionHumanName => true
      case _ => false
    }
  }

  def lookupLambdaIn(needleFunctionHumanName: String): Function2 = {
    val matches = functions.filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName))
    if (matches.size == 0) {
      vfail("Lambda for \"" + needleFunctionHumanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupImpl(structRef: StructRef2, interfaceRef: InterfaceRef2): Impl2 = {
    impls.find(impl => impl.struct == structRef && impl.interface == interfaceRef).get
  }
}

case class TemputsBox(var temputs: Temputs) {
  def functionGeneratorByName: Map[String, IFunctionGenerator] = temputs.functionGeneratorByName
  def declaredSignatures: Set[Signature2] = temputs.declaredSignatures
  def returnTypesBySignature: Map[Signature2, Coord] = temputs.returnTypesBySignature
  def functions: List[Function2] = temputs.functions
  def envByFunctionSignature: ListMap[Signature2, FunctionEnvironment] = temputs.envByFunctionSignature
  def mutabilitiesByCitizenRef: Map[CitizenRef2, Mutability] = temputs.mutabilitiesByCitizenRef
  def declaredStructs: Set[StructRef2] = temputs.declaredStructs
  def structDefsByRef: ListMap[StructRef2, StructDefinition2] = temputs.structDefsByRef
  def envByStructRef: Map[StructRef2, NamespaceEnvironment[IName2]] = temputs.envByStructRef
  def declaredInterfaces: Set[InterfaceRef2] = temputs.declaredInterfaces
  def interfaceDefsByRef: ListMap[InterfaceRef2, InterfaceDefinition2] = temputs.interfaceDefsByRef
  def envByInterfaceRef: Map[InterfaceRef2, NamespaceEnvironment[IName2]] = temputs.envByInterfaceRef
  def impls: List[Impl2] = temputs.impls
  def packTypes: Map[List[Coord], StructRef2] = temputs.packTypes
  def arraySequenceTypes: Map[(Int, RawArrayT2), ArraySequenceT2] = temputs.arraySequenceTypes
  def unknownSizeArrayTypes: Map[RawArrayT2, UnknownSizeArrayT2] = temputs.unknownSizeArrayTypes

  def lookupFunction(signature2: Signature2): Option[Function2] = temputs.lookupFunction(signature2)

  def declareFunctionSignature(signature: Signature2, maybeEnv: Option[FunctionEnvironment]): Unit = {
    temputs = temputs.declareFunctionSignature(signature, maybeEnv)
  }

  def declareFunctionReturnType(signature: Signature2, returnType2: Coord): Unit = {
    temputs = temputs.declareFunctionReturnType(signature, returnType2)
  }

  def addFunction(function: Function2): Unit = {
    temputs = temputs.addFunction(function)
  }

  // We can't declare the struct at the same time as we declare its mutability or environment,
  // see MFDBRE.
  def declareStruct(structRef: StructRef2): Unit = {
    temputs = temputs.declareStruct(structRef)
  }

  def declareStructMutability(structRef: StructRef2, mutability: Mutability): Unit = {
    temputs = temputs.declareStructMutability(structRef, mutability)
  }

  def declareStructEnv(structRef: StructRef2, env: NamespaceEnvironment[ICitizenName2]): Unit = {
    temputs = temputs.declareStructEnv(structRef, env)
  }

  def declareInterface(interfaceRef: InterfaceRef2): Unit = {
    temputs = temputs.declareInterface(interfaceRef)
  }

  def declareInterfaceMutability(interfaceRef: InterfaceRef2, mutability: Mutability): Unit = {
    temputs = temputs.declareInterfaceMutability(interfaceRef, mutability)
  }

  def declareInterfaceEnv(interfaceRef: InterfaceRef2, env: NamespaceEnvironment[CitizenName2]): Unit = {
    temputs = temputs.declareInterfaceEnv(interfaceRef, env)
  }

  def declarePack(members: List[Coord], understructRef2: StructRef2): Unit = {
    temputs = temputs.declarePack(members, understructRef2)
  }

  def add(structDef: StructDefinition2): Unit = {
    temputs = temputs.add(structDef)
  }

  def add(interfaceDef: InterfaceDefinition2): Unit = {
    temputs = temputs.add(interfaceDef)
  }

  def addArraySequence(size: Int, array2: ArraySequenceT2): Unit = {
    temputs = temputs.addArraySequence(size, array2)
  }

  def addUnknownSizeArray(array2: UnknownSizeArrayT2): Unit = {
    temputs = temputs.addUnknownSizeArray(array2)
  }

  def addImpl(structRef2: StructRef2, interfaceRef2: InterfaceRef2): Unit = {
    temputs = temputs.addImpl(structRef2, interfaceRef2)
  }

  def structDeclared(fullName: FullName2[ICitizenName2]): Option[StructRef2] = temputs.structDeclared(fullName)

  def prototypeDeclared(fullName: FullName2[IFunctionName2]): Option[Prototype2] = temputs.prototypeDeclared(fullName)

  def lookupMutability(citizenRef2: CitizenRef2): Mutability = temputs.lookupMutability(citizenRef2)

  def lookupStruct(structRef: StructRef2): StructDefinition2 = temputs.lookupStruct(structRef)

  def lookupCitizen(citizenRef: CitizenRef2): CitizenDefinition2 = temputs.lookupCitizen(citizenRef)

  def interfaceDeclared(fullName: FullName2[CitizenName2]): Option[InterfaceRef2] = temputs.interfaceDeclared(fullName)

  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2 = temputs.lookupInterface(interfaceRef)

  def exactDeclaredSignatureExists(fullName: FullName2[IFunctionName2]): Boolean = {
    temputs.exactDeclaredSignatureExists(fullName)
  }

  def exactDeclaredSignatureExists(signature: Signature2): Boolean = {
    temputs.exactDeclaredSignatureExists(signature)
  }

  def getAllStructs(): List[StructDefinition2] = temputs.getAllStructs()

  def getAllInterfaces(): List[InterfaceDefinition2] = temputs.getAllInterfaces()

  def getAllFunctions(): List[Function2] = temputs.getAllFunctions()
}

case class InterfaceEdgeBlueprint(
  interface: InterfaceRef2,
  superFamilyRootBanners: List[FunctionBanner2])

case class Edge2(
  struct: StructRef2,
  interface: InterfaceRef2,
  methods: List[Prototype2])

object Program2 {
  val emptyTupleStructRef = StructRef2(FullName2(List(), TupleName2(List())))
  val emptyPackType: PackT2 = PackT2(List(), Program2.emptyTupleStructRef)
  val emptyPackReference: Coord = Coord(Share, emptyPackType)

}

//trait Program2 {
//  def getAllInterfaces: Set[InterfaceDefinition2]
//  def getAllStructs: Set[StructDefinition2]
//  def getAllImpls: List[Impl2]
//  def getAllFunctions: Set[Function2]
//  def getAllCitizens: Set[CitizenDefinition2] = getAllInterfaces ++ getAllStructs
//  def getAllExterns: Set[FunctionHeader2]
//  def emptyPackStructRef: StructRef2
//
//  def lookupStruct(structRef: StructRef2): StructDefinition2;
//  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2;
//  def lookupCitizen(citizenRef: CitizenRef2): CitizenDefinition2;
//  def lookupFunction(signature2: Signature2): Option[Function2];
//
//  def getAllNonExternFunctions: Set[Function2] = {
//    getAllFunctions.filter(!_.header.isExtern)
//  }
//  def getAllUserFunctions: Set[Function2] = {
//    getAllFunctions.filter(_.header.isUserFunction)
//  }
//}

case class CompleteProgram2(
  interfaces: List[InterfaceDefinition2],
  structs: List[StructDefinition2],
  impls: List[Impl2],
  emptyPackStructRef: StructRef2,
  functions: List[Function2]) extends Queriable2 {
  def getAllNonExternFunctions: List[Function2] = {
    functions.filter(!_.header.isExtern)
  }
  def getAllUserFunctions: List[Function2] = {
    functions.filter(_.header.isUserFunction)
  }
  def lookupStruct(structRef: StructRef2): StructDefinition2 = {
    structs.find(_.getRef == structRef).get
  }
  def lookupImpl(structRef: StructRef2, interfaceRef: InterfaceRef2): Impl2 = {
    impls.find(impl => impl.struct == structRef && impl.interface == interfaceRef).get
  }

  def lookupInterface(interfaceRef: InterfaceRef2): InterfaceDefinition2 = {
    interfaces.find(_.getRef == interfaceRef).get
  }

  def lookupCitizen(citizenRef2: CitizenRef2): CitizenDefinition2 = {
    citizenRef2 match {
      case s@ StructRef2(_) => lookupStruct(s)
      case i @ InterfaceRef2(_) => lookupInterface(i)
    }
  }

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    interfaces.flatMap(_.all(func)) ++
      structs.flatMap(_.all(func)) ++
      functions.toList.flatMap(_.all(func))
  }

  def lookupUserFunction(humanName: String): Function2 = {
    val matches =
      functions
        .filter(function => simpleName.unapply(function.header.fullName).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupFunction(humanName: String): Function2 = {
    val matches = functions.filter(f => {
      f.header.fullName.last match {
        case FunctionName2(n, _, _) if n == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Function \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def nameIsLambdaIn(name: FullName2[IFunctionName2], needleFunctionHumanName: String): Boolean = {
    val lastThree = name.steps.slice(name.steps.size - 3, name.steps.size)
    lastThree match {
      case List(
      FunctionName2(functionHumanName, _, _),
      LambdaCitizenName2(_),
      FunctionName2("__call", _, _)) if functionHumanName == needleFunctionHumanName => true
      case _ => false
    }
  }

  def lookupLambdaIn(needleFunctionHumanName: String): Function2 = {
    val matches = functions.filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName))
    if (matches.size == 0) {
      vfail("Lambda for \"" + needleFunctionHumanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupFunction(signature2: Signature2): Option[Function2] = {
    functions.find(_.header.toSignature == signature2).headOption
  }
}

case class Function2(
  header: FunctionHeader2,
  // Used for testing
  variables: List[ILocalVariable2],
  body: ReferenceExpression2) extends Queriable2 {

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ header.all(func) ++ variables.flatMap(_.all(func)) ++ body.all(func)
  }
}

trait IFunctionGenerator {
  def generate(
    env: FunctionEnvironment,
    temputs: TemputsBox,
    // We might be able to move these all into the function environment... maybe....
    originFunction: Option[FunctionA],
    paramCoords: List[Parameter2],
    maybeRetCoord: Option[Coord]):
  (FunctionHeader2)
}
