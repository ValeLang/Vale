package dev.vale

import dev.vale.finalast._
import dev.vale.instantiating.ast._
import dev.vale.testvm.{ConstraintViolatedException, Heap, IntV, StructInstanceV}
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.von.{VonBool, VonFloat, VonInt}
import org.scalatest._


class IntegrationTestsB extends FunSuite with Matchers {
  test("Tests single expression and single statement functions' returns") {
    val compile = RunCompilation.test(
      """
        |struct MyThing { value int; }
        |func moo() MyThing { return MyThing(4); }
        |exported func main() { moo(); }
      """.stripMargin)
    compile.run(Vector())
  }

  test("Tests calling a templated struct's constructor") {
    val compile = RunCompilation.test(
      """
        |#!DeriveStructDrop
        |struct MySome<T Ref> { value T; }
        |
        |exported func main() int {
        |  [x] = MySome<int>(4);
        |  return x;
        |}
      """.stripMargin)
    compile.evalForKind(Vector())
  }

  test("Test array push, pop, len, capacity, drop") {
    val compile = RunCompilation.test(
      """
        |import castutils.*;
        |import printutils.*;
        |import array.make.*;
        |
        |exported func main() int {
        |  arr = Array<mut, int>(9);
        |  arr.push(420);
        |  arr.push(421);
        |  arr.push(422);
        |  arr.len();
        |  return arr.capacity();
        |  // implicit drop with pops
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    compile.evalForKind(Vector()) match {
      case VonInt(9) =>
    }
  }

  test("Test int generic") {
    val compile = RunCompilation.test(
      """
        |
        |struct Vec<N Int, T>
        |{
        |  values [#N]<imm>T;
        |}
        |
        |exported func main() int {
        |  v = Vec<3, int>(#[#](3, 4, 5));
        |  return v.values.2;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match {
      case VonInt(5) =>
    }
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/upcasting.vale"))
    compile.run(Vector())
  }

  test("Tests upcasting from if") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/if/upcastif.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("Tests lambda") {
    val compile =
      RunCompilation.test(
        """
          |exported func main() int {
          |  a = 7;
          |  return { a }();
          |}
          |""".stripMargin)
    compile.run(Vector())
  }

  test("Tests generic with a lambda") {
    val compile =
      RunCompilation.test(
        """
          |func genFunc<T>(a &T) &T {
          |  return { a }();
          |}
          |exported func main() int {
          |  genFunc(7)
          |}
          |""".stripMargin)
    compile.run(Vector())
  }

  test("Tests generic's lambda calling parent function's bound") {
    // See LCCPGB for explanation.
    val compile =
      RunCompilation.test(
        """
          |func genFunc<T>(a &T)
          |where func print(&T)void {
          |  { print(a); }()
          |}
          |exported func main() {
          |  genFunc("hello");
          |}
          |""".stripMargin)
    compile.run(Vector())
  }

  test("Tests generic with a polymorphic lambda") {
    // This lambda has an implicit <Y> template param
    val compile =
      RunCompilation.test(
        """
          |func genFunc<T>(a &T) &T {
          |  return (x => a)(true);
          |}
          |exported func main() int {
          |  genFunc(7)
          |}
          |""".stripMargin)
    compile.run(Vector())
  }

  test("Tests generic with a polymorphic lambda invoked twice") {
    // This lambda has an implicit <Y> template param, invoked with a bool then a string
    val compile =
      RunCompilation.test(
        """
          |func genFunc<T>(a &T) &T {
          |  lam = (x => a);
          |  lam(true);
          |  return lam("hello");
          |}
          |exported func main() int {
          |  genFunc(7)
          |}
          |""".stripMargin)
    compile.run(Vector())
  }

  //  test("Test getting generic value out of lambda") {
  //    val compile = RunCompilation.test(
  //      """
  //        |#!DeriveStructDrop
  //        |struct MyStruct<A Ref imm, B Ref imm, C Ref imm, D Ref imm> imm { a A; b B; c C; d D; }
  //        |
  //        |func bork<A, B, C, D>(m &MyStruct<A, B, C, D>) &D {
  //        |  return { m.d }();
  //        |}
  //        |exported func main() int {
  //        |  x = MyStruct(true, 1, "hello", 3);
  //        |  return bork(&x);
  //        |}
  //        |""".stripMargin)
  //    compile.evalForKind(Vector()) match { case VonInt(5) => }
  //  }

  test("Tests double closure") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/lambdas/doubleclosure.vale"))
    compile.run(Vector())
  }

  test("Tests from subdir file") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/round.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(8) =>
    }
  }

  test("Test generic param default") {
    val compile = RunCompilation.test(
      """
        |func bork<N Int = 42>() int { return N; }
        |exported func main() int { bork() }
      """.stripMargin)
    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("Tests calling a virtual function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/calling.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(7) =>
    }
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = RunCompilation.test(
      """
        |interface MyOption<T> where T Ref { }
        |
        |struct MySome<T> where T Ref {}
        |impl<T> MyOption<T> for MySome<T>;
        |
        |func doSomething(opt MyOption<int>) int {
        |  return 9;
        |}
        |
        |exported func main() int {
        |	 x MyOption<int> = MySome<int>();
        |	 return doSomething(x);
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match {
      case VonInt(9) =>
    }
  }


  test("Tests a linked list") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/virtuals/ordinarylinkedlist.vale"))
    compile.evalForKind(Vector())
  }

  test("Tests a templated linked list") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/templatedlinkedlist.vale"))
    compile.evalForKind(Vector())
  }

  test("Tests calling an abstract function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/callingAbstract.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(4) =>
    }
  }

  test("Template overrides are stamped") {
    // See TIBANFC: Translate Impl Bound Argument Names For Case
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/templatedoption.vale"), false)
    compile.evalForKind(Vector()) match {
      case VonInt(1) =>
    }
  }

  test("Tests a foreach for a linked list") {
    val compile = RunCompilation.test(
      Tests.loadExpected("programs/genericvirtuals/foreachlinkedlist.vale"))
    compile.evalForStdout(Vector()) shouldEqual "102030"
  }

  // When we call a function with a virtual parameter, try stamping for all ancestors in its
  // place.
  // We're stamping all ancestors, and all ancestors have virtual.
  // Virtual starts a function family.
  // So, this checks that it and its three ancestors are all stamped and all get their own
  // function families.
  //  test("Stamp multiple ancestors") {
  //    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/stampMultipleAncestors.vale"))
  //    val coutputs = compile.expectCompilerOutputs()
  //    compile.evalForKind(Vector())
  //  }

  test("Tests recursion") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/functions/recursion.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(120) =>
    }
  }

  test("Tests generic recursion") {
    val compile = RunCompilation.test(
      """
        |func factorial<T>(one T, x T) T
        |where func isZero(&T)bool, func *(&T, &T)T, func -(&T, &T)T, func drop(T)void {
        |  return if isZero(&x) {
        |      one
        |    } else {
        |      q = &one;
        |      x * factorial(one, x - q)
        |    };
        |}
        |
        |func isZero(x int) bool { x == 0 }
        |
        |exported func main() int {
        |  return factorial(1, 5);
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match {
      case VonInt(120) =>
    }
  }

}
