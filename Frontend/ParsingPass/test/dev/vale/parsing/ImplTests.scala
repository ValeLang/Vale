package dev.vale.parsing

import dev.vale.lexing.Lexer
import dev.vale.{Collector, Interner, StrI, vassertOne, vimpl}
import dev.vale.parsing.ast.{CallPT, IDenizenP, GenericParameterP, GenericParametersP, ImplP, MutabilityPT, MutableP, NameOrRunePT, NameP, TopLevelImplP}
import dev.vale.options.GlobalOptions
import org.scalatest.{FunSuite, Matchers}


class ImplTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Normal impl") {
    vassertOne(
      compileFile(
        """
          |impl MyInterface for SomeStruct;
      """.stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelImplP(ImplP(_,
          None,
          None,
          Some(NameOrRunePT(NameP(_, StrI("SomeStruct")))),
          NameOrRunePT(NameP(_, StrI("MyInterface"))),
          Vector())) =>
    }
  }

  test("Templated impl") {
    vassertOne(
      compileFile(
        """
          |impl<T> MyInterface<T> for SomeStruct<T>;
      """.stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelImplP(ImplP(_,
        Some(GenericParametersP(_, Vector(GenericParameterP(_, NameP(_, StrI("T")), _, Vector(), None)))),
        None,
        Some(CallPT(_,NameOrRunePT(NameP(_, StrI("SomeStruct"))), Vector(NameOrRunePT(NameP(_, StrI("T")))))),
        CallPT(_,NameOrRunePT(NameP(_, StrI("MyInterface"))), Vector(NameOrRunePT(NameP(_, StrI("T"))))),
        Vector())) =>
    }
  }

  test("Impling a template call") {
    vassertOne(
      compileFile(
        """
          |impl IFunction1<mut, int, int> for MyIntIdentity;
          |""".stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelImplP(ImplP(_,
        None,
        None,
        Some(NameOrRunePT(NameP(_, StrI("MyIntIdentity")))),
        CallPT(_,NameOrRunePT(NameP(_, StrI("IFunction1"))), Vector(MutabilityPT(_,MutableP), NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("int"))))),
        Vector())) =>
    }
  }
}
