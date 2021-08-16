package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vimpl

object TemplataNamer {
  // Identifier names need to come from the Templar output because some things are erased
  // by Hammer types, such as template args. Hammer will sometimes output many functions
  // with the same signature because of this.

  def getReferenceIdentifierName(reference: CoordT): String = {
    val CoordT(ownership, permission, kind) = reference;
    val ownershipString =
      ownership match {
        case ShareT => ""//"*"
        case ConstraintT => "&"
        case WeakT => "&&"
        case OwnT => ""//"^"
      }
    val permissionString =
      permission match {
        case ReadonlyT => "#"
        case ReadwriteT => "!"
//        case ExclusiveReadwrite => "!!"
      }
    ownershipString + permissionString + getKindIdentifierName(kind)
  }

  def stringifyTemplateArgs(templateArgs: Vector[ITemplata]): String = {
    "<" + templateArgs.map(templateArg => getIdentifierName(templateArg)).mkString(", ") + ">"
  }

  def stringifyParametersArgs(parameters: Vector[CoordT]): String = {
    "(" + parameters.map(parameter => getReferenceIdentifierName(parameter)).mkString(", ") + ")"
  }

  def getFullNameIdentifierName(fullName: FullNameT[INameT]): String = {
    // Some nice rune symbols: ᚠᚢᚣᚥᚨᚫᚬᚮᚱᚳᚴᚻᛃᛄᛇᛈᛉᛊᛋᛒᛗᛘᛝᛞᛟᛥ
    // Here's the ones we haven't used below: ᚢᚨᚬᚮᚳᚴᛃᛄᛇ
    // We should probably not use these long term since they're super unrecognizable,
    // we can switch to nicer symbols once things settle.
    fullName.steps.map({
      case ImplDeclareNameT(subCitizenHumanName, codeLocation) => "ᚠ" + subCitizenHumanName + "@" + codeLocation
      case LetNameT(codeLocation) => "ᚥ" + codeLocation
      case UnnamedLocalNameT(codeLocation) => "ᚣ" + codeLocation
      case ClosureParamNameT() => "ᛋ"
      case MagicParamNameT(magicParamNumber) => "ᛞ" + magicParamNumber
      case CodeVarNameT(name) => "ᛗ" + name
//      case CodeRune2(name) => "ᛝ" + name
//      case ImplicitRune2(name) => "ᚻ" + name
//      case MemberRune2(memberIndex) => "ᛒ" + memberIndex
//      case MagicImplicitRune2(magicParamIndex) => "ᛥ" + magicParamIndex
//      case ReturnRune2() => "ᚱ"
      case FunctionNameT(humanName, templateArgs, parameters) => "ᚫ" + humanName + stringifyTemplateArgs(templateArgs) + stringifyParametersArgs(parameters)
//      case LambdaName2(codeLocation, templateArgs, parameters) => "ᛈ" + codeLocation + stringifyTemplateArgs(templateArgs) + stringifyParametersArgs(parameters)
//      case CitizenName2(humanName, templateArgs) => "ᛟ" + humanName + stringifyTemplateArgs(templateArgs)
      case CitizenNameT(humanName, templateArgs) => "ᛘ" + humanName + stringifyTemplateArgs(templateArgs)
      case LambdaCitizenNameT(codeLocation) => "ᛊ" + codeLocation
      case AnonymousSubstructNameT(thing) =>
      case TupleNameT(members) => "tup#"
      case ImmDropNameT(kind) => "drop*" + getKindIdentifierName(kind)
      case x => vimpl(x.toString)
    }).mkString(".")
  }

  def getKindIdentifierName(tyype: KindT): String = {
    tyype match {
      case IntT(bits) => "i" + bits //"𝒾"
      case FloatT() => "float"//"𝒻"
      case BoolT() => "bool"// "𝒷"
      case StrT() => "str"// "𝓈"
      case VoidT() => "void" // "∅"
      case TupleTT(_, _) => "tup"
      case NeverT() => "never"
      case RuntimeSizedArrayTT(array) => "𝔸" + getReferenceIdentifierName(array.memberType)
      case StaticSizedArrayTT(size, arrayT2) => "𝔸" + size + getReferenceIdentifierName(arrayT2.memberType)
      case PackTT(_, underlyingStruct) => {
        getKindIdentifierName(underlyingStruct)
      }
      case StructTT(fullName) => "𝕊" + getFullNameIdentifierName(fullName)
      case InterfaceTT(fullName) => "𝕋" + getFullNameIdentifierName(fullName)
      case OverloadSet(env, name, _) => {
        "𝔾" + " " + env + " " + name
      }
    }
  }

  private def getIdentifierName(tyype: ITemplata): String = {
    tyype match {
      case KindTemplata(kind) => "ㄊ" + getKindIdentifierName(kind)
      case CoordTemplata(reference) => "ㄊ" + getReferenceIdentifierName(reference)
      case MutabilityTemplata(MutableT) => "ㄊmut"
      case MutabilityTemplata(ImmutableT) => "ㄊimm"
      case IntegerTemplata(num) => "ㄊ" + num
//      case StructTemplateTemplata(structA) => "ㄊ𝕊" + structA.struct1Id
//      case InterfaceTemplateTemplata(interfaceA) => "ㄊ𝕋" + interfaceA.interface1Id
    }
  }

  def getIdentifierName(prototype: PrototypeT): String = {
    val PrototypeT(fullName, returnType2) = prototype;
    "𝔽" + getFullNameIdentifierName(fullName) +
        getReferenceIdentifierName(returnType2)
  }

  def getIdentifierName(paramFilter: ParamFilter): String = {
    val ParamFilter(tyype, virtuality) = paramFilter
    getReferenceIdentifierName(tyype) +
      (virtuality match {
        case None => ""
        case Some(AbstractT$) => " abstract"
        case Some(OverrideT(kind)) => " impl " + getKindIdentifierName(kind)
      })
  }
}
