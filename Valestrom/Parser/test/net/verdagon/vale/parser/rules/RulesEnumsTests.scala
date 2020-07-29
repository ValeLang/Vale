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
    compile(rulePR, "X Ownership") shouldHave { case TypedPR(Some(StringP(_, "X")),OwnershipTypePR) => }
    compile(rulePR, "X = own") shouldHave { case EqualsPR(TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(OwnershipPRT(OwnP))) => }
    compile(rulePR, "X Ownership = own|borrow|weak") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "X")),OwnershipTypePR),
          OrPR(List(TemplexPR(OwnershipPRT(OwnP)), TemplexPR(OwnershipPRT(BorrowP)), TemplexPR(OwnershipPRT(WeakP))))) =>
    }
    compile(rulePR, "_ Ownership") shouldHave { case TypedPR(None,OwnershipTypePR) => }
    compile(rulePR, "own") shouldHave { case TemplexPR(OwnershipPRT(OwnP)) => }
    compile(rulePR, "_ Ownership = own|share") shouldHave {
      case EqualsPR(
          TypedPR(None,OwnershipTypePR),
          OrPR(List(TemplexPR(OwnershipPRT(OwnP)), TemplexPR(OwnershipPRT(ShareP))))) =>
    }
  }

  test("Mutability") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Mutability") shouldHave { case TypedPR(Some(StringP(_, "X")),MutabilityTypePR) => }
    compile(rulePR, "X = mut") shouldHave { case EqualsPR(TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(MutabilityPRT(MutableP))) => }
    compile(rulePR, "X Mutability = mut") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "X")),MutabilityTypePR),
          TemplexPR(MutabilityPRT(MutableP))) =>
    }
    compile(rulePR, "_ Mutability") shouldHave { case TypedPR(None,MutabilityTypePR) => }
    compile(rulePR, "mut") shouldHave { case TemplexPR(MutabilityPRT(MutableP)) => }
    compile(rulePR, "_ Mutability = mut|imm") shouldHave {
      case EqualsPR(
          TypedPR(None,MutabilityTypePR),
          OrPR(List(TemplexPR(MutabilityPRT(MutableP)), TemplexPR(MutabilityPRT(ImmutableP))))) =>
    }
  }

  test("Location") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Location") shouldHave { case TypedPR(Some(StringP(_, "X")),LocationTypePR) => }
    compile(rulePR, "X = inl") shouldHave { case EqualsPR(TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(LocationPRT(InlineP))) => }
    compile(rulePR, "X Location = inl") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "X")),LocationTypePR),
          TemplexPR(LocationPRT(InlineP))) =>
    }
    compile(rulePR, "_ Location") shouldHave { case TypedPR(None,LocationTypePR) => }
    compile(rulePR, "inl") shouldHave { case TemplexPR(LocationPRT(InlineP)) => }
    compile(rulePR, "_ Location = inl|yon") shouldHave {
      case EqualsPR(
          TypedPR(None,LocationTypePR),
          OrPR(List(TemplexPR(LocationPRT(InlineP)), TemplexPR(LocationPRT(YonderP))))) =>
    }
  }

  test("Permission") {
    compile(rulePR, "X") shouldHave { case TemplexPR(NameOrRunePRT(StringP(_, "X"))) => }
    compile(rulePR, "X Permission") shouldHave { case TypedPR(Some(StringP(_, "X")),PermissionTypePR) => }
    compile(rulePR, "X = rw") shouldHave { case EqualsPR(TemplexPR(NameOrRunePRT(StringP(_, "X"))),TemplexPR(PermissionPRT(ReadwriteP))) => }
    compile(rulePR, "X Permission = rw") shouldHave {
      case EqualsPR(
          TypedPR(Some(StringP(_, "X")),PermissionTypePR),
          TemplexPR(PermissionPRT(ReadwriteP))) =>
    }
    compile(rulePR, "_ Permission") shouldHave { case TypedPR(None,PermissionTypePR) => }
    compile(rulePR, "rw") shouldHave { case TemplexPR(PermissionPRT(ReadwriteP)) => }
    compile(rulePR, "_ Permission = xrw|rw|ro") shouldHave {
      case EqualsPR(
          TypedPR(None,PermissionTypePR),
          OrPR(
            List(
              TemplexPR(PermissionPRT(ExclusiveReadwriteP)),
              TemplexPR(PermissionPRT(ReadwriteP)),
              TemplexPR(PermissionPRT(ReadonlyP))))) =>
    }
  }

}
