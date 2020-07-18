package net.verdagon.vale

import scala.io.Source

object Samples {
  def get(resourceFilename: String): String = {
    val is = Source.fromInputStream(getClass().getClassLoader().getResourceAsStream(resourceFilename))
    vassert(is != null)
    is.mkString("")
  }
}
