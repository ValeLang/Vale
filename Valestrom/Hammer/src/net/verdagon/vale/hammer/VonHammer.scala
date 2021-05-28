package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.NameHammer.translateFileCoordinate
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
    val ProgramH(interfaces, structs, externs, functions, knownSizeArrays, unknownSizeArrays, immDestructorsByKind, moduleNameToExportedNameToExportee, moduleNameToExternedNameToExtern, regions) = program

    val fullNameToExportedNames =
      moduleNameToExportedNameToExportee.flatMap({ case (moduleName, exportedNameToExportee) =>
        exportedNameToExportee.map({ case (exportedName, (packageCoord, fullName)) =>
          (moduleName, exportedName, packageCoord, fullName)
        })
      }).groupBy(_._4).mapValues(_.map(_._2).toList)

    VonObject(
      "Program",
      None,
      Vector(
        VonMember("interfaces", VonArray(None, interfaces.map(vonifyInterface).toVector)),
        VonMember("structs", VonArray(None, structs.map(vonfiyStruct).toVector)),
        VonMember("externs", VonArray(None, externs.map(vonifyPrototype).toVector)),
        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector)),
        VonMember("knownSizeArrays", VonArray(None, knownSizeArrays.map(vonifyKnownSizeArrayDefinition).toVector)),
        VonMember("unknownSizeArrays", VonArray(None, unknownSizeArrays.map(vonifyUnknownSizeArrayDefinition).toVector)),
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
          "moduleNameToExportedNameToExportee",
          VonArray(
            None,
            moduleNameToExportedNameToExportee.toVector.map({ case (moduleName, exportedNameToExportee) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("moduleName", VonStr(moduleName)),
                  VonMember(
                    "exportedNameToExportee",
                    VonArray(
                      None,
                      exportedNameToExportee.toVector.map({ case (exportedName, (packageCoord, fullName)) =>
                        VonObject(
                          "Entry",
                          None,
                          Vector(
                            VonMember("exportedName", VonStr(exportedName)),
                            VonMember("module", VonStr(moduleName)),
                            VonMember(
                              "packageSteps",
                              VonArray(
                                None,
                                packageCoord.packages.map(VonStr).toVector)),
                            VonMember("fullName", VonStr(fullName.toReadableString))))
                      })))))
            }))),
        VonMember(
          "fullNameToExportedNames",
          VonArray(
            None,
            fullNameToExportedNames.toVector.map({ case (fullName, exportedNames) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("fullName", VonStr(fullName.toReadableString)),
                  VonMember(
                    "exportedNames",
                    VonArray(
                      None,
                      exportedNames.map(VonStr).toVector))))
            }))),
        VonMember(
          "moduleNameToExternedNameToExtern",
          VonArray(
            None,
            moduleNameToExternedNameToExtern.toVector.map({ case (moduleName, externedNameToExtern) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("moduleName", VonStr(moduleName)),
                  VonMember(
                    "externedNameToExtern",
                    VonArray(
                      None,
                      externedNameToExtern.toVector.map({ case (externedName, (packageCoord, fullName)) =>
                        VonObject(
                          "Entry",
                          None,
                          Vector(
                            VonMember("externedName", VonStr(externedName)),
                            VonMember("module", VonStr(moduleName)),
                            VonMember(
                              "packageSteps",
                              VonArray(
                                None,
                                packageCoord.packages.map(VonStr).toVector)),
                            VonMember("fullName", VonStr(fullName.toReadableString))))
                      })))))
            }))),
        VonMember(
          "regions",
          VonArray(
            None,
            regions.map(vonifyRegion).toVector))))
  }

  def vonifyRegion(region: RegionH): IVonData = {
    val RegionH(name, referends) = region

    VonObject(
      "Region",
      None,
      Vector(
        VonMember(
          "referends",
          VonArray(
            None,
            referends.map(vonifyKind).toVector))))
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

  def vonifyPermission(permission: m.PermissionH): IVonData = {
    permission match {
      case m.ReadonlyH => VonObject("Readonly", None, Vector())
      case m.ReadwriteH => VonObject("Readwrite", None, Vector())
//      case m.ExclusiveReadwriteH => VonObject("ExclusiveReadwrite", None, Vector())
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
    val ReferenceH(ownership, location, permission, referend) = coord

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
        VonMember("permission", vonifyPermission(permission)),
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
        VonMember("fullName", VonStr(name.toReadableString())),
        VonMember("name", VonStr(name.readableName)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyCoord(tyype))))
  }

  def vonifyUnknownSizeArrayDefinition(usaDef: UnknownSizeArrayDefinitionTH): IVonData = {
    val UnknownSizeArrayDefinitionTH(name, rawArray) = usaDef
    VonObject(
      "UnknownSizeArrayDefinition",
      None,
      Vector(
        VonMember("name", VonStr(name.toReadableString())),
        VonMember("referend", vonifyKind(usaDef.referend)),
        VonMember("array", vonifyRawArray(rawArray))))
  }

  def vonifyKnownSizeArrayDefinition(ksaDef: KnownSizeArrayDefinitionTH): IVonData = {
    val KnownSizeArrayDefinitionTH(name, size, rawArray) = ksaDef
    VonObject(
      "KnownSizeArrayDefinition",
      None,
      Vector(
        VonMember("name", VonStr(name.toReadableString())),
        VonMember("referend", vonifyKind(ksaDef.referend)),
        VonMember("size", VonInt(size)),
        VonMember("array", vonifyRawArray(rawArray))))
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
      case UnknownSizeArrayTH(name) => {
        VonObject(
          "UnknownSizeArray",
          None,
          Vector(
            VonMember("name", VonStr(name.toReadableString()))))
      }
      case KnownSizeArrayTH(name) => {
        VonObject(
          "KnownSizeArray",
          None,
          Vector(
            VonMember("name", VonStr(name.toReadableString()))))
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
        VonMember("block", vonifyExpression(body))))
  }

  def vonifyExpression(node: ExpressionH[ReferendH]): IVonData = {
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
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKnownLive", VonBool(false))))
      }
      case wa @ WeakAliasH(sourceExpr) => {
        VonObject(
          "WeakAlias",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceReferend", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(wa.resultType)),
            VonMember("resultReferend", vonifyKind(wa.resultType.kind))))
      }
      case AsSubtypeH(sourceExpr, targetType, resultResultType, okConstructor, errConstructor) => {
        VonObject(
          "AsSubtype",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("targetReferend", vonifyKind(targetType)),
            VonMember("okConstructor", vonifyPrototype(okConstructor)),
            VonMember("okType", vonifyCoord(okConstructor.returnType)),
            VonMember("okReferend", vonifyKind(okConstructor.returnType.kind)),
            VonMember("errConstructor", vonifyPrototype(errConstructor)),
            VonMember("errType", vonifyCoord(errConstructor.returnType)),
            VonMember("errReferend", vonifyKind(errConstructor.returnType.kind)),
            VonMember("resultResultType", vonifyCoord(resultResultType)),
            VonMember("resultResultReferend", vonifyKind(resultResultType.kind))))
      }
      case LockWeakH(sourceExpr, resultOptType, someConstructor, noneConstructor) => {
        VonObject(
          "LockWeak",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKnownLive", VonBool(false)),
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
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType))))
      }
      case DiscardH(sourceExpr) => {
        VonObject(
          "Discard",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
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
            VonMember("sourceExprs", VonArray(None, sourceExprs.map(vonifyExpression).toVector)),
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
              VonArray(None, sourceExprs.map(vonifyExpression).toVector)),
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
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("local", vonifyLocal(local)),
            VonMember("knownLive", VonBool(false)),
            VonMember("optName", vonifyOptional[FullNameH](name, n => VonStr(n.toReadableString())))))
      }
      case UnstackifyH(local) => {
        VonObject(
          "Unstackify",
          None,
          Vector(
            VonMember("local", vonifyLocal(local))))
      }
      case NarrowPermissionH(refExpression, targetPermission) => {
        VonObject(
          "NarrowPermission",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(refExpression)),
            VonMember("targetPermission", vonifyPermission(targetPermission))))
      }
      case CheckRefCountH(refExpr, category, numExpr) => {
        VonObject(
          "CheckRefCount",
          None,
          Vector(
            VonMember("refExpr", vonifyExpression(refExpr)),
            VonMember(
              "category",
              vonifyRefCountCategory(category)),
            VonMember("numExpr", vonifyExpression(numExpr))))
      }
      case DestroyKnownSizeArrayIntoFunctionH(arrayExpr, consumerExpr, consumerMethod, arrayElementType, arraySize) => {
        VonObject(
          "DestroyKnownSizeArrayIntoFunction",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyExpression(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerMethod", vonifyPrototype(consumerMethod)),
            VonMember("consumerKnownLive", VonBool(false)),
            VonMember("arrayElementType", vonifyCoord(arrayElementType)),
            VonMember("arraySize", VonInt(arraySize))))
      }
      case DestroyKnownSizeArrayIntoLocalsH(structExpr, localTypes, localIndices) => {
        VonObject(
          "DestroyKnownSizeArrayIntoLocals",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(structExpr)),
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
            VonMember("structExpr", vonifyExpression(structExpr)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember(
              "localTypes",
              VonArray(None, localTypes.map(localType => vonifyCoord(localType)).toVector)),
            VonMember(
              "localIndices",
              VonArray(None, locals.map(local => vonifyLocal(local)))),
            VonMember(
              "localsKnownLives",
              VonArray(None, locals.map(local => VonBool(false))))))
      }
      case DestroyUnknownSizeArrayH(arrayExpr, consumerExpr, consumerMethod, arrayElementType) => {
        VonObject(
          "DestroyUnknownSizeArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyExpression(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerReferend", vonifyKind(consumerExpr.resultType.kind)),
            VonMember("consumerMethod", vonifyPrototype(consumerMethod)),
            VonMember("arrayElementType", vonifyCoord(arrayElementType)),
            VonMember("consumerKnownLive", VonBool(false))))
      }
      case si @ StructToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        VonObject(
          "StructToInterfaceUpcast",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
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
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("localName", VonStr(localName.toReadableString())),
            VonMember("knownLive", VonBool(false))))
      }
      case LocalLoadH(local, targetOwnership, targetPermission, localName) => {
        VonObject(
          "LocalLoad",
          None,
          Vector(
            VonMember("local", vonifyLocal(local)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("targetPermission", vonifyPermission(targetPermission)),
            VonMember("localName", VonStr(localName.toReadableString()))))
      }
      case MemberStoreH(resultType, structExpr, memberIndex, sourceExpr, memberName) => {
        VonObject(
          "MemberStore",
          None,
          Vector(
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("structExpr", vonifyExpression(structExpr)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember("structKnownLive", VonBool(false)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("memberName", VonStr(memberName.toReadableString()))))
      }
      case ml @ MemberLoadH(structExpr, memberIndex, expectedMemberType, resultType, memberName) => {
        VonObject(
          "MemberLoad",
          None,
          Vector(
            VonMember("structExpr", vonifyExpression(structExpr)),
            VonMember("structId", vonifyStructRef(structExpr.resultType.kind)),
            VonMember("structType", vonifyCoord(structExpr.resultType)),
            VonMember("structKnownLive", VonBool(false)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("targetOwnership", vonifyOwnership(resultType.ownership)),
            VonMember("targetPermission", vonifyPermission(resultType.permission)),
            VonMember("expectedMemberType", vonifyCoord(expectedMemberType)),
            VonMember("expectedResultType", vonifyCoord(resultType)),
            VonMember("memberName", VonStr(memberName.toReadableString()))))
      }
      case KnownSizeArrayStoreH(arrayExpr, indexExpr, sourceExpr, resultType) => {
        VonObject(
          "KnownSizeArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case UnknownSizeArrayStoreH(arrayExpr, indexExpr, sourceExpr, resultType) => {
        VonObject(
          "UnknownSizeArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexReferend", vonifyKind(indexExpr.resultType.kind)),
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceReferend", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case usal @ UnknownSizeArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, resultType) => {
        VonObject(
          "UnknownSizeArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexReferend", vonifyKind(indexExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(usal.resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("targetPermission", vonifyPermission(targetPermission)),
            VonMember("expectedElementType", vonifyCoord(expectedElementType)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case ConstructUnknownSizeArrayH(sizeExpr, generatorExpr, generatorMethod, elementType, resultType) => {
        VonObject(
          "ConstructUnknownSizeArray",
          None,
          Vector(
            VonMember("sizeExpr", vonifyExpression(sizeExpr)),
            VonMember("sizeType", vonifyCoord(sizeExpr.resultType)),
            VonMember("sizeReferend", vonifyKind(sizeExpr.resultType.kind)),
            VonMember("generatorExpr", vonifyExpression(generatorExpr)),
            VonMember("generatorType", vonifyCoord(generatorExpr.resultType)),
            VonMember("generatorReferend", vonifyKind(generatorExpr.resultType.kind)),
            VonMember("generatorMethod", vonifyPrototype(generatorMethod)),
            VonMember("generatorKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("elementType", vonifyCoord(resultType))))
      }
      case StaticArrayFromCallableH(generatorExpr, generatorMethod, elementType, resultType) => {
        VonObject(
          "StaticArrayFromCallable",
          None,
          Vector(
            VonMember("generatorExpr", vonifyExpression(generatorExpr)),
            VonMember("generatorType", vonifyCoord(generatorExpr.resultType)),
            VonMember("generatorReferend", vonifyKind(generatorExpr.resultType.kind)),
            VonMember("generatorMethod", vonifyPrototype(generatorMethod)),
            VonMember("generatorKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("elementType", vonifyCoord(resultType))))
      }
      case ksal @ KnownSizeArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, arraySize, resultType) => {
        VonObject(
          "KnownSizeArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayReferend", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("resultType", vonifyCoord(ksal.resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("targetPermission", vonifyPermission(targetPermission)),
            VonMember("expectedElementType", vonifyCoord(expectedElementType)),
            VonMember("arraySize", VonInt(arraySize)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case ExternCallH(functionExpr, argsExprs) => {
        VonObject(
          "ExternCall",
          None,
          Vector(
            VonMember("function", vonifyPrototype(functionExpr)),
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyExpression))),
            VonMember("argTypes", VonArray(None, argsExprs.toVector.map(_.resultType).map(vonifyCoord)))))
      }
      case CallH(functionExpr, argsExprs) => {
        VonObject(
          "Call",
          None,
          Vector(
            VonMember("function", vonifyPrototype(functionExpr)),
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyExpression)))))
      }
      case InterfaceCallH(argsExprs, virtualParamIndex, interfaceRefH, indexInEdge, functionType) => {
        VonObject(
          "InterfaceCall",
          None,
          Vector(
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyExpression))),
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
            VonMember("conditionBlock", vonifyExpression(conditionBlock)),
            VonMember("thenBlock", vonifyExpression(thenBlock)),
            VonMember("thenResultType", vonifyCoord(thenBlock.resultType)),
            VonMember("elseBlock", vonifyExpression(elseBlock)),
            VonMember("elseResultType", vonifyCoord(elseBlock.resultType)),
            VonMember("commonSupertype", vonifyCoord(commonSupertype))))
      }
      case WhileH(bodyBlock) => {
        VonObject(
          "While",
          None,
          Vector(
            VonMember("bodyBlock", vonifyExpression(bodyBlock))))
      }
      case ConsecutorH(nodes) => {
        VonObject(
          "Consecutor",
          None,
          Vector(
            VonMember("exprs", VonArray(None, nodes.map(node => vonifyExpression(node)).toVector))))
      }
      case BlockH(inner) => {
        VonObject(
          "Block",
          None,
          Vector(
            VonMember("innerExpr", vonifyExpression(inner)),
            VonMember("innerType", vonifyCoord(inner.resultType))))
      }
      case IsSameInstanceH(left, right) => {
        VonObject(
          "Is",
          None,
          Vector(
            VonMember("leftExpr", vonifyExpression(left)),
            VonMember("leftExprType", vonifyCoord(left.resultType)),
            VonMember("rightExpr", vonifyExpression(right)),
            VonMember("rightExprType", vonifyCoord(right.resultType))))
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
    val Local(id, variability, tyype, keepAlive) = local

    VonObject(
      "Local",
      None,
      Vector(
        VonMember("id", vonifyVariableId(id)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyCoord(tyype)),
        VonMember("keepAlive", VonBool(keepAlive))))
  }

  def vonifyVariableId(id: VariableIdH): IVonData = {
    val VariableIdH(number, height, maybeName) = id

    VonObject(
      "VariableId",
      None,
      Vector(
        VonMember("number", VonInt(number)),
        VonMember("height", VonInt(number)),
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
      case PrototypeTemplata(prototype) => {
        VonObject(
          "PrototypeTemplata",
          None,
          Vector(
            VonMember("prototype", vonifyPrototype(FunctionHammer.translatePrototype(hinputs, hamuts, prototype)))))
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
    val m.CodeLocation(file, offset) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("file", translateFileCoordinate(file)),
        VonMember("offset", VonInt(offset))))
  }

  def vonifyCodeLocation2(codeLocation: CodeLocation2): IVonData = {
    val CodeLocation2(file, offset) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("file", translateFileCoordinate(file)),
        VonMember("offset", VonInt(offset))))
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
      case GlobalPackageName2() => {
        VonObject(
          "GlobalPackageName",
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
