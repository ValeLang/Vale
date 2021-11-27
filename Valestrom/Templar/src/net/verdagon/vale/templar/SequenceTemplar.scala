package net.verdagon.vale.templar

import net.verdagon.vale.templar.ast.{ExpressionT, ProgramT, ReferenceExpressionTE, TupleTE}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.citizen.{StructTemplar, StructTemplarCore}
import net.verdagon.vale.templar.env.{FunctionEnvironment, FunctionEnvironmentBox, IEnvironment, PackageEnvironment, TemplataLookupContext}
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar}
import net.verdagon.vale.templar.names.{CitizenTemplateNameT}
import net.verdagon.vale.{IProfiler, RangeS, vassert, vassertSome, vimpl}

class SequenceTemplar(
  opts: TemplarOptions,
  profiler: IProfiler,
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
          profiler, CitizenTemplateNameT(ProgramT.tupleHumanName), Set(TemplataLookupContext)))
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
