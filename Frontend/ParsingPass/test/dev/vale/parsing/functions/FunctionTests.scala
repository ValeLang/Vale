package dev.vale.parsing.functions

import dev.vale.{Collector, StrI, vassertOne, vimpl}
import dev.vale.parsing.ast._
import dev.vale.parsing._
import dev.vale.lexing.{BadFunctionBodyError, LightFunctionMustHaveParamTypes}
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.{FunSuite, Matchers}


class FunctionTests extends FunSuite with Collector with TestParseUtils {
  test("Simple function") {
    vassertOne(compileFileExpect("""func main() { }""").denizens) match {
      case TopLevelFunctionP(
      FunctionP(_,
      FunctionHeaderP(_,
      Some(NameP(_,StrI("main"))),
      Vector(),None,None,Some(ParamsP(_,Vector())),
      FunctionReturnP(_,None,None)),
      Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("Functions with weird names") {
    vassertOne(compileFileExpect("""func !=() { }""").denizens)
    vassertOne(compileFileExpect("""func <=() { }""").denizens)
    vassertOne(compileFileExpect("""func >=() { }""").denizens)
    vassertOne(compileFileExpect("""func <() { }""").denizens)
    vassertOne(compileFileExpect("""func >() { }""").denizens)
    vassertOne(compileFileExpect("""func ==() { }""").denizens)
  }

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

  test("Simple function with return") {
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

  test("Function ending with set") {
    compileDenizenExpect(
      """
        |func moo() {
        |  set bork = value
        |}
        |""".stripMargin)
  }

  test("Extern function generated") {
    vassertOne(compileFile("extern(\"bork\") func sum();").getOrDie().denizens) match {
      case TopLevelFunctionP(FunctionP(_,
      FunctionHeaderP(_,
      Some(NameP(_, StrI("sum"))), Vector(BuiltinAttributeP(_, NameP(_, StrI("bork")))), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, None)),
      None)) =>
    }
  }

  test("Extern function with return") {
    vassertOne(compileFile("extern func sum() int;").getOrDie().denizens) match {
      case TopLevelFunctionP(FunctionP(_,
      FunctionHeaderP(_,
      Some(NameP(_, StrI("sum"))), Vector(ExternAttributeP(_)), None, None, Some(ParamsP(_,Vector())), FunctionReturnP(_, None, Some(NameOrRunePT(NameP(_, StrI("int")))))),
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
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_, NameP(_, StrI("A")), None, Vector(), None) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<A Ref>(a A){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_, NameP(_, StrI("A")), Some(GenericParameterTypeP(_, CoordTypePR)), Vector(), None) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'a>(){}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_, NameP(_, StrI("a")), Some(GenericParameterTypeP(_, RegionTypePR)), Vector(), None) =>
    }
  }

  test("Readonly region rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r ro>(){}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_, NameP(_, StrI("r")), Some(GenericParameterTypeP(_, RegionTypePR)), Vector(ReadOnlyRegionRuneAttributeP(_)), None) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r>(a 'r &Marine){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_, NameP(_, StrI("r")), Some(GenericParameterTypeP(_, RegionTypePR)), Vector(), None) =>
    }
  }

  test("Pool region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r = pool>(a 'r &Marine){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_,
        NameP(_, StrI("r")),
        Some(GenericParameterTypeP(_, RegionTypePR)),
        Vector(),
        Some(NameOrRunePT(NameP(_,StrI("pool"))))) =>
    }
  }

  test("Pool readonly region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'r ro = pool>(a 'r &Marine){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_,
        NameP(_, StrI("r")),
        Some(GenericParameterTypeP(_, RegionTypePR)),
        Vector(ReadOnlyRegionRuneAttributeP(_)),
        Some(NameOrRunePT(NameP(_,StrI("pool"))))) =>
    }
  }

  test("Arena region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'x = arena>(a 'x &Marine){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_,
        NameP(_, StrI("x")),
        Some(GenericParameterTypeP(_, RegionTypePR)),
        Vector(),
        Some(NameOrRunePT(NameP(_,StrI("arena"))))) =>
    }
  }


  test("Readonly region") {
    val TopLevelFunctionP(func) =
      compileDenizen("func sum<'x>(a 'x &Marine){a}").getOrDie()
    func.header.genericParameters.get.params.head match {
      case GenericParameterP(_,
        NameP(_, StrI("x")),
        Some(GenericParameterTypeP(_, RegionTypePR)),
        Vector(),
        None) =>
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


  test("Function with parameter and return") {
    vassertOne(compileFileExpect("""func main(moo T) T { }""").denizens) shouldHave {
      case TopLevelFunctionP(
        FunctionP(_,
          FunctionHeaderP(_,
            Some(NameP(_,StrI("main"))),Vector(),None,None,
            Some(ParamsP(_,Vector(PatternPP(_,None,Some(LocalNameDeclarationP(NameP(_,StrI("moo")))),Some(NameOrRunePT(NameP(_,StrI("T")))),None,None)))),
            FunctionReturnP(_,None,Some(NameOrRunePT(NameP(_,StrI("T")))))),
          Some(BlockPE(_,VoidPE(_))))) =>
    }
  }

  test("Function with generics") {
    vassertOne(compileFileExpect("""func main<T>() { }""").denizens) shouldHave {
      case TopLevelFunctionP(
        FunctionP(_,
          FunctionHeaderP(_,
            Some(NameP(_,StrI("main"))),
            Vector(),
            Some(GenericParametersP(_,Vector(GenericParameterP(_,NameP(_,StrI("T")),None,Vector(), None)))),
            None,
            _,
            _),
          _)) =>
    }
  }

  test("Impl function") {
    compileDenizenExpect(
      "func maxHp(virtual this Marine) { return 5; }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("maxHp"))),Vector(), None, None,
          Some(
            ParamsP(
              _,
              Vector(
                PatternPP(_,_,
                  Some(LocalNameDeclarationP(NameP(_, StrI("this")))),
                  Some(NameOrRunePT(NameP(_, StrI("Marine")))),
                  None,
                  Some(AbstractP(_)))))),
          FunctionReturnP(_, None,None)),
        Some(BlockPE(_, _))) =>
    }
  }

  test("Param") {
    val program = compileDenizenExpect("func call(f F){f()}")
    program shouldHave {
      case PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, StrI("f")))),Some(NameOrRunePT(NameP(_, StrI("F")))),None,None) =>
    }
  }

  test("Func with rules") {
    compileDenizenExpect(
      "func sum () where X Int {3}") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("sum"))), Vector(), None, Some(_), Some(_), FunctionReturnP(_, None, None)),
        Some(BlockPE(_, ConstantIntPE(_, 3, _)))) =>
    }
  }

  test("Func with func bound") {
    compileDenizenExpect(
      "func sum<T>() where func moo(&T)void {3}") shouldHave {
      case TopLevelFunctionP(
        FunctionP(_,
          FunctionHeaderP(_,_,_,_,
            Some(
              TemplateRulesP(_,
                Vector(
                  TemplexPR(
                    FuncPT(_,
                      NameP(_,StrI("moo")),
                      _,
                      Vector(InterpretedPT(_,BorrowP,NameOrRunePT(NameP(_,StrI("T"))))),
                      NameOrRunePT(NameP(_,StrI("void")))))))),
          _,_),_)) =>
    }
  }



  test("Identifying runes") {
    compileDenizenExpect(
      "func wrap<A, F>(a A) { }") shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(NameP(_, StrI("wrap"))), Vector(),
          Some(
            GenericParametersP(_,
              Vector(
              GenericParameterP(_, NameP(_, StrI("A")), None, Vector(), None),
              GenericParameterP(_, NameP(_, StrI("F")), None, Vector(), None)))),
          None,
          Some(ParamsP(_, Vector(Patterns.capturedWithTypeRune("a", "A")))),
          FunctionReturnP(_, None, None)),
        Some(BlockPE(_, VoidPE(_)))) =>
    }
  }

  test("Never signature") {
    // This test is here because we were parsing the first _ of __Never as an anonymous
    // rune then stopping.
    compileDenizenExpect(
      "func __vbi_panic() __Never {}") shouldHave {
      case NameOrRunePT(NameP(_, StrI("__Never"))) =>
    }
  }

  test("Should require identifying runes") {
    val error =
      compileDenizen(
        """
          |func do(callable) int {callable()}
          |""".stripMargin).expectErr().error
    error match {
      case LightFunctionMustHaveParamTypes(_, 0) =>
    }
  }

  test("Short self") {
    compileDenizenExpect(
      """
        |interface IMoo {
        |  func moo(&self) {}
        |}
        |""".stripMargin) shouldHave {
      case TopLevelInterfaceP(
        InterfaceP(_,
          NameP(_,StrI("IMoo")),Vector(),None,None,None,_,
          Vector(
            FunctionP(_,
              FunctionHeaderP(_,
                Some(NameP(_, StrI("moo"))),
                Vector(),None,None,
                Some(
                  ParamsP(_,
                    Vector(
                      PatternPP(_,
                        Some(_),
                        Some(LocalNameDeclarationP(NameP(_, StrI("self")))),
                        None,None,None)))),
                _),
              _)))) =>
    }
  }
}
