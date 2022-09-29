package dev.vale.parsing.rules

import dev.vale.{Collector, StrI, vimpl}
import dev.vale.parsing.ast.{AnonymousRunePT, CallPT, ComponentsPR, EqualsPR, FinalP, IRulexPR, ImmutableP, IntPT, IntTypePR, InterpretedPT, KindTypePR, MutabilityPT, MutableP, NameOrRunePT, NameP, FuncPT, ShareP, StaticSizedArrayPT, TemplexPR, TuplePT, TypedPR, VariabilityPT}
import dev.vale.parsing.templex.TemplexParser
import dev.vale.parsing._
import dev.vale.parsing.ast._
import org.scalatest.{FunSuite, Matchers}

class KindRuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compileRulex(code)
//    compile(new TemplexParser().parseRule(_), code)
  }

  test("Empty Kind rule") {
    compile("_ Kind") shouldHave {
      case TypedPR(_,None,KindTypePR) =>
    }
  }

  test("Kind with rune") {
    compile("T Kind") shouldHave {
      case TypedPR(_,Some(NameP(_, StrI("T"))),KindTypePR) =>
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
      case TemplexPR(NameOrRunePT(NameP(_, StrI("int")))) =>
    }
  }

  test("Kind with value") {
    compile("T Kind = int") shouldHave {
      case EqualsPR(_,TypedPR(_,Some(NameP(_, StrI("T"))),KindTypePR),TemplexPR(NameOrRunePT(NameP(_, StrI("int"))))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile("T Kind = (int, bool)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, StrI("T"))),KindTypePR),
          TemplexPR(
            TuplePT(_,
              Vector(NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("bool"))))))) =>
    }
  }

  test("Lone sequence") {
    compile("(int, bool)") shouldHave {
      case TemplexPR(
          TuplePT(_,
            Vector(NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("bool")))))) =>
    }
  }

  test("Templated struct, one arg") {
    compile("Moo<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("Moo"))),Vector(NameOrRunePT(NameP(_, StrI("int")))))) =>
    }
    compile("Moo<@int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("Moo"))),Vector(InterpretedPT(_,ShareP,NameOrRunePT(NameP(_, StrI("int"))))))) =>
    }
  }

  test("RWKILC") {
    compile("List<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("List"))),Vector(NameOrRunePT(NameP(_, StrI("int")))))) =>
    }
    compile("K Int") shouldHave {
        case TypedPR(_,Some(NameP(_, StrI("K"))),IntTypePR) =>
    }
    compile("K<int>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("K"))),Vector(NameOrRunePT(NameP(_, StrI("int")))))) =>
    }
  }

  test("Templated struct, rune arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<R>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("Moo"))),Vector(NameOrRunePT(NameP(_, StrI("R")))))) =>
    }
  }
  test("Templated struct, multiple args") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<int, str>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, StrI("Moo"))),Vector(NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("str")))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<Blarg<int>>") shouldHave {
        case TemplexPR(
          CallPT(_,
            NameOrRunePT(NameP(_, StrI("Moo"))),
            Vector(
                CallPT(_,
                  NameOrRunePT(NameP(_, StrI("Blarg"))),
                  Vector(NameOrRunePT(NameP(_, StrI("int")))))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with multiple arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile("Moo<Blarg<int, str>>") shouldHave {
        case TemplexPR(
          CallPT(_,
            NameOrRunePT(NameP(_, StrI("Moo"))),
            Vector(
                CallPT(_,
                  NameOrRunePT(NameP(_, StrI("Blarg"))),
                  Vector(NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("str")))))))) =>
    }
  }

  test("Static sized array") {
    compileTemplex("[#_]_") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compileTemplex("[#_]<imm>_") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compileTemplex("[#3]int") shouldHave {
      case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), IntPT(_,3),NameOrRunePT(NameP(_, StrI("int")))) =>
    }
    compileTemplex("[#N]int") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, StrI("N"))),NameOrRunePT(NameP(_, StrI("int")))) =>
    }
    compileTemplex("[#_]int") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),NameOrRunePT(NameP(_, StrI("int")))) =>
    }
    compileTemplex("[#N]T") shouldHave {
        case StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, StrI("N"))),NameOrRunePT(NameP(_, StrI("T")))) =>
    }
  }

  test("Regular sequence") {
    compileTemplex("()") shouldHave {
        case TuplePT(_,Vector()) =>
    }
    compileTemplex("(int)") shouldHave {
        case TuplePT(_,Vector(NameOrRunePT(NameP(_, StrI("int"))))) =>
    }
    compileTemplex("(int, bool)") shouldHave {
        case TuplePT(_,Vector(NameOrRunePT(NameP(_, StrI("int"))), NameOrRunePT(NameP(_, StrI("bool"))))) =>
    }
    compileTemplex("(_, bool)") shouldHave {
        case TuplePT(_,Vector(AnonymousRunePT(_), NameOrRunePT(NameP(_, StrI("bool"))))) =>
    }
    compileTemplex("(_, _)") shouldHave {
        case TuplePT(_,Vector(AnonymousRunePT(_), AnonymousRunePT(_))) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "func(Int)Void") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "int")))),NameOrRunePT(StringP(_, "void")))
//    compile(callableRulePR, "func(T)R") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "T")))),NameOrRunePT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compileTemplex("func moo(int)void") shouldHave {
        case FuncPT(_,NameP(_, StrI("moo")), _, Vector(NameOrRunePT(NameP(_, StrI("int")))),NameOrRunePT(NameP(_, StrI("void")))) =>
    }
    compileTemplex("func moo(T)R") shouldHave {
        case FuncPT(_,NameP(_, StrI("moo")), _, Vector(NameOrRunePT(NameP(_, StrI("T")))),NameOrRunePT(NameP(_, StrI("R")))) =>
    }
  }
}
