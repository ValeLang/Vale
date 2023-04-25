package dev.vale.parsing.patterns

import dev.vale.{Collector, Err, StrI, vimpl}
import dev.vale.parsing.{PatternParser, TestParseUtils}
import dev.vale.parsing.ast.{AbstractP, DestructureP, IgnoredLocalNameDeclarationP, LocalNameDeclarationP, NameOrRunePT, NameP, PatternPP, Patterns, TuplePT}
import dev.vale.parsing.ast.Patterns._
import dev.vale.parsing._
import dev.vale.parsing.ast._
import org.scalatest.{FunSuite, Matchers}

class PatternParserTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): PatternPP = {
    compilePattern(code)
//    compile(new PatternParser().parsePattern(_), code)
  }

  private def checkFail[T](code: String) = {
    vimpl()
//    compileForError(new PatternParser().parsePattern(_), code)
  }

  private def checkRest[T](code: String, expectedRest: String) = {
    vimpl()
//    compileForRest(new PatternParser().parsePattern(_), code, expectedRest)
  }

  test("Simple Int") {
    // Make sure every pattern on the way down to kind can match Int
//    compile(Parser.parseTypeName(_),"int") shouldHave { case "int" => }
//    compile(runeOrKindPattern,"int") shouldHave { case NameOrRunePT(NameP(_, StrI("int"))) => }
//    compile(patternType,"int") shouldHave { case PatternTypePPI(None, NameOrRunePT(NameP(_, StrI("int")))) => }
    compile("_ int") shouldHave { case Patterns.fromEnv("int") => }
  }
  test("Name-only Capture") {
    compile("a") match {
      case PatternPP(_, _,Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)), None, None, None) =>
    }
  }
  test("Empty pattern") {
    compile("_") match { case PatternPP(_,_, Some(DestinationLocalP(IgnoredLocalNameDeclarationP(_), None)),None,None,None) => }
  }

  test("Capture with type with destructure") {
    compile("a Moo[a, b]") shouldHave {
      case PatternPP(
          _,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
          Some(NameOrRunePT(NameP(_, StrI("Moo")))),
          Some(DestructureP(_,Vector(capture("a"),capture("b")))),
          None) =>
    }
  }


  test("CSTODTS") {
    // This tests us handling an ambiguity properly, see CSTODTS in docs.
    compile("moo T[a int]") shouldHave {
      case PatternPP(
          _,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("moo"))), None)),
          Some(NameOrRunePT(NameP(_, StrI("T")))),
          Some(DestructureP(_,Vector(PatternPP(_,_, Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),Some(NameOrRunePT(NameP(_, StrI("int")))),None,None)))),
          None) =>
    }
  }

  test("Capture with destructure with type outside") {
    compile("a (int, bool)[a, b]") shouldHave {
      case PatternPP(
          _,_,
          Some(DestinationLocalP(LocalNameDeclarationP(NameP(_, StrI("a"))), None)),
          Some(
            TuplePT(_,
                  Vector(
                    NameOrRunePT(NameP(_, StrI("int"))),
                    NameOrRunePT(NameP(_, StrI("bool")))))),
          Some(DestructureP(_,Vector(capture("a"), capture("b")))),
          None) =>
    }
  }

}
