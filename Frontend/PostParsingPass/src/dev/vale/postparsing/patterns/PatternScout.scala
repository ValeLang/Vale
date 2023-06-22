package dev.vale.postparsing.patterns

import dev.vale.{Interner, RangeS, vimpl}
import dev.vale.parsing.ast._
import dev.vale.postparsing._
import dev.vale.postparsing.rules.{IRulexSR, TemplexScout}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing.rules._
import dev.vale.postparsing._

import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class PatternScout(
    interner: Interner,
    templexScout: TemplexScout) {
  def getParameterCaptures(pattern: AtomSP): Vector[VariableDeclaration] = {
    val AtomSP(_, maybeCapture, _, maybeDestructure) = pattern
  Vector.empty ++
      maybeCapture.toVector.flatMap(getCaptureCaptures) ++
        maybeDestructure.toVector.flatten.flatMap(getParameterCaptures)
  }
  private def getCaptureCaptures(capture: CaptureS): Vector[VariableDeclaration] = {
    if (capture.mutate) {
      Vector()
    } else {
      Vector(VariableDeclaration(capture.name))
    }
  }

  // Returns:
  // - Region rune, or None if it's an ignore pattern
  // - The translated patterns
  private[postparsing] def translatePattern(
    stackFrame: StackFrame,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)],
    patternPP: PatternPP):
  AtomSP = {
    val PatternPP(range,maybeCaptureP, maybeTypeP, maybeDestructureP) = patternPP

    val maybeCoordRuneS =
      maybeTypeP match {
        case Some(typeP) => {
          val runeS =
            templexScout.translateTypeIntoRune(
              stackFrame.parentEnv,
              lidb.child(),
              ruleBuilder,
              stackFrame.contextRegion,
              typeP)
          runeToExplicitType += ((runeS.rune, CoordTemplataType()))
          Some(runeS)
        }
        case None => {
          // This happens in patterns in lets, and in lambdas' parameters that have no types.
          None
        }
      }

    val maybePatternsS =
      maybeDestructureP match {
        case None => None
        case Some(DestructureP(_, destructureP)) => {
          Some(
            destructureP.map(
              translatePattern(
                stackFrame, lidb.child(), ruleBuilder, runeToExplicitType, _)))
        }
      }

    val captureS =
      maybeCaptureP match {
        case None => {
//          val codeLocation = Scout.evalPos(stackFrame.file, patternPP.range.begin)
          None
        }
        case Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), _)) => {
          None
        }
        case Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, name)), maybeMutate)) => {
          // if (name.str == "set" || name.str == "mut") {
          //   throw CompileErrorExceptionS(CantUseThatLocalName(PostParser.evalRange(stackFrame.file, range), name.str))
          // }
          Some(CaptureS(interner.intern(CodeVarNameS(name)), maybeMutate.nonEmpty))
        }
        case Some(DestinationLocalP(ConstructingMemberNameDeclarationP(NameP(_, name)), maybeMutate)) => {
          Some(CaptureS(interner.intern(ConstructingMemberNameS(name)), maybeMutate.nonEmpty))
        }
        case Some(DestinationLocalP(IterableNameDeclarationP(range), maybeMutate)) => {
          Some(CaptureS(interner.intern(IterableNameS(PostParser.evalRange(stackFrame.file, range))), maybeMutate.nonEmpty))
        }
        case Some(DestinationLocalP(IteratorNameDeclarationP(range), maybeMutate)) => {
          Some(CaptureS(interner.intern(IteratorNameS(PostParser.evalRange(stackFrame.file, range))), maybeMutate.nonEmpty))
        }
        case Some(DestinationLocalP(IterationOptionNameDeclarationP(range), maybeMutate)) => {
          Some(CaptureS(interner.intern(IterationOptionNameS(PostParser.evalRange(stackFrame.file, range))), maybeMutate.nonEmpty))
        }
      }

    val patternS =
      AtomSP(PostParser.evalRange(stackFrame.file, range), captureS, maybeCoordRuneS, maybePatternsS)
    patternS
  }

}
