package net.verdagon.vale

import java.io.FileNotFoundException
import net.verdagon.vale.templar._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.vivem.{ConstraintViolatedException, Heap, IntV, StructInstanceV}
import net.verdagon.von.{VonBool, VonFloat, VonInt, VonObject}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.hammer.VonHammer
import net.verdagon.vale.metal.{FullNameH, IntH, ReadonlyH, ReadwriteH, YonderH}
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Readonly, Share, Str2}

import scala.io.Source
import scala.util.Try

class IntegrationTestsA extends FunSuite with Matchers {
  test("Simple program returning an int") {
    val compile = Compilation("fn main() int export {3}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Hardcoding negative numbers") {
    val compile = Compilation("fn main() int export {-3}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(-3)
  }

  test("Taking an argument and returning it") {
    val compile = Compilation("fn main(a int) int {a}")
    compile.evalForReferend(Vector(IntV(5))) shouldEqual VonInt(5)
  }

  test("Tests adding two numbers") {
    val compile = Compilation("fn main() int export { +(2, 3) }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Tests adding two floats") {
    val compile = Compilation("fn main() float { +(2.5, 3.5) }")
    compile.evalForReferend(Vector()) shouldEqual VonFloat(6.0f)
  }

  test("Tests inline adding") {
    val compile = Compilation("fn main() int export { 2 + 3 }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Test constraint ref") {
    val compile = Compilation(Samples.get("programs/constraintRef.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Tests inline adding more") {
    val compile = Compilation("fn main() int export { 2 + 3 + 4 + 5 + 6 }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(20)
  }

  test("Simple lambda") {
    val compile = Compilation("fn main() int export {{7}()}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Lambda with one magic arg") {
    val compile = Compilation("fn main() int export {{_}(3)}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = Compilation("fn main() int export {(a int){ +(a,a)}(3)}");
    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }

  test("Test overloads") {
    val compile = Compilation(Samples.get("programs/functions/overloads.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }

  test("Test block") {
    val compile = Compilation("fn main() int export {true; 200; = 300;}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(300)
  }

  test("Test templates") {
    val compile = Compilation(
      """
        |fn ~<T>(a T, b T) T { a }
        |fn main() int export {true ~ false; 2 ~ 2; = 3 ~ 3;}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Test mutating a local var") {
    val compile = Compilation("fn main() {a! = 3; mut a = 4; }")
    compile.run(Vector())
  }

  test("Test returning a local mutable var") {
    val compile = Compilation("fn main() int export {a! = 3; mut a = 4; = a;}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Test taking a callable param") {
    val compile = Compilation(
      """
        |fn do(callable) infer-ret {callable()}
        |fn main() int export {do({ 3 })}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Stamps an interface template via a function parameter") {
    val compile = Compilation(
      """
        |interface MyInterface<T> rules(T Ref) { }
        |fn doAThing<T>(i MyInterface<T>) { }
        |
        |struct SomeStruct<T> rules(T Ref) { }
        |fn doAThing<T>(s SomeStruct<T>) { }
        |impl<T> MyInterface<T> for SomeStruct<T>;
        |
        |fn main(a SomeStruct<int>) {
        |  doAThing<int>(a);
        |}
      """.stripMargin)
    val hamuts = compile.getHamuts()
    val heap = new Heap(System.out)
    val ref =
      heap.add(m.OwnH, YonderH, ReadwriteH, StructInstanceV(
        hamuts.lookupStruct("SomeStruct"),
        Some(Vector())))
    compile.run(heap, Vector(ref))
  }

  test("Tests unstackifying a variable multiple times in a function") {
    val compile = Compilation(Samples.get("programs/multiUnstackify.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Reads a struct member") {
    val compile = Compilation(
      """
        |struct MyStruct { a int; }
        |fn main() int export { ms = MyStruct(7); = ms.a; }
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("=== true") {
    val compile = Compilation(
      """
        |struct MyStruct { a int; }
        |fn main() bool export {
        |  a = MyStruct(7);
        |  = &a === &a;
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("=== false") {
    val compile = Compilation(
      """
        |struct MyStruct { a int; }
        |fn main() bool export {
        |  a = MyStruct(7);
        |  b = MyStruct(7);
        |  = &a === &b;
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(false)
  }

  test("mut swapping locals") {
    val compile = Compilation(Samples.get("programs/mutswaplocals.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("imm tuple access") {
    val compile = Compilation(Samples.get("programs/immtupleaccess.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  // Known failure 2020-08-20
  test("Tests virtual doesn't get called if theres a better override") {
    val compile = Compilation(
      """
        |interface MyOption { }
        |
        |struct MySome {
        |  value MyList;
        |}
        |impl MyOption for MySome;
        |
        |struct MyNone { }
        |impl MyOption for MyNone;
        |
        |
        |struct MyList {
        |  value int;
        |  next MyOption;
        |}
        |
        |fn sum(list &MyList) int {
        |  list.value + sum(list.next)
        |}
        |
        |fn sum(virtual opt &MyOption) int { 0 }
        |fn sum(opt &MyNone impl MyOption) int { 0 }
        |fn sum(opt &MySome impl MyOption) int {
        |   sum(opt.value)
        |}
        |
        |
        |fn main() int export {
        |  list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
        |  = sum(&list);
        |}
        |
        |""".stripMargin)
    val hamuts = compile.getHamuts();
    compile.evalForReferend(Vector()) shouldEqual VonInt(60)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = Compilation(
      """
        |struct MyThing { value int; }
        |fn moo() MyThing { MyThing(4) }
        |fn main() { moo(); }
      """.stripMargin)
    compile.run(Vector())
  }

  test("Tests calling a templated struct's constructor") {
    val compile = Compilation(
      """
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() int export {
        |  MySome<int>(4).value
        |}
      """.stripMargin)
    compile.evalForReferend(Vector())
  }

  test("Test int generic") {
    val compile = Compilation(
      """
        |
        |struct Vec<N, T> rules(N int)
        |{
        |  values [<imm> N * T];
        |}
        |
        |fn main() int export {
        |  v = Vec<3, int>([3, 4, 5]);
        |  = v.values.2;
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = Compilation(Samples.get("programs/virtuals/upcasting.vale"))
    compile.run(Vector())
  }

  test("Tests upcasting from if") {
    val compile = Compilation(Samples.get("programs/if/upcastif.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Tests from file") {
    val compile = Compilation(Samples.get("programs/lambdas/doubleclosure.vale"))
    compile.run(Vector())
  }

  test("Tests from subdir file") {
    val compile = Compilation(Samples.get("programs/virtuals/round.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Tests calling a virtual function") {
    val compile = Compilation(Samples.get("programs/virtuals/calling.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = Compilation(
      """
        |interface MyOption<T> rules(T Ref) { }
        |
        |struct MySome<T> rules(T Ref) {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |fn doSomething(opt MyOption<int>) int {
        |  = 9;
        |}
        |
        |fn main() int export {
        |	x MyOption<int> = MySome<int>();
        |	= doSomething(x);
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }


  test("Tests a linked list") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      Samples.get("programs/virtuals/ordinarylinkedlist.vale"))
    compile.evalForReferend(Vector())
  }

  test("Tests a templated linked list") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("programs/genericvirtuals/templatedlinkedlist.vale"))
    compile.evalForReferend(Vector())
  }

  test("Tests calling an abstract function") {
    val compile = Compilation(Samples.get("programs/genericvirtuals/callingAbstract.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Template overrides are stamped") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("programs/genericvirtuals/templatedoption.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(1)
  }

  test("Tests a foreach for a linked list") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("programs/genericvirtuals/foreachlinkedlist.vale"))
    compile.evalForStdout(Vector()) shouldEqual "102030"
  }

  // When we call a function with a virtual parameter, try stamping for all ancestors in its
  // place.
  // We're stamping all ancestors, and all ancestors have virtual.
  // Virtual starts a function family.
  // So, this checks that it and its three ancestors are all stamped and all get their own
  // function families.
  test("Stamp multiple ancestors") {
    val compile = Compilation(Samples.get("programs/genericvirtuals/stampMultipleAncestors.vale"))
    val temputs = compile.getTemputs()
    compile.evalForReferend(Vector())
  }

  test("Tests recursion") {
    val compile = Compilation(Samples.get("programs/functions/recursion.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(120)
  }

  test("Tests floats") {
    val compile = Compilation(
      """
        |struct Moo imm {
        |  x float;
        |}
        |fn main() int export {
        |  7
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("getOr function") {
    val compile = Compilation(Samples.get("programs/genericvirtuals/getOr.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Function return without ret upcasts") {
    val compile = Compilation(
      """
        |interface Opt { }
        |struct Some { value int; }
        |impl Opt for Some;
        |
        |fn doIt() Opt {
        |  Some(9)
        |}
        |
        |fn main() int export {
        |  a = doIt();
        |  = 3;
        |}
        |""".stripMargin)

    val temputs = compile.getTemputs()
    val doIt = temputs.lookupFunction("doIt")
    doIt.only({
      case StructToInterfaceUpcast2(_, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Function return with ret upcasts") {
    val compile = Compilation(Samples.get("programs/virtuals/retUpcast.vale"))

    val temputs = compile.getTemputs()
    val doIt = temputs.lookupFunction("doIt")
    doIt.only({
      case StructToInterfaceUpcast2(_, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Map function") {
    val compile = Compilation.multiple(List(
      Samples.get("libraries/castutils.vale"),
        Samples.get("libraries/printutils.vale"),
        Samples.get("programs/genericvirtuals/mapFunc.vale")))

    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

  test("Test shaking") {
    // Make sure that functions that cant be called by main will not be included.

    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      """
        |fn bork(x str) { print(x); }
        |fn helperFunc(x int) { print(x); }
        |fn helperFunc(x str) { print(x); }
        |fn main() {
        |  helperFunc(4);
        |}
        |""".stripMargin)
    val hinputs = compile.getTemputs()

    vassertSome(hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("helperFunc", List(), List(Coord(Share, Readonly, Int2())))))))

    vassert(None == hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("bork", List(), List(Coord(Share, Readonly, Str2())))))))

    vassert(None == hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("helperFunc", List(), List(Coord(Share, Readonly, Str2())))))))
  }

//  test("Test overloading between borrow and own") {
//    val compile = Compilation(
//      """
//        |interface IMoo {}
//        |struct Moo {}
//        |impl IMoo for Moo;
//        |
//        |fn func(virtual moo IMoo) int abstract;
//        |fn func(virtual moo &IMoo) int abstract;
//        |
//        |fn func(moo Moo impl IMoo) int { 73 }
//        |fn func(moo &Moo impl IMoo) int { 42 }
//        |
//        |fn main() int export {
//        |  func(&Moo())
//        |}
//        |""".stripMargin)
//    val temputs = compile.getTemputs()
//
//    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
//  }

  test("Test returning empty seq") {
    val compile = Compilation(
      """fn main() [] {
        |  []
        |}
        |""".stripMargin)
    val temputs = compile.getTemputs()

    compile.run(Vector())
  }

  test("Test export functions") {
    val compile = Compilation(
      """fn moo() int export {
        |  42
        |}
        |""".stripMargin)
    val hamuts = compile.getHamuts()
    VonHammer.vonifyProgram(hamuts)
  }

  test("Test extern functions") {
    val compile = Compilation(Samples.get("programs/externs/extern.vale"))

    val hamuts = compile.getHamuts()

    // The extern we make should have the name we expect
    vassert(hamuts.externs.exists(_.fullName.readableName == "sqrt"))

    // We also made an internal function that contains an extern call
    val externSqrt = hamuts.lookupFunction("sqrt")
    vassert(externSqrt.isExtern)

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Test narrowing between borrow and owning overloads") {
    // See NMORFI for why this test is here. Before the SCCTT fix, it couldn't resolve between the two
    // `get` overloads, because the borrow ownership (from the opt.get()) was creeping into the rules
    // too far.

    val compile = Compilation(
      """
        |interface Opt<T> rules(T Ref) { }
        |struct None<T> rules(T Ref) { }
        |impl<T> Opt<T> for None<T>;
        |
        |fn get<T>(virtual opt Opt<T>) int abstract;
        |fn get<T>(opt None<T> impl Opt<T>) int { __panic() }
        |
        |fn get<T>(virtual opt &Opt<T>) int abstract;
        |fn get<T>(opt &None<T> impl Opt<T>) int { 42 }
        |
        |fn main() int export {
        |  opt Opt<int> = None<int>();
        |  = opt.get();
        |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Test catch deref after drop") {
    val compile = Compilation(Samples.get("programs/invalidaccess.vale"))
    try {
      compile.evalForReferend(Vector()) shouldEqual VonInt(42)
      vfail()
    } catch {
      case ConstraintViolatedException(_) => // good!
    }
  }

  // This test is here because we had a bug where the compiler was enforcing that we unstackify
  // the same locals from all branches of if, even if they were constraint refs.
  test("Using same constraint ref from both branches of if") {
    val compile = Compilation(
      """
        |struct Moo {}
        |fn foo(a &Moo) int { 41 }
        |fn bork(a &Moo) int {
        |  if (false) {
        |    ret foo(a);
        |  } else if (false) {
        |    ret foo(a);
        |  } else {
        |    // continue
        |  }
        |  ret foo(a) + 1;
        |}
        |fn main() int export {
        |  ret bork(&Moo());
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }


  // Compiler should be fine with moving things from if statements if we ret out.
  test("Moving same thing from both branches of if") {
    val compile = Compilation(
      """
        |struct Moo {}
        |fn foo(a Moo) int { 41 }
        |fn bork(a Moo) int {
        |  if (false) {
        |    ret foo(a);
        |  } else if (false) {
        |    ret foo(a);
        |  } else {
        |    // continue
        |  }
        |  ret 42;
        |}
        |fn main() int export {
        |  ret bork(Moo());
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("exporting array") {
    val compilation = Compilation("export Array<mut, int> as IntArray;")
    val hamuts = compilation.getHamuts()
    val (fullNameH, exportedName) = hamuts.exportedNameByFullName.head
    exportedName shouldEqual "IntArray"
    val usa = hamuts.unknownSizeArrays.find(_.name == fullNameH).get
    usa.rawArray.elementType.kind shouldEqual IntH()
  }
}
