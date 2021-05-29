package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox, TemplataLookupContext}
import net.verdagon.vale.{vassertSome, vfail}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._

import scala.collection.immutable.{List, Map, Set}

trait ITemplataTemplarDelegate {

  def getAncestorInterfaceDistance(
    temputs: Temputs,
    descendantCitizenRef: CitizenRef2,
    ancestorInterfaceRef: InterfaceRef2):
  Option[Int]

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  StructRef2

  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  InterfaceRef2

  def makeArraySequenceType(
    env: IEnvironment,
    temputs: Temputs,
    mutability: Mutability,
    variability: Variability,
    size: Int,
    type2: Coord):
  KnownSizeArrayT2

  def makeUnknownSizeArrayType(env: IEnvironment, state: Temputs, element: Coord, arrayMutability: Mutability, arrayVariability: Variability): UnknownSizeArrayT2

  def getTupleKind(env: IEnvironment, state: Temputs, elements: List[Coord]): TupleT2
}

class TemplataTemplar(
    opts: TemplarOptions,
    delegate: ITemplataTemplarDelegate) {

  def getTypeDistance(
    temputs: Temputs,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Option[TypeDistance]) = {
    makeInner().getTypeDistance(temputs,sourcePointerType, targetPointerType)
  }

  def evaluateTemplex(
    env: IEnvironment,
    temputs: Temputs,
    templex: ITemplexA
  ): (ITemplata) = {
    makeInner().evaluateTemplex(env, temputs, templex)
  }

  def evaluateTemplexes(env: IEnvironment, temputs: Temputs, templexes: List[ITemplexA]):
  (List[ITemplata]) = {
    makeInner().evaluateTemplexes(env, temputs, templexes)
  }

  def pointifyReferend(
    temputs: Temputs,
    referend: Kind,
    ownershipIfMutable: Ownership):
  Coord = {
    makeInner().pointifyReferend(temputs, referend, ownershipIfMutable)
  }

  def isTypeConvertible(
    temputs: Temputs,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    makeInner().isTypeConvertible(temputs, sourcePointerType, targetPointerType)
  }

  def isTypeTriviallyConvertible(
    temputs: Temputs,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    makeInner().isTypeTriviallyConvertible(temputs, sourcePointerType, targetPointerType)
  }

//
//  def lookupTemplata(
//    env: IEnvironmentBox,
//    temputs: Temputs,
//    name: String
//  ): (Option[ITemplata]) = {
//    env match {
//      case IEnvironment(globalEnv, _, _, _, templatas) => {
//        templatas.get(name) match {
//          case Some(templata) => (Some(templata))
//          case None => {
//            lookupTemplataFromGlobalEnv(globalEnv, temputs, name)
//          }
//        }
//      }
//      case globalEnv @ IEnvironment(_, _, _, _, _, _, _, _, _) => {
//        lookupTemplataFromGlobalEnv(globalEnv, temputs, name)
//      }
//    }
//  }
//
//  private def lookupTemplataFromGlobalEnv(
//    env: IEnvironmentBox,
//    temputs: Temputs,
//    name: String
//  ): (Option[ITemplata]) = {
//    val IEnvironment(
//      ordinaryStructBanners,
//      ordinaryInterfaceBanners,
//      templatedStructBanners,
//      templatedInterfaceBanners,
//      _,
//      _,
//      _,
//      _,
//      templatas) = env
//
//    templatas.get(name) match {
//      case Some(templata) => return (Some(templata))
//      case None =>
//    }
//    ordinaryStructBanners.get(name) match {
//      case Some(osb) => {
//        val structRef =
//          StructTemplar.getStructRef(env.globalEnv, temputs, osb)
//        return (Some(ReferendTemplata(structRef)))
//      }
//      case None =>
//    }
//    ordinaryInterfaceBanners.get(name) match {
//      case Some(oib) => {
//        val structRef =
//          StructTemplar.getInterfaceRef(env.globalEnv, temputs, oib)
//        return (Some(ReferendTemplata(structRef)))
//      }
//      case None =>
//    }
//    templatedStructBanners.get(name) match {
//      case Some(osb) => {
//        return (Some(StructTerryTemplata(StructTerry(None, name, List()))))
//      }
//      case None =>
//    }
//    templatedInterfaceBanners.get(name) match {
//      case Some(osb) => {
//        return (Some(InterfaceTerryTemplata(InterfaceTerry(None, name, List()))))
//      }
//      case None =>
//    }
//    return (None)
//  }

  def makeInner(): TemplataTemplarInner[IEnvironment, Temputs] = {
    new TemplataTemplarInner[IEnvironment, Temputs](
      new ITemplataTemplarInnerDelegate[IEnvironment, Temputs] {
        override def lookupTemplataImprecise(env: IEnvironment, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithName(name, Set(TemplataLookupContext)))
        }

        override def lookupTemplata(env: IEnvironment, range: RangeS, name: IName2): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)))
        }

        override def getMutability(temputs: Temputs, kind: Kind): Mutability = {
          Templar.getMutability(temputs, kind)
        }

//        override def getPackKind(env: IEnvironment, temputs: Temputs, types2: List[Coord]):
//        (PackT2, Mutability) = {
//          PackTemplar.makePackType(env.globalEnv, temputs, types2)
//        }

        override def evaluateInterfaceTemplata(state: Temputs, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]):
        (Kind) = {
          delegate.getInterfaceRef(state, callRange, templata, templateArgs)
        }

        override def evaluateStructTemplata(state: Temputs, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]):
        (Kind) = {
          delegate.getStructRef(state, callRange, templata, templateArgs)
        }

        //val elementCoord =
        //  templateArgTemplatas match {
        //    case List(ReferenceTemplata(ref)) => ref
        //    // todo: coerce referend into reference here... or somehow reuse all the machinery we have for
        //    // regular templates?
        //  }
        //val elementMutability = Templar.getMutability(state, elementCoord.referend)
        //if (arrayMutability == Immutable && elementMutability == Mutable) {
        //  throw new Exception("Can't have an immutable array of mutables")
        //}
        //val arrayType = UnknownSizeArrayT2(RawArrayT2(elementCoord, arrayMutability))
        //(state, ReferendTemplata(arrayType))

        override def getAncestorInterfaceDistance(
          temputs: Temputs,
          descendantCitizenRef: CitizenRef2,
          ancestorInterfaceRef: InterfaceRef2):
        (Option[Int]) = {
          delegate.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getArraySequenceKind(env: IEnvironment, state: Temputs, mutability: Mutability, variability: Variability, size: Int, element: Coord): (KnownSizeArrayT2) = {
          delegate.makeArraySequenceType(env, state, mutability, variability, size, element)
        }

        override def makeUnknownSizeArrayType(env: IEnvironment, state: Temputs, element: Coord, arrayMutability: Mutability, arrayVariability: Variability): UnknownSizeArrayT2 = {
          delegate.makeUnknownSizeArrayType(env, state, element, arrayMutability, arrayVariability)
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[Coord]): TupleT2 = {
          delegate.getTupleKind(env, state, elements)
        }

        override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
          it.originInterface.tyype
        }

        override def getStructTemplataType(st: StructTemplata): ITemplataType = {
          st.originStruct.tyype
        }
      })
  }

}
