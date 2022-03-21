package net.verdagon.vale.parser.rules


import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast._

import net.verdagon.vale.parser.templex.TemplexParser
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class KindRuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
  }

  test("Empty Kind rule") {
    compile("_ Kind") shouldHave {
      case TypedPR(_,None,KindTypePR) =>
    }
  }

  test("Kind with rune") {
    compile("T Kind") shouldHave {
      case TypedPR(_,Some(NameP(_, "T")),KindTypePR) =>
    }
    //runedTKind("T")
  }

  test("Kind with destructure only") {
    compile("Kind[_]") shouldHave {
      case ComponentsPR(_,KindTypePR,Vector(TemplexPR(AnonymousRunePT(_)))) =>
    }
//        KindPR(None, KindTypePR, None, None)
  }

  test("Kind matches plain Int") {
    compile("int") shouldHave {
      case TemplexPR(NameOrRunePT(NameP(_, "int"))) =>
    }
  }

  test("Kind with value") {
    compile("T Kind = int") shouldHave {
      case EqualsPR(_,TypedPR(_,Some(NameP(_, "T")),KindTypePR),TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile("T Kind = (int, bool)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),KindTypePR),
          TemplexPR(
            TuplePT(_,
              Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))))) =>
    }
  }

  test("Lone sequence") {
    compile("(int, bool)") shouldHave {
      case TemplexPR(
          TuplePT(_,
            Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool"))))) =>
    }
  }

  test("Templated struct, one arg") {
    compile("Moo<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
    compile("Moo<@int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(InterpretedPT(_,ShareP,NameOrRunePT(NameP(_, "int")))))) =>
    }
  }

  test("RWKILC") {
    compile("List<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "List")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
    compile("K int") shouldHave {
        case TypedPR(_,Some(NameP(_, "K")),IntTypePR) =>
    }
    compile("K<int>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "K")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
  }

  test("Templated struct, rune arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<R>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "R"))))) =>
    }
  }
  test("Templated struct, multiple args") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<int, str>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "str"))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<Blarg<int>>") shouldHave {
        case TemplexPR(
          CallPT(_,
            NameOrRunePT(NameP(_, "Moo")),
            Vector(
                CallPT(_,
                  NameOrRunePT(NameP(_, "Blarg")),
                  Vector(NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with multiple arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<Blarg<int, str>>") shouldHave {
        case TemplexPR(
          CallPT(_,
            NameOrRunePT(NameP(_, "Moo")),
            Vector(
                CallPT(_,
                  NameOrRunePT(NameP(_, "Blarg")),
                  Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "str"))))))) =>
    }
  }

  test("Static sized array") {
    compile(new TemplexParser().parseArray(_), "[#_]_") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compile(new TemplexParser().parseArray(_), "[#_]<imm>_") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compile(new TemplexParser().parseArray(_), "[#3]int") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), IntPT(_,3),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(new TemplexParser().parseArray(_), "[#N]int") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(new TemplexParser().parseArray(_), "[#_]int") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(new TemplexParser().parseArray(_), "[#N]T") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")),NameOrRunePT(NameP(_, "T"))) =>
    }
  }

  test("Regular sequence") {
    compile(new TemplexParser().parseTuple(_), "()") shouldHave {
        case TuplePT(_,Vector()) =>
    }
    compile(new TemplexParser().parseTuple(_), "(int)") shouldHave {
        case TuplePT(_,Vector(NameOrRunePT(NameP(_, "int")))) =>
    }
    compile(new TemplexParser().parseTuple(_), "(int, bool)") shouldHave {
        case TuplePT(_,Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))) =>
    }
    compile(new TemplexParser().parseTuple(_), "(_, bool)") shouldHave {
        case TuplePT(_,Vector(AnonymousRunePT(_), NameOrRunePT(NameP(_, "bool")))) =>
    }
    compile(new TemplexParser().parseTuple(_), "(_, _)") shouldHave {
        case TuplePT(_,Vector(AnonymousRunePT(_), AnonymousRunePT(_))) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "func(Int)Void") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "int")))),NameOrRunePT(StringP(_, "void")))
//    compile(callableRulePR, "func(T)R") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "T")))),NameOrRunePT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compile(new TemplexParser().parsePrototype(_), "func moo(int)void") shouldHave {
        case PrototypePT(_,NameP(_, "moo"), Vector(NameOrRunePT(NameP(_, "int"))),NameOrRunePT(NameP(_, "void"))) =>
    }
    compile(new TemplexParser().parsePrototype(_), "func moo(T)R") shouldHave {
        case PrototypePT(_,NameP(_, "moo"), Vector(NameOrRunePT(NameP(_, "T"))),NameOrRunePT(NameP(_, "R"))) =>
    }
  }
}
