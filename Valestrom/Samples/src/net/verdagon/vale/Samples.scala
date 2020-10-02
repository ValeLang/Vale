package net.verdagon.vale

import scala.io.Source

object Samples {
  def get(resourceFilename: String): String = {
    val stream = getClass().getClassLoader().getResourceAsStream(resourceFilename)
    vassert(stream != null)
    val source = Source.fromInputStream(stream)
    vassert(source != null)
    source.mkString("")
  }
}
