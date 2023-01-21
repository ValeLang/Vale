package dev.vale.parsing

import dev.vale.lexing.ImportL
import dev.vale.options.GlobalOptions
import dev.vale.{Collector, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, PackageCoordinate, StrI, vassertOne, vassertSome}
import dev.vale.parsing.ast.{BorrowP, CallPT, ExportAttributeP, FinalP, GenericParameterP, GenericParametersP, ImmutableP, IntTypePR, InterpretedPT, MutabilityPT, MutableP, NameOrRunePT, NameP, NormalStructMemberP, OwnP, RuntimeSizedArrayPT, ShareP, StaticSizedArrayPT, StructMembersP, StructP, TemplateRulesP, TopLevelStructP, TypedPR, VariabilityPT, VariadicStructMemberP, VaryingP, WeakP}
import dev.vale.parsing.ast._
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

  test("Simple struct") {
    vassertOne(compileFile("""struct Moo { }""").getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(_,
      NameP(_, StrI("Moo")),
      Vector(),
      None,
      None,
      None,
      _,
      _,
      StructMembersP(_,
      Vector()))) =>
    }
  }

  test("17a") {
    val denizen =
      vassertOne(
        compileFile(
          """
            |struct Mork {
            |  a @ListNode<T>;
            |}
            |""".stripMargin).getOrDie().denizens)
    denizen shouldHave {
      case NormalStructMemberP(_, NameP(_, StrI("a")), FinalP, InterpretedPT(_,Some(OwnershipPT(_, ShareP)),None,CallPT(_,NameOrRunePT(NameP(_, StrI("ListNode"))), Vector(NameOrRunePT(NameP(_, StrI("T"))))))) =>
//      case NormalStructMemberP(_,NameP(_,StrI(a)),final,InterpretedPT(_,share,CallPT(_,NameOrRunePT(NameP(_,StrI(ListNode))),Vector())))
    }
  }
  test("Imm generic param") {
    val denizen =
      compileDenizenExpect(
          """
            |struct MyImmContainer<T Ref imm> imm { value T; }
            |""".stripMargin)

    val struct =
      denizen match {
        case TopLevelStructP(s) => s
      }

    vassertOne(vassertSome(struct.identifyingRunes).params).attributes match {
      case Vector(ImmutableRuneAttributeP(_)) =>
    }

    struct.members.contents match {
      case Vector(NormalStructMemberP(_,NameP(_,StrI("value")),FinalP,NameOrRunePT(NameP(_,StrI("T"))))) =>
    }
  }

  test("18") {
    vassertOne(
      compileFile(
        """
          |struct Mork {
          |  a []<imm>T;
          |}
          |""".stripMargin).getOrDie().denizens) shouldHave {
      case NormalStructMemberP(_,NameP(_, StrI("a")),FinalP,RuntimeSizedArrayPT(_,MutabilityPT(_,ImmutableP),NameOrRunePT(NameP(_, StrI("T"))))) =>
    }
  }

  test("Variadic struct") {
    Collector.only(
      vassertOne(compileFile("struct Moo<T> { _ ..T; }").getOrDie().denizens),
      {
        case StructMembersP(_, Vector(VariadicStructMemberP(_, FinalP, NameOrRunePT(NameP(_, StrI("T")))))) =>
      })
  }

  test("Variadic struct with varying") {
    Collector.only(vassertOne(compileFile("struct Moo<T> { _! ..T; }").getOrDie().denizens), {
      case StructMembersP(_, Vector(VariadicStructMemberP(_, VaryingP, NameOrRunePT(NameP(_, StrI("T")))))) =>
    })
  }

  test("Struct with weak") {
    vassertOne(compileFile("struct Moo { x &&int; }").getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, StrI("Moo")), Vector(), None, None, None, _, _, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, StrI("x")), FinalP, InterpretedPT(_,Some(OwnershipPT(_, WeakP)),None,NameOrRunePT(NameP(_, StrI("int"))))))))) =>
    }
  }

  test("Struct with heap") {
    vassertOne(compileFile("struct Moo { x ^Marine; }").getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(_,NameP(_, StrI("Moo")),Vector(), None,None,None,_, _, StructMembersP(_,Vector(NormalStructMemberP(_,NameP(_, StrI("x")),FinalP,InterpretedPT(_,Some(OwnershipPT(_, OwnP)),None,NameOrRunePT(NameP(_, StrI("Marine"))))))))) =>
    }
  }

  test("Export struct") {
    vassertOne(compileFile("exported struct Moo { x &int; }").getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(_, NameP(_, StrI("Moo")), Vector(ExportAttributeP(_)), None, None, None, _, _, StructMembersP(_, Vector(NormalStructMemberP(_, NameP(_, StrI("x")), FinalP, InterpretedPT(_,Some(OwnershipPT(_, BorrowP)),None,NameOrRunePT(NameP(_, StrI("int"))))))))) =>
    }
  }

  test("Struct with rune") {
    vassertOne(
      compileFile(
        """
          |struct ListNode<E> {
          |  value E;
          |  next ListNode<E>;
          |}
        """.stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, StrI("ListNode")),
        Vector(),
        None,
        Some(GenericParametersP(_, Vector(GenericParameterP(_, NameP(_, StrI("E")), _, _, Vector(), None)))),
        None,
        _,
        _,
        StructMembersP(_,
          Vector(
            NormalStructMemberP(_,NameP(_, StrI("value")),FinalP,NameOrRunePT(NameP(_, StrI("E")))),
            NormalStructMemberP(_,NameP(_, StrI("next")),FinalP,CallPT(_,NameOrRunePT(NameP(_, StrI("ListNode"))),Vector(NameOrRunePT(NameP(_, StrI("E")))))))))) =>
    }
  }

  test("Struct with int rune") {
    vassertOne(
      compileFile(
        """
          |struct Vecf<N> where N Int
          |{
          |  values [#N]float;
          |}
          |
      """.stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelStructP(StructP(
        _,
        NameP(_, StrI("Vecf")),
        Vector(),
        None,
        Some(GenericParametersP(_, Vector(GenericParameterP(_, NameP(_, StrI("N")), _, _, Vector(), None)))),
        Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, StrI("N"))), IntTypePR)))),
        _,
        _,
        StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, StrI("values")), FinalP, StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_,FinalP), NameOrRunePT(NameP(_, StrI("N"))), NameOrRunePT(NameP(_, StrI("float"))))))))) =>
    }
  }

  test("Struct with int rune, array sequence specifies mutability") {
    vassertOne(
      compileFile(
        """
          |struct Vecf<N> where N Int
          |{
          |  values [#N]float;
          |}
      """.stripMargin).getOrDie().denizens) shouldHave {
      case TopLevelStructP(
          StructP(
            _,
            NameP(_, StrI("Vecf")),
            Vector(),
            None,
            Some(GenericParametersP(_, Vector(GenericParameterP(_, NameP(_, StrI("N")), _, _, Vector(), None)))),
            Some(TemplateRulesP(_, Vector(TypedPR(_,Some(NameP(_, StrI("N"))),IntTypePR)))),
            _,
            _,
            StructMembersP(_, Vector(NormalStructMemberP(_,NameP(_, StrI("values")),FinalP,StaticSizedArrayPT(_,MutabilityPT(_,MutableP), VariabilityPT(_, FinalP), NameOrRunePT(NameP(_, StrI("N"))), NameOrRunePT(NameP(_, StrI("float"))))))))) =>
//      case TopLevelStructP(
//        StructP(_,
//          NameP(_,StrI("Vecf")),
//          Vector(),
//          None,
//          Some(IdentifyingRunesP(_,Vector(IdentifyingRuneP(_,NameP(_,StrI("N")),Vector())))),
//          Some(TemplateRulesP(_,Vector(TypedPR(_,Some(NameP(_,StrI("N"))),IntTypePR)))),
//          StructMembersP(_,Vector(NormalStructMemberP(_,NameP(_,StrI("values")),FinalP,StaticSizedArrayPT(_,MutabilityPT(_,mut),VariabilityPT(_,final),NameOrRunePT(NameP(_,StrI(N))),NameOrRunePT(NameP(_,StrI(float)))))))))
    }
  }
}
