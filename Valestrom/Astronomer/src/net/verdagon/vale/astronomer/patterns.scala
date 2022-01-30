package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.INameDeclarationP
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, OverrideSP}
import net.verdagon.vale.scout._
import net.verdagon.vale.{vcurious, vimpl}

import scala.collection.immutable.List

object PatternSUtils {
  def getRuneTypesFromPattern(pattern: AtomSP): Iterable[(IRuneS, ITemplataType)] = {
    val runesFromVirtuality =
      pattern.virtuality match {
        case None => Vector.empty
        case Some(AbstractSP(_, _)) => Vector.empty
        case Some(OverrideSP(range, kindRune)) => Vector((kindRune.rune -> KindTemplataType))
      }
    val runesFromDestructures =
      pattern.destructure.toVector.flatten.flatMap(getRuneTypesFromPattern)
    (runesFromVirtuality ++ runesFromDestructures ++ pattern.coordRune.map(_.rune -> CoordTemplataType)).distinct
  }

}