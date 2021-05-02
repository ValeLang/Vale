package net.verdagon.vale.templar

import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.vimpl

object TemplataNamer {
  // Identifier names need to come from the Templar output because some things are erased
  // by Hammer types, such as template args. Hammer will sometimes output many functions
  // with the same signature because of this.

  def getReferenceIdentifierName(reference: Coord): String = {
    val Coord(ownership, permission, referend) = reference;
    val ownershipString =
      ownership match {
        case Share => ""//"*"
        case Borrow => "&"
        case Weak => "&&"
        case Own => ""//"^"
      }
    val permissionString =
      permission match {
        case Readonly => "#"
        case Readwrite => "!"
//        case ExclusiveReadwrite => "!!"
      }
    ownershipString + permissionString + getReferendIdentifierName(referend)
  }

  def stringifyTemplateArgs(templateArgs: List[ITemplata]): String = {
    "<" + templateArgs.map(templateArg => getIdentifierName(templateArg)).mkString(", ") + ">"
  }

  def stringifyParametersArgs(parameters: List[Coord]): String = {
    "(" + parameters.map(parameter => getReferenceIdentifierName(parameter)).mkString(", ") + ")"
  }

  def getFullNameIdentifierName(fullName: FullName2[IName2]): String = {
    // Some nice rune symbols: ᚠᚢᚣᚥᚨᚫᚬᚮᚱᚳᚴᚻᛃᛄᛇᛈᛉᛊᛋᛒᛗᛘᛝᛞᛟᛥ
    // Here's the ones we haven't used below: ᚢᚨᚬᚮᚳᚴᛃᛄᛇ
    // We should probably not use these long term since they're super unrecognizable,
    // we can switch to nicer symbols once things settle.
    fullName.steps.map({
      case ImplDeclareName2(subCitizenHumanName, codeLocation) => "ᚠ" + subCitizenHumanName + "@" + codeLocation
      case LetName2(codeLocation) => "ᚥ" + codeLocation
      case UnnamedLocalName2(codeLocation) => "ᚣ" + codeLocation
      case ClosureParamName2() => "ᛋ"
      case MagicParamName2(magicParamNumber) => "ᛞ" + magicParamNumber
      case CodeVarName2(name) => "ᛗ" + name
//      case CodeRune2(name) => "ᛝ" + name
//      case ImplicitRune2(name) => "ᚻ" + name
//      case MemberRune2(memberIndex) => "ᛒ" + memberIndex
//      case MagicImplicitRune2(magicParamIndex) => "ᛥ" + magicParamIndex
//      case ReturnRune2() => "ᚱ"
      case FunctionName2(humanName, templateArgs, parameters) => "ᚫ" + humanName + stringifyTemplateArgs(templateArgs) + stringifyParametersArgs(parameters)
//      case LambdaName2(codeLocation, templateArgs, parameters) => "ᛈ" + codeLocation + stringifyTemplateArgs(templateArgs) + stringifyParametersArgs(parameters)
//      case CitizenName2(humanName, templateArgs) => "ᛟ" + humanName + stringifyTemplateArgs(templateArgs)
      case CitizenName2(humanName, templateArgs) => "ᛘ" + humanName + stringifyTemplateArgs(templateArgs)
      case LambdaCitizenName2(codeLocation) => "ᛊ" + codeLocation
      case AnonymousSubstructName2(thing) =>
      case TupleName2(members) => "tup#"
      case ImmDropName2(kind) => "drop*" + getReferendIdentifierName(kind)
      case x => vimpl(x.toString)
    }).mkString(".")
  }

  def getReferendIdentifierName(tyype: Kind): String = {
    tyype match {
      case Int2() => "int"//"𝒾"
      case Float2() => "float"//"𝒻"
      case Bool2() => "bool"// "𝒷"
      case Str2() => "str"// "𝓈"
      case Void2() => "void" // "∅"
      case TupleT2(_, _) => "tup"
      case Never2() => "never"
      case UnknownSizeArrayT2(array) => "𝔸" + getReferenceIdentifierName(array.memberType)
      case KnownSizeArrayT2(size, arrayT2) => "𝔸" + size + getReferenceIdentifierName(arrayT2.memberType)
      case PackT2(_, underlyingStruct) => {
        getReferendIdentifierName(underlyingStruct)
      }
      case StructRef2(fullName) => "𝕊" + getFullNameIdentifierName(fullName)
      case InterfaceRef2(fullName) => "𝕋" + getFullNameIdentifierName(fullName)
      case OverloadSet(env, name, _) => {
        "𝔾" + " " + env + " " + name
      }
    }
  }

  private def getIdentifierName(tyype: ITemplata): String = {
    tyype match {
      case KindTemplata(referend) => "ㄊ" + getReferendIdentifierName(referend)
      case CoordTemplata(reference) => "ㄊ" + getReferenceIdentifierName(reference)
      case MutabilityTemplata(Mutable) => "ㄊmut"
      case MutabilityTemplata(Immutable) => "ㄊimm"
      case IntegerTemplata(num) => "ㄊ" + num
//      case StructTemplateTemplata(struct1) => "ㄊ𝕊" + struct1.struct1Id
//      case InterfaceTemplateTemplata(interface1) => "ㄊ𝕋" + interface1.interface1Id
    }
  }

  def getIdentifierName(prototype: Prototype2): String = {
    val Prototype2(fullName, returnType2) = prototype;
    "𝔽" + getFullNameIdentifierName(fullName) +
        getReferenceIdentifierName(returnType2)
  }

  def getIdentifierName(paramFilter: ParamFilter): String = {
    val ParamFilter(tyype, virtuality) = paramFilter
    getReferenceIdentifierName(tyype) +
      (virtuality match {
        case None => ""
        case Some(Abstract2) => " abstract"
        case Some(Override2(kind)) => " impl " + getReferendIdentifierName(kind)
      })
  }
}
