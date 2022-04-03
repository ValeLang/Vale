package dev.vale.typing

import dev.vale.postparsing.patterns.AtomSP
import dev.vale.{Err, Ok, Profiler, RangeS, Result, typing}
import dev.vale.postparsing.{CoordTemplataType, IRuneS, ITemplataType}
import dev.vale.postparsing.rules.{CoordSendSR, IRulexSR, RuneUsage}
import dev.vale.solver.{CompleteSolve, FailedSolve, IIncompleteOrFailedSolve, ISolverOutcome, IncompleteSolve}
import dev.vale.highertyping._
import dev.vale.postparsing.rules.CoordSendSR
import dev.vale.postparsing.{ArgumentRuneS, CoordTemplataType, IRuneS, ITemplataType}
import dev.vale.solver.RuleError
import OverloadResolver.FindFunctionFailure
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.infer.{IInfererDelegate, ITypingPassSolverError, CompilerSolver}
import dev.vale.typing.templata.ITemplata
import dev.vale.typing.citizen.AncestorHelper
import dev.vale.typing.env.TemplataLookupContext
import dev.vale.typing.infer._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Err, Ok, Profiler, RangeS, Result, vassert, vassertSome, vfail, vimpl, vwat}

import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class InitialSend(
  senderRune: RuneUsage,
  receiverRune: RuneUsage,
  sendTemplata: ITemplata)

case class InitialKnown(
  rune: RuneUsage,
  templata: ITemplata)

class InferCompiler(
    opts: TypingPassOptions,

    delegate: IInfererDelegate[IEnvironment, CompilerOutputs]) {
  def solveComplete(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]):
  Result[Map[IRuneS, ITemplata], IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITypingPassSolverError]] = {
    solve(env, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends) match {
      case f @ FailedSolve(_, _, _) => Err(f)
      case i @ IncompleteSolve(_, _, _) => Err(i)
      case CompleteSolve(conclusions) => Ok(conclusions)
    }
  }

  def solveExpectComplete(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]):
  Map[IRuneS, ITemplata] = {
    solve(env, coutputs, rules, runeToType, invocationRange, initialKnowns, initialSends) match {
      case f @ FailedSolve(_, _, err) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(invocationRange, f))
      }
      case i @ IncompleteSolve(_, _, _) => {
        throw CompileErrorExceptionT(typing.TypingPassSolverError(invocationRange, i))
      }
      case CompleteSolve(conclusions) => conclusions
    }
  }

  def solve(
    env: IEnvironment,
    state: CompilerOutputs,
    initialRules: Vector[IRulexSR],
    initialRuneToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]
  ): ISolverOutcome[IRulexSR, IRuneS, ITemplata, ITypingPassSolverError] = {
    Profiler.frame(() => {

      val runeToType =
        initialRuneToType ++
        initialSends.map({ case InitialSend(senderRune, _, _) =>
          senderRune.rune -> CoordTemplataType
        })
      val rules =
        initialRules ++
        initialSends.map({ case InitialSend(senderRune, receiverRune, _) =>
          CoordSendSR(receiverRune.range, senderRune, receiverRune)
        })
      val alreadyKnown =
        initialKnowns.map({ case InitialKnown(rune, templata) => rune.rune -> templata }).toMap ++
        initialSends.map({ case InitialSend(senderRune, _, senderTemplata) =>
          (senderRune.rune -> senderTemplata)
        })

      new CompilerSolver[IEnvironment, CompilerOutputs](opts.globalOptions, delegate).solve(
          invocationRange,
          env,
          state,
          rules,
          runeToType,
          alreadyKnown)
    })
  }
}
