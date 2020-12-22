package net.verdagon.vale.metal

import net.verdagon.vale.{vassert, vassertSome, vcurious, vfail, vimpl}
import net.verdagon.von.{IVonData, JsonSyntax, VonArray, VonMember, VonObject, VonPrinter, VonStr, VonSyntax}

import scala.collection.immutable.ListMap

object ProgramH {
  val emptyTupleStructRef =
    // If the templar ever decides to change this things name, update this to match templar's.
    StructRefH(FullNameH("Tup0", 0, List(VonObject("Tup",None,Vector(VonMember("members",VonArray(None,Vector())))))))

  def emptyTupleStructType = ReferenceH(ShareH, InlineH, emptyTupleStructRef)
}

case class ProgramH(
    // All the interfaces in the program.
    interfaces: List[InterfaceDefinitionH],
    // All the structs in the program.
    structs: List[StructDefinitionH],
    // All the externs that we're calling into from the program.
    externs: List[PrototypeH],
    // All of the user defined functions (and some from the compiler itself).
    functions: List[FunctionH],
    knownSizeArrays: List[KnownSizeArrayTH],
    unknownSizeArrays: List[UnknownSizeArrayTH],
    // Used for native compilation only, not JVM/CLR/JS/iOS.
    // These are pointing into the specific functions (in the `functions` field)
    // which should be called when we drop a reference to an immutable object.
    immDestructorsByKind: Map[ReferendH, PrototypeH],
    // Translations for backends to use if they need to export a name.
    exportedNameByFullName: Map[FullNameH, String]) {

  // These are convenience functions for the tests to look up various functions.
  def externFunctions = functions.filter(_.isExtern)
  def abstractFunctions = functions.filter(_.isAbstract)
  // Functions that are neither extern nor abstract
  def getAllUserImplementedFunctions = functions.filter(f => f.isUserFunction && !f.isExtern && !f.isAbstract)
  // Abstract or implemented
  def nonExternFunctions = functions.filter(!_.isExtern)
  def getAllUserFunctions = functions.filter(_.isUserFunction)

  // Convenience function for the tests to look up a function.
  // Function must be at the top level of the program.
  def lookupFunction(readableName: String) = {
    val matches =
      exportedNameByFullName.filter(_._2 == readableName).keys ++
        functions.filter(_.prototype.fullName.readableName == readableName).map(_.prototype.fullName)
    vassert(matches.size <= 1)
    vassert(matches.size >= 1)
    functions.find(_.prototype.fullName == matches.head).get
  }

  // Convenience function for the tests to look up a struct.
  // Struct must be at the top level of the program.
  def lookupStruct(humanName: String) = {
    val matches = structs.filter(_.fullName.readableName == humanName)
    vassert(matches.size == 1)
    matches.head
  }

  // Convenience function for the tests to look up an interface.
  // Interface must be at the top level of the program.
  def lookupInterface(humanName: String) = {
    val matches = interfaces.filter(_.fullName.readableName == humanName)
    vassert(matches.size == 1)
    matches.head
  }
}

// The struct definition, which defines a struct's name, members, and so on.
// There is only one of these per type of struct in the program.
case class StructDefinitionH(
    // Name of the struct. Guaranteed to be unique in the entire program.
    fullName: FullNameH,
    // Whether we should export this for use by clients.
    export: Boolean,
    // Whether we can take weak references to this object.
    // On native, this means an extra "weak ref count" will be included for the object.
    // On JVM/CLR/JS, this means the object will have an extra tiny object pointing
    // back at itself.
    // On iOS, this can be ignored, all objects are weakable already.
    weakable: Boolean,
    // Whether this struct is deeply immutable or not.
    // This affects how the struct is deallocated.
    // On native, this means that we potentially call the destructor any time we let go
    // of a reference.
    // On JVM/CLR/JS/iOS, this can be ignored.
    mutability: Mutability,
    // All of the `impl`s, in other words, all of the vtables for this struct for all
    // the interfaces it implements.
    edges: List[EdgeH],
    // The members of the struct, in order.
    members: List[StructMemberH]) {

  def getRef: StructRefH = StructRefH(fullName)
}

// A member of a struct.
case class StructMemberH(
  // Name of the struct member. This is *not* guaranteed to be unique in the entire
  // program.
  name: FullNameH,
  // Whether this field can be changed or not.
  // This isn't wired up to anything, feel free to ignore it.
  variability: Variability,
  // The type of the member.
  tyype: ReferenceH[ReferendH])

// An interface definition containing name, methods, etc.
case class InterfaceDefinitionH(
  fullName: FullNameH,
  // Whether we should export this for use by clients.
  export: Boolean,
  // Whether we can take weak references to this interface.
  // On native, this means an extra "weak ref count" will be included for the object.
  // On JVM/CLR/JS, this means the object should extend the IWeakable interface,
  // and expose a tiny object pointing back at itself.
  // On iOS, this can be ignored, all objects are weakable already.
  weakable: Boolean,
  // Whether this interface is deeply immutable or not.
  // On native, this affects how we free the object.
  // This can be ignored on JVM/CLR/JS/iOS.
  mutability: Mutability,
  // The interfaces that this interface extends.
  // This isnt hooked up to anything, and can be safely ignored.
  // TODO: Change this to edges, since interfaces impl other interfaces.
  superInterfaces: List[InterfaceRefH],
  // All the methods that we can call on this interface.
  methods: List[InterfaceMethodH]) {

  def getRef = InterfaceRefH(fullName)
}

// A method in an interface.
case class InterfaceMethodH(
  // The name, params, and return type of the method.
  prototypeH: PrototypeH,
  // Describes which param is the one that will have the vtable.
  // Currently this is always assumed to be zero.
  virtualParamIndex: Int) {
  vassert(virtualParamIndex >= 0)
}

// Represents how a struct implements an interface.
// Each edge has a vtable.
case class EdgeH(
  // The struct whose actual functions will be called.
  struct: StructRefH,
  // The interface that this struct is conforming to.
  interface: InterfaceRefH,
  // Map whose key is an interface method, and whose value is the method of the struct
  // that it's overriding.
  structPrototypesByInterfaceMethod: ListMap[InterfaceMethodH, PrototypeH])

sealed trait IFunctionAttributeH
case object UserFunctionH extends IFunctionAttributeH // Whether it was written by a human. Mostly for tests right now.

// A function's definition.
case class FunctionH(
  // Describes the function's name, params, and return type.
  prototype: PrototypeH,

  // Whether we should export this for use by clients.
  export: Boolean,
  // Whether this has a body. If true, the body will simply contain an InterfaceCallH instruction.
  isAbstract: Boolean,
  // Whether this has a body. If true, the body will simply contain an ExternCallH instruction to the same
  // prototype describing this function.
  isExtern: Boolean,

  attributes: List[IFunctionAttributeH],

  // The body of the function that contains the actual instructions.
  body: ExpressionH[ReferendH]) {

  def fullName = prototype.fullName
  def isUserFunction = attributes.contains(UserFunctionH)
}

// A wrapper around a function's name, which also has its params and return type.
case class PrototypeH(
  fullName: FullNameH,
  params: List[ReferenceH[ReferendH]],
  returnType: ReferenceH[ReferendH]
)

// A unique name for something in the program.
case class FullNameH(
    readableName: String,
    // -1 means extern and we wont suffix the readableName with the ID.
    id: Int,
    parts: List[IVonData]) {
  def toReadableString(): String = {
    readableName + (if (id >= 0) "_" + id else "")
  }
  def toFullString(): String = { FullNameH.namePartsToString(parts) }
}

object FullNameH {
  def namePartsToString(parts: List[IVonData]) = {
    parts.map(MetalPrinter.print).mkString(":")
  }
}
