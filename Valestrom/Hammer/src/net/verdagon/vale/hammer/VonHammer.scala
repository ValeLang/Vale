package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{vassert, vfail, vimpl, metal => m}
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types._
import net.verdagon.von._

object VonHammer {
  def vonifyProgram(program: ProgramH): IVonData = {
    val ProgramH(interfaces, structs, externs, functions, knownSizeArrays, unknownSizeArrays, immDestructorsByKind, exportedNameByFullName) = program

    VonObject(
      "Program",
      None,
      Vector(
        VonMember("interfaces", VonArray(None, interfaces.map(vonifyInterface).toVector)),
        VonMember("structs", VonArray(None, structs.map(vonfiyStruct).toVector)),
        VonMember("externs", VonArray(None, externs.map(vonifyPrototype).toVector)),
        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector)),
        VonMember("knownSizeArrays", VonArray(None, knownSizeArrays.map(vonifyKind).toVector)),
        VonMember("unknownSizeArrays", VonArray(None, unknownSizeArrays.map(vonifyKind).toVector)),
        VonMember("emptyTupleStructReferend", vonifyKind(ProgramH.emptyTupleStructRef)),
        VonMember(
          "immDestructorsByReferend",
          VonArray(
            None,
            immDestructorsByKind.toVector.map({ case (kind, destructor) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("referend", vonifyKind(kind)),
                  VonMember("destructor", vonifyPrototype(destructor))))
            }))),
        VonMember(
          "exportedNameByFullName",
          VonArray(
            None,
            exportedNameByFullName.toVector.map({ case (fullName, exportedName) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("fullName", VonStr(fullName.toReadableString)),
                  VonMember("exportedName", VonStr(exportedName))))
            })))))
  }

  def vonifyStructRef(ref: StructRefH): IVonData = {
    val StructRefH(fullName) = ref

    VonObject(
      "StructId",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toReadableString()))))
  }

  def vonifyInterfaceRef(ref: InterfaceRefH): IVonData = {
    val InterfaceRefH(fullName) = ref

    VonObject(
      "InterfaceId",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toReadableString()))))
  }

  def vonifyInterfaceMethod(interfaceMethodH: InterfaceMethodH): IVonData = {
    val InterfaceMethodH(prototype, virtualParamIndex) = interfaceMethodH

    VonObject(
      "InterfaceMethod",
      None,
      Vector(
        VonMember("prototype", vonifyPrototype(prototype)),
        VonMember("virtualParamIndex", VonInt(virtualParamIndex))))
  }

  def vonifyInterface(interface: InterfaceDefinitionH): IVonData = {
    val InterfaceDefinitionH(fullName, export, weakable, mutability, superInterfaces, prototypes) = interface

    VonObject(
      "Interface",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toReadableString())),
        VonMember("referend", vonifyInterfaceRef(interface.getRef)),
        VonMember("export", VonBool(export)),
        VonMember("weakable", VonBool(weakable)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("superInterfaces", VonArray(None, superInterfaces.map(vonifyInterfaceRef).toVector)),
        VonMember("methods", VonArray(None, prototypes.map(vonifyInterfaceMethod).toVector))))
  }

  def vonfiyStruct(struct: StructDefinitionH): IVonData = {
    val StructDefinitionH(fullName, export, weakable, mutability, edges, members) = struct

    VonObject(
      "Struct",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toReadableString())),
        VonMember("referend", vonifyStructRef(struct.getRef)),
        VonMember("weakable", VonBool(weakable)),
        VonMember("export", VonBool(export)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("edges", VonArray(None, edges.map(edge => vonifyEdge(edge)).toVector)),
        VonMember("members", VonArray(None, members.map(vonifyStructMember).toVector))))
  }

  def vonifyMutability(mutability: m.Mutability): IVonData = {
    mutability match {
      case m.Immutable => VonObject("Immutable", None, Vector())
      case m.Mutable => VonObject("Mutable", None, Vector())
    }
  }

  def vonifyPermission(permission: m.Permission): IVonData = {
    permission match {
      case m.Readonly => VonObject("Readonly", None, Vector())
      case m.Readwrite => VonObject("Readwrite", None, Vector())
      case m.ExclusiveReadwrite => VonObject("ExclusiveReadwrite", None, Vector())
    }
  }

  def vonifyLocation(location: m.LocationH): IVonData = {
    location match {
      case m.InlineH => VonObject("Inline", None, Vector())
      case m.YonderH => VonObject("Yonder", None, Vector())
    }
  }

  def vonifyVariability(variability: m.Variability): IVonData = {
    variability match {
      case m.Varying => VonObject("Varying", None, Vector())
      case m.Final => VonObject("Final", None, Vector())
    }
  }

  def vonifyPrototype(prototype: PrototypeH): IVonData = {
    val PrototypeH(fullName, params, returnType) = prototype

    VonObject(
      "Prototype",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toReadableString())),
        VonMember("params", VonArray(None, params.map(vonifyCoord).toVector)),
        VonMember("return", vonifyCoord(returnType))))
  }

  def vonifyCoord(coord: ReferenceH[ReferendH]): IVonData = {
    val ReferenceH(ownership, location, referend) = coord

//    val vonDataWithoutDebugStr =
//      VonObject(
//        "Ref",
//        None,
//        Vector(
//          VonMember("ownership", vonifyOwnership(ownership)),
//          VonMember("location", vonifyLocation(location)),
//          VonMember("referend", vonifyKind(referend))))
    VonObject(
      "Ref",
      None,
      Vector(
        VonMember("ownership", vonifyOwnership(ownership)),
        VonMember("location", vonifyLocation(location)),
        VonMember("referend", vonifyKind(referend))))
//        VonMember(
//          "debugStr",
//          VonStr(
//            MetalPrinter.print(vonDataWithoutDebugStr)
//              // Because all the quotes and backslashes are annoying when debugging
//              .replace('"'.toString, "")
//              .replace("\\", "")))))
  }

  def vonifyEdge(edgeH: EdgeH): IVonData = {
    val EdgeH(struct, interface, structPrototypesByInterfacePrototype) = edgeH

    VonObject(
      "Edge",
      None,
      Vector(
        VonMember("structName", vonifyStructRef(struct)),
        VonMember("interfaceName", vonifyInterfaceRef(interface)),
        VonMember(
          "methods",
          VonArray(
            None,
            structPrototypesByInterfacePrototype.toVector.map({ case (interfaceMethod, structPrototype) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("method", vonifyInterfaceMethod(interfaceMethod)),
                  VonMember("override", vonifyPrototype(structPrototype))))
            })))))
  }

  def vonifyOwnership(ownership: m.OwnershipH): IVonData = {
    ownership match {
      case m.OwnH => VonObject("Own", None, Vector())
      case m.BorrowH => VonObject("Borrow", None, Vector())
      case m.ShareH => VonObject("Share", None, Vector())
      case m.WeakH => VonObject("Weak", None, Vector())
    }
  }

  def vonifyRefCountCategory(category: m.RefCountCategory): IVonData = {
    category match {
      case m.VariableRefCount => VonObject("VariableRefCount", None, Vector())
      case m.MemberRefCount => VonObject("MemberRefCount", None, Vector())
      case m.ArgumentRefCount => VonObject("ArgumentRefCount", None, Vector())
      case m.RegisterRefCount => VonObject("RegisterRefCount", None, Vector())
    }
  }

  def vonifyStructMember(structMemberH: StructMemberH): IVonData = {
    val StructMemberH(name, variability, tyype) = structMemberH

    VonObject(
      "StructMember",
      None,
      Vector(
        VonMember("name", VonStr(name.toReadableString())),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyCoord(tyype))))
  }

  def vonifyKind(referend: ReferendH): IVonData = {
    referend match {
      case NeverH() => VonObject("Never", None, Vector())
      case IntH() => VonObject("Int", None, Vector())
      case BoolH() => VonObject("Bool", None, Vector())
      case StrH() => VonObject("Str", None, Vector())
      case FloatH() => VonObject("Float", None, Vector())
      case ir @ InterfaceRefH(_) => vonifyInterfaceRef(ir)
      case sr @ StructRefH(_) => vonifyStructRef(sr)
      case UnknownSizeArrayTH(name, rawArray) => {
        VonObject(
          "UnknownSizeArray",
          None,
          Vector(
            VonMember("name", VonStr(name.toReadableString())),
            VonMember("array", vonifyRawArray(rawArray))))
      }
      case KnownSizeArrayTH(name, size, rawArray) => {
        VonObject(
          "KnownSizeArray",
          None,
          Vector(
            VonMember("name", VonStr(name.toReadableString())),
            VonMember("size", VonInt(size)),
            VonMember("array", vonifyRawArray(rawArray))))
      }
    }
  }

  def vonifyRawArray(t: RawArrayTH): IVonData = {
    val RawArrayTH(mutability, elementType) = t

    VonObject(
      "Array",
      None,
      Vector(
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("elementType", vonifyCoord(elementType))))
  }

  def vonifyFunction(functionH: FunctionH): IVonData = {
    val FunctionH(prototype, export, _, _, _, body) = functionH

    VonObject(
      "Function",
      None,
      Vector(
        VonMember("prototype", vonifyPrototype(prototype)),
        VonMember("export", VonBool(export)),
        // TODO: rename block to body
        VonMember("block", vonifyNode(body))))
  }

  def vonifyNode(node: ExpressionH[ReferendH]): IVonData = {
    node match {
      case ConstantBoolH(value) => {
        VonObject(
          "ConstantBool",
          None,
          Vector(
            VonMember("value", VonBool(value))))
      }
      case ConstantI64H(value) => {
        VonObject(
          "ConstantI64",
          None,
          Vector(
            VonMember("value", VonInt(value))))
      }
      case ConstantStrH(value) => {
        VonObject(
          "ConstantStr",
          None,
          Vector(
            VonMember("value", VonStr(value))))
      }
      case ConstantF64H(value) => {
        VonObject(
          "ConstantF64",
          None,
          Vector(
            VonMember("value", VonFloat(value))))
      }
      case ArrayLengthH(sourceExpr) => {
        VonObject(
          "ArrayLength",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType))))
      }
      case wa @ WeakAliasH(sourceExpr) => {
        VonObject(
          "WeakAlias",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceReferend", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(wa.resultType)),
            VonMember("resultReferend", vonifyKind(wa.resultType.kind))))
      }
      case LockWeakH(sourceExpr, resultOptType, someConstructor, noneConstructor) => {
        VonObject(
          "LockWeak",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("someConstructor", vonifyPrototype(someConstructor)),
            VonMember("someType", vonifyCoord(someConstructor.returnType)),
            VonMember("someReferend", vonifyKind(someConstructor.returnType.kind)),
            VonMember("noneConstructor", vonifyPrototype(noneConstructor)),
            VonMember("noneType", vonifyCoord(noneConstructor.returnType)),
            VonMember("noneReferend", vonifyKind(noneConstructor.returnType.kind)),
            VonMember("resultOptType", vonifyCoord(resultOptType)),
            VonMember("resultOptReferend", vonifyKind(resultOptType.kind))))
      }
      case ReturnH(sourceExpr) => {
        VonObject(
          "Return",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType))))
      }
      case DiscardH(sourceExpr) => {
        VonObject(
          "Discard",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceResultType", vonifyCoord(sourceExpr.resultType))))
      }
      case ArgumentH(resultReference, argumentIndex) => {
        VonObject(
          "Argument",
          None,
          Vector(
            VonMember("resultType", vonifyCoord(resultReference)),
            VonMember("argumentIndex", VonInt(argumentIndex))))
      }
      case NewArrayFromValuesH(resultType, sourceExprs) => {
        VonObject(
          "NewArrayFromValues",
          None,
          Vector(
            VonMember("sourceExprs", VonArray(None, sourceExprs.map(vonifyNode).toVector)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("resultReferend", vonifyKind(resultType.kind))))
      }
      case NewStructH(sourceExprs, targetMemberNames, resultType) => {
        VonObject(
          "NewStruct",
          None,
          Vector(
            VonMember(
              "sourceExprs",
              VonArray(None, sourceExprs.map(vonifyNode).toVector)),
            VonMember(
              "memberNames",
              VonArray(None, targetMemberNames.map(n => VonStr(n.toReadableString)).toVector)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case StackifyH(sourceExpr, local, name) => {
        VonObject(
          "Stackify",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("local", vonifyLocal(local)),
            VonMember("optName", vonifyOptional[FullNameH](name, n => VonStr(n.toReadableString())))))
      }
      case UnstackifyH(local) => {
        VonObject(
          "Unstackify",
          None,
          Vector(
            VonMember("local", vonifyLocal(local))))
      }
      case CheckRefCountH(refExpr, category, numExpr) => {
        VonObject(
          "CheckRefCount",
          None,
          Vector(
            VonMember("refExpr", vonifyNode(refExpr)),
            VonMember(
              "category",
              vonifyRefCountCategory(category)),
            VonMember("numExpr", vonifyNode(numExpr))))
      }
      case DestroyKnownSizeArrayIntoFunctionH(arrayExpr, consumerExpr, consumerMethod) => {
        VonObject(
          "DestroyKnownSizeArrayIntoFunction",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyNode(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerMethod", vonifyPrototype(consumerMethod))))
      }
      case DestroyKnownSizeArrayIntoLocalsH(structExpr, localTypes, localIndices) => {
        VonObject(
          "DestroyKnownSizeArrayIntoLocals",
          None,
          Vector(
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember(
              "localTypes",
              VonArray(None, localTypes.map(localType => vonifyCoord(localType)).toVector)),
            VonMember(
              "localIndices",
              VonArray(None, localIndices.map(local => vonifyLocal(local))))))
      }
      case DestroyH(structExpr, localTypes, locals) => {
        VonObject(
          "Destroy",
          None,
          Vector(
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember(
              "localTypes",
              VonArray(None, localTypes.map(localType => vonifyCoord(localType)).toVector)),
            VonMember(
              "localIndices",
              VonArray(None, locals.map(local => vonifyLocal(local))))))
      }
      case DestroyUnknownSizeArrayH(arrayExpr, consumerExpr, consumerMethod) => {
        VonObject(
          "DestroyUnknownSizeArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyNode(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerReferend", vonifyKind(consumerExpr.resultType.kind)),
            VonMember("consumerMethod", vonifyPrototype(consumerMethod))))
      }
      case si @ StructToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        VonObject(
          "StructToInterfaceUpcast",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceStructType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceStructReferend", vonifyStructRef(sourceExpr.resultType.kind)),
            VonMember("targetInterfaceType", vonifyCoord(si.resultType)),
            VonMember("targetInterfaceReferend", vonifyInterfaceRef(targetInterfaceRef))))
      }
      case InterfaceToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        vimpl()
      }
      case LocalStoreH(local, sourceExpr,localName) => {
        VonObject(
          "LocalStore",
          None,
          Vector(
            VonMember("local", vonifyLocal(local)),
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("localName", VonStr(localName.toReadableString()))))
      }
      case LocalLoadH(local, targetOwnership, localName) => {
        VonObject(
          "LocalLoad",
          None,
          Vector(
            VonMember("local", vonifyLocal(local)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("localName", VonStr(localName.toReadableString()))))
      }
      case MemberStoreH(resultType, structExpr, memberIndex, sourceExpr, memberName) => {
        VonObject(
          "MemberStore",
          None,
          Vector(
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("memberName", VonStr(memberName.toReadableString()))))
      }
      case ml @ MemberLoadH(structExpr, memberIndex, targetOwnership, expectedMemberType, memberName) => {
        VonObject(
          "MemberLoad",
          None,
          Vector(
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember("structId", vonifyStructRef(structExpr.resultType.kind)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("expectedMemberType", vonifyCoord(expectedMemberType)),
            VonMember("expectedResultType", vonifyCoord(ml.resultType)),
            VonMember("memberName", VonStr(memberName.toReadableString()))))
      }
      case KnownSizeArrayStoreH(arrayExpr, indexExpr, sourceExpr) => {
        VonObject(
          "KnownSizeArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("sourceExpr", vonifyNode(sourceExpr))))
      }
      case UnknownSizeArrayStoreH(arrayExpr, indexExpr, sourceExpr) => {
        VonObject(
          "UnknownSizeArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexReferend", vonifyKind(indexExpr.resultType.kind)),
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceReferend", vonifyKind(sourceExpr.resultType.kind))))
      }
      case usal @ UnknownSizeArrayLoadH(arrayExpr, indexExpr, targetOwnership) => {
        VonObject(
          "UnknownSizeArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexReferend", vonifyKind(indexExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(usal.resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership))))
      }
      case ConstructUnknownSizeArrayH(sizeExpr, generatorExpr, generatorMethod, resultType) => {
        VonObject(
          "ConstructUnknownSizeArray",
          None,
          Vector(
            VonMember("sizeExpr", vonifyNode(sizeExpr)),
            VonMember("sizeType", vonifyCoord(sizeExpr.resultType)),
            VonMember("sizeReferend", vonifyKind(sizeExpr.resultType.kind)),
            VonMember("generatorExpr", vonifyNode(generatorExpr)),
            VonMember("generatorType", vonifyCoord(generatorExpr.resultType)),
            VonMember("generatorReferend", vonifyKind(generatorExpr.resultType.kind)),
            VonMember("generatorMethod", vonifyPrototype(generatorMethod)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case ksal @ KnownSizeArrayLoadH(arrayExpr, indexExpr, targetOwnership) => {
        VonObject(
          "KnownSizeArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("resultType", vonifyCoord(ksal.resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership))))
      }
      case ExternCallH(functionExpr, argsExprs) => {
        VonObject(
          "ExternCall",
          None,
          Vector(
            VonMember("function", vonifyPrototype(functionExpr)),
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyNode))),
            VonMember("argTypes", VonArray(None, argsExprs.toVector.map(_.resultType).map(vonifyCoord)))))
      }
      case CallH(functionExpr, argsExprs) => {
        VonObject(
          "Call",
          None,
          Vector(
            VonMember("function", vonifyPrototype(functionExpr)),
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyNode)))))
      }
      case InterfaceCallH(argsExprs, virtualParamIndex, interfaceRefH, indexInEdge, functionType) => {
        VonObject(
          "InterfaceCall",
          None,
          Vector(
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyNode))),
            VonMember("virtualParamIndex", VonInt(virtualParamIndex)),
            VonMember("interfaceRef", vonifyInterfaceRef(interfaceRefH)),
            VonMember("indexInEdge", VonInt(indexInEdge)),
            VonMember("functionType", vonifyPrototype(functionType))))
      }
      case IfH(conditionBlock, thenBlock, elseBlock, commonSupertype) => {
        VonObject(
          "If",
          None,
          Vector(
            VonMember("conditionBlock", vonifyNode(conditionBlock)),
            VonMember("thenBlock", vonifyNode(thenBlock)),
            VonMember("thenResultType", vonifyCoord(thenBlock.resultType)),
            VonMember("elseBlock", vonifyNode(elseBlock)),
            VonMember("elseResultType", vonifyCoord(elseBlock.resultType)),
            VonMember("commonSupertype", vonifyCoord(commonSupertype))))
      }
      case WhileH(bodyBlock) => {
        VonObject(
          "While",
          None,
          Vector(
            VonMember("bodyBlock", vonifyNode(bodyBlock))))
      }
      case ConsecutorH(nodes) => {
        VonObject(
          "Consecutor",
          None,
          Vector(
            VonMember("exprs", VonArray(None, nodes.map(node => vonifyNode(node)).toVector))))
      }
      case BlockH(inner) => {
        VonObject(
          "Block",
          None,
          Vector(
            VonMember("innerExpr", vonifyNode(inner)),
            VonMember("innerType", vonifyCoord(inner.resultType))))
      }
    }
  }

  def vonifyFunctionRef(ref: FunctionRefH): IVonData = {
    val FunctionRefH(prototype) = ref

    VonObject(
      "FunctionRef",
      None,
      Vector(
        VonMember("prototype", vonifyPrototype(prototype))))
  }

  def vonifyLocal(local: Local): IVonData = {
    val Local(id, tyype) = local

    VonObject(
      "Local",
      None,
      Vector(
        VonMember("id", vonifyVariableId(id)),
        VonMember("type", vonifyCoord(tyype))))
  }

  def vonifyVariableId(id: VariableIdH): IVonData = {
    val VariableIdH(number, maybeName) = id

    VonObject(
      "VariableId",
      None,
      Vector(
        VonMember("number", VonInt(number)),
        VonMember(
          "optName",
          vonifyOptional[FullNameH](maybeName, x => VonStr(x.toReadableString())))))
  }

  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
    opt match {
      case None => VonObject("None", None, Vector())
      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
    }
  }

  def vonifyTemplarName(hinputs: Hinputs, hamuts: HamutsBox, fullName2: FullName2[IName2]): VonStr = {
    val str = FullNameH.namePartsToString(fullName2.steps.map(step => translateName(hinputs, hamuts, step)))
    VonStr(str)
  }

  def vonifyTemplata(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    templata: ITemplata,
  ): IVonData = {
    templata match {
      case CoordTemplata(coord) => {
        VonObject(
          "CoordTemplata",
          None,
          Vector(
            VonMember("coord", vonifyCoord(TypeHammer.translateReference(hinputs, hamuts, coord)))))
      }
      case KindTemplata(kind) => {
        VonObject(
          "KindTemplata",
          None,
          Vector(
            VonMember("kind", vonifyKind(TypeHammer.translateKind(hinputs, hamuts, kind)))))
      }
      case ArrayTemplateTemplata() => VonObject("ArrayTemplateTemplata", None, Vector())
      case ft @ FunctionTemplata(env, functionA) => {
        VonObject(
          "FunctionTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember(
              "function",
              translateName(hinputs, hamuts, ft.getTemplateName()))))
      }
      case st @ StructTemplata(env, struct) => {
        VonObject(
          "StructTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember("structName", translateName(hinputs, hamuts, st.getTemplateName()))))
      }
      case it @ InterfaceTemplata(env, interface) => {
        VonObject(
          "InterfaceTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember("interfaceName", translateName(hinputs, hamuts, it.getTemplateName()))))
      }
      case it @ ImplTemplata(env, impl) => {
        VonObject(
          "ExternFunctionTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember("subCitizenHumanName", VonStr(impl.name.subCitizenHumanName)),
            VonMember("location", vonifyCodeLocation2(NameTranslator.translateCodeLocation(impl.name.codeLocation)))))
      }
      case ExternFunctionTemplata(header) => VonObject("ExternFunctionTemplata", None, Vector(VonMember("name", vonifyTemplarName(hinputs, hamuts, header.fullName))))
      case OwnershipTemplata(ownership) => VonObject("OwnershipTemplata", None, Vector(VonMember("ownership", vonifyOwnership(Conversions.evaluateOwnership(ownership)))))
      case VariabilityTemplata(variability) => VonObject("VariabilityTemplata", None, Vector(VonMember("variability", vonifyVariability(Conversions.evaluateVariability(variability)))))
      case MutabilityTemplata(mutability) => VonObject("MutabilityTemplata", None, Vector(VonMember("mutability", vonifyMutability(Conversions.evaluateMutability(mutability)))))
      case PermissionTemplata(permission) => VonObject("PermissionTemplata", None, Vector(VonMember("permission", vonifyPermission(Conversions.evaluatePermission(permission)))))
      case LocationTemplata(location) => VonObject("LocationTemplata", None, Vector(VonMember("location", vonifyLocation(Conversions.evaluateLocation(location)))))
      case BooleanTemplata(value) => VonObject("BooleanTemplata", None, Vector(VonMember("value", VonBool(value))))
      case IntegerTemplata(value) => VonObject("IntegerTemplata", None, Vector(VonMember("value", VonInt(value))))
    }
  }

  def vonifyCodeLocation(codeLocation: m.CodeLocation): IVonData = {
    val m.CodeLocation(line, char) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("line", VonInt(line)),
        VonMember("char", VonInt(char))))
  }

  def vonifyCodeLocation2(codeLocation: CodeLocation2): IVonData = {
    val CodeLocation2(line, char) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("line", VonInt(line)),
        VonMember("char", VonInt(char))))
  }

  def translateName(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    name: IName2
  ): IVonData = {
    name match {
      case ConstructingMemberName2(name) => {
        VonObject(
          "ConstructingMemberName",
          None,
          Vector(
            VonMember("name", VonStr(name))))
      }
      case ImmConcreteDestructorName2(kind) => {
        VonObject(
          "ImmConcreteDestructorName2",
          None,
          Vector(
            VonMember("kind", vonifyKind(TypeHammer.translateKind(hinputs, hamuts, kind)))))
      }
      case ImplDeclareName2(subCitizenHumanName, codeLocation) => {
        VonObject(
          "ImplDeclareName",
          None,
          Vector(
            VonMember("subCitizenHumanName", VonStr(subCitizenHumanName)),
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LetName2(codeLocation) => {
        VonObject(
          "LetName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case KnownSizeArrayName2(size, arr) => {
        VonObject(
          "KnownSizeArrayName",
          None,
          Vector(
            VonMember("size", VonInt(size)),
            VonMember("arr", translateName(hinputs, hamuts, arr))))
      }
      case UnknownSizeArrayName2(arr) => {
        VonObject(
          "UnknownSizeArrayName",
          None,
          Vector(
            VonMember("arr", translateName(hinputs, hamuts, arr))))
      }
      case RawArrayName2(mutability, elementType) => {
        VonObject(
          "RawArrayName",
          None,
          Vector(
            VonMember("mutability", vonifyMutability(Conversions.evaluateMutability(mutability))),
            VonMember("elementType", vonifyCoord(TypeHammer.translateReference(hinputs, hamuts, elementType)))))
      }
      case TemplarBlockResultVarName2(num) => {
        VonObject(
          "TemplarBlockResultVarName",
          None,
          Vector(
            VonMember("num", VonInt(num))))
      }
      case TemplarFunctionResultVarName2() => {
        VonObject(
          "TemplarFunctionResultVarName",
          None,
          Vector())
      }
      case TemplarTemporaryVarName2(num) => {
        VonObject(
          "TemplarTemporaryVarName",
          None,
          Vector(
            VonMember("num", VonInt(num))))
      }
      case TemplarPatternMemberName2(num, memberIndex) => {
        VonObject(
          "TemplarPatternMemberName",
          None,
          Vector(
            VonMember("num", VonInt(num))))
      }
      case TemplarPatternDestructureeName2(num) => {
        VonObject(
          "TemplarPatternPackName",
          None,
          Vector(
            VonMember("num", VonInt(num))))
      }
      case UnnamedLocalName2(codeLocation) => {
        VonObject(
          "UnnamedLocalName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case ClosureParamName2() => {
        VonObject(
          "ClosureParamName",
          None,
          Vector())
      }
      case MagicParamName2(codeLocation) => {
        VonObject(
          "MagicParamName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case CodeVarName2(name) => {
        VonObject(
          "CodeVarName",
          None,
          Vector(
            VonMember("name", VonStr(name))))
      }
      case AnonymousSubstructMemberName2(index) => {
        VonObject(
          "AnonymousSubstructMemberName",
          None,
          Vector())
      }
      case PrimitiveName2(humanName) => {
        VonObject(
          "PrimitiveName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName))))
      }
      case GlobalNamespaceName2() => {
        VonObject(
          "GlobalNamespaceName",
          None,
          Vector())
      }
      case ExternFunctionName2(humanName, parameters) => {
        VonObject(
          "EF",
          None,
          Vector(
            VonMember("humanName", VonStr(humanName))))
      }
      case FunctionName2(humanName, templateArgs, parameters) => {
        VonObject(
          "F",
          None,
          Vector(
            VonMember("humanName", VonStr(humanName)),
            VonMember(
              "templateArgs",
              VonArray(
                None,
                templateArgs
                  .map(templateArg => vonifyTemplata(hinputs, hamuts, templateArg))
                  .toVector)),
            VonMember(
              "parameters",
              VonArray(
                None,
                parameters
                  .map(templateArg => TypeHammer.translateReference(hinputs, hamuts, templateArg))
                  .map(vonifyCoord)
                  .toVector))))
      }
      case ImmDropName2(kind) => {
        VonObject(
          "ImmInterfaceDestructorName",
          None,
          Vector(
            VonMember(
              "kind",
              vonifyKind(TypeHammer.translateKind(hinputs, hamuts, kind)))))
      }
      case ImmInterfaceDestructorName2(templateArgs, parameters) => {
        VonObject(
          "ImmInterfaceDestructorName",
          None,
          Vector(
            VonMember(
              "templateArgs",
              VonArray(
                None,
                templateArgs
                  .map(templateArg => vonifyTemplata(hinputs, hamuts, templateArg))
                  .toVector)),
            VonMember(
              "parameters",
              VonArray(
                None,
                parameters
                  .map(templateArg => TypeHammer.translateReference(hinputs, hamuts, templateArg))
                  .map(vonifyCoord)
                  .toVector))))
      }
      case FunctionTemplateName2(humanName, codeLocation) => {
        VonObject(
          "FunctionTemplateName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName)),
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LambdaTemplateName2(codeLocation) => {
        VonObject(
          "LambdaTemplateName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case ConstructorName2(parameters) => {
        VonObject(
          "ConstructorName",
          None,
          Vector())
      }
      case CitizenName2(humanName, templateArgs) => {
        VonObject(
          "CitizenName",
          None,
          Vector(
            VonMember("humanName", VonStr(humanName)),
            VonMember(
              "templateArgs",
              VonArray(
                None,
                templateArgs
                  .map(templateArg => vonifyTemplata(hinputs, hamuts, templateArg))
                  .toVector))))
      }
      case TupleName2(members) => {
        VonObject(
          "Tup",
          None,
          Vector(
            VonMember(
              "members",
              VonArray(
                None,
                members
                  .map(coord => TypeHammer.translateReference(hinputs, hamuts, coord))
                  .map(vonifyCoord)
                  .toVector))))
      }
      case LambdaCitizenName2(codeLocation) => {
        VonObject(
          "LambdaCitizenName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case CitizenTemplateName2(humanName, codeLocation) => {
        VonObject(
          "CitizenTemplateName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName)),
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case AnonymousSubstructName2(callables) => {
        VonObject(
          "AnonymousSubstructName",
          None,
          Vector(
            VonMember(
              "callables",
              VonArray(
                None,
                callables
                  .map(coord => TypeHammer.translateReference(hinputs, hamuts, coord))
                  .map(vonifyCoord)
                  .toVector))))
      }
      case AnonymousSubstructImplName2() => {
        VonObject(
          "AnonymousSubstructImplName",
          None,
          Vector())
      }
      case CodeRune2(name) => {
        VonObject(
          "CodeRune",
          None,
          Vector(
            VonMember("name", VonStr(name))))
      }
      case ImplicitRune2(parentName, name) => {
        VonObject(
          "ImplicitRune",
          None,
          Vector(
            VonMember("parentName", translateName(hinputs, hamuts, parentName)),
            VonMember("name", VonInt(name))))
      }
      case LetImplicitRune2(codeLocation, name) => {
        VonObject(
          "LetImplicitRune",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case MemberRune2(memberIndex) => {
        VonObject(
          "MemberRune",
          None,
          Vector(
            VonMember("memberIndex", VonInt(memberIndex))))
      }
      case MagicImplicitRune2(codeLocation) => {
        VonObject(
          "MagicImplicitRune",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case ReturnRune2() => {
        VonObject(
          "ReturnRune",
          None,
          Vector())
      }
      case SolverKindRune2(paramRune) => {
        VonObject(
          "SolverKindRune",
          None,
          Vector(
            VonMember("paramRune", translateName(hinputs, hamuts, paramRune))))
      }
      case ExplicitTemplateArgRune2(index) => {
        VonObject(
          "ExplicitTemplateArgRune",
          None,
          Vector(
            VonMember("index", VonInt(index))))
      }
      case AnonymousSubstructParentInterfaceRune2() => {
        VonObject(
          "AnonymousSubstructParentInterfaceRune",
          None,
          Vector())
      }
    }
  }
}
