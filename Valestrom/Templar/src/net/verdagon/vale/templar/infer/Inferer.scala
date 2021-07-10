package net.verdagon.vale.templar.infer

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.{INameT, IRuneT}
import net.verdagon.vale.templar.infer.infer.IInferSolveResult
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{IProfiler, vimpl}

import scala.collection.immutable.List

trait IInfererDelegate[Env, State] {
  def evaluateType(
    env: Env,
    state: State,
    type1: ITemplexA):
  (ITemplata)

  def lookupMemberTypes(
    state: State,
    kind: KindT,
    // This is here so that the predictor can just give us however many things
    // we expect.
    expectedNumMembers: Int
  ): Option[List[CoordT]]

  def getMutability(state: State, kind: KindT): MutabilityT

  def lookupTemplata(env: Env, range: RangeS, name: INameT): ITemplata
  def lookupTemplataImprecise(env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata

  def evaluateStructTemplata(
    state: State,
    callRange: RangeS,
    templata: StructTemplata,
    templateArgs: List[ITemplata]):
  (KindT)

  def evaluateInterfaceTemplata(
    state: State,
    callRange: RangeS,
    templata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (KindT)

//  def getPackKind(env: Env, state: State, members: List[Coord]): (PackT2, Mutability)

  def getStaticSizedArrayKind(env: Env, state: State, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT)

  def getRuntimeSizedArrayKind(env: Env, state: State, type2: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT

  def getTupleKind(env: Env, state: State, elements: List[CoordT]): TupleTT

  def getAncestorInterfaceDistance(temputs: State, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): (Option[Int])

  def getAncestorInterfaces(temputs: State, descendantCitizenRef: CitizenRefT):
  (Set[InterfaceTT])

  def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType
  def getStructTemplataType(st: StructTemplata): ITemplataType

  def getMemberCoords(state: State, structTT: StructTT): List[CoordT]

  def structIsClosure(state: State, structTT: StructTT): Boolean

  def resolveExactSignature(env: Env, state: State, range: RangeS, name: String, coords: List[CoordT]): PrototypeT
}

// This is the public API for the outside world to use the Infer code.
object Inferer {
  def solve[Env, State](
    profiler: IProfiler,
    delegate: IInfererDelegate[Env, State],
    env: Env,
    state: State,
    rules: List[IRulexTR],
    typeByRune: Map[IRuneT, ITemplataType],
    localRunes: Set[IRuneT],
    invocationRange: RangeS,
    directInputs: Map[IRuneT, ITemplata],
    paramAtoms: List[AtomAP],
    maybeParamInputs: Option[List[ParamFilter]],
    checkAllRunesPresent: Boolean):
  (IInferSolveResult) = {
    val templataTemplar =
      new TemplataTemplarInner[Env, State](makeTemplataTemplarDelegate(delegate))
    val equalsLayer = new InfererEquator[Env, State](templataTemplar)
    val templar =
      new InfererEvaluator[Env, State](
        profiler,
        templataTemplar,
        equalsLayer,
        makeEvaluatorDelegate(delegate))
    templar.solve(
      env,
      state,
      rules,
      invocationRange,
      typeByRune,
      localRunes,
      directInputs,
      paramAtoms,
      maybeParamInputs,
      checkAllRunesPresent)
  }

  def makeTemplataTemplarDelegate[Env, State](
    delegate: IInfererDelegate[Env, State]):
  (ITemplataTemplarInnerDelegate[Env, State]) = {
    new ITemplataTemplarInnerDelegate[Env, State] {
      override def getAncestorInterfaceDistance(temputs: State, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): (Option[Int]) = {
        delegate.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
      }
      override def getMutability(state: State, kind: KindT): MutabilityT = {
        delegate.getMutability(state, kind)
      }

//      override def getPackKind(env: Env, state: State, members: List[Coord]): (PackT2, Mutability) = {
//        delegate.getPackKind(env, state, members)
//      }

      override def lookupTemplata(env: Env, range: RangeS, name: INameT): ITemplata = {
        delegate.lookupTemplata(env, range, name)
      }

      override def lookupTemplataImprecise(env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
        delegate.lookupTemplataImprecise(env, range, name)
      }

      override def evaluateInterfaceTemplata(state: State, callRange: RangeS, templata: InterfaceTemplata, templateArgs: List[ITemplata]): (KindT) = {
        delegate.evaluateInterfaceTemplata(state, callRange, templata, templateArgs)
      }

      override def evaluateStructTemplata(state: State, callRange: RangeS, templata: StructTemplata, templateArgs: List[ITemplata]): (KindT) = {
        delegate.evaluateStructTemplata(state, callRange, templata, templateArgs)
      }

      override def getStaticSizedArrayKind(env: Env, state: State, mutability: MutabilityT, variability: VariabilityT, size: Int, element: CoordT): (StaticSizedArrayTT) = {
        delegate.getStaticSizedArrayKind(env, state, mutability, variability, size, element)
      }

      override def getRuntimeSizedArrayKind(env: Env, state: State, type2: CoordT, arrayMutability: MutabilityT, arrayVariability: VariabilityT): RuntimeSizedArrayTT = {
        delegate.getRuntimeSizedArrayKind(env, state, type2, arrayMutability, arrayVariability)
      }

      override def getTupleKind(env: Env, state: State, elements: List[CoordT]): TupleTT = {
        delegate.getTupleKind(env, state, elements)
      }

      override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
        delegate.getInterfaceTemplataType(it)
      }

      override def getStructTemplataType(st: StructTemplata): ITemplataType = {
        delegate.getStructTemplataType(st)
      }
    }
  }

  def coerce(templata: ITemplata, tyype: ITemplataType): ITemplata = {
    (templata, tyype) match {
      case _ => vimpl()
    }
  }

  private def makeEvaluatorDelegate[Env, State](delegate: IInfererDelegate[Env, State]):
  IInfererEvaluatorDelegate[Env, State] = {
    new IInfererEvaluatorDelegate[Env, State] {
      override def getAncestorInterfaces(temputs: State, descendantCitizenRef: CitizenRefT): (Set[InterfaceTT]) = {
        delegate.getAncestorInterfaces(temputs, descendantCitizenRef)
      }

      override def lookupTemplata(env: Env, range: RangeS, name: INameT): ITemplata = {
        delegate.lookupTemplata(env, range, name)
      }
      override def lookupTemplata(profiler: IProfiler, env: Env, range: RangeS, name: IImpreciseNameStepA): ITemplata = {
        delegate.lookupTemplataImprecise(env, range, name)
      }

      override def lookupMemberTypes(state: State, kind: KindT, expectedNumMembers: Int):
      Option[List[CoordT]] = {
        delegate.lookupMemberTypes(state, kind, expectedNumMembers)
      }

      override def getMutability(state: State, kind: KindT): MutabilityT = {
        delegate.getMutability(state: State, kind: KindT)
      }

      override def getAncestorInterfaceDistance(temputs: State, descendantCitizenRef: CitizenRefT, ancestorInterfaceRef: InterfaceTT): (Option[Int]) = {
        delegate.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
      }

      override def getMemberCoords(state: State, structTT: StructTT): List[CoordT] = {
        delegate.getMemberCoords(state, structTT)
      }

      override def structIsClosure(state: State, structTT: StructTT): Boolean = {
        delegate.structIsClosure(state, structTT)
      }

      override def resolveExactSignature(env: Env, state: State, range: RangeS, name: String, coords: List[CoordT]): PrototypeT = {
        delegate.resolveExactSignature(env, state, range, name, coords)
      }
    }
  }
}
