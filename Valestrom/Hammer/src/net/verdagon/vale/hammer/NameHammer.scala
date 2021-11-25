package net.verdagon.vale.hammer

import net.verdagon.vale.metal._
import net.verdagon.vale.scout.{AnonymousSubstructParentInterfaceTemplateRuneS, CodeRuneS, ExplicitTemplateArgRuneS}
import net.verdagon.vale.templar.{Hinputs, _}
import net.verdagon.vale.templar.env.{IEnvironment, PackageEnvironment}
import net.verdagon.vale.templar.names._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{CodeLocationS, FileCoordinate, PackageCoordinate, vassert, vfail, vimpl, vwat}
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

  def getReadableName(namePart: INameT): String = {
    namePart match {
      case SelfNameT() => "self"
      case AnonymousSubstructImplNameT() => "AnonSubstructImpl"
      case VirtualFreeNameT(_, _) => "VirtualFree"
      case FreeNameT(templateArgs, parameters) => "Free"
      case AnonymousSubstructMemberNameT(index) => "anonSubstructMember" + index
      case AnonymousSubstructConstructorNameT(templateArgs, params) => "anonSubstructConstructor"
      case AnonymousSubstructNameT(_, _) => "AnonSubstruct"
//      case AnonymousSubstructParentInterfaceRuneS() => "anonSubstructParentInterfaceRune"
      case BuildingFunctionNameWithClosuredsT(_) => vwat() // Shouldnt see this in hammer
      case BuildingFunctionNameWithClosuredsAndTemplateArgsT(_, _) => vwat() // Shouldnt see this in hammer
      case CitizenNameT(templateName, templateArgs) => getReadableName(templateName)
      case CitizenTemplateNameT(humanName) => humanName
      case ClosureParamNameT() => "closure"
//      case CodeRuneS(name) => name
      case CodeVarNameT(name) => name
      case ConstructingMemberNameT(name) => name
      case ConstructorNameT(params) => "constructor"
      case ConstructorTemplateNameT(codeLoc) => "constructorTemplate"
//      case ExplicitTemplateArgRuneS(index) => "rune" + index
      case ExternFunctionNameT(humanName, params) => humanName
      case FunctionNameT(humanName, templateArgs, params) => humanName
      case FunctionTemplateNameT(humanName, codeLoc) => humanName
      case PackageTopLevelNameT() => vwat() // Does this ever make it to hammer?
//      case ImmConcreteDestructorNameT(kind) => "immConcreteDestructor"
//      case ImmConcreteDestructorTemplateNameT() => "immConcreteDestructorTemplate"
//      case DropNameT(kind) => "immDrop"
//      case DropTemplateNameT() => "immDropTemplate"
//      case ImmInterfaceDestructorNameT(templateArgs, params) => "immInterfaceDestructor"
//      case ImmInterfaceDestructorTemplateNameT() => "immInterfaceDestructorTemplate"
      case ImplDeclareNameT(codeLoc) => "impl" + codeLoc
//      case ImplicitRuneS(parentName, name) => "implicitRune" + name
      case StaticSizedArrayNameT(size, arr) => "ssa" + size
      case LambdaCitizenNameT(codeLoc) => "lam"
      case LambdaTemplateNameT(codeLoc) => "lamTemplate"
//      case LetImplicitRuneS(codeLoc, name) => "letImplicitRune" + name
      case LetNameT(codeLoc) => "let"
//      case MagicImplicitRuneS(codeLoc) => "magicRune"
      case MagicParamNameT(codeLoc) => "magicParam"
//      case MemberRuneS(memberIndex) => "memberRune" + memberIndex
      case PrimitiveNameT(humanName) => humanName
      case RawArrayNameT(mutability, elementType) => "rawArr"
//      case ReturnRuneS() => "retRune"
//      case SolverKindRuneS(paramRune) => "solverKindRune"
      case TemplarBlockResultVarNameT(num) => "blockResult" + num
      case TemplarFunctionResultVarNameT() => "funcResult"
      case TemplarPatternDestructureeNameT(num) => "patDestr" + num
      case TemplarPatternMemberNameT(life) => "patMem" + life
      case TemplarTemporaryVarNameT(num) => "tempVar" + num
//      case TupleNameT(members) => "Tup" + members.size
      case RuntimeSizedArrayNameT(arr) => "rsa"
      case UnnamedLocalNameT(codeLoc) => "unnamedLocal"
    }
  }

  def translateFullName(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    fullName2: FullNameT[INameT]
  ): FullNameH = {
    val FullNameT(packageCoord @ PackageCoordinate(project, packageSteps), _, _) = fullName2
    val newNameParts = fullName2.steps.map(step => VonHammer.translateName(hinputs, hamuts, step))
    val readableName = getReadableName(fullName2.last)

    val id =
      if (fullName2.last.isInstanceOf[ExternFunctionNameT]) {
        -1
      } else {
        hamuts.getNameId(readableName, packageCoord, newNameParts)
      }
    FullNameH(readableName, id, packageCoord, newNameParts)
  }

  // Adds a step to the name.
  def addStep(
    hamuts: HamutsBox,
    fullName: FullNameH,
    s: String):
  FullNameH = {
    val newNameParts = fullName.parts :+ VonStr(s)
    val id = hamuts.getNameId(s, fullName.packageCoordinate, newNameParts)
    FullNameH(s, id, fullName.packageCoordinate, newNameParts)
  }

  def translateCodeLocation(location: CodeLocationS): VonObject = {
    val CodeLocationS(fileCoord, offset) = location
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
      "FileCoordinate",
      None,
      Vector(
        VonMember("module", VonStr(module)),
        VonMember("paackage", VonArray(None, paackage.map(VonStr).toVector)),
        VonMember("filename", VonStr(filename))))
  }

  def translatePackageCoordinate(coord: PackageCoordinate): VonObject = {
    val PackageCoordinate(module, paackage) = coord
    val nonEmptyModuleName = if (module == "") "__vale" else module;
    VonObject(
      "PackageCoordinate",
      None,
      Vector(
        VonMember("project", VonStr(nonEmptyModuleName)),
        VonMember("packageSteps", VonArray(None, paackage.map(VonStr).toVector))))
  }
}
