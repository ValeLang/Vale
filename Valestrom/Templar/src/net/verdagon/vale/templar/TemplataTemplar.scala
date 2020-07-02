package net.verdagon.vale.templar.templata

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{ImplTemplar, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, IEnvironmentBox, TemplataLookupContext}
import net.verdagon.vale.{vassertSome, vfail}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._

import scala.collection.immutable.{List, Map, Set}

object TemplataTemplar {

  def getTypeDistance(
    temputs: TemputsBox,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Option[TypeDistance]) = {
    makeInner().getTypeDistance(temputs,sourcePointerType, targetPointerType)
  }

  def evaluateTemplex(
    env: IEnvironment,
    temputs: TemputsBox,
    templex: ITemplexA
  ): (ITemplata) = {
    makeInner().evaluateTemplex(env, temputs, templex)
  }

  def evaluateTemplexes(env: IEnvironment, temputs: TemputsBox, templexes: List[ITemplexA]):
  (List[ITemplata]) = {
    makeInner().evaluateTemplexes(env, temputs, templexes)
  }

  def pointifyReferend(
    temputs: TemputsBox,
    referend: Kind,
    ownershipIfMutable: Ownership):
  Coord = {
    makeInner().pointifyReferend(temputs, referend, ownershipIfMutable)
  }

  def isTypeConvertible(
    temputs: TemputsBox,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    makeInner().isTypeConvertible(temputs, sourcePointerType, targetPointerType)
  }

  def isTypeTriviallyConvertible(
    temputs: TemputsBox,
    sourcePointerType: Coord,
    targetPointerType: Coord):
  (Boolean) = {
    makeInner().isTypeTriviallyConvertible(temputs, sourcePointerType, targetPointerType)
  }

//
//  def lookupTemplata(
//    env: IEnvironmentBox,
//    temputs: TemputsBox,
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
//    temputs: TemputsBox,
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

  def makeInner(): TemplataTemplarInner[IEnvironment, TemputsBox] = {
    new TemplataTemplarInner[IEnvironment, TemputsBox](
      new ITemplataTemplarInnerDelegate[IEnvironment, TemputsBox] {
        override def lookupTemplataImprecise(env: IEnvironment, name: IImpreciseNameStepA): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithName(name, Set(TemplataLookupContext)))
        }

        override def lookupTemplata(env: IEnvironment, name: IName2): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)))
        }

        override def getMutability(temputs: TemputsBox, kind: Kind): Mutability = {
          Templar.getMutability(temputs, kind)
        }

        override def getPackKind(env: IEnvironment, temputs: TemputsBox, types2: List[Coord]):
        (PackT2, Mutability) = {
          PackTemplar.makePackType(env.globalEnv, temputs, types2)
        }

        override def evaluateInterfaceTemplata(state: TemputsBox, templata: InterfaceTemplata, templateArgs: List[ITemplata]):
        (Kind) = {
          StructTemplar.getInterfaceRef(state, templata, templateArgs)
        }

        override def evaluateStructTemplata(state: TemputsBox, templata: StructTemplata, templateArgs: List[ITemplata]):
        (Kind) = {
          StructTemplar.getStructRef(state, templata, templateArgs)
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
          temputs: TemputsBox,
          descendantCitizenRef: CitizenRef2,
          ancestorInterfaceRef: InterfaceRef2):
        (Option[Int]) = {
          ImplTemplar.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getArraySequenceKind(env: IEnvironment, state: TemputsBox, mutability: Mutability, size: Int, element: Coord): (KnownSizeArrayT2) = {
          ArrayTemplar.makeArraySequenceType(env, state, mutability, size, element)
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
