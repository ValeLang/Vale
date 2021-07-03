package net.verdagon.vale

import net.verdagon.vale.driver.FullCompilationOptions
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
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "moduleA.vale", moduleACode)
              .add("moduleB", List(), "moduleB.vale", moduleBCode))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
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
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "moduleA.vale", moduleACode)
              .add("moduleB", List(), "moduleB.vale", moduleBCode))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    vassert(!compile.getParseds().getOrDie().moduleToPackagesToFilenameToContents.contains("moduleB"))

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Tests import with paackage") {
    val moduleACode =
      """
        |import moduleB.bork.*;
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
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List())),
        Builtins.getCodeMap()
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "moduleA.vale", moduleACode)
              .add("moduleB", List("bork"), "moduleB.vale", moduleBCode))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

  test("Tests import of directory with no vale files") {
    val moduleACode =
      """
        |import moduleB.bork.*;
        |
        |fn main() int export {
        |  a = 42;
        |  = a;
        |}
      """.stripMargin

    val compile =
      new RunCompilation(
        List(PackageCoordinate.BUILTIN, PackageCoordinate("moduleA", List())),
        Builtins.getCodeMap()
          .or(Tests.getPackageToResourceResolver)
          .or(
            FileCoordinateMap(Map())
              .add("moduleA", List(), "moduleA.vale", moduleACode))
          .or({ case PackageCoordinate("moduleB", List("bork")) => Some(Map()) }),
    FullCompilationOptions())

    compile.evalForKind(Vector()) shouldEqual VonInt(42)
  }

}
