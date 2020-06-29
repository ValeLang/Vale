package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class KindRuleTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail(msg);
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
    compile(rulePR, "Int") shouldHave {
      case TemplexPR(NameOrRunePRT(StringP(_, "Int"))) =>
    }
  }

  test("Kind with value") {
    compile(rulePR, "T Kind = Int") shouldHave {
      case EqualsPR(TypedPR(Some(StringP(_, "T")),KindTypePR),TemplexPR(NameOrRunePRT(StringP(_, "Int")))) =>
    }
  }

  test("Kind with destructure and value") {
    compile(rulePR, "T Kind(_) = Int") shouldHave {
      case EqualsPR(
          ComponentsPR(TypedPR(Some(StringP(_, "T")),KindTypePR),List(TemplexPR(AnonymousRunePRT()))),
          TemplexPR(NameOrRunePRT(StringP(_, "Int")))) =>
    }
  }

  test("Kind with sequence in value spot") {
    compile(rulePR, "T Kind = [Int, Bool]") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "T")),KindTypePR),
          TemplexPR(
            ManualSequencePRT(
              List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Bool")))))) =>
    }
  }

  test("Braces without Kind is sequence") {
    compile(rulePR, "[Int, Bool]") shouldHave {
      case TemplexPR(
          ManualSequencePRT(
            List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Bool"))))) =>
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
    compile(rulePR,"Moo<Int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "Int"))))) =>
    }
    compile(rulePR,"Moo<*Int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(SharePRT(NameOrRunePRT(StringP(_, "Int")))))) =>
    }
  }

  test("RWKILC") {
    compile(rulePR,"List<Int>") shouldHave {
      case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "List")),List(NameOrRunePRT(StringP(_, "Int"))))) =>
    }
    compile(rulePR,"K Int") shouldHave {
        case TypedPR(Some(StringP(_, "K")),IntTypePR) =>
    }
    compile(rulePR,"K<Int>") shouldHave {
        case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "K")),List(NameOrRunePRT(StringP(_, "Int"))))) =>
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
    compile(rulePR,"Moo<Int, Str>") shouldHave {
        case TemplexPR(CallPRT(NameOrRunePRT(StringP(_, "Moo")),List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Str"))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with one arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<Int>>") shouldHave {
        case TemplexPR(
          CallPRT(
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "Int"))))))) =>
    }
  }
  test("Templated struct, arg is another templated struct with multiple arg") {
    // Make sure every pattern on the way down to kind can match Int
    compile(rulePR,"Moo<Blarg<Int, Str>>") shouldHave {
        case TemplexPR(
          CallPRT(
            NameOrRunePRT(StringP(_, "Moo")),
            List(
                CallPRT(
                  NameOrRunePRT(StringP(_, "Blarg")),
                  List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Str"))))))) =>
    }
  }

  test("Repeater sequence") {
    compile(repeaterSeqRulePR, "[_ * _]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(MutableP), AnonymousRunePRT(),AnonymousRunePRT()) =>
    }
    compile(repeaterSeqRulePR, "[<imm> _ * _]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(ImmutableP), AnonymousRunePRT(),AnonymousRunePRT()) =>
    }
    compile(repeaterSeqRulePR, "[3 * Int]") shouldHave {
      case RepeaterSequencePRT(MutabilityPRT(MutableP), IntPRT(3),NameOrRunePRT(StringP(_, "Int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * Int]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "Int"))) =>
    }
    compile(repeaterSeqRulePR, "[_ * Int]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), AnonymousRunePRT(),NameOrRunePRT(StringP(_, "Int"))) =>
    }
    compile(repeaterSeqRulePR, "[N * T]") shouldHave {
        case RepeaterSequencePRT(MutabilityPRT(MutableP), NameOrRunePRT(StringP(_, "N")),NameOrRunePRT(StringP(_, "T"))) =>
    }
  }

  test("Regular sequence") {
    compile(manualSeqRulePR, "[]") shouldHave {
        case ManualSequencePRT(List()) =>
    }
    compile(manualSeqRulePR, "[Int]") shouldHave {
        case ManualSequencePRT(List(NameOrRunePRT(StringP(_, "Int")))) =>
    }
    compile(manualSeqRulePR, "[Int, Bool]") shouldHave {
        case ManualSequencePRT(List(NameOrRunePRT(StringP(_, "Int")), NameOrRunePRT(StringP(_, "Bool")))) =>
    }
    compile(manualSeqRulePR, "[_, Bool]") shouldHave {
        case ManualSequencePRT(List(AnonymousRunePRT(), NameOrRunePRT(StringP(_, "Bool")))) =>
    }
    compile(manualSeqRulePR, "[_, _]") shouldHave {
        case ManualSequencePRT(List(AnonymousRunePRT(), AnonymousRunePRT())) =>
    }
  }

//  test("Callable kind rule") {
//    compile(callableRulePR, "fn(Int)Void") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "Int")))),NameOrRunePRT(StringP(_, "Void")))
//    compile(callableRulePR, "fn(T)R") shouldHave {//        case FunctionPRT(None,PackPRT(List(NameOrRunePRT(StringP(_, "T")))),NameOrRunePRT(StringP(_, "R")))
//  }

  test("Prototype kind rule") {
    compile(prototypeRulePR, "fn moo(Int)Void") shouldHave {
        case PrototypePRT(StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "Int"))),NameOrRunePRT(StringP(_, "Void"))) =>
    }
    compile(prototypeRulePR, "fn moo(T)R") shouldHave {
        case PrototypePRT(StringP(_, "moo"), List(NameOrRunePRT(StringP(_, "T"))),NameOrRunePRT(StringP(_, "R"))) =>
    }
  }
}
