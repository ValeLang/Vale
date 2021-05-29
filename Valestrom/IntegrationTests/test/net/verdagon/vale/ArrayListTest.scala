package net.verdagon.vale

import net.verdagon.vale.templar.{CodeVarName2, FullName2}
import net.verdagon.vale.templar.env.AddressibleLocalVariable2
import net.verdagon.vale.templar.types.Varying
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class ArrayListTest extends FunSuite with Matchers {
  test("Simple ArrayList, no optionals") {
    val compile = RunCompilation.test(
        """
          |struct List<E> rules(E Ref) {
          |  array! Array<mut, vary, E>;
          |}
          |fn listLen<E>(list &List<E>) int { len(&list.array) }
          |fn add<E>(list &!List<E>, newElement E) {
          |  newArray =
          |      [vary *](listLen(&list) + 1, &!IFunction1<mut, int, int>((index){
          |        = if (index == listLen(&list)) {
          |            = newElement;
          |          } else {
          |            a = list.array;
          |            = a[index];
          |          }
          |      }));
          |  set list.array = newArray;
          |}
          |// todo: make that return a &E
          |fn get<E>(list &List<E>, index int) E {
          |  a = list.array;
          |  = a[index];
          |}
          |
          |fn main() int export {
          |  l =
          |      List<int>(
          |           [vary *](
          |               0,
          |               &!IFunction1<mut, int, int>((index){
          |                 index
          |               })));
          |  add(&!l, 5);
          |  add(&!l, 9);
          |  add(&!l, 7);
          |  = l.get(1);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list with optionals") {
    val compile = RunCompilation.test(
      """import list.*;
        |
        |fn main() int export {
        |  l =
        |      List<int>(
        |          MakeVaryArray(
        |              0,
        |              (index){
        |                result Opt<int> = Some(index);
        |                = result;
        |              }),
        |          0);
        |  add(&!l, 5);
        |  add(&!l, 9);
        |  add(&!l, 7);
        |  = l.get(1);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list zero-constructor") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |fn main() int export {
          |  l = List<int>();
          |  add(&!l, 5);
          |  add(&!l, 9);
          |  add(&!l, 7);
          |  = l.get(1);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list len") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |fn main() int export {
          |  l = List<int>();
          |  add(&!l, 5);
          |  add(&!l, 9);
          |  add(&!l, 7);
          |  = l.len();
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array list set") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |fn main() int export {
          |  l = List<int>();
          |  add(&!l, 5);
          |  add(&!l, 9);
          |  add(&!l, 7);
          |  set(&!l, 1, 11);
          |  = l.get(1);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("Array list with optionals with mutable element") {
    val compile = RunCompilation.test(
        """import list.*;
          |struct Marine { hp int; }
          |
          |fn main() int export {
          |  l =
          |      List<Marine>(
          |          MakeVaryArray<Opt<Marine>>(
          |              0,
          |              (index){
          |                result Opt<Marine> = Some(Marine(index));
          |                = result;
          |              }),
          |          0);
          |  add(&!l, Marine(5));
          |  add(&!l, Marine(9));
          |  add(&!l, Marine(7));
          |  = l.get(1).hp;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Mutate mutable from in lambda") {
    val compile = RunCompilation.test(
        """import list.*;
          |struct Marine { hp int; }
          |
          |fn main() int export {
          |  m! = Marine(6);
          |  lam = {
          |    set m = Marine(9);
          |  };
          |  lam!();
          |  lam!();
          |  = m.hp;
          |}
        """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main");
    main.variables.collect({ case AddressibleLocalVariable2(FullName2(_, CodeVarName2("m")), Varying, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Move mutable from in lambda") {
    val compile = RunCompilation.test(
      """import list.*;
        |struct Marine { hp int; }
        |
        |fn main() int export {
        |  m! Opt<Marine> = Some(Marine(6));
        |  lam = {
        |    m2 = (set m = None<Marine>()).get();
        |    = m2.hp;
        |  };
        |  = lam!();
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main");
    main.variables.collect({ case AddressibleLocalVariable2(FullName2(_, CodeVarName2("m")), Varying, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }


  test("Remove from middle") {
    val compile = RunCompilation.test(
        """import list.*;
          |import panicutils.*;
          |struct Marine { hp int; }
          |
          |fn main() {
          |  l = List<Marine>();
          |  add(&!l, Marine(5));
          |  add(&!l, Marine(7));
          |  add(&!l, Marine(9));
          |  add(&!l, Marine(11));
          |  add(&!l, Marine(13));
          |  l!.remove(2);
          |  vassert(l.get(0).hp == 5);
          |  vassert(l.get(1).hp == 7);
          |  vassert(l.get(2).hp == 11);
          |  vassert(l.get(3).hp == 13);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector())
  }




  test("Remove from beginning") {
    val compile = RunCompilation.test(
        """import list.*;
          |import panicutils.*;
          |struct Marine { hp int; }
          |
          |fn main() {
          |  l = List<Marine>();
          |  add(&!l, Marine(5));
          |  add(&!l, Marine(7));
          |  l!.remove(0);
          |  l!.remove(0);
          |  vassert(l.len() == 0);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector())
  }
}
