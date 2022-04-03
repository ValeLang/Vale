package dev.vale

import com.sun.tools.javac.util.ArrayUtils
import dev.vale.parsing.ast.ImmutableP
import dev.vale.typing.NewImmRSANeedsCallable
import dev.vale.typing.ast.{LetNormalTE, NewImmRuntimeSizedArrayTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.{CodeVarNameT, FullNameT}
import dev.vale.typing.types.{CoordT, ImmutableT, IntT, MutableT, OwnT, RuntimeSizedArrayTT, ShareT}
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.names.CodeVarNameT
import dev.vale.typing.types._
import dev.vale.von.{VonBool, VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class ArrayTests extends FunSuite with Matchers {
  test("Returning static array from function and dotting it") {
    val compile = RunCompilation.test(
      """
        |func makeArray() infer-return { return [#][2, 3, 4, 5, 6]; }
        |exported func main() int {
        |  return makeArray().3;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Simple static array and runtime index lookup") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  i = 2;
        |  a = [#][2, 3, 4, 5, 6];
        |  return a[i];
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,_,_, _) => {
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Destroy SSA of imms into function") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = [#][13, 14, 15];
        |  sum = 0;
        |  drop_into(a, &(e) => { set sum = sum + e; });
        |  return sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Destroy RSA of imms into function") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = Array<imm>(3, {13 + _});
        |  sum = 0;
        |  drop_into(a, &(e) => { set sum = sum + e; });
        |  return sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Destroy SSA of muts into function") {
    val compile = RunCompilation.test(
      """
        |struct Spaceship { fuel int; }
        |exported func main() int {
        |  a = [#][Spaceship(13), Spaceship(14), Spaceship(15)];
        |  sum = 0;
        |  drop_into(a, &(e) => { set sum = sum + e.fuel; });
        |  return sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Destroy RSA of muts into function") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |exported func main() int {
        |  a = MakeVaryArray(3, &{Spaceship(13 + _)});
        |  sum = 0;
        |  drop_into(a, &(e) => { set sum = sum + e.fuel; });
        |  return sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Migrate RSA") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |exported func main() int {
        |  a = Array<mut, Spaceship>(3, &{Spaceship(41 + _)});
        |  b = Array<mut, Spaceship>(3);
        |  migrate(a, &b);
        |  return b[1].fuel;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Migrate SSA") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |exported func main() int {
        |  a = [#3](&{Spaceship(41 + _)});
        |  b = Array<mut, Spaceship>(3);
        |  migrate(a, &b);
        |  return b[1].fuel;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Unspecified-mutability static array from lambda defaults to mutable") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  i = 3;
        |  a = [#5](&{_ * 42});
        |  return a[1];
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Immutable static array from lambda") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssaimmfromcallable.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Mutable static array from lambda") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssamutfromcallable.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Immutable static array from values") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssaimmfromvalues.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Mutable static array from values") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssamutfromvalues.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Unspecified-mutability runtime array from lambda defaults to mutable") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |exported func main() int {
        |  i = 3;
        |  a = MakeVaryArray(5, &{_ * 42});
        |  return a[1];
        |}
        |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Immutable runtime array from lambda") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/arrays/rsaimmfromcallable.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Mutable runtime array from lambda") {
    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/arrays/rsamutfromcallable.vale"))

    val coutputs = compile.expectCompilerOutputs()
    Collector.only(coutputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  //m [<mut> 3 * [#3]<mut>int] = [mut][ [mut][1, 2, 3], [mut][4, 5, 6], [mut][7, 8, 9] ];
  test("Take arraysequence as a parameter") {
    val compile = RunCompilation.test(
      """
        |func doThings(arr [#5]<imm>int) int {
        |  return arr.3;
        |}
        |exported func main() int {
        |  a = #[#][2, 3, 4, 5, 6];
        |  return doThings(a);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Borrow arraysequence as a parameter") {
    val compile = RunCompilation.test(
      """
        |struct MutableStruct {
        |  x int;
        |}
        |
        |func doThings(arr &[#3]^MutableStruct) int {
        |  return arr.2.x;
        |}
        |exported func main() int {
        |  a = [#][MutableStruct(2), MutableStruct(3), MutableStruct(4)];
        |  return doThings(&a);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  // the argument to __Array doesnt even have to be a struct or a lambda or an
  // interface or whatever, its just passed straight through to the prototype
  test("array map with int") {
    val compile = RunCompilation.test(
      """
        |func __call(lol int, i int) int { return i; }
        |
        |exported func main() int {
        |  a = #[](10, 1337);
        |  return a.3;
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("new rsa") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = []int(3);
        |  a.push(73);
        |  a.push(42);
        |  a.push(73);
        |  return a.1;
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case LetNormalTE(ReferenceLocalVariableT(FullNameT(_,Vector(_),CodeVarNameT("a")),_,CoordT(OwnT,RuntimeSizedArrayTT(MutableT,CoordT(ShareT,IntT(_))))), _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("array map with lambda") {
    val compile = RunCompilation.test(
      """
        |struct Lam imm {}
        |func __call(lam Lam, i int) int { return i; }
        |
        |exported func main() int
        |where F Prot = Prot["__call", Refs(Lam, int), int] {
        |  a = #[](10, Lam());
        |  return a.3;
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("MakeArray map with struct") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |
          |struct Lam imm {}
          |func __call(lam Lam, i int) int { return i; }
          |
          |exported func main() int {
          |  a = MakeArray(10, Lam());
          |  return a.3;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("MakeArray map with lambda") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |exported func main() int {
          |  a = MakeArray(10, {_});
          |  return a.3;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("array map with interface") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |import ifunction.ifunction1.*;
          |exported func main() int {
          |  a = Array<imm, int>(10, &IFunction1<imm, int, int>({_}));
          |  return a.3;
          |}
          |""".stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("Array")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Array map taking a closure which captures something") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |exported func main() int {
          |  x = 7;
          |  a = MakeImmArray(10, { _ + x });
          |  return a.3;
          |}
        """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(10) => }
  }

  test("Simple array map with runtime index lookup") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |exported func main() int {
          |  a = MakeImmArray(10, {_});
          |  i = 5;
          |  return a[i];
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Nested array") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  return [#][[#][2]].0.0;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(2) => }
  }


  test("Two dimensional array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |exported func main() int {
          |  board =
          |      MakeArray(
          |          3,
          |          (row) => { MakeArray(3, { row + _ }) });
          |  return board.1.2;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Array with capture") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |struct IntBox {
          |  i int;
          |}
          |
          |exported func main() int {
          |  box = IntBox(7);
          |  board = MakeArray(3, &(col) => { box.i });
          |  return board.1;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }

  test("Capture") {
    val compile = RunCompilation.test(
      """
        |func myFunc<F>(generator F) T
        |where T Ref, Prot["__call", Refs(F, int), T]
        |{
        |  return generator(9);
        |}
        |
        |struct IntBox {
        |  i int;
        |}
        |
        |exported func main() int {
        |  box = IntBox(7);
        |  lam = (col) => { box.i };
        |  board = myFunc(&lam);
        |  return board;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(7) => }
  }


  test("Mutate array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |exported func main() int {
          |  arr = MakeVaryArray(3, {_});
          |  set arr[1] = 1337;
          |  return arr.1;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }

  test("Capture mutable array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |import ifunction.ifunction1.*;
          |struct MyIntIdentity {}
          |impl IFunction1<mut, int, int> for MyIntIdentity;
          |func __call(this &MyIntIdentity, i int) int { return i; }
          |exported func main() {
          |  m = MyIntIdentity();
          |  arr = MakeArray(10, &m);
          |  lam = { print(str(arr.6)); };
          |  lam();
          |}
        """.stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "6"
  }

  test("Swap out of array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |import ifunction.ifunction1.*;
          |struct Goblin { }
          |
          |struct GoblinMaker {}
          |impl IFunction1<mut, int, Goblin> for GoblinMaker;
          |func __call(this &GoblinMaker, i int) Goblin { return Goblin(); }
          |exported func main() int {
          |  m = GoblinMaker();
          |  arr = MakeVaryArray(1, &m);
          |  set arr.0 = Goblin();
          |  return 4;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }


  test("Test array length") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |exported func main() int {
          |  a = MakeArray(11, {_});
          |  return len(&a);
          |}
        """.stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(11) => }
  }

  test("Map using array construct") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |exported func main() int {
          |  board = MakeArray(5, {_});
          |  result =
          |      MakeArray(5, &(i) => {
          |        board[i] + 2
          |      });
          |  return result.2;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(4) => }
  }

  test("Map from hardcoded values") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |func toArray<M, N, E>(seq &[#N]<_>E) []<M>E
          |where M Mutability {
          |  return MakeArray(N, { seq[_] });
          |}
          |exported func main() int {
          |  return #[#][6, 4, 3, 5, 2, 8].toArray<mut>()[3];
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(5) => }
  }

  test("Nested imm arrays") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |exported func main() int {
        |  return #[#][#[#][6, 60].toImmArray(), #[#][4, 40].toImmArray(), #[#][3, 30].toImmArray()].toImmArray()[2][1];
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(30) => }
  }

  test("Array foreach") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |import array.each.*;
        |import ifunction.ifunction1.*;
        |exported func main() int {
        |  sum = 0;
        |  [#][6, 60, 103].each(&IFunction1<mut, int, void>({ set sum = sum + _; }));
        |  return sum;
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonInt(169) => }
  }

  test("Array has") {
    val compile = RunCompilation.test(
        """
          |import array.has.*;
          |exported func main() bool {
          |  return (&[#][6, 60, 103]).has(103);
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) match { case VonBool(true) => }
  }


  test("each on SSA") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |import array.iter.*;
          |exported func main() {
          |  planets = [#]["Venus", "Earth", "Mars"];
          |  foreach planet in planets {
          |    print(planet);
          |  }
          |}
          |""".stripMargin)
    compile.evalForStdout(Vector()) shouldEqual "VenusEarthMars"
  }

  test("Change mutability") {
    val compile = RunCompilation.test(
      """import array.make.*;
        |exported func main() str {
        |  a = MakeArray(10, { str(_) });
        |  b = a.toImmArray();
        |  return a.3;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) match { case VonStr("3") => }
  }

  test("Reports when making new imm rsa without lambda") {
    val compile = RunCompilation.test(
      """
        |exported func main() int {
        |  a = #[](3);
        |  a.push(73);
        |  return a.0;
        |}
      """.stripMargin)

    compile.getCompilerOutputs().expectErr() match {
      case NewImmRSANeedsCallable(_) =>
    }
  }

//  test("Destroy lambda with mutable captures") {
//    val compile = RunCompilation.test(
//      Samples.get("generics/iter.vale") +
//        """
//          |exported func main() int {
//          |  list = Array<mut, int>(3, *!IFunction1<mut, int, int>({_}));
//          |  n = 7;
//          |  newArray =
//          |      Array<mut, int>(3, *!IFunction1<mut, int, int>((index) => {
//          |        = if (index == 1) {
//          |            = n;
//          |          } else {
//          |            a = list.(index);
//          |            = a * 2;
//          |          }
//          |      }));
//          |  return newArray.0;
//          |}
//          |""".stripMargin)
//    compile.evalForKind(Vector()) match { case VonInt(0) => }
//  }



//  test("Map using map()") {
//    val compile = RunCompilation.test(
//      """
//        |func map
//        |:(n: Int, T: reference, F: kind)
//        |(arr: *[n T], generator: *F) {
//        |  Array<mut>(n, (i) => { generator(arr.(i))})
//        |}
//        |exported func main() int {
//        |  board = Array<mut>(5, (x) => { x});
//        |  result = map(board, {_});
//        |  return result.3;
//        |}
//      """.stripMargin)
//
//    compile.evalForKind(Vector()) match { case VonInt(3) => }
//  }



  // if we want to make sure that our thing returns an int, then we can
  // try and cast it to a callable:
  // func makeArray<T>(size: Int, callable: (Int):T) {
}
