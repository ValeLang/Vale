package dev.vale.highertyping

import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.postparsing.patterns._
import dev.vale.postparsing._
import dev.vale.vimpl

import scala.collection.immutable.List

object PatternSUtils {
  def getRuneTypesFromPattern(pattern: AtomSP): Iterable[(IRuneS, ITemplataType)] = {
    val runesFromDestructures =
      pattern.destructure.toVector.flatten.flatMap(getRuneTypesFromPattern)
    (runesFromDestructures ++ pattern.coordRune.map(_.rune -> CoordTemplataType())).distinct
  }

}