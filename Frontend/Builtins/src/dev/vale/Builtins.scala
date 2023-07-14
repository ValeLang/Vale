package dev.vale

import com.sun.org.apache.xpath.internal.compiler.Keywords

import java.nio.charset.Charset
import scala.io.Source

object Builtins {
  val moduleToFilename =
    Vector(
      "arith" -> "arith.vale",
      "functor1" -> "functor1.vale",
      "logic" -> "logic.vale",
      "migrate" -> "migrate.vale",
      "str" -> "str.vale",
      "drop" -> "drop.vale",
      "clone" -> "clone.vale",
      "arrays" -> "arrays.vale",
      "runtime_sized_array_mut_new" -> "runtime_sized_array_mut_new.vale",
      "runtime_sized_array_push" -> "runtime_sized_array_push.vale",
      "runtime_sized_array_pop" -> "runtime_sized_array_pop.vale",
      "runtime_sized_array_len" -> "runtime_sized_array_len.vale",
      "mainargs" -> "mainargs.vale",
      "as" -> "as.vale",
      "print" -> "print.vale",
      "tup0" -> "tup0.vale",
      "tup1" -> "tup1.vale",
      "tup2" -> "tup2.vale",
      "tupN" -> "tupN.vale",
      "streq" -> "streq.vale",
      "panic" -> "panic.vale",
      "panicutils" -> "panicutils.vale",
      "opt" -> "opt.vale",
      "result" -> "result.vale",
      "sameinstance" -> "sameinstance.vale",
      "weak" -> "weak.vale")

  def load(resourceFilename: String): String = {
    val stream =
      Profiler.frame(() => {
        getClass().getClassLoader().getResourceAsStream(resourceFilename)
      })
    Profiler.frame(() => {
//      // 4807 samples
//      vassert(stream != null)
//      val source = Source.fromInputStream(stream)
//      vassert(source != null)
//      source.mkString("")
//
//      // 655 samples
//      vassert(stream != null)
//      val stringBuffer = new StringBuffer()
//      val bytes = new Vector[Byte](1024)
//      while ({
//        val bytesRead = stream.read(bytes)
//        if (bytesRead > 0) {
//          stringBuffer.append(new String(bytes, 0, bytesRead, Charset.defaultCharset()))
//        }
//        bytesRead > 0
//      }) { }
//      stringBuffer.toString

      // 571 samples
      val bytes = stream.readAllBytes()
      new String(bytes, 0, bytes.length, Charset.defaultCharset())
    })
  }

  // Modulized is a made up word, it means we're pretending the builtins are in different modules.
  // This lets tests import only certain kinds of builtins.
  // The more basic foundational tests will choose not to import any builtins, so they can test the
  // bare minimum. For example, the most basic test is `func main() int { return 42; }`, and we don't want it
  // to fail just because the builtin-yet-unused `func as<T, X>(x X) Opt<T> { ... }` doesn't want to
  // work right now.
  def getModulizedCodeMap(interner: Interner, keywords: Keywords): FileCoordinateMap[String] = {
    val result = new FileCoordinateMap[String]()
    moduleToFilename.foreach({ case (moduleName, filename) =>
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate(
            keywords.v,
            Vector(keywords.builtins, interner.intern(StrI(moduleName))))),
          filename)),
        load(filename))
    })
    result
  }

  // Add an empty v.builtins.whatever so that the aforementioned imports still work.
  // But load the actual files all inside the root paackage.
  def getCodeMap(interner: Interner, keywords: Keywords): FileCoordinateMap[String] = {
    val builtinNamespaceCoord =
      interner.intern(PackageCoordinate(keywords.emptyString, Vector.empty))
    val result = new FileCoordinateMap[String]()
    moduleToFilename.foreach({ case (moduleName, filename) =>
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate(
            keywords.v,
            Vector(keywords.builtins, interner.intern(StrI(moduleName))))),
          filename)),
        "")
      result.put(
        interner.intern(FileCoordinate(builtinNamespaceCoord, filename)),
        load(filename))
    })
    result
  }
}
