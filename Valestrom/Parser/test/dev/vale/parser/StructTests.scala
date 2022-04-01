package dev.vale.parser

import dev.vale.Collector
import dev.vale.options.GlobalOptions
import dev.vale.parser.ast.{BorrowP, CallPT, ExportAttributeP, FinalP, IdentifyingRuneP, IdentifyingRunesP, ImmutableP, IntTypePR, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, NormalStructMemberP, OwnP, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, StructMembersP, StructP, TemplateRulesP, TopLevelStructP, TypedPR, VariabilityPT, VariadicStructMemberP, VaryingP, WeakP}
import dev.vale.parser.ast._
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
      case NormalStructMemberP(_, NameP(_, "a"), FinalP, InterpretedPT(_,ShareP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")), Vector(NameOrRunePT(NameP(_, "T")))))) =>
    }
  }

  test("18") {
    compile(makeParser().parseStructMember(_), "a []<imm>T;") shouldHave {
      case NormalStructMemberP(_,NameP(_,"a"),FinalP,RuntimeSizedArrayPT(_,MutabilityPT(_,ImmutableP),NameOrRunePT(NameP(_,"T")))) =>
    }
  }

  test("Simple struct") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      "struct Moo { x &int; }") shouldHave {
      case TopLevelStructP(StructP(_,
        NameP(_, "Moo"),
        Vector(),
        MutabilityPT(_, MutableP),
        None,
        None,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,BorrowP,NameOrRunePT(NameP(_, "int")))))))) =>
    }
  }

  test("Variadic struct") {
    val thing = compileMaybe(
      makeParser().parseTopLevelThing(_),
      "struct Moo<T> { _ ..T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, FinalP, NameOrRunePT(NameP(_, "T"))))) =>
    })
  }

  test("Variadic struct with varying") {
    val thing = compileMaybe(
      makeParser().parseTopLevelThing(_),
      "struct Moo<T> { _! ..T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, VaryingP, NameOrRunePT(NameP(_, "T"))))) =>
    })
  }

  test("Struct with weak") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      "struct Moo { x &&int; }") shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, "Moo"), Vector(), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,WeakP,NameOrRunePT(NameP(_, "int")))))))) =>
    }
  }

  test("Struct with heap") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      "struct Moo { x ^Marine; }") shouldHave {
      case TopLevelStructP(StructP(_,NameP(_,"Moo"),Vector(), MutabilityPT(_, MutableP),None,None,StructMembersP(_,Vector(NormalStructMemberP(_,NameP(_,"x"),FinalP,InterpretedPT(_,OwnP,NameOrRunePT(NameP(_,"Marine")))))))) =>
    }
  }

  test("Export struct") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      "exported struct Moo { x &int; }") shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, "Moo"), Vector(ExportAttributeP(_)), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,BorrowP,NameOrRunePT(NameP(_, "int")))))))) =>
    }
  }

  test("Struct with rune") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      """
        |struct ListNode<E> {
        |  value E;
        |  next ListNode<E>;
        |}
      """.stripMargin.strip()) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, "ListNode"),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "E"), Vector())))),
        None,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_,NameP(_, "value"),FinalP,NameOrRunePT(NameP(_, "E"))),
            NormalStructMemberP(_,NameP(_, "next"),FinalP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")),Vector(NameOrRunePT(NameP(_, "E"))))))))) =>
    }
  }

  test("Struct with int rune") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
      """
        |struct Vecf<N> where N int
        |{
        |  values [#N]float;
        |}
        |
      """.stripMargin.strip()) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, "Vecf"),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "N"), Vector())))),
        Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, "N")), IntTypePR)))),
        StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, "values"), FinalP, StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float")))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    compileMaybe(
      makeParser().parseTopLevelThing(_),
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
            NameP(_, "Vecf"),
            Vector(),
            MutabilityPT(_, MutableP),
            Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "N"), Vector())))),
            Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, "N")),IntTypePR)))),
            StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, "values"),FinalP,StaticSizedArrayPT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_, FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float")))))))) =>
    }
  }
}
