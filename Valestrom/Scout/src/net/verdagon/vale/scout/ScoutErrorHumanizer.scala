package net.verdagon.vale.scout

import net.verdagon.vale.{FileCoordinateMap, vimpl}
import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining, nextThingAndRestOfLine}
import net.verdagon.vale.parser.ast.{BorrowP, ExclusiveReadwriteP, FinalP, ImmutableP, MutabilityP, MutableP, OwnP, OwnershipP, PermissionP, PointerP, ReadonlyP, ReadwriteP, ShareP, VariabilityP, VaryingP, WeakP}
import net.verdagon.vale.scout.rules.{AugmentSR, CallSR, CoerceToCoordSR, CoordComponentsSR, CoordIsaSR, CoordSendSR, EqualsSR, ILiteralSL, IRulexSR, IntLiteralSL, IsInterfaceSR, IsStructSR, KindComponentsSR, LiteralSR, LookupSR, MutabilityLiteralSL, OneOfSR, OwnershipLiteralSL, PackSR, PermissionLiteralSL, PrototypeComponentsSR, RefListCompoundMutabilitySR, RuneParentEnvLookupSR, RuntimeSizedArraySR, StaticSizedArraySR, StringLiteralSL, VariabilityLiteralSL}
import net.verdagon.vale.solver.SolverErrorHumanizer

object ScoutErrorHumanizer {
  def humanize(
    codeMap: FileCoordinateMap[String],
    err: ICompileErrorS):
  String = {
    val errorStrBody =
      (err match {
        case RangedInternalErrorS(range, message) => " " + message
        case UnimplementedExpression(range, expressionName) => s": ${expressionName} not supported yet.\n"
        case CouldntFindVarToMutateS(range, name) => s": No variable named ${name}. Try declaring it above, like `${name} = 42;`\n"
        case CantOwnershipInterfaceInImpl(range) => s": Can only impl a plain interface, remove symbol."
        case CantOwnershipStructInImpl(range) => s": Only a plain struct/interface can be in an impl, remove symbol."
        case CantOverrideOwnershipped(range) => s": Can only impl a plain interface, remove symbol."
        case CouldntSolveRulesS(range, error) => {
          s": Couldn't solve:\n" +
          SolverErrorHumanizer.humanizeFailedSolve(
            codeMap,
            ScoutErrorHumanizer.humanizeRune,
            (codeMap, tyype: ITemplataType) => tyype.toString,
            (codeMap, u: IRuneTypeRuleError) => humanizeRuneTypeError(codeMap, u),
            (rule: IRulexSR) => rule.range,
            (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
            (rule: IRulexSR) => rule.runeUsages.map(_.rune),
            ScoutErrorHumanizer.humanizeRule,
            error.failedSolve)._1
        }
        case IdentifyingRunesIncompleteS(range, error) => {
          s": Not enough identifying runes:\n" +
            SolverErrorHumanizer.humanizeFailedSolve(
              codeMap,
              ScoutErrorHumanizer.humanizeRune,
              (codeMap, identified: Boolean) => identified.toString,
              (codeMap, u: IIdentifiabilityRuleError) => humanizeIdentifiabilityRuleErrorr(codeMap, u),
              (rule: IRulexSR) => rule.range,
              (rule: IRulexSR) => rule.runeUsages.map(u => (u.rune, u.range)),
              (rule: IRulexSR) => rule.runeUsages.map(_.rune),
              ScoutErrorHumanizer.humanizeRule,
              error.failedSolve)._1
        }
        case VariableNameAlreadyExists(range, name) => s": Local named " + humanizeName(name) + " already exists!\n(If you meant to modify the variable, use the `set` keyword beforehand.)"
        case InterfaceMethodNeedsSelf(range) => s": Interface's method needs a virtual param of interface's type!"
        case LightFunctionMustHaveParamTypes(range, paramIndex) => s": Function parameter must have a type!"
        case ForgotSetKeywordError(range) => s": Changing a struct's member must start with the `set` keyword."
        case CantUseThatLocalName(range, name) => s": Can't use the name ${name} for a local."
        case ExternHasBody(range) => s": Extern function can't have a body too."
        case CantInitializeIndividualElementsOfRuntimeSizedArray(range) => s": Can't initialize individual elements of a runtime-sized array."
        case InitializingRuntimeSizedArrayRequiresSizeAndCallable(range) => s": Initializing a runtime-sized array requires 1-2 arguments: a capacity, and optionally a function that will populate that many elements."
        case InitializingStaticSizedArrayRequiresSizeAndCallable(range) => s": Initializing a statically-sized array requires one argument: a function that will populate the elements."
        case InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(range) => s": Initializing a statically-sized array requires a size in-between the square brackets."
      })

    val posStr = humanizePos(codeMap, err.range.begin)
    val nextStuff = lineContaining(codeMap, err.range.begin)
    val errorId = "S"
    f"${posStr} error ${errorId}: ${errorStrBody}\n${nextStuff}\n"
  }

  def humanizeRuneTypeError(codeMap: FileCoordinateMap[String], error: IRuneTypeRuleError): String = {
    error match {
      case other => vimpl(other)
    }
  }

  def humanizeIdentifiabilityRuleErrorr(codeMap: FileCoordinateMap[String], error: IIdentifiabilityRuleError): String = {
    error match {
      case other => vimpl(other)
    }
  }

  def humanizeName(name: INameS): String = {
    name match {
//      case UnnamedLocalNameS(codeLocation) => "(unnamed)"
      case ClosureParamNameS() => "(closure)"
      case FreeDeclarationNameS(_) => "(free)"
//      case CodeNameS(n) => n
      case GlobalFunctionFamilyNameS(n) => n
//      case DropNameS(_) => "(drop)"
      case MagicParamNameS(codeLocation) => "(magic)"
      case CodeVarNameS(name) => name
      case ArbitraryNameS() => "(arbitrary)"
      case RuneNameS(rune) => humanizeRune(rune)
      case ConstructingMemberNameS(name) => "member " + name
      case FunctionNameS(name, codeLocation) => name
      case AnonymousSubstructTemplateNameS(tlcd) => humanizeName(tlcd) + ".anonymous"
      case TopLevelCitizenDeclarationNameS(name, range) => name
    }
  }

  def humanizeImpreciseName(name: IImpreciseNameS): String = {
    name match {
      case CodeNameS(n) => n
      case FreeImpreciseNameS() => "(free)"
      case RuneNameS(rune) => humanizeRune(rune)
      case AnonymousSubstructTemplateImpreciseNameS(interfaceHumanName) => "(anon substruct template of " + humanizeImpreciseName(interfaceHumanName) + ")"
      case LambdaStructImpreciseNameS(lambdaName) => humanizeImpreciseName(lambdaName) + ".struct"
      case LambdaImpreciseNameS() => "(lambda)"
      case VirtualFreeImpreciseNameS() => "(abstract virtual free)"
      case VirtualFreeImpreciseNameS() => "(override virtual free)"
    }
  }

  def humanizeRune(rune: IRuneS): String = {
    rune match {
      case ImplicitRuneS(lid) => "_" + lid.path.mkString("")
      case MagicParamRuneS(lid) => "_" + lid.path.mkString("")
      case CodeRuneS(name) => name
      case ArgumentRuneS(paramIndex) => "(arg " + paramIndex + ")"
      case AnonymousSubstructMemberRuneS(index) => "(anon member " + index + ")"
      case SelfKindRuneS() => "(self kind)"
      case SelfOwnershipRuneS() => "(self ownership)"
      case SelfPermissionRuneS() => "(self permission)"
      case SelfKindTemplateRuneS() => "(self kind template)"
      case PatternInputRuneS(codeLoc) => "(pattern input " + codeLoc + ")"
      case SelfRuneS() => "(self)"
      case ReturnRuneS() => "(return)"
      case AnonymousSubstructParentInterfaceTemplateRuneS() => "(anon sub parent interface)"
      case ImplDropVoidRuneS() => "(impl drop void)"
      case ImplDropCoordRuneS() => "(impl drop coord)"
      case FreeOverrideInterfaceRuneS() => "(freeing interface)"
      case FreeOverrideStructRuneS() => "(freeing struct)"
      case AnonymousSubstructRuneS() => "(anon substruct)"
      case AnonymousSubstructTemplateRuneS() => "(anon substruct template)"
      case AnonymousSubstructParentInterfaceTemplateRuneS() => "(anon sub parent template)"
      case AnonymousSubstructParentInterfaceRuneS() => "(anon sub parent)"
      case StructNameRuneS(inner) => humanizeName(inner)
      case FreeOverrideStructTemplateRuneS() => "(free override template)"
      case other => vimpl(other)
    }
  }

  def humanizeTemplataType(tyype: ITemplataType): String = {
    tyype match {
      case KindTemplataType => "kind"
      case CoordTemplataType => "type"
      case FunctionTemplataType => "func"
      case IntegerTemplataType => "int"
      case BooleanTemplataType => "bool"
      case MutabilityTemplataType => "mut"
      case PrototypeTemplataType => "prot"
      case StringTemplataType => "str"
      case PermissionTemplataType => "perm"
      case LocationTemplataType => "loc"
      case OwnershipTemplataType => "own"
      case VariabilityTemplataType => "vary"
      case PackTemplataType(elementType) => "pack<" + humanizeTemplataType(elementType) + ">"
      case TemplateTemplataType(params, ret) => humanizeTemplataType(ret) + "<" + params.map(humanizeTemplataType).mkString(",") + ">"
    }
  }

  def humanizeRule(rule: IRulexSR): String = {
    rule match {
      case KindComponentsSR(range, kindRune, mutabilityRune) => {
        humanizeRune(kindRune.rune) + " = Kind(" + humanizeRune(mutabilityRune.rune) + ")"
      }
      case CoordComponentsSR(range, resultRune, ownershipRune, permissionRune, kindRune) => {
        humanizeRune(resultRune.rune) + " = Ref(" + humanizeRune(ownershipRune.rune) + ", " + humanizeRune(permissionRune.rune) + ", " + humanizeRune(kindRune.rune) + ")"
      }
      case OneOfSR(range, resultRune, literals) => {
        humanizeRune(resultRune.rune) + " = " + literals.map(_.toString).mkString(" | ")
      }
      case IsInterfaceSR(range, resultRune) => "isInterface(" + humanizeRune(resultRune.rune) + ")"
      case IsStructSR(range, resultRune) => "isStruct(" + humanizeRune(resultRune.rune) + ")"
      case RefListCompoundMutabilitySR(range, resultRune, coordListRune) => humanizeRune(resultRune.rune) + " = refListCompoundMutability(" + humanizeRune(coordListRune.rune) + ")"
      case CoordIsaSR(range, subRune, superRune) => humanizeRune(subRune.rune) + " isa " + humanizeRune(superRune.rune)
      case CoordSendSR(range, senderRune, receiverRune) => humanizeRune(senderRune.rune) + " -> " + humanizeRune(receiverRune.rune)
      case CoerceToCoordSR(range, coordRune, kindRune) => "coerceToCoord(" + humanizeRune(coordRune.rune) + ", " + humanizeRune(kindRune.rune) + ")"
      case CallSR(range, resultRune, templateRune, argRunes) => humanizeRune(resultRune.rune) + " = " + humanizeRune(templateRune.rune) + "<" + argRunes.map(_.rune).map(humanizeRune).mkString(", ") + ">"
      case LookupSR(range, rune, name) => humanizeRune(rune.rune) + " = " + humanizeImpreciseName(name)
      case LiteralSR(range, rune, literal) => humanizeRune(rune.rune) + " = " + humanizeLiteral(literal)
      case AugmentSR(range, resultRune, ownership, permission, innerRune) => humanizeRune(resultRune.rune) + " = " + humanizeOwnership(ownership) + humanizePermission(permission) + humanizeRune(innerRune.rune)
      case EqualsSR(range, left, right) => humanizeRune(left.rune) + " = " + humanizeRune(right.rune)
      case RuneParentEnvLookupSR(range, rune) => "inherit " + humanizeRune(rune.rune)
      case PackSR(range, resultRune, members) => humanizeRune(resultRune.rune) + " = (" + members.map(x => humanizeRune(x.rune)).mkString(", ") + ")"
      case PrototypeComponentsSR(range, resultRune, nameRune, paramsListRune, returnRune) => {
        humanizeRune(resultRune.rune) + " = Prot(" + humanizeRune(nameRune.rune) + ", " + humanizeRune(paramsListRune.rune) + ", " + humanizeRune(returnRune.rune) + ")"
      }
      case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) => {
        humanizeRune(resultRune.rune) + " = " + "[#" + humanizeRune(sizeRune.rune) + "]<" + humanizeRune(mutabilityRune.rune) + ", " + humanizeRune(variabilityRune.rune) + ">" + humanizeRune(elementRune.rune)
      }
      case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) => {
        humanizeRune(resultRune.rune) + " = " + "[]<" + humanizeRune(mutabilityRune.rune) + ">" + humanizeRune(elementRune.rune)
      }
      case other => vimpl(other)
    }
  }

  def humanizeLiteral(literal: ILiteralSL): String = {
    literal match {
      case OwnershipLiteralSL(ownership) => humanizeOwnership(ownership)
      case PermissionLiteralSL(permission) => humanizePermission(permission)
      case MutabilityLiteralSL(mutability) => humanizeMutability(mutability)
      case VariabilityLiteralSL(variability) => humanizeVariability(variability)
      case IntLiteralSL(value) => value.toString
      case StringLiteralSL(value) => "\"" + value + "\""
      case other => vimpl(other)
    }
  }

  def humanizePermission(p: PermissionP) = {
    p match {
      case ReadwriteP => "!"
      case ReadonlyP => "#"
      case ExclusiveReadwriteP => "!!"
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
      case PointerP => "*"
      case BorrowP => "&"
      case WeakP => "**"
    }
  }
}
