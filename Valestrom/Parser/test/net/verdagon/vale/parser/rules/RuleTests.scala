package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class RuleTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail();
      }
      case VParser.Success(expr, rest) => {
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

  private def checkFail[T](parser: VParser.Parser[T], code: String) = {
    VParser.parse(parser, "") match {
      case VParser.NoSuccess(_, _) =>
      case VParser.Success(_, rest) => {
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
      case CallPR(StringP(_, "implements"),List(TemplexPR(NameOrRunePRT(StringP(_, "MyObject"))), TemplexPR(NameOrRunePRT(StringP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(R, IObject)") shouldHave {
        case CallPR(StringP(_, "implements"),List(TemplexPR(NameOrRunePRT(StringP(_, "R"))), TemplexPR(NameOrRunePRT(StringP(_, "IObject"))))) =>
    }
    compile(rulePR, "implements(MyObject, T)") shouldHave {
        case CallPR(StringP(_, "implements"),List(TemplexPR(NameOrRunePRT(StringP(_, "MyObject"))), TemplexPR(NameOrRunePRT(StringP(_, "T"))))) =>
    }
    compile(rulePR, "exists(fn +(T)Int)") shouldHave {
        case CallPR(StringP(_, "exists"), List(TemplexPR(PrototypePRT(StringP(_, "+"), List(NameOrRunePRT(StringP(_, "T"))), NameOrRunePRT(StringP(_, "Int")))))) =>
    }
  }

  test("Super complicated") {
    compile(rulePR, "C = [I * X] | [N * T]") // succeeds
  }
//
//  test("resolveExactSignature") {
//    compile(rulePR, "C = resolveExactSignature(\"__call\", (&F, Int))") shouldHave {//      case EqualsPR(
//        TemplexPR(NameOrRunePRT(StringP(_, "C"))),
//        CallPR(
//          "resolveExactSignature",
//          List(
//            TemplexPR(StringPRT("__call")),
//            PackPR(List(TemplexPR(BorrowPRT(NameOrRunePRT(StringP(_, "F")))), TemplexPR(NameOrRunePRT(StringP(_, "Int"))))))))
//  }

  test("destructure prototype") {
    compile(rulePR, "Prot(_, _, T) = moo") shouldHave {
      case EqualsPR(
        ComponentsPR(
          TypedPR(None,PrototypeTypePR),
          List(TemplexPR(AnonymousRunePRT()), TemplexPR(AnonymousRunePRT()), TemplexPR(NameOrRunePRT(StringP(_, "T"))))),
        TemplexPR(NameOrRunePRT(StringP(_, "moo")))) =>
    }
  }

  test("prototype with coords") {
    compile(rulePR, "Prot(_, (Int, Bool), _)") shouldHave {
      case ComponentsPR(
        TypedPR(None,PrototypeTypePR),
        List(
          TemplexPR(AnonymousRunePRT()),
          TemplexPR(PackPRT(List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Bool"))))),
          TemplexPR(AnonymousRunePRT()))) =>
    }
  }
}
