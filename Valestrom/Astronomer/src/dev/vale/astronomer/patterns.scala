package dev.vale.astronomer

import dev.vale.scout.{CoordTemplataType, IRuneS, ITemplataType}
import dev.vale.scout.patterns.AtomSP
import dev.vale.scout.patterns._
import dev.vale.scout._
import dev.vale.vimpl

import scala.collection.immutable.List

object PatternSUtils {
  def getRuneTypesFromPattern(pattern: AtomSP): Iterable[(IRuneS, ITemplataType)] = {
    val runesFromDestructures =
      pattern.destructure.toVector.flatten.flatMap(getRuneTypesFromPattern)
    (runesFromDestructures ++ pattern.coordRune.map(_.rune -> CoordTemplataType)).distinct
  }

}