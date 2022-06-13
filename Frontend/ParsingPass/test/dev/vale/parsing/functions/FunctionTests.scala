package dev.vale.parsing.functions

import dev.vale.{Collector, StrI, vassertOne}
import dev.vale.parsing.ast.{AbstractAttributeP, AbstractP, BlockPE, ConstantIntPE, CoordTypePR, ExternAttributeP, FunctionHeaderP, FunctionP, FunctionReturnP, IdentifyingRuneP, LocalNameDeclarationP, NameOrRunePT, NameP, ParamsP, PatternPP, PureAttributeP, ReadOnlyRuneAttributeP, RegionTypePR, TopLevelFunctionP, TopLevelStructP, TypeRuneAttributeP, VoidPE}
import dev.vale.parsing._
import dev.vale.parsing.ast.BlockPE
import dev.vale.lexing.BadFunctionBodyError
import org.scalatest.{FunSuite, Matchers}


class BiggerTests extends FunSuite with Collector with TestParseUtils {
  test("Function then struct") {
    val program =
      compileFile(
        """
          |exported func main() int {}
          |
          |struct mork { }
          |""".stripMargin).getOrDie()
    program.denizens(0) match { case TopLevelFunctionP(_) => }
    program.denizens(1) match { case TopLevelStructP(_) => }
  }

  test("Simple function") {
    compileDenizen("func sum() int {3}").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, Some(_))),
        Some(BlockPE(_, ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Pure function") {
    compileDenizen("pure func sum() {3}").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(PureAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _))))) =>
    }
  }

  test("Extern function") {
    vassertOne(compileFile("extern func sum();").getOrDie().denizens) match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(ExternAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None)) =>
    }
  }

  test("Abstract function") {
    compileDenizen("abstract func sum();").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(AbstractAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
        None)) =>
    }
  }

  test("Pure and default region") {
    compileDenizen("""pure func findNearbyUnits() 'i int 'i { }""").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("findNearbyUnits"))),
          Vector(PureAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, StrI("int")))))),
        Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("Attribute after return") {
    compileDenizen("abstract func sum() Int;").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))),
          Vector(AbstractAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, StrI("Int")))))),
        None)) =>
    }
  }

  test("Attribute before return") {
    compileDenizen("abstract func sum() Int;").getOrDie() match {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))),
          Vector(AbstractAttributeP(_)),
          None,
          None, Some(ParamsP(_,Vector())),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, StrI("Int")))))),
        None)) =>
    }
  }

  test("Simple function with identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<A>(a A){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, StrI("A")), Vector()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<A Ref>(a A){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, StrI("A")), Vector(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'a>(){}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, StrI("a")), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Readonly region rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r ro>(){}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, StrI("r")), Vector(TypeRuneAttributeP(_, RegionTypePR), ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r>(a 'r &Marine){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, NameP(_, StrI("r")), Vector(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r = pool>(a 'r &Marine){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, StrI("r")),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool readonly region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r ro = pool>(a 'r &Marine){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, StrI("r")),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR),
          ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'x = arena>(a 'x &Marine){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, StrI("x")),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }


  test("Readonly region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'x>(a 'x &Marine){a}").getOrDie()
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        NameP(_, StrI("x")),
        Vector(
          TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Virtual function") {
    compileDenizen("func doCivicDance(virtual this Car) int;".stripMargin).getOrDie() shouldHave {
      case TopLevelFunctionP(FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("doCivicDance"))), Vector(), None,
          None, Some(ParamsP(_, Vector(PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, StrI("this")))), Some(NameOrRunePT(NameP(_, StrI("Car")))), None, Some(AbstractP(_)))))),
          FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, StrI("int")))))),
        None)) =>
    }
  }

  test("Bad thing for body") {
    compileDenizen(
        """
          |func doCivicDance(virtual this Car) moo blork
        """.stripMargin).expectErr().error match {
      case BadFunctionBodyError(_) =>
    }
  }
}
