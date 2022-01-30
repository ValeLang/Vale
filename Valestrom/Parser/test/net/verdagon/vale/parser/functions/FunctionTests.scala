package net.verdagon.vale.parser.functions

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{AbstractAttributeP, AbstractP, ArenaRuneAttributeP, BlockPE, ConstantIntPE, CoordTypePR, ExternAttributeP, FunctionHeaderP, FunctionP, FunctionReturnP, IdentifyingRuneP, LocalNameDeclarationP, NameOrRunePT, NameP, ParamsP, PatternPP, PoolRuneAttributeP, PureAttributeP, ReadOnlyRuneAttributeP, RegionTypePR, TopLevelFunctionP, TopLevelStructP, TypeRuneAttributeP, VoidPE}
import net.verdagon.vale.parser.old.{CombinatorParsers, OldTestParseUtils}
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class BiggerTests extends FunSuite with Collector with TestParseUtils {
  test("Function then struct") {
    val program =
      compile(
        Parser.runParserInner(_),
      """
        |fn main() int export {}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunctionP(_) => }
    program.topLevelThings(1) match { case TopLevelStructP(_) => }
  }

  test("Simple function") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() int {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, Some(_))),
        Some(BlockPE(_, ConstantIntPE(_, 3, _)))) =>
    }
  }

  test("Pure function") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() pure {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(PureAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _)))) =>
    }
  }

  test("Extern function") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() extern;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(ExternAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None) =>
    }
  }

  test("Abstract function") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "sum")), Vector(AbstractAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None) =>
    }
  }

  test("Pure and default region") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace),
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
        Some(BlockPE(_,VoidPE(_)))) =>
    }
  }

  test("Attribute after return") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() Int abstract;") match {
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
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum() abstract Int;") match {
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
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<A>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<A coord>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<A reg>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<'A>(a 'A *Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, "A"), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<'A pool>(a 'A *Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
      NameP(_, "A"),
      Vector(
      TypeRuneAttributeP(_, RegionTypePR),
      PoolRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<'A arena>(a 'A *Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "A"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR),
          ArenaRuneAttributeP(_))) =>
    }
  }


  test("Readonly region") {
    val func = compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace), "fn sum<'A ro>(a 'A *Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, "A"),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR),
          ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Virtual function") {
    compileMaybe(
      Parser.parseFunction(_, StopBeforeCloseBrace),
      """
        |fn doCivicDance(virtual this Car) int;
      """.stripMargin) shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, "doCivicDance")), Vector(), None, None,
          Some(ParamsP(_, Vector(PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, "this"))), Some(NameOrRunePT(NameP(_, "Car"))), None, Some(AbstractP(_)))))),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, "int"))))),
        None) =>
    }
  }

  test("Bad thing for body") {
    compileForError(
      Parser.runParserInner(_),
        """
          |fn doCivicDance(virtual this Car) moo blork
        """.stripMargin) match {
      case BadFunctionBodyError(_) =>
    }
  }
}
