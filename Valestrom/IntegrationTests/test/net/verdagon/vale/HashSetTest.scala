package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class HashSetTest extends FunSuite with Matchers {
  test("Hash set has") {
    val compile = new Compilation(
      Samples.get("castutils.vale") +
        Samples.get("printutils.vale") +
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashset.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashSet<int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0);
          |  m.add(4);
          |  m.add(8);
          |  m.add(12);
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

  test("Hash set keys") {
    val compile = new Compilation(
      Samples.get("castutils.vale") +
        Samples.get("printutils.vale") +
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashset.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashSet<int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0);
          |  m.add(4);
          |  m.add(8);
          |  m.add(12);
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

  test("Hash set remove") {
    val compile = new Compilation(
      Samples.get("castutils.vale") +
        Samples.get("printutils.vale") +
      Samples.get("genericvirtuals/opt.vale") +
        Samples.get("genericvirtuals/optingarraylist.vale") +
        Samples.get("genericvirtuals/hashset.vale") +
        Samples.get("utils.vale") +
        """
          |fn main() {
          |  m = HashSet<int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0);
          |  m.add(4);
          |  m.add(8);
          |  m.add(12);
          |  assert(m.has(8));
          |  m.remove(8);
          |  assert(not m.has(8));
          |  m.add(8);
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
