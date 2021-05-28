package net.verdagon.vale.hammer

import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal._
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.{IEnvironment, PackageEnvironment}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{FileCoordinate, vassert, vfail, vimpl, vwat}
import net.verdagon.von.{IVonData, VonArray, VonInt, VonMember, VonObject, VonStr}

import scala.collection.immutable.List

object NameHammer {
//  // This is here temporarily until we make INameH fully support stuff.
//  // Maybe we want INameH to be more general, so people downstream dont have to support stuff?
//  // Maybe make a SpecialNodeH(Str) or something.
//  def stringify(name: FullName2[IName2]): String = {
//    name.toString
//  }
//  def stringifyNamePart(name: IName2): String = {
//    name.toString
//  }

  def getReadableName(namePart: IName2): String = {
    namePart match {
      case AnonymousSubstructImplName2() => "AnonSubstructImplName"
      case AnonymousSubstructMemberName2(index) => "anonSubstructMember" + index
      case AnonymousSubstructName2(callables) => "AnonSubstruct"
      case AnonymousSubstructParentInterfaceRune2() => "anonSubstructParentInterfaceRune"
      case BuildingFunctionNameWithClosureds2(_) => vwat() // Shouldnt see this in hammer
      case BuildingFunctionNameWithClosuredsAndTemplateArgs2(_, _) => vwat() // Shouldnt see this in hammer
      case CitizenName2(humanName, templateArgs) => humanName
      case CitizenTemplateName2(humanName, codeLocation) => humanName
      case ClosureParamName2() => "closure"
      case CodeRune2(name) => name
      case CodeVarName2(name) => name
      case ConstructingMemberName2(name) => name
      case ConstructorName2(params) => "constructor"
      case ConstructorTemplateName2(codeLoc) => "constructorTemplate"
      case ExplicitTemplateArgRune2(index) => "rune" + index
      case ExternFunctionName2(humanName, params) => humanName
      case FunctionName2(humanName, templateArgs, params) => humanName
      case FunctionTemplateName2(humanName, codeLoc) => humanName
      case GlobalPackageName2() => vwat() // Does this ever make it to hammer?
      case ImmConcreteDestructorName2(kind) => "immConcreteDestructor"
      case ImmConcreteDestructorTemplateName2() => "immConcreteDestructorTemplate"
      case ImmDropName2(kind) => "immDrop"
      case ImmDropTemplateName2() => "immDropTemplate"
      case ImmInterfaceDestructorName2(templateArgs, params) => "immInterfaceDestructor"
      case ImmInterfaceDestructorTemplateName2() => "immInterfaceDestructorTemplate"
      case ImplDeclareName2(subCitizenHumanName, codeLoc) => "impl" + subCitizenHumanName
      case ImplicitRune2(parentName, name) => "implicitRune" + name
      case KnownSizeArrayName2(size, arr) => "ksa" + size
      case LambdaCitizenName2(codeLoc) => "lam"
      case LambdaTemplateName2(codeLoc) => "lamTemplate"
      case LetImplicitRune2(codeLoc, name) => "letImplicitRune" + name
      case LetName2(codeLoc) => "letName"
      case MagicImplicitRune2(codeLoc) => "magicRune"
      case MagicParamName2(codeLoc) => "magicParam"
      case MemberRune2(memberIndex) => "memberRune" + memberIndex
      case PrimitiveName2(humanName) => humanName
      case RawArrayName2(mutability, elementType) => "rawArr"
      case ReturnRune2() => "retRune"
      case SolverKindRune2(paramRune) => "solverKindRune"
      case TemplarBlockResultVarName2(num) => "blockResult" + num
      case TemplarFunctionResultVarName2() => "funcResult"
      case TemplarPatternDestructureeName2(num) => "patDestrName" + num
      case TemplarPatternMemberName2(num, memberIndex) => "patMemName" + num + "_" + memberIndex
      case TemplarTemporaryVarName2(num) => "tempVarName" + num
      case TupleName2(members) => "Tup" + members.size
      case UnknownSizeArrayName2(arr) => "usa"
      case UnnamedLocalName2(codeLoc) => "unnamedLocal"
    }
  }

  def translateFullName(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    fullName2: FullName2[IName2]
  ): FullNameH = {
    val newNameParts = fullName2.steps.map(step => VonHammer.translateName(hinputs, hamuts, step))
    val readableName = getReadableName(fullName2.last)
    val id =
      if (fullName2.last.isInstanceOf[ExternFunctionName2]) {
        -1
      } else {
        hamuts.getNameId(readableName, newNameParts)
      }
    FullNameH(readableName, id, newNameParts)
  }

  // Adds a step to the name.
  def addStep(
    hamuts: HamutsBox,
    fullName: FullNameH,
    s: String):
  FullNameH = {
    val newNameParts = fullName.parts :+ VonStr(s)
    val id = hamuts.getNameId(s, newNameParts)
    FullNameH(s, id, newNameParts)
  }

  def translateCodeLocation(location: CodeLocation2): VonObject = {
    val CodeLocation2(fileCoord, offset) = location
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("file", translateFileCoordinate(fileCoord)),
        VonMember("offset", VonInt(offset))))
  }

  def translateFileCoordinate(coord: FileCoordinate): VonObject = {
    val FileCoordinate(module, paackage, filename) = coord
    VonObject(
      "CodeLocation",
      None,
      Vector(
        VonMember("module", VonStr(module)),
        VonMember("paackage", VonArray(None, paackage.map(VonStr).toVector)),
        VonMember("filename", VonStr(filename))))
  }
}
