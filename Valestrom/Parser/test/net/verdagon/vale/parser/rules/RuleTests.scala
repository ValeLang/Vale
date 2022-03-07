package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.old.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{AnonymousRunePT, BuiltinCallPR, ComponentsPR, EqualsPR, IRulexPR, NameOrRunePT, NameP, PackPT, PatternPP, PrototypePT, PrototypeTypePR, TemplexPR, TypedPR}
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.parser.templex.TemplexParser
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class RuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
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
    checkFail(isInterfacePR, "")
    checkFail(level3PR, "")
    checkFail(level4PR, "")
    checkFail(level5PR, "")
    checkFail(manualSeqRulePR, "")
    checkFail(packRulePR, "")
    checkFail(staticSizedArrayPR, "")
    checkFail(runtimeSizedArrayPR, "")
    checkFail(rulePR, "")
    checkFail(ruleTemplexPR, "")
    checkFail(ruleTemplexPR, "")
    checkFail(ruleTemplexSetPR, "")
//    checkFail(templateRulesPR, "")
    checkFail(refListCompoundMutabilityPR, "")
    checkFail(packPR, "")
  }

  test("Relations") {
    compile("implements(MyObject, IObject)") shouldHave {
      case BuiltinCallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "MyObject"))), TemplexPR(NameOrRunePT(NameP(_, "IObject"))))) =>
    }
    compile("implements(R, IObject)") shouldHave {
        case BuiltinCallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "R"))), TemplexPR(NameOrRunePT(NameP(_, "IObject"))))) =>
    }
    compile("implements(MyObject, T)") shouldHave {
        case BuiltinCallPR(_, NameP(_, "implements"),Vector(TemplexPR(NameOrRunePT(NameP(_, "MyObject"))), TemplexPR(NameOrRunePT(NameP(_, "T"))))) =>
    }
    compile("exists(func +(T)int)") shouldHave {
        case BuiltinCallPR(_, NameP(_, "exists"), Vector(TemplexPR(PrototypePT(_,NameP(_, "+"), Vector(NameOrRunePT(NameP(_, "T"))), NameOrRunePT(NameP(_, "int")))))) =>
    }
  }

  test("Super complicated") {
    compile("C = any([#I]X, [#N]T)") // succeeds
  }

  test("destructure prototype") {
    compile("Prot[_, _, T] = moo") shouldHave {
      case EqualsPR(_,
        ComponentsPR(_,
          PrototypeTypePR,
          Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(NameP(_, "T"))))),
        TemplexPR(NameOrRunePT(NameP(_, "moo")))) =>
    }
  }

  test("prototype with coords") {
    compile("Prot[_, pack(int, bool), _]") shouldHave {
      case ComponentsPR(_,
        PrototypeTypePR,
        Vector(
          TemplexPR(AnonymousRunePT(_)),
          BuiltinCallPR(_,NameP(_,"pack"),Vector(TemplexPR(NameOrRunePT(NameP(_, "int"))), TemplexPR(NameOrRunePT(NameP(_, "bool"))))),
          TemplexPR(AnonymousRunePT(_)))) =>
    }
  }
}
