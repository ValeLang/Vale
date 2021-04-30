package net.verdagon.vale.parser.rules

import net.verdagon.vale.{parser, vfail}
import net.verdagon.vale.parser.CombinatorParsers._
import net.verdagon.vale.parser._
import org.scalatest.{FunSuite, Matchers}

class RulesEnumsTests extends FunSuite with Matchers with Collector {
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

  test("Ownership") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePT(StringP(_, "X"))) => }
    compile(rulePR, "X Ownership") shouldHave { case TypedPR(_,Some(StringP(_, "X")),OwnershipTypePR) => }
    compile(rulePR, "X = own") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(StringP(_, "X"))),TemplexPR(OwnershipPT(_,OwnP))) => }
    compile(rulePR, "X Ownership = own|borrow|weak") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),OwnershipTypePR),
          OrPR(_,List(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,BorrowP)), TemplexPR(OwnershipPT(_,WeakP))))) =>
    }
    compile(rulePR, "_ Ownership") shouldHave { case TypedPR(_,None,OwnershipTypePR) => }
    compile(rulePR, "own") shouldHave { case TemplexPR(OwnershipPT(_,OwnP)) => }
    compile(rulePR, "_ Ownership = own|share") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,OwnershipTypePR),
          OrPR(_,List(TemplexPR(OwnershipPT(_,OwnP)), TemplexPR(OwnershipPT(_,ShareP))))) =>
    }
  }

  test("Mutability") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePT(StringP(_, "X"))) => }
    compile(rulePR, "X Mutability") shouldHave { case TypedPR(_,Some(StringP(_, "X")),MutabilityTypePR) => }
    compile(rulePR, "X = mut") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(StringP(_, "X"))),TemplexPR(MutabilityPT(_,MutableP))) => }
    compile(rulePR, "X Mutability = mut") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),MutabilityTypePR),
          TemplexPR(MutabilityPT(_,MutableP))) =>
    }
    compile(rulePR, "_ Mutability") shouldHave { case TypedPR(_,None,MutabilityTypePR) => }
    compile(rulePR, "mut") shouldHave { case TemplexPR(MutabilityPT(_,MutableP)) => }
    compile(rulePR, "_ Mutability = mut|imm") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,MutabilityTypePR),
          OrPR(_,List(TemplexPR(MutabilityPT(_,MutableP)), TemplexPR(MutabilityPT(_,ImmutableP))))) =>
    }
  }

  test("Location") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePT(StringP(_, "X"))) => }
    compile(rulePR, "X Location") shouldHave { case TypedPR(_,Some(StringP(_, "X")),LocationTypePR) => }
    compile(rulePR, "X = inl") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(StringP(_, "X"))),TemplexPR(LocationPT(_,InlineP))) => }
    compile(rulePR, "X Location = inl") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),LocationTypePR),
          TemplexPR(LocationPT(_,InlineP))) =>
    }
    compile(rulePR, "_ Location") shouldHave { case TypedPR(_,None,LocationTypePR) => }
    compile(rulePR, "inl") shouldHave { case TemplexPR(LocationPT(_,InlineP)) => }
    compile(rulePR, "_ Location = inl|yon") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,LocationTypePR),
          OrPR(_,List(TemplexPR(LocationPT(_,InlineP)), TemplexPR(LocationPT(_,YonderP))))) =>
    }
  }

  test("Permission") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePT(StringP(_, "X"))) => }
    compile(rulePR, "X Permission") shouldHave { case TypedPR(_,Some(StringP(_, "X")),PermissionTypePR) => }
    compile(rulePR, "X = rw") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePT(StringP(_, "X"))),TemplexPR(PermissionPT(_,ReadonlyP))) => }
    compile(rulePR, "X Permission = rw") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),PermissionTypePR),
          TemplexPR(PermissionPT(_,ReadonlyP))) =>
    }
    compile(rulePR, "_ Permission") shouldHave { case TypedPR(_,None,PermissionTypePR) => }
    compile(rulePR, "rw") shouldHave { case TemplexPR(PermissionPT(_,ReadonlyP)) => }
    compile(rulePR, "_ Permission = xrw|rw|ro") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,PermissionTypePR),
          OrPR(_,
            List(
              TemplexPR(PermissionPT(_,ExclusiveNormalP)),
              TemplexPR(PermissionPT(_,ReadonlyP)),
              TemplexPR(PermissionPT(_,ReadonlyP))))) =>
    }
  }

}
