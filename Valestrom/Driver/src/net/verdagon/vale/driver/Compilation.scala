package net.verdagon.vale.driver

import net.verdagon.vale.astronomer.{Astronomer, ProgramA}
import net.verdagon.vale.carpenter.Carpenter
import net.verdagon.vale.hammer.{Hammer, VonHammer}
import net.verdagon.vale.hinputs.Hinputs
import net.verdagon.vale.metal.ProgramH
import net.verdagon.vale.parser.{CombinatorParsers, ParseFailure, ParseSuccess, Parser, Program0}
import net.verdagon.vale.scout.{ProgramS, Scout}
import net.verdagon.vale.templar.{CompleteProgram2, Templar, Temputs}
import net.verdagon.vale.{vassert, vwat}
import net.verdagon.vale.vivem.{Heap, PrimitiveReferendV, ReferenceV, Vivem}
import net.verdagon.von.IVonData

class Compilation(code: String, useCommonEnv: Boolean = true) {
  var parsedCache: Option[Program0] = None
  var scoutputCache: Option[ProgramS] = None
  var astroutsCache: Option[ProgramA] = None
  var temputsCache: Option[Temputs] = None
  var hinputsCache: Option[Hinputs] = None
  var hamutsCache: Option[ProgramH] = None

  def getParsed(): Program0 = {
    parsedCache match {
      case Some(parsed) => parsed
      case None => {
        Parser.runParserForProgramAndCommentRanges(code) match {
          case ParseFailure(err) => vwat(err.toString)
          case ParseSuccess((program0, _)) => {
            parsedCache = Some(program0)
            program0
          }
        }
      }
    }
  }

  def getScoutput(): ProgramS = {
    scoutputCache match {
      case Some(scoutput) => scoutput
      case None => {
        val scoutput = Scout.scoutProgram(getParsed())
        scoutputCache = Some(scoutput)
        scoutput
      }
    }
  }

  def getAstrouts(): ProgramA = {
    astroutsCache match {
      case Some(astrouts) => astrouts
      case None => {
        val astrouts = Astronomer.runAstronomer(getScoutput())
        astroutsCache = Some(astrouts)
        astrouts
      }
    }
  }

  def getTemputs(): Temputs = {
    temputsCache match {
      case Some(temputs) => temputs
      case None => {
        val temputs = Templar.evaluate(getAstrouts())
        temputsCache = Some(temputs)
        temputs
      }
    }
  }

  def getHinputs(): Hinputs = {
    hinputsCache match {
      case Some(hinputs) => hinputs
      case None => {
        val hinputs = Carpenter.translate(getTemputs())
        hinputsCache = Some(hinputs)
        hinputs
      }
    }
  }

  def getHamuts(): ProgramH = {
    hamutsCache match {
      case Some(hamuts) => hamuts
      case None => {
        val hamuts = Hammer.translate(getHinputs())
        VonHammer.vonifyProgram(hamuts)
        hamutsCache = Some(hamuts)
        hamuts
      }
    }
  }

  def evalForReferend(heap: Heap, args: Vector[ReferenceV]): IVonData = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(heap: Heap, args: Vector[ReferenceV]): Unit = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(args: Vector[PrimitiveReferendV]): Unit = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForReferend(args: Vector[PrimitiveReferendV]): IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForReferend(
      args: Vector[PrimitiveReferendV],
      stdin: List[String]):
  IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.stdinFromList(stdin), Vivem.regularStdout)
  }
  def evalForStdout(args: Vector[PrimitiveReferendV]): String = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    stdoutStringBuilder.mkString
  }
  def evalForReferendAndStdout(args: Vector[PrimitiveReferendV]): (IVonData, String) = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    val referend = Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    (referend, stdoutStringBuilder.mkString)
  }
}