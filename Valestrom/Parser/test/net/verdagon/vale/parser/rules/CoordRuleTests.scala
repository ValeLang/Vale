package net.verdagon.vale.parser.rules


import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{AnonymousRunePT, ComponentsPR, CoordTypePR, EqualsPR, IRulexPR, KindTypePR, MutabilityPT, MutableP, NameOrRunePT, NameP, OwnP, OwnershipPT, PatternPP, TemplexPR, TuplePT, TypedPR}

import net.verdagon.vale.parser.templex.TemplexParser
import net.verdagon.vale.{Collector, vfail}
import org.scalatest.{FunSuite, Matchers}

class CoordRuleTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
  }

  test("Empty Coord rule") {
    compile("_ Ref") shouldHave {
      case TypedPR(_,None,CoordTypePR) =>
    }
  }

  test("Coord with rune") {
    compile("T Ref") shouldHave {
      case TypedPR(_,Some(NameP(_, "T")),CoordTypePR) =>
    }
  }

  test("Coord with destructure only") {
    compile("Ref[_, _]") shouldHave {
      case ComponentsPR(_,CoordTypePR,Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))) =>
    }
  }

  test("Coord with rune and destructure") {
    compile("T = Ref[_, _]") shouldHave {
      case ComponentsPR(_,CoordTypePR,Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))) =>
    }
    compile("T = Ref[own, _]") shouldHave {
        case ComponentsPR(_,
          CoordTypePR,
          Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(AnonymousRunePT(_)))) =>
    }
  }

  test("Coord matches plain Int") {
    // Coord can do this because I want to be able to say:
    //   func moo
    //   where #T = (Int):Void
    //   (a: #T)
    // instead of:
    //   func moo
    //   rules(
    //     Ref#T[_, Ref[_, Int]]:Ref[_, Void]))
    //   (a: #T)
    compile("int") shouldHave {
      case TemplexPR(NameOrRunePT(NameP(_, "int"))) =>
    }
//        CoordPR(None,None,None,None,None,Some(Vector(NameTemplexPR("int"))))

  }

  test("Coord with Int in kind rule") {
    compile("Ref[_, int]") shouldHave {
      case ComponentsPR(_,
          CoordTypePR,
          Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(NameOrRunePT(NameP(_, "int"))))) =>
    }
//      runedTCoordWithEnvKind("T", "int")

  }

  test("Coord with specific Kind rule") {
    compile("Ref[_, Kind[mut]]") shouldHave {
      case ComponentsPR(_,
          CoordTypePR,
          Vector(
            TemplexPR(AnonymousRunePT(_)),
            ComponentsPR(_,
              KindTypePR,Vector(TemplexPR(MutabilityPT(_,MutableP)))))) =>
    }
  }

  test("Coord with value") {
    compile("T Ref = int") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
  }

  test("Coord with destructure and value") {
    compile("T = Ref[_, _] = int") shouldHave {
      case EqualsPR(_,
          ComponentsPR(_,CoordTypePR,Vector(TemplexPR(AnonymousRunePT(_)), TemplexPR(AnonymousRunePT(_)))),
          TemplexPR(NameOrRunePT(NameP(_, "int")))) =>
    }
//        runedTCoordWithValue("T", NameTemplexPR("int"))
  }

  test("Coord with sequence in value spot") {
    compile("T Ref = (int, bool)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "T")),CoordTypePR),
          TemplexPR(
            TuplePT(_,
              Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool")))))) =>
    }
  }

  test("Lone tuple is sequence") {
    compile("(int, bool)") shouldHave {
      case TemplexPR(
          TuplePT(_,
            Vector(NameOrRunePT(NameP(_, "int")), NameOrRunePT(NameP(_, "bool"))))) =>
        }
  }
}
