package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
import org.scalatest.{FunSuite, Matchers}


class ImplTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Templated impl") {
    compile(
      CombinatorParsers.impl,
      """
        |impl<T> SomeStruct<T> for MyInterface<T>;
      """.stripMargin) shouldHave {
      case ImplP(_,
      Some(IdentifyingRunesP(_, List(IdentifyingRuneP(_, StringP(_, "T"), List())))),
      None,
      CallPT(_,NameOrRunePT(StringP(_, "SomeStruct")), List(NameOrRunePT(StringP(_, "T")))),
      CallPT(_,NameOrRunePT(StringP(_, "MyInterface")), List(NameOrRunePT(StringP(_, "T"))))) =>
    }
  }

  test("Impling a template call") {
    compile(
      CombinatorParsers.impl,
      """
        |impl MyIntIdentity for IFunction1<mut, int, int>;
        |""".stripMargin) shouldHave {
      case ImplP(_,
      None,
      None,
      NameOrRunePT(StringP(_, "MyIntIdentity")),
      CallPT(_,NameOrRunePT(StringP(_, "IFunction1")), List(MutabilityPT(MutableP), NameOrRunePT(StringP(_, "int")), NameOrRunePT(StringP(_, "int"))))) =>
    }
  }

}
