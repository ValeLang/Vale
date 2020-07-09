package net.verdagon.vale.parser

import net.verdagon.vale.vassert
import org.scalatest.{FunSuite, Matchers}


trait Collector {
  def recursiveCollectFirst[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Option[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return Some(partialFunction.apply(a))
    }
    a match {
      case p : Product => {
        val opt: Option[R] = None
        p.productIterator.foldLeft(opt)({
          case (Some(x), _) => Some(x)
          case (None, next) => recursiveCollectFirst(next, partialFunction)
        })
      }
      case _ => None
    }
  }

  implicit class ProgramWithExpect(program: Any) {
    def shouldHave[T](f: PartialFunction[Any, T]): T = {
      recursiveCollectFirst(program, f) match {
        case None => throw new AssertionError("Couldn't find the thing, in:\n" + program)
        case Some(t) => t
      }
    }
  }
}

class BiggerTests extends FunSuite with Matchers with Collector {
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

  test("Simple while loop") {
    compile(VParser.statement,"while () {}") shouldHave {
      case WhilePE(BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("Result after while loop") {
    compile(VParser.blockExprs,"while () {} = false;") shouldHave {
      case List(
      WhilePE(BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))),
      BoolLiteralPE(_, false)) =>
    }
  }

  test("Block with result") {
    compile(VParser.blockExprs,"= 3;") shouldHave {
      case List(IntLiteralPE(_, 3)) =>
    }
  }

  test("Simple function") {
    compile(VParser.topLevelFunction, "fn sum(){3}") match {
      case FunctionP(_, Some(StringP(_, "sum")), None, None, None, None, Some(ParamsP(_,List())), None, Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

//  test("Simple function with typed identifying rune") {
//    val func = compile(VParser.topLevelFunction, "fn sum<A>(a A){a}")
//    func.templateRules shouldHave {
  // case  }

  test("Function call") {
    val program = compile(VParser.program, "fn main(){call(sum)}")
//    val main = program.lookupFunction("main")

    program shouldHave {
      case FunctionCallPE(_, None, LookupPE(StringP(_, "call"), None),List(LookupPE(StringP(_, "sum"), None)),true) =>
    }
  }

  test("Mutating as statement") {
    val program = compile(VParser.topLevelFunction, "fn main() { mut x = 6; }")
    program shouldHave {
      case MutatePE(_,LookupPE(StringP(_, "x"),None),IntLiteralPE(_, 6)) =>
    }
  }





  test("Test templated lambda param") {
    val program = compile(VParser.program, "fn main(){(a){ a + a}(3)}")
    program shouldHave { case FunctionCallPE(_, None, LambdaPE(_, _), List(IntLiteralPE(_, 3)),true) => }
    program shouldHave {
      case PatternPP(_,_, Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),None,None,None) =>
    }
    program shouldHave {
      case FunctionCallPE(_, None, LookupPE(StringP(_, "+"), None),List(LookupPE(StringP(_, "a"), None), LookupPE(StringP(_, "a"), None)),true) =>
    }
  }

  test("Simple struct") {
    compile(VParser.struct, "struct Moo { x &int; }") shouldHave {
      case StructP(_, StringP(_, "Moo"), false, MutableP, None, None, StructMembersP(_, List(StructMemberP(_, StringP(_, "x"), FinalP, OwnershippedPT(_,BorrowP,NameOrRunePT(StringP(_, "int"))))))) =>
    }
  }

  test("Struct with inl") {
    compile(VParser.struct, "struct Moo { x inl Marine; }") shouldHave {
      case StructP(_,StringP(_,"Moo"),false,MutableP,None,None,StructMembersP(_,List(StructMemberP(_,StringP(_,"x"),FinalP,InlinePT(_,NameOrRunePT(StringP(_,"Marine"))))))) =>
    }
  }

  test("Export struct") {
    compile(VParser.struct, "struct Moo export { x &int; }") shouldHave {
      case StructP(_, StringP(_, "Moo"), true, MutableP, None, None, StructMembersP(_, List(StructMemberP(_, StringP(_, "x"), FinalP, OwnershippedPT(_,BorrowP,NameOrRunePT(StringP(_, "int"))))))) =>
    }
  }

  test("Test block's trailing void presence") {
    compile(VParser.filledBody, "{ moo() }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, LookupPE(StringP(_, "moo"), None), List(), true))) =>
    }

    compile(VParser.filledBody, "{ moo(); }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, LookupPE(StringP(_, "moo"), None), List(), true), VoidPE(_))) =>
    }
  }

  test("ifs") {
    compile(VParser.ifLadder, "if (true) { doBlarks(&x) } else { }") shouldHave {
      case IfPE(_,
      BlockPE(_, List(BoolLiteralPE(_, true))),
      BlockPE(_, List(FunctionCallPE(_, None, LookupPE(StringP(_, "doBlarks"), None), List(LendPE(_,LookupPE(StringP(_, "x"), None))), true))),
      BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("Block with only a result") {
    compile(
      VParser.blockExprs,
      "= doThings(a);") shouldHave {
      case List(FunctionCallPE(_, None, LookupPE(StringP(_, "doThings"), None), List(LookupPE(StringP(_, "a"), None)), true)) =>
    }
  }


  test("Block with statement and result") {
    compile(
      VParser.blockExprs,
      """
        |b;
        |= a;
      """.stripMargin) shouldHave {
      case List(LookupPE(StringP(_, "b"), None), LookupPE(StringP(_, "a"), None)) =>
    }
  }


  test("Block with result that could be an expr") {
    // = doThings(a); could be misinterpreted as an expression doThings(=, a) if we're
    // not careful.
    compile(
      VParser.blockExprs,
      """
        |a = 2;
        |= doThings(a);
      """.stripMargin) shouldHave {
      case List(
          LetPE(_,List(), PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "a")), FinalP)), None, None, None), IntLiteralPE(_, 2)),
            FunctionCallPE(_, None, LookupPE(StringP(_, "doThings"), None), List(LookupPE(StringP(_, "a"), None)), true)) =>
    }
  }

  test("Templated impl") {
    compile(
      VParser.impl,
      """
        |impl<T> SomeStruct<T> for MyInterface<T>;
      """.stripMargin) shouldHave {
      case ImplP(_,
      Some(IdentifyingRunesP(_, List(StringP(_, "T")))),
      None,
      CallPT(_,NameOrRunePT(StringP(_, "SomeStruct")), List(NameOrRunePT(StringP(_, "T")))),
      CallPT(_,NameOrRunePT(StringP(_, "MyInterface")), List(NameOrRunePT(StringP(_, "T"))))) =>
    }
  }

  test("Impling a template call") {
    compile(
      VParser.impl,
      """
        |impl MyIntIdentity for IFunction1<mut, int, int>;
        |""".stripMargin) shouldHave {
      case ImplP(_,
      None,
      None,
      NameOrRunePT(StringP(_, "MyIntIdentity")),
      CallPT(_,NameOrRunePT(StringP(_, "IFunction1")), List(MutabilityPT(MutableP), NameOrRunePT(StringP(_, "int")), NameOrRunePT(StringP(_, "int"))))) =>
    }
  }


  test("Virtual function") {
    compile(
      VParser.topLevelFunction,
      """
        |fn doCivicDance(virtual this Car) int;
      """.stripMargin) shouldHave {
      case FunctionP(
        _,
        Some(StringP(_, "doCivicDance")), None, None, None, None,
        Some(ParamsP(_, List(PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "this")), FinalP)), Some(NameOrRunePT(StringP(_, "Car"))), None, Some(AbstractP))))),
        Some(NameOrRunePT(StringP(_, "int"))), None) =>
    }
  }


  test("17") {
    compile(
      VParser.structMember,
      "a *ListNode<T>;") shouldHave {
      case StructMemberP(_, StringP(_, "a"), FinalP, OwnershippedPT(_,ShareP,CallPT(_,NameOrRunePT(StringP(_, "ListNode")), List(NameOrRunePT(StringP(_, "T")))))) =>
    }
  }

  test("18") {
    compile(
      VParser.structMember,
      "a Array<imm, T>;") shouldHave {
      case StructMemberP(_, StringP(_, "a"), FinalP, CallPT(_,NameOrRunePT(StringP(_, "Array")), List(MutabilityPT(ImmutableP), NameOrRunePT(StringP(_, "T"))))) =>
    }
  }

  test("19") {
    compile(VParser.statement,
      "newLen = if (num == 0) { 1 } else { 2 };") shouldHave {
      case LetPE(_,
      List(),
      PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "newLen")), FinalP)), None, None, None),
      IfPE(_,
      BlockPE(_, List(FunctionCallPE(_, None, LookupPE(StringP(_, "=="), None), List(LookupPE(StringP(_, "num"), None), IntLiteralPE(_, 0)), true))),
      BlockPE(_, List(IntLiteralPE(_, 1))),
      BlockPE(_, List(IntLiteralPE(_, 2))))) =>
    }
  }

  test("20") {
    compile(VParser.expression,
      "weapon.owner.map()") shouldHave {
      case MethodCallPE(_,
        DotPE(_,
          LookupPE(StringP(_,"weapon"),None),
          LookupPE(StringP(_,"owner"),None)),
        true,
        LookupPE(StringP(_,"map"),None),
      List()) =>
    }
  }

  test("!=") {
    compile(VParser.expression,"3 != 4") shouldHave {
      case FunctionCallPE(_, None, LookupPE(StringP(_, "!="), None), List(IntLiteralPE(_, 3), IntLiteralPE(_, 4)), true) =>
    }
  }
}
