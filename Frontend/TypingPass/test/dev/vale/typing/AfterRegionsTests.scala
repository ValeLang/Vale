package dev.vale.typing

import dev.vale.solver.{FailedSolve, RuleError}
import dev.vale.typing.OverloadResolver.InferFailure
import dev.vale.typing.ast.{SignatureT, _}
import dev.vale.typing.infer.SendingNonCitizen
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Collector, Err, Ok, vassert, vwat, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

import scala.io.Source

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
        |  launchGeneric(Raza(42));
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

  test("Prototype rule to get return type") {
    // i dont think we support this anymore, now that we have generics?

    val compile = CompilerTestCompilation.test(
      """
        |
        |import v.builtins.panicutils.*;
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
      case CoordT(_,_,StrT()) =>
    }
  }

  test("Can destructure and assemble tuple") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.tup.*;
        |import v.builtins.drop.*;
        |
        |func swap<T, Y>(x (T, Y)) (Y, T) {
        |  [a, b] = x;
        |  return (b, a);
        |}
        |
        |exported func main() bool {
        |  return swap((5, true)).a;
        |}
        |""".stripMargin
    )
    val coutputs = compile.expectCompilerOutputs()

    coutputs.lookupFunction("main").header.returnType match {
      case CoordT(ShareT, _,BoolT()) =>
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
      case CoordTemplataT(CoordT(OwnT, _,_)) =>
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
      case CoordTemplataT(CoordT(_,_,StructTT(IdT(_,_,StructNameT(StructTemplateNameT(StrI("Firefly")),_))))) =>
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

  test("Test struct default generic argument in call") {
    val compile = CompilerTestCompilation.test(
      """
        |struct MyHashSet<K Ref, H Int = 5> { }
        |func moo() {
        |  x = MyHashSet<bool>();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val moo = coutputs.lookupFunction("moo")
    val variable = Collector.only(moo, { case LetNormalTE(v, _) => v })
    variable.coord match {
      case CoordT(
      OwnT,
      _,
      StructTT(
      IdT(_,_,
      StructNameT(
      StructTemplateNameT(StrI("MyHashSet")),
      Vector(
      CoordTemplataT(CoordT(ShareT,_,BoolT())),
      IntegerTemplataT(5)))))) =>
    }
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
      CoordTemplataT(CoordT(ShareT,_,BoolT())),
      IntegerTemplataT(5)))))) =>
    }
  }

}
