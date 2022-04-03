package dev.vale.postparsing

import dev.vale.{Err, FileCoordinateMap, Ok, RangeS, vassertSome, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing._
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AbstractSP
import dev.vale.Err
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class PostParsingRuleTests extends FunSuite with Matchers {
  val tz = RangeS.testZero

  private def compile(code: String): ProgramS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(FileCoordinateMap.test(code), e))
      case Ok(t) => t.expectOne()
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => e
      case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
    }
  }

  test("Predict simple templex") {
    val program =
      compile(
        """
          |func main(a int) {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(main.params.head.pattern.coordRune.get.rune)) shouldEqual
      CoordTemplataType
  }

  test("Can know rune type from simple equals") {
    val program =
      compile(
        """
          |func main<T, Y>(a T)
          |where Y = T {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("T"))) shouldEqual
      CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("Y"))) shouldEqual
      CoordTemplataType
  }

  test("Predict knows type from Or rule") {
    val program =
      compile(
        """
          |func main<M>(a int)
          |where M = any(own, borrow) {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("M"))) shouldEqual
      OwnershipTemplataType
  }

  test("Predict CoordComponent types") {
    val program =
      compile(
        """
          |func main<T>(a T)
          |where T = Ref[O, K], O Ownership, K Kind {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("T"))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("O"))) shouldEqual OwnershipTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("K"))) shouldEqual KindTemplataType
  }

  test("Predict Call types") {
    val program =
      compile(
        """
          |func main<A, B, T>(p1 A, p2 B)
          |where A = T<B>, T = Option, A = int {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("A"))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("B"))) shouldEqual CoordTemplataType
    // We can't know if T it's a Coord->Coord or a Coord->Kind type.
    main.runeToPredictedType.get(CodeRuneS("T")) shouldEqual None
  }

  test("Predict array sequence types") {
    val program =
      compile(
        """
          |func main<M, V, N, E>(t T)
          |where T Ref = [#N]<M, V>E {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("M"))) shouldEqual MutabilityTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("V"))) shouldEqual VariabilityTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("N"))) shouldEqual IntegerTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("E"))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("T"))) shouldEqual CoordTemplataType
  }

  test("Predict for isInterface") {
    val program =
      compile(
        """
          |func main<A, B>()
          |where A = isInterface(B) {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("A"))) shouldEqual KindTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("B"))) shouldEqual KindTemplataType
  }
}
