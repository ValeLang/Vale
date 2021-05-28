package net.verdagon.vale

import scala.io.Source

object Tests {
  def load(resourceFilename: String): Option[String] = {
    val stream = getClass().getClassLoader().getResourceAsStream(resourceFilename)
    if (stream == null)
      return None
    val source = Source.fromInputStream(stream)
    vassert(source != null)
    Some(source.mkString(""))
  }
  def loadExpected(resourceFilename: String): String = {
    load(resourceFilename).get
  }

  def resolvePackageToResource(packageCoord: PackageCoordinate): Option[Map[String, String]] = {
    val directory = (packageCoord.module :: packageCoord.packages)
    val filename = directory.last + ".vale"
    val filepath = (directory :+ filename).mkString("/")
    load(filepath) match {
      case None => None
      case Some(source) => Some(Map(filename -> source))
    }
  }

  def getPackageToResourceResolver: IPackageResolver[Map[String, String]]
  = resolvePackageToResource
}
