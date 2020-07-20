package net.verdagon.vale

import java.io.FileNotFoundException

import net.verdagon.vale.templar._
import net.verdagon.vale.{metal => m}
import net.verdagon.vale.vivem.{Heap, IntV, StructInstanceV}
import net.verdagon.von.{VonBool, VonFloat, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation
import net.verdagon.vale.metal.YonderH
import net.verdagon.vale.templar.templata.Signature2
import net.verdagon.vale.templar.types.{Coord, Int2, Share, Str2}

import scala.io.Source
import scala.util.Try

class IntegrationTestsA extends FunSuite with Matchers {
  test("Simple program returning an int") {
    val compile = new Compilation("fn main(){3}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Hardcoding negative numbers") {
    val compile = new Compilation("fn main(){-3}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(-3)
  }

  test("Taking an argument and returning it") {
    val compile = new Compilation("fn main(a int){a}")
    compile.evalForReferend(Vector(IntV(5))) shouldEqual VonInt(5)
  }

  test("Tests adding two numbers") {
    val compile = new Compilation("fn main(){ +(2, 3) }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Tests adding two floats") {
    val compile = new Compilation("fn main(){ +(2.5, 3.5) }")
    compile.evalForReferend(Vector()) shouldEqual VonFloat(6.0f)
  }

  test("Tests inline adding") {
    val compile = new Compilation("fn main(){ 2 + 3 }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Test constraint ref") {
    val compile = new Compilation(Samples.get("constraintRef.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Tests inline adding more") {
    val compile = new Compilation("fn main(){ 2 + 3 + 4 + 5 + 6 }")
    compile.evalForReferend(Vector()) shouldEqual VonInt(20)
  }

  test("Simple lambda") {
    val compile = new Compilation("fn main(){{7}()}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Lambda with one magic arg") {
    val compile = new Compilation("fn main(){{_}(3)}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = new Compilation("fn main(){(a int){ +(a,a)}(3)}");
    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }

  test("Test overloads") {
    val compile = new Compilation(Samples.get("overloads.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }

  test("Test block") {
    val compile = new Compilation("fn main(){true; 200; = 300;}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(300)
  }

  test("Test templates") {
    val compile = new Compilation(
      """
        |fn ~<T>(a T, b T) T { a }
        |fn main(){true ~ false; 2 ~ 2; = 3 ~ 3;}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Test mutating a local var") {
    val compile = new Compilation("fn main(){a! = 3; mut a = 4; }")
    compile.run(Vector())
  }

  test("Test returning a local mutable var") {
    val compile = new Compilation("fn main(){a! = 3; mut a = 4; = a;}")
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Test taking a callable param") {
    val compile = new Compilation(
      """
        |fn do(callable) {callable()}
        |fn main() {do({ 3 })}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Stamps an interface template via a function parameter") {
    val compile = new Compilation(
      """
        |interface MyInterface<T> rules(T Ref) { }
        |fn doAThing<T>(i MyInterface<T>) { }
        |
        |struct SomeStruct<T> rules(T Ref) { }
        |fn doAThing<T>(s SomeStruct<T>) { }
        |impl<T> SomeStruct<T> for MyInterface<T>;
        |
        |fn main(a SomeStruct<int>) {
        |  doAThing<int>(a);
        |}
      """.stripMargin)
    val hamuts = compile.getHamuts()
    val heap = new Heap(System.out)
    val ref =
      heap.add(m.OwnH, YonderH, StructInstanceV(
        hamuts.lookupStruct("SomeStruct"),
        Some(Vector())))
    compile.run(heap, Vector(ref))
  }

  test("Tests unstackifying a variable multiple times in a function") {
    val compile = new Compilation(Samples.get("multiUnstackify.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Reads a struct member") {
    val compile = new Compilation(
      """
        |struct MyStruct { a int; }
        |fn main() { ms = MyStruct(7); = ms.a; }
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  // Known failure 2020-07-18
  test("Tests virtual doesn't get called if theres a better override") {
    val compile = new Compilation(
      """
        |interface MyOption { }
        |
        |struct MySome {
        |  value MyList;
        |}
        |impl MySome for MyOption;
        |
        |struct MyNone { }
        |impl MyNone for MyOption;
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
        |fn main() int {
        |  list = MyList(10, MySome(MyList(20, MySome(MyList(30, MyNone())))));
        |  = sum(&list);
        |}
        |
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(60)
  }

  test("Tests single expression and single statement functions' returns") {
    val compile = new Compilation(
      """
        |struct MyThing { value int; }
        |fn moo() { MyThing(4) }
        |fn main() { moo(); }
      """.stripMargin)
    compile.run(Vector())
  }

  test("Tests calling a templated struct's constructor") {
    val compile = new Compilation(
      """
        |struct MySome<T> rules(T Ref) { value T; }
        |fn main() {
        |  MySome<int>(4).value
        |}
      """.stripMargin)
    compile.evalForReferend(Vector())
  }

  test("Test int generic") {
    val compile = new Compilation(
      """
        |
        |struct Vec<N, T> rules(N int)
        |{
        |  values [<mut> N * T];
        |}
        |
        |fn main() {
        |  v = Vec<3, int>([3, 4, 5]);
        |  = v.values.2;
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Tests upcasting from a struct to an interface") {
    val compile = new Compilation(Samples.get("virtuals/upcasting.vale"))
    compile.run(Vector())
  }

  test("Tests from file") {
    val compile = new Compilation(Samples.get("doubleclosure.vale"))
    compile.run(Vector())
  }

  test("Tests from subdir file") {
    val compile = new Compilation(Samples.get("virtuals/round.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(8)
  }

  test("Tests calling a virtual function") {
    val compile = new Compilation(Samples.get("virtuals/calling.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("Tests making a variable with a pattern") {
    // Tests putting MyOption<int> as the type of x.
    val compile = new Compilation(
      """
        |interface MyOption<T> rules(T Ref) { }
        |
        |struct MySome<T> rules(T Ref) {}
        |impl<T> MySome<T> for MyOption<T>;
        |
        |fn doSomething(opt MyOption<int>) int {
        |  = 9;
        |}
        |
        |fn main() int {
        |	x MyOption<int> = MySome<int>();
        |	= doSomething(x);
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }


  test("Tests a linked list") {
    val compile = new Compilation(Samples.get("virtuals/ordinarylinkedlist.vale"))
    compile.evalForReferend(Vector())
  }

  test("Tests a templated linked list") {
    val compile = new Compilation(Samples.get("genericvirtuals/templatedlinkedlist.vale"))
    compile.evalForReferend(Vector())
  }

  test("Tests calling an abstract function") {
    val compile = new Compilation(Samples.get("genericvirtuals/callingAbstract.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Template overrides are stamped") {
    val compile = new Compilation(Samples.get("genericvirtuals/templatedoption.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(1)
  }

  test("Tests a foreach for a linked list") {
    val compile = new Compilation(Samples.get("genericvirtuals/foreachlinkedlist.vale"))
    compile.evalForStdout(Vector()) shouldEqual "102030"
  }

  // When we call a function with a virtual parameter, try stamping for all ancestors in its
  // place.
  // We're stamping all ancestors, and all ancestors have virtual.
  // Virtual starts a function family.
  // So, this checks that it and its three ancestors are all stamped and all get their own
  // function families.
  test("Stamp multiple ancestors") {
    val compile = new Compilation(Samples.get("genericvirtuals/stampMultipleAncestors.vale"))
    val temputs = compile.getTemputs()
    compile.evalForReferend(Vector())
  }

  test("Tests recursion") {
    val compile = new Compilation(Samples.get("recursion.vale"))
    compile.evalForReferend(Vector()) shouldEqual VonInt(120)
  }

  test("Tests floats") {
    val compile = new Compilation(
      """
        |struct Moo imm {
        |  x float;
        |}
        |fn main() {
        |  7
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  test("getOr function") {
    val compile = new Compilation(Samples.get("genericvirtuals/getOr.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Function return without ret upcasts") {
    val compile = new Compilation(
      """
        |interface Opt { }
        |struct Some { value int; }
        |impl Some for Opt;
        |
        |fn doIt() Opt {
        |  Some(9)
        |}
        |
        |fn main() {
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
    val compile = new Compilation(Samples.get("virtuals/retUpcast.vale"))

    val temputs = compile.getTemputs()
    val doIt = temputs.lookupFunction("doIt")
    doIt.only({
      case StructToInterfaceUpcast2(_, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Map function") {
    val compile = new Compilation(Samples.get("genericvirtuals/mapFunc.vale"))

    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }


  test("Test shaking") {
    // Make sure that functions that cant be called by main will not be included.

    val compile = new Compilation(
      """
        |fn bork(x str) { print(x); }
        |fn helperFunc(x int) { print(x); }
        |fn helperFunc(x str) { print(x); }
        |fn main() {
        |  helperFunc(4);
        |}
        |""".stripMargin)
    val hinputs = compile.getHinputs()

    vassertSome(hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("helperFunc", List(), List(Coord(Share, Int2())))))))

    vassert(None == hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("bork", List(), List(Coord(Share, Str2())))))))

    vassert(None == hinputs.lookupFunction(Signature2(FullName2(List(), FunctionName2("helperFunc", List(), List(Coord(Share, Str2())))))))
  }

  test("Test generic array func") {
    // Make sure that functions that cant be called by main will not be included.

    val compile = new Compilation(
      """
        |fn Arr<M, F>(n int, generator &F) Array<M, T>
        |rules(M Mutability, T Ref, Prot("__call", (&F, int), T))
        |{
        |  Array<M>(n, &IFunction1<mut, int, T>(generator))
        |}
        |
        |fn main() {
        |  a = Arr<mut>(5, (_){"hi"});
        |  = a.3;
        |}
        |""".stripMargin)
    val temputs = compile.getTemputs()

    compile.run(Vector())
  }



}
