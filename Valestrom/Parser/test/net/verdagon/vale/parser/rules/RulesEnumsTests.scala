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
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Ownership") shouldHave { case TypedPR(_,Some(StringP(_, "X")),OwnershipTypePR) => }
    compile(rulePR, "X = own") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(OwnershipPRT(_,OwnP))) => }
    compile(rulePR, "X Ownership = own|borrow|weak") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),OwnershipTypePR),
          OrPR(_,List(TemplexPR(OwnershipPRT(_,OwnP)), TemplexPR(OwnershipPRT(_,BorrowP)), TemplexPR(OwnershipPRT(_,WeakP))))) =>
    }
    compile(rulePR, "_ Ownership") shouldHave { case TypedPR(_,None,OwnershipTypePR) => }
    compile(rulePR, "own") shouldHave { case TemplexPR(OwnershipPRT(_,OwnP)) => }
    compile(rulePR, "_ Ownership = own|share") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,OwnershipTypePR),
          OrPR(_,List(TemplexPR(OwnershipPRT(_,OwnP)), TemplexPR(OwnershipPRT(_,ShareP))))) =>
    }
  }

  test("Mutability") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Mutability") shouldHave { case TypedPR(_,Some(StringP(_, "X")),MutabilityTypePR) => }
    compile(rulePR, "X = mut") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(MutabilityPRT(_,MutableP))) => }
    compile(rulePR, "X Mutability = mut") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),MutabilityTypePR),
          TemplexPR(MutabilityPRT(_,MutableP))) =>
    }
    compile(rulePR, "_ Mutability") shouldHave { case TypedPR(_,None,MutabilityTypePR) => }
    compile(rulePR, "mut") shouldHave { case TemplexPR(MutabilityPRT(_,MutableP)) => }
    compile(rulePR, "_ Mutability = mut|imm") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,MutabilityTypePR),
          OrPR(_,List(TemplexPR(MutabilityPRT(_,MutableP)), TemplexPR(MutabilityPRT(_,ImmutableP))))) =>
    }
  }

  test("Location") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Location") shouldHave { case TypedPR(_,Some(StringP(_, "X")),LocationTypePR) => }
    compile(rulePR, "X = inl") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(LocationPRT(_,InlineP))) => }
    compile(rulePR, "X Location = inl") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),LocationTypePR),
          TemplexPR(LocationPRT(_,InlineP))) =>
    }
    compile(rulePR, "_ Location") shouldHave { case TypedPR(_,None,LocationTypePR) => }
    compile(rulePR, "inl") shouldHave { case TemplexPR(LocationPRT(_,InlineP)) => }
    compile(rulePR, "_ Location = inl|yon") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,LocationTypePR),
          OrPR(_,List(TemplexPR(LocationPRT(_,InlineP)), TemplexPR(LocationPRT(_,YonderP))))) =>
    }
  }

  test("Permission") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Permission") shouldHave { case TypedPR(_,Some(StringP(_, "X")),PermissionTypePR) => }
    compile(rulePR, "X = rw") shouldHave { case EqualsPR(_,TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(PermissionPRT(_,ReadwriteP))) => }
    compile(rulePR, "X Permission = rw") shouldHave {
      case EqualsPR(_,
          TypedPR(_,Some(StringP(_, "X")),PermissionTypePR),
          TemplexPR(PermissionPRT(_,ReadwriteP))) =>
    }
    compile(rulePR, "_ Permission") shouldHave { case TypedPR(_,None,PermissionTypePR) => }
    compile(rulePR, "rw") shouldHave { case TemplexPR(PermissionPRT(_,ReadwriteP)) => }
    compile(rulePR, "_ Permission = xrw|rw|ro") shouldHave {
      case EqualsPR(_,
          TypedPR(_,None,PermissionTypePR),
          OrPR(_,
            List(
              TemplexPR(PermissionPRT(_,ExclusiveReadwriteP)),
              TemplexPR(PermissionPRT(_,ReadwriteP)),
              TemplexPR(PermissionPRT(_,ReadonlyP))))) =>
    }
  }

}
