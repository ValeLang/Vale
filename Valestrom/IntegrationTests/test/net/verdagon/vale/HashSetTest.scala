package net.verdagon.vale

import net.verdagon.vale.driver.Compilation
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

class HashSetTest extends FunSuite with Matchers {
  test("Hash set from KSA") {
    val compile = new Compilation(
      List(
        ("libraries/castutils.vale" -> Samples.get("libraries/castutils.vale")),
        ("libraries/printutils.vale" -> Samples.get("libraries/printutils.vale")),
        ("libraries/arrayutils.vale" -> Samples.get("libraries/arrayutils.vale")),
        ("libraries/opt.vale" -> Samples.get("libraries/opt.vale")),
        ("libraries/list.vale" -> Samples.get("libraries/list.vale")),
        ("libraries/hashset.vale" -> Samples.get("libraries/hashset.vale")),
        ("libraries/utils.vale" -> Samples.get("libraries/utils.vale")),
        ("in.vale" ->
          """
            |fn main() {
            |  m = HashSet<int>([0, 4, 8, 12], IFunction1<mut, int, int>({_}), ==);
            |  assert(m.has(0));
            |  assert(m.has(4));
            |  assert(m.has(8));
            |  assert(m.has(12));
            |  = 111;
            |}
          """.stripMargin)))

    compile.evalForReferend(Vector()) shouldEqual VonInt(111)
  }

  test("Hash set from Array") {
    val compile = new Compilation(
      List(
        ("libraries/castutils.vale" -> Samples.get("libraries/castutils.vale")),
        ("libraries/printutils.vale" -> Samples.get("libraries/printutils.vale")),
        ("libraries/arrayutils.vale" -> Samples.get("libraries/arrayutils.vale")),
        ("libraries/opt.vale" -> Samples.get("libraries/opt.vale")),
        ("libraries/list.vale" -> Samples.get("libraries/list.vale")),
        ("libraries/hashset.vale" -> Samples.get("libraries/hashset.vale")),
        ("libraries/utils.vale" -> Samples.get("libraries/utils.vale")),
        ("in.vale" ->
          """
            |fn main() {
            |  m = HashSet<int>([0, 4, 8, 12].toArray<imm>(), IFunction1<mut, int, int>({_}), ==);
            |  assert(m.has(0));
            |  assert(m.has(4));
            |  assert(m.has(8));
            |  assert(m.has(12));
            |  = 111;
            |}
          """.stripMargin)))

    compile.evalForReferend(Vector()) shouldEqual VonInt(111)
  }

  test("Hash set has") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        Samples.get("libraries/hashset.vale") +
        Samples.get("libraries/utils.vale") +
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

  test("Hash set toArray") {
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        Samples.get("libraries/hashset.vale") +
        Samples.get("libraries/utils.vale") +
        """
          |fn main() {
          |  m = HashSet<int>(IFunction1<mut, int, int>({_}), ==);
          |  m.add(0);
          |  m.add(4);
          |  m.add(8);
          |  m.add(12);
          |  k = m.toArray();
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
    val compile = Compilation(
      Samples.get("libraries/castutils.vale") +
        Samples.get("libraries/printutils.vale") +
      Samples.get("libraries/opt.vale") +
        Samples.get("libraries/list.vale") +
        Samples.get("libraries/hashset.vale") +
        Samples.get("libraries/utils.vale") +
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
