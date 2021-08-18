package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{Collector, vfail}
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
      case CallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "MyObject"))), TemplexPR(NameOrRunePT(NameP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(R, IObject)") shouldHave {
        case CallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "R"))), TemplexPR(NameOrRunePT(NameP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(MyObject, T)") shouldHave {
        case CallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "MyObject"))), TemplexPR(NameOrRunePT(NameP(_, "T"))))) =>
    }
    compile(rulePR, "exists(fn +(T)int)") shouldHave {
        case CallPR(_, NameP(_, "exists"), Vector(TemplexPR(PrototypePT(_,NameP(_, "+"), Vector(NameOrRunePT(NameP(_, "T"))), NameOrRunePT(NameP(_, "int")))))) =>
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
//          Vector(
//            TemplexPR(StringPT("__call")),
//            PackPR(Vector(TemplexPR(BorrowPT(NameOrRunePT(StringP(_, "F")))), TemplexPR(NameOrRunePT(StringP(_, "int"))))))))
//  }

  test("destructure prototype") {
    compile(rulePR, "Prot(_, _, T) = moo") shouldHave {
      case EqualsPR(_,
        ComponentsPR(_,
          TypedPR(_,None,PrototypeTypePR),
          Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(NameP(_, "T"))))),
        TemplexPR(NameOrRunePT(NameP(_, "moo")))) =>
    }
  }

  test("prototype with coords") {
    compile(rulePR, "Prot(_, (int, bool), _)") shouldHave {
      case ComponentsPR(_,
        TypedPR(_,None,PrototypeTypePR),
        Vector(
          TemplexPR(AnonymousRunePT(_)),
          TemplexPR(PackPT(_,Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool"))))),
          TemplexPR(AnonymousRunePT(_)))) =>
    }
  }
}
