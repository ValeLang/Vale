package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.{CoordSendSR, IRulexSR, RuneUsage}
import net.verdagon.vale.scout.{CoordTemplataType, IRuneS, ITemplataType, ArgumentRuneS}
import net.verdagon.vale.solver.{CompleteSolve, FailedSolve, IIncompleteOrFailedSolve, ISolverOutcome, IncompleteSolve, RuleError, SolverConflict}
import net.verdagon.vale.templar.OverloadTemplar.FindFunctionFailure
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, ILookupContext, TemplataLookupContext}
import net.verdagon.vale.templar.infer.{IInfererDelegate, _}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{Err, IProfiler, Ok, RangeS, Result, vassert, vassertSome, vfail, vimpl, vwat}

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

class InferTemplar(
    opts: TemplarOptions,
    profiler: IProfiler,
    delegate: IInfererDelegate[IEnvironment, Temputs]) {
  def solveComplete(
    env: IEnvironment,
    temputs: Temputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]):
  Result[Map[IRuneS, ITemplata], IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITemplarSolverError]] = {
    solve(env, temputs, rules, runeToType, invocationRange, initialKnowns, initialSends) match {
      case f @ FailedSolve(_, _, _) => Err(f)
      case i @ IncompleteSolve(_, _, _) => Err(i)
      case CompleteSolve(conclusions) => Ok(conclusions)
    }
  }

  def solveExpectComplete(
    env: IEnvironment,
    temputs: Temputs,
    rules: Vector[IRulexSR],
    runeToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]):
  Map[IRuneS, ITemplata] = {
    solve(env, temputs, rules, runeToType, invocationRange, initialKnowns, initialSends) match {
      case f @ FailedSolve(_, _, err) => {
        throw CompileErrorExceptionT(TemplarSolverError(invocationRange, f))
      }
      case i @ IncompleteSolve(_, _, _) => {
        throw CompileErrorExceptionT(TemplarSolverError(invocationRange, i))
      }
      case CompleteSolve(conclusions) => conclusions
    }
  }

  def solve(
    env: IEnvironment,
    state: Temputs,
    initialRules: Vector[IRulexSR],
    initialRuneToType: Map[IRuneS, ITemplataType],
    invocationRange: RangeS,
    initialKnowns: Vector[InitialKnown],
    initialSends: Vector[InitialSend]
  ): ISolverOutcome[IRulexSR, IRuneS, ITemplata, ITemplarSolverError] = {
    profiler.newProfile("infer", "", () => {

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

      new TemplarSolver[IEnvironment, Temputs](opts.globalOptions, delegate).solve(
          invocationRange,
          env,
          state,
          rules,
          runeToType,
          alreadyKnown)
    })
  }
}
