package dev.vale.parser.rules

import dev.vale.Collector
import dev.vale.parser.TestParseUtils
import dev.vale.parser.ast.{BorrowP, BuiltinCallPR, EqualsPR, IRulexPR, ImmutableP, InlineP, LocationPT, LocationTypePR, MutabilityPT, MutabilityTypePR, MutableP, NameOrRunePT, NameP, OwnP, OwnershipPT, OwnershipTypePR, ShareP, TemplexPR, TypedPR, WeakP, YonderP}
import dev.vale.parser.templex.TemplexParser
import dev.vale.parser
import dev.vale.parser._
import dev.vale.parser.ast._
import org.scalatest.{FunSuite, Matchers}

class RulesEnumsTests extends FunSuite with Matchers with Collector with TestParseUtils {
  private def compile[T](code: String): IRulexPR = {
    compile(new TemplexParser().parseRule(_), code)
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
}
