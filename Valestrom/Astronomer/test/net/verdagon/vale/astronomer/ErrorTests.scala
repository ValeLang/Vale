package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{FileP, ParseFailure, ParseSuccess, Parser, Range}
import net.verdagon.vale.scout.{CodeLocationS, ProgramS, RangeS, Scout}
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, PackageCoordinateMap, Ok, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ErrorTests extends FunSuite with Matchers  {

  class Compilation(code: String) {
    var parsedCache: Option[FileP] = None
    var scoutputCache: Option[ProgramS] = None

    def getFileMap() = {
      FileCoordinateMap.test(code)
    }

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
          val scoutput = {
            Scout.scoutProgram(FileCoordinate.test, getParsed()) match {
              case Err(e) => vfail(e.toString)
              case Ok(t) => t
            }
          }
          scoutputCache = Some(scoutput)
          scoutput
        }
      }
    }

    def getAstrouts(): Either[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
      Astronomer.runAstronomer(FileCoordinateMap.test(getScoutput()))
    }
  }

  def compileProgramForError(compilation: Compilation): ICompileErrorA = {
    compilation.getAstrouts() match {
      case Left(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
      case Right(err) => err
    }
  }

  test("Report type not found") {
    val compilation =
      new Compilation(
      """fn main() export {
        |  a Bork = 5;
        |}
        |""".stripMargin)


    compileProgramForError(compilation) match {
      case e @ CouldntFindTypeA(_, "Bork") => {
        val errorText = AstronomerErrorHumanizer.humanize(compilation.getFileMap(), e)
        vassert(errorText.contains("Couldn't find type `Bork`"))
      }
    }
  }

  test("Report couldnt solve rules") {
    val compilation =
      new Compilation(
        """
          |fn moo<A>(x int) {
          |  42
          |}
          |fn main() export {
          |  moo();
          |}
          |""".stripMargin)

    compileProgramForError(compilation) match {
      case e @ CouldntSolveRulesA(range, failure) => {
        val errorText = AstronomerErrorHumanizer.humanize(compilation.getFileMap(), e)
        vassert(errorText.contains("olve"))
      }
    }
  }
}
