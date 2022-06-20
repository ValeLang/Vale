package dev.vale.parsing

import dev.vale.parsing.ast._
import dev.vale.{Collector, StrI, vassertOne}
import org.scalatest.FunSuite


class FunctionTests extends FunSuite with Collector with TestParseUtils {
  test("Simple function") {
    vassertOne(compileFile("""func main() { }""").getOrDie().denizens) match {
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
    vassertOne(compileFile("""func main(moo T) T { }""").getOrDie().denizens) shouldHave {
      case null =>
    }
  }

  test("Function with generics") {
    vassertOne(compileFile("""func main<T>() { }""").getOrDie().denizens) shouldHave {
      case null =>
    }
  }
}
