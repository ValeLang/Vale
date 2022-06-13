package dev.vale

import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.passmanager.{FullCompilation, FullCompilationOptions}
import dev.vale.simplifying.VonHammer
import dev.vale.finalast.{FullNameH, IntH, OwnH, ProgramH, PrototypeH, YonderH}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing.{ICompileErrorS, ProgramS}
import dev.vale.typing.ast.{SignatureT, StructToInterfaceUpcastTE}
import dev.vale.typing.names.{FullNameT, FunctionNameT}
import dev.vale.typing.{Hinputs, ICompileErrorT, ast}
import dev.vale.typing.types.{CoordT, IntT, ShareT, StrT}
import dev.vale.testvm.{ConstraintViolatedException, Heap, IntV, PrimitiveKindV, ReferenceV, StructInstanceV, Vivem}
import dev.vale.highertyping.ICompileErrorA

import java.io.FileNotFoundException
import dev.vale.typing.ast
import dev.vale.{finalast => m}
import dev.vale.testvm.ReferenceV
import org.scalatest.{FunSuite, Matchers}
import dev.vale.passmanager.FullCompilation
import dev.vale.finalast.FullNameH
import dev.vale.postparsing.ICompileErrorS
import dev.vale.typing.ast._
import dev.vale.typing.names.FunctionNameT
import dev.vale.typing.types.StrT
import dev.vale.von.{IVonData, VonBool, VonFloat, VonInt}

import scala.collection.immutable.List


object RunCompilation {
  def test(code: String*): RunCompilation = {
    val interner = new Interner()
    new RunCompilation(
      interner,
      Vector(
        PackageCoordinate.BUILTIN(interner),
        PackageCoordinate.TEST_TLD(interner)),
      Builtins.getCodeMap(interner)
        .or(FileCoordinateMap.test(interner, code.toVector))
        .or(Tests.getPackageToResourceResolver),
      FullCompilationOptions(GlobalOptions(true, true, true, true)))
  }
}

class RunCompilation(
    val interner: Interner,
    packagesToBuild: Vector[PackageCoordinate],
    packageToContentsResolver: IPackageResolver[Map[String, String]],
    options: FullCompilationOptions = FullCompilationOptions()) {
  var fullCompilation = new FullCompilation(interner, packagesToBuild, packageToContentsResolver, options)

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = fullCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = fullCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = fullCompilation.getAstrouts()
  def getCompilerOutputs(): Result[Hinputs, ICompileErrorT] = fullCompilation.getCompilerOutputs()
  def expectCompilerOutputs(): Hinputs = fullCompilation.expectCompilerOutputs()
  def getHamuts(): ProgramH = {
    val hamuts = fullCompilation.getHamuts()
    fullCompilation.getVonHammer().vonifyProgram(hamuts)
    hamuts
  }

  def evalForKind(heap: Heap, args: Vector[ReferenceV]): IVonData = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(heap: Heap, args: Vector[ReferenceV]): Unit = {
    Vivem.executeWithHeap(getHamuts(), heap, args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def run(args: Vector[PrimitiveKindV]): Unit = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForKind(args: Vector[PrimitiveKindV]): IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, Vivem.regularStdout)
  }
  def evalForKind(
    args: Vector[PrimitiveKindV],
    stdin: Vector[String]):
  IVonData = {
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.stdinFromList(stdin), Vivem.regularStdout)
  }
  def evalForStdout(args: Vector[PrimitiveKindV]): String = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    stdoutStringBuilder.mkString
  }
  def evalForKindAndStdout(args: Vector[PrimitiveKindV]): (IVonData, String) = {
    val (stdoutStringBuilder, stdoutFunc) = Vivem.stdoutCollector()
    val kind = Vivem.executeWithPrimitiveArgs(getHamuts(), args, System.out, Vivem.emptyStdin, stdoutFunc)
    (kind, stdoutStringBuilder.mkString)
  }
}

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
    val compile = RunCompilation.test("exported func main() int { return 3; }")
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

  test("Test overload set") {
    val compile =
      RunCompilation.test(
        """
          |import array.each.*;
          |func myfunc(i int) { }
          |exported func main() int {
          |  mylist = [#][1, 3, 3, 7];
          |  mylist.each(myfunc);
          |  42
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }


  test("Test block") {
    val compile = RunCompilation.test("exported func main() int {true; 200; return 300;}")
    compile.evalForKind(Vector()) match { case VonInt(300) => }
  }

  test("Test templates") {
    val compile = RunCompilation.test(
      """
        |func ~<T>(a T, b T) T { return a; }
        |exported func main() int {true ~ false; 2 ~ 2; return 3 ~ 3;}
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
        |func do<T>(callable T) infer-return { return callable(); }
        |exported func main() int { return do({ 3 }); }
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Stamps an interface template via a function parameter") {
    val compile = RunCompilation.test(
      """
        |interface MyInterface<T> where T Ref { }
        |func doAThing<T>(i MyInterface<T>) { }
        |
        |struct SomeStruct<T> where T Ref { }
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
    val packageH = compile.getHamuts().lookupPackage(PackageCoordinate.TEST_TLD(compile.interner))
    val heap = new Heap(System.out)
    val ref =
      heap.add(OwnH, YonderH, StructInstanceV(
        packageH.lookupStruct("SomeStruct"),
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

  test("set swapping locals") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/mutswaplocals.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("imm tuple access") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/tuples/immtupleaccess.vale"))
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
        |struct MySome<T> where T Ref { value T; }
        |exported func main() int {
        |  return MySome<int>(4).value;
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
        |import ifunction.ifunction1.*;
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
    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Test int generic") {
    val compile = RunCompilation.test(
      """
        |
        |struct Vec<N, T> where N int
        |{
        |  values [#N]<imm>T;
        |}
        |
        |exported func main() int {
        |  v = Vec<3, int>(#[#][3, 4, 5]);
        |  return v.values.2;
        |}
      """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/upcasting.vale"))
    compile.run(Vector())
  }

  test("Tests upcasting from if") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/if/upcastif.vale"))
    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Tests from file") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/lambdas/doubleclosure.vale"))
    compile.run(Vector())
  }

  test("Tests from subdir file") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/round.vale"))
    compile.evalForKind(Vector()) match { case VonInt(8) => }
  }

  test("Tests calling a virtual function") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/virtuals/calling.vale"))
    compile.evalForKind(Vector()) match { case VonInt(7) => }
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
    compile.evalForKind(Vector()) match { case VonInt(9) => }
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
    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Template overrides are stamped") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedoption.vale"))
    compile.evalForKind(Vector()) match { case VonInt(1) => }
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
    compile.evalForKind(Vector()) match { case VonInt(120) => }
  }

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

  test("Function return without return upcasts") {
    val compile = RunCompilation.test(
      """
        |interface XOpt { }
        |struct XSome { value int; }
        |impl XOpt for XSome;
        |
        |func doIt() XOpt {
        |  return XSome(9);
        |}
        |
        |exported func main() int {
        |  a = doIt();
        |  return 3;
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val doIt = coutputs.lookupFunction("doIt")
    Collector.only(doIt, {
      case StructToInterfaceUpcastTE(_, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
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
      case StructToInterfaceUpcastTE(_, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Map function") {
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/mapFunc.vale"))
    compile.expectCompilerOutputs()

    compile.evalForKind(Vector()) match { case VonBool(true) => }
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
    val hinputs = compile.expectCompilerOutputs()
    val interner = compile.interner

    vassertSome(hinputs.lookupFunction(ast.SignatureT(FullNameT(PackageCoordinate.TEST_TLD(interner), Vector.empty, interner.intern(FunctionNameT("helperFunc", Vector.empty, Vector(CoordT(ShareT, IntT.i32))))))))

    vassert(None == hinputs.lookupFunction(SignatureT(FullNameT(PackageCoordinate.TEST_TLD(interner), Vector.empty, interner.intern(FunctionNameT("bork", Vector.empty, Vector(CoordT(ShareT, StrT()))))))))

    vassert(None == hinputs.lookupFunction(ast.SignatureT(FullNameT(PackageCoordinate.TEST_TLD(interner), Vector.empty, interner.intern(FunctionNameT("helperFunc", Vector.empty, Vector(CoordT(ShareT, StrT()))))))))
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

  test("Test returning empty seq") {
    val compile = RunCompilation.test(
      """export () as Tup0;
        |exported func main() () {
        |  return ();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    compile.run(Vector())
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

    val packageH = compile.getHamuts().lookupPackage(compile.interner.intern(PackageCoordinate("math", Vector.empty)))

    // The extern we make should have the name we expect
    vassertSome(packageH.externNameToFunction.get("sqrt")) match {
      case PrototypeH(FullNameH("sqrt",_,PackageCoordinate("math",Vector()),_),_,_) =>
    }

    // We also made an internal function that contains an extern call
    val externSqrt = packageH.lookupFunction("sqrt")
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
    val testPackage = hamuts.lookupPackage(PackageCoordinate.TEST_TLD(compilation.interner))
    val kindH = vassertSome(testPackage.exportNameToKind.get("IntArray"))

    val builtinPackage = hamuts.lookupPackage(PackageCoordinate.BUILTIN(compilation.interner))
    val rsa = vassertSome(builtinPackage.runtimeSizedArrays.find(_.kind == kindH))
    rsa.elementType.kind shouldEqual IntH.i32
  }

  test("Readwrite thing") {
    val compile = RunCompilation.test(
      """
        |func each<E, F>(func F) void
        |where func(&F, &E)void {
        |}
        |
        |struct PageMember { x int; }
        |struct SomeMutableThing { x int; }
        |
        |exported func main() {
        |  someMutableThing = SomeMutableThing(7);
        |  each<PageMember>((a &PageMember) => { someMutableThing.x; a.x; });
        |}
        |
        |""".stripMargin)
    compile.evalForKind(Vector())
  }
}
