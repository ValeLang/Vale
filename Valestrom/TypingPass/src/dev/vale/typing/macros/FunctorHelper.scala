package dev.vale.typing.macros

import dev.vale.postparsing.CodeNameS
import dev.vale.{Interner, RangeS, vwat}
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{ConstructTE, PrototypeT}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{FunctionEnvironment, TemplataLookupContext}
import dev.vale.typing.templata.{MutabilityTemplata, PrototypeTemplata, StructTemplata}
import dev.vale.typing.types.{CoordT, ImmutableT, ShareT}
import dev.vale.typing.ast._
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.templata.PrototypeTemplata
import dev.vale.typing.types.CoordT
import dev.vale.{Interner, Profiler, RangeS, vwat}

class FunctorHelper( interner: Interner, structCompiler: StructCompiler) {
  def getFunctorForPrototype(
    env: FunctionEnvironment, coutputs: CompilerOutputs, callRange: RangeS, dropFunction: PrototypeT):
  ConstructTE = {
    val functorTemplate =
      env.lookupNearestWithImpreciseName(
        interner.intern(CodeNameS("Functor1")), Set(TemplataLookupContext)) match {
        case Some(st@StructTemplata(_, _)) => st
        case other => vwat(other)
      }
    val functorStructTT =
      structCompiler.getStructRef(
        coutputs, callRange, functorTemplate,
        Vector(MutabilityTemplata(ImmutableT), PrototypeTemplata(dropFunction)))
    val functorTE =
      ConstructTE(functorStructTT, CoordT(ShareT, functorStructTT), Vector())
    functorTE
  }
}
