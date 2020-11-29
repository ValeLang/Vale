package net.verdagon.vale

import net.verdagon.vale.templar.{CodeVarName2, FullName2}
import net.verdagon.vale.templar.env.AddressibleLocalVariable2
import net.verdagon.vale.templar.types.Varying
import net.verdagon.vale.driver.Compilation
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class ArrayListTest extends FunSuite with Matchers {
  test("Simple ArrayList, no optionals") {
    val compile = Compilation(
      """
        |struct List<E> rules(E Ref) {
        |  array! Array<mut, E>;
        |}
        |fn len<E>(list &List<E>) int { len(list.array) }
        |fn add<E>(list &List<E>, newElement E) {
        |  newArray =
        |      Array<mut, E>(len(list) + 1, &IFunction1<mut, int, int>((index){
        |        = if (index == len(list)) {
        |            = ^newElement;
        |          } else {
        |            a = list.array;
        |            = a[index];
        |          }
        |      }));
        |  mut list.array = ^newArray;
        |}
        |// todo: make that return a &E
        |fn get<E>(list &List<E>, index int) E {
        |  a = list.array;
        |  = a[index];
        |}
        |
        |fn main() int {
        |  l =
        |      List<int>(
        |           Array<mut, int>(
        |               0,
        |               &IFunction1<mut, int, int>((index){
        |                 index
        |               })));
        |  add(l, 5);
        |  add(l, 9);
        |  add(l, 7);
        |  = l.get(1);
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list with optionals") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/utils.vale"),
        Samples.get("libraries/castutils.vale"),
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/list.vale"),
        """
          |
          |fn main() int {
          |  l =
          |      List<int>(
          |          Array<mut, Opt<int>>(
          |              0,
          |              &IFunction1<mut, int, Opt<int>>((index){
          |                result Opt<int> = Some(index);
          |                = ^result;
          |              })),
          |          0);
          |  add(l, 5);
          |  add(l, 9);
          |  add(l, 7);
          |  = l.get(1);
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list zero-constructor") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        """
          |
          |fn main() int {
          |  l = List<int>();
          |  add(l, 5);
          |  add(l, 9);
          |  add(l, 7);
          |  = l.get(1);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Array list len") {
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/list.vale"),
        """
          |
          |fn main() int {
          |  l = List<int>();
          |  add(l, 5);
          |  add(l, 9);
          |  add(l, 7);
          |  = l.len();
          |}
        """.stripMargin))

    compile.evalForReferend(Vector()) shouldEqual VonInt(3)
  }

  test("Array list set") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
      Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        """
          |
          |fn main() int {
          |  l = List<int>();
          |  add(l, 5);
          |  add(l, 9);
          |  add(l, 7);
          |  set(l, 1, 11);
          |  = l.get(1);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(11)
  }

  test("Array list with optionals with mutable element") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
      Samples.get("libraries/opt.vale") +
      Samples.get("libraries/list.vale") +
        """
          |struct Marine { hp int; }
          |
          |fn main() int {
          |  l =
          |      List<Marine>(
          |          Array<mut, Opt<Marine>>(
          |              0,
          |              &IFunction1<mut, int, Opt<Marine>>((index){
          |                result Opt<Marine> = Some(Marine(index));
          |                = ^result;
          |              })),
          |          0);
          |  add(l, Marine(5));
          |  add(l, Marine(9));
          |  add(l, Marine(7));
          |  = l.get(1).hp;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Mutate mutable from in lambda") {
    val compile = Compilation(
        """
          |struct Marine { hp int; }
          |
          |fn main() int {
          |  m = Marine(6);
          |  lam = {
          |    mut m = Marine(9);
          |  };
          |  lam();
          |  lam();
          |  = m.hp;
          |}
        """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main");
    main.variables.collect({ case AddressibleLocalVariable2(FullName2(_, CodeVarName2("m")), Varying, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(9)
  }

  test("Move mutable from in lambda") {
    val compile = Compilation(
      Samples.get("libraries/opt.vale") +
      """
        |struct Marine { hp int; }
        |
        |fn main() int {
        |  m Opt<Marine> = Some(Marine(6));
        |  lam = {
        |    m2 = (mut m = None<Marine>()).get();
        |    = m2.hp;
        |  };
        |  = lam();
        |}
      """.stripMargin)

    val temputs = compile.getTemputs()
    val main = temputs.lookupFunction("main");
    main.variables.collect({ case AddressibleLocalVariable2(FullName2(_, CodeVarName2("m")), Varying, _) => })

    compile.evalForReferend(Vector()) shouldEqual VonInt(6)
  }


  test("Remove from middle") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        """
          |struct Marine { hp int; }
          |
          |fn main() {
          |  l = List<Marine>();
          |  add(l, Marine(5));
          |  add(l, Marine(7));
          |  add(l, Marine(9));
          |  add(l, Marine(11));
          |  add(l, Marine(13));
          |  l.remove(2);
          |  vassert(l.get(0).hp == 5);
          |  vassert(l.get(1).hp == 7);
          |  vassert(l.get(2).hp == 11);
          |  vassert(l.get(3).hp == 13);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector())
  }




  test("Remove from beginning") {
    val compile = Compilation(
      Samples.get("libraries/utils.vale") +
        Samples.get("libraries/printutils.vale") +
        Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        """
          |struct Marine { hp int; }
          |
          |fn main() {
          |  l = List<Marine>();
          |  add(l, Marine(5));
          |  add(l, Marine(7));
          |  l.remove(0);
          |  l.remove(0);
          |  vassert(l.len() == 0);
          |}
        """.stripMargin)

    compile.evalForReferend(Vector())
  }
}
