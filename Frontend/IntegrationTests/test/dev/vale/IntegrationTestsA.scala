package dev.vale

import dev.vale.highertyping.{ICompileErrorA, ProgramA}
import dev.vale.passmanager.{FullCompilation, FullCompilationOptions}
import dev.vale.finalast.{IdH, IntHT, OwnH, ProgramH, PrototypeH, YonderH}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.FileP
import dev.vale.postparsing._
import dev.vale.typing.ast._
import dev.vale.instantiating.ast._
import dev.vale.typing.names.{FunctionNameT, FunctionTemplateNameT, IdT}
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


object RunCompilation {
  def test(code: String, includeAllBuiltins: Boolean = true): RunCompilation = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    new RunCompilation(
      interner,
      keywords,
      (if (includeAllBuiltins) {
        Vector(PackageCoordinate.BUILTIN(interner, keywords))
      } else {
        Vector()
      }) ++
      Vector(
        PackageCoordinate.TEST_TLD(interner, keywords)),
      (if (includeAllBuiltins) {
        Builtins.getCodeMap(interner, keywords)
      } else {
        Builtins.getModulizedCodeMap(interner, keywords)
      })
        .or(FileCoordinateMap.test(interner, Vector(code)))
        .or(Tests.getPackageToResourceResolver),
      FullCompilationOptions(GlobalOptions(true, true, true, true)))
  }
}

class RunCompilation(
    val interner: Interner,
    val keywords: Keywords,
    packagesToBuild: Vector[PackageCoordinate],
    packageToContentsResolver: IPackageResolver[Map[String, String]],
    options: FullCompilationOptions = FullCompilationOptions()) {
  var fullCompilation = new FullCompilation(interner, keywords, packagesToBuild, packageToContentsResolver, options)

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = fullCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = fullCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = fullCompilation.getScoutput()
  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = fullCompilation.getAstrouts()
  def getCompilerOutputs(): Result[HinputsT, ICompileErrorT] = fullCompilation.getCompilerOutputs()
  def expectCompilerOutputs(): HinputsT = fullCompilation.expectCompilerOutputs()
  def getMonouts(): HinputsI = fullCompilation.getMonouts()
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
    val compile = RunCompilation.test("import v.builtins.tup.*; exported func main() int { return 3; }", false)
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
    compile.evalForKind(Vector()) match { case VonInt(9) => }
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
    // See TIBANFC: Translate Impl Bound Argument Names For Case
    val compile = RunCompilation.test(
        Tests.loadExpected("programs/genericvirtuals/templatedoption.vale"), false)
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
}
