package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class KindRuleTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail(msg);
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

  test("Empty Kind rule") {
    compile(rulePR, "_ Kind") shouldHave {
      case TypedPR(_,None,KindTypePR) =>
    }
  }

  test("Kind with rune") {
    compile(rulePR, "T Kind") shouldHave {
      case TypedPR(_,Some(NameP(_, "T")),KindTypePR) =>
    }
    //runedTKind("T")
  }

  test("Kind with destructure only") {
    compile(rulePR, "Kind(_)") shouldHave {
      case ComponentsPR(_,TypedPR(_,None,KindTypePR),Vector(TemplexPR(AnonymousRunePT(_)))) =>
    }
//        KindPR(None, KindTypePR, None, None)
  }

  test("Kind with rune and destructure") {
    compile(rulePR, "T Kind(_)") shouldHave {
      case ComponentsPR(_,TypedPR(_,Some(NameP(_, "T")),KindTypePR),Vector(TemplexPR(AnonymousRunePT(_)))) =>
    }
    compile(rulePR, "T Kind(mut)") shouldHave {
        case ComponentsPR(_,
          TypedPR(_,Some(NameP(_, "T")),KindTypePR),
          Vector(TemplexPR(MutabilityPT(_,MutableP)))) =>
    }
  }

  test("Kind matches plain Int") {
    compile(rulePR, "int") shouldHave {
      case TemplexPR(NameOrRunePT(NameP(_, "int"))) =>
    }
  }

  test("Kind with value") {
    compile(rulePR, "T Kind = int") shouldHave {
      case EqualsPR(_,TypedPR(_,Some(NameP(_, "T")),KindTypePR),TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
  }

  test("Kind with destructure and value") {
    compile(rulePR, "T Kind(_) = int") shouldHave {
      case EqualsPR(_,
          ComponentsPR(_,TypedPR(_,Some(NameP(_, "T")),KindTypePR),Vector(TemplexPR(AnonymousRunePT(_)))),
          TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile(rulePR, "T Kind = [int, bool]") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),KindTypePR),
          TemplexPR(
            ManualSequencePT(_,
              Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))))) =>
    }
  }

  test("Braces without Kind is sequence") {
    compile(rulePR, "[int, bool]") shouldHave {
      case TemplexPR(
          ManualSequencePT(_,
            Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool"))))) =>
    }
  }

  test("Templated struct, one arg") {
    compile(rulePR,"Moo<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
    compile(rulePR,"Moo<*int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(SharePT(_,NameOrRunePT(NameP(_, "int")))))) =>
    }
  }

  test("RWKILC") {
    compile(rulePR,"List<int>") shouldHave {
      case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "List")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
    compile(rulePR,"K int") shouldHave {
        case TypedPR(_,Some(NameP(_, "K")),IntTypePR) =>
    }
    compile(rulePR,"K<int>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "K")),Vector(NameOrRunePT(NameP(_, "int"))))) =>
    }
  }

  test("Templated struct, rune arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<R>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "R"))))) =>
    }
  }
  test("Templated struct, multiple args") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<int, str>") shouldHave {
        case TemplexPR(CallPT(_,NameOrRunePT(NameP(_, "Moo")),Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "str"))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<int>>") shouldHave {
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
    compile(rulePR,"Moo<Blarg<int, str>>") shouldHave {
        case TemplexPR(
          CallPT(_,
            NameOrRunePT(NameP(_, "Moo")),
            Vector(
                CallPT(_,
                  NameOrRunePT(NameP(_, "Blarg")),
                  Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "str"))))))) =>
    }
  }

  test("Repeater sequence") {
    compile(repeaterSeqRulePR, "[_ * _]") shouldHave {
      case RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compile(repeaterSeqRulePR, "[<imm> _ * _]") shouldHave {
      case RepeaterSequencePT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),AnonymousRunePT(_)) =>
    }
    compile(repeaterSeqRulePR, "[3 * int]") shouldHave {
      case RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), IntPT(_,3),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * int]") shouldHave {
        case RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[_ * int]") shouldHave {
        case RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), AnonymousRunePT(_),NameOrRunePT(NameP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * T]") shouldHave {
        case RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")),NameOrRunePT(NameP(_, "T"))) =>
    }
  }

  test("Regular sequence") {
    compile(manualSeqRulePR, "[]") shouldHave {
        case ManualSequencePT(_,Vector()) =>
    }
    compile(manualSeqRulePR, "[int]") shouldHave {
        case ManualSequencePT(_,Vector(NameOrRunePT(NameP(_, "int")))) =>
    }
    compile(manualSeqRulePR, "[int, bool]") shouldHave {
        case ManualSequencePT(_,Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, bool]") shouldHave {
        case ManualSequencePT(_,Vector(AnonymousRunePT(_), NameOrRunePT(NameP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, _]") shouldHave {
        case ManualSequencePT(_,Vector(AnonymousRunePT(_), AnonymousRunePT(_))) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "fn(Int)Void") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "int")))),NameOrRunePT(StringP(_, "void")))
//    compile(callableRulePR, "fn(T)R") shouldHave {//        case FunctionPT(None,PackPT(Vector(NameOrRunePT(StringP(_, "T")))),NameOrRunePT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compile(prototypeRulePR, "fn moo(int)void") shouldHave {
        case PrototypePT(_,NameP(_, "moo"), Vector(NameOrRunePT(NameP(_, "int"))),NameOrRunePT(NameP(_, "void"))) =>
    }
    compile(prototypeRulePR, "fn moo(T)R") shouldHave {
        case PrototypePT(_,NameP(_, "moo"), Vector(NameOrRunePT(NameP(_, "T"))),NameOrRunePT(NameP(_, "R"))) =>
    }
  }
}
