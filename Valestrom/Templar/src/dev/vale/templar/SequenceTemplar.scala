package dev.vale.templar

import dev.vale.templar.ast.{ProgramT, ReferenceExpressionTE, TupleTE}
import dev.vale.{Interner, RangeS, vassertSome}
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.env.{IEnvironment, TemplataLookupContext}
import dev.vale.templar.names.CitizenTemplateNameT
import dev.vale.templar.templata.{CoordListTemplata, StructTemplata}
import dev.vale.templar.types.{CoordT, StructTT}
import dev.vale.templar.ast._
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.templar.citizen.StructTemplarCore
import dev.vale.templar.env.PackageEnvironment
import dev.vale.templar.function.FunctionTemplar
import dev.vale.{Interner, Profiler, RangeS, vassert, vassertSome, vimpl}

class SequenceTemplar(
  opts: TemplarOptions,

  interner: Interner,
    structTemplar: StructTemplar,
    templataTemplar: TemplataTemplar) {
  def makeEmptyTuple(
    env: IEnvironment,
    temputs: Temputs):
  (ReferenceExpressionTE) = {
    evaluate(env, temputs, Vector())
  }

  def evaluate(
    env: IEnvironment,
    temputs: Temputs,
    exprs2: Vector[ReferenceExpressionTE]):
  (ReferenceExpressionTE) = {
    val types2 = exprs2.map(_.result.expectReference().reference)
    val finalExpr = TupleTE(exprs2, makeTupleCoord(env, temputs, types2))
    (finalExpr)
  }

  def makeTupleKind(
    env: IEnvironment,
    temputs: Temputs,
    types2: Vector[CoordT]):
  StructTT = {
    val tupleTemplate @ StructTemplata(_, _) =
      vassertSome(
        env.lookupNearestWithName(
          interner.intern(CitizenTemplateNameT(ProgramT.tupleHumanName)), Set(TemplataLookupContext)))
    structTemplar.getStructRef(
      temputs,
      RangeS.internal(-17653),
      tupleTemplate,
      Vector(CoordListTemplata(types2)))
  }

  def makeTupleCoord(
    env: IEnvironment,
    temputs: Temputs,
    types2: Vector[CoordT]):
  CoordT = {
    templataTemplar.coerceKindToCoord(temputs, makeTupleKind(env, temputs, types2))
  }
}
