package dev.vale.instantiating

import dev.vale.instantiating.RegionCounter._
import dev.vale.instantiating.ast._
import dev.vale.{U, vassert, vimpl, vwat}

import scala.collection.mutable

object RegionCounter {
  class Counter {
    // TODO(optimize): Use an array for this, with a minimum index and maximum index (similar to
    // what a circular queue uses)
    val set: mutable.HashSet[Int] = mutable.HashSet[Int]()

    def count(region: RegionTemplataI[sI]): Unit = {
      set.add(region.pureHeight)
    }

    def assembleMap(): Map[Int, Int] = {
      val numRegions = set.size
      // Let's say we have a set that contains 3, 5, -2, 0, 4, it becomes...
      set.toVector
        .sorted // -2, 0, 3, 4, 5
        .zipWithIndex // (-2, 0), (0, 1), (3, 2), (4, 3), (5, 4)
        .map({ case (subjectiveRegion, i) =>
          // If we have 4 regions, then they should go from -3 to 0
          subjectiveRegion -> (i - numRegions + 1)
        }) // (-2, -4), (0, -3), (3, -2), (4, -1), (5, 0)
        .toMap
    }

//    def assembleMap(counter: Counter): Vector[Int] = {
//      var numRegions = 0
//      U.foreach(counts.toVector, (hasRegion: Boolean) => {
//        if (hasRegion) {
//          numRegions = numRegions + 1
//        }
//      })
//      // If we have 4 regions, then they should go from -3 to 0
//      var nextRegion = -numRegions + 1
//      U.mapWithIndex(counts.toVector, (i, hasRegion: Boolean) => {
//        if (hasRegion) {
//          val region = nextRegion
//          nextRegion = nextRegion + 1
//          region
//        } else {
//          Int.MaxValue
//        }
//      })
//    }

    //    private def count(counter: Counter, region: RegionTemplataI[sI]): Unit = {
//      while (region.pureHeight >= counts.size) {
//        counts += false
//      }
//      counts(region.pureHeight) = true
//    }

  }

  def countPrototype(counter: Counter, prototype: PrototypeI[sI]): Unit = {
    val PrototypeI(id, returnType) = prototype
    countFunctionId(counter, id)
    countCoord(counter, returnType)
  }

  def countId[T <: INameI[sI]](
    counter: Counter,
    id: IdI[sI, T],
    func: T => Unit):
  Unit = {
    val IdI(packageCoord, initSteps, localName) = id
    initSteps.foreach(x => countName(counter, x))
    func(localName)
  }

  def countFunctionId(
    counter: Counter,
    id: IdI[sI, IFunctionNameI[sI]]):
  Unit = {
    countId[IFunctionNameI[sI]](
      counter: Counter,
      id,
      x => countFunctionName(counter, x))
  }

  def countFunctionName(
    counter: Counter,
    name: IFunctionNameI[sI]):
  Unit = {
    name match {
      case FunctionNameIX(FunctionTemplateNameI(humanName, codeLocation), templateArgs, parameters) => {
        templateArgs.foreach(countTemplata(counter, _))
        parameters.foreach(countCoord(counter, _))
      }
      case ExternFunctionNameI(humanName, parameters) => {
        parameters.foreach(countCoord(counter, _))
      }
      case LambdaCallFunctionNameI(LambdaCallFunctionTemplateNameI(codeLoc, paramsTT), templateArgs, parameters) => {
        templateArgs.foreach(countTemplata(counter, _))
        parameters.foreach(countCoord(counter, _))
      }
      case AnonymousSubstructConstructorNameI(AnonymousSubstructConstructorTemplateNameI(substruct), templateArgs, parameters) => {
        countName(counter, substruct)
        templateArgs.foreach(countTemplata(counter, _))
        parameters.foreach(countCoord(counter, _))
      }
      case ForwarderFunctionNameI(ForwarderFunctionTemplateNameI(funcTemplateName, index), funcName) => {
        countName(counter, funcTemplateName)
        countFunctionName(counter, funcName)
      }
    }
  }

  def countCitizenName(
      counter: Counter,
      name: ICitizenNameI[sI]):
  Unit = {
    name match {
      case StructNameI(StructTemplateNameI(humanName), templateArgs) => {
        templateArgs.foreach(countTemplata(counter, _))
      }
      case LambdaCitizenNameI(template) => {
      }
      case InterfaceNameI(InterfaceTemplateNameI(humanName), templateArgs) => {
        templateArgs.foreach(countTemplata(counter, _))
      }
      case AnonymousSubstructNameI(AnonymousSubstructTemplateNameI(interface), templateArgs) => {
        countName(counter, interface)
        templateArgs.foreach(countTemplata(counter, _))
      }
    }
  }

  def countVarName(
    counter: Counter,
    name: IVarNameI[sI]):
  Unit = {
    name match {
      case CodeVarNameI(name) =>
      case TypingPassBlockResultVarNameI(life) =>
      case TypingPassTemporaryVarNameI(life) =>
      case TypingPassFunctionResultVarNameI() =>
    }
  }

  def countName(
    counter: Counter,
    name: INameI[sI]):
  Unit = {
    name match {
      case z : IFunctionNameI[_] => {
        // Scala can't seem to match generics.
        val x = z.asInstanceOf[IFunctionNameI[sI]]
        countFunctionName(counter, x)
      }
      case ExportNameI(template, region) => {
        counter.count(region)
      }
      case ExternNameI(template, region) => {
        counter.count(region)
      }
      case c : ICitizenNameI[_] => {
        // Scala can't seem to match generics.
        val x = c.asInstanceOf[ICitizenNameI[sI]]
        countCitizenName(counter, x)
      }
      case StructNameI(template, templateArgs) => {
        templateArgs.foreach(arg => countTemplata(counter, arg))
      }
      case StructTemplateNameI(_) =>
      case LambdaCitizenTemplateNameI(_) =>
      case InterfaceTemplateNameI(humanNamee) =>
      case AnonymousSubstructTemplateNameI(interface) => {
        countName(counter, interface)
      }
      case FunctionTemplateNameI(humanName, codeLocation) =>
      case other => vimpl(other)
    }
  }

  def countTemplata(
    counter: Counter,
    templata: ITemplataI[sI]):
  Unit = {
    templata match {
      case CoordTemplataI(region, coord) => {
        countTemplata(counter, region)
        countCoord(counter, coord)
      }
      case KindTemplataI(kind) => countKind(counter, kind)
      case r @ RegionTemplataI(_) => counter.count(r)
      case MutabilityTemplataI(mutability) =>
      case IntegerTemplataI(_) =>
      case VariabilityTemplataI(variability) =>
      case other => vimpl(other)
    }
  }

  def countCoord(
    counter: Counter,
    coord: CoordI[sI]):
  Unit = {
    val CoordI(ownership, kind) = coord
    countKind(counter, kind)
  }

  def countKind(kind: KindIT[sI]): Map[Int, Int] = {
    val map = new RegionCounter.Counter()
    RegionCounter.countKind(map, kind)
    map.assembleMap()
  }

  def countKind(
    counter: Counter,
    kind: KindIT[sI]):
  Unit = {
    kind match {
      case NeverIT(_) =>
      case VoidIT() =>
      case IntIT(_) =>
      case BoolIT() =>
      case FloatIT() =>
      case StrIT() =>
      case StructIT(id) => countStructId(counter, id)
      case InterfaceIT(id) => countInterfaceId(counter, id)
      case StaticSizedArrayIT(ssaId) => {
        countId[StaticSizedArrayNameI[sI]](
          counter,
          ssaId,
          { case StaticSizedArrayNameI(template, size, variability, RawArrayNameI(mutability, elementType, selfRegion)) =>
            countTemplata(elementType)
            counter.count(selfRegion)
          })
      }
      case RuntimeSizedArrayIT(ssaId) => {
        countId[RuntimeSizedArrayNameI[sI]](
          counter,
          ssaId,
          { case RuntimeSizedArrayNameI(template, RawArrayNameI(mutability, elementType, selfRegion)) =>
            countTemplata(elementType)
            counter.count(selfRegion)
          })
      }
    }
  }

  def countRuntimeSizedArray(
    counter: Counter,
    rsa: RuntimeSizedArrayIT[sI]):
  Unit = {
    val RuntimeSizedArrayIT(rsaId) = rsa
    countId[RuntimeSizedArrayNameI[sI]](
      counter,
      rsaId,
      { case RuntimeSizedArrayNameI(template, RawArrayNameI(mutability, elementType, selfRegion)) =>
        countTemplata(counter, elementType)
        counter.count(selfRegion)
      })
  }

  def countStaticSizedArray(
    counter: Counter,
    ssa: StaticSizedArrayIT[sI]):
  Unit = {
    val StaticSizedArrayIT(ssaId) = ssa
    countId[StaticSizedArrayNameI[sI]](
      counter,
      ssaId,
      { case StaticSizedArrayNameI(template, size, variability, RawArrayNameI(mutability, elementType, selfRegion)) =>
        countTemplata(elementType)
        counter.count(selfRegion)
      })
  }

  def countCitizenId(
      counter: Counter,
      citizenId: IdI[sI, ICitizenNameI[sI]]):
  Unit = {
    citizenId match {
      case IdI(packageCoord, initSteps, localName: IStructNameI[_]) => {
        countStructId(IdI(packageCoord, initSteps, localName.asInstanceOf[IStructNameI[sI]]))
      }
      case IdI(packageCoord, initSteps, localName: IInterfaceNameI[_]) => {
        countInterfaceId(IdI(packageCoord, initSteps, localName.asInstanceOf[IInterfaceNameI[sI]]))
      }
    }
  }

  def countStructId(
    counter: Counter,
    structId: IdI[sI, IStructNameI[sI]]):
  Unit = {
    countId[IStructNameI[sI]](
      counter,
      structId,
      countStructName(counter, _))
  }

  def countStructTemplateName(
      counter: Counter,
      structName: IStructTemplateNameI[sI]):
  Unit = {
    structName match {
      case StructTemplateNameI(humanName) => StructTemplateNameI(humanName)
    }
  }

  def countStructName(
      counter: Counter,
      structName: IStructNameI[sI]):
  Unit = {
    structName match {
      case StructNameI(template, templateArgs) => {
        countStructTemplateName(counter, template)
        templateArgs.foreach(countTemplata(counter, _))
      }
      case LambdaCitizenNameI(template) => {
      }
      case AnonymousSubstructNameI(template, templateArgs) => {
        templateArgs.foreach(countTemplata(counter, _))
      }
    }
  }

  def countImplId(
    counter: Counter,
    structId: IdI[sI, IImplNameI[sI]]):
  Unit = {
    countId[IImplNameI[sI]](
      counter,
      structId,
      x => countImplName(counter, x))
  }

  def countImplName(
      counter: Counter,
      implId: IImplNameI[sI]):
  Unit = {
    implId match {
      case ImplNameI(template, templateArgs, subCitizen) => {
        countImplTemplateName(counter, template)
        templateArgs.foreach(countTemplata(counter, _))
        countCitizenId(subCitizen.id)
      }
      case AnonymousSubstructImplNameI(AnonymousSubstructImplTemplateNameI(interface), templateArgs, subCitizen) => {
        countName(counter, interface)
        templateArgs.foreach(countTemplata(counter, _))
        countCitizenId(subCitizen.id)
      }
      case ImplBoundNameI(ImplBoundTemplateNameI(codeLocationS), templateArgs) => {
        templateArgs.foreach(countTemplata(counter, _))
      }
    }
  }

  def countImplTemplateName(
    counter: Counter,
    structName: IImplTemplateNameI[sI]):
  Unit = {
    structName match {
      case ImplTemplateNameI(humanName) => ImplTemplateNameI(humanName)
    }
  }

  def countExportId(idI: IdI[sI, ExportNameI[sI]]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countId(counter, idI, (x: ExportNameI[sI]) => RegionCounter.countName(counter, x))
    counter.assembleMap()
  }

  def countExternId(idI: IdI[sI, ExternNameI[sI]]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countId(counter, idI, (x: ExternNameI[sI]) => RegionCounter.countName(counter, x))
    counter.assembleMap()
  }

  def countStructId(idI: IdI[sI, IStructNameI[sI]]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countId(counter, idI, (x: IStructNameI[sI]) => RegionCounter.countName(counter, x))
    counter.assembleMap()
  }

  def countInterfaceId(idI: IdI[sI, IInterfaceNameI[sI]]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    countInterfaceId(counter, idI)
    counter.assembleMap()
  }

  def countInterfaceId(
      counter: Counter,
      interfaceId: IdI[sI, IInterfaceNameI[sI]]):
  Unit = {
    RegionCounter.countId(counter, interfaceId, (x: IInterfaceNameI[sI]) => RegionCounter.countName(counter, x))
  }

  def countFunctionId(idI: IdI[sI, IFunctionNameI[sI]]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countId(counter, idI, (x: IFunctionNameI[sI]) => RegionCounter.countName(counter, x))
    counter.assembleMap()
  }

  def countImplId(
    implId: IdI[sI, IImplNameI[sI]]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countId(
      counter, implId, (x: IImplNameI[sI]) => RegionCounter.countImplName(counter, x))
    counter.assembleMap()
  }

  def countCoord(coord: CoordI[sI]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countCoord(counter, coord)
    counter.assembleMap()
  }

  def countVarName(
    name: IVarNameI[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countVarName(counter, name)
    counter.assembleMap()
  }

  def countStaticSizedArray(
    ssa: StaticSizedArrayIT[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countStaticSizedArray(counter, ssa)
    counter.assembleMap()
  }

  def countRuntimeSizedArray(
    rsa: RuntimeSizedArrayIT[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countRuntimeSizedArray(counter, rsa)
    counter.assembleMap()
  }

  def countPrototype(prototype: PrototypeI[sI]): Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countPrototype(counter, prototype)
    counter.assembleMap()
  }

  def countFunctionName(
    name: IFunctionNameI[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countFunctionName(counter, name)
    counter.assembleMap()
  }

  def countImplName(
      name: IImplNameI[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countImplName(counter, name)
    counter.assembleMap()
  }

  def countCitizenName(
      name: ICitizenNameI[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countCitizenName(counter, name)
    counter.assembleMap()
  }

  def countCitizenId(
      name: IdI[sI, ICitizenNameI[sI]]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countCitizenId(counter, name)
    counter.assembleMap()
  }

  def countTemplata(
      name: ITemplataI[sI]):
  Map[Int, Int] = {
    val counter = new RegionCounter.Counter()
    RegionCounter.countTemplata(counter, name)
    counter.assembleMap()
  }
}
