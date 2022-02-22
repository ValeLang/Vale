package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariableT
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class HashMapTest extends FunSuite with Matchers {
  test("Hash map update") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  m!.update(8, 108);
          |  ret m.get(8).get();
          |}
          |""".stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(108) => }
  }

  test("Hash map collisions") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  m!.add(16, 104);
          |  m!.add(20, 105);
          |  m!.add(24, 106);
          |  m!.add(28, 107);
          |  m!.add(32, 108);
          |  m!.add(36, 109);
          |  m!.add(40, 110);
          |  m!.add(44, 111);
          |  vassertEq(m.get(0).get(), 100, "val at 0 not 100!");
          |  vassertEq(m.get(4).get(), 101, "val at 1 not 101!");
          |  vassertEq(m.get(8).get(), 102, "val at 2 not 102!");
          |  vassertEq(m.get(12).get(), 103, "val at 3 not 103!");
          |  vassertEq(m.get(16).get(), 104, "val at 4 not 104!");
          |  vassertEq(m.get(20).get(), 105, "val at 5 not 105!");
          |  vassertEq(m.get(24).get(), 106, "val at 6 not 106!");
          |  vassertEq(m.get(28).get(), 107, "val at 7 not 107!");
          |  vassertEq(m.get(32).get(), 108, "val at 8 not 108!");
          |  vassertEq(m.get(36).get(), 109, "val at 9 not 109!");
          |  vassertEq(m.get(40).get(), 110, "val at 10 not 110!");
          |  vassertEq(m.get(44).get(), 111, "val at 11 not 111!");
          |  vassert(m.get(1337).isEmpty(), "expected nothing at 1337!");
          |  ret m.get(44).get();
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(111) => }
  }

  test("Hash map with functors") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |func add42(map &!HashMap<int, int, IntHasher, IntEquator>) {
          |  map!.add(42, 100);
          |}
          |
          |exported func main() int {
          |  m = HashMap<int, int, IntHasher, IntEquator>(IntHasher(), IntEquator());
          |  add42(&!m);
          |  ret m.get(42).get();
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(100) => }
  }

  test("Hash map with struct as key") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |
          |struct Location imm {
          |  groupX int;
          |  groupY int;
          |  indexInGroup int;
          |}
          |
          |struct LocationHasher { }
          |func __call(this &LocationHasher, loc Location) int {
          |  hash! = 0;
          |  set hash = 41 * hash + loc.groupX;
          |  set hash = 41 * hash + loc.groupY;
          |  set hash = 41 * hash + loc.indexInGroup;
          |  ret hash;
          |}
          |
          |struct LocationEquator { }
          |func __call(this &LocationEquator, a Location, b Location) bool {
          |  ret (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup);
          |}
          |
          |exported func main() int {
          |  m = HashMap<Location, int>(LocationHasher(), LocationEquator());
          |  m!.add(Location(4, 5, 6), 100);
          |  ret m.get(Location(4, 5, 6)).get();
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(100) => }
  }

  test("Hash map has") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  vassert(m.has(0));
          |  vassert(not(m.has(1)));
          |  vassert(not(m.has(2)));
          |  vassert(not(m.has(3)));
          |  vassert(m.has(4));
          |  vassert(m.has(8));
          |  vassert(m.has(12));
          |  ret 111;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(111) => }
  }

  test("Hash map keys") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  k = m.keys();
          |  vassertEq(k.len(), 4);
          |  vassertEq(k[0], 0);
          |  vassertEq(k[1], 4);
          |  vassertEq(k[2], 8);
          |  vassertEq(k[3], 12);
          |  ret 1337;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }

  test("Hash map values") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  k = m.values();
          |  vassertEq(k.len(), 4);
          |  vassertEq(k[0], 100);
          |  vassertEq(k[1], 101);
          |  vassertEq(k[2], 102);
          |  vassertEq(k[3], 103);
          |  ret 1337;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }

  test("Hash map with mutable values") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |struct Plane {}
          |
          |exported func main() int {
          |  m = HashMap<int, Plane>(IntHasher(), IntEquator());
          |  m!.add(0, Plane());
          |  m!.add(4, Plane());
          |  m!.add(8, Plane());
          |  m!.add(12, Plane());
          |  vassert(m.has(0));
          |  vassert(m.has(4));
          |  vassert(m.has(8));
          |  vassert(m.has(12));
          |  m!.remove(12);
          |  vassert(not m.has(12));
          |  ret 1337;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }

  test("Hash map remove") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 100);
          |  m!.add(4, 101);
          |  m!.add(8, 102);
          |  m!.add(12, 103);
          |  vassert(m.has(8));
          |  m!.remove(8);
          |  vassert(not m.has(8));
          |  m!.add(8, 102);
          |  vassert(m.has(8));
          |  vassert(m.has(4));
          |  m!.remove(4);
          |  vassert(not m.has(4));
          |  ret 1337;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }

  test("Hash map remove 2") {
    val compile = RunCompilation.test(
        """
          |import hashmap.*;
          |import panicutils.*;
          |
          |exported func main() int {
          |  m = HashMap<int, int>(IntHasher(), IntEquator());
          |  m!.add(0, 0);
          |  m!.add(1, 1);
          |  m!.add(2, 2);
          |  m!.add(3, 3);
          |  m!.remove(1);
          |  m!.remove(2);
          |  m!.add(4, 4);
          |
          |  values = m.values();
          |  vassertEq(values.len(), 3, "wat");
          |  vassertEq(values[0], 0, "wat");
          |  vassertEq(values[1], 3, "wat");
          |  vassertEq(values[2], 4, "wat");
          |  ret 1337;
          |}
        """.stripMargin)

    compile.evalForKind(Vector()) match { case VonInt(1337) => }
  }
}
