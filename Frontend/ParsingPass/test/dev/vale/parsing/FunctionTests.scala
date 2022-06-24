package dev.vale.parsing

import dev.vale.parsing.ast._
import dev.vale.{Collector, StrI, vassertOne}
import org.scalatest.FunSuite


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
            Some(IdentifyingRunesP(_,Vector(IdentifyingRuneP(_,NameP(_,StrI("T")),Vector())))),
            None,
            _,
            _),
          _)) =>
    }
  }
}
