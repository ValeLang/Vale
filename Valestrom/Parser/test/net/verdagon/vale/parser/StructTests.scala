package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}


class StructTests extends FunSuite with Matchers with Collector {
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
      CombinatorParsers.structMember,
      "a *ListNode<T>;") shouldHave {
      case StructMemberP(_, NameP(_, "a"), FinalP, InterpretedPT(_,ShareP,ReadonlyP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")), List(NameOrRunePT(NameP(_, "T")))))) =>
    }
  }

  test("18") {
    compile(
      CombinatorParsers.structMember,
      "a Array<imm, final, T>;") shouldHave {
      case StructMemberP(_, NameP(_, "a"), FinalP, CallPT(_,NameOrRunePT(NameP(_, "Array")), List(MutabilityPT(_,ImmutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "T"))))) =>
    }
  }

  test("Simple struct") {
    compile(CombinatorParsers.struct, "struct Moo { x &int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), Nil, MutableP, None, None, StructMembersP(_, List(StructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,ConstraintP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Struct with weak") {
    compile(CombinatorParsers.struct, "struct Moo { x &&int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), Nil, MutableP, None, None, StructMembersP(_, List(StructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,WeakP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Struct with inl") {
    compile(CombinatorParsers.struct, "struct Moo { x inl Marine; }") shouldHave {
      case StructP(_,NameP(_,"Moo"),Nil, MutableP,None,None,StructMembersP(_,List(StructMemberP(_,NameP(_,"x"),FinalP,InlinePT(_,NameOrRunePT(NameP(_,"Marine"))))))) =>
    }
  }

  test("Export struct") {
    compile(CombinatorParsers.struct, "struct Moo export { x &int; }") shouldHave {
      case StructP(_, NameP(_, "Moo"), List(ExportP(_)), MutableP, None, None, StructMembersP(_, List(StructMemberP(_, NameP(_, "x"), FinalP, InterpretedPT(_,ConstraintP,ReadonlyP,NameOrRunePT(NameP(_, "int"))))))) =>
    }
  }

  test("Struct with rune") {
    compile(CombinatorParsers.struct,
      """
        |struct ListNode<E> {
        |  value E;
        |  next ListNode<E>;
        |}
      """.stripMargin.strip()) shouldHave {
      case StructP(
        _,
        NameP(_, "ListNode"),
        Nil,
        MutableP,
        Some(IdentifyingRunesP(_, List(IdentifyingRuneP(_, NameP(_, "E"), Nil)))),
        None,
        StructMembersP(_,
          List(
            StructMemberP(_,NameP(_, "value"),FinalP,NameOrRunePT(NameP(_, "E"))),
            StructMemberP(_,NameP(_, "next"),FinalP,CallPT(_,NameOrRunePT(NameP(_, "ListNode")),List(NameOrRunePT(NameP(_, "E")))))))) =>
    }
  }

  test("Struct with int rune") {
    compile(CombinatorParsers.struct,
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
        Nil,
        MutableP,
        Some(IdentifyingRunesP(_, List(IdentifyingRuneP(_, NameP(_, "N"), Nil)))),
        Some(TemplateRulesP(_, List(TypedPR(_,Some(NameP(_, "N")), IntTypePR)))),
        StructMembersP(_, List(StructMemberP(_,NameP(_, "values"), FinalP, RepeaterSequencePT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float"))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    compile(CombinatorParsers.struct,
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
          Nil,
          MutableP,
          Some(IdentifyingRunesP(_, List(IdentifyingRuneP(_, NameP(_, "N"), Nil)))),
          Some(TemplateRulesP(_, List(TypedPR(_,Some(NameP(_, "N")),IntTypePR)))),
          StructMembersP(_, List(StructMemberP(_,NameP(_, "values"),FinalP,RepeaterSequencePT(_,MutabilityPT(_,ImmutableP), VariabilityPT(_, FinalP), NameOrRunePT(NameP(_, "N")), NameOrRunePT(NameP(_, "float"))))))) =>
    }
  }
}
