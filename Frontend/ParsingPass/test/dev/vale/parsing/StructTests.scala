package dev.vale.parsing

import dev.vale.{Collector, StrI}
import dev.vale.parsing.ast.{BorrowP, CallPT, ExportAttributeP, FinalP, IdentifyingRuneP, IdentifyingRunesP, ImmutableP, IntTypePR, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, NormalStructMemberP, OwnP, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, StructMembersP, StructP, TemplateRulesP, TopLevelStructP, TypedPR, VariabilityPT, VariadicStructMemberP, VaryingP, WeakP}
import dev.vale.parsing.ast._
import dev.vale.Collector
import org.scalatest.{FunSuite, Matchers}


class StructTests extends FunSuite with Collector with TestParseUtils {
//  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
//    // The strip is in here because things inside the parser don't expect whitespace before and after
//    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
//      case CombinatorParsers.NoSuccess(msg, input) => {
//        fail("Couldn't parse!\n" + input.pos.longString);
//      }
//      case CombinatorParsers.Success(expr, rest) => {
//        vassert(rest.atEnd)
//        expr
//      }
//    }
//  }

  test("17") {
    compile(makeParser().parseStructMember(_), "a @ListNode<T>;") shouldHave {
      case NormalStructMemberP(_, NameP(_, StrI("a")), FinalP, InterpretedPT(_,ShareP,CallPT(_,NameOrRunePT(NameP(_, StrI("ListNode"))), Vector(NameOrRunePT(NameP(_, StrI("T"))))))) =>
    }
  }

  test("18") {
    compile(makeParser().parseStructMember(_), "a []<imm>T;") shouldHave {
      case NormalStructMemberP(_,NameP(_, StrI("a")),FinalP,RuntimeSizedArrayPT(_,MutabilityPT(_,ImmutableP),NameOrRunePT(NameP(_, StrI("T"))))) =>
    }
  }

  test("Simple struct") {
    compileMaybe(
      makeParser().parseDenizen(_),
      "struct Moo { x &int; }") shouldHave {
      case TopLevelStructP(StructP(_,
        NameP(_, StrI("Moo")),
        Vector(),
        MutabilityPT(_, MutableP),
        None,
        None,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_, NameP(_, StrI("x")), FinalP, InterpretedPT(_,BorrowP,NameOrRunePT(NameP(_, StrI("int"))))))))) =>
    }
  }

  test("Variadic struct") {
    val thing = compileMaybe(
      makeParser().parseDenizen(_),
      "struct Moo<T> { _ ..T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, FinalP, NameOrRunePT(NameP(_, StrI("T")))))) =>
    })
  }

  test("Variadic struct with varying") {
    val thing = compileMaybe(
      makeParser().parseDenizen(_),
      "struct Moo<T> { _! ..T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, VaryingP, NameOrRunePT(NameP(_, StrI("T")))))) =>
    })
  }

  test("Struct with weak") {
    compileMaybe(
      makeParser().parseDenizen(_),
      "struct Moo { x &&int; }") shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, StrI("Moo")), Vector(), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, StrI("x")), FinalP, InterpretedPT(_,WeakP,NameOrRunePT(NameP(_, StrI("int"))))))))) =>
    }
  }

  test("Struct with heap") {
    compileMaybe(
      makeParser().parseDenizen(_),
      "struct Moo { x ^Marine; }") shouldHave {
      case TopLevelStructP(StructP(_,NameP(_, StrI("Moo")),Vector(), MutabilityPT(_, MutableP),None,None,StructMembersP(_,Vector(NormalStructMemberP(_,NameP(_, StrI("x")),FinalP,InterpretedPT(_,OwnP,NameOrRunePT(NameP(_, StrI("Marine"))))))))) =>
    }
  }

  test("Export struct") {
    compileMaybe(
      makeParser().parseDenizen(_),
      "exported struct Moo { x &int; }") shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, StrI("Moo")), Vector(ExportAttributeP(_)), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, StrI("x")), FinalP, InterpretedPT(_,BorrowP,NameOrRunePT(NameP(_, StrI("int"))))))))) =>
    }
  }

  test("Struct with rune") {
    compileMaybe(
      makeParser().parseDenizen(_),
      """
        |struct ListNode<E> {
        |  value E;
        |  next ListNode<E>;
        |}
      """.stripMargin.strip()) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, StrI("ListNode")),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, StrI("E")), Vector())))),
        None,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_,NameP(_, StrI("value")),FinalP,NameOrRunePT(NameP(_, StrI("E")))),
            NormalStructMemberP(_,NameP(_, StrI("next")),FinalP,CallPT(_,NameOrRunePT(NameP(_, StrI("ListNode"))),Vector(NameOrRunePT(NameP(_, StrI("E")))))))))) =>
    }
  }

  test("Struct with int rune") {
    compileMaybe(
      makeParser().parseDenizen(_),
      """
        |struct Vecf<N> where N int
        |{
        |  values [#N]float;
        |}
        |
      """.stripMargin.strip()) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, StrI("Vecf")),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, StrI("N")), Vector())))),
        Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, StrI("N"))), IntTypePR)))),
        StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, StrI("values")), FinalP, StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, StrI("N"))), NameOrRunePT(NameP(_, StrI("float"))))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    compileMaybe(
      makeParser().parseDenizen(_),
      """
        |struct Vecf<N> where N int
        |{
        |  values [#N]<imm>float;
        |}
        |
      """.stripMargin.strip()) shouldHave {
      case TopLevelStructP(
          StructP(
            _,
            NameP(_, StrI("Vecf")),
            Vector(),
            MutabilityPT(_, MutableP),
            Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, StrI("N")), Vector())))),
            Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, StrI("N"))),IntTypePR)))),
            StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, StrI("values")),FinalP,StaticSizedArrayPT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_, FinalP), NameOrRunePT(NameP(_, StrI("N"))), NameOrRunePT(NameP(_, StrI("float"))))))))) =>
    }
  }
}
