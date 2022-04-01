package dev.vale.parsing

import dev.vale.Collector
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{BlockPE, ExportAsP, FileP, FunctionP, ImportP, NameOrRunePT, NameP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImportP, TopLevelStructP, VoidPE}
import dev.vale.parsing.ast.BlockPE
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with TestParseUtils {
  def compile(code: String): FileP = {
    new Parser(GlobalOptions(true, true, true, true))
      .runParserForProgramAndCommentRanges(code)
      .getOrDie()
      ._1
  }

  def compileForError(code: String): IParseError = {
    new Parser(GlobalOptions(true, true, true, true))
      .runParserForProgramAndCommentRanges(code)
      .expectErr()
  }

  test("Function then struct") {
    val program = compile(
      """
        |exported func main() int {}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunctionP(_) => }
    program.topLevelThings(1) match { case TopLevelStructP(_) => }
  }

  test("Ellipses ignored") {
    compile("""exported func main(...) int {}""".stripMargin)
    compile("""exported func main() ... {}""".stripMargin)
    compile("""exported func main() int {} ... """.stripMargin)
    compile("""exported func main() int {...}""".stripMargin)
    compile("""exported func main() int {moo(...)}""".stripMargin)
    compile("""exported func main() int {x = ...;}""".stripMargin)
    compile("""exported func main() int {set x = ...;}""".stripMargin)
    compile("""struct Moo {} ... """.stripMargin)
    compile("""struct Moo {...}""".stripMargin)
  }

//  test("Function containing if") {
//    val program = compile(
//      """
//        |func main() int {
//        |  if true { 3 } else { 4 }
//        |}
//        |""".stripMargin)
//    val main = program.lookupFunction("main")
//    main.body.get
//  }




  test("Reports unrecognized at top level") {
    val code =
      """func main(){}
        |blort
        |""".stripMargin
    val err = compileForError(code)
    err match {
      case UnrecognizedTopLevelThingError(_) =>
    }
  }

  // lol
  test("Funky function") {
    compile("funky main() { }")
  }

  // To support the examples on the site for the syntax highlighter
  test("empty") {
    val program = compile("func foo() { ... }")
    program.topLevelThings(0) match {
      case TopLevelFunctionP(
      FunctionP(_,
      _,
      Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("exporting int") {
    val program = compile("export int as NumberThing;")
    program.topLevelThings(0) match {
      case TopLevelExportAsP(ExportAsP(_,NameOrRunePT(NameP(_,"int")),NameP(_,"NumberThing"))) =>

    }
  }

  test("exporting imm array 1") {
    val program = compile("export []<mut>int as IntArray;")
    program.topLevelThings(0) match {
      case TopLevelExportAsP(ExportAsP(_,_,NameP(_,"IntArray"))) =>
    }
  }

  test("exporting imm array 2") {
    val program = compile("export #[]int as IntArray;")
    program.topLevelThings(0) match {
      case TopLevelExportAsP(ExportAsP(_,_,NameP(_,"IntArray"))) =>
    }
  }

  test("import wildcard") {
    val program = compile("import somemodule.*;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(), NameP(_, "*"))) =>
    }
  }

  test("import just module and thing") {
    val program = compile("import somemodule.List;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(), NameP(_, "List"))) =>
    }
  }

  test("full import") {
    val program = compile("import somemodule.subpackage.List;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(NameP(_, "subpackage")), NameP(_, "List"))) =>
    }
  }

  test("Return with region generics") {
    val program = compile(
      """
        |func strongestDesire() IDesire<'r, 'i> { }
        |""".stripMargin)
    program.topLevelThings(0) match {
      case TopLevelFunctionP(func) =>
    }
  }


  test("Bad start of statement") {
    compileForError(
      """
        |func doCivicDance(virtual this Car) {
        |  )
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
    compileForError(
      """
        |func doCivicDance(virtual this Car) {
        |  ]
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
  }
}
