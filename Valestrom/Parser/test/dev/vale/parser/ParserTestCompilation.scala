package dev.vale.parser

import dev.vale.{FileCoordinateMap, IPackageResolver, PackageCoordinate}
import dev.vale.options.GlobalOptions
import dev.vale.{FileCoordinateMap, IPackageResolver, PackageCoordinate}

import scala.collection.immutable.Map

object ParserTestCompilation {
  def test(code: String*): ParserCompilation = {
    val codeMap = FileCoordinateMap.test(code.toVector)
    new ParserCompilation(
      GlobalOptions(true, true, true, true),
      Vector(PackageCoordinate.TEST_TLD),
      new IPackageResolver[Map[String, String]]() {
        override def resolve(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
          // For testing the parser, we dont want it to fetch things with import statements
          Some(codeMap.resolve(packageCoord).getOrElse(Map("" -> "")))
        }
      })

  }
}
