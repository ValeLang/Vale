package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.Patterns._
import net.verdagon.vale.parser.VParser._
import net.verdagon.vale.parser._
import net.verdagon.vale.vfail
import org.scalatest.{FunSuite, Matchers}

class TypeAndDestructureTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    VParser.parse(parser, code.toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail();
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




  test("Empty destructure") {
    compile("_ Muta()") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(StringP(_, "Muta"))),
          Some(DestructureP(_,List())),
          None) =>
    }
  }

  test("Templated destructure") {
    compile("_ Muta<Int>()") shouldHave {
      case PatternPP(_,_,
          None,
          Some(
            CallPT(_,
              NameOrRunePT(StringP(_, "Muta")),
              List(NameOrRunePT(StringP(_, "Int"))))),
          Some(DestructureP(_,List())),
          None) =>
    }
    compile("_ Muta<R>()") shouldHave {
        case PatternPP(_,_,
          None,
          Some(
            CallPT(_,
              NameOrRunePT(StringP(_, "Muta")),
              List(NameOrRunePT(StringP(_, "R"))))),
          Some(DestructureP(_,List())),
          None) =>
    }
  }


  test("Destructure with type outside") {
    compile("_ [Int, Bool](a, b)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(
            ManualSequencePT(_,
                List(
                  NameOrRunePT(StringP(_, "Int")),
                  NameOrRunePT(StringP(_, "Bool"))))),
          Some(DestructureP(_,List(capture("a"), capture("b")))),
          None) =>
    }
  }
  test("Destructure with typeless capture") {
    compile("_ Muta(b)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(StringP(_, "Muta"))),
          Some(DestructureP(_,List(PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "b")),FinalP)),None,None,None)))),
          None) =>
    }
  }
  test("Destructure with typed capture") {
    compile("_ Muta(b Marine)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(StringP(_, "Muta"))),
          Some(DestructureP(_,List(PatternPP(_,_,Some(CaptureP(_,LocalNameP(StringP(_, "b")),FinalP)),Some(NameOrRunePT(StringP(_, "Marine"))),None,None)))),
          None) =>
    }
  }
  test("Destructure with unnamed capture") {
    compile("_ Muta(_ Marine)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(StringP(_, "Muta"))),
          Some(DestructureP(_,List(PatternPP(_,_,None,Some(NameOrRunePT(StringP(_, "Marine"))),None,None)))),
          None) =>
    }
  }
  test("Destructure with runed capture") {
    compile("_ Muta(_ R)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(StringP(_, "Muta"))),
          Some(DestructureP(_,List(PatternPP(_,_,None,Some(NameOrRunePT(StringP(_, "R"))),None,None)))),
          None) =>
        }
  }
}
