package dev.vale.typing

import dev.vale.typing.infer._
import dev.vale.solver.{FailedSolve, RuleError}
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing.ast.{SignatureT, _}
import dev.vale.typing.infer.SendingNonCitizen
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.postparsing._
import dev.vale.typing.types._
import dev.vale.{Collector, Err, Ok, vassert, vwat, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

import scala.io.Source
import OverloadResolver._

class AfterRegionsTests extends FunSuite with Matchers {

  test("Method call on generic data") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |sealed interface IShip {
        |  func launch(virtual self &IShip);
        |}
        |
        |struct Raza { fuel int; }
        |
        |impl IShip for Raza;
        |func launch(self &Raza) { }
        |
        |func launchGeneric<T>(x &T)
        |where implements(T, IShip) {
        |  x.launch();
        |}
        |
        |exported func main() {
        |  launchGeneric(&Raza(42));
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val launchGeneric = coutputs.lookupFunction("launchGeneric")

    val main = coutputs.lookupFunction("main")
    Collector.all(main, { case UpcastTE(_, _, _) => }).size shouldEqual 0
    vimpl()
    //    Collector.all(main, {
    //      case FuncCallTE =>
    //    })
  }

  test("Tests overload set and concept function") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.print.*;
        |import v.builtins.drop.*;
        |import v.builtins.str.*;
        |
        |func moo<X, F>(x X, f F)
        |where func(&F, &X)void, func drop(X)void, func drop(F)void {
        |  f(&x);
        |}
        |exported func main() {
        |  moo("hello", print);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Generic interface anonymous subclass") {
    val compile = CompilerTestCompilation.test(
      """
        |interface Bork<T Ref> {
        |  func bork(virtual self &Bork<T>, x T) int;
        |}
        |
        |exported func main() int {
        |  f = Bork((x) => { 7 });
        |  return f.bork();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Prototype rule to get return type") {
    // i dont think we support this anymore, now that we have generics?

    val compile = CompilerTestCompilation.test(
      """
        |
        |import v.builtins.panic.*;
        |
        |func moo(i int, b bool) str { return "hello"; }
        |
        |exported func main() R
        |where mooFunc Prot = Prot["moo", Refs(int, bool), R Ref] {
        |  __vbi_panic();
        |}
        |
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("main").header.returnType match {
      case CoordT(_,_, StrT()) =>
    }
  }

  test("Can destructure and assemble tuple") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup2.*;
        |import v.builtins.drop.*;
        |
        |func swap<T, Y>(x (T, Y)) (Y, T) {
        |  [a, b] = x;
        |  return (b, a);
        |}
        |
        |exported func main() bool {
        |  return swap((5, true)).0;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()

    coutputs.lookupFunction("main").header.returnType match {
      case CoordT(ShareT, _, BoolT()) =>
    }

//    coutputs.lookupFunction("swap").header.fullName.last.templateArgs.last match {
//      case CoordTemplata(CoordT(ShareT, BoolT())) =>
//    }
  }

  test("Can turn a borrow coord into an owning coord") {
    vimpl()
    // not sure this test ever really tested what it was supposed to.
    // perhaps we wanted a &SomeStruct() instead?

    val compile = CompilerTestCompilation.test(
      """
        |
        |
        |struct SomeStruct { }
        |
        |func bork<T>(x T) ^T {
        |  return SomeStruct();
        |}
        |
        |exported func main() {
        |  bork(SomeStruct());
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("bork").header.id.localName.templateArgs.last match {
      case CoordTemplataT(CoordT(OwnT, _, _)) =>
    }
  }

  // Depends on Method call on generic data
  test("Impl rule") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |
        |interface IShip {
        |  func getFuel(virtual self &IShip) int;
        |}
        |struct Firefly {}
        |func getFuel(self &Firefly) int { return 7; }
        |impl IShip for Firefly;
        |
        |func genericGetFuel<T>(x T) int
        |where implements(T, IShip) {
        |  return x.getFuel();
        |}
        |
        |exported func main() int {
        |  return genericGetFuel(Firefly());
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()
    coutputs.lookupFunction("genericGetFuel").header.id.localName.templateArgs.last match {
      case CoordTemplataT(CoordT(_,_, StructTT(IdT(_,_,StructNameT(StructTemplateNameT(StrI("Firefly")),_))))) =>
    }
  }

  test("Test two instantiations of anonymous-param lambda") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |import v.builtins.logic.*;
        |
        |func doThing<T, F>(func F, a T, b T) bool
        |where func __call(&F, T, T)bool, func drop(F)void {
        |  func(a, b)
        |}
        |
        |exported func main() {
        |  lam = (a, b) => { a == b };
        |  doThing(lam, 7, 8);
        |  doThing(lam, true, false);
        |}
        |
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    val lambdaFuncs =
      coutputs.functions.filter(func => {
        func.header.id.localName.template match {
          case FunctionTemplateNameT(StrI("__call"), _) => true
          case _ => false
        }
      })
    lambdaFuncs.size shouldEqual 2

    // The above test seems to work, but we still have to decide whether we want lambda function
    // instantiations to have different identifying runes, or if we just want to disambiguate
    // by parameters alone.
    // See also "Test one-anonymous-param lambda identifying runes"
    vimpl()

    //    val lamFunc = coutputs.lookupFunction("__call")
    //    lamFunc.header.fullName.last.templateArgs.size shouldEqual 1
    //
    //    val main = coutputs.lookupFunction("main")
    //    val call =
    //      Collector.only(main, { case call @ FunctionCallTE(PrototypeT(FullNameT(_, _, FunctionNameT(FunctionTemplateNameT(StrI("__call"), _), _, _)), _), _) => call })
  }

  test("Test interface default generic argument in type") {
    val compile = CompilerTestCompilation.test(
      """
        |sealed interface MyInterface<K Ref, H Int = 5> { }
        |struct MyStruct {
        |  x MyInterface<bool>;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupStruct("MyStruct")
    val tyype = Collector.only(moo, { case ReferenceMemberTypeT(c) => c })
    tyype match {
      case CoordT(
      OwnT,
      _,
      InterfaceTT(
      IdT(_,_,
      InterfaceNameT(
      InterfaceTemplateNameT(StrI("MyInterface")),
      Vector(
      CoordTemplataT(CoordT(ShareT,_, BoolT())),
      IntegerTemplataT(5)))))) =>
    }
  }

  test("Reports when we give too many args") {
    val compile = CompilerTestCompilation.test(
      """
        |func moo(a int, b bool, s str) int { a }
        |exported func main() int {
        |  moo(42, true, "hello", false)
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      // Err(     case WrongNumberOfArguments(_, _)) =>
      case Err(CouldntFindFunctionToCallT(_, fff)) => {
        vassert(fff.rejectedCalleeToReason.size == 1)
        fff.rejectedCalleeToReason.head._2 match {
          case WrongNumberOfArguments(4, 3) =>
        }
      }
    }
  }

  test("Reports when ownership doesnt match") {
    val compile = CompilerTestCompilation.test(
      """
        |
        |struct Firefly {}
        |func getFuel(self &Firefly) int { return 7; }
        |
        |exported func main() int {
        |  f = Firefly();
        |  return (f).getFuel();
        |}
        |""".stripMargin
    )
    compile.getCompilerOutputs() match {
      case Err(CouldntFindFunctionToCallT(range, fff)) => {
        fff.name match {
          case CodeNameS(StrI("getFuel")) =>
        }
        fff.rejectedCalleeToReason.size shouldEqual 1
        val reason = fff.rejectedCalleeToReason.head._2
        reason match {
          case InferFailure(FailedCompilerSolve(_, _, RuleError(OwnershipDidntMatch(CoordT(OwnT, _, _), BorrowT)))) =>
          //          case SpecificParamDoesntSend(0, _, _) =>
          case other => vfail(other)
        }
      }
    }
  }

  test("Failure to resolve a Prot rule's function doesnt halt") {
    // In the below example, it should disqualify the first foo() because T = bool
    // and there exists no moo(bool). Instead, we saw the Prot rule throw and halt
    // compilation.

    // Instead, we need to bubble up that failure to find the right function, so
    // it disqualifies the candidate and goes with the other one.

    // Note from later: It seems this isn't detected by the typing phase anymore.
    // When we try to resolve a func moo(str)void, we actually find one in the overload index,
    // specifically foo.bound:moo(str).
    // Obviously we shouldnt be considering that.
    // Normally, bounds have some sort of placeholder type that acts as a filter so people don't
    // see them unless they have that placeholder type. Here, not so much.

    // We can solve this in two ways:
    // - Making a visibility mask for various overloads in the overload set. This one is only visible from foo,
    //   so when we try to resolve it from main it wont be found.
    // - Require all bounds have a placeholder type in them. Seems reasonable tbh.

    CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |
        |func moo(a str) { }
        |func foo<T>(f T) void where func drop(T)void, func moo(str)void { }
        |func foo<T>(f T) void where func drop(T)void, func moo(bool)void { }
        |func main() { foo("hello"); }
        |""".stripMargin).expectCompilerOutputs()
  }

  // Depends on IFunction1, and maybe Generic interface anonymous subclass
  test("Basic IFunction1 anonymous subclass") {
    val compile = CompilerTestCompilation.test(
      """
        |import ifunction.ifunction1.*;
        |
        |exported func main() int {
        |  f = IFunction1<mut, int, int>({_});
        |  return (f)(7);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }
}
