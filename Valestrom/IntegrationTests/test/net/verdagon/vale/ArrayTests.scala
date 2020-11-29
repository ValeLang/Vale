package net.verdagon.vale

import com.sun.tools.javac.util.ArrayUtils
import net.verdagon.vale.parser.ImmutableP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.{Compilation, CompilationOptions}

class ArrayTests extends FunSuite with Matchers {
  test("Returning array from function and dotting it") {
    val compile = Compilation(
      """
        |fn makeArray() infer-ret { [2, 3, 4, 5, 6] }
        |fn main() int {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Simple arraysequence and runtime index lookup") {
    val compile = Compilation(
      """
        |fn main() int {
        |  i = 2;
        |  a = [2, 3, 4, 5, 6];
        |  = a[i];
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({
      case ArraySequenceLookup2(_,_,_, _, _) => {
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Take arraysequence as a parameter") {
    val compile = Compilation(
      """
        |fn doThings(arr [<imm> 5 * int]) int {
        |  arr.3
        |}
        |fn main() int {
        |  a = [2, 3, 4, 5, 6];
        |  = doThings(a);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Borrow arraysequence as a parameter") {
    val compile = Compilation(
      """
        |struct MutableStruct {
        |  x int;
        |}
        |
        |fn doThings(arr &[3 * ^MutableStruct]) int {
        |  arr.2.x
        |}
        |fn main() int {
        |  a = [MutableStruct(2), MutableStruct(3), MutableStruct(4)];
        |  = doThings(a);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Simple array map") {
    val compile = Compilation(
      """
        |fn main() int {
        |  a = Array<imm, int>(10, &IFunction1<imm, int, int>({_}));
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("Array")
    main.only({
      case ConstructArray2(UnknownSizeArrayT2(RawArrayT2(Coord(Share, Int2()), Immutable)), _, _, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array map taking a closure which captures something") {
    val compile = Compilation(
      """
        |fn main() int {
        |  x = 7;
        |  a = Array<imm, int>(10, &IFunction1<imm, int, int>({_ + x}));
        |  = a.3;
        |}
      """.stripMargin)
//    val compile = Compilation(
//      """
//        |struct MyIntFunctor { x: Int; }
//        |impl IFunction1<mut, int, int> for MyIntFunctor;
//        |fn __call(this: &MyIntFunctor for IFunction1<mut, int, int>, i: Int) int { this.x + i }
//        |fn main() int {
//        |  x = 7;
//        |  a = Array<imm>(10, MyIntFunctor(x));
//        |  = a.3;
//        |}
//      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }

  test("Simple array map with runtime index lookup") {
    val compile = Compilation(
      """
        |fn main() int {
        |  a = Array<imm, int>(10, &IFunction1<imm, int, int>({_}));
        |  i = 5;
        |  = a[i];
        |}
      """.stripMargin)
//    val compile = Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl IFunction1<mut, int, int> for MyIntIdentity;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, int, int>, i: Int) int { i }
//        |fn main() int {
//        |  m = MyIntIdentity();
//        |  a = Array<imm>(10, &m);
//        |  i = 5;
//        |  = a.(i);
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested array") {
    val compile = Compilation(
      """
        |fn main() int {
        |  = [[2]].0.0;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(2)
  }


  test("Two dimensional array") {
    val compile = Compilation(
      """
        |fn main() int {
        |  board =
        |      Array<mut, Array<mut, int>>(
        |          3,
        |          &IFunction1<imm, int, Array<mut, int>>((row){
        |              Array<mut, int>(
        |                  3,
        |                  &IFunction1<imm, int, int>((col){ row + col}))}));
        |  = board.1.2;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array with capture") {
    val compile = Compilation(
      """
        |struct IntBox {
        |  i int;
        |}
        |
        |fn main() int {
        |  box = IntBox(7);
        |  board =
        |      Array<mut>(
        |          3,
        |          &IFunction1<mut, int, int>(
        |              (col){ box.i }));
        |  = board.1;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  // Known failure 2020-08-20
  test("Arr helper with capture") {
    val compile = Compilation(
      """
        |fn Arr<M, F>(n int, generator &F) Array<M, T>
        |rules(M Mutability, T Ref, Prot("__call", (&F, int), T))
        |{
        |  Array<M>(n, &IFunction1<mut, int, T>(generator))
        |}
        |
        |struct IntBox {
        |  i int;
        |}
        |
        |fn main() int {
        |  box = IntBox(7);
        |  lam = (col){ box.i };
        |  board =
        |      Arr<mut>(
        |          3,
        |          &lam);
        |  = board.1;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }


  test("Mutate array") {
    val compile = Compilation(
      """
        |fn main() int {
        |  arr = Array<mut, int>(3, &IFunction1<imm, int, int>((row){row}));
        |  mut arr[1] = 1337;
        |  = arr.1;
        |}
      """.stripMargin)
//    val compile = Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl IFunction1<mut, int, int> for MyIntIdentity;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, int, int>, i: Int) int { i }
//        |fn main() int {
//        |  m = MyIntIdentity();
//        |  arr = Array<mut>(10, &m);
//        |  mut arr.(1) = 1337;
//        |  = arr.1;
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }

  test("Capture mutable array") {
    val compile = Compilation(
      """
        |struct MyIntIdentity {}
        |impl IFunction1<mut, int, int> for MyIntIdentity;
        |fn __call(this &MyIntIdentity impl IFunction1<mut, int, int>, i int) int { i }
        |fn main() {
        |  m = MyIntIdentity();
        |  arr = Array<mut, int>(10, m);
        |  lam = { println(arr.6); };
        |  (lam)();
        |}
      """.stripMargin +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale"))

    compile.evalForStdout(Vector()) shouldEqual "6\n"
  }

  test("Swap out of array") {
    val compile = Compilation(
      """
        |struct Goblin { }
        |
        |struct GoblinMaker {}
        |impl IFunction1<mut, int, Goblin> for GoblinMaker;
        |fn __call(this &GoblinMaker impl IFunction1<mut, int, Goblin>, i int) Goblin { Goblin() }
        |fn main() int {
        |  m = GoblinMaker();
        |  arr = Array<mut, Goblin>(1, m);
        |  mut arr.0 = Goblin();
        |  = 4;
        |}
      """.stripMargin,
      CompilationOptions(profiler = new Profiler()))

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }


  test("Test array length") {
    val compile = Compilation(
      """
        |fn main() int {
        |  a = Array<mut, int>(11, &IFunction1<imm, int, int>({_}));
        |  = len(a);
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("Map using array construct") {
    val compile = Compilation(
      """
        |fn main() int {
        |  board = Array<mut, int>(5, IFunction1<imm, int, int>((x){x}));
        |  result =
        |      Array<mut, int>(5, &IFunction1<mut, int, int>((i){
        |        board[i] + 2
        |      }));
        |  = result.2;
        |}
      """.stripMargin)
//    val compile = Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl IFunction1<mut, int, int> for MyIntIdentity;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, int, int>, i: Int) int { i }
//        |
//        |struct MyMappingFunctor {
//        |  board: &Array<mut, int>;
//        |}
//        |impl IFunction1<mut, int, int> for MyMappingFunctor;
//        |fn __call(this: &MyMappingFunctor for IFunction1<mut, int, int>, i: Int) int {
//        |  board = this.board;
//        |  old = board.(i);
//        |  = old + 2;
//        |}
//        |
//        |fn main() int {
//        |  m = MyIntIdentity();
//        |  board = Array<mut>(10, &m);
//        |
//        |  mapper = MyMappingFunctor(&board);
//        |  result = Array<mut>(5, &mapper);
//        |  = result.2;
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Map from hardcoded values") {
    val compile = Compilation(
      """fn toArray<M, N, E>(seq &[<_> N * E]) Array<M, E>
        |rules(M Mutability) {
        |  Array<M, E>(N, &IFunction1<imm, int, int>((i){ seq[i]}))
        |}
        |fn main() int {
        |  [6, 4, 3, 5, 2, 8].toArray<mut>()[3]
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested imm arrays") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
      """fn main() int {
        |  [[6, 60].toArray<imm>(), [4, 40].toArray<imm>(), [3, 30].toArray<imm>()].toArray<imm>()[2][1]
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(30)
  }

  // Known failure 2020-08-05
  test("Array foreach") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
      """fn main() int {
        |  sum = 0;
        |  [6, 60, 103].each(&IFunction1<mut, int, void>({ mut sum = sum + _; }));
        |  = sum;
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(169)
  }

  test("Array has") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
        """fn main() bool {
          |  [6, 60, 103].has(103)
          |}
          |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

//  test("Destroy lambda with mutable captures") {
//    val compile = Compilation(
//      Samples.get("generics/arrayutils.vale") +
//        """
//          |fn main() int {
//          |  list = Array<mut, int>(3, &IFunction1<mut, int, int>({_}));
//          |  n = 7;
//          |  newArray =
//          |      Array<mut, int>(3, &IFunction1<mut, int, int>((index){
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
//    compile.evalForReferend(Vector()) shouldEqual VonInt(0)
//  }



//  test("Map using map()") {
//    val compile = Compilation(
//      """
//        |fn map
//        |:(n: Int, T: reference, F: referend)
//        |(arr: &[n T], generator: &F) {
//        |  Array<mut>(n, (i){ generator(arr.(i))})
//        |}
//        |fn main() int {
//        |  board = Array<mut>(5, (x){ x});
//        |  result = map(board, {_});
//        |  = result.3;
//        |}
//      """.stripMargin)
//
//    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
//  }



  // if we want to make sure that our thing returns an int, then we can
  // try and cast it to a callable:
  // fn makeArray<T>(size: Int, callable: (Int):T) {
}
