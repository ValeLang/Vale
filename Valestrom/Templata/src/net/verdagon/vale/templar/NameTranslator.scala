package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.CodeLocationS
import net.verdagon.vale.templar.templata.CodeLocation2
import net.verdagon.vale.templar.types.CitizenRef2
import net.verdagon.vale.{vimpl, vwat}

import scala.collection.immutable.List

object NameTranslator {
  def translateFunctionNameToTemplateName(functionName: IFunctionDeclarationNameA): IFunctionTemplateName2 = {
      functionName match {
        case ImmConcreteDestructorNameA() => ImmConcreteDestructorTemplateName2()
        case ImmInterfaceDestructorNameA() => ImmInterfaceDestructorTemplateName2()
        case ImmDropNameA() => ImmDropTemplateName2()
        case LambdaNameA(/*parent, */codeLocation) => {
          LambdaTemplateName2(NameTranslator.translateCodeLocation(codeLocation))
        }
        case FunctionNameA(name, codeLocation) => {
          FunctionTemplateName2(name, NameTranslator.translateCodeLocation(codeLocation))
        }
        case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => {
          FunctionTemplateName2(name, NameTranslator.translateCodeLocation(codeLocation))
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

  def translateCitizenName(name: TopLevelCitizenDeclarationNameA): CitizenTemplateName2 = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = name
    CitizenTemplateName2(humanName, NameTranslator.translateCodeLocation(codeLocation))
  }

  def translateNameStep(name: INameA): IName2 = {
    name match {
//      case LambdaNameA(codeLocation) => LambdaName2(codeLocation)
//      case FunctionNameA(name, codeLocation) => FunctionName2(name, codeLocation)
//      case TopLevelCitizenDeclarationNameA(name, codeLocation) => TopLevelCitizenDeclarationName2(name, codeLocation)
      case LambdaStructNameA(LambdaNameA(codeLocation)) => LambdaCitizenName2(NameTranslator.translateCodeLocation(codeLocation))
      case ImplNameA(subCitizenHumanName, codeLocation) => ImplDeclareName2(subCitizenHumanName, translateCodeLocation(codeLocation))
      case LetNameA(codeLocation) => LetName2(translateCodeLocation(codeLocation))
      case UnnamedLocalNameA(codeLocation) => UnnamedLocalName2(translateCodeLocation(codeLocation))
      case ClosureParamNameA() => ClosureParamName2()
      case MagicParamNameA(codeLocation) => MagicParamName2(translateCodeLocation(codeLocation))
      case CodeVarNameA(name) => CodeVarName2(name)
      case ImplicitRuneA(parentName, name) => ImplicitRune2(translateNameStep(parentName), name)
      case t @ TopLevelCitizenDeclarationNameA(_, _) => translateCitizenName(t)
      case CodeRuneA(name) => CodeRune2(name)
      case MagicImplicitRuneA(codeLocationS) => MagicImplicitRune2(NameTranslator.translateCodeLocation(codeLocationS))
      case AnonymousSubstructParentInterfaceRuneA() => AnonymousSubstructParentInterfaceRune2()
      case LetImplicitRuneA(codeLocation, name) => LetImplicitRune2(translateCodeLocation(codeLocation), name)
//      case ImplicitRuneA(name) => ImplicitRune2(name)
//      case MagicImplicitRuneA(magicParamIndex) => MagicImplicitRune2(magicParamIndex)
      case MemberRuneA(memberIndex) => MemberRune2(memberIndex)
      case ReturnRuneA() => ReturnRune2()

      case LambdaNameA(codeLocation) => {
        LambdaTemplateName2(NameTranslator.translateCodeLocation(codeLocation))
      }
      case FunctionNameA(name, codeLocation) => {
        FunctionTemplateName2(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => {
        FunctionTemplateName2(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case _ => vimpl(name.toString)
    }
  }

  def translateCodeLocation(s: CodeLocationS): CodeLocation2 = {
    val CodeLocationS(line, col) = s
    CodeLocation2(line, col)
  }

  def translateVarNameStep(name: IVarNameA): IVarName2 = {
    name match {
      case UnnamedLocalNameA(codeLocation) => UnnamedLocalName2(translateCodeLocation(codeLocation))
      case ClosureParamNameA() => ClosureParamName2()
      case MagicParamNameA(codeLocation) => MagicParamName2(translateCodeLocation(codeLocation))
      case ConstructingMemberNameA(n) => ConstructingMemberName2(n)
      case CodeVarNameA(name) => CodeVarName2(name)
      case AnonymousSubstructMemberNameA(index) => AnonymousSubstructMemberName2(index)
    }
  }

  def translateRune(rune: IRuneA): IRune2 = {
    rune match {
      case CodeRuneA(name) => CodeRune2(name)
      case ImplicitRuneA(containerName, name) => ImplicitRune2(translateNameStep(containerName), name)
      case LetImplicitRuneA(codeLocation, name) => LetImplicitRune2(translateCodeLocation(codeLocation), name)
      case MagicImplicitRuneA(codeLocation) => MagicImplicitRune2(translateCodeLocation(codeLocation))
      case MemberRuneA(memberIndex) => MemberRune2(memberIndex)
      case ReturnRuneA() => ReturnRune2()
      case AnonymousSubstructParentInterfaceRuneA() => AnonymousSubstructParentInterfaceRune2()
      case ExplicitTemplateArgRuneA(index) => ExplicitTemplateArgRune2(index)
      case _ => vimpl()
    }
  }

  def translateImplName(n: ImplNameA): ImplDeclareName2 = {
    val ImplNameA(subCitizenHumanName, l) = n
    ImplDeclareName2(subCitizenHumanName, translateCodeLocation(l))
  }

  def getImplNameForNameInner(useOptimization: Boolean, nameSteps: List[IName2]): Option[ImplImpreciseNameA] = {
    nameSteps.last match {
      case CitizenName2(humanName, templateArgs) => Some(ImplImpreciseNameA(humanName))
      case TupleName2(_) => None
      case LambdaCitizenName2(_) => None
      case AnonymousSubstructName2(_) => {
        // Use the paren'ts name, see INSHN.
        getImplNameForNameInner(useOptimization, nameSteps.init)
      }
      case _ => vwat()
    }
  }

  // Gets the name of an impl that would be for this citizen.
  // Returns None if it can't be in an impl.
  def getImplNameForName(useOptimization: Boolean, ref: CitizenRef2): Option[ImplImpreciseNameA] = {
    getImplNameForNameInner(useOptimization, ref.fullName.steps)
  }
}
