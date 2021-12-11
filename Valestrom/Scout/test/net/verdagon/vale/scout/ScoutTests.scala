package net.verdagon.vale.scout

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{Collector, Err, FileCoordinate, Ok, vassert, vfail, vimpl, vwat}
import net.verdagon.von.{JsonSyntax, VonPrinter}
import org.scalatest.{FunSuite, Matchers}

class ScoutTests extends FunSuite with Matchers with Collector {
  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case ParseFailure(error) => vwat(error.toString)
            case ParseSuccess(program0) => program0
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
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(firstProgram0) => {
        val von = ParserVonifier.vonifyFile(firstProgram0)
        val vpstJson = new VonPrinter(JsonSyntax, 120).print(von)
        val program0 =
          ParsedLoader.load(vpstJson) match {
            case ParseFailure(error) => vwat(error.toString)
            case ParseSuccess(program0) => program0
          }
        new Scout(GlobalOptions.test()).scoutProgram(FileCoordinate.test, program0) match {
          case Err(e) => e
          case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
        }
      }
    }
  }

  test("Lookup +") {
    val program1 = compile("fn main() int export { +(3, 4) }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block match {
      case BlockSE(_, _, Vector(FunctionCallSE(_, OutsideLoadSE(_, _, CodeNameS("+"), _, _), _))) =>
    }
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
    val program1 = compile("fn main() int export { {_ + _}!(4, 6) }")

    val CodeBodyS(BodySE(_, _, BlockSE(_, _, Vector(expr)))) = program1.lookupFunction("main").body
    val FunctionCallSE(_, OwnershippedSE(_,FunctionSE(lambda@FunctionS(_, _, _, _, _, _, _, _, _)), LendConstraintP(Some(ReadwriteP))), _) = expr
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
    val program1 = compile("fn main() int export { x = 4; = x.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block match {
      case BlockSE(_, _, Vector(_, FunctionCallSE(_, OutsideLoadSE(_, _, CodeNameS("shout"), _, _), Vector(LocalLoadSE(_, name, LendConstraintP(Some(ReadonlyP))))))) => {
        name match {
          case CodeVarNameS("x") =>
        }
      }
    }
  }

  test("Moving method call") {
    val program1 = compile("fn main() int export { x = 4; = (x).shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block match {
      case BlockSE(_, _, Vector(_, FunctionCallSE(_, OutsideLoadSE(_, _, CodeNameS("shout"), _, _),Vector(LocalLoadSE(_,CodeVarNameS("x"), UseP))))) =>
    }
  }

  test("Function with magic lambda and regular lambda") {
    // There was a bug that confused the two, and an underscore would add a magic param to every lambda after it

    val program1 =
      compile(
        """fn main() int export {
          |  {_};
          |  (a){a};
          |}
        """.stripMargin)
    val main = program1.lookupFunction("main")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    val BlockSE(_, _, things) = block
    val lambda1 = things(0).asInstanceOf[FunctionSE].function
    val lambda2 = things(1).asInstanceOf[FunctionSE].function
    lambda1.params match {
      case Vector(_, ParameterS(AtomSP(_, Some(CaptureS(MagicParamNameS(_))), None, Some(RuneUsage(_, MagicParamRuneS(_))), None))) =>
    }
    lambda2.params match {
      case Vector(_, ParameterS(AtomSP(_, Some(CaptureS(CodeVarNameS("a"))), None, Some(RuneUsage(_, ImplicitRuneS(_))), None))) =>
//      case Vector(_, ParameterS(AtomSP(_, Some(CaptureS(CodeVarNameS(a))), None, None, None))) =>
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
    block match {
      case BlockSE(_,
      Vector(
      LocalS(ConstructingMemberNameS("x"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed),
      LocalS(ConstructingMemberNameS("y"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
      Vector(
      LetSE(_,
      _,
      AtomSP(_, Some(CaptureS(ConstructingMemberNameS("x"))), None, _, None),
      ConstantIntSE(_, 4, _)),
      LetSE(_,
      _,
      AtomSP(_, Some(CaptureS(ConstructingMemberNameS("y"))), None, _, None),
      ConstantBoolSE(_, true)),
      FunctionCallSE(_,
      OutsideLoadSE(_, _, CodeNameS("MyStruct"), _, _),
      Vector(
      LocalLoadSE(_, ConstructingMemberNameS("x"), UseP),
      LocalLoadSE(_, ConstructingMemberNameS("y"), UseP))))) =>
    }
  }

  test("Forgetting set when changing") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = Spaceship(10);
        |  ship.x = 4;
        |}
        |""".stripMargin)
    error match {
      case ForgotSetKeywordError(_) =>
    }
  }

  test("Cant use set as a local name") {
    val error = compileForError(
      """fn moo() {
        |  (set) = [6];
        |}
        |""".stripMargin)
    error match {
      case CantUseThatLocalName(_, "set") =>
    }
  }

  test("CantInitializeIndividualElementsOfRuntimeSizedArray") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [*][4, 5, 6];
        |}
        |""".stripMargin)
    error match {
      case CantInitializeIndividualElementsOfRuntimeSizedArray(_) =>
    }
  }

  test("InitializingRuntimeSizedArrayRequiresSizeAndCallable too few") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [*](4);
        |}
        |""".stripMargin)
    error match {
      case InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingRuntimeSizedArrayRequiresSizeAndCallable too many") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [*](4, {_}, 10);
        |}
        |""".stripMargin)
    error match {
      case InitializingRuntimeSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayRequiresSizeAndCallable too few") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [5]();
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayRequiresSizeAndCallable too many") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = [5](4, {_});
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayRequiresSizeAndCallable(_) =>
    }
  }

  test("InitializingStaticSizedArrayFromCallableNeedsSizeTemplex") {
    val error = compileForError(
      """fn MyStruct() {
        |  ship = []({_});
        |}
        |""".stripMargin)
    error match {
      case InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(_) =>
    }
  }

  test("Test loading from member") {
    val program1 = compile(
      """fn MyStruct() {
        |  moo.x
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block match {
      case BlockSE(
      _,Vector(),
      Vector(
      DotSE(_,OutsideLoadSE(_,_,CodeNameS("moo"),None,LendConstraintP(None)),x,true))) =>
    }

  }

  test("Test loading from member 2") {
    val program1 = compile(
      """fn MyStruct() {
        |  *moo.x
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBodyS(BodySE(_, _, block)) = main.body
    block match {
      case BlockSE(
      _,Vector(),
      Vector(
      OwnershippedSE(_,
      DotSE(_,OutsideLoadSE(_,_,CodeNameS("moo"),None,LendConstraintP(None)),x,true),LendConstraintP(Some(ReadonlyP))))) =>
    }

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
    block match {
      case BlockSE(_,
      Vector(
      LocalS(ConstructingMemberNameS("x"), Used, Used, NotUsed, NotUsed, NotUsed, NotUsed),
      LocalS(ConstructingMemberNameS("y"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
      Vector(
      LetSE(_, _,
      AtomSP(_, Some(CaptureS(ConstructingMemberNameS("x"))), None, _, None),
      ConstantIntSE(_, 4, _)),
      LetSE(_, _,
      AtomSP(_, Some(CaptureS(ConstructingMemberNameS("y"))), None, _, None),
      LocalLoadSE(_, ConstructingMemberNameS("x"), LendConstraintP(Some(ReadonlyP)))),
      FunctionCallSE(_,
      OutsideLoadSE(_, _, CodeNameS("MyStruct"), _, _),
      Vector(
      LocalLoadSE(_, ConstructingMemberNameS("x"), UseP),
      LocalLoadSE(_, ConstructingMemberNameS("y"), UseP))))) =>
    }
  }

  test("this isnt special if was explicit param") {
    val program1 = compile(
      """fn moo(this *MyStruct) {
        |  println(this.x);
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("moo")
    main.body match {
      case CodeBodyS(
      BodySE(_,
      Vector(),
      BlockSE(_,
      Vector(LocalS(CodeVarNameS("this"), Used, NotUsed, NotUsed, NotUsed, NotUsed, NotUsed)),
      Vector(
      FunctionCallSE(_,
      OutsideLoadSE(_, _, CodeNameS("println"), _, _),
      Vector(DotSE(_, LocalLoadSE(_, CodeVarNameS("this"), LendConstraintP(None)), "x", true))),
      VoidSE(_))))) =>
    }
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
        |fn func(moo *Moo impl *IMoo) int { 73 }
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
}