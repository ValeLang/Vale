package dev.vale.templar.macros

import dev.vale.scout.CodeNameS
import dev.vale.{Interner, RangeS, vwat}
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{ConstructTE, PrototypeT}
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.templar.templata.{MutabilityTemplata, PrototypeTemplata, StructTemplata}
import dev.vale.templar.types.{CoordT, ImmutableT, ShareT}
import dev.vale.templar.ast._
import dev.vale.templar.env.TemplataLookupContext
import dev.vale.templar.templata.PrototypeTemplata
import dev.vale.templar.types.CoordT
import dev.vale.{Interner, Profiler, RangeS, vwat}

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
      ConstructTE(functorStructTT, CoordT(ShareT, functorStructTT), Vector())
    functorTE
  }
}
