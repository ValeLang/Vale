package net.verdagon.vale.parser

import net.verdagon.vale.vimpl
import net.verdagon.von.{IVonData, VonArray, VonBool, VonFloat, VonInt, VonMember, VonObject, VonStr}

object ParserVonifier {

  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
    opt match {
      case None => VonObject("None", None, Vector())
      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
    }
  }

  def vonifyFile(file: FileP): IVonData = {
    val FileP(topLevelThings) = file

    VonObject(
      "File",
      None,
      Vector(
        VonMember("topLevelThings", VonArray(None, topLevelThings.map(vonifyTopLevelThing).toVector))))
  }

  def vonifyTopLevelThing(topLevelThingP: ITopLevelThingP): VonObject = {
    topLevelThingP match {
      case TopLevelFunctionP(function) => vonifyFunction(function)
      case TopLevelStructP(struct) => vonifyStruct(struct)
      case TopLevelInterfaceP(interface) => vonifyInterface(interface)
      case TopLevelImplP(impl) => vonifyImpl(impl)
      case TopLevelExportAsP(impl) => vonifyExportAs(impl)
      case TopLevelImportP(impl) => vonifyImport(impl)
    }
  }

  def vonifyCitizenAttribute(thing: ICitizenAttributeP): VonObject = {
    thing match {
      case WeakableP(range) => VonObject("WeakableAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case SealedP(range) => VonObject("SealedAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ExportP(range) => VonObject("ExportAttribute", None, Vector(VonMember("range", vonifyRange(range))))
    }
  }

  def vonifyRuneAttribute(thing: IRuneAttributeP): VonObject = {
    thing match {
      case TypeRuneAttributeP(range, tyype) => {
        VonObject(
          "ReadOnlyRuneAttribute",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("type", vonifyRuneType(tyype))))
      }
      case ReadOnlyRuneAttributeP(range) => VonObject("ReadOnlyRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case PoolRuneAttributeP(range) => VonObject("PoolRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ArenaRuneAttributeP(range) => VonObject("ArenaRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case BumpRuneAttributeP(range) => VonObject("BumpRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case x => vimpl(x.toString)
    }
  }

  def vonifyStruct(thing: StructP): VonObject = {
    val StructP(range, name, attributes, mutability, identifyingRunes, templateRules, members) = thing
    VonObject(
      "Struct",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyCitizenAttribute).toVector)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("identifyingRunes", vonifyOptional(identifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("members", vonifyStructMembers(members))))
  }

  def vonifyStructMembers(thing: StructMembersP) = {
    val StructMembersP(range, contents) = thing
    VonObject(
      "StructMembers",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("members", VonArray(None, contents.map(vonifyStructContents).toVector))))
  }

  def vonifyStructContents(thing: IStructContent) = {
    thing match {
      case sm @ StructMethodP(_) => vonifyStructMethod(sm)
      case sm @ StructMemberP(_, _, _, _) => vonifyStructMember(sm)
    }
  }

  def vonifyStructMember(thing: StructMemberP) = {
    val StructMemberP(range, name, variability, tyype) = thing
    VonObject(
      "StructMember",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyTemplex(tyype))))
  }

  def vonifyStructMethod(thing: StructMethodP) = {
    val StructMethodP(function) = thing
    VonObject(
      "StructMethod",
      None,
      Vector(
        VonMember("function", vonifyFunction(function))))
  }

  def vonifyInterface(thing: InterfaceP): VonObject = {
    val InterfaceP(range, name, attributes, mutability, maybeIdentifyingRunes, templateRules, members) = thing
    VonObject(
      "Interface",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyCitizenAttribute).toVector)),
        VonMember("mutability", vonifyMutability(mutability)),
        VonMember("maybeIdentifyingRunes", vonifyOptional(maybeIdentifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("members", VonArray(None, members.map(vonifyFunction).toVector))))
  }

  def vonifyImpl(impl: ImplP): VonObject = {
    val ImplP(range, identifyingRunes, rules, struct, interface) = impl

    VonObject(
      "Impl",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("identifyingRunes", vonifyOptional(identifyingRunes, vonifyIdentifyingRunes)),
        VonMember("rules", vonifyOptional(rules, vonifyTemplateRules)),
        VonMember("struct", vonifyTemplex(struct)),
        VonMember("interface", vonifyTemplex(interface))))
  }

  def vonifyExportAs(exportAs: ExportAsP): VonObject = {
    val ExportAsP(range, struct, exportedName) = exportAs

    VonObject(
      "ExportAs",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("struct", vonifyTemplex(struct)),
        VonMember("exportedName", vonifyName(exportedName))))
  }

  def vonifyImport(exportAs: ImportP): VonObject = {
    val ImportP(range, moduleName, packageSteps, importeeName) = exportAs

    VonObject(
      "Import",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("moduleName", vonifyName(moduleName)),
        VonMember("packageSteps", VonArray(None, packageSteps.map(vonifyName).toVector)),
        VonMember("importeeName", vonifyName(importeeName))))
  }

  def vonifyRange(range: Range): VonObject = {
    val Range(begin, end) = range
    VonObject(
      "Range",
      None,
      Vector(
        VonMember("begin", VonInt(begin)),
        VonMember("end", VonInt(end))))
  }

  def vonifyFunction(thing: FunctionP): VonObject = {
    val FunctionP(range, header, body) = thing
    VonObject(
      "Function",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("header", vonifyFunctionHeader(header)),
        VonMember("body", vonifyOptional(body, vonifyBlock))))
  }

  def vonifyFunctionHeader(thing: FunctionHeaderP): VonObject = {
    val FunctionHeaderP(range, name, attributes, maybeUserSpecifiedIdentifyingRunes, templateRules, params, FunctionReturnP(retRange, inferRet, retType)) = thing
    VonObject(
      "FunctionHeader",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyOptional(name, vonifyName)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyFunctionAttribute).toVector)),
        VonMember("maybeUserSpecifiedIdentifyingRunes", vonifyOptional(maybeUserSpecifiedIdentifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("params", vonifyOptional(params, vonifyParams)),
        VonMember(
          "ret",
          VonObject(
            "FunctionReturn",
            None,
            Vector(
              VonMember("range", vonifyRange(retRange)),
              VonMember("inferRet", vonifyOptional(inferRet, vonifyUnit)),
              VonMember("retType", vonifyOptional(retType, vonifyTemplex)))))))
  }

  def vonifyParams(thing: ParamsP): VonObject = {
    val ParamsP(range, patterns) = thing
    VonObject(
      "IdentifyingRunes",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("patterns", VonArray(None, patterns.map(vonifyPattern).toVector))))
  }

  def vonifyPattern(thing: PatternPP): VonObject = {
    val PatternPP(range, preBorrow, capture, templex, destructure, virtuality) = thing
    VonObject(
      "IdentifyingRunes",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("preBorrow", vonifyOptional(preBorrow, vonifyUnit)),
        VonMember("capture", vonifyOptional(capture, vonifyCapture)),
        VonMember("templex", vonifyOptional(templex, vonifyTemplex)),
        VonMember("destructure", vonifyOptional(destructure, vonifyDestructure)),
        VonMember("virtuality", vonifyOptional(virtuality, vonifyVirtuality))))
  }

  def vonifyCapture(thing: CaptureP): VonObject = {
    val CaptureP(range, captureName) = thing
    VonObject(
      "Capture",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("captureName", vonifyCaptureName(captureName))))
  }

  def vonifyCaptureName(thing: ICaptureNameP): VonObject = {
    thing match {
      case LocalNameP(name) => VonObject("LocalName", None, Vector(VonMember("name", vonifyName(name))))
      case ConstructingMemberNameP(name) => VonObject("ConstructingMemberName", None, Vector(VonMember("name", vonifyName(name))))
    }
  }

  def vonifyVariability(thing: VariabilityP): VonObject = {
    thing match {
      case FinalP => VonObject("Final", None, Vector())
      case VaryingP => VonObject("Varying", None, Vector())
    }
  }

  def vonifyVirtuality(thing: IVirtualityP): VonObject = {
    thing match {
      case AbstractP => VonObject("Abstract", None, Vector())
      case ov @ OverrideP(_, _) => vonifyOverride(ov)
    }
  }

  def vonifyOverride(thing: OverrideP): VonObject = {
    val OverrideP(range, tyype) = thing
    VonObject(
      "Override",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("type", vonifyTemplex(tyype))))
  }

  def vonifyDestructure(thing: DestructureP): VonObject = {
    val DestructureP(range, patterns) = thing
    VonObject(
      "Destructure",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("patterns", VonArray(None, patterns.map(vonifyPattern).toVector))))
  }

  def vonifyUnit(thing: UnitP): VonObject = {
    val UnitP(range) = thing
    VonObject(
      "Unit",
      None,
      Vector(
        VonMember("range", vonifyRange(range))))
  }

  def vonifyFunctionAttribute(thing: IFunctionAttributeP): VonObject = {
    thing match {
      case AbstractAttributeP(range) => VonObject("AbstractAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ExternAttributeP(range) => VonObject("ExternAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ExportAttributeP(range) => VonObject("ExportAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case PureAttributeP(range) => VonObject("PureAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case BuiltinAttributeP(range, generatorName) => {
        VonObject(
          "BuiltinAttribute",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("generatorName", vonifyName(generatorName))))
      }
    }
  }

  def vonifyTemplateRules(thing: TemplateRulesP): VonObject = {
    val TemplateRulesP(range, rules) = thing
    VonObject(
      "TemplateRules",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("rules", VonArray(None, rules.map(vonifyRule).toVector))))
  }

  def vonifyRule(thing: IRulexPR): VonObject = {
    thing match {
      case EqualsPR(range, left, right) => {
        VonObject(
          "EqualsPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyRule(left)),
            VonMember("right", vonifyRule(right))
          )
        )
      }
      case OrPR(range, possibilities) => {
        VonObject(
          "OrPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("possibilities", VonArray(None, possibilities.map(vonifyRule).toVector))
          )
        )
      }
      case DotPR(range, container, memberName) => {
        VonObject(
          "DotPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("container", vonifyRule(container)),
            VonMember("memberName", vonifyName(memberName))))
      }
      case ComponentsPR(range, container, components) => {
        VonObject(
          "ComponentsPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("container", vonifyRule(container)),
            VonMember("components", VonArray(None, components.map(vonifyRule).toVector))
          )
        )
      }
      case TypedPR(range, rune, tyype) => {
        VonObject(
          "TypedPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("rune", vonifyOptional(rune, vonifyName)),
            VonMember("type", vonifyRuneType(tyype))))
      }
      case TemplexPR(templex) => {
        VonObject(
          "TemplexPR",
          None,
          Vector(
            VonMember("templex", vonifyTemplex(templex))))
      }
      case CallPR(range, name, args) => {
        VonObject(
          "CallPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("name", vonifyName(name)),
            VonMember("args", VonArray(None, args.map(vonifyRule).toVector))
          )
        )
      }
      case ResolveSignaturePR(range, nameStrRule, argsPackRule) => {
        VonObject(
          "ResolveSignaturePR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("nameStrRule", vonifyRule(nameStrRule)),
            VonMember("argsPackRule", vonifyRule(argsPackRule)),
          )
        )
      }
      case PackPR(range, elements) => {
        VonObject(
          "PackPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("elements", VonArray(None, elements.map(vonifyRule).toVector))
          )
        )
      }
    }
  }

  def vonifyRuneType(thing: ITypePR): VonObject = {
    thing match {
      case IntTypePR => VonObject("IntTypePR", None, Vector())
      case BoolTypePR => VonObject("BoolTypePR", None, Vector())
      case OwnershipTypePR => VonObject("OwnershipTypePR", None, Vector())
      case MutabilityTypePR => VonObject("MutabilityTypePR", None, Vector())
      case PermissionTypePR => VonObject("PermissionTypePR", None, Vector())
      case LocationTypePR => VonObject("LocationTypePR", None, Vector())
      case CoordTypePR => VonObject("CoordTypePR", None, Vector())
      case PrototypeTypePR => VonObject("PrototypeTypePR", None, Vector())
      case KindTypePR => VonObject("KindTypePR", None, Vector())
      case RegionTypePR => VonObject("RegionTypePR", None, Vector())
      case CitizenTemplateTypePR => VonObject("CitizenTemplateTypePR", None, Vector())
    }
  }

  def vonifyIdentifyingRunes(thing: IdentifyingRunesP): VonObject = {
    val IdentifyingRunesP(range, identifyingRunesP) = thing
    VonObject(
      "IdentifyingRunes",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("identifyingRunes", VonArray(None, identifyingRunesP.map(vonifyIdentifyingRune).toVector))))
  }

  def vonifyIdentifyingRune(thing: IdentifyingRuneP): VonObject = {
    val IdentifyingRuneP(range, name, attributes) = thing
    VonObject(
      "IdentifyingRune",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyRuneAttribute).toVector))))
  }

  def vonifyName(thing: NameP): VonObject = {
    val NameP(range, name) = thing
    VonObject(
      "Name",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", VonStr(name))))
  }

  def vonifyTemplex(thing: ITemplexPT): VonObject = {
    thing match {
      case AnonymousRunePT(range) => {
        VonObject(
          "AnonymousRuneT",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case BoolPT(range, value) => {
        VonObject(
          "BoolT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonBool(value))))
      }
      case CallPT(range, template, args) => {
        VonObject(
          "CallT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("template", vonifyTemplex(template)),
            VonMember("args", VonArray(None, args.map(vonifyTemplex).toVector))))
      }
      case InlinePT(range, inner) => {
        VonObject(
          "InlineT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case IntPT(range, value) => {
        VonObject(
          "IntT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", VonStr(value.toString))))
      }
      case LocationPT(range, location) => {
        VonObject(
          "LocationT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("location", vonifyLocation(location))))
      }
      case ManualSequencePT(range, members) => {
        VonObject(
          "ManualSequenceT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("members", VonArray(None, members.map(vonifyTemplex).toVector))))
      }
      case MutabilityPT(range, mutability) => {
        VonObject(
          "MutabilityT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyMutability(mutability))))
      }
      case NameOrRunePT(rune) => {
        VonObject(
          "NameOrRuneT",
          None,
          Vector(
            VonMember("rune", vonifyName(rune))))
      }
      case NullablePT(range, inner) => {
        VonObject(
          "NullableT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case InterpretedPT(range, ownership, permission, inner) => {
        VonObject(
          "InterpretedT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("ownership", vonifyOwnership(ownership)),
            VonMember("permission", vonifyPermission(permission)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case OwnershipPT(range, ownership) => {
        VonObject(
          "OwnershipT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("ownership", vonifyOwnership(ownership))))
      }
      case PermissionPT(range, permission) => {
        VonObject(
          "PermissionT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("permission", vonifyPermission(permission))))
      }
      case RepeaterSequencePT(range, mutability, variability, size, element) => {
        VonObject(
          "RepeaterSequenceT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyTemplex(mutability)),
            VonMember("variability", vonifyTemplex(variability)),
            VonMember("size", vonifyTemplex(size)),
            VonMember("element", vonifyTemplex(element))))
      }
      case BorrowPT(range, inner) => {
        VonObject(
          "BorrowT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case FunctionPT(range, mutability, parameters, returnType) => {
        VonObject(
          "FunctionT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyOptional(mutability, vonifyTemplex)),
            VonMember("parameters", vonifyTemplex(parameters)),
            VonMember("returnType", vonifyTemplex(returnType))))
      }
      case PackPT(range, members) => {
        VonObject(
          "PackT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("members", VonArray(None, members.map(vonifyTemplex).toVector))))
      }
      case PrototypePT(range, name, parameters, returnType) => {
        VonObject(
          "PrototypeT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("name", vonifyName(name)),
            VonMember("parameters", VonArray(None, parameters.map(vonifyTemplex).toVector)),
            VonMember("returnType", vonifyTemplex(returnType))))
      }
      case SharePT(range, inner) => {
        VonObject(
          "ShareT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case StringPT(range, str) => {
        VonObject(
          "StringT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("str", VonStr(str))))
      }
      case TypedRunePT(range, rune, tyype) => {
        VonObject(
          "TypedRuneT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("rune", vonifyName(rune)),
            VonMember("type", vonifyRuneType(tyype))))
      }
      case VariabilityPT(range, variability) => {
        VonObject(
          "VariabilityT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("variability", vonifyVariability(variability))))
      }
    }
  }

  def vonifyPermission(thing: PermissionP): VonObject = {
    thing match {
      case ReadonlyP => VonObject("Readonly", None, Vector())
      case ReadwriteP => VonObject("Readwrite", None, Vector())
      case ExclusiveReadwriteP => VonObject("ExclusiveReadwrite", None, Vector())
    }
  }

  def vonifyMutability(thing: MutabilityP): IVonData = {
    thing match {
      case MutableP => VonObject("Mutable", None, Vector())
      case ImmutableP => VonObject("Immutable", None, Vector())
    }
  }

  def vonifyLocation(thing: LocationP): VonObject = {
    thing match {
      case InlineP => VonObject("Inline", None, Vector())
      case YonderP => VonObject("Yonder", None, Vector())
    }
  }

  def vonifyOwnership(thing: OwnershipP): VonObject = {
    thing match {
      case ShareP => VonObject("Share", None, Vector())
      case OwnP => VonObject("Own", None, Vector())
      case ConstraintP => VonObject("Constraint", None, Vector())
      case WeakP => VonObject("Weak", None, Vector())
    }
  }

  def vonifyLoadAs(thing: LoadAsP): VonObject = {
    thing match {
      case UseP => VonObject("Use", None, Vector())
      case MoveP => VonObject("Move", None, Vector())
      case LendConstraintP(permission) => {
        VonObject(
          "LendConstraint", None,
          Vector(
            VonMember("permission", vonifyOptional(permission, vonifyPermission))))
      }
      case LendWeakP(permission) => {
        VonObject(
          "LendWeak", None,
          Vector(
            VonMember("permission", vonifyPermission(permission))))
      }
    }
  }

  def vonifyBlock(thing: BlockPE): VonObject = {
    val BlockPE(range, elements) = thing
    VonObject(
      "Block",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("elements", VonArray(None, elements.map(vonifyExpression).toVector))))
  }

  def vonifyExpression(thing: IExpressionPE): VonObject = {
    thing match {
      case ConstantBoolPE(range, value) => {
        VonObject(
          "ConstantBool",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonBool(value))))
      }
      case DotPE(range, left, operatorRange, isMapAccess, member) => {
        VonObject(
          "Dot",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyExpression(left)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("isMapAccess", VonBool(isMapAccess)),
            VonMember("member", vonifyName(member))))
      }
      case ConstantFloatPE(range, value) => {
        VonObject(
          "ConstantFloat",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonFloat(value))))
      }
      case FunctionCallPE(range, inline, operatorRange, isMapCall, callableExpr, argExprs, callableTargetOwnership) => {
        VonObject(
          "FunctionCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inline", vonifyOptional(inline, vonifyUnit)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("isMapCall", VonBool(isMapCall)),
            VonMember("callableExpr", vonifyExpression(callableExpr)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector)),
            VonMember("callableTargetOwnership", vonifyLoadAs(callableTargetOwnership))))
      }
      case PackPE(range, innersPE) => {
        VonObject(
          "Pack",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("innerExprs", VonArray(None, innersPE.map(vonifyExpression).toVector))))
      }
      case MethodCallPE(range, inline, subjectExpr, operatorRange, subjectTargetOwnership, isMapCall, methodLookup, argExprs) => {
        VonObject(
          "MethodCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inline", vonifyOptional(inline, vonifyUnit)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("isMapCall", VonBool(isMapCall)),
            VonMember("subjectExpr", vonifyExpression(subjectExpr)),
            VonMember("method", vonifyExpression(methodLookup)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector)),
            VonMember("subjectTargetOwnership", vonifyLoadAs(subjectTargetOwnership))))
      }
      case ShortcallPE(range, argExprs) => {
        VonObject(
          "Shortcall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector))))
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        VonObject(
          "If",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("condition", vonifyExpression(condition)),
            VonMember("thenBody", vonifyExpression(thenBody)),
            VonMember("elseBody", vonifyExpression(elseBody))))
      }
      case IndexPE(range, left, args) => {
        VonObject(
          "Index",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyExpression(left)),
            VonMember("args", VonArray(None, args.map(vonifyExpression).toVector))))
      }
      case ConstantIntPE(range, value, bits) => {
        VonObject(
          "ConstantInt",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonStr(value.toString)),
            VonMember("bits", VonInt(bits))))
      }
      case LendPE(range, inner, targetOwnership) => {
        VonObject(
          "Lend",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyExpression(inner)),
            VonMember("targetOwnership", vonifyLoadAs(targetOwnership))))
      }
      case LetPE(range, templateRules, pattern, source) => {
        VonObject(
          "Let",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
            VonMember("pattern", vonifyPattern(pattern)),
            VonMember("source", vonifyExpression(source))))
      }
      case BadLetPE(range) => {
        VonObject(
          "BadLet",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case LookupPE(name, templateArgs) => {
        VonObject(
          "Lookup",
          None,
          Vector(
            VonMember("name", vonifyName(name)),
            VonMember("templateArgs", vonifyOptional(templateArgs, vonifyTemplateArgs))))
      }
      case MutatePE(range, mutatee, expr) => {
        VonObject(
          "Mutate",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("source", vonifyExpression(expr)),
            VonMember("mutatee", vonifyExpression(mutatee))))
      }
      case ReturnPE(range, expr) => {
        VonObject(
          "Return",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("expr", vonifyExpression(expr))))
      }
      case ConstantStrPE(range, value) => {
        VonObject(
          "ConstantStr",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonStr(value))))
      }
      case StrInterpolatePE(range, parts) => {
        VonObject(
          "StrInterpolate",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("parts", VonArray(None, parts.toVector.map(vonifyExpression)))))
      }
      case AndPE(range, left, right) => {
        VonObject(
          "And",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyExpression(left)),
            VonMember("right", vonifyExpression(right))))
      }
      case OrPE(range, left, right) => {
        VonObject(
          "Or",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyExpression(left)),
            VonMember("right", vonifyExpression(right))))
      }
      case b @ BlockPE(_, _) => {
        vonifyBlock(b)
      }
      case DestructPE(range, inner) => {
        VonObject(
          "Destruct",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyExpression(inner))))
      }
      case LambdaPE(captures, function) => {
        VonObject(
          "Lambda",
          None,
          Vector(
            VonMember("captures", vonifyOptional(captures, vonifyUnit)),
            VonMember("function", vonifyFunction(function))))
      }
      case MagicParamLookupPE(range) => {
        VonObject(
          "MagicParamLookup",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case MatchPE(range, condition, lambdas) => {
        VonObject(
          "Match",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("condition", vonifyExpression(condition)),
            VonMember("lambdas", VonArray(None, lambdas.map(vonifyExpression).toVector))))
      }
      case TuplePE(range, elements) => {
        VonObject(
          "Tuple",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("elements", VonArray(None, elements.map(vonifyExpression).toVector))))
      }
      case ca @ ConstructArrayPE(range, mutability, variability, size, initializingIndividualElements, args) => {
        vonifyConstructArray(ca)
      }
      case VoidPE(range) => {
        VonObject(
          "Void",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case WhilePE(range, condition, body) => {
        VonObject(
          "While",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("condition", vonifyBlock(condition)),
            VonMember("body", vonifyBlock(body))))
      }
    }
  }

  private def vonifyArraySize(obj: IArraySizeP): VonObject = {
    obj match {
      case RuntimeSizedP => VonObject("RuntimeSized", None, Vector())
      case StaticSizedP(maybeSize) => {
        VonObject(
          "StaticSized",
          None,
          Vector(
            VonMember("size", vonifyOptional(maybeSize, vonifyTemplex))
          ))
      }
    }
  }

  private def vonifyConstructArray(ca: ConstructArrayPE): VonObject = {
    val ConstructArrayPE(range, mutability, variability, size, initializingIndividualElements, args) = ca

    VonObject(
      "ConstructArray",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("mutability", vonifyOptional(mutability, vonifyTemplex)),
        VonMember("variability", vonifyOptional(variability, vonifyTemplex)),
        VonMember("size", vonifyArraySize(size)),
        VonMember("initializingIndividualElements", VonBool(initializingIndividualElements)),
        VonMember("args", VonArray(None, args.map(vonifyExpression).toVector))))
  }

  def vonifyTemplateArgs(thing: TemplateArgsP): IVonData = {
    val TemplateArgsP(range, args) = thing
    VonObject(
      "TemplateArgs",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("args", VonArray(None, args.map(vonifyTemplex).toVector))))
  }

//
//  def vonifyThing(thing: Thing): VonObject = {
//    val Thing(range) = thing
//    VonObject(
//      "Thing",
//      None,
//      Vector(
//        VonMember("range", vonifyRange(range))))
//  }
}
