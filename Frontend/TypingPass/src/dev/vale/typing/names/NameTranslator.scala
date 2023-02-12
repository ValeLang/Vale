package dev.vale.typing.names

import dev.vale.{CodeLocationS, Interner, vcurious, vfail, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.typing.types.{CoordT, ICitizenTT}
import dev.vale.highertyping._
import dev.vale.postparsing._

import scala.collection.mutable


class NameTranslator(interner: Interner) {
  def translateGenericTemplateFunctionName(
    functionName: IFunctionDeclarationNameS,
    params: Vector[CoordT]):
  IFunctionTemplateNameT = {
    functionName match {
      case LambdaDeclarationNameS(codeLocation) => {
        interner.intern(LambdaCallFunctionTemplateNameT(translateCodeLocation(codeLocation), params))
      }
      case other => vwat(other) // Only templates should call this
    }
  }

  def translateGenericFunctionName(functionName: IFunctionDeclarationNameS): IFunctionTemplateNameT = {
    functionName match {
      case LambdaDeclarationNameS(codeLocation) => {
        vfail() // Lambdas are generic templates, not generics
      }
      case FunctionNameS(name, codeLocation) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation)))
      }
      case ForwarderFunctionDeclarationNameS(inner, index) => {
        interner.intern(ForwarderFunctionTemplateNameT(translateGenericFunctionName(inner), index))
      }
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, codeLocation)) => {
        interner.intern(FunctionTemplateNameT(name, translateCodeLocation(codeLocation.begin)))
      }
      case ConstructorNameS(thing @ AnonymousSubstructTemplateNameS(_)) => {
        interner.intern(AnonymousSubstructConstructorTemplateNameT(translateCitizenName(thing)))
      }
    }
  }

  def translateStructName(name: IStructDeclarationNameS): IStructTemplateNameT = {
    name match {
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        interner.intern(StructTemplateNameT(humanName))
      }
      case AnonymousSubstructTemplateNameS(interfaceName) => {
        // Now strip it off, stuff it inside our new name. See LNASC.
        interner.intern(AnonymousSubstructTemplateNameT(translateInterfaceName(interfaceName)))
      }
    }
  }

  def translateInterfaceName(name: IInterfaceDeclarationNameS): IInterfaceTemplateNameT = {
    name match {
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        interner.intern(InterfaceTemplateNameT(humanName))
      }
    }
  }

  def translateCitizenName(name: ICitizenDeclarationNameS): ICitizenTemplateNameT = {
    name match {
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        interner.intern(StructTemplateNameT(humanName))
      }
      case AnonymousSubstructTemplateNameS(interfaceName) => {
        // Now strip it off, stuff it inside our new name. See LNASC.
        interner.intern(AnonymousSubstructTemplateNameT(translateInterfaceName(interfaceName)))
      }
      case TopLevelCitizenDeclarationNameS(humanName, codeLocation) => {
        interner.intern(InterfaceTemplateNameT(humanName))
      }
    }
  }

  def translateNameStep(name: INameS): INameT = {
    name match {
      case LambdaStructDeclarationNameS(LambdaDeclarationNameS(codeLocation)) => interner.intern(LambdaCitizenNameT(interner.intern(LambdaCitizenTemplateNameT(translateCodeLocation(codeLocation)))))
      case LetNameS(codeLocation) => interner.intern(LetNameT(translateCodeLocation(codeLocation)))
      case ExportAsNameS(codeLocation) => interner.intern(ExportAsNameT(translateCodeLocation(codeLocation)))
      case ClosureParamNameS(codeLocation) => interner.intern(ClosureParamNameT(codeLocation))
      case MagicParamNameS(codeLocation) => interner.intern(MagicParamNameT(translateCodeLocation(codeLocation)))
      case CodeVarNameS(name) => interner.intern(CodeVarNameT(name))
      case s @ TopLevelStructDeclarationNameS(_, _) => translateStructName(s)
      case s @ TopLevelInterfaceDeclarationNameS(_, _) => translateInterfaceName(s)
      case LambdaDeclarationNameS(codeLocation) => {
        vcurious()
//        interner.intern(LambdaTemplateNameT(translateCodeLocation(codeLocation)))
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
        interner.intern(AnonymousSubstructTemplateNameT(translateInterfaceName(tlcd)))
      }
      case AnonymousSubstructImplDeclarationNameS(tlcd) => {
        // See LNASC.
        interner.intern(AnonymousSubstructImplTemplateNameT(translateInterfaceName(tlcd)))
      }
      case ImplDeclarationNameS(codeLocation) => {
        vimpl()
//        interner.intern(ImplDeclareNameT(codeLocation))
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
      case ClosureParamNameS(codeLocation) => interner.intern(ClosureParamNameT(codeLocation))
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

  def translateImplName(n: IImplDeclarationNameS): IImplTemplateNameT = {
    n match {
      case ImplDeclarationNameS(l) => {
        interner.intern(ImplTemplateNameT(translateCodeLocation(l)))
      }
      case AnonymousSubstructImplDeclarationNameS(interfaceName) => {
        interner.intern(AnonymousSubstructImplTemplateNameT(translateInterfaceName(interfaceName)))
      }
    }
  }
}
