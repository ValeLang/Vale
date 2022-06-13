package dev.vale.parsing.rules

import dev.vale.{Collector, StrI}
import dev.vale.parsing.ast.{AnonymousRunePT, BuiltinCallPR, ComponentsPR, EqualsPR, IRulexPR, NameOrRunePT, NameP, PrototypePT, PrototypeTypePR, TemplexPR}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.parsing._
import dev.vale.parsing.ast.PatternPP
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}

class RuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compileRulex(code)
//    compile(new TemplexParser().parseRule(_), code)
  }

  test("Relations") {
    compile("implements(MyObject, IObject)") shouldHave {
      case BuiltinCallPR(_, NameP(_, StrI("implements")),Vector(TemplexPR(NameOrRunePT(NameP(_, StrI("MyObject")))), TemplexPR(NameOrRunePT(NameP(_, StrI("IObject")))))) =>
    }
    compile("implements(R, IObject)") shouldHave {
        case BuiltinCallPR(_, NameP(_, StrI("implements")),Vector(TemplexPR(NameOrRunePT(NameP(_, StrI("R")))), TemplexPR(NameOrRunePT(NameP(_, StrI("IObject")))))) =>
    }
    compile("implements(MyObject, T)") shouldHave {
        case BuiltinCallPR(_, NameP(_, StrI("implements")),Vector(TemplexPR(NameOrRunePT(NameP(_, StrI("MyObject")))), TemplexPR(NameOrRunePT(NameP(_, StrI("T")))))) =>
    }
    compile("exists(func +(T)int)") shouldHave {
        case BuiltinCallPR(_, NameP(_, StrI("exists")), Vector(TemplexPR(PrototypePT(_,NameP(_, StrI("+")), Vector(NameOrRunePT(NameP(_, StrI("T")))), NameOrRunePT(NameP(_, StrI("int"))))))) =>
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
          Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(NameP(_, StrI("T")))))),
        TemplexPR(NameOrRunePT(NameP(_, StrI("moo"))))) =>
    }
  }

  test("prototype with coords") {
    compile("Prot[_, pack(int, bool), _]") shouldHave {
      case ComponentsPR(_,
        PrototypeTypePR,
        Vector(
          TemplexPR(AnonymousRunePT(_)),
          BuiltinCallPR(_,NameP(_, StrI("pack")),Vector(TemplexPR(NameOrRunePT(NameP(_, StrI("int")))), TemplexPR(NameOrRunePT(NameP(_, StrI("bool")))))),
          TemplexPR(AnonymousRunePT(_)))) =>
    }
  }
}
