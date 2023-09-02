package dev.vale.instantiating

import dev.vale.instantiating.ast._
import dev.vale.{vassertSome, vimpl, vwat}

import scala.collection.immutable.Map

// See ICRHRC for why/when we count the regions.
// This one will collapse every node based on only the things it contains.
// It creates a new collapsing map for each one.
object RegionCollapserIndividual {
  def collapsePrototype(prototype: PrototypeI[sI]): PrototypeI[cI] = {
    val PrototypeI(id, returnType) = prototype
    PrototypeI(
      collapseFunctionId(id),
      collapseCoord(returnType))
  }

  def collapseId[T <: INameI[sI], Y <: INameI[cI]](
    id: IdI[sI, T],
    func: T => Y):
  IdI[cI, Y] = {
    val IdI(packageCoord, initSteps, localName) = id
    IdI(
      packageCoord,
      initSteps.map(x => collapseName(x)),
      func(localName))
  }

  def collapseFunctionId(
    id: IdI[sI, IFunctionNameI[sI]]):
  IdI[cI, IFunctionNameI[cI]] = {
    collapseId[IFunctionNameI[sI], IFunctionNameI[cI]](
      id,
      x => collapseFunctionName(x))
  }

  def collapseFunctionName(
    name: IFunctionNameI[sI]):
  IFunctionNameI[cI] = {
    name match {
      case n @ FunctionNameIX(FunctionTemplateNameI(humanName, codeLocation), templateArgs, parameters) => {
        val map = RegionCounter.countFunctionName(n)
        val templateC = FunctionTemplateNameI[cI](humanName, codeLocation)
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(param)
          })
        FunctionNameIX[cI](templateC, templateArgsC, paramsC)
      }
      case ExternFunctionNameI(humanName, parameters) => {
        val paramsC =
          parameters.map(param => {
            collapseCoord(param)
          })
        ExternFunctionNameI[cI](humanName, paramsC)
      }
      case n @ LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(codeLocation, paramsTT), templateArgs, parameters) => {
        val map = RegionCounter.countFunctionName(n)
        val templateC = LambdaCallFunctionTemplateNameI[cI](codeLocation, paramsTT)
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(param)
          })
        LambdaCallFunctionNameI[cI](templateC, templateArgsC, paramsC)
      }
      case n @ AnonymousSubstructConstructorNameI(AnonymousSubstructConstructorTemplateNameI(substruct), templateArgs, parameters) => {
        val map = RegionCounter.countFunctionName(n)
        val templateC = AnonymousSubstructConstructorTemplateNameI[cI](collapseCitizenTemplateName(substruct))
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(param)
          })
        AnonymousSubstructConstructorNameI[cI](templateC, templateArgsC, paramsC)
      }
      // case n@OverrideDispatcherNameI(OverrideDispatcherTemplateNameI(implId), templateArgs, parameters) => {
      //   val map = RegionCounter.countFunctionName(n)
      //   val templateC = OverrideDispatcherTemplateNameI[cI](collapseImplTemplateId(implId))
      //   val templateArgsC = templateArgs.map(collapseTemplata(map, _))
      //   val paramsC =
      //     parameters.map(param => {
      //       collapseCoord(param)
      //     })
      //   OverrideDispatcherNameI[cI](templateC, templateArgsC, paramsC)
      // }
      // case n@CaseFunctionFromImplNameI(CaseFunctionFromImplTemplateNameI(humanName, runeInImpl, runeInCitizen), templateArgs, parameters) => {
      //   val map = RegionCounter.countFunctionName(n)
      //   val templateC = CaseFunctionFromImplTemplateNameI[cI](humanName, runeInImpl, runeInCitizen)
      //   val templateArgsC = templateArgs.map(collapseTemplata(map, _))
      //   val paramsC =
      //     parameters.map(param => {
      //       collapseCoord(param)
      //     })
      //   CaseFunctionFromImplNameI[cI](templateC, templateArgsC, paramsC)
      // }
      case ForwarderFunctionNameI(ForwarderFunctionTemplateNameI(innerTemplate, index), funcName) => {
        ForwarderFunctionNameI(
          ForwarderFunctionTemplateNameI(collapseFunctionTemplateName(innerTemplate), index),
          collapseFunctionName(funcName))
      }
      case other => vimpl(other)
    }
  }

  def collapseCitizenTemplateName(citizen: ICitizenTemplateNameI[sI]): ICitizenTemplateNameI[cI] = {
    citizen match {
      case s : IStructTemplateNameI[_] => {
        collapseStructTemplateName(s.asInstanceOf[IStructTemplateNameI[sI]])
      }
      case i : IInterfaceTemplateNameI[_] => {
        collapseInterfaceTemplateName(i.asInstanceOf[IInterfaceTemplateNameI[sI]])
      }
      case other => vimpl(other)
    }
  }

  def collapseVarName(
    name: IVarNameI[sI]):
  IVarNameI[cI] = {
    name match {
      case TypingPassBlockResultVarNameI(life) => TypingPassBlockResultVarNameI(life)
      case CodeVarNameI(name) => CodeVarNameI(name)
      case TypingPassTemporaryVarNameI(life) => TypingPassTemporaryVarNameI(life)
      case TypingPassFunctionResultVarNameI() => TypingPassFunctionResultVarNameI()
      case ClosureParamNameI(codeLocation) => ClosureParamNameI(codeLocation)
      case MagicParamNameI(codeLocation2) => MagicParamNameI(codeLocation2)
      case IterableNameI(range) => IterableNameI(range)
      case ConstructingMemberNameI(name) => ConstructingMemberNameI(name)
      case IteratorNameI(range) => IteratorNameI(range)
      case IterationOptionNameI(range) => IterationOptionNameI(range)
      case SelfNameI() => SelfNameI()
    }
  }

  def collapseFunctionTemplateName(
      functionName: IFunctionTemplateNameI[sI]):
  IFunctionTemplateNameI[cI] = {
    functionName match {
      case FunctionTemplateNameI(humanName, codeLocation) => FunctionTemplateNameI(humanName, codeLocation)
    }
  }

  def collapseName(
    name: INameI[sI]):
  INameI[cI] = {
    name match {
      case n : IFunctionNameI[_] => collapseFunctionName(n.asInstanceOf[IFunctionNameI[sI]])
      case x : IFunctionTemplateNameI[_] => collapseFunctionTemplateName(x.asInstanceOf[IFunctionTemplateNameI[sI]])
      case StructTemplateNameI(humanName) => StructTemplateNameI(humanName)
      case x @ LambdaCitizenNameI(_) => collapseStructName(x)
      case LambdaCitizenTemplateNameI(codeLocation) => LambdaCitizenTemplateNameI(codeLocation)
      case n @ LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(codeLocation, paramTypes), templateArgs, parameters) => collapseFunctionName(n)
      case InterfaceTemplateNameI(humanName) => InterfaceTemplateNameI(humanName)
      case AnonymousSubstructTemplateNameI(interface) => AnonymousSubstructTemplateNameI(collapseInterfaceTemplateName(interface))
      case other => vimpl(other)
    }
  }

  def collapseCoordTemplata(
      map: Map[Int, Int],
      templata: CoordTemplataI[sI]):
  CoordTemplataI[cI] = {
    val CoordTemplataI(region, coord) = templata
    CoordTemplataI(collapseRegionTemplata(map, region), collapseCoord(coord))
  }

  def collapseTemplata(
    map: Map[Int, Int],
    templata: ITemplataI[sI]):
  ITemplataI[cI] = {
    templata match {
      case c @ CoordTemplataI(_, _) => collapseCoordTemplata(map, c)
      case KindTemplataI(kind) => KindTemplataI(collapseKind(kind))
      case r @ RegionTemplataI(_) => collapseRegionTemplata(map, r)
      case MutabilityTemplataI(mutability) => MutabilityTemplataI(mutability)
      case IntegerTemplataI(x) => IntegerTemplataI(x)
      case VariabilityTemplataI(variability) => VariabilityTemplataI(variability)
      case other => vimpl(other)
    }
  }

  def collapseRegionTemplata(
    map: Map[Int, Int],
    templata: RegionTemplataI[sI]):
  RegionTemplataI[cI] = {
    val RegionTemplataI(oldPureHeight) = templata
    RegionTemplataI[cI](vassertSome(map.get(oldPureHeight)))
  }

  def collapseCoord(
    coord: CoordI[sI]):
  CoordI[cI] = {
    val CoordI(ownership, kind) = coord
    CoordI(ownership, collapseKind(kind))
  }

  def collapseKind(
    kind: KindIT[sI]):
  KindIT[cI] = {
    kind match {
      case NeverIT(fromBreak) => NeverIT(fromBreak)
      case VoidIT() => VoidIT()
      case IntIT(x) => IntIT(x)
      case BoolIT() => BoolIT()
      case FloatIT() => FloatIT()
      case StrIT() => StrIT()
      case StructIT(id) => StructIT(collapseStructId(id))
      case InterfaceIT(id) => InterfaceIT(collapseInterfaceId(id))
      case ssa @ StaticSizedArrayIT(_) => collapseStaticSizedArray(ssa)
      case rsa @ RuntimeSizedArrayIT(_) => collapseRuntimeSizedArray(rsa)
    }
  }

  def collapseRuntimeSizedArray(
    rsa: RuntimeSizedArrayIT[sI]):
  RuntimeSizedArrayIT[cI] = {
    val RuntimeSizedArrayIT(rsaId) = rsa
    val map = RegionCounter.countRuntimeSizedArray(rsa)
    RuntimeSizedArrayIT(
      collapseId[RuntimeSizedArrayNameI[sI], RuntimeSizedArrayNameI[cI]](
        rsaId,
        { case RuntimeSizedArrayNameI(RuntimeSizedArrayTemplateNameI(), RawArrayNameI(mutability, elementType, selfRegion)) =>
          RuntimeSizedArrayNameI(
            RuntimeSizedArrayTemplateNameI(),
            RawArrayNameI(
              mutability,
              collapseTemplata(map, elementType).expectCoordTemplata(),
              collapseRegionTemplata(map, selfRegion)))
        }))
  }

  def collapseStaticSizedArray(
    ssa: StaticSizedArrayIT[sI]):
  StaticSizedArrayIT[cI] = {
    val StaticSizedArrayIT(ssaId) = ssa
    val map = RegionCounter.countStaticSizedArray(ssa)
    StaticSizedArrayIT(
      collapseId[StaticSizedArrayNameI[sI], StaticSizedArrayNameI[cI]](
        ssaId,
        { case StaticSizedArrayNameI(StaticSizedArrayTemplateNameI(), size, variability, RawArrayNameI(mutability, elementType, selfRegion)) =>
          StaticSizedArrayNameI(
            StaticSizedArrayTemplateNameI(),
            size,
            variability,
            RawArrayNameI(
              mutability,
              collapseTemplata(map, elementType).expectCoordTemplata(),
              collapseRegionTemplata(map, selfRegion)))
        }))
  }

  def collapseInterfaceId(
      interfaceId: IdI[sI, IInterfaceNameI[sI]]):
  IdI[cI, IInterfaceNameI[cI]] = {
    collapseId[IInterfaceNameI[sI], IInterfaceNameI[cI]](
      interfaceId,
      x => collapseInterfaceName(x))
  }

  def collapseStructId(
    structId: IdI[sI, IStructNameI[sI]]):
  IdI[cI, IStructNameI[cI]] = {
    collapseId[IStructNameI[sI], IStructNameI[cI]](
      structId,
      x => collapseStructName(x))
  }

  def collapseStructName(
      structName: IStructNameI[sI]):
  IStructNameI[cI] = {
    structName match {
      case StructNameI(template, templateArgs) => {
        val map = RegionCounter.countCitizenName(structName)
        StructNameI(
          collapseStructTemplateName(template),
          templateArgs.map(collapseTemplata(map, _)))
      }
      case LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation)) => {
        LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation))
      }
      case AnonymousSubstructNameI(AnonymousSubstructTemplateNameI(interface), templateArgs) => {
        val map = RegionCounter.countCitizenName(structName)
        AnonymousSubstructNameI(
          AnonymousSubstructTemplateNameI(collapseInterfaceTemplateName(interface)),
          templateArgs.map(collapseTemplata(map, _)))
      }
    }
  }

  def collapseImplName(
      implName: IImplNameI[sI]):
  IImplNameI[cI] = {
    implName match {
      case ImplNameI(template, templateArgs, subCitizen) => {
        val map = RegionCounter.countImplName(implName)
        ImplNameI[cI](
          collapseImplTemplateName(template),
          templateArgs.map(collapseTemplata(map, _)),
          collapseCitizen(subCitizen))
      }
      case ImplBoundNameI(ImplBoundTemplateNameI(codeLocationS), templateArgs) => {
        val map = RegionCounter.countImplName(implName)
        ImplBoundNameI(
          ImplBoundTemplateNameI(codeLocationS),
          templateArgs.map(collapseTemplata(map, _)))
      }
      case AnonymousSubstructImplNameI(AnonymousSubstructImplTemplateNameI(interface), templateArgs, subCitizen) => {
        val map = RegionCounter.countImplName(implName)
        AnonymousSubstructImplNameI[cI](
          AnonymousSubstructImplTemplateNameI(collapseInterfaceTemplateName(interface)),
          templateArgs.map(collapseTemplata(map, _)),
          collapseCitizen(subCitizen))
      }
    }
  }

  def collapseInterfaceName(
      interfaceName: IInterfaceNameI[sI]):
  IInterfaceNameI[cI] = {
    interfaceName match {
      case InterfaceNameI(InterfaceTemplateNameI(humanNamee), templateArgs) => {
        val map = RegionCounter.countCitizenName(interfaceName)
        InterfaceNameI(
          InterfaceTemplateNameI(humanNamee),
          templateArgs.map(collapseTemplata(map, _)))
      }
      case other => vimpl(other)
    }
  }

  def collapseExportId(
    map: Map[Int, Int],
    structId: IdI[sI, ExportNameI[sI]]):
  IdI[cI, ExportNameI[cI]] = {
    collapseId[ExportNameI[sI], ExportNameI[cI]](
      structId,
      { case ExportNameI(ExportTemplateNameI(codeLoc), templateArg) =>
        ExportNameI(
          ExportTemplateNameI(codeLoc),
          collapseRegionTemplata(map, templateArg))
      })
  }

  def collapseExternId(
    map: Map[Int, Int],
    structId: IdI[sI, ExternNameI[sI]]):
  IdI[cI, ExternNameI[cI]] = {
    collapseId[ExternNameI[sI], ExternNameI[cI]](
      structId,
      { case ExternNameI(ExternTemplateNameI(codeLoc), templateArg) =>
        ExternNameI(
          ExternTemplateNameI(codeLoc),
          collapseRegionTemplata(map, templateArg))
      })
  }

  def collapseStructTemplateName(
    structName: IStructTemplateNameI[sI]):
  IStructTemplateNameI[cI] = {
    structName match {
      case StructTemplateNameI(humanName) => StructTemplateNameI(humanName)
      case AnonymousSubstructTemplateNameI(interface) => AnonymousSubstructTemplateNameI(collapseInterfaceTemplateName(interface))
    }
  }

  def collapseInterfaceTemplateName(
      structName: IInterfaceTemplateNameI[sI]):
  IInterfaceTemplateNameI[cI] = {
    structName match {
      case InterfaceTemplateNameI(humanName) => InterfaceTemplateNameI(humanName)
    }
  }

  def collapseImplId(
      implId: IdI[sI, IImplNameI[sI]]):
  IdI[cI, IImplNameI[cI]] = {
    collapseId[IImplNameI[sI], IImplNameI[cI]](
      implId,
      x => collapseImplName(x))
  }
  //
  // def collapseImplTemplateId(
  //     implId: IdI[sI, IImplTemplateNameI[sI]]):
  // IdI[cI, IImplTemplateNameI[cI]] = {
  //   collapseId[IImplTemplateNameI[sI], IImplTemplateNameI[cI]](
  //     implId,
  //     x => collapseImplTemplateName(x))
  // }

  def collapseImplTemplateName(
    structName: IImplTemplateNameI[sI]):
  IImplTemplateNameI[cI] = {
    structName match {
      case ImplTemplateNameI(humanName) => ImplTemplateNameI(humanName)
      case AnonymousSubstructImplTemplateNameI(interface) => AnonymousSubstructImplTemplateNameI(collapseInterfaceTemplateName(interface))
    }
  }

  def collapseCitizen(
      citizen: ICitizenIT[sI]):
  ICitizenIT[cI] = {
    citizen match {
      case StructIT(structIdT) => StructIT(collapseStructId(structIdT))
      case InterfaceIT(interfaceIdT) => InterfaceIT(collapseInterfaceId(interfaceIdT))
    }
  }

}
