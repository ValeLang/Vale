package net.verdagon.vale.parser.patterns

import net.verdagon.vale.{parser, vfail, vimpl}
import net.verdagon.vale.parser.Patterns.{fromEnv, withType}
import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import org.scalatest.{FunSuite, Matchers}

class TypeTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail(msg + "\n" + input);
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
    VParser.parse(parser, code) match {
      case VParser.NoSuccess(_, _) =>
      case VParser.Success(_, rest) => {
        if (!rest.atEnd) {
          // That's good, it didn't parse all of it
        } else {
          fail()
        }
      }
    }
  }

  test("Ignoring name") {
    compile("_ Int") shouldHave { case fromEnv("Int") => }
  }
//  test("Callable type") {
//    compile("_ fn(T)Void") shouldHave {
//      case withType(
//          FunctionPT(
//            None,
//            List(NameOrRunePT(StringP(_, "T"))),
//            NameOrRunePT(StringP(_, "Void")))) =>
//    }
//  }
  test("15a") {
    compile("_ [3 * MutableStruct]") shouldHave {
      case withType(
          RepeaterSequencePT(_,
              MutabilityPT(MutableP),
              IntPT(_,3),
              NameOrRunePT(StringP(_, "MutableStruct")))) =>
    }
  }

  test("15b") {
    compile("_ [<imm> 3 * MutableStruct]") shouldHave {
      case withType(
        RepeaterSequencePT(_,
          MutabilityPT(ImmutableP),
          IntPT(_,3),
          NameOrRunePT(StringP(_, "MutableStruct")))) =>
    }
  }

  test("Sequence type") {
    compile("_ [Int, Bool]") shouldHave {
      case withType(
          ManualSequencePT(_,
            List(
              NameOrRunePT(StringP(_, "Int")),
              NameOrRunePT(StringP(_, "Bool"))))) =>
    }
  }
  test("15") {
    compile("_ &[3 * MutableStruct]") shouldHave {
      case PatternPP(_,_,
        None,
        Some(
          OwnershippedPT(_,
            BorrowP,
            RepeaterSequencePT(_,
              MutabilityPT(MutableP),
              IntPT(_,3),
              NameOrRunePT(StringP(_, "MutableStruct"))))),
        None,
        None) =>
    }
  }
  test("15m") {
    compile("_ &[<_> 3 * MutableStruct]") shouldHave {
      case PatternPP(_,_,
        None,
        Some(
          OwnershippedPT(_,
            BorrowP,
            RepeaterSequencePT(_,
              AnonymousRunePT(),
              IntPT(_,3),
              NameOrRunePT(StringP(_, "MutableStruct"))))),
        None,
        None) =>
    }
  }
  test("15z") {
    compile("_ MyOption<MyList<Int>>") shouldHave {
      case PatternPP(_,_,
        None,
        Some(
          CallPT(
            _,
            NameOrRunePT(StringP(_, "MyOption")),
            List(
              CallPT(_,
                NameOrRunePT(StringP(_, "MyList")),
                List(
                  NameOrRunePT(StringP(_, "Int"))))))),
        None,
        None) =>
    }
  }
}
