package net.verdagon.vale.scout.rules

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.{vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class RuleScoutTests extends FunSuite with Matchers {
  private def compile(code: String): List[IRulexSR] = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => {
        val programS = Scout.scoutProgram(program0)
        programS.lookupFunction("main").templateRules
      }
    }
  }

  test("A") {
    val expectedRulesS =
      List(
        EqualsSR(
          TypedSR(CodeRuneS("B"),CoordTypeSR),
          TemplexSR(CallST(NameST(CodeTypeNameS("List")),List(RuneST(CodeRuneS("A")))))),
        EqualsSR(
          TypedSR(CodeRuneS("C"),CoordTypeSR),
          OrSR(List(TemplexSR(RuneST(CodeRuneS("B"))), TemplexSR(RuneST(CodeRuneS("A"))), TemplexSR(NameST(CodeTypeNameS("int")))))),
        TypedSR(CodeRuneS("A"),CoordTypeSR))
    RuleSUtils.getDistinctOrderedRunesForRulexes(expectedRulesS) shouldEqual
      List(CodeRuneS("B"), CodeRuneS("A"), CodeRuneS("C"))

    val results =
      compile(
        """fn main<A>(a A)
          |rules(
          |  B Ref = List<A>,
          |  C Ref = B | A | int)
          |{ }
          |""".stripMargin)
    results match {
      case List(
        EqualsSR(
          TypedSR(br1 @ CodeRuneS("B"),CoordTypeSR),
          TemplexSR(CallST(NameST(CodeTypeNameS("List")),List(RuneST(ar1 @ CodeRuneS("A")))))),
        EqualsSR(
          TypedSR(CodeRuneS("C"),CoordTypeSR),
          OrSR(List(TemplexSR(RuneST(br2)), TemplexSR(RuneST(ar3)), TemplexSR(NameST(CodeTypeNameS("int")))))),
        TypedSR(ar2,CoordTypeSR)) => {
        vassert(br1 == br2)
        vassert(ar1 == ar2)
        vassert(ar1 == ar3)
      }
    }
  }

  test("B") {
    val rulesS = compile("fn main() rules(B Ref = List<A>, A Ref, C Ref = B | A | Int) {}")
    RuleSUtils.getDistinctOrderedRunesForRulexes(rulesS) match {
      case List(
        CodeRuneS("B"),
        CodeRuneS("A"),
        CodeRuneS("C")) =>
    }
  }
}
