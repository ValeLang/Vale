package net.verdagon.vale.parser.functions

import net.verdagon.vale.parser._
import net.verdagon.vale.{Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class BiggerTests extends FunSuite with Matchers with Collector with TestParseUtils {
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

  test("Simple function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() int {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, Some(_))),
        Some(BlockPE(_, Vector(ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Pure function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() pure {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(PureAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, Vector(ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Extern function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() extern;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(ExternAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None) =>
    }
  }

  test("Abstract function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(AbstractAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
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
          Some(NameP(_,"findNearbyUnits")),
          Vector(PureAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"int"))))),
        Some(BlockPE(_,Vector(VoidPE(_))))) =>
    }
  }

  test("Attribute after return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() Int abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")),
          Vector(AbstractAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"Int"))))),
        None) =>
    }
  }

  test("Attribute before return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract Int;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")),
          Vector(AbstractAttributeP(_)),
          None,
          None,
          Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_,"Int"))))),
        None) =>
    }
  }

  test("Simple function with identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A coord>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A reg>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A pool>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
      NameP(_, "A"),
      Vector(
      TypeRuneAttributeP(_, RegionTypePR),
      PoolRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A arena>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "A"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR),
          ArenaRuneAttributeP(_))) =>
    }
  }


  test("Readonly region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A ro>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "A"),
        Vector(
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
          Some(NameP(_, "doCivicDance")), Vector(), None, None,
          Some(ParamsP(_, Vector(PatternPP(_, _,Some(CaptureP(_,LocalNameP(NameP(_, "this")))), Some(NameOrRunePT(NameP(_, "Car"))), None, Some(AbstractP))))),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, "int"))))),
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
