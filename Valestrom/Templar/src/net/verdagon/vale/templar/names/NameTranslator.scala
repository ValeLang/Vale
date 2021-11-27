package net.verdagon.vale.templar.names

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout._
import net.verdagon.vale.templar.types.CitizenRefT
import net.verdagon.vale.{CodeLocationS, vassert, vimpl, vwat}

object NameTranslator {
  def translateFunctionNameToTemplateName(functionName: IFunctionDeclarationNameS): IFunctionTemplateNameT = {
    functionName match {
      case LambdaDeclarationNameS(/*parent, */ codeLocation) => {
        LambdaTemplateNameT(NameTranslator.translateCodeLocation(codeLocation))
      }
      case FreeDeclarationNameS(codeLocationS) => {
        FreeTemplateNameT(NameTranslator.translateCodeLocation(codeLocationS))
      }
      case VirtualFreeDeclarationNameS(codeLoc) => {
        VirtualFreeTemplateNameT(codeLoc)
      }
      case FunctionNameS(name, codeLocation) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, codeLocation)) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation.begin))
      }
      case ConstructorNameS(thing @ AnonymousSubstructTemplateNameS(_)) => {
        AnonymousSubstructConstructorTemplateNameT(translateCitizenName(thing))
      }
    }
  }

  def translateCitizenName(name: ICitizenDeclarationNameS): ICitizenTemplateNameT = {
    name match {
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        CitizenTemplateNameT(humanName)
      }
      case AnonymousSubstructTemplateNameS(interfaceName) => {
        // Now strip it off, stuff it inside our new name. See LNASC.
        AnonymousSubstructTemplateNameT(translateCitizenName(interfaceName))
      }
    }
  }

  def translateNameStep(name: INameS): INameT = {
    name match {
      case LambdaStructDeclarationNameS(LambdaDeclarationNameS(codeLocation)) => LambdaCitizenNameT(LambdaCitizenTemplateNameT(NameTranslator.translateCodeLocation(codeLocation)))
      case LetNameS(codeLocation) => LetNameT(translateCodeLocation(codeLocation))
      case ExportAsNameS(codeLocation) => ExportAsNameT(translateCodeLocation(codeLocation))
      case ClosureParamNameS() => ClosureParamNameT()
      case MagicParamNameS(codeLocation) => MagicParamNameT(translateCodeLocation(codeLocation))
      case CodeVarNameS(name) => CodeVarNameT(name)
      case t@TopLevelCitizenDeclarationNameS(_, _) => translateCitizenName(t)
      case LambdaDeclarationNameS(codeLocation) => {
        LambdaTemplateNameT(NameTranslator.translateCodeLocation(codeLocation))
      }
      case FunctionNameS(name, codeLocation) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation))
      }
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, codeLocation)) => {
        FunctionTemplateNameT(name, NameTranslator.translateCodeLocation(codeLocation.begin))
      }
      case ConstructorNameS(astn @ AnonymousSubstructTemplateNameS(_)) => {
        // See LNASC.
        AnonymousSubstructConstructorTemplateNameT(translateCitizenName(astn))
      }
      case AnonymousSubstructTemplateNameS(tlcd) => {
        // See LNASC.
        AnonymousSubstructTemplateNameT(translateCitizenName(tlcd))
      }
      case ImplDeclarationNameS(codeLocation) => {
        ImplDeclareNameT(codeLocation)
      }
      case _ => vimpl(name.toString)
    }
  }

  def translateCodeLocation(s: CodeLocationS): CodeLocationS = {
    val CodeLocationS(line, col) = s
    CodeLocationS(line, col)
  }

  def translateVarNameStep(name: IVarNameS): IVarNameT = {
    name match {
      //      case UnnamedLocalNameS(codeLocation) => UnnamedLocalNameT(translateCodeLocation(codeLocation))
      case ClosureParamNameS() => ClosureParamNameT()
      case SelfNameS() => SelfNameT()
      case MagicParamNameS(codeLocation) => MagicParamNameT(translateCodeLocation(codeLocation))
      case ConstructingMemberNameS(n) => ConstructingMemberNameT(n)
      case CodeVarNameS(name) => CodeVarNameT(name)
      case AnonymousSubstructMemberNameS(index) => AnonymousSubstructMemberNameT(index)
    }
  }

  def translateImplName(n: ImplDeclarationNameS): ImplDeclareNameT = {
    val ImplDeclarationNameS(l) = n
    ImplDeclareNameT(translateCodeLocation(l))
  }
}
