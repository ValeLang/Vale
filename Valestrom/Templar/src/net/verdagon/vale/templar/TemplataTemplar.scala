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
    descendantCitizenRef: CitizenRefT,
    ancestorInterfaceRef: InterfaceRefT):
  Option[Int]

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  StructRefT

  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceTemplata,
    uncoercedTemplateArgs: List[ITemplata]):
  InterfaceRefT

  def getStaticSizedArrayKind(
    env: IEnvironment,
    temputs: Temputs,
    mutability: MutabilityT,
    variability: VariabilityT,
    size: Int,
    type2: CoordT):
  StaticSizedArrayTT

  def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT

  def getTupleKind(env: IEnvironment, state: Temputs, elements: List[CoordT]): TupleTT
}

class TemplataTemplar(
    opts: TemplarOptions,
    delegate: ITemplataTemplarDelegate) {

  def getTypeDistance(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
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

  def pointifyKind(
    temputs: Temputs,
    kind: KindT,
    ownershipIfMutable: OwnershipT):
  CoordT = {
    makeInner().pointifyKind(temputs, kind, ownershipIfMutable)
  }

  def isTypeConvertible(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Boolean) = {
    makeInner().isTypeConvertible(temputs, sourcePointerType, targetPointerType)
  }

  def isTypeTriviallyConvertible(
    temputs: Temputs,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  (Boolean) = {
    makeInner().isTypeTriviallyConvertible(temputs, sourcePointerType, targetPointerType)
  }

  def makeInner(): TemplataTemplarInner[IEnvironment, Temputs] = {
    new TemplataTemplarInner[IEnvironment, Temputs](
      new ITemplataTemplarInnerDelegate[IEnvironment, Temputs] {
        override def lookupTemplataImprecise(env: IEnvironment, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithName(name, Set(TemplataLookupContext)))
        }

        override def lookupTemplata(env: IEnvironment, range: RangeS, name: INameT): ITemplata = {
          // Changed this from AnythingLookupContext to TemplataLookupContext
          // because this is called from StructTemplar to figure out its members.
          // We could instead pipe a lookup context through, if this proves problematic.
          vassertSome(env.getNearestTemplataWithAbsoluteName2(name, Set(TemplataLookupContext)))
        }

        override def getMutability(temputs: Temputs, kind: KindT): MutabilityT = {
          Templar.getMutability(temputs, kind)
        }

        override def evaluateInterfaceTemplata(state: Temputs, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]):
        (KindT) = {
          delegate.getInterfaceRef(state, callRange, templata, templateArgs)
        }

        override def evaluateStructTemplata(state: Temputs, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]):
        (KindT) = {
          delegate.getStructRef(state, callRange, templata, templateArgs)
        }

        override def getAncestorInterfaceDistance(
          temputs: Temputs,
          descendantCitizenRef: CitizenRefT,
          ancestorInterfaceRef: InterfaceRefT):
        (Option[Int]) = {
          delegate.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
        }

        override def getStaticSizedArrayKind(env: IEnvironment, state: Temputs, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
          delegate.getStaticSizedArrayKind(env, state, mutability, variability, size, element)
        }

        override def getRuntimeSizedArrayKind(env: IEnvironment, state: Temputs, element: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
          delegate.getRuntimeSizedArrayKind(env, state, element, arrayMutability, arrayVariability)
        }

        override def getTupleKind(env: IEnvironment, state: Temputs, elements: List[CoordT]): TupleTT = {
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
