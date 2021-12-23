package net.verdagon.vale

import com.sun.tools.javac.util.ArrayUtils
import net.verdagon.vale.parser.ImmutableP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{NewImmRuntimeSizedArrayTE, RuntimeSizedArrayLookupTE, StaticSizedArrayLookupTE}
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonBool, VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}

class ArrayTests extends FunSuite with Matchers {
  test("Returning static array from function and dotting it") {
    val compile = RunCompilation.test(
      """
        |fn makeArray() infer-ret { [][2, 3, 4, 5, 6] }
        |fn main() int export {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Simple static array and runtime index lookup") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  i = 2;
        |  a = [][2, 3, 4, 5, 6];
        |  = a[i];
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,_,_, _, _) => {
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Destroy SSA of imms into function") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  a = [][13, 14, 15];
        |  sum = 0;
        |  drop_into(a, &!(e){ set sum = sum + e; });
        |  = sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Destroy RSA of imms into function") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  a = Array<imm>(3, {13 + _});
        |  sum = 0;
        |  drop_into(a, &!(e){ set sum = sum + e; });
        |  = sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Destroy SSA of muts into function") {
    val compile = RunCompilation.test(
      """
        |struct Spaceship { fuel int; }
        |fn main() int export {
        |  a = [][Spaceship(13), Spaceship(14), Spaceship(15)];
        |  sum = 0;
        |  drop_into(a, &!(e){ set sum = sum + e.fuel; });
        |  = sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Destroy RSA of muts into function") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |fn main() int export {
        |  a = MakeVaryArray(3, &!{Spaceship(13 + _)});
        |  sum = 0;
        |  drop_into(a, &!(e){ set sum = sum + e.fuel; });
        |  = sum;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Migrate RSA") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |fn main() int export {
        |  a = Array<mut, Spaceship>(3, &!{Spaceship(41 + _)});
        |  b = Array<mut, Spaceship>(3);
        |  migrate(a, &!b);
        |  = b[1].fuel;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Migrate SSA") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |struct Spaceship { fuel int; }
        |fn main() int export {
        |  a = [3](&!{Spaceship(41 + _)});
        |  b = Array<mut, Spaceship>(3);
        |  migrate(a, &!b);
        |  = b[1].fuel;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Unspecified-mutability static array from lambda defaults to mutable") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  i = 3;
        |  a = [5](*!{_ * 42});
        |  = a[1];
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Immutable static array from lambda") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssaimmfromcallable.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Mutable static array from lambda") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssamutfromcallable.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Immutable static array from values") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssaimmfromvalues.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Mutable static array from values") {
    val compile = RunCompilation.test( Tests.loadExpected("programs/arrays/ssamutfromvalues.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case StaticSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Unspecified-mutability runtime array from lambda defaults to mutable") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |fn main() int export {
        |  i = 3;
        |  a = MakeVaryArray(5, *!{_ * 42});
        |  = a[1];
        |}
        |""".stripMargin)

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Immutable runtime array from lambda") {
    val compile = RunCompilation.test(Tests.loadExpected("programs/arrays/rsaimmfromcallable.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual ImmutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Mutable runtime array from lambda") {
    val compile =
      RunCompilation.test(
        Tests.loadExpected("programs/arrays/rsamutfromcallable.vale"))

    val temputs = compile.expectTemputs()
    Collector.only(temputs.lookupFunction("main"), {
      case RuntimeSizedArrayLookupTE(_,_,arrayType, _, _, _) => {
        arrayType.mutability shouldEqual MutableT
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  //m [<mut> 3 * [<mut> 3 * int]] = [mut][ [mut][1, 2, 3], [mut][4, 5, 6], [mut][7, 8, 9] ];
  test("Take arraysequence as a parameter") {
    val compile = RunCompilation.test(
      """
        |fn doThings(arr [<imm> 5 * int]) int {
        |  arr.3
        |}
        |fn main() int export {
        |  a = [imm][2, 3, 4, 5, 6];
        |  = doThings(a);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Borrow arraysequence as a parameter") {
    val compile = RunCompilation.test(
      """
        |struct MutableStruct {
        |  x int;
        |}
        |
        |fn doThings(arr *[3 * ^MutableStruct]) int {
        |  arr.2.x
        |}
        |fn main() int export {
        |  a = [][MutableStruct(2), MutableStruct(3), MutableStruct(4)];
        |  = doThings(*a);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  // the argument to __Array doesnt even have to be a struct or a lambda or an
  // interface or whatever, its just passed straight through to the prototype
  test("array map with int") {
    val compile = RunCompilation.test(
      """
        |fn __call(lol int, i int) int { i }
        |
        |fn main() int export {
        |  a = [imm *](10, 1337);
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, ReadonlyT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("array map with lambda") {
    val compile = RunCompilation.test(
      """
        |struct Lam imm {}
        |fn __call(lam Lam, i int) int { i }
        |
        |fn main() int
        |rules(F Prot = Prot("__call", (Lam, int), int))
        |export {
        |  a = [imm, final, *](10, Lam());
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, ReadonlyT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("MakeArray map with struct") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |
          |struct Lam imm {}
          |fn __call(lam Lam, i int) int { i }
          |
          |fn main() int export {
          |  a = MakeArray(10, Lam());
          |  = a.3;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("MakeArray map with lambda") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |fn main() int export {
          |  a = MakeArray(10, {_});
          |  = a.3;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("array map with interface") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |import ifunction.ifunction1.*;
          |fn main() int export {
          |  a = Array<imm, int>(10, *!IFunction1<imm, int, int>({_}));
          |  = a.3;
          |}
          |""".stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("Array")
    Collector.only(main, {
      case NewImmRuntimeSizedArrayTE(RuntimeSizedArrayTT(ImmutableT, CoordT(ShareT, ReadonlyT, IntT(_))), _, _, _) =>
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Array map taking a closure which captures something") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |fn main() int export {
          |  x = 7;
          |  a = MakeImmArray(10, { _ + x });
          |  = a.3;
          |}
        """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(10)
  }

  test("Simple array map with runtime index lookup") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |fn main() int export {
          |  a = MakeImmArray(10, {_});
          |  i = 5;
          |  = a[i];
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Nested array") {
    val compile = RunCompilation.test(
      """
        |fn main() int export {
        |  = [[2]].0.0;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(2)
  }


  test("Two dimensional array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |fn main() int export {
          |  board =
          |      MakeArray(
          |          3,
          |          (row){ MakeArray(3, { row + _ }) });
          |  = board.1.2;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
  }

  test("Array with capture") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |struct IntBox {
          |  i int;
          |}
          |
          |fn main() int export {
          |  box = IntBox(7);
          |  board = MakeArray(3, &!(col){ box.i });
          |  = board.1;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }

  test("Capture") {
    val compile = RunCompilation.test(
      """
        |fn myFunc<F>(generator F) T
        |rules(T Ref, Prot("__call", (F, int), T))
        |{
        |  generator!(9)
        |}
        |
        |struct IntBox {
        |  i int;
        |}
        |
        |fn main() int export {
        |  box = IntBox(7);
        |  lam = (col){ box.i };
        |  board = myFunc(&!lam);
        |  = board;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(7)
  }


  test("Mutate array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |fn main() int export {
          |  arr = MakeVaryArray(3, {_});
          |  set arr[1] = 1337;
          |  = arr.1;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(1337)
  }

  test("Capture mutable array") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |import ifunction.ifunction1.*;
          |struct MyIntIdentity {}
          |impl IFunction1<mut, int, int> for MyIntIdentity;
          |fn __call(this &!MyIntIdentity impl IFunction1<mut, int, int>, i int) int { i }
          |fn main() export {
          |  m = MyIntIdentity();
          |  arr = MakeArray(10, &!m);
          |  lam = { print(str(arr.6)); };
          |  (lam)();
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
          |fn __call(this &!GoblinMaker impl IFunction1<mut, int, Goblin>, i int) Goblin { Goblin() }
          |fn main() int export {
          |  m = GoblinMaker();
          |  arr = MakeVaryArray(1, &!m);
          |  set arr.0 = Goblin();
          |  = 4;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }


  test("Test array length") {
    val compile = RunCompilation.test(
        """import array.make.*;
          |fn main() int export {
          |  a = MakeArray(11, {_});
          |  = len(&a);
          |}
        """.stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(11)
  }

  test("Map using array construct") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |fn main() int export {
          |  board = MakeArray(5, {_});
          |  result =
          |      MakeArray(5, &!(i){
          |        board[i] + 2
          |      });
          |  = result.2;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(4)
  }

  test("Map from hardcoded values") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |fn toArray<M, N, E>(seq *[<_> N * E]) Array<M, E>
          |rules(M Mutability) {
          |  MakeArray(N, { seq[_] })
          |}
          |fn main() int export {
          |  [imm][6, 4, 3, 5, 2, 8].toArray<mut>()[3]
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(5)
  }

  test("Nested imm arrays") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |fn main() int export {
        |  [imm][[imm][6, 60].toImmArray(), [imm][4, 40].toImmArray(), [imm][3, 30].toImmArray()].toImmArray()[2][1]
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(30)
  }

  test("Array foreach") {
    val compile = RunCompilation.test(
      """
        |import array.make.*;
        |import array.each.*;
        |import ifunction.ifunction1.*;
        |fn main() int export {
        |  sum! = 0;
        |  [][6, 60, 103].each(&!IFunction1<mut, int, void>({ set sum = sum + _; }));
        |  = sum;
        |}
        |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonInt(169)
  }

  test("Array has") {
    val compile = RunCompilation.test(
        """
          |import array.has.*;
          |fn main() bool export {
          |  [][6, 60, 103].has(103)
          |}
          |""".stripMargin)
    compile.evalForKind(Vector()) shouldEqual VonBool(true)
  }


  test("each on KSA") {
    val compile = RunCompilation.test(
        """
          |import array.make.*;
          |import array.each.*;
          |fn main() export {
          |  planets = []["Venus", "Earth", "Mars"];
          |  each planets (planet){
          |    print(planet);
          |  }
          |}
          |""".stripMargin)
    compile.evalForStdout(Vector()) shouldEqual "VenusEarthMars"
  }

  test("Change mutability") {
    val compile = RunCompilation.test(
      """import array.make.*;
        |fn main() str export {
        |  a = MakeArray(10, { str(_) });
        |  b = a.toImmArray();
        |  = a.3;
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonStr("3")
  }

//  test("Destroy lambda with mutable captures") {
//    val compile = RunCompilation.test(
//      Samples.get("generics/each.vale") +
//        """
//          |fn main() int export {
//          |  list = Array<mut, int>(3, *!IFunction1<mut, int, int>({_}));
//          |  n = 7;
//          |  newArray =
//          |      Array<mut, int>(3, *!IFunction1<mut, int, int>((index){
//          |        = if (index == 1) {
//          |            = n;
//          |          } else {
//          |            a = list.(index);
//          |            = a * 2;
//          |          }
//          |      }));
//          |  = newArray.0;
//          |}
//          |""".stripMargin)
//    compile.evalForKind(Vector()) shouldEqual VonInt(0)
//  }



//  test("Map using map()") {
//    val compile = RunCompilation.test(
//      """
//        |fn map
//        |:(n: Int, T: reference, F: kind)
//        |(arr: *[n T], generator: *F) {
//        |  Array<mut>(n, (i){ generator(arr.(i))})
//        |}
//        |fn main() int export {
//        |  board = Array<mut>(5, (x){ x});
//        |  result = map(board, {_});
//        |  = result.3;
//        |}
//      """.stripMargin)
//
//    compile.evalForKind(Vector()) shouldEqual VonInt(3)
//  }



  // if we want to make sure that our thing returns an int, then we can
  // try and cast it to a callable:
  // fn makeArray<T>(size: Int, callable: (Int):T) {
}
