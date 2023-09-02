package dev.vale.typing

import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{RuleError, Step}
import dev.vale.typing.OverloadResolver.{FindFunctionFailure, InferFailure, WrongNumberOfArguments}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, FunctionCallTE, FunctionDefinitionT, FunctionHeaderT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SoftLoadTE, _}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.infer.{KindIsNotConcrete, OwnershipDidntMatch}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinateMap, PackageCoordinate, RangeS, Tests, vassert, vassertOne, vpass, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List
import scala.io.Source

class CompilerHgmTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Tests live borrow") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |#!DeriveStructDrop
        |struct Ship { hp int; }
        |func bork(x live&Ship) int {
        |  x.hp + x.hp
        |}
        |exported func main() int {
        |  ship = Ship(42);
        |  x = bork(&ship);
        |  [_] = ship;
        |  return x;
        |}
        """.stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")
    vimpl()
  }

  test("Tests pre borrow") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |#!DeriveStructDrop
        |struct Ship { hp int; }
        |func bork(x pre&Ship) int {
        |  x.hp
        |}
        |exported func main() int {
        |  ship = Ship(42);
        |  x = bork(&ship);
        |  [_] = ship;
        |  return x;
        |}
        """.stripMargin)
    val main = compile.expectCompilerOutputs().lookupFunction("main")

    val bork = compile.expectCompilerOutputs().lookupFunction("bork")
    bork.header.params.head.preChecked shouldEqual true
  }

}
