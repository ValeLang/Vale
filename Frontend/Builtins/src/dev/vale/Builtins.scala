package dev.vale

import scala.io.Source

object Builtins {
  def NAMESPACE_COORD(interner: Interner) = {
    interner.intern(PackageCoordinate("", Vector.empty))
  }

  val moduleToFilename =
    Map(
      "arith" -> "arith.vale",
      "functor1" -> "functor1.vale",
      "logic" -> "logic.vale",
      "migrate" -> "migrate.vale",
      "str" -> "str.vale",
      "drop" -> "drop.vale",
      "arrays" -> "arrays.vale",
      "mainargs" -> "mainargs.vale",
      "as" -> "as.vale",
      "print" -> "print.vale",
      "tup" -> "tup.vale",
      "panic" -> "panic.vale",
      "opt" -> "opt.vale",
      "result" -> "result.vale",
      "sameinstance" -> "sameinstance.vale",
      "weak" -> "weak.vale")

  def load(resourceFilename: String): String = {
    val stream = getClass().getClassLoader().getResourceAsStream(resourceFilename)
    vassert(stream != null)
    val source = Source.fromInputStream(stream)
    vassert(source != null)
    source.mkString("")
  }

  // Modulized is a made up word, it means we're pretending the builtins are in different modules.
  // This lets tests import only certain kinds of builtins.
  // The more basic foundational tests will choose not to import any builtins, so they can test the
  // bare minimum. For example, the most basic test is `func main() int { return 42; }`, and we don't want it
  // to fail just because the builtin-yet-unused `func as<T, X>(x X) Opt<T> { ... }` doesn't want to
  // work right now.
  def getModulizedCodeMap(interner: Interner): FileCoordinateMap[String] = {
    val result = new FileCoordinateMap[String]()
    moduleToFilename.foreach({ case (moduleName, filename) =>
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate("v", Vector("builtins", moduleName))),
          filename)),
        load(filename))
    })
    result
  }

  // Add an empty v.builtins.whatever so that the aforementioned imports still work.
  // But load the actual files all inside the root paackage.
  def getCodeMap(interner: Interner): FileCoordinateMap[String] = {
    val result = new FileCoordinateMap[String]()
    moduleToFilename.foreach({ case (moduleName, filename) =>
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate("v", Vector("builtins", moduleName))),
          filename)),
        "")
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate(NAMESPACE_COORD(interner).module, NAMESPACE_COORD(interner).packages)),
          filename)),
        load(filename))
    })
    result
  }
}
