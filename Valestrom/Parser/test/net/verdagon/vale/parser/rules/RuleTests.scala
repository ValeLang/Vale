package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class RuleTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail();
      }
      case CombinatorParsers.Success(expr, rest) => {
        if (!rest.atEnd) {
          vfail(rest.pos.longString)
        }
        expr
      }
    }
  }
  private def compile[T](code: String): PatternPP = {
    compile(atomPattern, code)
  }

  private def checkFail[T](parser: CombinatorParsers.Parser[T], code: String) = {
    CombinatorParsers.parse(parser, "") match {
      case CombinatorParsers.NoSuccess(_, _) =>
      case CombinatorParsers.Success(_, rest) => {
        if (!rest.atEnd) {
          fail(rest.pos.longString)
        }
        fail()
      }
    }
  }

  test("Nothing matches empty string") {
    checkFail(prototypeRulePR, "")
    checkFail(callableRulePR, "")
    checkFail(existsPR, "")
    checkFail(implementsPR, "")
    checkFail(keywordOrIdentifierOrRuneRuleTemplexPR, "")
    checkFail(level0PR, "")
    checkFail(level1PR, "")
    checkFail(level2PR, "")
    checkFail(level3PR, "")
    checkFail(level4PR, "")
    checkFail(level5PR, "")
    checkFail(manualSeqRulePR, "")
    checkFail(packRulePR, "")
    checkFail(repeaterSeqRulePR, "")
    checkFail(rulePR, "")
    checkFail(ruleTemplexPR, "")
    checkFail(ruleTemplexPR, "")
    checkFail(ruleTemplexSetPR, "")
    checkFail(templateRulesPR, "")
    checkFail(packPR, "")
  }

  test("Relations") {
    compile(rulePR, "implements(MyObject, IObject)") shouldHave {
      case CallPR(_, StringP(_, "implements"),List(TemplexPR(NameOrRunePT(StringP(_, "MyObject"))), TemplexPR(NameOrRunePT(StringP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(R, IObject)") shouldHave {
        case CallPR(_, StringP(_, "implements"),List(TemplexPR(NameOrRunePT(StringP(_, "R"))), TemplexPR(NameOrRunePT(StringP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(MyObject, T)") shouldHave {
        case CallPR(_, StringP(_, "implements"),List(TemplexPR(NameOrRunePT(StringP(_, "MyObject"))), TemplexPR(NameOrRunePT(StringP(_, "T"))))) =>
    }
    compile(rulePR, "exists(fn +(T)int)") shouldHave {
        case CallPR(_, StringP(_, "exists"), List(TemplexPR(PrototypePT(_,StringP(_, "+"), List(NameOrRunePT(StringP(_, "T"))), NameOrRunePT(StringP(_, "int")))))) =>
    }
  }

  test("Super complicated") {
    compile(rulePR, "C = [I * X] | [N * T]") // succeeds
  }
//
//  test("resolveExactSignature") {
//    compile(rulePR, "C = resolveExactSignature(\"__call\", (&F, Int))") shouldHave {//      case EqualsPR(_,
//        TemplexPR(NameOrRunePT(StringP(_, "C"))),
//        CallPR(
//          "resolveExactSignature",
//          List(
//            TemplexPR(StringPT("__call")),
//            PackPR(List(TemplexPR(BorrowPT(NameOrRunePT(StringP(_, "F")))), TemplexPR(NameOrRunePT(StringP(_, "int"))))))))
//  }

  test("destructure prototype") {
    compile(rulePR, "Prot(_, _, T) = moo") shouldHave {
      case EqualsPR(_,
        ComponentsPR(_,
          TypedPR(_,None,PrototypeTypePR),
          List(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(StringP(_, "T"))))),
        TemplexPR(NameOrRunePT(StringP(_, "moo")))) =>
    }
  }

  test("prototype with coords") {
    compile(rulePR, "Prot(_, (int, bool), _)") shouldHave {
      case ComponentsPR(_,
        TypedPR(_,None,PrototypeTypePR),
        List(
          TemplexPR(AnonymousRunePT(_)),
          TemplexPR(PackPT(_,List(NameOrRunePT(StringP(_, "int")), NameOrRunePT(StringP(_, "bool"))))),
          TemplexPR(AnonymousRunePT(_)))) =>
    }
  }
}
