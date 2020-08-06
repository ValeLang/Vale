package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{FileP, ParseFailure, ParseSuccess, Parser, Range}
import net.verdagon.vale.scout.{CodeLocationS, ProgramS, RangeS, Scout}
import net.verdagon.vale.{vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ErrorTests extends FunSuite with Matchers  {

  class Compilation(code: String) {
    var parsedCache: Option[FileP] = None
    var scoutputCache: Option[ProgramS] = None

    def getParsed(): FileP = {
      parsedCache match {
        case Some(parsed) => parsed
        case None => {
          Parser.runParserForProgramAndCommentRanges(code) match {
            case ParseFailure(err) => fail(err.toString)
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
          val scoutput = Scout.scoutProgram(List(getParsed()))
          scoutputCache = Some(scoutput)
          scoutput
        }
      }
    }

    def getAstrouts(): Either[ProgramA, ICompileErrorA] = {
      Astronomer.runAstronomer(getScoutput())
    }
  }

  def compileProgramForError(code: String): ICompileErrorA = {
    new Compilation(code).getAstrouts() match {
      case Left(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
      case Right(err) => err
    }
  }

  test("Report type not found") {
    val code =
      """fn main() {
        |  a Bork = 5;
        |}
        |""".stripMargin
    compileProgramForError(code) match {
      case e @ CouldntFindType(RangeS(CodeLocationS(0, 16), CodeLocationS(0, 20)), "Bork") => {
        val errorText = AstronomerErrorHumanizer.humanize(code, e)
        errorText shouldEqual
          """2:6: Couldn't find type `Bork`:
            |  a Bork = 5;
            |""".stripMargin
      }
    }
  }
}
