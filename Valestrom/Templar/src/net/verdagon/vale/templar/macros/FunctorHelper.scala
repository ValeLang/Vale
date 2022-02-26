package net.verdagon.vale.templar.macros

import net.verdagon.vale.scout.CodeNameS
import net.verdagon.vale.templar.Temputs
import net.verdagon.vale.templar.ast.{ConstructTE, PrototypeT}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import net.verdagon.vale.templar.templata.{MutabilityTemplata, PrototypeTemplata, StructTemplata}
import net.verdagon.vale.templar.types.{CoordT, ImmutableT, ReadonlyT, ShareT}
import net.verdagon.vale.{Profiler, Interner, RangeS, vwat}

class FunctorHelper( interner: Interner, structTemplar: StructTemplar) {
  def getFunctorForPrototype(
    env: FunctionEnvironment, temputs: Temputs, callRange: RangeS, dropFunction: PrototypeT):
  ConstructTE = {
    val functorTemplate =
      env.lookupNearestWithImpreciseName(
        interner.intern(CodeNameS("Functor1")), Set(TemplataLookupContext)) match {
        case Some(st@StructTemplata(_, _)) => st
        case other => vwat(other)
      }
    val functorStructTT =
      structTemplar.getStructRef(
        temputs, callRange, functorTemplate,
        Vector(MutabilityTemplata(ImmutableT), PrototypeTemplata(dropFunction)))
    val functorTE =
      ConstructTE(functorStructTT, CoordT(ShareT, ReadonlyT, functorStructTT), Vector())
    functorTE
  }
}
