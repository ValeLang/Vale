package dev.vale.parsing

import dev.vale.{Collector, Interner, StrI, vassertOne}
import dev.vale.parsing.ast.{BlockPE, ExportAsP, FileP, FunctionP, ImportP, NameOrRunePT, NameP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImportP, TopLevelStructP, VoidPE}
import dev.vale.parsing.ast.BlockPE
import dev.vale.lexing.{BadStartOfStatementError, IParseError, Lexer, UnrecognizedDenizenError}
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with TestParseUtils {
  def compile(code: String): FileP = {
    compileFile(code).getOrDie()
  }

  def compileForError(code: String): IParseError = {
    compileFile(code).expectErr().error
  }

  test("Function then struct") {
    val program = compile(
      """
        |exported func main() int {}
        |
        |struct mork { }
        |""".stripMargin)
    program.denizens(0) match { case TopLevelFunctionP(_) => }
    program.denizens(1) match { case TopLevelStructP(_) => }
  }

  test("Ellipses ignored") {
    // Unicode … symbol is treated as an expression by the parser
    compile("""exported func main() int {x = …;}""".stripMargin)
    compile("""exported func main() int {set x = …;}""".stripMargin)

    // Three dots is treated as a comment
    compile("""exported func main(...) int {}""".stripMargin)
    compile("""exported func main() ... {}""".stripMargin)
    compile("""exported func main() int {} ... """.stripMargin)
    compile("""exported func main() int {...}""".stripMargin)
    compile("""exported func main() int {moo(...)}""".stripMargin)
    compile("""struct Moo {} ... """.stripMargin)
    compile("""struct Moo {...}""".stripMargin)
  }

  test("Comments ignored") {
    compile(
      """
        |exported func main(
        |        // moo
        |) int {}
        |""".stripMargin)
    compile(
      """
        |exported func main()
        |        // moo
        |{}
        |""".stripMargin)
    compile(
      """
        |exported func main() int {}
        |        // moo
        |""".stripMargin)
    compile(
      """
        |exported func main() int {
        |        // moo
        |}
        |""".stripMargin)
    compile(
      """
        |exported func main() int {
        |  moo(
        |        // moo
        |  )
        |}
        |""".stripMargin)
    compile(
      """
        |struct Moo {}
        |        // moo
        |""".stripMargin)
    compile(
      """
        |struct Moo {
        |        // moo
        |}
        |""".stripMargin)
    compile(
      """
        |struct Moo {
        |}
        |// moo""".stripMargin)
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
      case UnrecognizedDenizenError(_) =>
    }
  }

  // lol
  test("Funky function") {
    compile("funky main() { }")
  }

  // To support the examples on the site for the syntax highlighter
  test("empty") {
    val program = compile("func foo() { ... }")
    program.denizens(0) match {
      case TopLevelFunctionP(
      FunctionP(_,
      _,
      Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("exporting int") {
    val program = compile("export int as NumberThing;")
    program.denizens(0) match {
      case TopLevelExportAsP(ExportAsP(_,NameOrRunePT(NameP(_, StrI("int"))),NameP(_, StrI("NumberThing")))) =>

    }
  }

  test("exporting imm array 1") {
    val program = compile("export []<mut>int as IntArray;")
    program.denizens(0) match {
      case TopLevelExportAsP(ExportAsP(_,_,NameP(_, StrI("IntArray")))) =>
    }
  }

  test("exporting imm array 2") {
    val program = compile("export #[]int as IntArray;")
    program.denizens(0) match {
      case TopLevelExportAsP(ExportAsP(_,_,NameP(_, StrI("IntArray")))) =>
    }
  }

  test("import wildcard") {
    val program = compile("import somemodule.*;")
    program.denizens(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, StrI("somemodule")), Vector(), NameP(_, StrI("*")))) =>
    }
  }

  test("import just module and thing") {
    val program = compile("import somemodule.List;")
    program.denizens(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, StrI("somemodule")), Vector(), NameP(_, StrI("List")))) =>
    }
  }

  test("full import") {
    val program = compile("import somemodule.subpackage.List;")
    program.denizens(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, StrI("somemodule")), Vector(NameP(_, StrI("subpackage"))), NameP(_, StrI("List")))) =>
    }
  }

  test("Return with region generics") {
    val program = compile(
      """
        |func strongestDesire() IDesire<'r, 'i> { }
        |""".stripMargin)
    program.denizens(0) match {
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
