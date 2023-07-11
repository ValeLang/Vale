package dev.vale

import dev.vale.typing.ast.LetNormalTE
import dev.vale.typing.env.AddressibleLocalVariableT
import dev.vale.typing.names.{CodeVarNameT, IdT}
import dev.vale.typing.types.VaryingT
import dev.vale.typing.names.CodeVarNameT
import dev.vale.von.VonInt
import org.scalatest._

class ArrayListTest extends FunSuite with Matchers {
  test("Simple ArrayList, no optionals") {
    val compile = RunCompilation.test(
        """
          |import v.builtins.migrate.*;
          |
          |#!DeriveStructDrop
          |struct List<E Ref> {
          |  array! []<mut>E;
          |}
          |func drop<E>(self List<E>)
          |where func drop(E)void {
          |  [array] = self;
          |  drop(array);
          |}
          |func len<E>(list &List<E>) int { return len(&list.array); }
          |func add<E>(list &List<E>, newElement E) {
          |  oldArray = set list.array = Array<mut, E>(len(&list) + 1);
          |  migrate(oldArray, list.array);
          |  list.array.push(newElement);
          |}
          |func get<E>(list &List<E>, index int) &E {
          |  a = list.array;
          |  return a[index];
          |}
          |exported func main() int {
          |  l = List<int>(Array<mut, int>(0));
          |  add(&l, 5);
          |  add(&l, 9);
          |  add(&l, 7);
          |  return l.get(1);
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Doubling ArrayList") {
    val compile = RunCompilation.test(
      """
        |import list.*;
        |
        |exported func main() int {
        |  l = List<int>(Array<mut, int>(0));
        |  add(&l, 5);
        |  add(&l, 9);
        |  add(&l, 7);
        |  return l.get(1);
        |}
        |
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Array list zero-constructor") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |exported func main() int {
          |  l = List<int>();
          |  add(&l, 5);
          |  add(&l, 9);
          |  add(&l, 7);
          |  return l.get(1);
          |}
          |
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Array list len") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |exported func main() int {
          |  l = List<int>();
          |  add(&l, 5);
          |  add(&l, 9);
          |  add(&l, 7);
          |  return l.len();
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(3) => }
  }

  test("Array list set") {
    val compile = RunCompilation.test(
        """import list.*;
          |
          |exported func main() int {
          |  l = List<int>();
          |  add(&l, 5);
          |  add(&l, 9);
          |  add(&l, 7);
          |  set(&l, 1, 11);
          |  return l.get(1);
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(11) => }
  }

  test("Array list with optionals with mutable element") {
    val compile = RunCompilation.test(
        """import list.*;
          |struct Marine { hp int; }
          |
          |exported func main() int {
          |  l =
          |      List<Marine>(
          |          Array<mut, Marine>(
          |              0,
          |              (index) => { Marine(index) }));
          |  add(&l, Marine(5));
          |  add(&l, Marine(9));
          |  add(&l, Marine(7));
          |  return l.get(1).hp;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Mutate mutable from in lambda") {
    val compile = RunCompilation.test(
        """import list.*;
          |struct Marine { hp int; }
          |
          |exported func main() int {
          |  m = Marine(6);
          |  lam = {
          |    set m = Marine(9);
          |  };
          |  lam();
          |  lam();
          |  return m.hp;
          |}
        """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main");
    Collector.only(main, {
      case LetNormalTE(AddressibleLocalVariableT(CodeVarNameT(StrI("m")), VaryingT, _), _) => {
        vpass()
      }
    })

    compile.evalForKind(Vector()) match { case VonInt(9) => }
  }

  test("Move mutable from in lambda") {
    val compile = RunCompilation.test(
      """import list.*;
        |struct Marine { hp int; }
        |
        |exported func main() int {
        |  m Opt<Marine> = Some(Marine(6));
        |  lam = {
        |    m2 = (set m = None<Marine>()).get();
        |    m2.hp
        |  };
        |  return lam();
        |}
      """.stripMargin)

    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main");
    Collector.only(main, { case LetNormalTE(AddressibleLocalVariableT(CodeVarNameT(StrI("m")), VaryingT, _), _) => })

    compile.evalForKind(Vector()) match { case VonInt(6) => }
  }


  test("Remove from middle") {
    val compile = RunCompilation.test(
        """import list.*;
          |import panicutils.*;
          |struct Marine { hp int; }
          |
          |exported func main() {
          |  l = List<Marine>();
          |  add(&l, Marine(5));
          |  add(&l, Marine(7));
          |  add(&l, Marine(9));
          |  add(&l, Marine(11));
          |  add(&l, Marine(13));
          |  l.remove(2);
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
          |exported func main() {
          |  l = List<Marine>();
          |  add(&l, Marine(5));
          |  add(&l, Marine(7));
          |  l.remove(0);
          |  l.remove(0);
          |  vassert(l.len() == 0);
          |}
        """.stripMargin)

    compile.evalForKind(Vector())
  }
}
