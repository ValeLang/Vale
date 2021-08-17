package net.verdagon.vale.parser.rules

import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class CoordRuleTests extends FunSuite with Matchers with Collector {
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

  test("Empty Coord rule") {
    compile(rulePR, "_ Ref") shouldHave {
      case TypedPR(_,None,CoordTypePR) =>
    }
  }

  test("Coord with rune") {
    compile(rulePR, "T Ref") shouldHave {
      case TypedPR(_,Some(NameP(_, "T")),CoordTypePR) =>
    }
  }

  test("Coord with destructure only") {
    compile(rulePR, "Ref(_, _)") shouldHave {
      case ComponentsPR(_,TypedPR(_,None,CoordTypePR),Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))) =>
    }
  }

  test("Coord with rune and destructure") {
    compile(rulePR, "T Ref(_, _)") shouldHave {
      case ComponentsPR(_,TypedPR(_,Some(NameP(_, "T")),CoordTypePR),Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))) =>
    }
    compile(rulePR, "T Ref(own, _)") shouldHave {
        case ComponentsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(AnonymousRunePT(_)))) =>
    }
  }

  test("Coord matches plain Int") {
    // Coord can do this because I want to be able to say:
    //   fn moo
    //   rules(#T = (Int):Void)
    //   (a: #T)
    // instead of:
    //   fn moo
    //   rules(
    //     Ref#T[_, _, Ref[_, _, Int]]:Ref[_, _, Void]))
    //   (a: #T)
    compile(rulePR, "int") shouldHave {
      case TemplexPR(NameOrRunePT(NameP(_, "int"))) =>
    }
//        CoordPR(None,None,None,None,None,Some(Vector(NameTemplexPR("int"))))

  }

  test("Coord with Int in kind rule") {
    compile(rulePR, "T Ref(_, int)") shouldHave {
      case ComponentsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(NameP(_, "int"))))) =>
    }
//      runedTCoordWithEnvKind("T", "int")

  }

  test("Coord with specific Kind rule") {
    compile(rulePR, "T Ref(_, Kind(mut))") shouldHave {
      case ComponentsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          Vector(
            TemplexPR(AnonymousRunePT(_)),
            ComponentsPR(_,
              TypedPR(_,None,KindTypePR),Vector(TemplexPR(MutabilityPT(_,MutableP)))))) =>
    }
  }

  test("Coord with value") {
    compile(rulePR, "T Ref = int") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
  }

  test("Coord with destructure and value") {
    compile(rulePR, "T Ref(_, _) = int") shouldHave {
      case EqualsPR(_,
          ComponentsPR(_,TypedPR(_,Some(NameP(_, "T")),CoordTypePR),Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))),
          TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
//        runedTCoordWithValue("T", NameTemplexPR("int"))
  }

  test("Coord with sequence in value spot") {
    compile(rulePR, "T Ref = [int, bool]") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          TemplexPR(
            ManualSequencePT(_,
              Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))))) =>
    }
  }

  test("Braces without Ref is sequence") {
    compile(rulePR, "[int, bool]") shouldHave {
      case TemplexPR(
          ManualSequencePT(_,
            Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool"))))) =>
        }
  }
}
