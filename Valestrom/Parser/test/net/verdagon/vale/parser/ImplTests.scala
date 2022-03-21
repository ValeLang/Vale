package net.verdagon.vale.parser

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.ast.{CallPT, IdentifyingRuneP, IdentifyingRunesP, ImplP, MutabilityPT, MutableP, NameOrRunePT, NameP, TopLevelImplP}
import net.verdagon.vale.{Collector, Tests, vassert}
import org.scalatest.{FunSuite, Matchers}


class ImplTests extends FunSuite with Matchers with Collector with TestParseUtils {
  test("Templated impl") {
    val p = new Parser(GlobalOptions(true, true, true, true))
    compile(
      p.parseTopLevelThing(_),
      """
        |impl<T> MyInterface<T> for SomeStruct<T>;
      """.stripMargin) shouldHave {
      case Some(TopLevelImplP(ImplP(_,
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "T"), Vector())))),
        None,
        Some(CallPT(_,NameOrRunePT(NameP(_, "SomeStruct")), Vector(NameOrRunePT(NameP(_, "T"))))),
        CallPT(_,NameOrRunePT(NameP(_, "MyInterface")), Vector(NameOrRunePT(NameP(_, "T")))),
        Vector()))) =>
    }
  }

  test("Impling a template call") {
    val p = new Parser(GlobalOptions(true, true, true, true))
    compile(
      p.parseTopLevelThing(_),
      """
        |impl IFunction1<mut, int, int> for MyIntIdentity;
        |""".stripMargin) shouldHave {
      case Some(TopLevelImplP(ImplP(_,
        None,
        None,
        Some(NameOrRunePT(NameP(_, "MyIntIdentity"))),
        CallPT(_,NameOrRunePT(NameP(_, "IFunction1")), Vector(MutabilityPT(_,MutableP), NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "int")))),
        Vector()))) =>
    }
  }
}
