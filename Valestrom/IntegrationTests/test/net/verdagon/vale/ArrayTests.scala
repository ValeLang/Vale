package net.verdagon.vale

import com.sun.tools.javac.util.ArrayUtils
import net.verdagon.vale.parser.ImmutableP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonBool, VonInt}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class ArrayTests extends FunSuite with Matchers {
  test("Simple arraysequence and compiletime index lookup") {
    val compile = new Compilation(
      """
        |fn main() {
        |  a = [2, 3, 4, 5, 6];
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main")
    main.only({
      case LetNormal2(ReferenceLocalVariable2(FullName2(_, CodeVarName2("a")), _, _), expr) => {
        expr.resultRegister.reference.referend match {
          case ArraySequenceT2(5, RawArrayT2(Coord(Share, Int2()), Immutable)) =>
        }
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Returning array from function and dotting it") {
    val compile = new Compilation(
      """
        |fn makeArray() { [2, 3, 4, 5, 6] }
        |fn main() {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Simple arraysequence and runtime index lookup") {
    val compile = new Compilation(
      """
        |fn main() {
        |  i = 2;
        |  a = [2, 3, 4, 5, 6];
        |  = a[i];
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    temputs.lookupFunction("main").only({
      case ArraySequenceLookup2(_, _, _) => {
      }
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Take arraysequence as a parameter") {
    val compile = new Compilation(
      """
        |fn doThings(arr [5 * *Int]) {
        |  arr.3
        |}
        |fn main() {
        |  a = [2, 3, 4, 5, 6];
        |  = doThings(a);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Borrow arraysequence as a parameter") {
    val compile = new Compilation(
      """
        |struct MutableStruct {
        |  x *Int;
        |}
        |
        |fn doThings(arr &[3 * ^MutableStruct]) {
        |  arr.2.x
        |}
        |fn main() {
        |  a = [MutableStruct(2), MutableStruct(3), MutableStruct(4)];
        |  = doThings(&a);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  test("Simple array map") {
    val compile = new Compilation(
      """
        |fn main() {
        |  a = Array<imm, Int>(10, &IFunction1<imm, Int, Int>({_}));
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("Array")
    main.only({
      case ConstructArray2(UnknownSizeArrayT2(RawArrayT2(Coord(Share, Int2()), Immutable)), _, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array map taking a closure which captures something") {
    val compile = new Compilation(
      """
        |fn main() {
        |  x = 7;
        |  a = Array<imm, Int>(10, &IFunction1<imm, Int, Int>({_ + x}));
        |  = a.3;
        |}
      """.stripMargin)
//    val compile = new Compilation(
//      """
//        |struct MyIntFunctor { x: Int; }
//        |impl MyIntFunctor for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyIntFunctor for IFunction1<mut, Int, Int>, i: Int) Int { this.x + i }
//        |fn main() {
//        |  x = 7;
//        |  a = Array<imm>(10, MyIntFunctor(x));
//        |  = a.3;
//        |}
//      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }

  test("Simple array map with runtime index lookup") {
    val compile = new Compilation(
      """
        |fn main() {
        |  a = Array<imm, Int>(10, &IFunction1<imm, Int, Int>({_}));
        |  i = 5;
        |  = a[i];
        |}
      """.stripMargin)
//    val compile = new Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl MyIntIdentity for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, Int, Int>, i: Int) Int { i }
//        |fn main() {
//        |  m = MyIntIdentity();
//        |  a = Array<imm>(10, &m);
//        |  i = 5;
//        |  = a.(i);
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested array") {
    val compile = new Compilation(
      """
        |fn main() {
        |  = [[2]].0.0;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(2)
  }


  test("Two dimensional array") {
    val compile = new Compilation(
      """
        |fn main() {
        |  board =
        |      Array<mut, Array<mut, Int>>(
        |          3,
        |          &IFunction1<imm, Int, Array<mut, Int>>((row){
        |              Array<mut, Int>(
        |                  3,
        |                  &IFunction1<imm, Int, Int>((col){ row + col}))}));
        |  = board.1.2;
        |}
      """.stripMargin)
//    val compile = new Compilation(
//      """
//        |struct MyInnerIntFunctor { x: Int; }
//        |impl MyInnerIntFunctor for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyInnerIntFunctor for IFunction1<mut, Int, Int>, i: Int) Int { this.x + i }
//        |
//        |
//        |struct MyOuterIntFunctor {}
//        |impl MyOuterIntFunctor for IFunction1:(mut, Int, Array<imm, Int>);
//        |fn __call(this: &MyOuterIntFunctor for IFunction1:(mut, Int, Array<imm, Int>, i: Int) Array<imm, Int> {
//        |  innerIntFunctor = MyInnerIntFunctor(i);
//        |  = Array<imm>(20, &innerIntFunctor);
//        |}
//        |
//        |fn main() {
//        |  outerIntFunctor = MyOuterIntFunctor();
//        |  board = Array<imm>(20, &outerIntFunctor);
//        |  = board.8.9;
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }


  test("Mutate array") {
    val compile = new Compilation(
      """
        |fn main() {
        |  arr = Array<mut, Int>(3, &IFunction1<imm, Int, Int>((row){row}));
        |  mut arr[1] = 1337;
        |  = arr.1;
        |}
      """.stripMargin)
//    val compile = new Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl MyIntIdentity for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, Int, Int>, i: Int) Int { i }
//        |fn main() {
//        |  m = MyIntIdentity();
//        |  arr = Array<mut>(10, &m);
//        |  mut arr.(1) = 1337;
//        |  = arr.1;
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }

  test("Capture mutable array") {
    val compile = new Compilation(
      """
        |struct MyIntIdentity {}
        |impl MyIntIdentity for IFunction1<mut, Int, Int>;
        |fn __call(this &MyIntIdentity impl IFunction1<mut, Int, Int>, i Int) Int { i }
        |fn main() {
        |  m = MyIntIdentity();
        |  arr = Array<mut, Int>(10, &m);
        |  lam = { println(arr.6); };
        |  (lam)();
        |}
      """.stripMargin)

    compile.evalForStdout(Vector()) shouldEqual "6\n"
  }


  test("Test array length") {
    val compile = new Compilation(
      """
        |fn main() {
        |  a = Array<mut, Int>(11, &IFunction1<imm, Int, Int>({_}));
        |  = len(&a);
        |}
      """.stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("Map using array construct") {
    val compile = new Compilation(
      """
        |fn main() {
        |  board = Array<mut, Int>(5, IFunction1<imm, Int, Int>((x){x}));
        |  result =
        |      Array<mut, Int>(5, &IFunction1<mut, Int, Int>((i){
        |        board[i] + 2
        |      }));
        |  = result.2;
        |}
      """.stripMargin)
//    val compile = new Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl MyIntIdentity for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, Int, Int>, i: Int) Int { i }
//        |
//        |struct MyMappingFunctor {
//        |  board: &Array<mut, Int>;
//        |}
//        |impl MyMappingFunctor for IFunction1<mut, Int, Int>;
//        |fn __call(this: &MyMappingFunctor for IFunction1<mut, Int, Int>, i: Int) Int {
//        |  board = this.board;
//        |  old = board.(i);
//        |  = old + 2;
//        |}
//        |
//        |fn main() {
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
    val compile = new Compilation(
      """fn toArray<M, N, E>(seq &[<_> N * E]) rules(M Mutability) {
        |  Array<M, E>(N, &IFunction1<imm, Int, Int>((i){ seq[i]}))
        |}
        |fn main() {
        |  [6, 4, 3, 5, 2, 8].toArray<mut>()[3]
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested imm arrays") {
    val compile = new Compilation(
      ArrayUtils.code +
      """fn main() {
        |  [[6, 60].toArray<imm>(), [4, 40].toArray<imm>(), [3, 30].toArray<imm>()].toArray<imm>()[2][1]
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(30)
  }

  test("Array foreach") {
    val compile = new Compilation(
      ArrayUtils.code +
      """fn main() {
        |  sum = 0;
        |  [6, 60, 103].each(&IFunction1<mut, Int, Void>({ mut sum = sum + _; }));
        |  = sum;
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(169)
  }

  test("Array has") {
    val compile = new Compilation(
      ArrayUtils.code +
        """fn main() {
          |  [6, 60, 103].has(103)
          |}
          |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }

//  test("Destroy lambda with mutable captures") {
//    val compile = new Compilation(
//      ArrayUtils.code +
//        """
//          |fn main() {
//          |  list = Array<mut, Int>(3, &IFunction1<mut, Int, Int>({_}));
//          |  n = 7;
//          |  newArray =
//          |      Array<mut, Int>(3, &IFunction1<mut, Int, Int>((index){
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
//    val compile = new Compilation(
//      """
//        |fn map
//        |:(n: Int, T: reference, F: referend)
//        |(arr: &[n T], generator: &F) {
//        |  Array<mut>(n, (i){ generator(arr.(i))})
//        |}
//        |fn main() {
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
