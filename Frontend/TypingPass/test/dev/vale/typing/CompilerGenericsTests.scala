package dev.vale.typing

import dev.vale._
import OverloadResolver.FindFunctionFailure
import dev.vale.postparsing.CodeNameS
import dev.vale.typing.ast.RestackifyTE
import dev.vale.typing.env.ReferenceLocalVariableT
import dev.vale.typing.names.CodeVarNameT
import dev.vale.vassert
import dev.vale.typing.templata._
import dev.vale.typing.types._
import org.scalatest._

import scala.collection.immutable.List
import scala.io.Source

class CompilerGenericsTests extends FunSuite with Matchers {
  // TODO: pull all of the typingpass specific stuff out, the unit test-y stuff

  def readCodeFromResource(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }


  test("Upcasting with generic bounds") {

    val compile = CompilerTestCompilation.test(
      """
        |import v.builtins.panic.*;
        |import v.builtins.drop.*;
        |
        |#!DeriveInterfaceDrop
        |sealed interface XOpt<T Ref> where func drop(T)void {
        |  func harvest(virtual opt XOpt<T>) &T;
        |}
        |
        |#!DeriveStructDrop
        |struct XNone<T Ref> where func drop(T)void  { }
        |
        |impl<T> XOpt<T> for XNone<T>;
        |
        |func harvest<T>(opt XNone<T>) &T {
        |  __vbi_panic();
        |}
        |
        |exported func main() int {
        |  m XOpt<int> = XNone<int>();
        |  return (m).harvest();
        |}
        |
        |""".stripMargin)
    val coutputs = compile.expectCompilerOutputs()
  }
}
