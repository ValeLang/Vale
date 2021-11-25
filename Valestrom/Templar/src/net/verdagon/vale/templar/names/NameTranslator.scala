package net.verdagon.vale.templar.names

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout._
import net.verdagon.vale.templar.types.CitizenRefT
import net.verdagon.vale.{CodeLocationS, vassert, vimpl, vwat}

object NameTranslator {
  def translateFunctionNameToTemplateName(functionName: IFunctionDeclarationNameS): IFunctionTemplateNameT = {
    functionName match {
//      case ImmConcreteDestructorNameS(_) => ImmConcreteDestructorTemplateNameT()
//      case ImmInterfaceDestructorNameS(_) => ImmInterfaceDestructorTemplateNameT()
//      case DropNameS(_) => DropTemplateNameT()
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

  //  def translateImpreciseTypeName(fullNameS: ImpreciseNameS[CodeTypeNameS]): ImpreciseName2[CodeTypeName2] = {
  //    val ImpreciseNameS(initS, lastS) = fullNameS
  //    ImpreciseName2(initS.map(translateImpreciseNameStep), translateCodeTypeName(lastS))
  //  }
  //
  //  def translateImpreciseName(fullNameS: ImpreciseNameS[INameS]): ImpreciseName2[IImpreciseNameStep2] = {
  //    val ImpreciseNameS(initS, lastS) = fullNameS
  //    ImpreciseName2(initS.map(translateImpreciseNameStep), translateImpreciseNameStep(lastS))
  //  }
  //
  //  def translateCodeTypeName(codeTypeNameS: CodeTypeNameS): CodeTypeName2 = {
  //    val CodeTypeNameS(name) = codeTypeNameS
  //    CodeTypeName2(name)
  //  }
  //
  //  def translateImpreciseNameStep(impreciseNameStepA: INameS): IImpreciseNameStep2 = {
  //    impreciseNameStepA match {
  //      case ctn @ CodeTypeNameS(_) => translateCodeTypeName(ctn)
  //      case GlobalFunctionFamilyNameS(name) => GlobalFunctionFamilyName2(name)
  //      case icvn @ ImpreciseCodeVarNameS(_) => translateImpreciseCodeVarNameStep(icvn)
  //    }
  //  }
  //
  //  def translateImpreciseCodeVarNameStep(impreciseNameStepA: ImpreciseCodeVarNameS): ImpreciseCodeVarName2 = {
  //    var ImpreciseCodeVarNameS(name) = impreciseNameStepA
  //    ImpreciseCodeVarName2(name)
  //  }
  //
  //  def translateRune(absoluteNameS: AbsoluteNameS[IRuneS]): FullName2[IRuneT] = {
  //    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
  //    FullName2(file, initS.map(translateNameStep), translateRune(lastS))
  //  }
  //
  //  def translateVarAbsoluteName(absoluteNameS: AbsoluteNameS[IVarNameS]): FullName2[IVarName2] = {
  //    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
  //    FullName2(file, initS.map(translateNameStep), translateVarNameStep(lastS))
  //  }
  //
  //  def translateVarImpreciseName(absoluteNameS: ImpreciseNameS[ImpreciseCodeVarNameS]):
  //  ImpreciseName2[ImpreciseCodeVarName2] = {
  //    val ImpreciseNameS(initS, lastS) = absoluteNameS
  //    ImpreciseName2(initS.map(translateImpreciseNameStep), translateImpreciseCodeVarNameStep(lastS))
  //  }
  //
  //  def translateFunctionFamilyName(name: ImpreciseNameS[GlobalFunctionFamilyNameS]):
  //  ImpreciseName2[GlobalFunctionFamilyName2] = {
  //    val ImpreciseNameS(init, last) = name
  //    ImpreciseName2(init.map(translateImpreciseNameStep), translateGlobalFunctionFamilyName(last))
  //  }
  //
  //  def translateGlobalFunctionFamilyName(s: GlobalFunctionFamilyNameS): GlobalFunctionFamilyName2 = {
  //    val GlobalFunctionFamilyNameS(name) = s
  //    GlobalFunctionFamilyName2(name)
  //  }
  //
  //  def translateName(absoluteNameS: AbsoluteNameS[INameS]): FullName2[IName2] = {
  //    val AbsoluteNameS(file, initS, lastS) = absoluteNameS
  //    FullName2(file, initS.map(translateNameStep), translateNameStep(lastS))
  //  }

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
      //      case LambdaNameS(codeLocation) => LambdaName2(codeLocation)
      //      case FunctionNameS(name, codeLocation) => FunctionName2(name, codeLocation)
      //      case TopLevelCitizenDeclarationNameS(name, codeLocation) => TopLevelCitizenDeclarationName2(name, codeLocation)
      //      case CodeNameS(n@("int" | "str")) => PrimitiveNameT(n)
      //      case ImplNameS(subCitizenName, codeLocation) => ImplDeclareNameT(translateNameStep(subCitizenName), translateCodeLocation(codeLocation))
      //      case UnnamedLocalNameS(codeLocation) => UnnamedLocalNameT(translateCodeLocation(codeLocation))
      //      case ImplicitRuneS(parentName, name) => ImplicitRuneT(translateNameStep(parentName), name)
      //      case CodeRuneS(name) => CodeRuneT(name)
      //      case MagicImplicitRuneS(codeLocationS) => MagicImplicitRuneT(codeLocationS)
      //      case AnonymousSubstructParentInterfaceRuneS() => AnonymousSubstructParentInterfaceRuneT()
      //      case LetImplicitRuneS(codeLocation, name) => LetImplicitRuneT(translateCodeLocation(codeLocation), name)
      //      case ImplicitRuneS(name) => ImplicitRune2(name)
      //      case MagicImplicitRuneS(magicParamIndex) => MagicImplicitRune2(magicParamIndex)
      //      case MemberRuneS(memberIndex) => MemberRuneT(memberIndex)
      //      case ReturnRuneS() => ReturnRuneT()
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

  //  def translateRune(rune: IRuneS): IRuneS = {
  //    rune match {
  //      case CodeRuneS(name) => CodeRuneS(name)
  ////      case ImplicitRuneS(containerName, name) => ImplicitRuneT(translateNameStep(containerName), name)
  ////      case LetImplicitRuneS(codeLocation, name) => LetImplicitRuneT(translateCodeLocation(codeLocation), name)
  ////      case MagicImplicitRuneS(codeLocation) => MagicImplicitRuneT(codeLocation)
  //      case MemberRuneS(memberIndex) => MemberRuneS(memberIndex)
  //      case ReturnRuneS() => ReturnRuneS()
  //      case ArraySizeImplicitRuneS() => ArraySizeImplicitRuneS()
  //      case ArrayVariabilityImplicitRuneS() => ArrayVariabilityImplicitRuneS()
  //      case ArrayMutabilityImplicitRuneS() => ArrayMutabilityImplicitRuneS()
  ////      case AnonymousSubstructParentInterfaceRuneS() => AnonymousSubstructParentInterfaceRuneT()
  //      case ExplicitTemplateArgRuneS(index) => ExplicitTemplateArgRuneS(index)
  //      case x => vimpl(x.toString)
  //    }
  //  }

  def translateImplName(n: ImplDeclarationNameS): ImplDeclareNameT = {
    val ImplDeclarationNameS(l) = n
    ImplDeclareNameT(translateCodeLocation(l))
  }

//  def getImplNameForNameInner(name: INameT): Option[ImplImpreciseNameS] = {
//    name match {
//      case CitizenNameT(humanName, templateArgs) => Some(ImplImpreciseNameS(humanName))
////      case TupleNameT(_) => None
//      case LambdaCitizenNameT(_) => None
//      case AnonymousSubstructNameT(interfaceName, _) => {
//        // Use the paren'ts name, see INSHN.
//        getImplNameForNameInner(interfaceName)
//      }
//      case _ => vwat()
//    }
//  }
//
//  // Gets the name of an impl that would be for this citizen.
//  // Returns None if it can't be in an impl.
//  def getImplNameForName(ref: CitizenRefT): Option[ImplImpreciseNameS] = {
//    getImplNameForNameInner(ref.fullName.last)
//  }
}
