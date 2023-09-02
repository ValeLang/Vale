package dev.vale.typing

import dev.vale.typing.ast.{FunctionCallTE, ParameterT}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names._
import dev.vale.typing.templata.MutabilityTemplataT
import dev.vale.typing.types._
import dev.vale.{Collector, vassert, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers}

import scala.io.Source

class CompilerArrayTests extends FunSuite with Matchers {

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Make array and dot it") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  a = [#]int(6, 60, 103);
        |  x = 2;
        |  [_, _, _] = a;
        |  return 2;
        |}
        |""".stripMargin)
    compile.expectCompilerOutputs()
  }

  test("Test Array of StructTemplata") {
    val compile = CompilerTestCompilation.test(
      """
        |struct Vec2 imm {
        |  x float;
        |  y float;
        |}
        |struct Pattern imm {
        |  patternTiles []<imm>Vec2;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Report when multiple types in array") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int {
        |  arr = [#](true, 42);
        |  return arr.1;
        |}
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ArrayElementsHaveDifferentTypes(_, types)) => {
        vassertSome(types.collectFirst({ case CoordT(ShareT, _, IntT.i32) => }))
        vassertSome(types.collectFirst({ case CoordT(ShareT, _, BoolT()) => }))
      }
    }
  }

  test("Test receiving imm array") {
    val compile = CompilerTestCompilation.test(
      """
        |extern func __vbi_panic() __Never;
        |func zork(arr #[]int) {
        |  __vbi_panic();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("zork")
    main.header.params.head.tyype.kind match { case contentsRuntimeSizedArrayTT(MutabilityTemplataT(ImmutableT), _, _) => }
  }

  test("Test receiving mut array") {
    val compile = CompilerTestCompilation.test(
      """
        |extern func __vbi_panic() __Never;
        |func zork(arr []int) {
        |  __vbi_panic();
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("zork")
    main.header.params.head.tyype.kind match { case contentsRuntimeSizedArrayTT(MutabilityTemplataT(MutableT), _, _) => }
  }

  test("Test creating mut array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.panic.*;
        |
        |exported func main() {
        |  arr = []int(7);
        |  __vbi_panic();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
    val arr = Collector.only(main, { case ReferenceLocalVariableT(localName, _, c) => c })
    arr.kind match { case contentsRuntimeSizedArrayTT(MutabilityTemplataT(MutableT), _, _) => }
  }

  test("Test destroying array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |
        |exported func main() {
        |  arr = []int(7);
        |  [] = arr;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Test adding to array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_push.*;
        |import v.builtins.panic.*;
        |
        |exported func main() {
        |  arr = []int(7);
        |  arr.push(5);
        |  __vbi_panic();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Test reading from array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_push.*;
        |import v.builtins.panic.*;
        |
        |exported func main() {
        |  arr = []int(7);
        |  arr.push(5);
        |  x = arr[0];
        |  __vbi_panic();
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Test removing from array") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_push.*;
        |import v.builtins.runtime_sized_array_pop.*;
        |
        |exported func main() {
        |  arr = []int(7);
        |  arr.push(5);
        |  arr.pop();
        |  [] = arr;
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
    val main = coutputs.lookupFunction("main")
  }

  test("Reports when exported SSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |export [#5]<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Reports when exported RSA depends on non-exported element") {
    val compile = CompilerTestCompilation.test(
      """
        |export []<imm>Raza as RazaArray;
        |struct Raza imm { }
        |""".stripMargin)
    compile.getCompilerOutputs() match {
      case Err(ExportedImmutableKindDependedOnNonExportedKind(_, _, _, _)) =>
    }
  }

  test("Test MakeArray") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panicutils.*;
        |import v.builtins.arith.*;
        |import array.make.*;
        |import v.builtins.arrays.*;
        |import v.builtins.drop.*;
        |
        |exported func main() int {
        |  a = MakeArray<int>(11, {_});
        |  return len(&a);
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test array push, pop, len, capacity") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_push.*;
        |import v.builtins.runtime_sized_array_len.*;
        |import v.builtins.runtime_sized_array_capacity.*;
        |
        |exported func main() void {
        |  arr = Array<mut, int>(9);
        |  arr.push(420);
        |  arr.push(421);
        |  arr.push(422);
        |  arr.len();
        |  arr.capacity();
        |  [] = arr;
        |}
      """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test array drop") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.runtime_sized_array_mut_new.*;
        |import v.builtins.runtime_sized_array_mut_drop.*;
        |
        |exported func main() void {
        |  arr = Array<mut, int>(9);
        |  // implicit drop with pops
        |}
    """.stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Test cellular automata") {
    val compile =
      CompilerTestCompilation.test(
        readCodeFromResource("programs/cellularautomata.vale"))
    compile.expectCompilerOutputs()
  }
}
