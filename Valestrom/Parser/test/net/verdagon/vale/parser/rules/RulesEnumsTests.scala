package net.verdagon.vale.parser.rules

import net.verdagon.vale.{Collector, parser, vfail}
import net.verdagon.vale.parser.old.CombinatorParsers._
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{BorrowP, BuiltinCallPR, EqualsPR, ExclusiveReadwriteP, IRulexPR, ImmutableP, InlineP, LocationPT, LocationTypePR, MutabilityPT, MutabilityTypePR, MutableP, NameOrRunePT, NameP, OrPR, OwnP, OwnershipPT, OwnershipTypePR, PatternPP, PermissionPT, PermissionTypePR, ReadonlyP, ReadwriteP, ShareP, TemplexPR, TypedPR, WeakP, YonderP}
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.parser.templex.TemplexParser
import org.scalatest.{FunSuite, Matchers}

class RulesEnumsTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
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

  test("Ownership") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, "X"))) => }
    compile("X Ownership") shouldHave { case TypedPR(_,Some(NameP(_, "X")),OwnershipTypePR) => }
    compile("X = own") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, "X"))),TemplexPR(OwnershipPT(_,OwnP))) => }
    compile("X Ownership = any(own, borrow, weak)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "X")),OwnershipTypePR),
          BuiltinCallPR(_,NameP(_,"any"),Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,BorrowP)), TemplexPR(OwnershipPT(_,WeakP))))) =>
    }
    compile("_ Ownership") shouldHave { case TypedPR(_,None,OwnershipTypePR) => }
    compile("own") shouldHave { case TemplexPR(OwnershipPT(_,OwnP)) => }
    compile("_ Ownership = any(own, share)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,OwnershipTypePR),
          BuiltinCallPR(_,NameP(_,"any"),Vector(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,ShareP))))) =>
    }
  }

  test("Mutability") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, "X"))) => }
    compile("X Mutability") shouldHave { case TypedPR(_,Some(NameP(_, "X")),MutabilityTypePR) => }
    compile("X = mut") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, "X"))),TemplexPR(MutabilityPT(_,MutableP))) => }
    compile("X Mutability = mut") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "X")),MutabilityTypePR),
          TemplexPR(MutabilityPT(_,MutableP))) =>
    }
    compile("_ Mutability") shouldHave { case TypedPR(_,None,MutabilityTypePR) => }
    compile("mut") shouldHave { case TemplexPR(MutabilityPT(_,MutableP)) => }
    compile("_ Mutability = any(mut, imm)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,MutabilityTypePR),
          BuiltinCallPR(_,NameP(_,"any"),Vector(TemplexPR(MutabilityPT(_,MutableP)), TemplexPR(MutabilityPT(_,ImmutableP))))) =>
    }
  }

  test("Location") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, "X"))) => }
    compile("X Location") shouldHave { case TypedPR(_,Some(NameP(_, "X")),LocationTypePR) => }
    compile("X = inl") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, "X"))),TemplexPR(LocationPT(_,InlineP))) => }
    compile("X Location = inl") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "X")),LocationTypePR),
          TemplexPR(LocationPT(_,InlineP))) =>
    }
    compile("_ Location") shouldHave { case TypedPR(_,None,LocationTypePR) => }
    compile("inl") shouldHave { case TemplexPR(LocationPT(_,InlineP)) => }
    compile("_ Location = any(inl, heap)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,LocationTypePR),
          BuiltinCallPR(_,NameP(_,"any"),Vector(TemplexPR(LocationPT(_,InlineP)), TemplexPR(LocationPT(_,YonderP))))) =>
    }
  }

  test("Permission") {
    compile("X") shouldHave { case TemplexPR(NameOrRunePT(NameP(_, "X"))) => }
    compile("X Permission") shouldHave { case TypedPR(_,Some(NameP(_, "X")),PermissionTypePR) => }
    compile("X = rw") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(NameP(_, "X"))),TemplexPR(PermissionPT(_,ReadwriteP))) => }
    compile("X Permission = rw") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(NameP(_, "X")),PermissionTypePR),
          TemplexPR(PermissionPT(_,ReadwriteP))) =>
    }
    compile("_ Permission") shouldHave { case TypedPR(_,None,PermissionTypePR) => }
    compile("rw") shouldHave { case TemplexPR(PermissionPT(_,ReadwriteP)) => }
    compile("_ Permission = any(xrw, rw, ro)") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,PermissionTypePR),
          BuiltinCallPR(_,NameP(_,"any"),
            Vector(
              TemplexPR(PermissionPT(_,ExclusiveReadwriteP)),
              TemplexPR(PermissionPT(_,ReadwriteP)),
              TemplexPR(PermissionPT(_,ReadonlyP))))) =>
    }
  }

}
