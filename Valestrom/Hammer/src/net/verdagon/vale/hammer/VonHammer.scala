package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vimpl
import net.verdagon.von._

object VonHammer {
  def vonifyProgram(program: ProgramH): IVonData = {
    val ProgramH(interfaces, structs, externs, functions) = program

    VonObject(
      "Program",
      None,
      Vector(
        VonMember("interfaces", VonArray(None, interfaces.map(vonifyInterface).toVector)),
        VonMember("structs", VonArray(None, structs.map(vonfiyStruct).toVector)),
        VonMember("externs", VonArray(None, externs.map(vonifyPrototype).toVector)),
        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector))))
  }

  def vonifyStructRef(ref: StructRefH): IVonData = {
    val StructRefH(fullName) = ref

    VonObject(
      "StructId",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toString()))))
  }

  def vonifyInterfaceRef(ref: InterfaceRefH): IVonData = {
    val InterfaceRefH(fullName) = ref

    VonObject(
      "InterfaceId",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toString()))))
  }

  def vonifyInterface(interface: InterfaceDefinitionH): IVonData = {
    val InterfaceDefinitionH(fullName, mutability, superInterfaces, prototypes) = interface

    VonObject(
      "Interface",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toString())),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("superInterfaces", VonArray(None, superInterfaces.map(vonifyInterfaceRef).toVector)),
        VonMember("methods", VonArray(None, prototypes.map(vonifyPrototype).toVector))))
  }

  def vonfiyStruct(struct: StructDefinitionH): IVonData = {
    val StructDefinitionH(fullName, export, mutability, edges, members) = struct

    VonObject(
      "Struct",
      None,
      Vector(
        VonMember("name", VonStr(fullName.toString())),
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

  def vonifyLocation(location: m.Location): IVonData = {
    location match {
      case m.Inline => VonObject("Inline", None, Vector())
      case m.Yonder => VonObject("Yonder", None, Vector())
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
        VonMember("name", VonStr(fullName.toString())),
        VonMember("params", VonArray(None, params.map(vonifyCoord).toVector)),
        VonMember("return", vonifyCoord(returnType))))
  }

  def vonifyCoord(coord: ReferenceH[ReferendH]): IVonData = {
    val ReferenceH(ownership, referend) = coord

    VonObject(
      "Ref",
      None,
      Vector(
        VonMember("ownership", vonifyOwnership(ownership)),
        VonMember("referend", vonifyKind(referend))))
  }

  def vonifyEdge(edgeH: EdgeH): IVonData = {
    val EdgeH(struct, interface, structPrototypesByInterfacePrototype) = edgeH

    VonObject(
      "Edge",
      None,
      Vector(
        VonMember("struct", vonifyStructRef(struct)),
        VonMember("interface", vonifyInterfaceRef(interface)),
        VonMember(
          "methods",
          VonArray(
            None,
            structPrototypesByInterfacePrototype.toVector.map({ case (interfacePrototype, structPrototype) =>
              VonObject(
                "Entry",
                None,
                Vector(
                  VonMember("key", vonifyPrototype(interfacePrototype)),
                  VonMember("value", vonifyPrototype(structPrototype))))
            })))))
  }

  def vonifyOwnership(ownership: m.OwnershipH): IVonData = {
    ownership match {
      case m.OwnH => VonObject("Own", None, Vector())
      case m.BorrowH => VonObject("Borrow", None, Vector())
      case m.ShareH => VonObject("Share", None, Vector())
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
        VonMember("name", VonStr(name.toString())),
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
      case UnknownSizeArrayTH(rawArray) => {
        VonObject(
          "UnknownSizeArray",
          None,
          Vector(
            VonMember("array", vonifyRawArray(rawArray))))
      }
      case KnownSizeArrayTH(size, rawArray) => {
        VonObject(
          "UnknownSizeArray",
          None,
          Vector(
            VonMember("size", VonInt(size)),
            VonMember("array", vonifyRawArray(rawArray))))
      }
    }
  }

  def vonifyRawArray(t: RawArrayTH): IVonData = {
    val RawArrayTH(elementType) = t

    VonObject(
      "Array",
      None,
      Vector(
        VonMember("elementType", vonifyCoord(elementType))))
  }

  def vonifyFunction(functionH: FunctionH): IVonData = {
    val FunctionH(prototype, _, _, _, body) = functionH

    VonObject(
      "Function",
      None,
      Vector(
        VonMember("prototype", vonifyPrototype(prototype)),
        // TODO: rename block to body
        VonMember("block", vonifyNode(body))))
  }

  def vonifyBlock(block: BlockH): IVonData = {
    val BlockH(nodes) = block

    VonObject(
      "Block",
      None,
      Vector(
        VonMember("exprs", VonArray(None, nodes.map(node => vonifyNode(node)).toVector))))
  }

  def vonifyNode(node: ExpressionH[ReferendH]): IVonData = {
    node match {
      case UnreachableMootH(sourceExpr) => {
        VonObject(
          "UnreachableMoot",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr))))
      }
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
            VonMember("sourceExpr", vonifyNode(sourceExpr))))
      }
      case ReturnH(sourceExpr) => {
        VonObject(
          "Return",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr))))
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
      case NewArrayFromValuesH(resultType, sourceRegisters) => {
        VonObject(
          "NewArrayFromValuesH",
          None,
          Vector(
            VonMember(
              "sourceExprs",
              VonArray(None, sourceRegisters.map(vonifyNode).toVector)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case NewStructH(sourceExprs, resultType) => {
        VonObject(
          "NewStruct",
          None,
          Vector(
            VonMember(
              "sourceExprs",
              VonArray(None, sourceExprs.map(vonifyNode).toVector)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case StackifyH(sourceExpr, local, name) => {
        VonObject(
          "Stackify",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("local", vonifyLocal(local)),
            VonMember("name", vonifyOptional[FullNameH](name, n => VonStr(n.toString())))))
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
      case DestroyKnownSizeArrayH(arrayRegister, consumerRegister) => {
        VonObject(
          "DestroyKnownSizeArray",
          None,
          Vector(
            VonMember("arrayRegister", vonifyNode(arrayRegister)),
            VonMember("consumerRegister", vonifyNode(consumerRegister))))
      }
      case DestructureArraySequenceH(structExpr, localTypes, localIndices) => {
        VonObject(
          "DestructureArraySequenceH",
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
      case DestructureH(structExpr, localTypes, locals) => {
        VonObject(
          "Destructure",
          None,
          Vector(
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember(
              "localTypes",
              VonArray(None, localTypes.map(localType => vonifyCoord(localType)).toVector)),
            VonMember(
              "localIndices",
              VonArray(None, locals.map(local => vonifyLocal(local))))))
      }
      case DestroyUnknownSizeArrayH(arrayRegister, consumerRegister) => {
        VonObject(
          "DestroyUnknownSizeArray",
          None,
          Vector(
            VonMember("arrayRegister", vonifyNode(arrayRegister)),
            VonMember("consumerRegister", vonifyNode(consumerRegister))))
      }
      case StructToInterfaceUpcastH(sourceExpr, targetInterfaceRef) => {
        VonObject(
          "StructToInterfaceUpcast",
          None,
          Vector(
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("targetInterfaceRef", vonifyKind(targetInterfaceRef))))
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
            VonMember("localName", VonStr(localName.toString()))))
      }
      case LocalLoadH(local, targetOwnership, localName) => {
        VonObject(
          "LocalLoad",
          None,
          Vector(
            VonMember("local", vonifyLocal(local)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("localName", VonStr(localName.toString()))))
      }
      case MemberStoreH(resultType, structExpr, memberIndex, sourceExpr, memberName) => {
        VonObject(
          "MemberStore",
          None,
          Vector(
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("sourceExpr", vonifyNode(sourceExpr)),
            VonMember("memberName", VonStr(memberName.toString()))))
      }
      case MemberLoadH(structExpr, memberIndex, targetOwnership, expectedMemberType, expectedResultType, memberName) => {
        VonObject(
          "MemberLoad",
          None,
          Vector(
            VonMember("structExpr", vonifyNode(structExpr)),
            VonMember("structId", vonifyStructRef(structExpr.resultType.kind)),
            VonMember("memberIndex", VonInt(memberIndex)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("expectedMemberType", vonifyCoord(expectedMemberType)),
            VonMember("expectedResultType", vonifyCoord(expectedResultType)),
            VonMember("memberName", VonStr(memberName.toString()))))
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
          "UnknownSizeArrayStoreH",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("sourceExpr", vonifyNode(sourceExpr))))
      }
      case UnknownSizeArrayLoadH(arrayExpr, indexExpr, resultType, targetOwnership) => {
        VonObject(
          "UnknownSizeArrayLoadH",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership))))
      }
      case ConstructUnknownSizeArrayH(sizeRegister, generatorRegister, resultType) => {
        VonObject(
          "UnknownSizeArrayLoadH",
          None,
          Vector(
            VonMember("sizeRegister", vonifyNode(sizeRegister)),
            VonMember("generatorRegister", vonifyNode(generatorRegister)),
            VonMember("resultType", vonifyCoord(resultType))))
      }
      case KnownSizeArrayLoadH(arrayExpr, indexExpr, resultType, targetOwnership) => {
        VonObject(
          "KnownSizeArrayLoadH",
          None,
          Vector(
            VonMember("arrayExpr", vonifyNode(arrayExpr)),
            VonMember("indexExpr", vonifyNode(indexExpr)),
            VonMember("resultType", vonifyCoord(resultType)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership))))
      }
      case ExternCallH(functionExpr, argsExprs) => {
        VonObject(
          "ExternCall",
          None,
          Vector(
            VonMember("function", vonifyPrototype(functionExpr)),
            VonMember("argExprs", VonArray(None, argsExprs.toVector.map(vonifyNode)))))
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
            VonMember("interfaceRefH", vonifyInterfaceRef(interfaceRefH)),
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
            VonMember("elseBlock", vonifyNode(elseBlock)),
            VonMember("commonSupertype", vonifyCoord(commonSupertype))))
      }
      case WhileH(bodyBlock) => {
        VonObject(
          "While",
          None,
          Vector(
            VonMember("bodyBlock", vonifyNode(bodyBlock))))
      }
      case blockH @ BlockH(_) => {
        vonifyBlock(blockH)
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
          "name",
          vonifyOptional[FullNameH](maybeName, x => VonStr(x.toString())))))
  }

  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
    opt match {
      case None => VonObject("None", None, Vector())
      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
    }
  }

//  def translateTemplata(hinputs: Hinputs, hamuts: HamutsBox, templata: ITemplata): ITemplataH = {
//    templata match {
//      case CoordTemplata(reference) => {
//        val (coordH) = TypeHammer.translateReference(hinputs, hamuts, reference)
//        (CoordTemplataH(coordH))
//      }
//      case KindTemplata(kind) => {
//        val (kindH) = TypeHammer.translateKind(hinputs, hamuts, kind)
//        (KindTemplataH(kindH))
//      }
//      case ArrayTemplateTemplata() => ArrayTemplateTemplataH()
//      case FunctionTemplata(outerEnv, unevaluatedContainers, function) => {
//        val (outerEnvNameH) = translateName(hinputs, hamuts, outerEnv.fullName)
//        (FunctionTemplataH(outerEnvNameH, unevaluatedContainers.map(translateContainer), function.name, Conversions.evaluateCodeLocation(function.codeLocation)))
//      }
//      case StructTemplata(outerEnv, struct) => {
//        val (outerEnvNameH) = translateName(hinputs, hamuts, outerEnv.fullName)
//        (StructTemplataH(outerEnvNameH, struct.name, Conversions.evaluateCodeLocation(struct.codeLocation)))
//      }
//      case InterfaceTemplata(outerEnv, interface) => {
//        val (outerEnvNameH) = translateName(hinputs, hamuts, outerEnv.fullName)
//        (InterfaceTemplataH(outerEnvNameH, interface.name, Conversions.evaluateCodeLocation(interface.codeLocation)))
//      }
//      case ImplTemplata(outerEnv, impl) => {
//        val (outerEnvNameH) = translateName(hinputs, hamuts, outerEnv.fullName)
//        (ImplTemplataH(outerEnvNameH, Conversions.evaluateCodeLocation(impl.codeLocation)))
//      }
//      case ExternFunctionTemplata(header) => {
//        val (outerEnvNameH) = translateName(hinputs, hamuts, header.fullName)
//        (ExternFunctionTemplataH(outerEnvNameH))
//      }
//      case OwnershipTemplata(ownership) => OwnershipTemplataH(Conversions.evaluateOwnership(ownership))
//      case VariabilityTemplata(variability) => VariabilityTemplataH(Conversions.evaluateVariability(variability))
//      case MutabilityTemplata(mutability) => MutabilityTemplataH(Conversions.evaluateMutability(mutability))
//      case PermissionTemplata(permission) => PermissionTemplataH(Conversions.evaluatePermission(permission))
//      case LocationTemplata(location) => LocationTemplataH(Conversions.evaluateLocation(location))
//      case BooleanTemplata(value) => BooleanTemplataH(value)
//      case IntegerTemplata(value) => IntegerTemplataH(value)
//    }
//  }

  def vonifyFullName(hinputs: Hinputs, hamuts: HamutsBox, fullName2: FullName2[IName2]): VonStr = {
    val array = VonArray(None, fullName2.steps.map(step => translateName(hinputs, hamuts, step)).toVector)
    VonStr(array.toString)
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
            VonMember("envName", vonifyFullName(hinputs, hamuts, env.fullName)),
            VonMember(
              "function",
              translateName(hinputs, hamuts, ft.getTemplateName()))))
      }
      case st @ StructTemplata(env, struct) => {
        VonObject(
          "StructTemplata",
          None,
          Vector(
            VonMember("envName", vonifyFullName(hinputs, hamuts, env.fullName)),
            VonMember("structName", translateName(hinputs, hamuts, st.getTemplateName()))))
      }
      case it @ InterfaceTemplata(env, interface) => {
        VonObject(
          "InterfaceTemplata",
          None,
          Vector(
            VonMember("envName", vonifyFullName(hinputs, hamuts, env.fullName)),
            VonMember("interfaceName", translateName(hinputs, hamuts, it.getTemplateName()))))
      }
      case it @ ImplTemplata(env, impl) => {
        VonObject(
          "ExternFunctionTemplata",
          None,
          Vector(
            VonMember("envName", vonifyFullName(hinputs, hamuts, env.fullName)),
            VonMember("location", translateName(hinputs, hamuts, it.getTemplateName()))))
      }
      case ExternFunctionTemplata(header) => VonObject("ExternFunctionTemplata", None, Vector(VonMember("name", vonifyFullName(hinputs, hamuts, header.fullName))))
      case OwnershipTemplata(ownership) => VonObject("OwnershipTemplata", None, Vector(VonMember("ownership", vonifyOwnership(Conversions.evaluateOwnership(ownership)))))
      case VariabilityTemplata(variability) => VonObject("VariabilityTemplata", None, Vector(VonMember("variability", vonifyVariability(Conversions.evaluateVariability(variability)))))
      case MutabilityTemplata(mutability) => VonObject("MutabilityTemplata", None, Vector(VonMember("mutability", vonifyMutability(Conversions.evaluateMutability(mutability)))))
      case PermissionTemplata(permission) => VonObject("PermissionTemplata", None, Vector(VonMember("permission", vonifyPermission(Conversions.evaluatePermission(permission)))))
      case LocationTemplata(location) => VonObject("LocationTemplata", None, Vector(VonMember("location", vonifyLocation(Conversions.evaluateLocation(location)))))
      case BooleanTemplata(value) => VonObject("BooleanTemplata", None, Vector(VonMember("value", VonBool(value))))
      case IntegerTemplata(value) => VonObject("IntegerTemplata", None, Vector(VonMember("value", VonInt(value))))
    }
  }

//  def vonifyContainer(x: ContainerH): IVonData = {
//    val ContainerH(humanName, location) = x
//    VonObject(
//      "Container",
//      None,
//      Vector(
//        VonMember(None, Some(humanName), VonStr(humanName)),
//        VonMember("codeLocation", vonifyCodeLocation(location))))
//  }

  def vonifyCodeLocation(codeLocation: m.CodeLocation): IVonData = {
    val m.CodeLocation(line, char) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("line", VonInt(line)),
        VonMember("char", VonInt(char))))
  }

  def vonifyCodeLocationH(codeLocation: CodeLocationH): IVonData = {
    val CodeLocationH(file, line, char) = codeLocation
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("file", VonStr(file)),
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
      case ImplDeclareName2(codeLocation) => {
        VonObject(
          "ImplDeclareName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
      }
      case LetName2(codeLocation) => {
        VonObject(
          "LetName",
          None,
          Vector(
            VonMember("codeLocation", vonifyCodeLocation2(codeLocation))))
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
