package net.verdagon.vale

import net.verdagon.vale.templar.ast.LetNormalTE
import net.verdagon.vale.templar.env.AddressibleLocalVariableT
import net.verdagon.vale.templar.names.{CodeVarNameT, FullNameT}
import net.verdagon.vale.templar.types.VaryingT
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class ArrayListTest extends FunSuite with Matchers {
  test("Simple ArrayList, no optionals") {
    val compile = RunCompilation.test(
        """
          |struct List<E> rules(E Ref) {
          |  array! []<mut>E;
          |}
          |fn len<E>(list &List<E>) int { ret len(&list.array); }
          |fn add<E>(list &!List<E>, newElement E) {
          |  newArray = Array<mut, E>(len(&list) + 1);
          |  while (newArray.len() < newArray.capacity()) {
          |    index = newArray.len();
          |    if (index == len(&list)) {
          |      newArray!.push(newElement);
          |    } else {
          |      a = list.array;
          |      newArray!.push(a[index]);
          |    }
          |  }
          |  set list.array = newArray;
          |}
          |// todo: make that return a &E
          |fn get<E>(list &List<E>, index int) E {
          |  a = list.array;
          |  ret a[index];
          |}
          |
          |fn main() int export {
          |  l = List<int>(Array<mut, int>(0));
          |  add(&!l, 5);
          |  add(&!l, 9);
          |  add(&!l, 7);
          |  ret l.get(1);
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Doubling ArrayList") {
    val compile = RunCompilation.test(
      """
        |import list.*;
        |
        |fn main() int export {
        |  l = List<int>(Array<mut, int>(0));
        |  add(&!l, 5);
        |  add(&!l, 9);
        |  add(&!l, 7);
        |  ret l.get(1);
        |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
  }

  test("Array list with optionals") {
    val compile = RunCompilation.test(
      """import list.*;
        |import ifunction.ifunction1.*;
        |
        |fn main() int export {
        |  l =
        |      List<int>(
        |          Array<mut, int>(
        |              0,
        |              &!(index) => {
        |                0
        |              }));
        |  add(&!l, 5);
        |  add(&!l, 9);
        |  add(&!l, 7);
        |  ret l.get(1);
        |}
      """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
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
          |  ret l.get(1);
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
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
          |  ret l.len();
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(3)
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
          |  ret l.get(1);
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(11)
  }

  test("Array list with optionals with mutable element") {
    val compile = RunCompilation.test(
        """import list.*;
          |struct Marine { hp int; }
          |
          |fn main() int export {
          |  l =
          |      List<Marine>(
          |          Array<mut, Marine>(
          |              0,
          |              (index) => { Marine(index) }));
          |  add(&!l, Marine(5));
          |  add(&!l, Marine(9));
          |  add(&!l, Marine(7));
          |  ret l.get(1).hp;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
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
          |  ret m.hp;
          |}
        """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main");
    Collector.only(main, {
      case LetNormalTE(AddressibleLocalVariableT(FullNameT(_, _, CodeVarNameT("m")), VaryingT, _), _) => {
        vpass()
      }
    })

    compile.evalForKind(Vector()) shouldEqual VonInt(9)
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
        |    m2.hp
        |  };
        |  ret lam!();
        |}
      """.stripMargin)

    val temputs = compile.expectTemputs()
    val main = temputs.lookupFunction("main");
    Collector.only(main, { case LetNormalTE(AddressibleLocalVariableT(FullNameT(_, _, CodeVarNameT("m")), VaryingT, _), _) => })

    compile.evalForKind(Vector()) shouldEqual VonInt(6)
  }


  test("Remove from middle") {
    val compile = RunCompilation.test(
        """import list.*;
          |import panicutils.*;
          |struct Marine { hp int; }
          |
          |fn main() export {
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

    compile.evalForKind(Vector())
  }




  test("Remove from beginning") {
    val compile = RunCompilation.test(
        """import list.*;
          |import panicutils.*;
          |struct Marine { hp int; }
          |
          |fn main() export {
          |  l = List<Marine>();
          |  add(&!l, Marine(5));
          |  add(&!l, Marine(7));
          |  l!.remove(0);
          |  l!.remove(0);
          |  vassert(l.len() == 0);
          |}
        """.stripMargin)

    compile.evalForKind(Vector())
  }
}
