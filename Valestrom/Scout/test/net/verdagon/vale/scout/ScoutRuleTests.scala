package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP}
import net.verdagon.vale.{Err, FileCoordinate, Ok, RangeS, vassert, vassertSome, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class ScoutRuleTests extends FunSuite with Matchers {
  val tz = RangeS.testZero

  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case Err(error) => vwat(error.toString)
            case Ok(program0) => program0
          }
        new Scout(GlobalOptions.test()).scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => vfail(e.toString)
          case Ok(t) => t
        }
      }
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case Err(error) => vwat(error.toString)
            case Ok(program0) => program0
          }
        new Scout(GlobalOptions.test()).scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => e
          case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
        }
      }
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
          |where M = own | borrow {}
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
          |where T Ref(O, P, K), O Ownership, P Permission, K Kind {}
          |""".stripMargin)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS("T"))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("O"))) shouldEqual OwnershipTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS("P"))) shouldEqual PermissionTemplataType
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
