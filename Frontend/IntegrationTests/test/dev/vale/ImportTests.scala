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
    val keywords = new Keywords(interner)
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(
          interner.intern(StrI("moduleA")),
          Vector.empty)),
        "moduleA.vale")),
      moduleACode)
    map.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(interner.intern(StrI("moduleB")), Vector.empty)),
        "moduleB.vale")),
      moduleBCode)

    val compile =
      new RunCompilation(
        interner,
        keywords,
        Vector(PackageCoordinate.BUILTIN(interner, keywords), interner.intern(PackageCoordinate(interner.intern(StrI("moduleA")), Vector.empty))),
        Builtins.getCodeMap(interner, keywords)
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
    val keywords = new Keywords(interner)
    val moduleACoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleA")), Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleB")), Vector.empty))
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
        keywords,
        Vector(PackageCoordinate.BUILTIN(interner, keywords), moduleACoord),
        Builtins.getCodeMap(interner, keywords)
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
    val keywords = new Keywords(interner)
    val moduleACoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleA")), Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleB")), Vector(interner.intern(StrI("bork")))))
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
        keywords,
        Vector(PackageCoordinate.BUILTIN(interner, keywords), moduleACoord),
        Builtins.getCodeMap(interner, keywords)
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
    val keywords = new Keywords(interner)
    val moduleACoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleA")), Vector.empty))
    val moduleBCoord = interner.intern(PackageCoordinate(interner.intern(StrI("moduleB")), Vector(interner.intern(StrI("bork")))))
    val map = new FileCoordinateMap[String]()
    map.put(
      interner.intern(FileCoordinate(moduleACoord, "moduleA.vale")),
      moduleACode)

    val compile =
      new RunCompilation(
        interner,
        keywords,
        Vector(PackageCoordinate.BUILTIN(interner, keywords), interner.intern(PackageCoordinate(interner.intern(StrI("moduleA")), Vector.empty))),
        Builtins.getCodeMap(interner, keywords)
          .or(Tests.getPackageToResourceResolver)
          .or(map)
          .or({ case PackageCoordinate(StrI("moduleB"), Vector(StrI("bork"))) => Some(Map()) }),
    FullCompilationOptions())

    compile.evalForKind(Vector()) match { case VonInt(42) => }
  }

}
