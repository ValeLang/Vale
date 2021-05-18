package net.verdagon.vale

import net.verdagon.vale.driver.Compilation.builtins
import net.verdagon.vale.driver.{Compilation, CompilationOptions}
import net.verdagon.vale.metal._
import net.verdagon.vale.{metal => m}
import net.verdagon.von.VonInt
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class ImportTests extends FunSuite with Matchers {
  test("Tests import") {
    val moduleACode =
      """
        |import moduleB.moo;
        |
        |fn main() int export {
        |  a = moo();
        |  = a;
        |}
      """.stripMargin

    val moduleBCode =
      """
        |fn moo() int { 42 }
      """.stripMargin

    val compile =
      new Compilation(
        List("moduleA"),
        {
          case NamespaceCoordinate("moduleA", List()) => Map("test.vale" -> moduleACode)
          case NamespaceCoordinate("moduleB", List()) => Map("moo.vale" -> moduleBCode)
          case NamespaceCoordinate("", List()) => builtins
          case x => vfail("Couldn't find module: " + x)
        },
        CompilationOptions())

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }

  test("Tests non-imported module isn't brought in") {
    val moduleACode =
      """
        |fn main() int export {
        |  a = 42;
        |  = a;
        |}
      """.stripMargin

    val moduleBCode =
      """
        |fn moo() int { 73 }
      """.stripMargin

    val compile =
      new Compilation(
        List("moduleA"),
        {
          case NamespaceCoordinate("moduleA", List()) => Map("test.vale" -> moduleACode)
          case NamespaceCoordinate("moduleB", List()) => Map("moo.vale" -> moduleBCode)
          case NamespaceCoordinate("", List()) => builtins
          case x => vfail("Couldn't find module: " + x)
        },
        CompilationOptions())

    vassert(!compile.getParseds().moduleToNamespacesToFilenameToContents.contains("moduleB"))

    compile.evalForReferend(Vector()) shouldEqual VonInt(42)
  }
}
