package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{FileP, ParseFailure, ParseSuccess, Parser, Range}
import net.verdagon.vale.scout.{CodeNameS, ProgramS, Scout}
import net.verdagon.vale.{Err, FileCoordinate, FileCoordinateMap, Ok, PackageCoordinateMap, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ErrorTests extends FunSuite with Matchers  {
  def compileProgramForError(compilation: AstronomerCompilation): ICompileErrorA = {
    compilation.getAstrouts() match {
      case Ok(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
      case Err(err) => err
    }
  }

  test("Report type not found") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn main(a Bork) export {
          |}
          |""".stripMargin)


    compileProgramForError(compilation) match {
      case e @ CouldntFindTypeA(_, CodeNameS("Bork")) => {
        val errorText = AstronomerErrorHumanizer.humanize(compilation.getCodeMap().getOrDie(), e)
        vassert(errorText.contains("Couldn't find type `Bork`"))
      }
    }
  }

  test("Report couldnt solve rules") {
    val compilation =
      AstronomerTestCompilation.test(
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
        val errorText = AstronomerErrorHumanizer.humanize(compilation.getCodeMap().getOrDie(), e)
        vassert(errorText.contains("olve"))
      }
    }
  }
}
