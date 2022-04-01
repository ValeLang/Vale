package dev.vale.parser.patterns

import dev.vale.Collector
import dev.vale.parser.{PatternParser, TestParseUtils}
import dev.vale.parser.ast.{AbstractP, DestructureP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, Patterns, TuplePT}
import dev.vale.parser.ast.Patterns._
import dev.vale.parser._
import dev.vale.parser.ast._
import dev.vale.Err
import org.scalatest.{FunSuite, Matchers}

class PatternParserTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compile(new PatternParser().parsePattern(_), code)
  }

  private def checkFail[T](code: String) = {
    compileForError(new PatternParser().parsePattern(_), code)
  }

  private def checkRest[T](code: String, expectedRest: String) = {
    compileForRest(new PatternParser().parsePattern(_), code, expectedRest)
  }

  test("Simple Int") {
    // Make sure every pattern on the way down to kind can match Int
//    compile(Parser.parseTypeName(_),"int") shouldHave { case "int" => }
//    compile(runeOrKindPattern,"int") shouldHave { case NameOrRunePT(NameP(_, "int")) => }
//    compile(patternType,"int") shouldHave { case PatternTypePPI(None, NameOrRunePT(NameP(_, "int"))) => }
    compile("_ int") shouldHave { case Patterns.fromEnv("int") => }
  }
  test("Name-only Capture") {
    compile("a") match {
      case PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, "a"))), None, None, None) =>
    }
  }
  test("Simple pattern doesn't eat = after it") {
    compile( "a Int")
    checkRest("a Int=", "=")
    checkRest("a Int =", " =")
    checkRest("a Int = m", " = m")
    checkRest("a Int = m;", " = m;")
  }
  test("Empty pattern") {
    compile("_") match { case PatternPP(_,_, Some(IgnoredLocalNameDeclarationP(_)),None,None,None) => }
  }

  test("Capture with type with destructure") {
    compile("a Moo[a, b]") shouldHave {
      case PatternPP(
          _,_,
          Some(LocalNameDeclarationP(NameP(_, "a"))),
          Some(NameOrRunePT(NameP(_, "Moo"))),
          Some(DestructureP(_,Vector(capture("a"),capture("b")))),
          None) =>
    }
  }


  test("CSTODTS") {
    // This tests us handling an ambiguity properly, see CSTODTS in docs.
    compile("moo T[a int]") shouldHave {
      case PatternPP(
          _,_,
          Some(LocalNameDeclarationP(NameP(_, "moo"))),
          Some(NameOrRunePT(NameP(_, "T"))),
          Some(DestructureP(_,Vector(PatternPP(_,_, Some(LocalNameDeclarationP(NameP(_, "a"))),Some(NameOrRunePT(NameP(_, "int"))),None,None)))),
          None) =>
    }
  }

  test("Capture with destructure with type outside") {
    compile("a (int, bool)[a, b]") shouldHave {
      case PatternPP(
          _,_,
          Some(LocalNameDeclarationP(NameP(_, "a"))),
          Some(
            TuplePT(_,
                  Vector(
                    NameOrRunePT(NameP(_, "int")),
                    NameOrRunePT(NameP(_, "bool"))))),
          Some(DestructureP(_,Vector(capture("a"), capture("b")))),
          None) =>
    }
  }

  test("Virtual function") {
    compile("virtual this Car") shouldHave {
      case PatternPP(_, _,Some(LocalNameDeclarationP(NameP(_, "this"))),Some(NameOrRunePT(NameP(_, "Car"))),None,Some(AbstractP(_))) =>
    }
  }
}
