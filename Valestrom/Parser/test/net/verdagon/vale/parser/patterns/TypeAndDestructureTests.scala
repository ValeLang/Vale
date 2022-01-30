package net.verdagon.vale.parser.patterns

import net.verdagon.vale.parser.ast.Patterns._
import net.verdagon.vale.parser.old.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{CallPT, DestructureP, LocalNameDeclarationP, ManualSequencePT, NameOrRunePT, NameP, PatternPP}
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class TypeAndDestructureTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    CombinatorParsers.parse(parser, code.toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail();
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
    CombinatorParsers.parse(parser, code) match {
      case CombinatorParsers.NoSuccess(_, _) =>
      case CombinatorParsers.Success(_, rest) => {
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
          Some(NameOrRunePT(NameP(_, "Muta"))),
          Some(DestructureP(_,Vector())),
          None) =>
    }
  }

  test("Templated destructure") {
    compile("_ Muta<int>()") shouldHave {
      case PatternPP(_,_,
          None,
          Some(
            CallPT(_,
              NameOrRunePT(NameP(_, "Muta")),
              Vector(NameOrRunePT(NameP(_, "int"))))),
          Some(DestructureP(_,Vector())),
          None) =>
    }
    compile("_ Muta<R>()") shouldHave {
        case PatternPP(_,_,
          None,
          Some(
            CallPT(_,
              NameOrRunePT(NameP(_, "Muta")),
              Vector(NameOrRunePT(NameP(_, "R"))))),
          Some(DestructureP(_,Vector())),
          None) =>
    }
  }


  test("Destructure with type outside") {
    compile("_ [int, bool](a, b)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(
            ManualSequencePT(_,
                Vector(
                  NameOrRunePT(NameP(_, "int")),
                  NameOrRunePT(NameP(_, "bool"))))),
          Some(DestructureP(_,Vector(capture("a"), capture("b")))),
          None) =>
    }
  }
  test("Destructure with typeless capture") {
    compile("_ Muta(b)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(NameP(_, "Muta"))),
          Some(DestructureP(_,Vector(PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "b"))),None,None,None)))),
          None) =>
    }
  }
  test("Destructure with typed capture") {
    compile("_ Muta(b Marine)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(NameP(_, "Muta"))),
          Some(DestructureP(_,Vector(PatternPP(_,_,Some(LocalNameDeclarationP(NameP(_, "b"))),Some(NameOrRunePT(NameP(_, "Marine"))),None,None)))),
          None) =>
    }
  }
  test("Destructure with unnamed capture") {
    compile("_ Muta(_ Marine)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(NameP(_, "Muta"))),
          Some(DestructureP(_,Vector(PatternPP(_,_,None,Some(NameOrRunePT(NameP(_, "Marine"))),None,None)))),
          None) =>
    }
  }
  test("Destructure with runed capture") {
    compile("_ Muta(_ R)") shouldHave {
      case PatternPP(_,_,
          None,
          Some(NameOrRunePT(NameP(_, "Muta"))),
          Some(DestructureP(_,Vector(PatternPP(_,_,None,Some(NameOrRunePT(NameP(_, "R"))),None,None)))),
          None) =>
        }
  }
}
