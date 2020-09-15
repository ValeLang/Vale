package net.verdagon.vale.parser

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
//        VonMember("structs", VonArray(None, structs.map(vonfiyStruct).toVector)),
//        VonMember("externs", VonArray(None, externs.map(vonifyPrototype).toVector)),
//        VonMember("functions", VonArray(None, functions.map(vonifyFunction).toVector)),
//        VonMember("knownSizeArrays", VonArray(None, knownSizeArrays.map(vonifyKind).toVector)),
//        VonMember("unknownSizeArrays", VonArray(None, unknownSizeArrays.map(vonifyKind).toVector)),
//        VonMember("emptyTupleStructReferend", vonifyKind(ProgramH.emptyTupleStructRef)),
//        VonMember(
//          "immDestructorsByReferend",
//          VonArray(
//            None,
//            immDestructorsByKind.toVector.map({ case (kind, destructor) =>
//              VonObject(
//                "Entry",
//                None,
//                Vector(
//                  VonMember("referend", vonifyKind(kind)),
//                  VonMember("destructor", vonifyPrototype(destructor))))
//            }))),
//        VonMember(
//          "exportedNameByFullName",
//          VonArray(
//            None,
//            exportedNameByFullName.toVector.map({ case (fullName, exportedName) =>
//              VonObject(
//                "Entry",
//                None,
//                Vector(
//                  VonMember("fullName", VonStr(fullName.toReadableString)),
//                  VonMember("exportedName", VonStr(exportedName))))
//            })))))
  }

  def vonifyTopLevelThing(topLevelThingP: ITopLevelThingP): VonObject = {
    topLevelThingP match {
      case TopLevelFunctionP(function) => vonifyFunction(function)
      case TopLevelStructP(struct) => vonifyStruct(struct)
      case TopLevelInterfaceP(interface) => vonifyInterface(interface)
      case TopLevelImplP(impl) => vonifyImpl(impl)
    }
  }

  def vonifyStruct(thing: StructP): VonObject = {
    val StructP(range, name, attributes, mutability, identifyingRunes, templateRules, members) = thing
    VonObject(
      "StructTODO",
      None,
      Vector())
  }

  def vonifyInterface(thing: InterfaceP): VonObject = {
    val InterfaceP(range, name, attributes, mutability, maybeIdentifyingRunes, templateRules, members) = thing
    VonObject(
      "InterfaceTODO",
      None,
      Vector())
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
    val FunctionHeaderP(range, name, attributes, maybeUserSpecifiedIdentifyingRunes, templateRules, params, ret) = thing
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
        VonMember("ret", vonifyOptional(ret, vonifyTemplex))))
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
    val CaptureP(range, captureName, variability) = thing
    VonObject(
      "Capture",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("captureName", vonifyCaptureName(captureName)),
        VonMember("variability", vonifyVariability(variability))))
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
    }
  }

  def vonifyTemplateRules(thing: TemplateRulesP): VonObject = {
    val TemplateRulesP(range, rules) = thing
    VonObject(
      "TemplateRulesTODO",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("rules", VonInt(0))))
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
        VonMember("range", vonifyRange(range))))
  }

  def vonifyName(thing: StringP): VonObject = {
    val StringP(range, name) = thing
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
            VonMember("inner", VonInt(value))))
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
            VonMember("inner", vonifyMutability(mutability))))
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
      case OwnershippedPT(range, ownership, inner) => {
        VonObject(
          "OwnershippedT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("ownership", vonifyOwnership(ownership)),
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
      case PermissionedPT(range, permission, inner) => {
        VonObject(
          "PermissionedT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("permission", vonifyPermission(permission)),
            VonMember("inner", vonifyTemplex(inner))))
      }
      case PermissionPT(range, permission) => {
        VonObject(
          "PermissionT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("permission", vonifyPermission(permission))))
      }
      case RepeaterSequencePT(range, mutability, size, element) => {
        VonObject(
          "RepeaterSequenceT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyTemplex(mutability)),
            VonMember("size", vonifyTemplex(size)),
            VonMember("element", vonifyTemplex(element))))
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

  def vonifyMutability(thing: MutabilityP): VonObject = {
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
      case BorrowP => VonObject("Constraint", None, Vector())
      case WeakP => VonObject("Weak", None, Vector())
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
      case BoolLiteralPE(range, value) => {
        VonObject(
          "BoolLiteral",
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
      case FloatLiteralPE(range, value) => {
        VonObject(
          "FloatLiteral",
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
            VonMember("callableTargetOwnership", vonifyOwnership(callableTargetOwnership))))
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
      case IntLiteralPE(range, value) => {
        VonObject(
          "IntLiteral",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonInt(value))))
      }
      case LendPE(range, inner, targetOwnership) => {
        VonObject(
          "Lend",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyExpression(inner)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership))))
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
      case LookupPE(name, templateArgs) => {
        VonObject(
          "Lookup",
          None,
          Vector(
            VonMember("name", vonifyName(name)),
            VonMember("templateArgs", vonifyOptional(templateArgs, vonifyTemplateArgs))))
      }
      case MethodCallPE(range, callableExpr, operatorRange, callableTargetOwnership, isMapCall, methodLookup, argExprs) => {
        VonObject(
          "MethodCallTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("isMapCall", VonBool(isMapCall)),
            VonMember("callableExpr", vonifyExpression(callableExpr)),
            VonMember("method", vonifyExpression(methodLookup)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector)),
            VonMember("callableTargetOwnership", vonifyOwnership(callableTargetOwnership))))
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
      case StrLiteralPE(range, value) => {
        VonObject(
          "StrLiteralTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case AndPE(left, right) => {
        VonObject(
          "AndTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case b @ BlockPE(_, _) => {
        vonifyBlock(b)
      }
      case DestructPE(range, inner) => {
        VonObject(
          "DestructTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case LambdaPE(captures, function) => {
        VonObject(
          "LambdaTODO",
          None,
          Vector(

          ))
      }
      case MagicParamLookupPE(range) => {
        VonObject(
          "MagicParamLookupTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case MatchPE(range, condition, lambdas) => {
        VonObject(
          "MatchTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case OrPE(left, right) => {
        VonObject(
          "OrTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case RepeaterBlockIteratorPE(expression) => {
        VonObject(
          "RepeaterBlockIteratorTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case RepeaterBlockPE(expression) => {
        VonObject(
          "RepeaterBlockTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case RepeaterPackIteratorPE(expression) => {
        VonObject(
          "RepeaterPackIteratorTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case RepeaterPackPE(expression) => {
        VonObject(
          "RepeaterPackTODO",
          None,
          Vector())
//            VonMember("range", vonifyRange(range))))
      }
      case SequencePE(range, elements) => {
        VonObject(
          "SequenceTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case VoidPE(range) => {
        VonObject(
          "VoidTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
      case WhilePE(range, condition, body) => {
        VonObject(
          "WhileTODO",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
      }
    }
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
