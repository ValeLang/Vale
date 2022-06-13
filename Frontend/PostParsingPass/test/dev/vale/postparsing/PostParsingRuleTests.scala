package dev.vale.postparsing

import dev.vale.{Err, FileCoordinateMap, Interner, Ok, RangeS, StrI, vassertSome, vfail}
import dev.vale.options.GlobalOptions
import dev.vale.parsing._
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AbstractSP
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class PostParsingRuleTests extends FunSuite with Matchers {
  private def compile(code: String, interner: Interner = new Interner()): ProgramS = {
    PostParserTestCompilation.test(code).getScoutput() match {
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(FileCoordinateMap.test(interner, code), e))
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
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<T, Y>(a T)
          |where Y = T {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("T"))))) shouldEqual
      CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("Y"))))) shouldEqual
      CoordTemplataType
  }

  test("Predict knows type from Or rule") {
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<M>(a int)
          |where M = any(own, borrow) {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("M"))))) shouldEqual
      OwnershipTemplataType
  }

  test("Predict CoordComponent types") {
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<T>(a T)
          |where T = Ref[O, K], O Ownership, K Kind {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("T"))))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("O"))))) shouldEqual OwnershipTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("K"))))) shouldEqual KindTemplataType
  }

  test("Predict Call types") {
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<A, B, T>(p1 A, p2 B)
          |where A = T<B>, T = Option, A = int {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("A"))))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("B"))))) shouldEqual CoordTemplataType
    // We can't know if T it's a Coord->Coord or a Coord->Kind type.
    main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("T")))) shouldEqual None
  }

  test("Predict array sequence types") {
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<M, V, N, E>(t T)
          |where T Ref = [#N]<M, V>E {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("M"))))) shouldEqual MutabilityTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("V"))))) shouldEqual VariabilityTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("N"))))) shouldEqual IntegerTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("E"))))) shouldEqual CoordTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("T"))))) shouldEqual CoordTemplataType
  }

  test("Predict for isInterface") {
    val interner = new Interner()
    val program =
      compile(
        """
          |func main<A, B>()
          |where A = isInterface(B) {}
          |""".stripMargin, interner)
    val main = program.lookupFunction("main")

    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("A"))))) shouldEqual KindTemplataType
    vassertSome(main.runeToPredictedType.get(CodeRuneS(interner.intern(StrI("B"))))) shouldEqual KindTemplataType
  }
}
