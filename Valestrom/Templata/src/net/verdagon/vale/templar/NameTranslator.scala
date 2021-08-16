package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata.CodeLocationT
import net.verdagon.vale.templar.types.CitizenRefT
import net.verdagon.vale.{vimpl, vwat}

import scala.collection.immutable.List

object NameTranslator {
  def translateFunctionNameToTemplateName(functionName: IFunctionDeclarationNameA): IFunctionTemplateNameT = {
      functionName match {
        case ImmConcreteDestructorNameA(_) => ImmConcreteDestructorTemplateNameT()
        case ImmInterfaceDestructorNameA(_) => ImmInterfaceDestructorTemplateNameT()
        case ImmDropNameA(_) => ImmDropTemplateNameT()
        case LambdaNameA(/*parent, */codeLocation) => {
          LambdaTemplateNameT(NameTranslator.translateCodeLocation(codeLocation))
        }
        case FunctionNameA(name, codeLocation) => {
          FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
        }
        case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => {
          FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
        }
      }
  }

//  def translateImpreciseTypeName(fullNameA: ImpreciseNameA[CodeTypeNameA]): ImpreciseName2[CodeTypeName2] = {
//    val ImpreciseNameA(initS, lastS) = fullNameA
//    ImpreciseName2(initS.map(translateImpreciseNameStep), translateCodeTypeName(lastS))
//  }
//
//  def translateImpreciseName(fullNameA: ImpreciseNameA[IImpreciseNameStepA]): ImpreciseName2[IImpreciseNameStep2] = {
//    val ImpreciseNameA(initS, lastS) = fullNameA
//    ImpreciseName2(initS.map(translateImpreciseNameStep), translateImpreciseNameStep(lastS))
//  }
//
//  def translateCodeTypeName(codeTypeNameA: CodeTypeNameA): CodeTypeName2 = {
//    val CodeTypeNameA(name) = codeTypeNameA
//    CodeTypeName2(name)
//  }
//
//  def translateImpreciseNameStep(impreciseNameStepA: IImpreciseNameStepA): IImpreciseNameStep2 = {
//    impreciseNameStepA match {
//      case ctn @ CodeTypeNameA(_) => translateCodeTypeName(ctn)
//      case GlobalFunctionFamilyNameA(name) => GlobalFunctionFamilyName2(name)
//      case icvn @ ImpreciseCodeVarNameA(_) => translateImpreciseCodeVarNameStep(icvn)
//    }
//  }
//
//  def translateImpreciseCodeVarNameStep(impreciseNameStepA: ImpreciseCodeVarNameA): ImpreciseCodeVarName2 = {
//    var ImpreciseCodeVarNameA(name) = impreciseNameStepA
//    ImpreciseCodeVarName2(name)
//  }
//
//  def translateRune(absoluteNameA: AbsoluteNameA[IRuneA]): FullName2[IRune2] = {
//    val AbsoluteNameA(file, initS, lastS) = absoluteNameA
//    FullName2(file, initS.map(translateNameStep), translateRune(lastS))
//  }
//
//  def translateVarAbsoluteName(absoluteNameA: AbsoluteNameA[IVarNameA]): FullName2[IVarName2] = {
//    val AbsoluteNameA(file, initS, lastS) = absoluteNameA
//    FullName2(file, initS.map(translateNameStep), translateVarNameStep(lastS))
//  }
//
//  def translateVarImpreciseName(absoluteNameA: ImpreciseNameA[ImpreciseCodeVarNameA]):
//  ImpreciseName2[ImpreciseCodeVarName2] = {
//    val ImpreciseNameA(initS, lastS) = absoluteNameA
//    ImpreciseName2(initS.map(translateImpreciseNameStep), translateImpreciseCodeVarNameStep(lastS))
//  }
//
//  def translateFunctionFamilyName(name: ImpreciseNameA[GlobalFunctionFamilyNameA]):
//  ImpreciseName2[GlobalFunctionFamilyName2] = {
//    val ImpreciseNameA(init, last) = name
//    ImpreciseName2(init.map(translateImpreciseNameStep), translateGlobalFunctionFamilyName(last))
//  }
//
//  def translateGlobalFunctionFamilyName(s: GlobalFunctionFamilyNameA): GlobalFunctionFamilyName2 = {
//    val GlobalFunctionFamilyNameA(name) = s
//    GlobalFunctionFamilyName2(name)
//  }
//
//  def translateName(absoluteNameA: AbsoluteNameA[INameA]): FullName2[IName2] = {
//    val AbsoluteNameA(file, initS, lastS) = absoluteNameA
//    FullName2(file, initS.map(translateNameStep), translateNameStep(lastS))
//  }

  def translateCitizenName(name: TopLevelCitizenDeclarationNameA): CitizenTemplateNameT = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = name
    CitizenTemplateNameT(humanName, NameTranslator.translateCodeLocation(codeLocation))
  }

  def translateNameStep(name: INameA): INameT = {
    name match {
//      case LambdaNameA(codeLocation) => LambdaName2(codeLocation)
//      case FunctionNameA(name, codeLocation) => FunctionName2(name, codeLocation)
//      case TopLevelCitizenDeclarationNameA(name, codeLocation) => TopLevelCitizenDeclarationName2(name, codeLocation)
      case LambdaStructNameA(LambdaNameA(codeLocation)) => LambdaCitizenNameT(NameTranslator.translateCodeLocation(codeLocation))
      case ImplNameA(subCitizenHumanName, codeLocation) => ImplDeclareNameT(subCitizenHumanName, translateCodeLocation(codeLocation))
      case LetNameA(codeLocation) => LetNameT(translateCodeLocation(codeLocation))
      case ExportAsNameA(codeLocation) => ExportAsNameT(translateCodeLocation(codeLocation))
      case UnnamedLocalNameA(codeLocation) => UnnamedLocalNameT(translateCodeLocation(codeLocation))
      case ClosureParamNameA() => ClosureParamNameT()
      case MagicParamNameA(codeLocation) => MagicParamNameT(translateCodeLocation(codeLocation))
      case CodeVarNameA(name) => CodeVarNameT(name)
      case ImplicitRuneA(parentName, name) => ImplicitRuneT(translateNameStep(parentName), name)
      case t @ TopLevelCitizenDeclarationNameA(_, _) => translateCitizenName(t)
      case CodeRuneA(name) => CodeRuneT(name)
      case MagicImplicitRuneA(codeLocationS) => MagicImplicitRuneT(NameTranslator.translateCodeLocation(codeLocationS))
      case AnonymousSubstructParentInterfaceRuneA() => AnonymousSubstructParentInterfaceRuneT()
      case LetImplicitRuneA(codeLocation, name) => LetImplicitRuneT(translateCodeLocation(codeLocation), name)
//      case ImplicitRuneA(name) => ImplicitRune2(name)
//      case MagicImplicitRuneA(magicParamIndex) => MagicImplicitRune2(magicParamIndex)
      case MemberRuneA(memberIndex) => MemberRuneT(memberIndex)
      case ReturnRuneA() => ReturnRuneT()

      case LambdaNameA(codeLocation) => {
        LambdaTemplateNameT(NameTranslator.translateCodeLocation(codeLocation))
      }
      case FunctionNameA(name, codeLocation) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case _ => vimpl(name.toString)
    }
  }

  def translateCodeLocation(s: CodeLocationS): CodeLocationT = {
    val CodeLocationS(line, col) = s
    CodeLocationT(line, col)
  }

  def translateVarNameStep(name: IVarNameA): IVarNameT = {
    name match {
      case UnnamedLocalNameA(codeLocation) => UnnamedLocalNameT(translateCodeLocation(codeLocation))
      case ClosureParamNameA() => ClosureParamNameT()
      case MagicParamNameA(codeLocation) => MagicParamNameT(translateCodeLocation(codeLocation))
      case ConstructingMemberNameA(n) => ConstructingMemberNameT(n)
      case CodeVarNameA(name) => CodeVarNameT(name)
      case AnonymousSubstructMemberNameA(index) => AnonymousSubstructMemberNameT(index)
    }
  }

  def translateRune(rune: IRuneA): IRuneT = {
    rune match {
      case CodeRuneA(name) => CodeRuneT(name)
      case ImplicitRuneA(containerName, name) => ImplicitRuneT(translateNameStep(containerName), name)
      case LetImplicitRuneA(codeLocation, name) => LetImplicitRuneT(translateCodeLocation(codeLocation), name)
      case MagicImplicitRuneA(codeLocation) => MagicImplicitRuneT(translateCodeLocation(codeLocation))
      case MemberRuneA(memberIndex) => MemberRuneT(memberIndex)
      case ReturnRuneA() => ReturnRuneT()
      case ArraySizeImplicitRuneA() => ArraySizeImplicitRuneT()
      case ArrayVariabilityImplicitRuneA() => ArrayVariabilityImplicitRuneT()
      case ArrayMutabilityImplicitRuneA() => ArrayMutabilityImplicitRuneT()
      case AnonymousSubstructParentInterfaceRuneA() => AnonymousSubstructParentInterfaceRuneT()
      case ExplicitTemplateArgRuneA(index) => ExplicitTemplateArgRuneT(index)
      case x => vimpl(x.toString)
    }
  }

  def translateImplName(n: ImplNameA): ImplDeclareNameT = {
    val ImplNameA(subCitizenHumanName, l) = n
    ImplDeclareNameT(subCitizenHumanName, translateCodeLocation(l))
  }

  def getImplNameForNameInner(useOptimization: Boolean, nameSteps: Vector[INameT]): Option[ImplImpreciseNameA] = {
    nameSteps.last match {
      case CitizenNameT(humanName, templateArgs) => Some(ImplImpreciseNameA(humanName))
      case TupleNameT(_) => None
      case LambdaCitizenNameT(_) => None
      case AnonymousSubstructNameT(_) => {
        // Use the paren'ts name, see INSHN.
        getImplNameForNameInner(useOptimization, nameSteps.init)
      }
      case _ => vwat()
    }
  }

  // Gets the name of an impl that would be for this citizen.
  // Returns None if it can't be in an impl.
  def getImplNameForName(useOptimization: Boolean, ref: CitizenRefT): Option[ImplImpreciseNameA] = {
    getImplNameForNameInner(useOptimization, ref.fullName.steps)
  }
}
