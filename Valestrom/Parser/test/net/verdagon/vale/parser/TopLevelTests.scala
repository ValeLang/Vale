package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Function then struct") {
    val program = compileProgram(
      """
        |fn main(){}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunction(_) => }
    program.topLevelThings(1) match { case TopLevelStruct(_) => }
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
    new ParseErrorHumanizer(code).humanize(err) shouldEqual
      """2:2: expected fn, struct, interface, or impl, but found:
        |blort
        |""".stripMargin
  }
}
