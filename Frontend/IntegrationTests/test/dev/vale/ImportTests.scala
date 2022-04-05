package dev.vale

import dev.vale.passmanager.FullCompilationOptions
import dev.vale.finalast._
import dev.vale.von.VonInt
import dev.vale.{finalast => m}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.List

class ImportTests extends FunSuite with Matchers {
  test("Tests import") {
    val moduleACode =
      """
        |import moduleB.moo;
        |
        |exported func main() int {
        |  a = moo();
        |  return a;
        |}
      """.stripMargin

    val moduleBCode =
      """
        |func moo() int { return 42; }
      """.stripMargin

    val interner = new Interner()
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate("moduleA", Vector.empty)),
        "moduleA.vale")),
      moduleACode)
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate("moduleB", Vector.empty)),
        "moduleB.vale")),
      moduleBCode)

    val compile =
      new RunCompilation(
        interner,
        Vector(PackageCoordinate.BUILTIN(interner), interner.intern(PackageCoordinate("moduleA", Vector.empty))),
        Builtins.getCodeMap(interner)
          .or(map)
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Tests non-imported module isn't brought in") {
    val moduleACode =
      """
        |exported func main() int {
        |  a = 42;
        |  return a;
        |}
      """.stripMargin

    val moduleBCode =
      """
        |func moo() int { return 73; }
      """.stripMargin


    val interner = new Interner()
    val moduleACoord = interner.intern(PackageCoordinate("moduleA", Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate("moduleB", Vector.empty))
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(moduleACoord, "moduleA.vale")),
      moduleACode)
    map.put(
      interner.intern(FileCoordinate(moduleBCoord, "moduleB.vale")),
      moduleBCode)

    val compile =
      new RunCompilation(
        interner,
        Vector(PackageCoordinate.BUILTIN(interner), moduleACoord),
        Builtins.getCodeMap(interner)
          .or(map)
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    vassert(!compile.getParseds().getOrDie().packageCoordToFileCoords.contains(moduleBCoord))

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Tests import with paackage") {
    val moduleACode =
      """
        |import moduleB.bork.*;
        |
        |exported func main() int {
        |  a = moo();
        |  return a;
        |}
      """.stripMargin

    val moduleBCode =
      """
        |func moo() int { return 42; }
      """.stripMargin

    val interner = new Interner()
    val moduleACoord = interner.intern(PackageCoordinate("moduleA", Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate("moduleB", Vector("bork")))
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(moduleACoord, "moduleA.vale")),
      moduleACode)
    map.put(
      interner.intern(FileCoordinate(moduleBCoord, "moduleB.vale")),
      moduleBCode)

    val compile =
      new RunCompilation(
        interner,
        Vector(PackageCoordinate.BUILTIN(interner), moduleACoord),
        Builtins.getCodeMap(interner)
          .or(map)
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions())

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

  test("Tests import of directory with no vale files") {
    val moduleACode =
      """
        |import moduleB.bork.*;
        |
        |exported func main() int {
        |  a = 42;
        |  return a;
        |}
      """.stripMargin

    val interner = new Interner()
    val moduleACoord = interner.intern(PackageCoordinate("moduleA", Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate("moduleB", Vector("bork")))
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(moduleACoord, "moduleA.vale")),
      moduleACode)

    val compile =
      new RunCompilation(
        interner,
        Vector(PackageCoordinate.BUILTIN(interner), interner.intern(PackageCoordinate("moduleA", Vector.empty))),
        Builtins.getCodeMap(interner)
          .or(Tests.getPackageToResourceResolver)
          .or(map)
          .or({ case PackageCoordinate("moduleB", Vector("bork")) => Some(Map()) }),
    FullCompilationOptions())

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

}
