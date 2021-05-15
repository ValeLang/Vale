package net.verdagon.vale

import com.sun.tools.javac.util.ArrayUtils
import net.verdagon.vale.parser.ImmutableP
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.{VonBool, VonInt, VonStr}
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.{Compilation, CompilationOptions}

class ArrayTests extends FunSuite with Matchers {
  test("Returning static array from function and dotting it") {
    val compile = Compilation(
      """
        |fn makeArray() infer-ret { [][2, 3, 4, 5, 6] }
        |fn main() int export {
        |  makeArray().3
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Simple static array and runtime index lookup") {
    val compile = Compilation(
      """
        |fn main() int export {
        |  i = 2;
        |  a = [][2, 3, 4, 5, 6];
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

  test("Static array from lambda") {
    val compile = Compilation(
      """
        |fn main() int export {
        |  i = 2;
        |  a = [5]({_ * 2});
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
        |fn main() int export {
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
        |fn main() int export {
        |  a = [MutableStruct(2), MutableStruct(3), MutableStruct(4)];
        |  = doThings(&a);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }

  // the argument to __Array doesnt even have to be a struct or a lambda or an
  // interface or whatever, its just passed straight through to the prototype
  test("array map with int") {
    val compile = Compilation(
      """
        |fn makeElement(lol int, i int) int { i }
        |
        |fn main() int
        |rules(F Prot = Prot("makeElement", (int, int), int))
        |export {
        |  a = __Array<imm, int, int, F>(10, 1337);
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("__Array")
    main.only({
      case ConstructArray2(UnknownSizeArrayT2(RawArrayT2(Coord(Share, Readonly, Int2()), Immutable)), _, _, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("array map with lambda") {
    val compile = Compilation(
      """
        |struct Lam imm {}
        |fn makeElement(lam Lam, i int) int { i }
        |
        |fn main() int
        |rules(F Prot = Prot("makeElement", (Lam, int), int))
        |export {
        |  a = __Array<imm, int, Lam, F>(10, Lam());
        |  = a.3;
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("__Array")
    main.only({
      case ConstructArray2(UnknownSizeArrayT2(RawArrayT2(Coord(Share, Readonly, Int2()), Immutable)), _, _, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("MakeArray map with struct") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |struct Lam imm {}
          |fn __call(lam Lam, i int) int { i }
          |
          |fn main() int export {
          |  a = MakeArray(10, Lam());
          |  = a.3;
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("MakeArray map with lambda") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn main() int export {
          |  a = MakeArray(10, {_});
          |  = a.3;
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("array map with interface") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeImmArray.vale"),
        """
          |fn main() int export {
          |  a = MakeImmArray(10, &!IFunction1<imm, int, int>({_}));
          |  = a.3;
          |}
        """.stripMargin))

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("__Array")
    main.only({
      case ConstructArray2(UnknownSizeArrayT2(RawArrayT2(Coord(Share, Readonly, Int2()), Immutable)), _, _, _) =>
    })

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array map taking a closure which captures something") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeImmArray.vale"),
        """
          |fn main() int export {
          |  x = 7;
          |  a = MakeImmArray(10, { _ + x });
          |  = a.3;
          |}
        """.stripMargin))
    compile.evalForReferend(Vector()) shouldEqual VonInt(10)
  }

  test("Simple array map with runtime index lookup") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeImmArray.vale"),
        """
          |fn main() int export {
          |  a = MakeImmArray(10, {_});
          |  i = 5;
          |  = a[i];
          |}
        """.stripMargin))
//    val compile = Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl IFunction1<mut, int, int> for MyIntIdentity;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, int, int>, i: Int) int { i }
//        |fn main() int export {
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
        |fn main() int export {
        |  = [[2]].0.0;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(2)
  }


  test("Two dimensional array") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn main() int export {
          |  board =
          |      MakeArray(
          |          3,
          |          (row){ MakeArray(3, { row + _ }) });
          |  = board.1.2;
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array with capture") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |struct IntBox {
          |  i int;
          |}
          |
          |fn main() int export {
          |  box = IntBox(7);
          |  board = MakeArray(3, &!(col){ box.i });
          |  = board.1;
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }

  // Known failure 2020-08-20
  test("Capture") {
    val compile = Compilation(
      """
        |fn myFunc<F>(generator &F) T
        |rules(T Ref, Prot("__call", (&F, int), T))
        |{
        |  myFunc(generator)
        |}
        |
        |struct IntBox {
        |  i int;
        |}
        |
        |fn main() int export {
        |  box = IntBox(7);
        |  lam = (col){ box.i };
        |  board = myFunc(&lam);
        |  = board.1;
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(7)
  }


  test("Mutate array") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn main() int export {
          |  arr = MakeArray(3, {_});
          |  set arr[1] = 1337;
          |  = arr.1;
          |}
        """.stripMargin))
//    val compile = Compilation(
//      """
//        |struct MyIntIdentity {}
//        |impl IFunction1<mut, int, int> for MyIntIdentity;
//        |fn __call(this: &MyIntIdentity for IFunction1<mut, int, int>, i: Int) int { i }
//        |fn main() int export {
//        |  m = MyIntIdentity();
//        |  arr = Array<mut>(10, &m);
//        |  mut arr.(1) = 1337;
//        |  = arr.1;
//        |}
//      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }

  test("Capture mutable array") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |struct MyIntIdentity {}
          |impl IFunction1<mut, int, int> for MyIntIdentity;
          |fn __call(this &!MyIntIdentity impl IFunction1<mut, int, int>, i int) int { i }
          |fn main() {
          |  m = MyIntIdentity();
          |  arr = MakeArray(10, &!m);
          |  lam = { print(str(arr.6)); };
          |  (lam)();
          |}
        """.stripMargin))

    compile.evalForStdout(Vector()) shouldEqual "6"
  }

  test("Swap out of array") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |struct Goblin { }
          |
          |struct GoblinMaker {}
          |impl IFunction1<mut, int, Goblin> for GoblinMaker;
          |fn __call(this &!GoblinMaker impl IFunction1<mut, int, Goblin>, i int) Goblin { Goblin() }
          |fn main() int export {
          |  m = GoblinMaker();
          |  arr = MakeArray(1, &!m);
          |  set arr.0 = Goblin();
          |  = 4;
          |}
        """.stripMargin),
        CompilationOptions(profiler = new Profiler()))

    compile.evalForReferend(Vector()) shouldEqual VonInt(4)
  }


  test("Test array length") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn main() int export {
          |  a = MakeArray(11, {_});
          |  = len(&a);
          |}
        """.stripMargin))
    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("Map using array construct") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn main() int export {
          |  board = MakeArray(5, {_});
          |  result =
          |      MakeArray(5, &!(i){
          |        board[i] + 2
          |      });
          |  = result.2;
          |}
        """.stripMargin))
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
//        |fn main() int export {
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
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/MakeArray.vale"),
        """
          |fn toArray<M, N, E>(seq &[<_> N * E]) Array<M, E>
          |rules(M Mutability) {
          |  MakeArray(N, { seq[_] })
          |}
          |fn main() int export {
          |  [6, 4, 3, 5, 2, 8].toArray<mut>()[3]
          |}
          |""".stripMargin))
    compile.evalForReferend(Vector()) shouldEqual VonInt(5)
  }

  test("Nested imm arrays") {
    val compile = Compilation.multiple(List(
      Samples.get("libraries/MakeImmArray.vale"),
      Samples.get("libraries/ksaToImmArray.vale"),
      """fn main() int export {
        |  [[6, 60].toImmArray(), [4, 40].toImmArray(), [3, 30].toImmArray()].toImmArray()[2][1]
        |}
        |""".stripMargin))
    compile.evalForReferend(Vector()) shouldEqual VonInt(30)
  }

  // Known failure 2020-08-05
  test("Array foreach") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
      """fn main() int export {
        |  sum! = 0;
        |  [6, 60, 103].each(&!IFunction1<mut, int, void>({ set sum = sum + _; }));
        |  = sum;
        |}
        |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonInt(169)
  }

  test("Array has") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
        """fn main() bool export {
          |  [6, 60, 103].has(103)
          |}
          |""".stripMargin)
    compile.evalForReferend(Vector()) shouldEqual VonBool(true)
  }


  test("each on KSA") {
    val compile = Compilation(
      Samples.get("libraries/arrayutils.vale") +
        """fn main() {
          |  planets = ["Venus", "Earth", "Mars"];
          |  each planets (planet){
          |    print(planet);
          |  }
          |}
          |""".stripMargin)
    compile.evalForStdout(Vector()) shouldEqual "VenusEarthMars"
  }

  test("Change mutability") {
    val compile = Compilation.multiple(List(
      Samples.get("libraries/MakeArray.vale"),
      Samples.get("libraries/MakeImmArray.vale"),
      Samples.get("libraries/usaToImmArray.vale"),
      """
        |fn main() str export {
        |  a = MakeArray(10, { str(_) });
        |  b = a.toImmArray();
        |  = a.3;
        |}
      """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonStr("3")
  }

//  test("Destroy lambda with mutable captures") {
//    val compile = Compilation(
//      Samples.get("generics/arrayutils.vale") +
//        """
//          |fn main() int export {
//          |  list = Array<mut, int>(3, &!IFunction1<mut, int, int>({_}));
//          |  n = 7;
//          |  newArray =
//          |      Array<mut, int>(3, &!IFunction1<mut, int, int>((index){
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
//        |fn main() int export {
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
