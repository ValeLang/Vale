package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Function then struct") {
    val program = compileProgram(
      """
        |fn main() int {}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunctionP(_) => }
    program.topLevelThings(1) match { case TopLevelStructP(_) => }
  }


  test("Reports unrecognized at top level") {
    val code =
      """fn main(){}
        |blort
        |""".stripMargin
    val err = compileProgramForError(code)
    err match {
      case UnrecognizedTopLevelThingError(12) =>
    }
  }
}
