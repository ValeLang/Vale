package dev.vale.typing

import dev.vale.typing.ast.{ProgramT, ReferenceExpressionTE, TupleTE}
import dev.vale.{Interner, RangeS, vassertSome}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{IEnvironment, TemplataLookupContext}
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.templata.{CoordListTemplata, StructTemplata}
import dev.vale.typing.types.{CoordT, StructTT}
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.citizen.StructCompilerCore
import dev.vale.typing.env.PackageEnvironment
import dev.vale.typing.function.FunctionCompiler
import dev.vale.{Interner, Profiler, RangeS, vassert, vassertSome, vimpl}

class SequenceCompiler(
  opts: TypingPassOptions,

  interner: Interner,
    structCompiler: StructCompiler,
    templataCompiler: TemplataCompiler) {
  def makeEmptyTuple(
    env: IEnvironment,
    coutputs: CompilerOutputs):
  (ReferenceExpressionTE) = {
    evaluate(env, coutputs, Vector())
  }

  def evaluate(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    exprs2: Vector[ReferenceExpressionTE]):
  (ReferenceExpressionTE) = {
    val types2 = exprs2.map(_.result.expectReference().reference)
    val finalExpr = TupleTE(exprs2, makeTupleCoord(env, coutputs, types2))
    (finalExpr)
  }

  def makeTupleKind(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    types2: Vector[CoordT]):
  StructTT = {
    val tupleTemplate @ StructTemplata(_, _) =
      vassertSome(
        env.lookupNearestWithName(
          interner.intern(CitizenTemplateNameT(ProgramT.tupleHumanName)), Set(TemplataLookupContext)))
    structCompiler.getStructRef(
      coutputs,
      RangeS.internal(interner, -17653),
      tupleTemplate,
      Vector(CoordListTemplata(types2)))
  }

  def makeTupleCoord(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    types2: Vector[CoordT]):
  CoordT = {
    templataCompiler.coerceKindToCoord(coutputs, makeTupleKind(env, coutputs, types2))
  }
}
