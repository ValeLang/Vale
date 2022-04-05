package dev.vale.parsing

import dev.vale.options.GlobalOptions
import dev.vale.{FileCoordinateMap, IPackageResolver, Interner, PackageCoordinate}
import dev.vale.{FileCoordinateMap, IPackageResolver, PackageCoordinate}

import scala.collection.immutable.Map

object ParserTestCompilation {
  def test(interner: Interner, code: String*): ParserCompilation = {
    val codeMap = FileCoordinateMap.test(interner, code.toVector)
    new ParserCompilation(
      GlobalOptions(true, true, true, true),
      interner,
      Vector(PackageCoordinate.TEST_TLD(interner)),
      new IPackageResolver[Map[String, String]]() {
        override def resolve(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
          // For testing the parser, we dont want it to fetch things with import statements
          Some(codeMap.resolve(packageCoord).getOrElse(Map("" -> "")))
        }
      })

  }
}
