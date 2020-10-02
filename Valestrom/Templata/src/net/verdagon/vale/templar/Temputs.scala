package net.verdagon.vale.templar

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassert, vassertSome, vfail, vwat}

import scala.collection.immutable._
import scala.collection.mutable

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

case class InterfaceEdgeBlueprint(
  interface: InterfaceRef2,
  superFamilyRootBanners: List[FunctionBanner2])

case class Edge2(
  struct: StructRef2,
  interface: InterfaceRef2,
  methods: List[Prototype2])

object Program2 {
  val emptyTupleStructRef = StructRef2(FullName2(List(), TupleName2(List())))
  val emptyTupleType: PackT2 = PackT2(List(), Program2.emptyTupleStructRef)
  val emptyTupleReference: Coord = Coord(Share, emptyTupleType)
  val emptyPackExpression: PackE2 = PackE2(List(), Coord(Share, Program2.emptyTupleType), Program2.emptyTupleType)

  val intType = Coord(Share, Int2())
  val boolType = Coord(Share, Bool2())
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

case class Function2(
  header: FunctionHeader2,
  // Used for testing
  variables: List[ILocalVariable2],
  body: ReferenceExpression2) extends Queriable2 {

  def all[T](func: PartialFunction[Queriable2, T]): List[T] = {
    List(this).collect(func) ++ header.all(func) ++ variables.flatMap(_.all(func)) ++ body.all(func)
  }
}

case class Temputs() {

  // Signatures that have already started to be compiled.
  private val declaredSignatures: mutable.HashSet[Signature2] = mutable.HashSet()

  // Not all signatures/banners will have a return type here, it might not have been processed yet.
  private val returnTypesBySignature: mutable.HashMap[Signature2, Coord] = mutable.HashMap()

  // Not all signatures/banners or even return types will have a function here, it might not have
  // been processed yet.
  private val functions: mutable.ArrayBuffer[Function2] = mutable.ArrayBuffer()
  private val envByFunctionSignature: mutable.HashMap[Signature2, FunctionEnvironment] = mutable.HashMap()

  // Prototypes for extern functions
  private val externPrototypes: mutable.HashSet[Prototype2] = mutable.HashSet()

  // One must fill this in when putting things into declaredStructs/Interfaces.
  private val mutabilitiesByCitizenRef: mutable.HashMap[CitizenRef2, Mutability] = mutable.HashMap()

  // declaredStructs is the structs that we're currently in the process of defining
  // Things will appear here before they appear in structDefsByRef
  // This is to prevent infinite recursion / stack overflow when templaring recursive types
  // Not too sure about the type of declaredStructs, we might need something else
  private val declaredStructs: mutable.HashSet[StructRef2] = mutable.HashSet()
  private val structDefsByRef: mutable.HashMap[StructRef2, StructDefinition2] = mutable.HashMap()
  private val envByStructRef: mutable.HashMap[StructRef2, NamespaceEnvironment[IName2]] = mutable.HashMap()
  // declaredInterfaces is the interfaces that we're currently in the process of defining
  // Things will appear here before they appear in interfaceDefsByRef
  // This is to prevent infinite recursion / stack overflow when templaring recursive types
  // Not too sure about the type of declaredInterfaces, we might need something else
  private val declaredInterfaces: mutable.HashSet[InterfaceRef2] = mutable.HashSet()
  private val interfaceDefsByRef: mutable.HashMap[InterfaceRef2, InterfaceDefinition2] = mutable.HashMap()
  private val envByInterfaceRef: mutable.HashMap[InterfaceRef2, NamespaceEnvironment[IName2]] = mutable.HashMap()

  private val impls: mutable.ArrayBuffer[Impl2] = mutable.ArrayBuffer()

  // Only PackTemplar can make a PackT2.
  private val packTypes: mutable.HashMap[List[Coord], StructRef2] = mutable.HashMap()
  // Only ArrayTemplar can make an RawArrayT2.
  private val arraySequenceTypes: mutable.HashMap[(Int, RawArrayT2), KnownSizeArrayT2] = mutable.HashMap()
  // Only ArrayTemplar can make an RawArrayT2.
  private val unknownSizeArrayTypes: mutable.HashMap[RawArrayT2, UnknownSizeArrayT2] = mutable.HashMap()

  def lookupFunction(humanName: String): Function2 = {
    val matches = getAllFunctions().filter(f => {
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

  def lookupStruct(humanName: String): StructDefinition2 = {
    val matches = getAllStructs().filter(s => {
      s.fullName.last match {
        case CitizenName2(n, _) if n == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Struct \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupInterface(humanName: String): InterfaceDefinition2 = {
    val matches = getAllInterfaces().filter(s => {
      s.fullName.last match {
        case CitizenName2(n, _) if n == humanName => true
        case _ => false
      }
    })
    if (matches.size == 0) {
      vfail("Interface \"" + humanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupUserFunction(humanName: String): Function2 = {
    val matches =
      getAllFunctions()
        .filter(function => simpleName.unapply(function.header.fullName).contains(humanName))
        .filter(_.header.isUserFunction)
    if (matches.size == 0) {
      vfail("Not found!")
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
    val matches = getAllFunctions().filter(f => nameIsLambdaIn(f.header.fullName, needleFunctionHumanName))
    if (matches.size == 0) {
      vfail("Lambda for \"" + needleFunctionHumanName + "\" not found!")
    } else if (matches.size > 1) {
      vfail("Multiple found!")
    }
    matches.head
  }

  def lookupImpl(structRef: StructRef2, interfaceRef: InterfaceRef2): Impl2 = {
    getAllImpls().find(impl => impl.struct == structRef && impl.interface == interfaceRef).get
  }

  def getAllNonExternFunctions: List[Function2] = {
    getAllFunctions().filter(!_.header.isExtern)
  }
  def getAllUserFunctions: List[Function2] = {
    getAllFunctions().filter(_.header.isUserFunction)
  }

  def lookupFunction(signature2: Signature2): Option[Function2] = {
    functions.find(_.header.toSignature == signature2)
  }

  // This means we've at least started to evaluate this function's body.
  // We use this to cut short any infinite looping that might happen when,
  // for example, there's a recursive function call.
  def declareFunctionSignature(signature: Signature2, maybeEnv: Option[FunctionEnvironment]): Unit = {
    // The only difference between this and declareNonGlobalFunctionSignature is
    // that we put an environment in here.

    if (declaredSignatures.contains(signature)) {
      vfail("wat")
    }
    declaredSignatures += signature
    envByFunctionSignature ++= maybeEnv.map(env => Map(signature -> env)).getOrElse(Map())
    this
  }

  def declareFunctionReturnType(signature: Signature2, returnType2: Coord): Unit = {
    returnTypesBySignature.get(signature) match {
      case None =>
      case Some(existingReturnType2) => vassert(existingReturnType2 == returnType2)
    }
    if (!declaredSignatures.contains(signature)) {
      vfail("wot")
    }
    returnTypesBySignature += (signature -> returnType2)
  }

  def addFunction(function: Function2): Unit = {
    vassert(declaredSignatures.contains(function.header.toSignature))

    if (functions.exists(_.header == function.header)) {
      vfail("wot")
    }

    functions += function
  }

  // We can't declare the struct at the same time as we declare its mutability or environment,
  // see MFDBRE.
  def declareStruct(
    structRef: StructRef2
  ): Unit = {
    vassert(!declaredStructs.contains(structRef))
    declaredStructs += structRef
  }

  def declareStructMutability(
    structRef: StructRef2,
    mutability: Mutability
  ): Unit = {
    vassert(declaredStructs.contains(structRef))
    vassert(!mutabilitiesByCitizenRef.contains(structRef))
    mutabilitiesByCitizenRef += (structRef -> mutability)
  }

  def declareStructEnv(
    structRef: StructRef2,
    env: NamespaceEnvironment[ICitizenName2],
  ): Unit = {
    vassert(declaredStructs.contains(structRef))
    vassert(!envByStructRef.contains(structRef))
    envByStructRef += (structRef -> env)
  }

  def declareInterface(interfaceRef: InterfaceRef2): Unit = {
    vassert(!declaredInterfaces.contains(interfaceRef))
    declaredInterfaces += interfaceRef
  }

  def declareInterfaceMutability(
    interfaceRef: InterfaceRef2,
    mutability: Mutability
  ): Unit = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!mutabilitiesByCitizenRef.contains(interfaceRef))
    mutabilitiesByCitizenRef += (interfaceRef -> mutability)
  }

  def declareInterfaceEnv(
    interfaceRef: InterfaceRef2,
    env: NamespaceEnvironment[CitizenName2]
  ): Unit = {
    vassert(declaredInterfaces.contains(interfaceRef))
    vassert(!envByInterfaceRef.contains(interfaceRef))
    envByInterfaceRef += (interfaceRef -> env)
  }

  def declarePack(members: List[Coord], understructRef2: StructRef2): Unit = {
    packTypes += (members -> understructRef2)
  }

  def add(structDef: StructDefinition2): Unit = {
    if (structDef.mutability == Immutable) {
      if (structDef.members.exists(_.tyype.reference.ownership != Share)) {
        vfail("ImmutableP contains a non-immutable!")
      }
    }
    vassert(!structDefsByRef.contains(structDef.getRef))
    structDefsByRef += (structDef.getRef -> structDef)
  }

  def add(interfaceDef: InterfaceDefinition2): Unit = {
    vassert(!interfaceDefsByRef.contains(interfaceDef.getRef))
    interfaceDefsByRef += (interfaceDef.getRef -> interfaceDef)
  }

  def addArraySequence(size: Int, array2: KnownSizeArrayT2): Unit = {
    arraySequenceTypes += ((size, array2.array) -> array2)
  }

  def addUnknownSizeArray(array2: UnknownSizeArrayT2): Unit = {
    unknownSizeArrayTypes += (array2.array -> array2)
  }

  def addImpl(structRef2: StructRef2, interfaceRef2: InterfaceRef2): Unit = {
    impls += Impl2(structRef2, interfaceRef2)
  }

  def addExternPrototype(prototype2: Prototype2): Unit = {
    prototype2.fullName.last match {
      case n @ ExternFunctionName2(_, _) => {
        vassert(!externPrototypes.exists(_.toSignature.fullName.last == n))
      }
    }
    externPrototypes += prototype2
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

  def getAllFunctions(): List[Function2] = functions.toList

  def getAllImpls(): List[Impl2] = impls.toList

  def getArraySequenceType(size: Int, array: RawArrayT2): Option[KnownSizeArrayT2] = {
    arraySequenceTypes.get((size, array))
  }
  def getEnvForFunctionSignature(sig: Signature2): FunctionEnvironment = {
    envByFunctionSignature(sig)
  }
  def getEnvForInterfaceRef(sr: InterfaceRef2): NamespaceEnvironment[IName2] = {
    envByInterfaceRef(sr)
  }
  def getEnvForStructRef(sr: StructRef2): NamespaceEnvironment[IName2] = {
    envByStructRef(sr)
  }
  def getInterfaceDefForRef(ir: InterfaceRef2): InterfaceDefinition2 = {
    interfaceDefsByRef(ir)
  }
  def getPackType(coords: List[Coord]): Option[StructRef2] = {
    packTypes.get(coords)
  }
  def getReturnTypeForSignature(sig: Signature2): Option[Coord] = {
    returnTypesBySignature.get(sig)
  }
  def getStructDefForRef(sr: StructRef2): StructDefinition2 = {
    structDefsByRef(sr)
  }
  def getUnknownSizeArray(array: RawArrayT2): Option[UnknownSizeArrayT2] = {
    unknownSizeArrayTypes.get(array)
  }
  def getExternPrototypes: List[Prototype2] = {
    externPrototypes.toList
  }
}