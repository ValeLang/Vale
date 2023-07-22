package dev.vale.postparsing

import dev.vale.{CodeLocationS, FileCoordinateMap, RangeS, vimpl}
import dev.vale.postparsing.rules._
import dev.vale.solver.SolverErrorHumanizer
import dev.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}
import dev.vale.parsing.ast.{BorrowP, FinalP, ImmutableP, MutabilityP, MutableP, OwnP, OwnershipP, ShareP, VariabilityP, VaryingP, WeakP}
import dev.vale.parsing.ast._
import dev.vale.postparsing.rules._

object PostParserErrorHumanizer {
  def humanize(
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    err: ICompileErrorS):
  String = {
    val errorStrBody =
      (err match {
        case RuneExplicitTypeConflictS(range, rune, types) => "Too many explicit types for rune " + humanizeRune(rune) + "" + types.map(humanizeTemplataType).mkString(", ")
        case RangedInternalErrorS(range, message) => " " + message
        case UnknownRuleFunctionS(range, name) => "Unknown rule function name: "+ name
        case UnknownRegionError(range, name) => "Unknown region: " + name
        case UnimplementedExpression(range, expressionName) => s"${expressionName} not supported yet.\n"
        case CouldntFindRuneS(range, name) => "Couldn't find generic parameter \"" + name + "\".\n"
        case CouldntFindVarToMutateS(range, name) => s"No variable named ${name}. Try declaring it above, like `${name} = 42;`\n"
        case CantOwnershipInterfaceInImpl(range) => s"Can only impl a plain interface, remove symbol."
        case CantOwnershipStructInImpl(range) => s"Only a plain struct/interface can be in an impl, remove symbol."
        case CantOverrideOwnershipped(range) => s"Can only impl a plain interface, remove symbol."
        case BadRuneAttributeErrorS(range, attr) => "Bad rune attribute: " + attr
        case CouldntSolveRulesS(range, error) => {
          s"Couldn't solve:\n" +
          SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, ITemplataType, IRuneTypeRuleError](
            codeMap,
            linesBetween,
            lineRangeContaining,
            lineContaining,
            PostParserErrorHumanizer.humanizeRune,
            (tyype: ITemplataType) => tyype.toString,
            (u: IRuneTypeRuleError) => humanizeRuneTypeError(codeMap, u),
            (rule: IRulexSR) => rule.range,
            (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
            (rule: IRulexSR) => rule.runeUsages.map(_.rune),
            PostParserErrorHumanizer.humanizeRule,
            error.failedSolve)._1
        }
        case IdentifyingRunesIncompleteS(range, error) => {
          s"Not enough identifying runes:\n" +
            SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, Boolean, IIdentifiabilityRuleError](
              codeMap,
              linesBetween,
              lineRangeContaining,
              lineContaining,
              PostParserErrorHumanizer.humanizeRune,
              (identified: Boolean) => identified.toString,
              (u: IIdentifiabilityRuleError) => humanizeIdentifiabilityRuleErrorr(codeMap, u),
              (rule: IRulexSR) => rule.range,
              (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
              (rule: IRulexSR) => rule.runeUsages.map(_.rune),
              PostParserErrorHumanizer.humanizeRule,
              error.failedSolve)._1
        }
        case VariableNameAlreadyExists(range, name) => s"Local named " + humanizeName(name) + " already exists!\n(If you meant to modify the variable, use the `set` keyword beforehand.)"
        case InterfaceMethodNeedsSelf(range) => s"Interface's method needs a virtual param of interface's type!"
        case ForgotSetKeywordError(range) => s"Changing a struct's member must start with the `set` keyword."
        case ExternHasBody(range) => s"Extern function can't have a body too."
//        case CantInitializeIndividualElementsOfRuntimeSizedArray(range) => s"Can't initialize individual elements of a runtime-sized array."
        case InitializingRuntimeSizedArrayRequiresSizeAndCallable(range) => s"Initializing a runtime-sized array requires 1-2 arguments: a capacity, and optionally a function that will populate that many elements."
        case InitializingStaticSizedArrayRequiresSizeAndCallable(range) => s"Initializing a statically-sized array requires one argument: a function that will populate the elements."
//        case InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(range) => s"Initializing a statically-sized array requires a size in-between the square brackets."
      })

    val posStr = codeMap(err.range.begin)
    val nextStuff = lineContaining(err.range.begin)
    val errorId = "S"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }

  def humanizeRuneTypeError(
    codeMap: CodeLocationS => String,
    error: IRuneTypeRuleError):
  String = {
    error match {
      case FoundTemplataDidntMatchExpectedType(range, expectedType, actualType) => {
        "Expected " + humanizeTemplataType(expectedType) + " but found " + humanizeTemplataType(actualType)
      }
      case RuneTypingCouldntFindType(range, name) => {
        "Couldn't find anything with the name '" + humanizeImpreciseName(name) + "'"
      }
      case NotEnoughArgumentsForGenericCall(range, indexOfNonDefaultingParam) => {
        "Not enough arguments for generic call, expected at least " + (indexOfNonDefaultingParam + 1)
      }
      case FoundPrimitiveDidntMatchExpectedType(range, expectedType, actualType) => {
        "Found primitive didnt match expected type. Expected " + humanizeTemplataType(expectedType) + " but was " + humanizeTemplataType(actualType)
      }
      case other => vimpl(other)
    }
  }

  def humanizeIdentifiabilityRuleErrorr(
    codeMap: CodeLocationS => String,
    error: IIdentifiabilityRuleError):
  String = {
    error match {
      case other => vimpl(other)
    }
  }

  def humanizeName(name: INameS): String = {
    name match {
//      case UnnamedLocalNameS(codeLocation) => "(unnamed)"
      case ClosureParamNameS(_) => "(closure)"
//      case FreeDeclarationNameS(_) => "(free)"
//      case CodeNameS(n) => n
      case GlobalFunctionFamilyNameS(n) => n
//      case DropNameS(_) => "(drop)"
      case MagicParamNameS(codeLocation) => "(magic)"
      case CodeVarNameS(name) => name.str
      case ArbitraryNameS() => "(arbitrary)"
      case RuneNameS(rune) => humanizeRune(rune)
      case ConstructingMemberNameS(name) => "member " + name
      case FunctionNameS(name, codeLocation) => name.str
      case AnonymousSubstructTemplateNameS(tlcd) => humanizeName(tlcd) + ".anonymous"
      case TopLevelCitizenDeclarationNameS(name, range) => name.str
      case RuntimeSizedArrayDeclarationNameS() => "__rsa"
      case ImplDeclarationNameS(_) => "(impl)"
    }
  }

  def humanizeImpreciseName(name: IImpreciseNameS): String = {
    name match {
      case ArbitraryNameS() => "_arby"
      case SelfNameS() => "_Self"
      case CodeNameS(n) => n.str
//      case FreeImpreciseNameS() => "_Free"
      case RuneNameS(rune) => humanizeRune(rune)
      case AnonymousSubstructTemplateImpreciseNameS(interfaceHumanName) => humanizeImpreciseName(interfaceHumanName) + "._AnonSub"
      case LambdaStructImpreciseNameS(lambdaName) => humanizeImpreciseName(lambdaName) + ".struct"
      case LambdaImpreciseNameS() => "_Lam"
//      case VirtualFreeImpreciseNameS() => "(abstract virtual free)"
//      case VirtualFreeImpreciseNameS() => "(override virtual free)"
    }
  }

  def humanizeRune(rune: IRuneS): String = {
    rune match {
      case ImplicitRuneS(lid) => "_" + lid.path.mkString("")
      case MagicParamRuneS(lid) => "_" + lid.path.mkString("")
      case CodeRuneS(name) => name.str
      case ArgumentRuneS(paramIndex) => "(arg " + paramIndex + ")"
      case SelfKindRuneS() => "(self kind)"
      case SelfOwnershipRuneS() => "(self ownership)"
      case SelfKindTemplateRuneS(_) => "(self kind template)"
      case PatternInputRuneS(codeLoc) => "(pattern input " + codeLoc + ")"
      case SelfRuneS() => "(self)"
      case SelfCoordRuneS() => "(self ref)"
      case ReturnRuneS() => "(ret)"
      case AnonymousSubstructParentInterfaceTemplateRuneS() => "(anon sub parent interface)"
      case ImplDropVoidRuneS() => "(impl drop void)"
      case ImplDropCoordRuneS() => "(impl drop coord)"
      case FreeOverrideInterfaceRuneS() => "(freeing interface)"
      case FreeOverrideStructRuneS() => "(freeing struct)"
      case AnonymousSubstructKindRuneS() => "(anon substruct kind)"
      case AnonymousSubstructCoordRuneS() => "(anon substruct ref)"
      case AnonymousSubstructTemplateRuneS() => "(anon substruct template)"
      case AnonymousSubstructParentInterfaceTemplateRuneS() => "(anon sub parent template)"
      case AnonymousSubstructParentInterfaceKindRuneS() => "(anon sub parent kind)"
      case AnonymousSubstructParentInterfaceCoordRuneS() => "(anon sub parent ref)"
      case StructNameRuneS(inner) => humanizeName(inner)
      case FreeOverrideStructTemplateRuneS() => "(free override template)"
      case FunctorPrototypeRuneNameS() => "(functor prototype)"
      case MacroSelfKindRuneS() => "_MSelfK"
      case MacroSelfCoordRuneS() => "_MSelf"
      case MacroVoidKindRuneS() => "_MVoidK"
      case MacroVoidCoordRuneS() => "_MVoid"
      case MacroSelfKindTemplateRuneS() => "_MSelfKT"
      case AnonymousSubstructMemberRuneS(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".functor"
      case AnonymousSubstructFunctionBoundParamsListRuneS(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".params"
      case AnonymousSubstructFunctionBoundPrototypeRuneS(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".proto"
      case AnonymousSubstructFunctionInterfaceTemplateRune(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".itemplate"
     case AnonymousSubstructFunctionInterfaceKindRune(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".ikind"
//      case AnonymousSubstructFunctionInterfaceOwnershipRune(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".iown"
      case AnonymousSubstructDropBoundParamsListRuneS(interface, method) => "$" + humanizeName(interface) + ".anon.drop.params"
      case AnonymousSubstructDropBoundPrototypeRuneS(interface, method) => "$" + humanizeName(interface) + ".anon.drop.proto"
      case AnonymousSubstructMethodInheritedRuneS(interface, method, inner) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ":" + humanizeRune(inner)
      case AnonymousSubstructMethodSelfOwnCoordRuneS(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".ownself"
      case AnonymousSubstructMethodSelfBorrowCoordRuneS(interface, method) => "$" + humanizeName(interface) + ".anon." + humanizeName(method) + ".borrowself"
      case DenizenDefaultRegionRuneS(denizenName) => humanizeName(denizenName) + "'"
      case ExternDefaultRegionRuneS(denizenName) => humanizeName(denizenName) + "'"
      case AnonymousSubstructVoidKindRuneS() => "anon.void.kind"
      case AnonymousSubstructVoidCoordRuneS() => "anon.void"
      case ImplicitCoercionOwnershipRuneS(_, inner) => humanizeRune(inner) + ".own"
      case ImplicitCoercionKindRuneS(_, inner) => humanizeRune(inner) + ".kind"
      case ImplicitCoercionTemplateRuneS(_, inner) => humanizeRune(inner) + ".gen"
      case ImplicitRegionRuneS(originalRune) => humanizeRune(originalRune) + ".region"
      case CallRegionRuneS(lid) => "_" + lid.path.mkString("") + ".pcall"
      case other => vimpl(other)
    }
  }

  def humanizeTemplataType(tyype: ITemplataType): String = {
    tyype match {
      case KindTemplataType() => "Kind"
      case CoordTemplataType() => "Type"
      case FunctionTemplataType() => "Func"
      case IntegerTemplataType() => "Int"
      case RegionTemplataType() => "Region"
      case BooleanTemplataType() => "Bool"
      case MutabilityTemplataType() => "Mut"
      case PrototypeTemplataType() => "Prot"
      case StringTemplataType() => "Str"
      case LocationTemplataType() => "Loc"
      case OwnershipTemplataType() => "Own"
      case VariabilityTemplataType() => "Vary"
      case PackTemplataType(elementType) => "Pack<" + humanizeTemplataType(elementType) + ">"
      case TemplateTemplataType(params, ret) => humanizeTemplataType(ret) + "<" + params.map(humanizeTemplataType).mkString(",") + ">"
    }
  }

  def humanizeRule(rule: IRulexSR): String = {
    rule match {
      case KindComponentsSR(range, kindRune, mutabilityRune) => {
        humanizeRune(kindRune.rune) + " = Kind[" + humanizeRune(mutabilityRune.rune) + "]"
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, kindRune) => {
        humanizeRune(resultRune.rune) + " = Ref[" + humanizeRune(ownershipRune.rune) + ", " + humanizeRune(kindRune.rune) + "]"
      }
      case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) => {
        humanizeRune(resultRune.rune) + " = Prot[" + humanizeRune(paramsRune.rune) + ", " + humanizeRune(returnRune.rune) + "]"
      }
      case OneOfSR(range, resultRune, literals) => {
        humanizeRune(resultRune.rune) + " = " + literals.map(_.toString).mkString(" | ")
      }
      case IsInterfaceSR(range, resultRune) => "isInterface(" + humanizeRune(resultRune.rune) + ")"
      case IsStructSR(range, resultRune) => "isStruct(" + humanizeRune(resultRune.rune) + ")"
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => humanizeRune(resultRune.rune) + " = refListCompoundMutability(" + humanizeRune(coordListRune.rune) + ")"
      case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) => humanizeRune(resultRune.rune) + " = " + humanizeRune(subRune.rune) + " def-isa " + humanizeRune(superRune.rune)
      case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) => resultRune.map(r => humanizeRune(r.rune)).getOrElse("_") + " = " + humanizeRune(subRune.rune) + " call-isa " + humanizeRune(superRune.rune)
      case CoordSendSR(range, senderRune, receiverRune) => humanizeRune(senderRune.rune) + " -> " + humanizeRune(receiverRune.rune)
      case CoerceToCoordSR(range, coordRune, kindRune) => "coerceToCoord(" + humanizeRune(coordRune.rune) + ", " + humanizeRune(kindRune.rune) + ")"
      case MaybeCoercingCallSR(range, resultRune, templateRune, argRunes) => humanizeRune(resultRune.rune) + " = " + humanizeRune(templateRune.rune) + "<" + argRunes.map(_.rune).map(humanizeRune).mkString(", ") + ">"
      case MaybeCoercingLookupSR(range, rune, name) => humanizeRune(rune.rune) + " = " + "\"" + humanizeImpreciseName(name) + "\""
      case CallSR(range, resultRune, templateRune, argRunes) => humanizeRune(resultRune.rune) + " = " + humanizeRune(templateRune.rune) + "<" + argRunes.map(_.rune).map(humanizeRune).mkString(", ") + ">"
      case LookupSR(range, rune, name) => humanizeRune(rune.rune) + " = \"" + humanizeImpreciseName(name) + "\""
      case LiteralSR(range, rune, literal) => humanizeRune(rune.rune) + " = " + humanizeLiteral(literal)
      case AugmentSR(range, resultRune, ownership, innerRune) => humanizeRune(resultRune.rune) + " = " + ownership.map(humanizeOwnership).getOrElse("") + humanizeRune(innerRune.rune)
      case EqualsSR(range, left, right) => humanizeRune(left.rune) + " = " + humanizeRune(right.rune)
      case RuneParentEnvLookupSR(range, rune) => "inherit " + humanizeRune(rune.rune)
      case PackSR(range, resultRune, members) => humanizeRune(resultRune.rune) + " = (" + members.map(x => humanizeRune(x.rune)).mkString(", ") + ")"
      case ResolveSR(range, resultRune, name, paramsListRune, returnRune) => {
        humanizeRune(resultRune.rune) + " = resolve-func " + name + "(" + humanizeRune(paramsListRune.rune) + ")" + humanizeRune(returnRune.rune)
      }
      case CallSiteFuncSR(range, resultRune, name, paramsListRune, returnRune) => {
        humanizeRune(resultRune.rune) + " = callsite-func " + name + "(" + humanizeRune(paramsListRune.rune) + ")" + humanizeRune(returnRune.rune)
      }
      case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) => {
        humanizeRune(resultRune.rune) + " = definition-func " + name + "(" + humanizeRune(paramsListRune.rune) + ")" + humanizeRune(returnRune.rune)
      }
//      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
//        humanizeRune(resultRune.rune) + " = " + "[#" + humanizeRune(sizeRune.rune) + "]<" + humanizeRune(mutabilityRune.rune) + ", " + humanizeRune(variabilityRune.rune) + ">" + humanizeRune(elementRune.rune)
//      }
//      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => {
//        humanizeRune(resultRune.rune) + " = " + "[]<" + humanizeRune(mutabilityRune.rune) + ">" + humanizeRune(elementRune.rune)
//      }
      case other => vimpl(other)
    }
  }

  def humanizeLiteral(literal: ILiteralSL): String = {
    literal match {
      case OwnershipLiteralSL(ownership) => humanizeOwnership(ownership)
      case MutabilityLiteralSL(mutability) => humanizeMutability(mutability)
      case VariabilityLiteralSL(variability) => humanizeVariability(variability)
      case IntLiteralSL(value) => value.toString
      case StringLiteralSL(value) => "\"" + value + "\""
      case other => vimpl(other)
    }
  }

  def humanizeMutability(p: MutabilityP) = {
    p match {
      case MutableP => "mut"
      case ImmutableP => "imm"
    }
  }

  def humanizeVariability(p: VariabilityP) = {
    p match {
      case VaryingP => "vary"
      case FinalP => "final"
    }
  }

  def humanizeOwnership(p: OwnershipP) = {
    p match {
      case OwnP => "^"
      case ShareP => "@"
      case BorrowP => "&"
      case WeakP => "&&"
    }
  }

  def humanizeRegion(r: RuneUsage) = {
    vimpl(r)
  }
}
