package net.verdagon.vale

import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env.ReferenceLocalVariable2
import net.verdagon.vale.templar.types._
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}
import net.verdagon.vale.driver.Compilation

class HashMapTest extends FunSuite with Matchers {
  test("Hash map collisions") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
      Samples.get("genericvirtuals/optingarraylist.vale") +
      Samples.get("genericvirtuals/hashmap.vale") +
      Samples.get("utils.vale") +
      """
        |fn main() {
        |  m = HashMap<int, int>({_}, ==);
        |  m.add(0, 100);
        |  m.add(4, 101);
        |  m.add(8, 102);
        |  m.add(12, 103);
        |  m.add(16, 104);
        |  m.add(20, 105);
        |  m.add(24, 106);
        |  m.add(28, 107);
        |  m.add(32, 108);
        |  m.add(36, 109);
        |  m.add(40, 110);
        |  m.add(44, 111);
        |  assertEq(m.get(0).get(), 100, "val at 0 not 100!");
        |  assertEq(m.get(4).get(), 101, "val at 1 not 101!");
        |  assertEq(m.get(8).get(), 102, "val at 2 not 102!");
        |  assertEq(m.get(12).get(), 103, "val at 3 not 103!");
        |  assertEq(m.get(16).get(), 104, "val at 4 not 104!");
        |  assertEq(m.get(20).get(), 105, "val at 5 not 105!");
        |  assertEq(m.get(24).get(), 106, "val at 6 not 106!");
        |  assertEq(m.get(28).get(), 107, "val at 7 not 107!");
        |  assertEq(m.get(32).get(), 108, "val at 8 not 108!");
        |  assertEq(m.get(36).get(), 109, "val at 9 not 109!");
        |  assertEq(m.get(40).get(), 110, "val at 10 not 110!");
        |  assertEq(m.get(44).get(), 111, "val at 11 not 111!");
        |  assert(m.get(1337).empty?(), "expected nothing at 1337!");
        |  = m.get(44).get();
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(111)
  }

  test("Hash map with functors") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
      Samples.get("genericvirtuals/optingarraylist.vale") +
      Samples.get("genericvirtuals/hashmap.vale") +
      Samples.get("utils.vale") +
      """
        |struct IntHasher { }
        |fn __call(this &IntHasher, x int) { x }
        |
        |struct IntEquator { }
        |fn __call(this &IntEquator, a int, b int) { a == b }
        |
        |fn add42(map &HashMap<int, int, IntHasher, IntEquator>) {
        |  map.add(42, 100);
        |}
        |
        |fn main() {
        |  m = HashMap<int, int, IntHasher, IntEquator>(IntHasher(), IntEquator());
        |  add42(&m);
        |  = m.get(42).get();
        |}
      """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(100)
  }

  test("Hash map with struct as key") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashmap.vale") +
        Samples.get("utils.vale") +
        """
          |struct Location imm {
          |  groupX int;
          |  groupY int;
          |  indexInGroup int;
          |}
          |
          |struct LocationHasher { }
          |fn __call(this &LocationHasher, loc Location) {
          |  hash = 0;
          |  mut hash = 41 * hash + loc.groupX;
          |  mut hash = 41 * hash + loc.groupY;
          |  mut hash = 41 * hash + loc.indexInGroup;
          |  = hash;
          |}
          |
          |struct LocationEquator { }
          |fn __call(this &LocationEquator, a Location, b Location) {
          |  (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup)
          |}
          |
          |fn main() {
          |  m = HashMap<Location, int>(LocationHasher(), LocationEquator());
          |  m.add(Location(4, 5, 6), 100);
          |  = m.get(Location(4, 5, 6)).get();
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(100)
  }

  test("Hash map has") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashmap.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashMap<int, int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0, 100);
          |  m.add(4, 101);
          |  m.add(8, 102);
          |  m.add(12, 103);
          |  assert(m.has(0));
          |  assert(not(m.has(1)));
          |  assert(not(m.has(2)));
          |  assert(not(m.has(3)));
          |  assert(m.has(4));
          |  assert(m.has(8));
          |  assert(m.has(12));
          |  = 111;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(111)
  }

  test("Hash map keys") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashmap.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashMap<int, int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0, 100);
          |  m.add(4, 101);
          |  m.add(8, 102);
          |  m.add(12, 103);
          |  k = m.keys();
          |  assertEq(k.len(), 4);
          |  assertEq(k[0], 0);
          |  assertEq(k[1], 4);
          |  assertEq(k[2], 8);
          |  assertEq(k[3], 12);
          |  = 1337;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }

  test("Hash map with mutable values") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashmap.vale") +
        Samples.get("utils.vale") +
        """
          |struct Plane {}
          |
          |fn main() {
          |  m = HashMap<int, Plane>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0, Plane());
          |  m.add(4, Plane());
          |  m.add(8, Plane());
          |  m.add(12, Plane());
          |  assert(m.has(0));
          |  assert(m.has(4));
          |  assert(m.has(8));
          |  assert(m.has(12));
          |  m.remove(12);
          |  assert(not m.has(12));
          |  = 1337;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }

  test("Hash map remove") {
    val compile = new Compilation(
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashmap.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashMap<int, int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0, 100);
          |  m.add(4, 101);
          |  m.add(8, 102);
          |  m.add(12, 103);
          |  assert(m.has(8));
          |  m.remove(8);
          |  assert(not m.has(8));
          |  m.add(8, 102);
          |  assert(m.has(8));
          |  assert(m.has(4));
          |  m.remove(4);
          |  assert(not m.has(4));
          |  = 1337;
          |}
        """.stripMargin)

    compile.evalForReferend(Vector()) shouldEqual VonInt(1337)
  }
}
