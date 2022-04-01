package dev.vale.parsing.functions

import dev.vale.Collector
import dev.vale.parsing.{BadFunctionBodyError, TestParseUtils}
import dev.vale.parsing.ast.{AbstractAttributeP, AbstractP, BlockPE, ConstantIntPE, CoordTypePR, ExternAttributeP, FunctionHeaderP, FunctionP, FunctionReturnP, IdentifyingRuneP, LocalNameDeclarationP, NameOrRunePT, NameP, ParamsP, PatternPP, PureAttributeP, ReadOnlyRuneAttributeP, RegionTypePR, TopLevelFunctionP, TopLevelStructP, TypeRuneAttributeP, VoidPE}
import dev.vale.parsing._
import dev.vale.parsing.ast.BlockPE
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}


class BiggerTests extends FunSuite with Collector with TestParseUtils {
  test("Function then struct") {
    val program =
      compile(
        makeParser().runParserInner(_),
      """
        |exported func main() int {}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunctionP(_) => }
    program.topLevelThings(1) match { case TopLevelStructP(_) => }
  }

  test("Simple function") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum() int {3}") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, Some(_))),
        Some(BlockPE(_, ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Pure function") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "pure func sum() {3}") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(PureAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Extern function") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "extern func sum();") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(ExternAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None)) =>
    }
  }

  test("Abstract function") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "abstract func sum();") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(AbstractAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None)) =>
    }
  }

  test("Pure and default region") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      """pure func findNearbyUnits() 'i int 'i { }
        |""".stripMargin) match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_,"findNearbyUnits")),
          Vector(PureAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"int"))))),
        Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("Attribute after return") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "abstract func sum() Int;") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")),
          Vector(AbstractAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"Int"))))),
        None)) =>
    }
  }

  test("Attribute before return") {
    compileMaybe(
      makeParser().parseTopLevelThing(_), "abstract func sum() Int;") match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")),
          Vector(AbstractAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"Int"))))),
        None)) =>
    }
  }

  test("Simple function with identifying rune") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<A>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<A Ref>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'a>(){}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "a"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Readonly region rune") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'r ro>(){}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "r"), Vector(TypeRuneAttributeP(_, RegionTypePR), ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'r>(a 'r &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "r"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'r = pool>(a 'r &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "r"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool readonly region") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'r ro = pool>(a 'r &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "r"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR),
          ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'x = arena>(a 'x &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "x"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }


  test("Readonly region") {
    val TopLevelFunctionP(func) = compileMaybe(
      makeParser().parseTopLevelThing(_), "func sum<'x>(a 'x &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "x"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Virtual function") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      """
        |func doCivicDance(virtual this Car) int;
      """.stripMargin) shouldHave {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "doCivicDance")), Vector(), None,
          None, Some(ParamsP(_, Vector(PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, "this"))), Some(NameOrRunePT(NameP(_, "Car"))), None, Some(AbstractP(_)))))),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, "int"))))),
        None)) =>
    }
  }

  test("Bad thing for body") {
    compileForError(
      makeParser().runParserInner(_),
        """
          |func doCivicDance(virtual this Car) moo blork
        """.stripMargin) match {
      case BadFunctionBodyError(_) =>
    }
  }
}
