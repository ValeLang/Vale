package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.patterns._
import net.verdagon.vale.scout._
import net.verdagon.vale.{vcurious, vimpl}

import scala.collection.immutable.List

object PatternSUtils {
  def getRuneTypesFromPattern(pattern: AtomSP): Iterable[(IRuneS, ITemplataType)] = {
    val runesFromDestructures =
      pattern.destructure.toVector.flatten.flatMap(getRuneTypesFromPattern)
    (runesFromDestructures ++ pattern.coordRune.map(_.rune -> CoordTemplataType)).distinct
  }

}