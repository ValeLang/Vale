package dev.vale.typing

import dev.vale.Collector.ProgramWithExpect
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{RuleError, Step}
import dev.vale.typing.OverloadResolver.{FindFunctionFailure, InferFailure, WrongNumberOfArguments}
import dev.vale.typing.ast.{ConstantIntTE, DestroyTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionDefinitionT, KindExportT, LetAndLendTE, LetNormalTE, LocalLookupTE, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SoftLoadTE, referenceExprResultKind, referenceExprResultStructName, _}
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.infer.{KindIsNotConcrete, OwnershipDidntMatch}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{CodeLocationS, Collector, Err, FileCoordinateMap, PackageCoordinate, RangeS, Tests, vassert, vassertOne, vpass, _}
//import dev.vale.typingpass.infer.NotEnoughToSolveError
import org.scalatest._

import scala.collection.immutable.List
import scala.io.Source

class CompilerLambdaTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }

  test("Simple lambda") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() int { return { 7 }(); }
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    coutputs.lookupLambdaIn("main").header.returnType shouldEqual CoordT(ShareT, RegionT(), IntT.i32)
    coutputs.lookupFunction("main").header.returnType shouldEqual CoordT(ShareT, RegionT(), IntT.i32)
  }

  test("Lambda with one magic arg") {
    val compile =
      CompilerTestCompilation.test(
        """
          |exported func main() int { return {_}(3); }
          |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // Make sure it inferred the param type and return type correctly
    Collector.only(coutputs.lookupLambdaIn("main"),
      { case ParameterT(_, None, _, CoordT(ShareT, _, IntT.i32)) => })

    coutputs.lookupLambdaIn("main").header.returnType shouldEqual
      CoordT(ShareT, RegionT(), IntT.i32)
  }

  test("Lambda is reused") {
    // Since we call it with an int both times, the template generic should only generate one generic.

    val compile = CompilerTestCompilation.test(
      """
        |exported func main() {
        |  lam = x => x;
        |  lam(4);
        |  lam(7);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // There should be three lambdas generated
    // See GLIOGN
    val lambdas = coutputs.lookupLambdasIn("main")
    vassert(lambdas.size == 1)
  }

  test("Lambda called with different types") {
    // Since we call it with an int both times, the template generic should only generate one generic.

    val compile = CompilerTestCompilation.test(
      """
        |exported func main() {
        |  lam = x => x;
        |  lam(4);
        |  lam(true);
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // There should be three lambdas generated
    // See GLIOGN
    val lambdas = coutputs.lookupLambdasIn("main")
    vassert(lambdas.size == 2)
  }

  test("Curried lambda") {
    val compile = CompilerTestCompilation.test(
      """
        |exported func main() {
        |  lam = x => y => 7;
        |  lam(true)(4);
        |  lam(true)("hello");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // There should be three lambdas generated:
    //  * lam(true)
    //  * lam(true)(4)
    //  * lam(true)("hello")
    // See GLIOGN
    val lambdas = coutputs.lookupLambdasIn("main")
    vassert(lambdas.size == 3)
  }


  // Test that the lambda's arg is the right type, and the name is right
  test("Lambda with a type specified param") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.arith.*;
        |exported func main() int {
        |  return (a int) => {+(a,a)}(3);
        |}
        |""".stripMargin);
    val coutputs = compile.expectCompilerOutputs()

    val lambda = coutputs.lookupLambdaIn("main");

    // Check that the param type is right
    Collector.only(lambda, { case ParameterT(CodeVarNameT(StrI("a")), None, _, CoordT(ShareT, _, IntT.i32)) => {} })
    // Check the name is right
    vassert(coutputs.nameIsLambdaIn(lambda.header.id, "main"))

    val main = coutputs.lookupFunction("main");
    Collector.only(main, { case FunctionCallTE(callee, _) if coutputs.nameIsLambdaIn(callee.id, "main") => })
  }

  test("Tests lambda and concept function") {
    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.print.*;
        |import v.builtins.drop.*;
        |import v.builtins.str.*;
        |
        |func moo<X, F>(x X, f F)
        |where func(&F, &X)void, func drop(X)void, func drop(F)void {
        |  f(&x);
        |}
        |exported func main() {
        |  moo("hello", { print(_); });
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Lambda inside different function with same name") {
    // This originally didn't work because both helperFunc(:Int) and helperFunc(:Str)
    // made a closure struct called helperFunc:lam1, which collided.
    // We need to disambiguate by parameters, not just template args.

    val compile = CompilerTestCompilation.test(
      """
        |import printutils.*;
        |
        |func helperFunc(x int) {
        |  { print(x); }();
        |}
        |func helperFunc(x str) {
        |  { print(x); }();
        |}
        |exported func main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }

  test("Lambda inside template") {
    // This originally didn't work because both helperFunc<int> and helperFunc<Str>
    // made a closure struct called helperFunc:lam1, which collided.
    // This is what spurred paackage support.

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.drop.*;
        |import printutils.*;
        |
        |func helperFunc<T>(x T)
        |where func print(&T)void, func drop(T)void
        |{
        |  { print(x); }();
        |}
        |exported func main() {
        |  helperFunc(4);
        |  helperFunc("bork");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }


  test("Curried lambda inside template") {
    val compile = CompilerTestCompilation.test(
      """import v.builtins.drop.*;
        |func helper<T>(x &T) &T {
        |  lam = a => b => x;
        |  return lam(true)(7);
        |}
        |exported func main() {
        |  helper(4);
        |  helper("bork");
        |}
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()

    // There should be two lambdas generated:
    //  * helper<helper$0>.lam:3:9.__call{true}
    //  * helper<helper$0>.lam:3:9.__call{true}.lam:3:14.__call{int}
    // See GLIOGN
    val lambdas = coutputs.lookupLambdasIn("helper")
    vassert(lambdas.size == 2)
  }

}
