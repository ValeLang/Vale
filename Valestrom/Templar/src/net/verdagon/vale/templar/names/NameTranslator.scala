package net.verdagon.vale.templar.names

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout._
import net.verdagon.vale.templar.types.CitizenRefT
import net.verdagon.vale.{CodeLocationS, Interner, vassert, vimpl, vwat}

import scala.collection.mutable


class NameTranslator(interner: Interner) {
  def translateFunctionNameToTemplateName(functionName: IFunctionDeclarationNameS): IFunctionTemplateNameT = {
    functionName match {
      case LambdaDeclarationNameS(/*parent, */ codeLocation) => {
        interner.intern(LambdaTemplateNameT(translateCodeLocation(codeLocation)))
      }
      case FreeDeclarationNameS(codeLocationS) => {
        interner.intern(FreeTemplateNameT(translateCodeLocation(codeLocationS)))
      }
      case AbstractVirtualFreeDeclarationNameS(codeLoc) => {
        interner.intern(AbstractVirtualFreeTemplateNameT(codeLoc))
      }
      case OverrideVirtualFreeDeclarationNameS(codeLoc) => {
        interner.intern(OverrideVirtualFreeTemplateNameT(codeLoc))
      }
      case FunctionNameS(name, codeLocation) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation)))
      }
      case ForwarderFunctionDeclarationNameS(inner, index) => {
        interner.intern(ForwarderFunctionTemplateNameT(translateFunctionNameToTemplateName(inner), index))
      }
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, codeLocation)) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation.begin)))
      }
      case ConstructorNameS(thing @ AnonymousSubstructTemplateNameS(_)) => {
        interner.intern(AnonymousSubstructConstructorTemplateNameT(translateCitizenName(thing)))
      }
      case OverrideVirtualDropFunctionDeclarationNameS(implName) => {
        interner.intern(OverrideVirtualDropFunctionTemplateNameT(
          translateNameStep(implName)))
      }
      case AbstractVirtualDropFunctionDeclarationNameS(implName) => {
        interner.intern(AbstractVirtualDropFunctionTemplateNameT(
          translateNameStep(implName)))
      }
    }
  }

  def translateCitizenName(name: ICitizenDeclarationNameS): ICitizenTemplateNameT = {
    name match {
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        interner.intern(CitizenTemplateNameT(humanName))
      }
      case AnonymousSubstructTemplateNameS(interfaceName) => {
        // Now strip it off, stuff it inside our new name. See LNASC.
        interner.intern(AnonymousSubstructTemplateNameT(translateCitizenName(interfaceName)))
      }
    }
  }

  def translateNameStep(name: INameS): INameT = {
    name match {
      case LambdaStructDeclarationNameS(LambdaDeclarationNameS(codeLocation)) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(translateCodeLocation(codeLocation)))))
      case LetNameS(codeLocation) => interner.intern(LetNameT(translateCodeLocation(codeLocation)))
      case ExportAsNameS(codeLocation) => interner.intern(ExportAsNameT(translateCodeLocation(codeLocation)))
      case ClosureParamNameS() => interner.intern(ClosureParamNameT())
      case MagicParamNameS(codeLocation) => interner.intern(MagicParamNameT(translateCodeLocation(codeLocation)))
      case CodeVarNameS(name) => interner.intern(CodeVarNameT(name))
      case t@TopLevelCitizenDeclarationNameS(_, _) => translateCitizenName(t)
      case LambdaDeclarationNameS(codeLocation) => {
        interner.intern(LambdaTemplateNameT(translateCodeLocation(codeLocation)))
      }
      case FunctionNameS(name, codeLocation) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation)))
      }
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, codeLocation)) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation.begin)))
      }
      case ConstructorNameS(astn @ AnonymousSubstructTemplateNameS(_)) => {
        // See LNASC.
        interner.intern(AnonymousSubstructConstructorTemplateNameT(translateCitizenName(astn)))
      }
      case AnonymousSubstructTemplateNameS(tlcd) => {
        // See LNASC.
        interner.intern(AnonymousSubstructTemplateNameT(translateCitizenName(tlcd)))
      }
      case AnonymousSubstructImplDeclarationNameS(tlcd) => {
        // See LNASC.
        interner.intern(AnonymousSubstructImplTemplateNameT(translateCitizenName(tlcd)))
      }
      case ImplDeclarationNameS(codeLocation) => {
        interner.intern(ImplDeclareNameT(codeLocation))
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
      case ClosureParamNameS() => interner.intern(ClosureParamNameT())
      case SelfNameS() => interner.intern(SelfNameT())
      case IterableNameS(range) => interner.intern(IterableNameT(range))
      case IteratorNameS(range) => interner.intern(IteratorNameT(range))
      case IterationOptionNameS(range) => interner.intern(IterationOptionNameT(range))
      case MagicParamNameS(codeLocation) => interner.intern(MagicParamNameT(translateCodeLocation(codeLocation)))
      case ConstructingMemberNameS(n) => interner.intern(ConstructingMemberNameT(n))
      case WhileCondResultNameS(range) => interner.intern(WhileCondResultNameT(range))
      case CodeVarNameS(name) => interner.intern(CodeVarNameT(name))
      case AnonymousSubstructMemberNameS(index) => interner.intern(AnonymousSubstructMemberNameT(index))
    }
  }

  def translateImplName(n: IImplDeclarationNameS): IImplDeclareNameT = {
    n match {
      case ImplDeclarationNameS(l) => {
        interner.intern(ImplDeclareNameT(translateCodeLocation(l)))
      }
      case AnonymousSubstructImplDeclarationNameS(interfaceName) => {
        interner.intern(AnonymousSubstructImplDeclarationNameT(translateNameStep(interfaceName)))
      }
    }
  }
}
