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

class NameHammer(translateName: (Hinputs, HamutsBox, INameT) => IVonData) {
  def getReadableName(namePart: INameT): String = {
    namePart match {
      case SelfNameT() => "self"
      case AnonymousSubstructImplNameT() => "AnonSubstructImpl"
      case AbstractVirtualFreeNameT(_, _) => "(abstract vfree)"
      case OverrideVirtualFreeNameT(_, _) => "(override vfree)"
      case AbstractVirtualDropFunctionNameT(_, _, _) => "vdrop"
      case OverrideVirtualDropFunctionNameT(_, _, _) => "vdrop"
      case FreeNameT(templateArgs, parameters) => "Free"
      case AnonymousSubstructMemberNameT(index) => "anonSubstructMember" + index
      case AnonymousSubstructConstructorNameT(templateArgs, params) => "anonSubstructConstructor"
      case AnonymousSubstructNameT(_, _) => "AnonSubstruct"
      case BuildingFunctionNameWithClosuredsT(_) => vwat() // Shouldnt see this in hammer
      case BuildingFunctionNameWithClosuredsAndTemplateArgsT(_, _) => vwat() // Shouldnt see this in hammer
      case CitizenNameT(templateName, templateArgs) => getReadableName(templateName)
      case CitizenTemplateNameT(humanName) => humanName
      case ClosureParamNameT() => "closure"
      case CodeVarNameT(name) => name
      case ConstructingMemberNameT(name) => name
      case ConstructorNameT(params) => "constructor"
      case ConstructorTemplateNameT(codeLoc) => "constructorTemplate"
      case ExternFunctionNameT(humanName, params) => humanName
      case FunctionNameT(humanName, templateArgs, params) => humanName
      case FunctionTemplateNameT(humanName, codeLoc) => humanName
      case PackageTopLevelNameT() => vwat() // Does this ever make it to hammer?
      case ImplDeclareNameT(codeLoc) => "impl" + codeLoc
      case StaticSizedArrayNameT(size, arr) => "ssa" + size
      case LambdaCitizenNameT(codeLoc) => "lam"
      case LambdaTemplateNameT(codeLoc) => "lamTemplate"
      case LetNameT(codeLoc) => "let"
      case IterableNameT(range) => "iterable"
      case IteratorNameT(range) => "iterator"
      case IterationOptionNameT(range) => "iterationOption"
      case MagicParamNameT(codeLoc) => "magicParam"
      case PrimitiveNameT(humanName) => humanName
      case RawArrayNameT(mutability, elementType) => "rawArr"
      case TemplarBlockResultVarNameT(num) => "blockResult" + num
      case TemplarFunctionResultVarNameT() => "funcResult"
      case TemplarPatternDestructureeNameT(num) => "patDestr" + num
      case TemplarPatternMemberNameT(life) => "patMem" + life
      case TemplarTemporaryVarNameT(num) => "tempVar" + num
      case RuntimeSizedArrayNameT(arr) => "rsa"
      case UnnamedLocalNameT(codeLoc) => "unnamedLocal"
      case ForwarderFunctionTemplateNameT(inner, index) => "fwdt_" + index + "_" + getReadableName(inner)
      case ForwarderFunctionNameT(inner, index) => "fwd_" + index + "_" + getReadableName(inner)
    }
  }

  def translateFullName(
    hinputs: Hinputs,
    hamuts: HamutsBox,
    fullName2: FullNameT[INameT]
  ): FullNameH = {
    val FullNameT(packageCoord@PackageCoordinate(project, packageSteps), _, _) = fullName2
    val newNameParts = fullName2.steps.map(step => translateName(hinputs, hamuts, step))
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
}

object NameHammer {
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
