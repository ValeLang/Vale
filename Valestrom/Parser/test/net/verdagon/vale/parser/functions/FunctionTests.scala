package net.verdagon.vale.parser.functions

import net.verdagon.vale.parser._
import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}


class BiggerTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Function then struct") {
    val program = compileProgram(
      """
        |fn main() int {}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunctionP(_) => }
    program.topLevelThings(1) match { case TopLevelStructP(_) => }
  }

  test("Simple function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() int {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(), None, None, Some(ParamsP(_,List())), FunctionReturnP(_, None, Some(_))),
        Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Pure function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() pure {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(PureAttributeP(_)), None, None, Some(ParamsP(_,List())), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Extern function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() extern;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(ExternAttributeP(_)), None, None, Some(ParamsP(_,List())), FunctionReturnP(_, None, None)),
        None) =>
    }
  }

  test("Abstract function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(AbstractAttributeP(_)), None, None, Some(ParamsP(_,List())), FunctionReturnP(_, None, None)),
        None) =>
    }
  }

  test("Pure and default region") {
    compile(
      CombinatorParsers.topLevelFunction,
      """fn findNearbyUnits() 'i int pure 'i { }
        |""".stripMargin) match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_,"findNearbyUnits")),
          List(PureAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,List())),
          FunctionReturnP(_, None, Some(NameOrRunePT(StringP(_,"int"))))),
        Some(BlockPE(_,List(VoidPE(_))))) =>
    }
  }

  test("Attribute after return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() Int abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")),
          List(AbstractAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,List())),
          FunctionReturnP(_, None, Some(NameOrRunePT(StringP(_,"Int"))))),
        None) =>
    }
  }

  test("Attribute before return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract Int;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")),
          List(AbstractAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,List())),
          FunctionReturnP(_, None, Some(NameOrRunePT(StringP(_,"Int"))))),
        None) =>
    }
  }

  test("Simple function with identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A coord>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A reg>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A pool>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
      StringP(_, "A"),
      List(
      TypeRuneAttributeP(_, RegionTypePR),
      PoolRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A arena>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        StringP(_, "A"),
        List(
          TypeRuneAttributeP(_, RegionTypePR),
          ArenaRuneAttributeP(_))) =>
    }
  }


  test("Readonly region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A ro>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        StringP(_, "A"),
        List(
          TypeRuneAttributeP(_, RegionTypePR),
          ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Virtual function") {
    compile(
      CombinatorParsers.topLevelFunction,
      """
        |fn doCivicDance(virtual this Car) int;
      """.stripMargin) shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "doCivicDance")), List(), None, None,
          Some(ParamsP(_, List(PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "this")), FinalP)), Some(NameOrRunePT(StringP(_, "Car"))), None, Some(AbstractP))))),
          FunctionReturnP(_, None, Some(NameOrRunePT(StringP(_, "int"))))),
        None) =>
    }
  }

  test("Bad thing for body") {
    compileProgramForError(
        """
          |fn doCivicDance(virtual this Car) moo blork
        """.stripMargin) match {
      case BadFunctionBodyError(_) =>
    }
  }
}
