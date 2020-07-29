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
      case TypedPR(None,KindTypePR) =>
    }
  }

  test("Kind with rune") {
    compile(rulePR, "T Kind") shouldHave {
      case TypedPR(Some(StringP(_, "T")),KindTypePR) =>
    }
    //runedTKind("T")
  }

  test("Kind with destructure only") {
    compile(rulePR, "Kind(_)") shouldHave {
      case ComponentsPR(TypedPR(None,KindTypePR),List(TemplexPR(AnonymousRunePRT()))) =>
    }
//        KindPR(None, KindTypePR, None, None)
  }

  test("Kind with rune and destructure") {
    compile(rulePR, "T Kind(_)") shouldHave {
      case ComponentsPR(TypedPR(Some(StringP(_, "T")),KindTypePR),List(TemplexPR(AnonymousRunePRT()))) =>
    }
    compile(rulePR, "T Kind(mut)") shouldHave {
        case ComponentsPR(
          TypedPR(Some(StringP(_, "T")),KindTypePR),
          List(TemplexPR(MutabilityPRT(MutableP)))) =>
    }
  }

  test("Kind matches plain Int") {
    compile(rulePR, "int") shouldHave {
      case TemplexPR(NameOrRunePRT(StringP(_, "int"))) =>
    }
  }

  test("Kind with value") {
    compile(rulePR, "T Kind = int") shouldHave {
      case EqualsPR(TypedPR(Some(StringP(_, "T")),KindTypePR),TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
  }

  test("Kind with destructure and value") {
    compile(rulePR, "T Kind(_) = int") shouldHave {
      case EqualsPR(
          ComponentsPR(TypedPR(Some(StringP(_, "T")),KindTypePR),List(TemplexPR(AnonymousRunePRT()))),
          TemplexPR(NameOrRunePRT(StringP(_, "int")))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile(rulePR, "T Kind = [int, bool]") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "T")),KindTypePR),
          TemplexPR(
            ManualSequencePRT(
              List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool")))))) =>
    }
  }

  test("Braces without Kind is sequence") {
    compile(rulePR, "[int, bool]") shouldHave {
      case TemplexPR(
          ManualSequencePRT(
            List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool"))))) =>
    }
  }

//  test("Simple kind filters") {
//    compile(rulePR, ":Struct") shouldHave {//        case TypedPR(None,StructTypePR)
//    compile(rulePR, ":Interface") shouldHave {//        case TypedPR(None,InterfaceTypePR)
////    compile(rulePR, ":Callable") shouldHave {////        case TypedPR(None,CallableTypePR)
//    compile(rulePR, ":KindTemplate") shouldHave {//        case TypedPR(None,CitizenTemplateTypePR)
//    compile(rulePR, ":Seq") shouldHave {//        case TypedPR(None,SequenceTypePR)
//  }

  test("Templated struct, one arg") {
    compile(rulePR,"Moo<int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
    compile(rulePR,"Moo<*int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(SharePRT(NameOrRunePRT(StringP(_, "int")))))) =>
    }
  }

  test("RWKILC") {
    compile(rulePR,"List<int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "List")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
    compile(rulePR,"K int") shouldHave {
        case TypedPR(Some(StringP(_, "K")),IntTypePR) =>
    }
    compile(rulePR,"K<int>") shouldHave {
        case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "K")),List(NameOrRunePRT(StringP(_, "int"))))) =>
    }
  }

  test("Templated struct, rune arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<R>") shouldHave {
        case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "R"))))) =>
    }
  }
  test("Templated struct, multiple args") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<int, str>") shouldHave {
        case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "str"))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<int>>") shouldHave {
        case TemplexPR(
          CallPRT(
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "int"))))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with multiple arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<int, str>>") shouldHave {
        case TemplexPR(
          CallPRT(
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "str"))))))) =>
    }
  }

  test("Repeater sequence") {
    compile(repeaterSeqRulePR, "[_ * _]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(MutableP), AnonymousRunePRT(),AnonymousRunePRT()) =>
    }
    compile(repeaterSeqRulePR, "[<imm> _ * _]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(ImmutableP), AnonymousRunePRT(),AnonymousRunePRT()) =>
    }
    compile(repeaterSeqRulePR, "[3 * int]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(MutableP), IntPRT(3),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * int]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[_ * int]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), AnonymousRunePRT(),NameOrRunePRT(StringP(_, "int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * T]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "T"))) =>
    }
  }

  test("Regular sequence") {
    compile(manualSeqRulePR, "[]") shouldHave {
        case ManualSequencePRT(List()) =>
    }
    compile(manualSeqRulePR, "[int]") shouldHave {
        case ManualSequencePRT(List(NameOrRunePRT(StringP(_, "int")))) =>
    }
    compile(manualSeqRulePR, "[int, bool]") shouldHave {
        case ManualSequencePRT(List(NameOrRunePRT(StringP(_, "int")), NameOrRunePRT(StringP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, bool]") shouldHave {
        case ManualSequencePRT(List(AnonymousRunePRT(), NameOrRunePRT(StringP(_, "bool")))) =>
    }
    compile(manualSeqRulePR, "[_, _]") shouldHave {
        case ManualSequencePRT(List(AnonymousRunePRT(), AnonymousRunePRT())) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "fn(Int)Void") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "int")))),NameOrRunePRT(StringP(_, "void")))
//    compile(callableRulePR, "fn(T)R") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "T")))),NameOrRunePRT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compile(prototypeRulePR, "fn moo(int)void") shouldHave {
        case PrototypePRT(StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "int"))),NameOrRunePRT(StringP(_, "void"))) =>
    }
    compile(prototypeRulePR, "fn moo(T)R") shouldHave {
        case PrototypePRT(StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "T"))),NameOrRunePRT(StringP(_, "R"))) =>
    }
  }
}
