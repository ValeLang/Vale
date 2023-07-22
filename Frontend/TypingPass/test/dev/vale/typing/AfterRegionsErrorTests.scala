package dev.vale.typing

import dev.vale.solver.{FailedSolve, RuleError}
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing.ast._
import dev.vale.typing.infer.SendingNonCitizen
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.typing.citizen._
import dev.vale.typing.expression._
import dev.vale.{Collector, Err, Ok, vwat, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

class AfterRegionsErrorTests extends FunSuite with Matchers {

  test("Prints bread crumb trail") {
    val compile = CompilerTestCompilation.test(
      """
        |import printutils.*;
        |import v.builtins.panicutils.*;
        |
        |#!DeriveInterfaceDrop
        |sealed interface Opt<T> where T Ref { }
        |#!DeriveStructDrop
        |struct Some<T> where T Ref { value T; }
        |
        |impl<T> Opt<T> for Some<T>;
        |#!DeriveStructDrop
        |struct None<T> where T Ref { }
        |
        |impl<T> Opt<T> for None<T>;
        |
        |abstract func drop<T>(virtual opt Opt<T>)
        |where func drop(T)void;
        |
        |func drop<T>(opt Some<T>)
        |where func drop(T)void
        |{
        |  [x] = opt;
        |}
        |
        |func drop<T>(opt None<T>) {
        |  [ ] = opt;
        |}
        |
        |abstract func isEmpty<T>(virtual opt &Opt<T>) bool;
        |func isEmpty<T>(opt &None<T>) bool { return true; }
        |func isEmpty<T>(opt &Some<T>) bool { return false; }
        |
        |abstract func isEmpty<T>(virtual opt Opt<T>) bool;
        |func isEmpty<T>(opt None<T>) bool { return true; }
        |func isEmpty<T>(opt Some<T>) bool
        |where func drop(T)void
        |{ return false; }
        |
        |abstract func get<T>(virtual opt Opt<T>) T;
        |func get<T>(opt None<T>) T { panic("Called get() on a None!"); }
        |func get<T>(opt Some<T>) T {
        |  [value] = opt;
        |  return value;
        |}
        |
        |abstract func get<T>(virtual opt &Opt<T>) &T;
        |func get<T>(opt &None<T>) &T { panic("Called get() on a None!"); }
        |func get<T>(opt &Some<T>) &T { return &opt.value; }
        |
        |
        |#!DeriveStructDrop
        |struct MyList<T Ref> {
        |  value T;
        |  next Opt<MyList<T>>;
        |}
        |
        |func drop<T>(this MyList<T>)
        |where func drop(T)void {
        |  [value, next] = this;
        |}
        |
        |func printValues(list &MyList<int>) void {
        |  print(list.value);
        |  printNextValue(list.next);
        |}
        |
        |func printNextValue(virtual opt &Opt<MyList<int>>) void { }
        |func printNextValue(opt &None<MyList<int>>) void { }
        |func printNextValue(opt &Some<MyList<int>>) void {
        |  printValues(opt.value);
        |}
        |
        |
        |exported func main() int {
        |  list = MyList<int>(10, Some<MyList<int>>(MyList<int>(20, Some<MyList<int>>(MyList<int>(30, None<MyList<int>>())))));
        |  printValues(&list);
        |  return 0;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    // Ensure it properly prints out that the original error is from isEmpty
    // Also prune it down a bit
    vimpl()
  }

  // Depends on Basic interface anonymous subclass
  test("Reports error") {
    // https://github.com/ValeLang/Vale/issues/548

    val compile = CompilerTestCompilation.test(
      """
        |interface A {
        |	func foo(virtual a &A) int;
        |}
        |
        |struct B imm { val int; }
        |impl A for B;
        |
        |func foo(b &B) int { return b.val; }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    vimpl()
  }

  // right now there is no collision because they have different template names.
  test("Reports when two functions with same signature") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func moo() int { return 1337; }
        |exported func moo() int { return 1448; }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(FunctionAlreadyExists(_, _, IdT(_, Vector(), null))) =>
//      case Err(FunctionAlreadyExists(_, _, FullNameT(_, Vector(), FunctionTemplateNameT(StrI("moo"), _)))) =>
    }
  }

  // Interface bounds, downcasting
  test("Report when downcasting to interface") {
    vimpl() // can we solve this by putting an impl in the environment for that placeholder?

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISuper { }
        |interface ISub { }
        |impl ISuper for ISub;
        |
        |exported func main() {
        |  ship = __pretend<ISuper>();
        |  ship.as<ISub>();
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(CantDowncastToInterface(_, _)) =>
    }
  }

  test("Report when downcasting between unrelated types") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.as.*;
        |import panicutils.*;
        |
        |interface ISpaceship { }
        |struct Spoon { }
        |
        |exported func main() {
        |  ship = __pretend<ISpaceship>();
        |  ship.as<Spoon>();
        |}
        |""".stripMargin)
    compile.expectCompilerOutputs()
    vimpl()
    //    compile.getCompilerOutputs() match {
    //      case Err(CantDowncastUnrelatedTypes(_, _, _, _)) =>
    //    }
  }

  // Depends on Generic interface anonymous subclass
  test("Lambda is incompatible anonymous interface") {
    val compile = CompilerTestCompilation.test(
      """
        |interface AFunction1<P Ref> {
        |  func __call(virtual this &AFunction1<P>, a P) int;
        |}
        |exported func main() {
        |  arr = AFunction1<int>((_) => { 4 });
        |}
        |""".stripMargin)

    compile.getCompilerOutputs() match {
      case Err(BodyResultDoesntMatch(_, _, _, _)) =>
      case Err(other) => {
        val codeMap = compile.getCodeMap().getOrDie()
        vwat(
          CompilerErrorHumanizer.humanize(
          true,
          SourceCodeUtils.humanizePos(codeMap, _),
          SourceCodeUtils.linesBetween(codeMap, _, _),
          SourceCodeUtils.lineRangeContaining(codeMap, _),
          SourceCodeUtils.lineContaining(codeMap, _),
          other))
      }
      case Ok(wat) => vwat(wat)
    }
  }

  test("Detects sending non-citizen to citizen") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |interface MyInterface {}
        |func moo<T>(a T)
        |where implements(T, MyInterface)
        |{ }
        |exported func main() {
        |  moo(7);
        |}
        |""".stripMargin
    )
    compile.getCompilerOutputs() match {
      case Err(CouldntFindFunctionToCallT(range, fff)) => {
        fff.rejectedCalleeToReason.map(_._2).head match {
          case InferFailure(reason) => {
            reason match {
              case FailedCompilerSolve(_, _, RuleError(SendingNonCitizen(IntT(32)))) =>
              case other => vfail(other)
            }
          }
        }
      }
    }
  }

  test("Abstract func without virtual") {
    val compile = CompilerTestCompilation.test(
      """
        |sealed interface ISpaceship<X Ref, Y Ref, Z Ref> { }
        |abstract func launch<X, Y, Z>(self &ISpaceship<X, Y, Z>, bork X) where func drop(X)void;
        |
        |exported func main() int {
        |  a = #[](10, {_});
        |  return a.3;
        |}
    """.stripMargin)

    compile.getCompilerOutputs() match {
      case Err(e) => vimpl(e)
      case Ok(_) => vfail()
    }
  }

  test("Cant make non-weakable extend a weakable") {
    val compile = CompilerTestCompilation.test(
      """
        |weakable interface IUnit {}
        |struct Muta { hp int; }
        |impl IUnit for Muta;
        |func main(muta Muta) int  { return 7; }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(false, true) =>
      case other => {
        other.printStackTrace()
        vfail()
      }
    }
  }


  test("Cant make weakable extend a non-weakable") {
    val compile = CompilerTestCompilation.test(
      """
        |interface IUnit {}
        |weakable struct Muta { hp int; }
        |impl IUnit for Muta;
        |func main(muta Muta) int  { return 7; }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case WeakableImplingMismatch(true, false) =>
      case _ => vfail()
    }
  }
  test("Cant make weak ref to non-weakable") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Muta { hp int; }
        |func getHp(weakMuta &&Muta) { (lock(weakMuta)).get().hp }
        |exported func main() int { getHp(&&Muta(7)) }
        |""".stripMargin)

    try {
      compile.expectCompilerOutputs().lookupFunction("main")
      vfail()
    } catch {
      case TookWeakRefOfNonWeakableError() =>
      case _ => vfail()
    }

  }

}
