package net.verdagon.vale.parser

import net.verdagon.vale.parser.ast.{CallPT, IdentifyingRuneP, IdentifyingRunesP, ImplP, MutabilityPT, MutableP, NameOrRunePT, NameP}
import net.verdagon.vale.parser.old.{CombinatorParsers, OldTestParseUtils}
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class ImplTests extends FunSuite with Matchers with Collector with OldTestParseUtils {
  test("Templated impl") {
    oldCompile(
      CombinatorParsers.impl,
      """
        |impl<T> MyInterface<T> for SomeStruct<T>;
      """.stripMargin) shouldHave {
      case ImplP(_,
      Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "T"), Vector())))),
      None,
      CallPT(_,NameOrRunePT(NameP(_, "SomeStruct")), Vector(NameOrRunePT(NameP(_, "T")))),
      CallPT(_,NameOrRunePT(NameP(_, "MyInterface")), Vector(NameOrRunePT(NameP(_, "T"))))) =>
    }
  }

  test("Impling a template call") {
    oldCompile(
      CombinatorParsers.impl,
      """
        |impl IFunction1<mut, int, int> for MyIntIdentity;
        |""".stripMargin) shouldHave {
      case ImplP(_,
      None,
      None,
      NameOrRunePT(NameP(_, "MyIntIdentity")),
      CallPT(_,NameOrRunePT(NameP(_, "IFunction1")), Vector(MutabilityPT(_,MutableP), NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "int"))))) =>
    }
  }

}
