package dev.vale.parsing.rules

import dev.vale.Collector
import dev.vale.parsing.TestParseUtils
import dev.vale.parsing.ast.{AnonymousRunePT, BuiltinCallPR, ComponentsPR, EqualsPR, IRulexPR, NameOrRunePT, NameP, PrototypePT, PrototypeTypePR, TemplexPR}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.parsing._
import dev.vale.parsing.ast.PatternPP
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

class RuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
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
