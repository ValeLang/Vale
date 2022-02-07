package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{Parser}
import net.verdagon.vale.scout.{CodeRuneS, CoordTemplataType, PackTemplataType, ProgramS, RuneTypeSolveError, Scout}
import net.verdagon.vale._
import org.scalatest.{FunSuite, Matchers}

class AstronomerTests extends FunSuite with Matchers  {
  def compileProgramForError(compilation: AstronomerCompilation): ICompileErrorA = {
    compilation.getAstrouts() match {
      case Ok(result) => vfail("Expected error, but actually parsed invalid program:\n" + result)
      case Err(err) => err
    }
  }

  test("Type simple main function") {
    val compilation =
      AstronomerTestCompilation.test(
      """fn main() export {
        |}
        |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic function") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn moo<T>() rules(T Ref) export {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Infer coord type from parameters") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn moo<T>(x T) export {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS("T")) shouldEqual CoordTemplataType
  }

  test("Type simple struct") {
    val compilation =
      AstronomerTestCompilation.test(
        """struct Moo {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic struct") {
    val compilation =
      AstronomerTestCompilation.test(
        """struct Moo<T> {
          |  bork T;
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Template call, recursively evaluate") {
    val compilation =
      AstronomerTestCompilation.test(
        """struct Moo<T> {
          |  bork T;
          |}
          |struct Bork<T> {
          |  x Moo<T>;
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupStruct("Bork")
    main.runeToType(CodeRuneS("T")) shouldEqual CoordTemplataType
  }

  test("Type simple interface") {
    val compilation =
      AstronomerTestCompilation.test(
        """interface Moo {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic interface") {
    val compilation =
      AstronomerTestCompilation.test(
        """interface Moo<T> rules(T Ref) {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Type simple generic interface method") {
    val compilation =
      AstronomerTestCompilation.test(
        """interface Moo<T> rules(T Ref) {
          |  fn bork(virtual self *Moo<T>) int;
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
  }

  test("Infer generic type through param type template call") {
    val compilation =
      AstronomerTestCompilation.test(
        """struct List<T> {
          |  moo T;
          |}
          |fn moo<T>(x List<T>) export {
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS("T")) shouldEqual CoordTemplataType
  }

  test("Test evaluate Pack") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn moo<T>()
          |rules(T = Refs(int, bool))
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.expectAstrouts()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS("T")) shouldEqual PackTemplataType(CoordTemplataType)
  }

  test("Test infer Pack from result") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn moo<T>()
          |rules(Prot("moo", Refs(T, bool), str))
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS("T")) shouldEqual CoordTemplataType
  }

  test("Test infer Pack from empty result") {
    val compilation =
      AstronomerTestCompilation.test(
        """fn moo<P>()
          |rules(P = Refs(), Prot("moo", P, str))
          |{
          |}
          |""".stripMargin)
    val astrouts = compilation.getAstrouts().getOrDie()
    val program = vassertSome(astrouts.get(PackageCoordinate.TEST_TLD))
    val main = program.lookupFunction("moo")
    main.runeToType(CodeRuneS("P")) shouldEqual PackTemplataType(CoordTemplataType)
  }

//  test("Test cant solve empty Pack") {
//    val compilation =
//      AstronomerTestCompilation.test(
//        """fn moo<P>()
//          |rules(P = ())
//          |{
//          |}
//          |""".stripMargin)
//    compilation.getAstrouts() match {
//      case Err(CouldntSolveRulesA(_, RuneTypeSolveError(range, IncompleteSolve(incompleteConclusions, unsolvedRules, unknownRunes)))) => {
//        vassert(unknownRunes.contains(CodeRuneS("P")))
//      }
//    }
//  }

}
