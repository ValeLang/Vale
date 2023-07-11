package dev.vale.instantiating

import dev.vale.instantiating.ast._
import dev.vale.{vassertSome, vimpl}

// See ICRHRC for why/when we count the regions.
// This one will use one map for the entire deep collapse, rather than making a new map for everything.
object RegionCollapserConsistent {

  def collapsePrototype(map: Map[Int, Int], prototype: PrototypeI[sI]): PrototypeI[nI] = {
    val PrototypeI(id, returnType) = prototype
    PrototypeI(
      collapseFunctionId(map, id),
      collapseCoord(map, returnType))
  }

  def collapseId[T <: INameI[sI], Y <: INameI[nI]](
      map: Map[Int, Int],
      id: IdI[sI, T],
      func: T => Y):
  IdI[nI, Y] = {
    val IdI(packageCoord, initSteps, localName) = id
    IdI(
      packageCoord,
      initSteps.map(x => collapseName(map, x)),
      func(localName))
  }

  def collapseFunctionId(
      map: Map[Int, Int],
      id: IdI[sI, IFunctionNameI[sI]]):
  IdI[nI, IFunctionNameI[nI]] = {
    collapseId[IFunctionNameI[sI], IFunctionNameI[nI]](
      map,
      id,
      x => collapseFunctionName(map, x))
  }

  def collapseFunctionName(
      map: Map[Int, Int],
      name: IFunctionNameI[sI]):
  IFunctionNameI[nI] = {
    name match {
      case n @ FunctionNameIX(FunctionTemplateNameI(humanName, codeLocation), templateArgs, parameters) => {
        val templateC = FunctionTemplateNameI[nI](humanName, codeLocation)
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(map, param)
          })
        FunctionNameIX[nI](templateC, templateArgsC, paramsC)
      }
      case ExternFunctionNameI(humanName, parameters) => {
        val paramsC =
          parameters.map(param => {
            collapseCoord(map, param)
          })
        ExternFunctionNameI[nI](humanName, paramsC)
      }
      case LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(codeLocation, paramsTT), templateArgs, parameters) => {
        val templateC = LambdaCallFunctionTemplateNameI[nI](codeLocation, paramsTT)
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(map, param)
          })
        LambdaCallFunctionNameI[nI](templateC, templateArgsC, paramsC)
      }
      case AnonymousSubstructConstructorNameI(AnonymousSubstructConstructorTemplateNameI(substruct), templateArgs, parameters) => {
        val templateC = AnonymousSubstructConstructorTemplateNameI[nI](collapseCitizenTemplateName(substruct))
        val templateArgsC = templateArgs.map(collapseTemplata(map, _))
        val paramsC =
          parameters.map(param => {
            collapseCoord(map, param)
          })
        AnonymousSubstructConstructorNameI[nI](templateC, templateArgsC, paramsC)
      }
      case ForwarderFunctionNameI(ForwarderFunctionTemplateNameI(funcTemplateName, index), funcName) => {
        ForwarderFunctionNameI(
          ForwarderFunctionTemplateNameI(collapseFunctionTemplateName(funcTemplateName), index),
          collapseFunctionName(map, funcName))
      }
    }
  }

  def collapseVarName(
    name: IVarNameI[sI]):
  IVarNameI[sI] = {
    name match {
      case TypingPassBlockResultVarNameI(life) => TypingPassBlockResultVarNameI(life)
      case CodeVarNameI(name) => CodeVarNameI(name)
      case TypingPassTemporaryVarNameI(life) => TypingPassTemporaryVarNameI(life)
      case TypingPassFunctionResultVarNameI() => TypingPassFunctionResultVarNameI()
    }
  }

  def collapseName(
      map: Map[Int, Int],
      name: INameI[sI]):
  INameI[nI] = {
    name match {
      case s: IStructTemplateNameI[_] => {
        collapseStructTemplateName(s.asInstanceOf[IStructTemplateNameI[sI]])
      }
      case s: IInterfaceTemplateNameI[_] => {
        collapseInterfaceTemplateName(s.asInstanceOf[IInterfaceTemplateNameI[sI]])
      }
      case s: IFunctionTemplateNameI[_] => {
        collapseFunctionTemplateName(s.asInstanceOf[IFunctionTemplateNameI[sI]])
      }
      case n @ FunctionNameIX(_, _, _) => {
        collapseFunctionName(map, n)
      }
      case n @ LambdaCallFunctionNameI(_, _, _) => collapseFunctionName(map, n)
      case other => vimpl(other)
    }
  }

  def collapseCoordTemplata(
      map: Map[Int, Int],
      templata: CoordTemplataI[sI]):
  CoordTemplataI[nI] = {
    val CoordTemplataI(region, coord) = templata
    CoordTemplataI(collapseRegionTemplata(map, region), collapseCoord(map, coord))
  }

  def collapseTemplata(
    map: Map[Int, Int],
    templata: ITemplataI[sI]):
  ITemplataI[nI] = {
    templata match {
      case c @ CoordTemplataI(_, _) => collapseCoordTemplata(map, c)
      case KindTemplataI(kind) => KindTemplataI(collapseKind(map, kind))
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
  RegionTemplataI[nI] = {
    val RegionTemplataI(oldPureHeight) = templata
    RegionTemplataI[nI](vassertSome(map.get(oldPureHeight)))
  }

  def collapseCoord(
      map: Map[Int, Int],
      coord: CoordI[sI]):
  CoordI[nI] = {
    val CoordI(ownership, kind) = coord
    CoordI(ownership, collapseKind(map, kind))
  }

  def collapseKind(
      map: Map[Int, Int],
      kind: KindIT[sI]):
  KindIT[nI] = {
    kind match {
      case NeverIT(fromBreak) => NeverIT(fromBreak)
      case VoidIT() => VoidIT()
      case IntIT(x) => IntIT(x)
      case BoolIT() => BoolIT()
      case FloatIT() => FloatIT()
      case StrIT() => StrIT()
      case StructIT(id) => StructIT(collapseStructId(map, id))
      case InterfaceIT(id) => InterfaceIT(collapseInterfaceId(map, id))
      case ssa @ StaticSizedArrayIT(_) => collapseStaticSizedArray(map, ssa)
      case rsa @ RuntimeSizedArrayIT(_) => collapseRuntimeSizedArray(map, rsa)
    }
  }

  def collapseRuntimeSizedArray(
    map: Map[Int, Int],
    rsa: RuntimeSizedArrayIT[sI]):
  RuntimeSizedArrayIT[nI] = {
    val RuntimeSizedArrayIT(ssaId) = rsa
    RuntimeSizedArrayIT(
      collapseId[RuntimeSizedArrayNameI[sI], RuntimeSizedArrayNameI[nI]](
        map,
        ssaId,
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
    map: Map[Int, Int],
    ssa: StaticSizedArrayIT[sI]):
  StaticSizedArrayIT[nI] = {
    val StaticSizedArrayIT(ssaId) = ssa
    StaticSizedArrayIT(
      collapseId[StaticSizedArrayNameI[sI], StaticSizedArrayNameI[nI]](
        map,
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

  def collapseCitizen(
      map: Map[Int, Int],
      citizen: ICitizenIT[sI]):
  ICitizenIT[nI] = {
    citizen match {
      case StructIT(structId) => StructIT(collapseStructId(map, structId))
      case InterfaceIT(structId) => InterfaceIT(collapseInterfaceId(map, structId))
    }
  }

  def collapseCitizenId(
      map: Map[Int, Int],
      implId: IdI[sI, ICitizenNameI[sI]]):
  IdI[nI, ICitizenNameI[nI]] = {
    collapseId[ICitizenNameI[sI], ICitizenNameI[nI]](
      map,
      implId,
      collapseCitizenName(map, _))
  }

  def collapseCitizenName(
      map: Map[Int, Int],
      citizenName: ICitizenNameI[sI]):
  ICitizenNameI[nI] = {
    citizenName match {
      case s: IStructNameI[_] => collapseStructName(map, s.asInstanceOf[IStructNameI[sI]])
      case i: IInterfaceNameI[_] => collapseInterfaceName(map, i.asInstanceOf[IInterfaceNameI[sI]])
    }
  }

  def collapseStructName(
      map: Map[Int, Int],
      structName: IStructNameI[sI]):
  IStructNameI[nI] = {
    structName match {
      case StructNameI(template, templateArgs) => {
        StructNameI(
          collapseStructTemplateName(template),
          templateArgs.map(collapseTemplata(map, _)))
      }
      case LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation)) => {
        LambdaCitizenNameI(LambdaCitizenTemplateNameI(codeLocation))
      }
      case AnonymousSubstructNameI(AnonymousSubstructTemplateNameI(interface), templateArgs) => {
        AnonymousSubstructNameI(
          AnonymousSubstructTemplateNameI(collapseInterfaceTemplateName(interface)),
          templateArgs.map(collapseTemplata(map, _)))
      }
    }
  }

  def collapseStructId(
    map: Map[Int, Int],
    structId: IdI[sI, IStructNameI[sI]]):
  IdI[nI, IStructNameI[nI]] = {
    collapseId[IStructNameI[sI], IStructNameI[nI]](
      map,
      structId,
      collapseStructName(map, _))
  }

  def collapseInterfaceName(
      map: Map[Int, Int],
      interfaceName: IInterfaceNameI[sI]):
  IInterfaceNameI[nI] = {
    interfaceName match {
      case InterfaceNameI(template, templateArgs) => {
        InterfaceNameI(
          collapseInterfaceTemplateName(template),
          templateArgs.map(collapseTemplata(map, _)))
      }
    }
  }

  def collapseInterfaceId(
      map: Map[Int, Int],
      interfaceId: IdI[sI, IInterfaceNameI[sI]]):
  IdI[nI, IInterfaceNameI[nI]] = {
    collapseId[IInterfaceNameI[sI], IInterfaceNameI[nI]](
      map,
      interfaceId,
      collapseInterfaceName(map, _))
  }

  def collapseExportId(
    map: Map[Int, Int],
    structId: IdI[sI, ExportNameI[sI]]):
  IdI[nI, ExportNameI[nI]] = {
    collapseId[ExportNameI[sI], ExportNameI[nI]](
      map,
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
  IdI[nI, ExternNameI[nI]] = {
    collapseId[ExternNameI[sI], ExternNameI[nI]](
      map,
      structId,
      { case ExternNameI(ExternTemplateNameI(codeLoc), templateArg) =>
        ExternNameI(
          ExternTemplateNameI(codeLoc),
          collapseRegionTemplata(map, templateArg))
      })
  }

  def collapseCitizenTemplateName(
      citizenName: ICitizenTemplateNameI[sI]):
  ICitizenTemplateNameI[nI] = {
    citizenName match {
      case s : IStructTemplateNameI[_] => {
        collapseStructTemplateName(s.asInstanceOf[IStructTemplateNameI[nI]])
      }
      case s: IInterfaceTemplateNameI[_] => {
        collapseInterfaceTemplateName(s.asInstanceOf[IInterfaceTemplateNameI[nI]])
      }
    }
  }

  def collapseStructTemplateName(
    structName: IStructTemplateNameI[sI]):
  IStructTemplateNameI[nI] = {
    structName match {
      case StructTemplateNameI(humanName) => StructTemplateNameI(humanName)
      case AnonymousSubstructTemplateNameI(interface) => AnonymousSubstructTemplateNameI(collapseInterfaceTemplateName(interface))
      case LambdaCitizenTemplateNameI(codeLocation) => LambdaCitizenTemplateNameI(codeLocation)
    }
  }

  def collapseFunctionTemplateName(
      structName: IFunctionTemplateNameI[sI]):
  IFunctionTemplateNameI[nI] = {
    structName match {
      case FunctionTemplateNameI(humanName, codeLocation) => FunctionTemplateNameI(humanName, codeLocation)
    }
  }

  def collapseInterfaceTemplateName(
      structName: IInterfaceTemplateNameI[sI]):
  IInterfaceTemplateNameI[nI] = {
    structName match {
      case InterfaceTemplateNameI(humanName) => InterfaceTemplateNameI(humanName)
    }
  }

  def collapseImplName(
      map: Map[Int, Int],
      name: IImplNameI[sI]):
  IImplNameI[nI] = {
    name match {
      case ImplNameI(template, templateArgs, subCitizen) => {
        ImplNameI(
          collapseImplTemplateName(map, template),
          templateArgs.map(collapseTemplata(map, _)),
          collapseCitizen(map, subCitizen))
      }
      case AnonymousSubstructImplNameI(AnonymousSubstructImplTemplateNameI(interface), templateArgs, subCitizen) => {
        AnonymousSubstructImplNameI(
          AnonymousSubstructImplTemplateNameI(collapseInterfaceTemplateName(interface)),
          templateArgs.map(collapseTemplata(map, _)),
          collapseCitizen(map, subCitizen))
      }
      case ImplBoundNameI(ImplBoundTemplateNameI(codeLocationS), templateArgs) => {
        ImplBoundNameI(
          ImplBoundTemplateNameI(codeLocationS),
          templateArgs.map(collapseTemplata(map, _)))
      }
    }
  }

  def collapseImplId(
    map: Map[Int, Int],
    structId: IdI[sI, IImplNameI[sI]]):
  IdI[nI, IImplNameI[nI]] = {
    collapseId[IImplNameI[sI], IImplNameI[nI]](
      map,
      structId,
      collapseImplName(map, _))
  }

  def collapseImplTemplateName(
    map: Map[Int, Int],
    structName: IImplTemplateNameI[sI]):
  IImplTemplateNameI[nI] = {
    structName match {
      case ImplTemplateNameI(humanName) => ImplTemplateNameI(humanName)
    }
  }

}
