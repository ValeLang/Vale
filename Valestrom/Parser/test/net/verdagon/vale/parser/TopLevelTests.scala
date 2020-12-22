package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Function then struct") {
    val program = compileProgram(
      """
        |fn main() int export {}
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

  // To support the examples on the site for the syntax highlighter
  test("empty") {
    val program = compileProgram("fn foo() { ... }")
    program.topLevelThings(0) match {
      case TopLevelFunctionP(
      FunctionP(_,
      _,
      Some(BlockPE(_,List(VoidPE(_)))))) =>
    }
  }
}
