package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}


class StructTests extends FunSuite with Matchers with Collector {
  private def compile[T](parser: VParser.Parser[T], code: String): T = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    VParser.parse(parser, code.strip().toCharArray()) match {
      case VParser.NoSuccess(msg, input) => {
        fail("Couldn't parse!\n" + input.pos.longString);
      }
      case VParser.Success(expr, rest) => {
        vassert(rest.atEnd)
        expr
      }
    }
  }

  test("Struct with rune") {
    compile(VParser.topLevelThing,
      """
        |struct ListNode<E> {
        |  value E;
        |  next ListNode<E>;
        |}
      """.stripMargin) shouldHave {
      case TopLevelStruct(
            StructP(
              _,
              StringP(_, "ListNode"),
              false,
              MutableP,
              Some(IdentifyingRunesP(_, List(StringP(_, "E")))),
              None,
              StructMembersP(_,
                List(
                  StructMemberP(_,StringP(_, "value"),FinalP,NameOrRunePT(StringP(_, "E"))),
                  StructMemberP(_,StringP(_, "next"),FinalP,CallPT(_,NameOrRunePT(StringP(_, "ListNode")),List(NameOrRunePT(StringP(_, "E"))))))))) =>
    }
  }

  test("Struct with int rune") {
    compile(VParser.topLevelThing,
      """
        |struct Vecf<N>
        |rules(N int)
        |{
        |  values [N * float];
        |}
        |
      """.stripMargin) shouldHave {
      case TopLevelStruct(
      StructP(
      _,
      StringP(_, "Vecf"),
      false,
      MutableP,
      Some(IdentifyingRunesP(_, List(StringP(_, "N")))),
      Some(TemplateRulesP(_, List(TypedPR(Some(StringP(_, "N")), IntTypePR)))),
      StructMembersP(_, List(StructMemberP(_,StringP(_, "values"), FinalP, RepeaterSequencePT(_,MutabilityPT(MutableP), NameOrRunePT(StringP(_, "N")), NameOrRunePT(StringP(_, "float")))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    compile(VParser.topLevelThing,
      """
        |struct Vecf<N>
        |rules(N int)
        |{
        |  values [<imm> N * float];
        |}
        |
      """.stripMargin) shouldHave {
      case TopLevelStruct(
        StructP(
        _,
          StringP(_, "Vecf"),
          false,
          MutableP,
          Some(IdentifyingRunesP(_, List(StringP(_, "N")))),
          Some(TemplateRulesP(_, List(TypedPR(Some(StringP(_, "N")),IntTypePR)))),
          StructMembersP(_, List(StructMemberP(_,StringP(_, "values"),FinalP,RepeaterSequencePT(_,MutabilityPT(ImmutableP), NameOrRunePT(StringP(_, "N")), NameOrRunePT(StringP(_, "float")))))))) =>
    }
  }
}
