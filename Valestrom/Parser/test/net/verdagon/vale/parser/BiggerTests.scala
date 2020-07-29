package net.verdagon.vale.parser

import net.verdagon.vale.{Samples, vassert}
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
  private def compileProgramWithComments(code: String): Program0 = {
    Parser.runParserForProgramAndCommentRanges(code) match {
      case ParseFailure(pos, msg) => fail(msg + " (" + pos + ")");
      case ParseSuccess(result) => result._1
    }
  }
  private def compileProgram(code: String): Program0 = {
    // The strip is in here because things inside the parser don't expect whitespace before and after
    Parser.runParser(code) match {
      case ParseFailure(pos, msg) => fail(msg + " (" + pos + ")");
      case ParseSuccess(result) => result
    }
  }

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

  test("Function then struct") {
    val program = compileProgram(
      """
        |fn main(){}
        |
        |struct mork { }
        |""".stripMargin)
    program.topLevelThings(0) match { case TopLevelFunction(_) => }
    program.topLevelThings(1) match { case TopLevelStruct(_) => }
  }

  test("Simple while loop") {
    compile(CombinatorParsers.statement,"while () {}") shouldHave {
      case WhilePE(_, BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("Result after while loop") {
    compile(CombinatorParsers.blockExprs,"while () {} = false;") shouldHave {
      case List(
      WhilePE(_, BlockPE(_, List(VoidPE(_))), BlockPE(_, List(VoidPE(_)))),
      BoolLiteralPE(_, false)) =>
    }
  }

  test("Block with result") {
    compile(CombinatorParsers.blockExprs,"= 3;") shouldHave {
      case List(IntLiteralPE(_, 3)) =>
    }
  }

  test("Simple function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum(){3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(), None, None, Some(ParamsP(_,List())), None),
        Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Pure function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() pure {3}") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(PureAttributeP(_)), None, None, Some(ParamsP(_,List())), None),
        Some(BlockPE(_, List(IntLiteralPE(_, 3))))) =>
    }
  }

  test("Extern function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() extern;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(ExternAttributeP(_)), None, None, Some(ParamsP(_,List())), None),
        None) =>
    }
  }

  test("Abstract function") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(AbstractAttributeP(_)), None, None, Some(ParamsP(_,List())), None),
        None) =>
    }
  }

  test("Attribute after return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() Int abstract;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(AbstractAttributeP(_)), None, None, Some(ParamsP(_,List())), Some(NameOrRunePT(StringP(_,"Int")))),
        None) =>
    }
  }

  test("Attribute before return") {
    compile(CombinatorParsers.topLevelFunction, "fn sum() abstract Int;") match {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "sum")), List(AbstractAttributeP(_)), None, None, Some(ParamsP(_,List())), Some(NameOrRunePT(StringP(_,"Int")))),
        None) =>
    }
  }

  test("Simple function with identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List()) =>
    }
  }

  test("Simple function with coord-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A coord>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, CoordTypePR))) =>
    }
  }

  test("Simple function with region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<A reg>(a A){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Simple function with apostrophe region-typed identifying rune") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_, StringP(_, "A"), List(TypeRuneAttributeP(_, RegionTypePR))) =>
    }
  }

  test("Pool region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A pool>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
      StringP(_, "A"),
      List(
      TypeRuneAttributeP(_, RegionTypePR),
      PoolRuneAttributeP(_))) =>
    }
  }

  test("Arena region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A arena>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        StringP(_, "A"),
        List(
          TypeRuneAttributeP(_, RegionTypePR),
          ArenaRuneAttributeP(_))) =>
    }
  }


  test("Readonly region") {
    val func = compile(CombinatorParsers.topLevelFunction, "fn sum<'A ro>(a 'A &Marine){a}")
    func.header.maybeUserSpecifiedIdentifyingRunes.get.runes.head match {
      case IdentifyingRuneP(_,
        StringP(_, "A"),
        List(
          TypeRuneAttributeP(_, RegionTypePR),
          ReadOnlyRuneAttributeP(_))) =>
    }
  }

  test("Function call") {
    val program = compileProgram("fn main(){call(sum)}")
//    val main = program.lookupFunction("main")

    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "call"), None),List(LookupPE(StringP(_, "sum"), None)),BorrowP) =>
    }
  }

  test("Mutating as statement") {
    val program = compile(CombinatorParsers.topLevelFunction, "fn main() { mut x = 6; }")
    program shouldHave {
      case MutatePE(_,LookupPE(StringP(_, "x"),None),IntLiteralPE(_, 6)) =>
    }
  }





  test("Test templated lambda param") {
    val program = compileProgram("fn main(){(a){ a + a}(3)}")
    program shouldHave { case FunctionCallPE(_, None, _, false, LambdaPE(_, _), List(IntLiteralPE(_, 3)),BorrowP) => }
    program shouldHave {
      case PatternPP(_,_, Some(CaptureP(_,LocalNameP(StringP(_, "a")),FinalP)),None,None,None) =>
    }
    program shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "+"), None),List(LookupPE(StringP(_, "a"), None), LookupPE(StringP(_, "a"), None)),BorrowP) =>
    }
  }

  test("Simple struct") {
    compile(CombinatorParsers.struct, "struct Moo { x &int; }") shouldHave {
      case StructP(_, StringP(_, "Moo"), List(), MutableP, None, None, StructMembersP(_, List(StructMemberP(_, StringP(_, "x"), FinalP, OwnershippedPT(_,BorrowP,NameOrRunePT(StringP(_, "int"))))))) =>
    }
  }

  test("Struct with weak") {
    compile(CombinatorParsers.struct, "struct Moo { x &&int; }") shouldHave {
      case StructP(_, StringP(_, "Moo"), List(), MutableP, None, None, StructMembersP(_, List(StructMemberP(_, StringP(_, "x"), FinalP, OwnershippedPT(_,WeakP,NameOrRunePT(StringP(_, "int"))))))) =>
    }
  }

  test("Struct with inl") {
    compile(CombinatorParsers.struct, "struct Moo { x inl Marine; }") shouldHave {
      case StructP(_,StringP(_,"Moo"),List(), MutableP,None,None,StructMembersP(_,List(StructMemberP(_,StringP(_,"x"),FinalP,InlinePT(_,NameOrRunePT(StringP(_,"Marine"))))))) =>
    }
  }

  test("Export struct") {
    compile(CombinatorParsers.struct, "struct Moo export { x &int; }") shouldHave {
      case StructP(_, StringP(_, "Moo"), List(ExportP(_)), MutableP, None, None, StructMembersP(_, List(StructMemberP(_, StringP(_, "x"), FinalP, OwnershippedPT(_,BorrowP,NameOrRunePT(StringP(_, "int"))))))) =>
    }
  }

  test("Test block's trailing void presence") {
    compile(CombinatorParsers.filledBody, "{ moo() }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "moo"), None), List(), BorrowP))) =>
    }

    compile(CombinatorParsers.filledBody, "{ moo(); }") shouldHave {
      case BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "moo"), None), List(), BorrowP), VoidPE(_))) =>
    }
  }

  test("ifs") {
    compile(CombinatorParsers.ifLadder, "if (true) { doBlarks(&x) } else { }") shouldHave {
      case IfPE(_,
      BlockPE(_, List(BoolLiteralPE(_, true))),
      BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "doBlarks"), None), List(LendPE(_,LookupPE(StringP(_, "x"), None), BorrowP)), BorrowP))),
      BlockPE(_, List(VoidPE(_)))) =>
    }
  }

  test("if let") {
    compile(CombinatorParsers.ifLadder, "if ((u) = a) {}") shouldHave {
      case IfPE(_,
        BlockPE(_,
          List(
            LetPE(_,List(),
              PatternPP(_,None,None,None,
                Some(
                  DestructureP(_,
                    List(
                      PatternPP(_,None,Some(CaptureP(_,LocalNameP(StringP(_,"u")),FinalP)),None,None,None)))),
                None),
              LookupPE(StringP(_,"a"),None)))),
        BlockPE(_,List(VoidPE(_))),
        BlockPE(_,List(VoidPE(_)))) =>
    }
  }


  test("Block with only a result") {
    compile(
      CombinatorParsers.blockExprs,
      "= doThings(a);") shouldHave {
      case List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "doThings"), None), List(LookupPE(StringP(_, "a"), None)), BorrowP)) =>
    }
  }


  test("Block with statement and result") {
    compile(
      CombinatorParsers.blockExprs,
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
      CombinatorParsers.blockExprs,
      """
        |a = 2;
        |= doThings(a);
      """.stripMargin) shouldHave {
      case List(
          LetPE(_,List(), PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "a")), FinalP)), None, None, None), IntLiteralPE(_, 2)),
            FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "doThings"), None), List(LookupPE(StringP(_, "a"), None)), BorrowP)) =>
    }
  }

  test("Templated impl") {
    compile(
      CombinatorParsers.impl,
      """
        |impl<T> SomeStruct<T> for MyInterface<T>;
      """.stripMargin) shouldHave {
      case ImplP(_,
      Some(IdentifyingRunesP(_, List(IdentifyingRuneP(_, StringP(_, "T"), List())))),
      None,
      CallPT(_,NameOrRunePT(StringP(_, "SomeStruct")), List(NameOrRunePT(StringP(_, "T")))),
      CallPT(_,NameOrRunePT(StringP(_, "MyInterface")), List(NameOrRunePT(StringP(_, "T"))))) =>
    }
  }

  test("Impling a template call") {
    compile(
      CombinatorParsers.impl,
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
      CombinatorParsers.topLevelFunction,
      """
        |fn doCivicDance(virtual this Car) int;
      """.stripMargin) shouldHave {
      case FunctionP(_,
        FunctionHeaderP(_,
          Some(StringP(_, "doCivicDance")), List(), None, None,
          Some(ParamsP(_, List(PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "this")), FinalP)), Some(NameOrRunePT(StringP(_, "Car"))), None, Some(AbstractP))))),
          Some(NameOrRunePT(StringP(_, "int")))),
        None) =>
    }
  }


  test("17") {
    compile(
      CombinatorParsers.structMember,
      "a *ListNode<T>;") shouldHave {
      case StructMemberP(_, StringP(_, "a"), FinalP, OwnershippedPT(_,ShareP,CallPT(_,NameOrRunePT(StringP(_, "ListNode")), List(NameOrRunePT(StringP(_, "T")))))) =>
    }
  }

  test("18") {
    compile(
      CombinatorParsers.structMember,
      "a Array<imm, T>;") shouldHave {
      case StructMemberP(_, StringP(_, "a"), FinalP, CallPT(_,NameOrRunePT(StringP(_, "Array")), List(MutabilityPT(ImmutableP), NameOrRunePT(StringP(_, "T"))))) =>
    }
  }

  test("19") {
    compile(CombinatorParsers.statement,
      "newLen = if (num == 0) { 1 } else { 2 };") shouldHave {
      case LetPE(_,
      List(),
      PatternPP(_, _,Some(CaptureP(_,LocalNameP(StringP(_, "newLen")), FinalP)), None, None, None),
      IfPE(_,
      BlockPE(_, List(FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "=="), None), List(LookupPE(StringP(_, "num"), None), IntLiteralPE(_, 0)), BorrowP))),
      BlockPE(_, List(IntLiteralPE(_, 1))),
      BlockPE(_, List(IntLiteralPE(_, 2))))) =>
    }
  }

  test("20") {
    compile(CombinatorParsers.expression,
      "weapon.owner.map()") shouldHave {
      case MethodCallPE(_,
        DotPE(_,
          LookupPE(StringP(_,"weapon"),None),
          _, false,
          LookupPE(StringP(_,"owner"),None)),
        _, BorrowP,
        false,
        LookupPE(StringP(_,"map"),None),
      List()) =>
    }
  }

  test("!=") {
    compile(CombinatorParsers.expression,"3 != 4") shouldHave {
      case FunctionCallPE(_, None, _, false, LookupPE(StringP(_, "!="), None), List(IntLiteralPE(_, 3), IntLiteralPE(_, 4)), BorrowP) =>
    }
  }

  test("weaks/dropThenLock.vale") { compileProgramWithComments(Samples.get("weaks/dropThenLock.vale")) }
  test("weaks/lockWhileLive.vale") { compileProgramWithComments(Samples.get("weaks/lockWhileLive.vale")) }
  test("weaks/weakFromCRef.vale") { compileProgramWithComments(Samples.get("weaks/weakFromCRef.vale")) }
  test("weaks/weakFromLocalCRef.vale") { compileProgramWithComments(Samples.get("weaks/weakFromLocalCRef.vale")) }
  test("addret.vale") { compileProgramWithComments(Samples.get("addret.vale")) }
  test("arrays/immusa.vale") { compileProgramWithComments(Samples.get("arrays/immusa.vale")) }
  test("arrays/immusalen.vale") { compileProgramWithComments(Samples.get("arrays/immusalen.vale")) }
  test("arrays/knownsizeimmarray.vale") { compileProgramWithComments(Samples.get("arrays/knownsizeimmarray.vale")) }
  test("arrays/mutusa.vale") { compileProgramWithComments(Samples.get("arrays/mutusa.vale")) }
  test("arrays/mutusalen.vale") { compileProgramWithComments(Samples.get("arrays/mutusalen.vale")) }
  test("arrays/swapmutusadestroy.vale") { compileProgramWithComments(Samples.get("arrays/swapmutusadestroy.vale")) }
  test("constraintRef.vale") { compileProgramWithComments(Samples.get("constraintRef.vale")) }
  test("genericvirtuals/opt.vale") { compileProgramWithComments(Samples.get("genericvirtuals/opt.vale")) }
  test("if/if.vale") { compileProgramWithComments(Samples.get("if/if.vale")) }
  test("if/nestedif.vale") { compileProgramWithComments(Samples.get("if/nestedif.vale")) }
  test("lambdas/lambda.vale") { compileProgramWithComments(Samples.get("lambdas/lambda.vale")) }
  test("lambdas/lambdamut.vale") { compileProgramWithComments(Samples.get("lambdas/lambdamut.vale")) }
  test("mutlocal.vale") { compileProgramWithComments(Samples.get("mutlocal.vale")) }
  test("nestedblocks.vale") { compileProgramWithComments(Samples.get("nestedblocks.vale")) }
  test("panic.vale") { compileProgramWithComments(Samples.get("panic.vale")) }
  test("strings/inttostr.vale") { compileProgramWithComments(Samples.get("strings/inttostr.vale")) }
  test("strings/stradd.vale") { compileProgramWithComments(Samples.get("strings/stradd.vale")) }
  test("strings/strprint.vale") { compileProgramWithComments(Samples.get("strings/strprint.vale")) }
  test("structs/bigimmstruct.vale") { compileProgramWithComments(Samples.get("structs/bigimmstruct.vale")) }
  test("structs/immstruct.vale") { compileProgramWithComments(Samples.get("structs/immstruct.vale")) }
  test("structs/memberrefcount.vale") { compileProgramWithComments(Samples.get("structs/memberrefcount.vale")) }
  test("structs/mutstruct.vale") { compileProgramWithComments(Samples.get("structs/mutstruct.vale")) }
  test("structs/mutstructstore.vale") { compileProgramWithComments(Samples.get("structs/mutstructstore.vale")) }
  test("unreachablemoot.vale") { compileProgramWithComments(Samples.get("unreachablemoot.vale")) }
  test("unstackifyret.vale") { compileProgramWithComments(Samples.get("unstackifyret.vale")) }
  test("virtuals/imminterface.vale") { compileProgramWithComments(Samples.get("virtuals/imminterface.vale")) }
  test("virtuals/mutinterface.vale") { compileProgramWithComments(Samples.get("virtuals/mutinterface.vale")) }
  test("while/while.vale") { compileProgramWithComments(Samples.get("while/while.vale")) }

}
