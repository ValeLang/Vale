package net.verdagon.vale.hammer

import net.verdagon.vale.hammer.NameHammer.{translateFileCoordinate, translateFullName}
import net.verdagon.vale.metal._
import net.verdagon.vale.{CodeLocationS, PackageCoordinate, vassert, vfail, vimpl, metal => m}
import net.verdagon.vale.scout._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.names._
import net.verdagon.vale.templar.types._
import net.verdagon.von._

object VonHammer {
  def vonifyProgram(program: ProgramH): IVonData = {
    val ProgramH(packages) = program

    VonObject(
      "Program",
      None,
      Vector(
        VonMember(
          "packages",
          VonArray(
            None,
            packages.flatMap({ case (packageCoord, paackage) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("packageCoordinate", NameHammer.translatePackageCoordinate(packageCoord)),
                  VonMember("package", vonifyPackage(packageCoord, paackage))
                ))
            }).toVector))))
  }

  def vonifyPackage(packageCoord: PackageCoordinate, paackage: PackageH): IVonData = {
    val PackageH(
      interfaces,
      structs,
      functions,
      staticSizedArrays,
      runtimeSizedArrays,
      immDestructorsByKind,
      exportNameToFunction,
      exportNameToKind,
      externNameToFunction,
      externNameToKind,
    ) = paackage

    VonObject(
      "Package",
      None,
      Vector(
        VonMember("packageCoordinate", NameHammer.translatePackageCoordinate(packageCoord)),
        VonMember("interfaces", VonArray(None, interfaces.map(vonifyInterface).toVector)),
        VonMember("structs", VonArray(None, structs.map(vonfiyStruct).toVector)),
        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector)),
        VonMember("staticSizedArrays", VonArray(None, staticSizedArrays.map(vonifyStaticSizedArrayDefinition).toVector)),
        VonMember("runtimeSizedArrays", VonArray(None, runtimeSizedArrays.map(vonifyRuntimeSizedArrayDefinition).toVector)),
        VonMember("emptyTupleStructKind", vonifyKind(ProgramH.emptyTupleStructRef)),
        VonMember(
          "immDestructorsByKind",
          VonArray(
            None,
            immDestructorsByKind.toVector.map({ case (kind, destructor) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("kind", vonifyKind(kind)),
                  VonMember("destructor", vonifyPrototype(destructor))))
            }))),
        VonMember(
          "exportNameToFunction",
          VonArray(
            None,
            exportNameToFunction.toVector.map({ case (exportName, prototype) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("exportName", VonStr(exportName)),
                  VonMember("prototype", vonifyPrototype(prototype))))
            }))),
        VonMember(
          "exportNameToKind",
          VonArray(
            None,
            exportNameToKind.toVector.map({ case (exportName, kind) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("exportName", VonStr(exportName)),
                  VonMember("kind", vonifyKind(kind))))
            }))),
        VonMember(
          "externNameToFunction",
          VonArray(
            None,
            externNameToFunction.toVector.map({ case (externName, prototype) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("externName", VonStr(externName)),
                  VonMember("prototype", vonifyPrototype(prototype))))
            }))),
        VonMember(
          "externNameToKind",
          VonArray(
            None,
            externNameToKind.toVector.map({ case (externName, kind) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("externName", VonStr(externName)),
                  VonMember("kind", vonifyKind(kind))))
            })))))
  }

  def vonifyRegion(region: RegionH): IVonData = {
    val RegionH(name, kinds) = region

    VonObject(
      "Region",
      None,
      Vector(
        VonMember(
          "kinds",
          VonArray(
            None,
            kinds.map(vonifyKind).toVector))))
  }

  def vonifyStructRef(ref: StructRefH): IVonData = {
    val StructRefH(fullName) = ref

    VonObject(
      "StructId",
      None,
      Vector(
        VonMember("name", vonifyName(fullName))))
  }

  def vonifyInterfaceRef(ref: InterfaceRefH): IVonData = {
    val InterfaceRefH(fullName) = ref

    VonObject(
      "InterfaceId",
      None,
      Vector(
        VonMember("name", vonifyName(fullName))))
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
    val InterfaceDefinitionH(fullName, weakable, mutability, superInterfaces, prototypes) = interface

    VonObject(
      "Interface",
      None,
      Vector(
        VonMember("name", vonifyName(fullName)),
        VonMember("kind", vonifyInterfaceRef(interface.getRef)),
        VonMember("weakable", VonBool(weakable)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("superInterfaces", VonArray(None, superInterfaces.map(vonifyInterfaceRef).toVector)),
        VonMember("methods", VonArray(None, prototypes.map(vonifyInterfaceMethod).toVector))))
  }

  def vonfiyStruct(struct: StructDefinitionH): IVonData = {
    val StructDefinitionH(fullName, weakable, mutability, edges, members) = struct

    VonObject(
      "Struct",
      None,
      Vector(
        VonMember("name", vonifyName(fullName)),
        VonMember("kind", vonifyStructRef(struct.getRef)),
        VonMember("weakable", VonBool(weakable)),
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
        VonMember("name", vonifyName(fullName)),
        VonMember("params", VonArray(None, params.map(vonifyCoord).toVector)),
        VonMember("return", vonifyCoord(returnType))))
  }

  def vonifyCoord(coord: ReferenceH[KindH]): IVonData = {
    val ReferenceH(ownership, location, permission, kind) = coord

    VonObject(
      "Ref",
      None,
      Vector(
        VonMember("ownership", vonifyOwnership(ownership)),
        VonMember("location", vonifyLocation(location)),
        VonMember("permission", vonifyPermission(permission)),
        VonMember("kind", vonifyKind(kind))))
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
      case m.PointerH => VonObject("Pointer", None, Vector())
      case m.BorrowH => VonObject("Borrow", None, Vector())
      case m.ShareH => VonObject("Share", None, Vector())
      case m.WeakH => VonObject("Weak", None, Vector())
    }
  }

  def vonifyStructMember(structMemberH: StructMemberH): IVonData = {
    val StructMemberH(name, variability, tyype) = structMemberH

    VonObject(
      "StructMember",
      None,
      Vector(
        VonMember("fullName", vonifyName(name)),
        VonMember("name", VonStr(name.readableName)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyCoord(tyype))))
  }

  def vonifyRuntimeSizedArrayDefinition(rsaDef: RuntimeSizedArrayDefinitionTH): IVonData = {
    val RuntimeSizedArrayDefinitionTH(name, mutability, elementType) = rsaDef
    VonObject(
      "RuntimeSizedArrayDefinition",
      None,
      Vector(
        VonMember("name", vonifyName(name)),
        VonMember("kind", vonifyKind(rsaDef.kind)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("elementType", vonifyCoord(elementType))))
  }

  def vonifyStaticSizedArrayDefinition(ssaDef: StaticSizedArrayDefinitionTH): IVonData = {
    val StaticSizedArrayDefinitionTH(name, size, mutability, variability, elementType) = ssaDef
    VonObject(
      "StaticSizedArrayDefinition",
      None,
      Vector(
        VonMember("name", vonifyName(name)),
        VonMember("kind", vonifyKind(ssaDef.kind)),
        VonMember("size", VonInt(size)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("elementType", vonifyCoord(elementType))))
  }

  def vonifyKind(kind: KindH): IVonData = {
    kind match {
      case NeverH() => VonObject("Never", None, Vector())
      case IntH(bits) => VonObject("Int", None, Vector(VonMember("bits", VonInt(bits))))
      case BoolH() => VonObject("Bool", None, Vector())
      case StrH() => VonObject("Str", None, Vector())
      case FloatH() => VonObject("Float", None, Vector())
      case ir @ InterfaceRefH(_) => vonifyInterfaceRef(ir)
      case sr @ StructRefH(_) => vonifyStructRef(sr)
      case RuntimeSizedArrayHT(name) => {
        VonObject(
          "RuntimeSizedArray",
          None,
          Vector(
            VonMember("name", vonifyName(name))))
      }
      case StaticSizedArrayHT(name) => {
        VonObject(
          "StaticSizedArray",
          None,
          Vector(
            VonMember("name", vonifyName(name))))
      }
    }
  }

  def vonifyFunction(functionH: FunctionH): IVonData = {
    val FunctionH(prototype, _, _, _, body) = functionH

    VonObject(
      "Function",
      None,
      Vector(
        VonMember("prototype", vonifyPrototype(prototype)),
        // TODO: rename block to body
        VonMember("block", vonifyExpression(body))))
  }

  def vonifyExpression(node: ExpressionH[KindH]): IVonData = {
    node match {
      case ConstantBoolH(value) => {
        VonObject(
          "ConstantBool",
          None,
          Vector(
            VonMember("value", VonBool(value))))
      }
      case ConstantIntH(value, bits) => {
        VonObject(
          "ConstantInt",
          None,
          Vector(
            VonMember("value", VonStr(value.toString)),
            VonMember("bits", VonInt(bits))))
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
      case ArrayCapacityH(sourceExpr) => {
        VonObject(
          "ArrayCapacity",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKnownLive", VonBool(false))))
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
      case wa @ BorrowToWeakH(sourceExpr) => {
        VonObject(
          "BorrowToWeak",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKind", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(wa.resultType)),
            VonMember("resultKind", vonifyKind(wa.resultType.kind))))
      }
      case wa @ PointerToWeakH(sourceExpr) => {
        VonObject(
          "PointerToWeak",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKind", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(wa.resultType)),
            VonMember("resultKind", vonifyKind(wa.resultType.kind))))
      }
      case AsSubtypeH(sourceExpr, targetType, resultResultType, okConstructor, errConstructor) => {
        VonObject(
          "AsSubtype",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("targetKind", vonifyKind(targetType)),
            VonMember("okConstructor", vonifyPrototype(okConstructor)),
            VonMember("okType", vonifyCoord(okConstructor.returnType)),
            VonMember("okKind", vonifyKind(okConstructor.returnType.kind)),
            VonMember("errConstructor", vonifyPrototype(errConstructor)),
            VonMember("errType", vonifyCoord(errConstructor.returnType)),
            VonMember("errKind", vonifyKind(errConstructor.returnType.kind)),
            VonMember("resultResultType", vonifyCoord(resultResultType)),
            VonMember("resultResultKind", vonifyKind(resultResultType.kind))))
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
            VonMember("someKind", vonifyKind(someConstructor.returnType.kind)),
            VonMember("noneConstructor", vonifyPrototype(noneConstructor)),
            VonMember("noneType", vonifyCoord(noneConstructor.returnType)),
            VonMember("noneKind", vonifyKind(noneConstructor.returnType.kind)),
            VonMember("resultOptType", vonifyCoord(resultOptType)),
            VonMember("resultOptKind", vonifyKind(resultOptType.kind))))
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
            VonMember("resultKind", vonifyKind(resultType.kind))))
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
              VonArray(None, targetMemberNames.map(n => vonifyName(n)).toVector)),
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
            VonMember("optName", vonifyOptional[FullNameH](name, n => vonifyName(n)))))
      }
      case UnstackifyH(local) => {
        VonObject(
          "Unstackify",
          None,
          Vector(
            VonMember("local", vonifyLocal(local))))
      }
      case BorrowToPointerH(sourceExpr) => {
        VonObject(
          "BorrowToPointer",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("resultType", vonifyCoord(sourceExpr.resultType))))
      }
      case PointerToBorrowH(sourceExpr) => {
        VonObject(
          "PointerToBorrow",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("resultType", vonifyCoord(sourceExpr.resultType))))
      }
      case NarrowPermissionH(refExpression, targetPermission) => {
        VonObject(
          "NarrowPermission",
          None,
          Vector(
            VonMember("sourceExpr", vonifyExpression(refExpression)),
            VonMember("targetPermission", vonifyPermission(targetPermission))))
      }
      case DestroyStaticSizedArrayIntoFunctionH(arrayExpr, consumerExpr, consumerMethod, arrayElementType, arraySize) => {
        VonObject(
          "DestroyStaticSizedArrayIntoFunction",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyExpression(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerMethod", vonifyPrototype(consumerMethod)),
            VonMember("consumerKnownLive", VonBool(false)),
            VonMember("arrayElementType", vonifyCoord(arrayElementType)),
            VonMember("arraySize", VonInt(arraySize))))
      }
      case DestroyStaticSizedArrayIntoLocalsH(structExpr, localTypes, localIndices) => {
        VonObject(
          "DestroyStaticSizedArrayIntoLocals",
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
      case PushRuntimeSizedArrayH(arrayExpr, newcomerExpr) => {
        VonObject(
          "PushRuntimeSizedArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("newcomerExpr", vonifyExpression(newcomerExpr)),
            VonMember("newcomerType", vonifyCoord(newcomerExpr.resultType)),
            VonMember("newcomerKind", vonifyKind(newcomerExpr.resultType.kind)),
            VonMember("consumerKnownLive", VonBool(false))))
      }
      case PopRuntimeSizedArrayH(arrayExpr, arrayElementType) => {
        VonObject(
          "PopRuntimeSizedArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayElementType", vonifyCoord(arrayElementType)),
            VonMember("consumerKnownLive", VonBool(false))))
      }
      case DestroyMutRuntimeSizedArrayH(arrayExpr) => {
        VonObject(
          "DestroyMutRuntimeSizedArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind))))
      }
      case DestroyImmRuntimeSizedArrayH(arrayExpr, consumerExpr, consumerMethod, arrayElementType) => {
        VonObject(
          "DestroyImmRuntimeSizedArray",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("consumerExpr", vonifyExpression(consumerExpr)),
            VonMember("consumerType", vonifyCoord(consumerExpr.resultType)),
            VonMember("consumerKind", vonifyKind(consumerExpr.resultType.kind)),
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
            VonMember("sourceStructKind", vonifyStructRef(sourceExpr.resultType.kind)),
            VonMember("targetInterfaceType", vonifyCoord(si.resultType)),
            VonMember("targetInterfaceKind", vonifyInterfaceRef(targetInterfaceRef))))
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
            VonMember("localName", vonifyName(localName)),
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
            VonMember("localName", vonifyName(localName))))
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
            VonMember("memberName", vonifyName(memberName))))
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
            VonMember("memberName", vonifyName(memberName))))
      }
      case StaticSizedArrayStoreH(arrayExpr, indexExpr, sourceExpr, resultType) => {
        VonObject(
          "StaticSizedArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case RuntimeSizedArrayStoreH(arrayExpr, indexExpr, sourceExpr, resultType) => {
        VonObject(
          "RuntimeSizedArrayStore",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexKind", vonifyKind(indexExpr.resultType.kind)),
            VonMember("sourceExpr", vonifyExpression(sourceExpr)),
            VonMember("sourceType", vonifyCoord(sourceExpr.resultType)),
            VonMember("sourceKind", vonifyKind(sourceExpr.resultType.kind)),
            VonMember("sourceKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case rsal @ RuntimeSizedArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, resultType) => {
        VonObject(
          "RuntimeSizedArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("indexType", vonifyCoord(indexExpr.resultType)),
            VonMember("indexKind", vonifyKind(indexExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(rsal.resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("targetPermission", vonifyPermission(targetPermission)),
            VonMember("expectedElementType", vonifyCoord(expectedElementType)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case NewMutRuntimeSizedArrayH(capacityExpr, elementType, resultType) => {
        VonObject(
          "NewMutRuntimeSizedArray",
          None,
          Vector(
            VonMember("capacityExpr", vonifyExpression(capacityExpr)),
            VonMember("capacityType", vonifyCoord(capacityExpr.resultType)),
            VonMember("capacityKind", vonifyKind(capacityExpr.resultType.kind)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("elementType", vonifyCoord(elementType))))
      }
      case NewImmRuntimeSizedArrayH(sizeExpr, generatorExpr, generatorMethod, elementType, resultType) => {
        VonObject(
          "NewImmRuntimeSizedArray",
          None,
          Vector(
            VonMember("sizeExpr", vonifyExpression(sizeExpr)),
            VonMember("sizeType", vonifyCoord(sizeExpr.resultType)),
            VonMember("sizeKind", vonifyKind(sizeExpr.resultType.kind)),
            VonMember("generatorExpr", vonifyExpression(generatorExpr)),
            VonMember("generatorType", vonifyCoord(generatorExpr.resultType)),
            VonMember("generatorKind", vonifyKind(generatorExpr.resultType.kind)),
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
            VonMember("generatorKind", vonifyKind(generatorExpr.resultType.kind)),
            VonMember("generatorMethod", vonifyPrototype(generatorMethod)),
            VonMember("generatorKnownLive", VonBool(false)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("elementType", vonifyCoord(resultType))))
      }
      case ssal @ StaticSizedArrayLoadH(arrayExpr, indexExpr, targetOwnership, targetPermission, expectedElementType, arraySize, resultType) => {
        VonObject(
          "StaticSizedArrayLoad",
          None,
          Vector(
            VonMember("arrayExpr", vonifyExpression(arrayExpr)),
            VonMember("arrayType", vonifyCoord(arrayExpr.resultType)),
            VonMember("arrayKind", vonifyKind(arrayExpr.resultType.kind)),
            VonMember("arrayKnownLive", VonBool(false)),
            VonMember("indexExpr", vonifyExpression(indexExpr)),
            VonMember("resultType", vonifyCoord(ssal.resultType)),
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
          vonifyOptional[FullNameH](maybeName, x => vonifyName(x)))))
  }

  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
    opt match {
      case None => VonObject("None", None, Vector())
      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
    }
  }

  def vonifyTemplarName(hinputs: Hinputs, hamuts: HamutsBox, fullName2: FullNameT[INameT]): VonStr = {
    val str = FullNameH.namePartsToString(fullName2.packageCoord, fullName2.steps.map(step => translateName(hinputs, hamuts, step)))
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
      case CoordListTemplata(coords) => {
        VonObject(
          "CoordListTemplata",
          None,
          Vector(
            VonMember("coords",
              VonArray(
                None,
                coords.map(coord => vonifyCoord(TypeHammer.translateReference(hinputs, hamuts, coord)))))))
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
      case RuntimeSizedArrayTemplateTemplata() => VonObject("ArrayTemplateTemplata", None, Vector())
      case ft @ FunctionTemplata(env, functionA) => {
        VonObject(
          "FunctionTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember(
              "function",
              vonifyName(translateFullName(hinputs, hamuts, ft.getTemplateName())))))
      }
      case st @ StructTemplata(env, struct) => {
        VonObject(
          "StructTemplata",
          None,
          Vector(
            VonMember("envName", vonifyTemplarName(hinputs, hamuts, env.fullName)),
            VonMember("structName", translateName(hinputs, hamuts, NameTranslator.translateCitizenName(st.originStruct.name)))))
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
            VonMember("subCitizenHumanName", translateName(hinputs, hamuts, NameTranslator.translateNameStep(impl.name))),
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

  def vonifyCodeLocation2(codeLocation: CodeLocationS): IVonData = {
    val CodeLocationS(file, offset) = codeLocation
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
    name: INameT
  ): IVonData = {
    name match {
      case ConstructingMemberNameT(name) => {
        VonObject(
          "ConstructingMemberName",
          None,
          Vector(
            VonMember("name", VonStr(name))))
      }
      case SelfNameT() => {
        VonObject(
          "SelfName",
          None,
          Vector())
      }
      case ImplDeclareNameT(codeLocation) => {
        VonObject(
          "ImplDeclareName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LetNameT(codeLocation) => {
        VonObject(
          "LetName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case StaticSizedArrayNameT(size, arr) => {
        VonObject(
          "StaticSizedArrayName",
          None,
          Vector(
            VonMember("size", VonInt(size)),
            VonMember("arr", translateName(hinputs, hamuts, arr))))
      }
      case RuntimeSizedArrayNameT(arr) => {
        VonObject(
          "RuntimeSizedArrayName",
          None,
          Vector(
            VonMember("arr", translateName(hinputs, hamuts, arr))))
      }
      case RawArrayNameT(mutability, elementType) => {
        VonObject(
          "RawArrayName",
          None,
          Vector(
            VonMember("mutability", vonifyMutability(Conversions.evaluateMutability(mutability))),
            VonMember("elementType", vonifyCoord(TypeHammer.translateReference(hinputs, hamuts, elementType)))))
      }
      case TemplarBlockResultVarNameT(life) => {
        VonObject(
          "TemplarBlockResultVarName",
          None,
          Vector(
            VonMember("life", VonStr(life.toString))))
      }
      case TemplarFunctionResultVarNameT() => {
        VonObject(
          "TemplarFunctionResultVarName",
          None,
          Vector())
      }
      case TemplarTemporaryVarNameT(life) => {
        VonObject(
          "TemplarTemporaryVarName",
          None,
          Vector(
            VonMember("life", VonStr(life.toString))))
      }
      case TemplarPatternMemberNameT(life) => {
        VonObject(
          "TemplarPatternMemberName",
          None,
          Vector(
            VonMember("life", VonStr(life.toString))))
      }
      case TemplarPatternDestructureeNameT(life) => {
        VonObject(
          "TemplarPatternPackName",
          None,
          Vector(
            VonMember("life", VonStr(life.toString))))
      }
      case UnnamedLocalNameT(codeLocation) => {
        VonObject(
          "UnnamedLocalName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case ClosureParamNameT() => {
        VonObject(
          "ClosureParamName",
          None,
          Vector())
      }
      case MagicParamNameT(codeLocation) => {
        VonObject(
          "MagicParamName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case CodeVarNameT(name) => {
        VonObject(
          "CodeVarName",
          None,
          Vector(
            VonMember("name", VonStr(name))))
      }
      case AnonymousSubstructConstructorNameT(templateArgs, params) => {
        VonObject(
          "AnonymousSubstructConstructorName",
          None,
          Vector(
            VonMember(
              "templateArgs",
              VonArray(None, templateArgs.map(vonifyTemplata(hinputs, hamuts, _)))),
            VonMember(
              "params",
              VonArray(None, templateArgs.map(vonifyTemplata(hinputs, hamuts, _))))))
      }
      case AnonymousSubstructTemplateNameT(interface) => {
        VonObject(
          "AnonymousSubstructMemberName",
          None,
          Vector(
            VonMember("interface", translateName(hinputs, hamuts, interface))))
      }
      case AnonymousSubstructMemberNameT(index) => {
        VonObject(
          "AnonymousSubstructMemberName",
          None,
          Vector(
            VonMember("interface", VonInt(index))))
      }
      case PrimitiveNameT(humanName) => {
        VonObject(
          "PrimitiveName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName))))
      }
      case PackageTopLevelNameT() => {
        VonObject(
          "GlobalPackageName",
          None,
          Vector())
      }
      case ExternFunctionNameT(humanName, parameters) => {
        VonObject(
          "EF",
          None,
          Vector(
            VonMember("humanName", VonStr(humanName))))
      }
      case FreeNameT(templateArgs, kind) => {
        VonObject(
          "StructFreeName",
          None,
          Vector(
//            VonMember("codeLoc", vonifyCodeLocation2(NameTranslator.translateCodeLocation(codeLoc))),
            VonMember(
              "templateArgs",
              VonArray(
                None,
                templateArgs
                  .map(templateArg => vonifyTemplata(hinputs, hamuts, templateArg))
                  .toVector)),
            VonMember(
              "kind",
              vonifyKind(TypeHammer.translateKind(hinputs, hamuts, kind)))))
      }
      case VirtualFreeNameT(templateArgs, param) => {
        VonObject(
          "F",
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
              "param",
              vonifyKind(TypeHammer.translateKind(hinputs, hamuts, param)))))
      }
      case FunctionNameT(humanName, templateArgs, parameters) => {
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
      case FunctionTemplateNameT(humanName, codeLocation) => {
        VonObject(
          "FunctionTemplateName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName)),
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LambdaTemplateNameT(codeLocation) => {
        VonObject(
          "LambdaTemplateName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LambdaCitizenTemplateNameT(codeLocation) => {
        VonObject(
          "LambdaCitizenTemplateName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case ConstructorNameT(parameters) => {
        VonObject(
          "ConstructorName",
          None,
          Vector())
      }
      case CitizenNameT(humanName, templateArgs) => {
        VonObject(
          "CitizenName",
          None,
          Vector(
            VonMember("humanName", translateName(hinputs, hamuts, humanName)),
            VonMember(
              "templateArgs",
              VonArray(
                None,
                templateArgs
                  .map(templateArg => vonifyTemplata(hinputs, hamuts, templateArg))
                  .toVector))))
      }
      case LambdaCitizenNameT(name) => {
        VonObject(
          "LambdaCitizenName",
          None,
          Vector(
            VonMember("name", translateName(hinputs, hamuts, name))))
      }
      case CitizenTemplateNameT(humanName) => {
        VonObject(
          "CitizenTemplateName",
          None,
          Vector(
            VonMember(humanName, VonStr(humanName))))
      }
      case AnonymousSubstructNameT(template, callables) => {
        VonObject(
          "AnonymousSubstructName",
          None,
          Vector(
            VonMember(
              "template",
              translateName(hinputs, hamuts, template)),
            VonMember(
              "callables",
              VonArray(
                None,
                callables
                  .map(coord => vonifyTemplata(hinputs, hamuts, coord))
                  .toVector))))
      }
      case AnonymousSubstructImplNameT() => {
        VonObject(
          "AnonymousSubstructImplName",
          None,
          Vector())
      }
    }
  }

  def vonifyName(h: FullNameH): IVonData = {
    val FullNameH(readableName, id, packageCoordinate, parts) = h
    VonObject(
      "Name",
      None,
      Vector(
        VonMember("readableName", VonStr(readableName)),
        VonMember("id", VonInt(id)),
        VonMember("packageCoordinate", NameHammer.translatePackageCoordinate(packageCoordinate)),
        VonMember("parts", VonArray(None, parts.map(MetalPrinter.print).map(VonStr).toVector))))
  }
}
