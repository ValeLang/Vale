package net.verdagon.vale.parser

import net.verdagon.vale.parser.ExpressionParser.StopBeforeCloseBrace
import net.verdagon.vale.parser.ast.{CallPT, ExportP, FinalP, IdentifyingRuneP, IdentifyingRunesP, ImmutableP, InlinePT, IntTypePR, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, NormalStructMemberP, PointerP, ReadonlyP, RepeaterSequencePT, ShareP, StructMembersP, StructP, TemplateRulesP, TypedPR, VariabilityPT, VariadicStructMemberP, VaryingP, WeakP}
import net.verdagon.vale.parser.old.CombinatorParsers
import net.verdagon.vale.{Collector, vassert}
import org.scalatest.{FunSuite, Matchers}


class StructTests extends FunSuite with Collector with TestParseUtils {
  private def compile[T](parser: CombinatorParsers.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    CombinatorParsers.parse(parser, code.strip().toCharArray()) match {
      case CombinatorParsers.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case CombinatorParsers.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("17") {
    compile(
      CombinatorParsers.normalStructMember,
      "a @ListNode<T>;") shouldHave {
      case NormalStructMemberP(_, NameP(_, "a"), FinalP, InterpretedPT(_,ShareP,ReadonlyP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")), Vector(NameOrRunePT(NameP(_, "T")))))) =>
    }
  }

  test("18") {
    compile(
      CombinatorParsers.normalStructMember,
      "a Array<imm, T>;") shouldHave {
      case NormalStructMemberP(_, NameP(_, "a"), FinalP, CallPT(_,NameOrRunePT(NameP(_, "Array")), Vector(MutabilityPT(_,ImmutableP), NameOrRunePT(NameP(_, "T"))))) =>
    }
  }

  test("Simple struct") {
    compileMaybe(
      Parser.parseStruct(_),
      "struct Moo { x *int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), Vector(), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,PointerP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Variadic struct") {
    val thing = compileMaybe(
      Parser.parseStruct(_),
      "struct Moo<T> { _ ...T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, FinalP, NameOrRunePT(NameP(_, "T"))))) =>
    })
  }

  test("Variadic struct with varying") {
    val thing = compileMaybe(
      Parser.parseStruct(_),
      "struct Moo<T> { _! ...T; }")
    Collector.only(thing, {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, VaryingP, NameOrRunePT(NameP(_, "T"))))) =>
    })
  }

  test("Struct with weak") {
    compileMaybe(
      Parser.parseStruct(_),
      "struct Moo { x **int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), Vector(), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,WeakP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Struct with inl") {
    compileMaybe(
      Parser.parseStruct(_),
      "struct Moo { x inl Marine; }") shouldHave {
      case StructP(_,NameP(_,"Moo"),Vector(), MutabilityPT(_, MutableP),None,None,StructMembersP(_,Vector(NormalStructMemberP(_,NameP(_,"x"),FinalP,InlinePT(_,NameOrRunePT(NameP(_,"Marine"))))))) =>
    }
  }

  test("Export struct") {
    compileMaybe(
      Parser.parseStruct(_),
      "struct Moo export { x *int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), Vector(ExportP(_)), MutabilityPT(_, MutableP), None, None, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,PointerP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Struct with rune") {
    compileMaybe(
      Parser.parseStruct(_),
      """
        |struct ListNode<E> {
        |  value E;
        |  next ListNode<E>;
        |}
      """.stripMargin.strip()) shouldHave {
      case StructP(
        _,
        NameP(_, "ListNode"),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "E"), Vector())))),
        None,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_,NameP(_, "value"),FinalP,NameOrRunePT(NameP(_, "E"))),
            NormalStructMemberP(_,NameP(_, "next"),FinalP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")),Vector(NameOrRunePT(NameP(_, "E")))))))) =>
    }
  }

  test("Struct with int rune") {
    compileMaybe(
      Parser.parseStruct(_),
      """
        |struct Vecf<N>
        |rules(N int)
        |{
        |  values [N * float];
        |}
        |
      """.stripMargin.strip()) shouldHave {
      case StructP(
        _,
        NameP(_, "Vecf"),
        Vector(),
        MutabilityPT(_, MutableP),
        Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "N"), Vector())))),
        Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, "N")), IntTypePR)))),
        StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, "values"), FinalP, RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float"))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    compileMaybe(
      Parser.parseStruct(_),
      """
        |struct Vecf<N>
        |rules(N int)
        |{
        |  values [<imm> N * float];
        |}
        |
      """.stripMargin.strip()) shouldHave {
      case StructP(
          _,
          NameP(_, "Vecf"),
          Vector(),
          MutabilityPT(_, MutableP),
          Some(IdentifyingRunesP(_, Vector(IdentifyingRuneP(_, NameP(_, "N"), Vector())))),
          Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, "N")),IntTypePR)))),
          StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, "values"),FinalP,RepeaterSequencePT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_, FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float"))))))) =>
    }
  }
}
