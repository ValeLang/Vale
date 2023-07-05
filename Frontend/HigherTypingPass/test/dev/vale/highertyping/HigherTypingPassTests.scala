package dev.vale.highertyping

import dev.vale.{Err, Ok, PackageCoordinate, vassertSome, vfail}
import dev.vale.postparsing._
import dev.vale.parsing.Parser
import dev.vale.postparsing.RuneTypeSolveError
import dev.vale._
import org.scalatest.{FunSuite, Matchers}

class HigherTypingPassTests extends FunSuite with Matchers  {
  def compileProgramForError(compilation: HigherTypingCompilation): ICompileErrorA = {
    compilation.getAstrouts() match {
      case Ok(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
      case Err(err) => err
    }
  }

  test("Type simple main function") {
    val compilation =
      HigherTypingTestCompilation.test(
      """exported func main() {
        |}
        |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic function") {
    val compilation =
      HigherTypingTestCompilation.test(
        """exported func moo<T>() where T Ref {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Infer coord type from parameters") {
    val compilation =
      HigherTypingTestCompilation.test(
        """exported func moo<T>(x T) {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS(compilation.keywords.T)) shouldEqual CoordTemplataType()
  }

  test("Type simple struct") {
    val compilation =
      HigherTypingTestCompilation.test(
        """struct Moo {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic struct") {
    val compilation =
      HigherTypingTestCompilation.test(
        """struct Moo<T> {
          |  bork T;
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Template call, recursively evaluate") {
    val compilation =
      HigherTypingTestCompilation.test(
        """struct Moo<T> {
          |  bork T;
          |}
          |struct Bork<T> {
          |  x Moo<T>;
          |}
          |""".stripMargin)
    val astrouts = compilation.expectAstrouts()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupStruct("Bork")
    main.headerRuneToType(CodeRuneS(compilation.keywords.T)) shouldEqual CoordTemplataType()
  }

  test("Type simple interface") {
    val compilation =
      HigherTypingTestCompilation.test(
        """interface Moo {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic interface") {
    val compilation =
      HigherTypingTestCompilation.test(
        """interface Moo<T> where T Ref {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic interface method") {
    val compilation =
      HigherTypingTestCompilation.test(
        """interface Moo<T> where T Ref {
          |  func bork(virtual self &Moo<T>) int;
          |}
          |""".stripMargin)
    val astrouts = compilation.expectAstrouts()
  }

  test("Infer generic type through param type template call") {
    val compilation =
      HigherTypingTestCompilation.test(
        """struct List<T> {
          |  moo T;
          |}
          |exported func moo<T>(x List<T>) {
          |}
          |""".stripMargin)
    val astrouts = compilation.expectAstrouts()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS(compilation.keywords.T)) shouldEqual CoordTemplataType()
  }

  test("Test evaluate Pack") {
    val compilation =
      HigherTypingTestCompilation.test(
        """func moo<T RefList>()
          |where T = Refs(int, bool)
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.expectAstrouts()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS(compilation.keywords.T)) shouldEqual PackTemplataType(CoordTemplataType())
  }

  test("Test infer Pack from result") {
    val compilation =
      HigherTypingTestCompilation.test(
        """func moo<T>()
          |where func moo(T, bool)str
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS(compilation.keywords.T)) shouldEqual CoordTemplataType()
  }

  test("Test infer Pack from empty result") {
    val compilation =
      HigherTypingTestCompilation.test(
        """func moo<P RefList>()
          |where P = Refs(), Prot[P, str]
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords)))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS(compilation.interner.intern(StrI("P")))) shouldEqual PackTemplataType(CoordTemplataType())
  }

//  test("Test cant solve empty Pack") {
//    val compilation =
//      AstronomerTestCompilation.test(
//        """func moo<P>()
//          |where P = ()
//          |{
//          |}
//          |""".stripMargin)
//    compilation.getAstrouts() match {
//      case Err(CouldntSolveRulesA(_, RuneTypeSolveError(range, IncompleteSolve(incompleteConclusions, unsolvedRules, unknownRunes)))) => {
//        vassert(unknownRunes.contains(CodeRuneS(StrI("P"))))
//      }
//    }
//  }

}
