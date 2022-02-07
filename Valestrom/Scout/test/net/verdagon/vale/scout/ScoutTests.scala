package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{FinalP, LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsPointerP, MutableP, ReadonlyP, ReadwriteP, UseP}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.solver.{IncompleteSolve, Step}
import net.verdagon.vale.{Collector, Err, FileCoordinate, FileCoordinateMap, Ok, vassert, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import org.scalatest.{FunSuite, Matchers}

class ScoutTests extends FunSuite with Matchers with Collector {
  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case Err(err) => fail(ParseErrorHumanizer.humanize(FileCoordinateMap.test(code), FileCoordinate.test, err))
      case Ok(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case Err(error) => vwat(error.toString)
            case Ok(program0) => program0
          }
        new Scout(GlobalOptions.test()).scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => vfail(e.toString)
          case Ok(t) => t
        }
      }
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    Parser.runParser(code) match {
      case Err(err) => fail(err.toString)
      case Ok(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case Err(error) => vwat(error.toString)
            case Ok(program0) => program0
          }
        new Scout(GlobalOptions.test()).scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => e
          case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
        }
      }
    }
  }

  // See: User Must Specify Enough Identifying Runes (UMSEIR)
  test("Test UMSEIR") {
    // This should work, its fine that the _ is there because we can always figure out what
    // that rune is, from the identifying runes.
    val main =
    compile(
      """
        |fn moo<T>(a T)
        |rules(K Ref, T = Map<K, _>) { ... }
        |""".stripMargin).lookupFunction("moo")

    // This should fail, because we can't figure out what it is, given the identifying runes.
    val error = compileForError(
      """
        |fn moo<K, V>(a Map<K, V, _>) { ... }
        |""".stripMargin)
    error match {
      case IdentifyingRunesIncompleteS(_, IdentifiabilitySolveError(_, IncompleteSolve(_, _,runes))) => {
        // The param rune, and the _ rune are both unknown
        vassert(runes.size == 2)
      }
    }
  }

  test("Lookup +") {
    val program1 = compile("fn main() int export { ret +(3, 4); }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    val ret = Collector.only(block.expr, { case x @ ReturnSE(_, _) => x })
    val call = Collector.only(ret.inner, { case x @ FunctionCallSE(_, _, _) => x })
    Collector.only(call.callableExpr, { case x @ OutsideLoadSE(_, _, CodeNameS("+"), _, _) => x })
  }

  test("Struct") {
    val program1 = compile("struct Moo { x int; }")
    val imoo = program1.lookupStruct("Moo")

    imoo.rules shouldHave {
      case LiteralSR(_, r, MutabilityLiteralSL(MutableP)) => vassert(r == imoo.mutabilityRune)
    }
    imoo.rules shouldHave {
      case LookupSR(_, m, CodeNameS("int")) => vassert(m == imoo.members(0).typeRune)
    }
    imoo.members match {
      case Vector(NormalStructMemberS(_, "x", FinalP, _)) =>
    }
  }

  test("Lambda") {
    val program1 = compile("fn main() int export { ret {_ + _}!(4, 6); }")

    val CodeBodyS(BodySE(_, _, BlockSE(_, _, expr))) = program1.lookupFunction("main").body
    val lambda =
      Collector.only(expr, {
        case ReturnSE(_, FunctionCallSE(_, OwnershippedSE(_, FunctionSE(lambda@FunctionS(_, _, _, _, _, _, _, _, _)), LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadwriteP))), _)) => lambda
      })
    // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
    lambda.identifyingRunes match {
      case Vector(RuneUsage(_, MagicParamRuneS(mp1)), RuneUsage(_, MagicParamRuneS(mp2))) => {
        vassert(mp1 != mp2)
      }
    }
  }

  test("Interface") {
    val program1 = compile("interface IMoo { fn blork(virtual this *IMoo, a bool)void; }")
    val imoo = program1.lookupInterface("IMoo")

    val blork = imoo.internalMethods.head
    blork.name match {
      case FunctionNameS("blork", _) =>
    }
  }

  test("Generic interface") {
    val program1 = compile("interface IMoo<T> { fn blork(virtual this *IMoo, a T)void; }")
    val imoo = program1.lookupInterface("IMoo")

    val blork = imoo.internalMethods.head
    blork.name match {
      case FunctionNameS("blork", _) =>
    }

    vassert(imoo.identifyingRunes.map(_.rune).contains(CodeRuneS("T")))
    // Interface methods of generic interfaces will have the same identifying runes of their
    // generic interfaces, see IMCBT.
    vassert(blork.identifyingRunes.map(_.rune).contains(CodeRuneS("T")))
  }

  test("Impl") {
    val program1 = compile("impl IMoo for Moo;")
    val impl = program1.impls.head
    impl.rules shouldHave {
      case LookupSR(_, r, CodeNameS("Moo")) => vassert(r == impl.structKindRune)
    }
    impl.rules shouldHave {
      case LookupSR(_, r, CodeNameS("IMoo")) => vassert(r == impl.interfaceKindRune)
    }
  }

  test("Method call") {
    val program1 = compile("fn main() int export { ret true.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    val ret = Collector.only(block, { case r @ ReturnSE(_, _) => r })
    Collector.only(ret, { case FunctionCallSE(_, OutsideLoadSE(_, _, CodeNameS("shout"), _, _), Vector(OwnershippedSE(_,ConstantBoolSE(_,true),LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadonlyP))))) => })
//    { case ReturnSE(_,FunctionCallSE(_,_,Vector()) => }
  }

  test("Moving method call") {
    val program1 = compile("fn main() int export { x = 4; ret (x).shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    val ret = Collector.only(block, { case r @ ReturnSE(_, _) => r })
    Collector.only(ret, { case FunctionCallSE(_, OutsideLoadSE(_, _, CodeNameS("shout"), _, _), Vector(LocalLoadSE(_,CodeVarNameS("x"), UseP))) => })
  }

  test("Function with magic lambda and regular lambda") {
    // There was a bug that confused the two, and an underscore would add a magic param to every lambda after it

    val program1 =
      compile(
        """fn main() int export {
          |  {_};
          |  (a) => {a};
          |}
        """.stripMargin)
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    val BlockSE(_, _, ConsecutorSE(things)) = block
    val lambdas = Collector.all(things, { case f @ FunctionSE(_) => f }).toList
    lambdas.head.function.params match {
      case Vector(_, ParameterS(AtomSP(_, Some(CaptureS(MagicParamNameS(_))), None, Some(RuneUsage(_, MagicParamRuneS(_))), None))) =>
    }
    lambdas.last.function.params match {
      case Vector(_, ParameterS(AtomSP(_, Some(CaptureS(CodeVarNameS("a"))), None, Some(RuneUsage(_, ImplicitRuneS(_))), None))) =>
    }
  }


  test("Constructing members") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = true;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block.locals match {
      case Vector(
        LocalS(ConstructingMemberNameS("x"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
        LocalS(ConstructingMemberNameS("y"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)) =>
    }
    val exprs = block.expr match { case ConsecutorSE(exprs) => exprs }
    Collector.only(exprs, {
      case LetSE(_,
      _,
      AtomSP(_, Some(CaptureS(ConstructingMemberNameS("x"))), None, _, None),
      ConstantIntSE(_, 4, _)) =>
    })
    Collector.only(exprs, {
      case LetSE(_,
        _,
        AtomSP(_, Some(CaptureS(ConstructingMemberNameS("y"))), None, _, None),
        ConstantBoolSE(_, true)) =>
    })
    Collector.only(exprs, {
      case FunctionCallSE(_,
        OutsideLoadSE(_, _, CodeNameS("MyStruct"), _, _),
        Vector(
          LocalLoadSE(_, ConstructingMemberNameS("x"), UseP),
          LocalLoadSE(_, ConstructingMemberNameS("y"), UseP))) =>
    })
  }

  test("Cant use set as a local name") {
    val error = compileForError(
      """fn moo() {
        |  [set] = (6,);
        |}
        |""".stripMargin)
    error match {
      case CantUseThatLocalName(_, "set") =>
    }
  }

  test("CantInitializeIndividualElementsOfRuntimeSizedArray") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [][4, 5, 6];
        |}
        |""".stripMargin)
    error match {
      case CantInitializeIndividualElementsOfRuntimeSizedArray(_) =>
    }
  }

  test("InitializingRuntimeSizedArrayRequiresSizeAndCallable too few") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [](4);
        |}
        |""".stripMargin)
    error match {
      case InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingRuntimeSizedArrayRequiresSizeAndCallable too many") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [](4, {_}, 10);
        |}
        |""".stripMargin)
    error match {
      case InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayRequiresSizeAndCallable too few") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [#5]();
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayRequiresSizeAndCallable too many") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [#5](4, {_});
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayFromCallableNeedsSizeTemplex") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [#]({_});
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(_) =>
    }
  }

  test("Test loading from member") {
    val program1 = compile(
      """fn MyStruct() {
        |  ret moo.x;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    Collector.only(block,
      { case ReturnSE(_, DotSE(_,OutsideLoadSE(_,_,CodeNameS("moo"),None,LoadAsBorrowOrIfContainerIsPointerThenPointerP(None)),"x",true)) => })

  }

  test("Test loading from member 2") {
    val program1 = compile(
      """fn MyStruct() {
        |  ret *moo.x;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    Collector.only(block, {
      case ReturnSE(_, OwnershippedSE(_, DotSE(_,OutsideLoadSE(_,_,CodeNameS("moo"),None,LoadAsBorrowOrIfContainerIsPointerThenPointerP(None)),x,true),LoadAsPointerP(Some(ReadonlyP)))) =>
    })
  }

  test("Constructing members, borrowing another member") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = *this.x;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block.locals match {
      case Vector(
        LocalS(ConstructingMemberNameS("x"), Used, Used, NotUsed, NotUsed, NotUsed, NotUsed),
        LocalS(ConstructingMemberNameS("y"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)) =>
    }
    Collector.only(block, {
      case LetSE(_, _,
        AtomSP(_, Some(CaptureS(ConstructingMemberNameS("x"))), None, _, None),
        ConstantIntSE(_, 4, _)) =>
    })
    Collector.only(block, {
      case LetSE(_, _,
        AtomSP(_, Some(CaptureS(ConstructingMemberNameS("y"))), None, _, None),
        LocalLoadSE(_, ConstructingMemberNameS("x"), LoadAsPointerP(Some(ReadonlyP)))) =>
    })
    Collector.only(block, {
      case FunctionCallSE(_,
      OutsideLoadSE(_, _, CodeNameS("MyStruct"), _, _),
      Vector(
      LocalLoadSE(_, ConstructingMemberNameS("x"), UseP),
      LocalLoadSE(_, ConstructingMemberNameS("y"), UseP))) =>
    })
  }

  test("foreach") {
    val program1 = compile(
      """fn main() {
        |  foreach i in myList { }
        |}
        |""".stripMargin)

    val function = program1.lookupFunction("main")
    val CodeBodyS(body) = function.body
    body.block shouldHave {
      case LocalS(IterableNameS(_),Used,NotUsed,NotUsed,NotUsed,NotUsed,NotUsed) =>
    }
    body.block shouldHave {
      case LocalS(IteratorNameS(_),Used,NotUsed,NotUsed,NotUsed,NotUsed,NotUsed) =>
    }
    body.block shouldHave {
      case LocalS(IterationOptionNameS(_),Used,Used,NotUsed,NotUsed,NotUsed,NotUsed) =>
    }
    body.block shouldHave {
      case LocalS(CodeVarNameS("i"),NotUsed,NotUsed,NotUsed,NotUsed,NotUsed,NotUsed) =>
    }
    body.block shouldHave {
      case LetSE(_,_,
        AtomSP(_,Some(CaptureS(IterableNameS(_))),None,None,None),
        OutsideLoadSE(_,_,CodeNameS("myList"),None,UseP)) =>
    }
    body.block shouldHave {
      case LetSE(_,_,
        AtomSP(_,Some(CaptureS(IteratorNameS(_))),None,None,None),
        FunctionCallSE(_,
          OutsideLoadSE(_,_,CodeNameS("begin"),None,LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadonlyP))),
          Vector(LocalLoadSE(_,IterableNameS(_),LoadAsBorrowP(None))))) =>
    }
    body.block shouldHave {
      case WhileSE(_, _) =>
    }
    body.block shouldHave {
      case LetSE(_,_,
        AtomSP(_,Some(CaptureS(IterationOptionNameS(_))),None,None,None),
        FunctionCallSE(_,
          OutsideLoadSE(_,_,CodeNameS("next"),None,LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadonlyP))),
          Vector(
            LocalLoadSE(_,IteratorNameS(_),LoadAsBorrowP(Some(ReadwriteP)))))) =>
    }
    body.block shouldHave {
      case FunctionCallSE(_,
        OutsideLoadSE(_,_,CodeNameS("isEmpty"),_,_),
        Vector(
          LocalLoadSE(_,IterationOptionNameS(_),LoadAsBorrowP(Some(ReadonlyP))))) =>
    }
    body.block shouldHave {
      case BreakSE(_) =>
    }
    body.block shouldHave {
      case LetSE(_,_,
        AtomSP(_,Some(CaptureS(CodeVarNameS("i"))),None,None,None),
        FunctionCallSE(_,
          OutsideLoadSE(_,_,CodeNameS("get"),None,LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadonlyP))),
          Vector(LocalLoadSE(_,IterationOptionNameS(_),UseP)))) =>
    }
    body.block shouldHave {
      case LocalLoadSE(_,IterationOptionNameS(_),UseP) =>
    }
  }

  test("this isnt special if was explicit param") {
    val program1 = compile(
      """fn moo(this *MyStruct) {
        |  println(this.x);
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("moo")
    Collector.only(main.body, {
      case FunctionCallSE(_,
        OutsideLoadSE(_, _, CodeNameS("println"), _, _),
        Vector(DotSE(_, LocalLoadSE(_, CodeVarNameS("this"), LoadAsBorrowOrIfContainerIsPointerThenPointerP(None)), "x", true))) =>
    })
    Collector.all(main.body, { case FunctionCallSE(_, _, _) => }).size shouldEqual 1
  }

  test("Reports when mutating nonexistant local") {
    val err = compileForError(
      """fn main() int export {
        |  set a = a + 1;
        |}
        |""".stripMargin)
    err match {
      case CouldntFindVarToMutateS(_, "a") =>
    }
  }

  test("Reports when overriding non-kind in param") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |fn func(moo *Moo impl *IMoo) int { ret 73; }
        |""".stripMargin)
    err match {
      case CantOverrideOwnershipped(_) =>
    }
  }

  test("Reports when non-kind interface in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl *IMoo for Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipInterfaceInImpl(_) =>
    }
  }

  test("Reports when extern function has body") {
    val err = compileForError(
      """
        |fn bork() int extern {
        |  3
        |}
        |""".stripMargin)
    err match {
      case ExternHasBody(_) =>
    }
  }

  test("Reports when non-kind struct in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl IMoo for *Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipStructInImpl(_) =>
    }
  }

  test("Reports when we forget set") {
    val err = compileForError(
      """
        |fn main() export {
        |  x = "world!";
        |  x = "changed";
        |}
        |""".stripMargin)
    err match {
      case VariableNameAlreadyExists(_, CodeVarNameS("x")) =>
      case _ => vfail()
    }
  }

  test("Reports when interface method doesnt have self") {
    val err = compileForError("interface IMoo { fn blork(a bool)void; }")
    err match {
      case InterfaceMethodNeedsSelf(_) =>
      case _ => vfail()
    }
  }

  test("Statement after result or return") {
    compileForError(
      """
        |fn doCivicDance(virtual this Car) {
        |  ret 4;
        |  7
        |}
        """.stripMargin) match {
      case StatementAfterReturnS(_) =>
    }
  }
}