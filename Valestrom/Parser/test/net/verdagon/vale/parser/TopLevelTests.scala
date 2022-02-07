package net.verdagon.vale.parser

import net.verdagon.vale.parser.ast.{BlockPE, ExportAsP, FunctionP, ImportP, NameOrRunePT, NameP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImportP, TopLevelStructP, VoidPE}
import net.verdagon.vale.parser.old.OldTestParseUtils
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}



class TopLevelTests extends FunSuite with Matchers with Collector with OldTestParseUtils {
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

//  test("Function containing if") {
//    val program = compileProgram(
//      """
//        |fn main() int {
//        |  if true { 3 } else { 4 }
//        |}
//        |""".stripMargin)
//    val main = program.lookupFunction("main")
//    main.body.get
//  }




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
      Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("exporting int") {
    val program = compileProgram("export int as NumberThing;")
    program.topLevelThings(0) match {
      case TopLevelExportAsP(ExportAsP(_,NameOrRunePT(NameP(_,"int")),NameP(_,"NumberThing"))) =>

    }
  }

  test("exporting array") {
    val program = compileProgram("export []<mut>int as IntArray;")
    program.topLevelThings(0) match {
      case TopLevelExportAsP(ExportAsP(_,_,NameP(_,"IntArray"))) =>
    }
  }

  test("import wildcard") {
    val program = compileProgram("import somemodule.*;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(), NameP(_, "*"))) =>
    }
  }

  test("import just module and thing") {
    val program = compileProgram("import somemodule.List;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(), NameP(_, "List"))) =>
    }
  }

  test("full import") {
    val program = compileProgram("import somemodule.subpackage.List;")
    program.topLevelThings(0) match {
      case TopLevelImportP(ImportP(_, NameP(_, "somemodule"), Vector(NameP(_, "subpackage")), NameP(_, "List"))) =>
    }
  }

  test("Return with region generics") {
    val program = compileProgram(
      """
        |fn strongestDesire() IDesire<'r, 'i> { }
        |""".stripMargin)
    program.topLevelThings(0) match {
      case TopLevelFunctionP(func) =>
    }
  }


  test("Bad start of statement") {
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  )
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
    compileProgramForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  ]
        |}
        """.stripMargin) match {
      case BadStartOfStatementError(_) =>
    }
  }
}
