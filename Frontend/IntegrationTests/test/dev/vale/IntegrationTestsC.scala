package dev.vale

import dev.vale.finalast._
import dev.vale.instantiating.ast._
import dev.vale.testvm.{ConstraintViolatedException, Heap, IntV, StructInstanceV}
import dev.vale.typing.ast._
import dev.vale.typing.types._
import dev.vale.von.{VonBool, VonFloat, VonInt}
import org.scalatest._


class IntegrationTestsC extends FunSuite with Matchers {

  test("Tests floats") {
    val compile = RunCompilation.test(
      """
        |struct Moo imm {
        |  x float;
        |}
        |exported func main() int {
        |  return 7;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("getOr function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/genericvirtuals/getOr.vale"))

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  // Not sure if this is desirable behavior, because borrow_ship isnt really used after
  // we drop ship. Still, let's have this test so we don't *accidentally* change it.
  test("Panic on drop because of outstanding borrow") {
    val compile = RunCompilation.test(
      """
        |struct Ship { hp int; }
        |
        |exported func main() {
        |  ship = Ship(1337);
        |  borrow_ship = &ship;
        |  ship; // drops it
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    try {
      compile.evalForKind(Vector())
      vfail()
    } catch {
      case ConstraintViolatedException(_) =>
    }
  }

  // Not sure if this is desirable behavior, because borrow_ship isnt really used after
  // we drop ship. Still, let's have this test so we don't *accidentally* change it.
  test("Unlet to avoid an outstanding-borrow panic") {
    val compile = RunCompilation.test(
      """
        |struct Ship { hp int; }
        |
        |exported func main() {
        |  ship = Ship(1337);
        |  borrow_ship = &ship;
        |  unlet borrow_ship;
        |  ship; // drops it
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    compile.evalForKind(Vector())
  }

  test("Function return with return upcasts") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/retUpcast.vale"))

    val coutputs = compile.expectCompilerOutputs()
    val doIt = coutputs.lookupFunction("doIt")
    Collector.only(doIt, {
      case UpcastTE(_, _, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Test shaking") {
    // Make sure that functions that cant be called by main will not be included.

    val compile = RunCompilation.test(
      """import printutils.*;
        |func bork(x str) { print(x); }
        |func helperFunc(x int) { print(x); }
        |func helperFunc(x str) { print(x); }
        |exported func main() {
        |  helperFunc(4);
        |}
        |""".stripMargin)
    val hinputs = compile.getMonouts()
    val interner = compile.interner
    val keywords = compile.keywords

    vassert(
      !hinputs.functions.exists(func => func.header.id.localName match {
        case FunctionNameIX(FunctionTemplateNameI(StrI("bork"), _), _, _) => true
        case _ => false
      }))

    vassert(
      hinputs.functions.find(func => func.header.id.localName match {
        case FunctionNameIX(FunctionTemplateNameI(StrI("helperFunc"), _), _, _) => true
        case _ => false
      }).size == 1)
  }

  test("Test overloading between borrow and weak") {
    val compile = RunCompilation.test(
      """
        |sealed interface IMoo  {}
        |struct Moo {}
        |impl IMoo for Moo;
        |
        |abstract func func(virtual moo &IMoo) int;
        |abstract func func(virtual moo &&IMoo) int;
        |
        |func func(moo &Moo) int { return 42; }
        |func func(moo &&Moo) int { return 73; }
        |
        |exported func main() int {
        |  return func(&Moo());
        |}
        |""".stripMargin)
    val coutputs = compile.getCompilerOutputs()

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Truncate i64 to i32") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  return TruncateI64ToI32(4300000000i64);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    compile.evalForKind(Vector()) match { case VonInt(5032704) => }
  }

  test("Return without return") {
    val compile = RunCompilation.test(
      """
        |exported func main() int { 73 }
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(73) => }
  }

  test("Test export functions") {
    val compile = RunCompilation.test(
      """exported func moo() int {
        |  return 42;
        |}
        |""".stripMargin)
    val hamuts = compile.getHamuts()
  }

  test("Test extern functions") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/externs/extern.vale"))
    val interner = compile.interner
    val keywords = compile.keywords

    val packageH = compile.getHamuts().lookupPackage(interner.intern(PackageCoordinate(interner.intern(StrI("math")), Vector.empty)))

    // The extern we make should have the name we expect
    vassertSome(packageH.externNameToFunction.get(interner.intern(StrI("sqrt")))) match {
      case PrototypeH(IdH("sqrt",PackageCoordinate(StrI("math"),Vector()),_,_),_,_) =>
    }

    // We also made an internal function that contains an extern call
    val externSqrt = packageH.lookupFunction("sqrt(float)")
    vassert(externSqrt.isExtern)

    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Test narrowing between borrow and owning overloads") {
    // See NMORFI for why this test is here. Before the SCCTT fix, it couldn't resolve between the two
    // `get` overloads, because the borrow ownership (from the opt.get()) was creeping into the rules
    // too far.

    val compile = RunCompilation.test(
      """
        |import panicutils.*;
        |
        |sealed interface XOpt<T> where T Ref { }
        |struct XNone<T> where T Ref { }
        |impl<T> XOpt<T> for XNone<T>;
        |
        |abstract func get<T>(virtual opt XOpt<T>) int;
        |func get<T>(opt XNone<T>) int { __vbi_panic() }
        |
        |abstract func get<T>(virtual opt &XOpt<T>) int;
        |func get<T>(opt &XNone<T>) int { return 42; }
        |
        |exported func main() int {
        |  opt XOpt<int> = XNone<int>();
        |  return opt.get();
        |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Test catch deref after drop") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/invalidaccess.vale"))
    try {
      compile.evalForKind(Vector()) match { case VonInt(42) => }
      vfail()
    } catch {
      case ConstraintViolatedException(_) => // good!
    }
  }

  // This test is here because we had a bug where the compiler was enforcing that we unstackify
  // the same locals from all branches of if, even if they were constraint refs.
  test("Using same constraint ref from both branches of if") {
    val compile = RunCompilation.test(
      """
        |struct Moo {}
        |func foo(a &Moo) int { return 41; }
        |func bork(a &Moo) int {
        |  if (false) {
        |    return foo(a);
        |  } else if (false) {
        |    return foo(a);
        |  } else {
        |    // continue
        |  }
        |  return foo(a) + 1;
        |}
        |exported func main() int {
        |  return bork(&Moo());
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }


  // Compiler should be fine with moving things from if statements if we return out.
  test("Moving same thing from both branches of if") {
    val compile = RunCompilation.test(
      """
        |struct Moo {}
        |func foo(a Moo) int { return 41; }
        |func bork(a Moo) int {
        |  if (false) {
        |    return foo(a);
        |  } else if (false) {
        |    return foo(a);
        |  } else {
        |    // continue
        |  }
        |  return 42;
        |}
        |exported func main() int {
        |  return bork(Moo());
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("exporting array") {
    val compilation = RunCompilation.test("export []<mut>int as IntArray;")
    val hamuts = compilation.getHamuts()
    val testPackage = hamuts.lookupPackage(PackageCoordinate.TEST_TLD(compilation.interner, compilation.keywords))
    val kindH = vassertSome(testPackage.exportNameToKind.get(compilation.interner.intern(StrI("IntArray"))))

    val builtinPackage = hamuts.lookupPackage(PackageCoordinate.BUILTIN(compilation.interner, compilation.keywords))
    val rsa = vassertSome(builtinPackage.runtimeSizedArrays.find(_.kind == kindH))
    rsa.elementType.kind shouldEqual IntHT.i32
  }

  test("Call borrow parameter with shared reference") {
    val compile = RunCompilation.test(
      """
        |func bork<T>(a &T) &T { return a; }
        |
        |exported func main() int {
        |  return bork(6);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(6) => }
  }

  test("Same type multiple times in an invocation") {
    val compile = RunCompilation.test(
      """
        |struct Bork<T> where func drop(T)void {
        |  a T;
        |}
        |
        |exported func main() int {
        |  return Bork<Bork<Bork<int>>>(Bork(Bork(7))).a.a.a;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Restackify") {
    // Allow set on variables that have been moved already, which is useful for linear style.
    val compile = RunCompilation.test(Tests.loadExpected("programs/restackify.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Destructure restackify") {
    // Allow set on variables that have been moved already, which is useful for linear style.
    val compile = RunCompilation.test(Tests.loadExpected("programs/destructure_restackify.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("Loop restackify") {
    // Allow set on variables that have been moved already, which is useful for linear style.
    val compile = RunCompilation.test(Tests.loadExpected("programs/loop_restackify.vale"))
    compile.evalForKind(Vector()) match {
      case VonInt(42) =>
    }
  }

  test("Ignoring receiver") {
    val compile = RunCompilation.test(
      """
        |struct Marine { hp int; }
        |exported func main() int { [_, y] = (Marine(6), Marine(8)); return y.hp; }
        |
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main");
    main.header.returnType shouldEqual CoordT(ShareT, RegionT(), IntT.i32)
    compile.evalForKind(Vector()) match {
      case VonInt(8) =>
    }
  }
}
