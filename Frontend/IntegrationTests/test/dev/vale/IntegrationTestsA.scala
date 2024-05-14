package dev.vale

import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.passmanager.{FullCompilation, FullCompilationOptions}
import dev.vale.finalast.{IdH, IntHT, OwnH, ProgramH, PrototypeH, YonderH}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing._
import dev.vale.typing.ast._
import dev.vale.instantiating.ast._
import dev.vale.typing.names._
import dev.vale.typing.{HinputsT, ICompileErrorT, ast}
import dev.vale.typing.types._
import dev.vale.testvm.{ConstraintViolatedException, Heap, IntV, PrimitiveKindV, ReferenceV, StructInstanceV, Vivem}
import dev.vale.highertyping.ICompileErrorA

import java.io.FileNotFoundException
import dev.vale.typing.ast
import dev.vale.{finalast => m}
import dev.vale.testvm.ReferenceV
import org.scalatest._
import dev.vale.passmanager.FullCompilation
import dev.vale.finalast.IdH
import dev.vale.instantiating.ast.HinputsI
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.postparsing.ICompileErrorS
import dev.vale.typing.ast._
import dev.vale.typing.types.StrT
import dev.vale.von.{IVonData, VonBool, VonFloat, VonInt}

import scala.collection.immutable.List


class IntegrationTestsA extends FunSuite with Matchers {
  //  test("Scratch scratch") {
  //    val compile =
  //      RunCompilation.test(
  //        """
  //          |scratch code here
  //          |""".stripMargin)
  //    compile.evalForKind(Vector())
  //  }

  test("Simple program returning an int") {
    val compile = RunCompilation.test("exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Simple program with drop") {
    val compile = RunCompilation.test("import v.builtins.drop.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with arith") {
    val compile = RunCompilation.test("import v.builtins.arith.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with logic") {
    val compile = RunCompilation.test("import v.builtins.logic.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with migrate") {
    val compile = RunCompilation.test("import v.builtins.migrate.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with str") {
    val compile = RunCompilation.test("import v.builtins.str.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with arrays") {
    val compile = RunCompilation.test("import v.builtins.arrays.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with mainargs") {
    val compile = RunCompilation.test("import v.builtins.mainargs.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with as") {
    val compile = RunCompilation.test("import v.builtins.as.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with print") {
    val compile = RunCompilation.test("import v.builtins.print.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with tup") {
    val compile = RunCompilation.test("import v.builtins.tup2.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with panic") {
    val compile = RunCompilation.test("import v.builtins.panic.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with opt") {
    val compile = RunCompilation.test("import v.builtins.opt.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with result") {
    val compile = RunCompilation.test("import v.builtins.result.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with sameinstance") {
    val compile = RunCompilation.test("import v.builtins.sameinstance.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }
  test("Simple program with weak") {
    val compile = RunCompilation.test("import v.builtins.weak.*; exported func main() int { return 3; }", false)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Hardcoding negative numbers") {
    val compile = RunCompilation.test("exported func main() int { return -3; }")
    compile.evalForKind(Vector()) match { case VonInt(-3) => }
  }

  test("Taking an argument and returning it") {
    val compile = RunCompilation.test("exported func main(a int) int { return a; }")
    compile.evalForKind(Vector(IntV(5, 32))) match { case VonInt(5) => }
  }

  test("Tests adding two numbers") {
    val compile = RunCompilation.test("exported func main() int { return +(2, 3); }")
    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Tests adding two floats") {
    val compile = RunCompilation.test("exported func main() float { return +(2.5, 3.5); }")
    compile.evalForKind(Vector()) match { case VonFloat(6.0f) => }
  }

  test("Tests inline adding") {
    val compile = RunCompilation.test("exported func main() int { return 2 + 3; }")
    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Test constraint ref") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/constraintRef.vale"))
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }

  test("Test borrow ref") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/borrowRef.vale"))
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }

  test("Tests inline adding more") {
    val compile = RunCompilation.test("exported func main() int { return 2 + 3 + 4 + 5 + 6; }")
    compile.evalForKind(Vector()) match { case VonInt(20) => }
  }

  test("Simple lambda") {
    val compile = RunCompilation.test("exported func main() int { return {7}(); }")
    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Lambda with one magic arg") {
    val compile = RunCompilation.test("exported func main() int { return {_}(3); }")
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = RunCompilation.test("exported func main() int { return (a int) => { return +(a,a); }(3); }");
    compile.evalForKind(Vector()) match { case VonInt(6) => }
  }

  test("Test overloads") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/functions/overloads.vale"))
    compile.evalForKind(Vector()) match { case VonInt(6) => }
  }


  test("Test block") {
    val compile = RunCompilation.test("exported func main() int {true; 200; return 300;}")
    compile.evalForKind(Vector()) match { case VonInt(300) => }
  }

  test("Test generic") {
    val compile = RunCompilation.test(
      """
        |func drop(x int) { }
        |func bork<T>(a T) void where func drop(T)void {
        |  // implicitly calls drop
        |}
        |exported func main() {
        |  bork(3);
        |}
      """.stripMargin, false)
    compile.evalForKind(Vector())
  }

  test("Test multiple invocations of generic") {
    val compile = RunCompilation.test(
      """
        |func bork<T>(a T, b T) T where func drop(T)void { return a; }
        |exported func main() int {true bork false; 2 bork 2; return 3 bork 3;}
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Test mutating a local var") {
    val compile = RunCompilation.test("exported func main() {a = 3; set a = 4; }")
    compile.run(Vector())
  }

  test("Test returning a local mutable var") {
    val compile = RunCompilation.test("exported func main() int {a = 3; set a = 4; return a;}")
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Test taking a callable param") {
    val compile = RunCompilation.test(
      """
        |func do<T>(callable T) int where func(&T)int, func drop(T)void { return callable(); }
        |exported func main() int { return do({ 3 }); }
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Stamps an interface template via a function parameter") {
    val compile = RunCompilation.test(
      """
        |interface MyInterface<T Ref> { }
        |func doAThing<T>(i MyInterface<T>) { }
        |
        |struct SomeStruct<T Ref> { }
        |func doAThing<T>(s SomeStruct<T>) { }
        |impl<T> MyInterface<T> for SomeStruct<T>;
        |
        |export MyInterface<int> as SomeIntInterface;
        |export SomeStruct<int> as SomeIntStruct;
        |
        |exported func main(a SomeStruct<int>) {
        |  doAThing<int>(a);
        |}
      """.stripMargin)
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner, compile.keywords))
    val heap = new Heap(System.out)
    val ref =
      heap.add(OwnH, YonderH, StructInstanceV(
        packageH.lookupStruct("SomeStruct<i32>"),
        Some(Vector())))
    compile.run(heap, Vector(ref))
  }

  test("Tests unstackifying a variable multiple times in a function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/multiUnstackify.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Reads a struct member") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |exported func main() int { ms = MyStruct(7); return ms.a; }
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Add two i64") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/add64ret.vale"))
    val coutputs = compile.getCompilerOutputs()
    val hamuts = compile.getHamuts()
    compile.evalForKind(Vector()) match { case VonInt(42L) => }
  }

  test("=== true") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |exported func main() bool {
        |  a = MyStruct(7);
        |  return &a === &a;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }

  test("=== false") {
    val compile = RunCompilation.test(
      """
        |struct MyStruct { a int; }
        |exported func main() bool {
        |  a = MyStruct(7);
        |  b = MyStruct(7);
        |  return &a === &b;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonBool(false) => }
  }

  // See LCCSL
  test("Lambda can call sibling lambda") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  continueF = (x) => { x };
        |  barkF = (x) => { continueF(x) };
        |  return barkF(42);
        |}
    """.stripMargin)
    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("set swapping locals") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/mutswaplocals.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  // Known failure 2020-08-20
  // The reason this isnt working:
  // The InterfaceCall2 instruction is only ever created as part of an abstract function's body.
  // (Yes, abstract functions have a body, specifically only containing an InterfaceCall2 on the param)
  // So, if there's *already* a body there, we won't be making the InterfaceCall2 instruction.
  // Short term, let's disallow default implementations.
//  test("Tests virtual doesn't get called if theres a better override") {
//    val compile = RunCompilation.test(
//      """
//        |interface MyOption { }
//        |
//        |struct MySome {
//        |  value MyList;
//        |}
//        |impl MyOption for MySome;
//        |
//        |struct MyNone { }
//        |impl MyOption for MyNone;
//        |
//        |
//        |struct MyList {
//        |  value int;
//        |  next MyOption;
//        |}
//        |
//        |func sum(list *MyList) int {
//        |  list.value + sum(list.next)
//        |}
//        |
//        |func sum(virtual opt *MyOption) int { panic("called virtual sum!") }
//        |func sum(opt *MyNone impl MyOption) int { return 0; }
//        |func sum(opt *MySome impl MyOption) int {
//        |   sum(opt.value)
//        |}
//        |
//        |
//        |exported func main() int {
//        |  list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
//        |  return sum(&list);
//        |}
//        |
//        |""".stripMargin)
//    val hamuts = compile.getHamuts();
//    compile.evalForKind(Vector()) match { case VonInt(60) => }
//  }

}
