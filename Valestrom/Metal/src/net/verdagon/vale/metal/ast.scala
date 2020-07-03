package net.verdagon.vale.metal

import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl}
import net.verdagon.von.{IVonData, JsonSyntax, VonArray, VonMember, VonObject, VonPrinter, VonStr, VonSyntax}

import scala.collection.immutable.ListMap

object ProgramH {
  val emptyTupleStructRef =
    // If the templar ever decides to change this things name, update this to match templar's.
    StructRefH(FullNameH(List(VonObject("Tup",None,Vector(VonMember("members",VonArray(None,Vector())))))))
  val emptyTupleStructType = ReferenceH(ShareH, emptyTupleStructRef)
}

case class ProgramH(
  interfaces: List[InterfaceDefinitionH],
  structs: List[StructDefinitionH],
  externs: List[PrototypeH],
  functions: List[FunctionH]) {
  def externFunctions = functions.filter(_.isExtern)
  def abstractFunctions = functions.filter(_.isAbstract)
  // Functions that are neither extern nor abstract
  def getAllUserImplementedFunctions = functions.filter(f => f.isUserFunction && !f.isExtern && !f.isAbstract)
  // Abstract or implemented
  def nonExternFunctions = functions.filter(!_.isExtern)
  def getAllUserFunctions = functions.filter(_.isUserFunction)
  def main() = {
    val matching = functions.filter(f => f.fullName.toString.startsWith("F(\"main\"") && f.fullName.parts.size == 1)
    vassert(matching.size == 1)
    matching.head
  }

  def lookupFunction(humanName: String) = {
    val matches = functions.filter(_.fullName.parts.last == vimpl(humanName))
    vassert(matches.size == 1)
    matches.head
  }

  // Must be at top level
  def lookupStruct(humanName: String) = {
    val matches = structs.filter(_.fullName.toString.startsWith(s"""C("${humanName}""""))
    vassert(matches.size == 1)
    matches.head
  }
}

case class StructDefinitionH(
    fullName: FullNameH,
    export: Boolean,
    mutability: Mutability,
    edges: List[EdgeH],
    members: List[StructMemberH]) {

  def getRef: StructRefH = StructRefH(fullName)

  // These functions are tightly coupled with StructSculptor.declareStructInfo
  def getInterfacePtrElementIndex(interfaceRef: InterfaceRefH): Int = {
    val index = edges.indexWhere(_.interface == interfaceRef)
    vassert(index >= 0)
    index
  }
  def getSInfoPtrElementIndex(): Int = {
    edges.size + 1
  }

  def getMemberLlvmIndex(memberIndex: Int): Int = {
    vassert(memberIndex < members.size)
    edges.size + 2 + memberIndex
  }

  def getTypeAndIndex(memberName: String): (ReferenceH[ReferendH], Int) = {
    members.zipWithIndex.find(p => p._1.name.equals(memberName)) match {
      case None => vfail("wat " + this + " " + memberName)
      case Some((member, index)) => (member.tyype, index)
    }
  }
}

case class StructMemberH(
  name: FullNameH,
  variability: Variability,
  tyype: ReferenceH[ReferendH])

case class InterfaceMethodH(
    prototypeH: PrototypeH,
    virtualParamIndex: Int) {
  assert(virtualParamIndex >= 0)
}

case class InterfaceDefinitionH(
  fullName: FullNameH,
  mutability: Mutability,
  // TODO: Change this to edges, since interfaces impl other interfaces.
  superInterfaces: List[InterfaceRefH],
  methods: List[InterfaceMethodH]) {
  def getRef = InterfaceRefH(fullName)
}

// Represents how a struct implements an interface.
// Each edge has a vtable.
case class EdgeH(
  struct: StructRefH,
  interface: InterfaceRefH,
  structPrototypesByInterfaceMethod: ListMap[InterfaceMethodH, PrototypeH])

case class FunctionH(
  prototype: PrototypeH,
  // TODO: Get rid of this, since it's only for testing. Perhaps use an external set?
  isAbstract: Boolean,
  // TODO: Get rid of this, since it's only for testing. Perhaps use an external set?
  isExtern: Boolean,
  // TODO: Get rid of this, since it's only for testing. Perhaps use an external set?
  isUserFunction: Boolean,
  body: ExpressionH[ReferendH]) {
  def fullName = prototype.fullName
}

case class PrototypeH(
  fullName: FullNameH,
  // TODO: Perhaps get rid of this, since we can get it from the full name.
  params: List[ReferenceH[ReferendH]],
  returnType: ReferenceH[ReferendH]
)

case class FullNameH(parts: List[IVonData]) {
  def addStep(s: String) = {
    FullNameH(parts :+ VonStr(s))
  }

  def toVonArray(): VonArray = {
    VonArray(None, parts.toVector)
  }

  override def toString: String = {
    val nameMap =
      Map(
        "Str" -> "s",
        "Int" -> "i",
        "Float" -> "f",
        "Void" -> "v",
        "Bool" -> "b",
        "Function" -> "F",
        "Ref" -> "R",
        "Share" -> "*",
        "Borrow" -> "&",
        "Own" -> "^",
        "CoordTemplata" -> "TR",
        "KindTemplata" -> "TK",
        "CitizenName" -> "C",
        "Immutable" -> "imm",
        "MutabilityTemplata" -> "TM",
        "StructId" -> "SId",
        "InterfaceId" -> "IId",
        "AnonymousSubstructName" -> "AS",
        "LambdaCitizenName" -> "LC",
      )
    val printer = new VonPrinter(VonSyntax(false, true, false, false), Int.MaxValue, nameMap, false);
    parts.map(printer.print).mkString(":")
  }
}

case class CodeLocationH(
  file: String,
  line: Int,
  char: Int)
