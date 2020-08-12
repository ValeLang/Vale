package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
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
      case TypedPR(_,Some(StringP(_, "T")),KindTypePR) =>
    }
    //runedTKind("T")
  }

  test("Kind with destructure only") {
    compile(rulePR, "Kind(_)") shouldHave {
      case ComponentsPR(_,TypedPR(_,None,KindTypePR),List(TemplexPR(AnonymousRunePRT(_)))) =>
    }
//        KindPR(None, KindTypePR, None, None)
  }

  test("Kind with rune and destructure") {
    compile(rulePR, "T Kind(_)") shouldHave {
      case ComponentsPR(_,TypedPR(_,Some(StringP(_, "T")),KindTypePR),List(TemplexPR(AnonymousRunePRT(_)))) =>
    }
    compile(rulePR, "T Kind(mut)") shouldHave {
        case ComponentsPR(_,
          TypedPR(_,Some(StringP(_, "T")),KindTypePR),
          List(TemplexPR(MutabilityPRT(_,MutableP)))) =>
    }
  }

  test("Kind matches plain Int") {
    compile(rulePR, "int") shouldHave {
      case TemplexPR(NameOrRunePRT(StringP(_, "int"))) =>
    }
  }

  test("Kind with value") {
    compile(rulePR, "T Kind = int") shouldHave {
      case EqualsPR(_,TypedPR(_,Some(StringP(_, "T")),KindTypePR),TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
  }

  test("Kind with destructure and value") {
    compile(rulePR, "T Kind(_) = int") shouldHave {
      case EqualsPR(_,
          ComponentsPR(_,TypedPR(_,Some(StringP(_, "T")),KindTypePR),List(TemplexPR(AnonymousRunePRT(_)))),
          TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile(rulePR, "T Kind = [int, bool]") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "T")),KindTypePR),
          TemplexPR(
            ManualSequencePRT(_,
              List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool")))))) =>
    }
  }

  test("Braces without Kind is sequence") {
    compile(rulePR, "[int, bool]") shouldHave {
      case TemplexPR(
          ManualSequencePRT(_,
            List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool"))))) =>
    }
  }

//  test("Simple kind filters") {
//    compile(rulePR, ":Struct") shouldHave {//        case TypedPR(_,None,StructTypePR)
//    compile(rulePR, ":Interface") shouldHave {//        case TypedPR(_,None,InterfaceTypePR)
////    compile(rulePR, ":Callable") shouldHave {////        case TypedPR(_,None,CallableTypePR)
//    compile(rulePR, ":KindTemplate") shouldHave {//        case TypedPR(_,None,CitizenTemplateTypePR)
//    compile(rulePR, ":Seq") shouldHave {//        case TypedPR(_,None,SequenceTypePR)
//  }

  test("Templated struct, one arg") {
    compile(rulePR,"Moo<int>") shouldHave {
      case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
    compile(rulePR,"Moo<*int>") shouldHave {
      case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "Moo")),List(SharePRT(_,NameOrRunePRT(StringP(_, "int")))))) =>
    }
  }

  test("RWKILC") {
    compile(rulePR,"List<int>") shouldHave {
      case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "List")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
    compile(rulePR,"K int") shouldHave {
        case TypedPR(_,Some(StringP(_, "K")),IntTypePR) =>
    }
    compile(rulePR,"K<int>") shouldHave {
        case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "K")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
  }

  test("Templated struct, rune arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<R>") shouldHave {
        case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "R"))))) =>
    }
  }
  test("Templated struct, multiple args") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<int, str>") shouldHave {
        case TemplexPR(CallPRT(_,NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "str"))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<int>>") shouldHave {
        case TemplexPR(
          CallPRT(_,
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(_,
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "int"))))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with multiple arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<int, str>>") shouldHave {
        case TemplexPR(
          CallPRT(_,
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(_,
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "str"))))))) =>
    }
  }

  test("Repeater sequence") {
    compile(repeaterSeqRulePR, "[_ * _]") shouldHave {
      case RepeaterSequencePRT(_,MutabilityPRT(_,MutableP), AnonymousRunePRT(_),AnonymousRunePRT(_)) =>
    }
    compile(repeaterSeqRulePR, "[<imm> _ * _]") shouldHave {
      case RepeaterSequencePRT(_,MutabilityPRT(_,ImmutableP), AnonymousRunePRT(_),AnonymousRunePRT(_)) =>
    }
    compile(repeaterSeqRulePR, "[3 * int]") shouldHave {
      case RepeaterSequencePRT(_,MutabilityPRT(_,MutableP), IntPRT(_,3),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * int]") shouldHave {
        case RepeaterSequencePRT(_,MutabilityPRT(_,MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[_ * int]") shouldHave {
        case RepeaterSequencePRT(_,MutabilityPRT(_,MutableP), AnonymousRunePRT(_),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * T]") shouldHave {
        case RepeaterSequencePRT(_,MutabilityPRT(_,MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "T"))) =>
    }
  }

  test("Regular sequence") {
    compile(manualSeqRulePR, "[]") shouldHave {
        case ManualSequencePRT(_,List()) =>
    }
    compile(manualSeqRulePR, "[int]") shouldHave {
        case ManualSequencePRT(_,List(NameOrRunePRT(StringP(_, "int")))) =>
    }
    compile(manualSeqRulePR, "[int, bool]") shouldHave {
        case ManualSequencePRT(_,List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, bool]") shouldHave {
        case ManualSequencePRT(_,List(AnonymousRunePRT(_), NameOrRunePRT(StringP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, _]") shouldHave {
        case ManualSequencePRT(_,List(AnonymousRunePRT(_), AnonymousRunePRT(_))) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "fn(Int)Void") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "int")))),NameOrRunePRT(StringP(_, "void")))
//    compile(callableRulePR, "fn(T)R") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "T")))),NameOrRunePRT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compile(prototypeRulePR, "fn moo(int)void") shouldHave {
        case PrototypePRT(_,StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "int"))),NameOrRunePRT(StringP(_, "void"))) =>
    }
    compile(prototypeRulePR, "fn moo(T)R") shouldHave {
        case PrototypePRT(_,StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "T"))),NameOrRunePRT(StringP(_, "R"))) =>
    }
  }
}
