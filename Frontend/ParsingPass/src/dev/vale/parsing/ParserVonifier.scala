package dev.vale.parsing

import dev.vale.lexing.RangeL
import dev.vale.parsing.ast._
import dev.vale.{FileCoordinate, PackageCoordinate, Profiler, vimpl}
import dev.vale.parsing.ast._
import dev.vale.von.{IVonData, VonArray, VonBool, VonFloat, VonInt, VonMember, VonObject, VonStr}

object ParserVonifier {

  def vonifyOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
    opt match {
      case None => VonObject("None", None, Vector())
      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
    }
  }

  def vonifyFile(file: FileP): IVonData = {
    Profiler.frame(() => {
      val FileP(fileCoord, commentsRanges, denizens) = file

      VonObject(
        "File",
        None,
        Vector(
          VonMember("fileCoord", vonifyFileCoord(fileCoord)),
          VonMember("commentsRanges", VonArray(None, commentsRanges.map(vonifyRange).toVector)),
          VonMember("denizens", VonArray(None, denizens.map(vonifyDenizen).toVector))))
    })
  }

  def vonifyDenizen(denizenP: IDenizenP): VonObject = {
    denizenP match {
      case TopLevelFunctionP(function) => vonifyFunction(function)
      case TopLevelStructP(struct) => vonifyStruct(struct)
      case TopLevelInterfaceP(interface) => vonifyInterface(interface)
      case TopLevelImplP(impl) => vonifyImpl(impl)
      case TopLevelExportAsP(impl) => vonifyExportAs(impl)
      case TopLevelImportP(impl) => vonifyImport(impl)
    }
  }

  def vonifyGenericParameterType(thing: GenericParameterTypeP): VonObject = {
    val GenericParameterTypeP(range, tyype) = thing
    VonObject(
      "GenericParameterType",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("type", vonifyRuneType(tyype))))
  }

  def vonifyRuneAttribute(thing: IRuneAttributeP): VonObject = {
    thing match {
      case ReadOnlyRegionRuneAttributeP(range) => VonObject("ReadOnlyRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ReadWriteRegionRuneAttributeP(range) => VonObject("ReadWriteRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case AdditiveRegionRuneAttributeP(range) => VonObject("AdditiveRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ImmutableRegionRuneAttributeP(range) => VonObject("ImmutableRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case PoolRuneAttributeP(range) => VonObject("PoolRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ArenaRuneAttributeP(range) => VonObject("ArenaRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case BumpRuneAttributeP(range) => VonObject("BumpRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ImmutableRegionRuneAttributeP(range) => VonObject("ImmutableRegionRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ImmutableRuneAttributeP(range) => VonObject("ImmutableRuneAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case x => vimpl(x.toString)
    }
  }

  def vonifyStruct(thing: StructP): VonObject = {
    val StructP(range, name, attributes, mutability, identifyingRunes, templateRules, maybeDefaultRegionRuneP, bodyRange, members) = thing
    VonObject(
      "Struct",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyAttribute).toVector)),
        VonMember("mutability", vonifyOptional(mutability, vonifyTemplex)),
        VonMember("identifyingRunes", vonifyOptional(identifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("maybeDefaultRegion", vonifyOptional(maybeDefaultRegionRuneP, vonifyRegionRune)),
        VonMember("bodyRange", vonifyRange(range)),
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
      case sm @ NormalStructMemberP(_, _, _, _) => vonifyStructMember(sm)
      case sm @ VariadicStructMemberP(_, _, _) => vonifyVariadicStructMember(sm)
    }
  }

  def vonifyStructMember(thing: NormalStructMemberP) = {
    val NormalStructMemberP(range, name, variability, tyype) = thing
    VonObject(
      "NormalStructMember",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("variability", vonifyVariability(variability)),
        VonMember("type", vonifyTemplex(tyype))))
  }

  def vonifyVariadicStructMember(thing: VariadicStructMemberP) = {
    val VariadicStructMemberP(range, variability, tyype) = thing
    VonObject(
      "VariadicStructMember",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
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
    val InterfaceP(range, name, attributes, mutability, maybeIdentifyingRunes, templateRules, maybeDefaultRegionRuneP, bodyRange, members) = thing
    VonObject(
      "Interface",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyAttribute).toVector)),
        VonMember("mutability", vonifyOptional(mutability, vonifyTemplex)),
        VonMember("maybeIdentifyingRunes", vonifyOptional(maybeIdentifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("maybeDefaultRegion", vonifyOptional(maybeDefaultRegionRuneP, vonifyRegionRune)),
        VonMember("bodyRange", vonifyRange(range)),
        VonMember("members", VonArray(None, members.map(vonifyFunction).toVector))))
  }

  def vonifyImpl(impl: ImplP): VonObject = {
    val ImplP(range, identifyingRunes, templateRules, struct, interface, attributes) = impl

    VonObject(
      "Impl",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("identifyingRunes", vonifyOptional(identifyingRunes, vonifyIdentifyingRunes)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyAttribute).toVector)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("struct", vonifyOptional(struct, vonifyTemplex)),
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

  def vonifyFileCoord(range: FileCoordinate): VonObject = {
    val FileCoordinate(packageCoord, filepath) = range
    VonObject(
      "FileCoordinate",
      None,
      Vector(
        VonMember("packageCoord", vonifyPackageCoord(packageCoord)),
        VonMember("filepath", VonStr(filepath))))
  }

  def vonifyPackageCoord(range: PackageCoordinate): VonObject = {
    val PackageCoordinate(module, packages) = range
    VonObject(
      "PackageCoordinate",
      None,
      Vector(
        VonMember("module", VonStr(module.str)),
        VonMember("packages", VonArray(None, packages.map(p => VonStr(p.str))))))
  }

  def vonifyRange(range: RangeL): VonObject = {
    val RangeL(begin, end) = range
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
    val FunctionHeaderP(range, name, attributes, maybeUserSpecifiedIdentifyingRunes, templateRules, params, FunctionReturnP(retRange, retType)) = thing
    VonObject(
      "FunctionHeader",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyOptional(name, vonifyName)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyAttribute).toVector)),
        VonMember("maybeUserSpecifiedIdentifyingRunes", vonifyOptional(maybeUserSpecifiedIdentifyingRunes, vonifyIdentifyingRunes)),
        VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
        VonMember("params", vonifyOptional(params, vonifyParams)),
        VonMember(
          "return",
          VonObject(
            "FunctionReturn",
            None,
            Vector(
              VonMember("range", vonifyRange(retRange)),
              VonMember("retType", vonifyOptional(retType, vonifyTemplex)))))))
//        VonMember("maybeDefaultRegion", vonifyOptional(maybeDefaultRegion, vonifyName))))
  }

  def vonifyParams(thing: ParamsP): VonObject = {
    val ParamsP(range, patterns) = thing
    VonObject(
      "Params",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("params", VonArray(None, patterns.map(vonifyParameter)))))
  }

  def vonifyParameter(thing: ParameterP): VonObject = {
    val ParameterP(range, virtuality, maybePreChecked, selfBorrow, pattern) = thing
    VonObject(
      "Parameter",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("selfBorrow", vonifyOptional(selfBorrow, vonifyRange)),
        VonMember("maybePreChecked", vonifyOptional(maybePreChecked, vonifyRange)),
        VonMember("virtuality", vonifyOptional(virtuality, vonifyVirtuality)),
        VonMember("pattern", vonifyOptional(pattern, vonifyPattern))))
  }

  def vonifyPattern(thing: PatternPP): VonObject = {
    val PatternPP(range, capture, templex, destructure) = thing
    VonObject(
      "Pattern",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("capture", vonifyOptional(capture, vonifyDestinationLocal)),
        VonMember("templex", vonifyOptional(templex, vonifyTemplex)),
        VonMember("destructure", vonifyOptional(destructure, vonifyDestructure))))
  }

//  def vonifyCapture(thing: INameDeclarationP): VonObject = {
//    val INameDeclarationP(range, captureName) = thing
//    VonObject(
//      "Capture",
//      None,
//      Vector(
//        VonMember("range", vonifyRange(range)),
//        VonMember("captureName", vonifyCaptureName(captureName))))
//  }

  def vonifyDestinationLocal(thing: DestinationLocalP): VonObject = {
    val DestinationLocalP(decl, mutate) = thing
    VonObject(
      "DestinationLocal",
      None,
      Vector(
        VonMember("name", vonifyNameDeclaration(decl)),
        VonMember("mutate", vonifyOptional(mutate, vonifyRange))))
  }

  def vonifyNameDeclaration(thing: INameDeclarationP): VonObject = {
    thing match {
      case IgnoredLocalNameDeclarationP(range) => VonObject("IgnoredLocalNameDeclaration", None, Vector(VonMember("range", vonifyRange(range))))
      case LocalNameDeclarationP(name) => VonObject("LocalNameDeclaration", None, Vector(VonMember("name", vonifyName(name))))
      case ConstructingMemberNameDeclarationP(name) => VonObject("ConstructingMemberNameDeclaration", None, Vector(VonMember("name", vonifyName(name))))
      case IterableNameDeclarationP(range) => VonObject("IterableNameDeclaration", None, Vector(VonMember("range", vonifyRange(range))))
      case IteratorNameDeclarationP(range) => VonObject("IteratorNameDeclaration", None, Vector(VonMember("range", vonifyRange(range))))
      case IterationOptionNameDeclarationP(range) => VonObject("IterationOptionNameDeclaration", None, Vector(VonMember("range", vonifyRange(range))))
    }
  }

  def vonifyImpreciseName(thing: IImpreciseNameP): VonObject = {
    thing match {
      case LookupNameP(name) => VonObject("LookupName", None, Vector(VonMember("name", vonifyName(name))))
      case IterableNameP(range) => VonObject("IterableName", None, Vector(VonMember("range", vonifyRange(range))))
      case IteratorNameP(range) => VonObject("IteratorName", None, Vector(VonMember("range", vonifyRange(range))))
      case IterationOptionNameP(range) => VonObject("IterationOptionName", None, Vector(VonMember("range", vonifyRange(range))))
    }
  }

  def vonifyVariability(thing: VariabilityP): VonObject = {
    thing match {
      case FinalP => VonObject("Final", None, Vector())
      case VaryingP => VonObject("Varying", None, Vector())
    }
  }

  def vonifyVirtuality(thing: AbstractP): VonObject = {
//    thing match {
//      case AbstractP(range) => {
    val AbstractP(range) = thing
        VonObject(
          "Abstract",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
//      }
//      case ov @ OverrideP(_, _) => vonifyOverride(ov)
//    }
  }

//  def vonifyOverride(thing: OverrideP): VonObject = {
//    val OverrideP(range, tyype) = thing
//    VonObject(
//      "Override",
//      None,
//      Vector(
//        VonMember("range", vonifyRange(range)),
//        VonMember("type", vonifyTemplex(tyype))))
//  }

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

  def vonifyAttribute(thing: IAttributeP): VonObject = {
    thing match {
      case WeakableAttributeP(range) => VonObject("WeakableAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case SealedAttributeP(range) => VonObject("SealedAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case LinearAttributeP(range) => VonObject("LinearAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ExportAttributeP(range) => VonObject("ExportAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case MacroCallP(range, dontCall, name) => {
        VonObject(
          "MacroCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("dontCall", VonBool(dontCall == DontCallMacroP)),
            VonMember("name", vonifyName(name))))
      }
      case AbstractAttributeP(range) => VonObject("AbstractAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case ExternAttributeP(range) => VonObject("ExternAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case PureAttributeP(range) => VonObject("PureAttribute", None, Vector(VonMember("range", vonifyRange(range))))
      case AdditiveAttributeP(range) => VonObject("AdditiveAttribute", None, Vector(VonMember("range", vonifyRange(range))))
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
            VonMember("container", vonifyRuneType(container)),
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
      case BuiltinCallPR(range, name, args) => {
        VonObject(
          "BuiltinCallPR",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("name", vonifyName(name)),
            VonMember("args", VonArray(None, args.map(vonifyRule).toVector))
          )
        )
      }
//      case ResolveSignaturePR(range, nameStrRule, argsPackRule) => {
//        VonObject(
//          "ResolveSignaturePR",
//          None,
//          Vector(
//            VonMember("range", vonifyRange(range)),
//            VonMember("nameStrRule", vonifyRule(nameStrRule)),
//            VonMember("argsPackRule", vonifyRule(argsPackRule)),
//          )
//        )
//      }
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
      case VariabilityTypePR => VonObject("VariabilityTypePR", None, Vector())
      case LocationTypePR => VonObject("LocationTypePR", None, Vector())
      case CoordTypePR => VonObject("CoordTypePR", None, Vector())
      case CoordListTypePR => VonObject("CoordListTypePR", None, Vector())
      case PrototypeTypePR => VonObject("PrototypeTypePR", None, Vector())
      case KindTypePR => VonObject("KindTypePR", None, Vector())
      case RegionTypePR => VonObject("RegionTypePR", None, Vector())
      case CitizenTemplateTypePR => VonObject("CitizenTemplateTypePR", None, Vector())
    }
  }

  def vonifyIdentifyingRunes(thing: GenericParametersP): VonObject = {
    val GenericParametersP(range, identifyingRunesP) = thing
    VonObject(
      "IdentifyingRunes",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("identifyingRunes", VonArray(None, identifyingRunesP.map(vonifyGenericParameter).toVector))))
  }

  def vonifyGenericParameter(thing: GenericParameterP): VonObject = {
    val GenericParameterP(range, name, maybeType, maybeCoordRegion, attributes, maybeDefault) = thing
    VonObject(
      "IdentifyingRune",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyName(name)),
        VonMember("maybeType", vonifyOptional(maybeType, vonifyGenericParameterType)),
        VonMember("maybeCoordRegion", vonifyOptional(maybeCoordRegion, vonifyRegionRune)),
        VonMember("attributes", VonArray(None, attributes.map(vonifyRuneAttribute).toVector)),
        VonMember("maybeDefault", vonifyOptional(maybeDefault, vonifyTemplex))))
  }

  def vonifyName(thing: NameP): VonObject = {
    val NameP(range, name) = thing
    VonObject(
      "Name",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", VonStr(name.str))))
  }

  def vonifyTemplex(thing: ITemplexPT): VonObject = {
    thing match {
      case r @ RegionRunePT(_, _) => {
        vonifyRegionRune(r)
      }
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
      case TuplePT(range, members) => {
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
      case InterpretedPT(range, maybeOwnership, maybeRegion, inner) => {
        VonObject(
          "InterpretedT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("maybeOwnership", vonifyOptional(maybeOwnership, vonifyTemplex)),
            VonMember("maybeRegion", vonifyOptional(maybeRegion, vonifyRegionRune)),
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
      case StaticSizedArrayPT(range, mutability, variability, size, element) => {
        VonObject(
          "StaticSizedArrayT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyTemplex(mutability)),
            VonMember("variability", vonifyTemplex(variability)),
            VonMember("size", vonifyTemplex(size)),
            VonMember("element", vonifyTemplex(element))))
      }
      case RuntimeSizedArrayPT(range, mutability, element) => {
        VonObject(
          "RuntimeSizedArrayT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyTemplex(mutability)),
            VonMember("element", vonifyTemplex(element))))
      }
      case FunctionPT(range, mutability, parameters, returnType) => {
        VonObject(
          "FunctionT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("mutability", vonifyOptional(mutability, vonifyTemplex)),
            VonMember("params", vonifyTemplex(parameters)),
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
      case FuncPT(range, name, paramsRange, parameters, returnType) => {
        VonObject(
          "PrototypeT",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("name", vonifyName(name)),
            VonMember("paramsRange", vonifyRange(paramsRange)),
            VonMember("params", VonArray(None, parameters.map(vonifyTemplex).toVector)),
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

  private def vonifyRegionRune(regionRune: RegionRunePT): VonObject = {
    val RegionRunePT(range, name) = regionRune
    VonObject(
      "RegionRuneT",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("name", vonifyOptional(name, x => vonifyName(x)))))
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
      case BorrowP => VonObject("Borrow", None, Vector())
      case WeakP => VonObject("Weak", None, Vector())
    }
  }

  def vonifyLoadAs(thing: LoadAsP): VonObject = {
    thing match {
      case UseP => VonObject("Use", None, Vector())
      case MoveP => VonObject("Move", None, Vector())
      case LoadAsBorrowP => VonObject("LoadAsBorrow", None, Vector())
      case LoadAsWeakP => VonObject("LoadAsWeak", None, Vector())
    }
  }

  def vonifyBlock(thing: BlockPE): VonObject = {
    val BlockPE(range, maybePure, maybeDefaultRegion, inner) = thing
    VonObject(
      "Block",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("maybePure", vonifyOptional(maybePure, vonifyRange)),
        VonMember("maybeDefaultRegion", vonifyOptional(maybeDefaultRegion, vonifyRegionRune)),
        VonMember("inner", vonifyExpression(inner))))
  }

  def vonifyConsecutor(thing: ConsecutorPE): VonObject = {
    val ConsecutorPE(inners) = thing
    VonObject(
      "Consecutor",
      None,
      Vector(
        VonMember("inners", VonArray(None, inners.map(vonifyExpression).toVector))))
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
      case DotPE(range, left, operatorRange, member) => {
        VonObject(
          "Dot",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("left", vonifyExpression(left)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
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
      case NotPE(range, inner) => {
        VonObject(
          "Not",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("innerExpr", vonifyExpression(inner))))
      }
      case RangePE(range, begin, end) => {
        VonObject(
          "Range",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("begin", vonifyExpression(begin)),
            VonMember("end", vonifyExpression(end))))
      }
      case FunctionCallPE(range, operatorRange, callableExpr, argExprs) => {
        VonObject(
          "FunctionCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("callableExpr", vonifyExpression(callableExpr)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector))))
      }
      case BraceCallPE(range, operatorRange, callableExpr, argExprs, callableReadwrite) => {
        VonObject(
          "BraceCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("callableExpr", vonifyExpression(callableExpr)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector)),
            VonMember("callableReadwrite", VonBool(callableReadwrite))))
      }
      case BinaryCallPE(range, name, leftExpr, rightExpr) => {
        VonObject(
          "BinaryCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("functionName", vonifyName(name)),
            VonMember("leftExpr", vonifyExpression(leftExpr)),
            VonMember("rightExpr", vonifyExpression(rightExpr))))
      }
      case SubExpressionPE(range, inner) => {
        VonObject(
          "SubExpression",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("innerExpr", vonifyExpression(inner))))
      }
      case EachPE(range, maybePure, entryPattern, inRange, iterableExpr, body) => {
        VonObject(
          "Each",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("maybePure", vonifyOptional(maybePure, vonifyRange)),
            VonMember("entryPattern", vonifyPattern(entryPattern)),
            VonMember("inRange", vonifyRange(inRange)),
            VonMember("iterableExpr", vonifyExpression(iterableExpr)),
            VonMember("body", vonifyBlock(body))))
      }
      case PackPE(range, innersPE) => {
        VonObject(
          "Pack",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("innerExprs", VonArray(None, innersPE.map(vonifyExpression).toVector))))
      }
      case MethodCallPE(range, subjectExpr, operatorRange, methodLookup, argExprs) => {
        VonObject(
          "MethodCall",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("operatorRange", vonifyRange(operatorRange)),
            VonMember("subjectExpr", vonifyExpression(subjectExpr)),
            VonMember("method", vonifyExpression(methodLookup)),
            VonMember("argExprs", VonArray(None, argExprs.map(vonifyExpression).toVector))))
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
//      case ResultPE(range, source) => {
//        VonObject(
//          "Result",
//          None,
//          Vector(
//            VonMember("range", vonifyRange(range)),
//            VonMember("source", vonifyExpression(source))))
//      }
      case ConstantIntPE(range, value, bits) => {
        VonObject(
          "ConstantInt",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("value", VonStr(value.toString)),
            VonMember("bits", vonifyOptional(bits, VonInt(_)))))
      }
      case AugmentPE(range, targetOwnership, inner) => {
        VonObject(
          "Augment",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("targetOwnership", vonifyOwnership(targetOwnership)),
            VonMember("inner", vonifyExpression(inner))))
      }
      case TransmigratePE(range, targetRegion, inner) => {
        VonObject(
          "Transmigrate",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("targetRegion", vonifyName(targetRegion)),
            VonMember("inner", vonifyExpression(inner))))
      }
      case LetPE(range, pattern, source) => {
        VonObject(
          "Let",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
//            VonMember("templateRules", vonifyOptional(templateRules, vonifyTemplateRules)),
            VonMember("pattern", vonifyPattern(pattern)),
            VonMember("source", vonifyExpression(source))))
      }
//      case BadLetPE(range) => {
//        VonObject(
//          "BadLet",
//          None,
//          Vector(
//            VonMember("range", vonifyRange(range))))
//      }
      case LookupPE(name, templateArgs) => {
        VonObject(
          "Lookup",
          None,
          Vector(
            VonMember("name", vonifyImpreciseName(name)),
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
      case BreakPE(range) => {
        VonObject(
          "Break",
          None,
          Vector(
            VonMember("range", vonifyRange(range))))
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
      case b @ BlockPE(_, _, _, _) => {
        vonifyBlock(b)
      }
      case c @ ConsecutorPE(_) => {
        vonifyConsecutor(c)
      }
      case DestructPE(range, inner) => {
        VonObject(
          "Destruct",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("inner", vonifyExpression(inner))))
      }
      case UnletPE(range, inner) => {
        VonObject(
          "Unlet",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("localName", vonifyImpreciseName(inner))))
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
//      case MatchPE(range, condition, lambdas) => {
//        VonObject(
//          "Match",
//          None,
//          Vector(
//            VonMember("range", vonifyRange(range)),
//            VonMember("condition", vonifyExpression(condition)),
//            VonMember("lambdas", VonArray(None, lambdas.map(vonifyExpression).toVector))))
//      }
      case TuplePE(range, elements) => {
        VonObject(
          "Tuple",
          None,
          Vector(
            VonMember("range", vonifyRange(range)),
            VonMember("elements", VonArray(None, elements.map(vonifyExpression).toVector))))
      }
      case ca @ ConstructArrayPE(range, tyype, mutability, variability, size, initializingIndividualElements, args) => {
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
            VonMember("condition", vonifyExpression(condition)),
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
    val ConstructArrayPE(range, tyype, mutability, variability, size, initializingIndividualElements, args) = ca

    VonObject(
      "ConstructArray",
      None,
      Vector(
        VonMember("range", vonifyRange(range)),
        VonMember("type", vonifyOptional(tyype, vonifyTemplex)),
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
}
