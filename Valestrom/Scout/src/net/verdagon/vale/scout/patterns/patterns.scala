package net.verdagon.vale.scout.patterns

import net.verdagon.vale.parser.{CaptureP, VariabilityP}
import net.verdagon.vale.scout._

import scala.collection.immutable.List

case class CaptureS(
  name: IVarNameS)

case class AtomSP(
  range: RangeS,
  // This is an option because in PatternTemplar, if it's None, we'll explode the
  // expression into the destructure or throw the incoming thing away right now (see DIPRA),
  // and if it's Some, we'll make this variable an owning ref.
  name: Option[CaptureS],
  virtuality: Option[VirtualitySP],
  coordRune: IRuneS,
  destructure: Option[List[AtomSP]])

sealed trait VirtualitySP
case object AbstractSP extends VirtualitySP
case class OverrideSP(range: RangeS, kindRune: IRuneS) extends VirtualitySP

object PatternSUtils {
  def getDistinctOrderedRunesForPattern(pattern: AtomSP): List[IRuneS] = {
    val runesFromVirtuality =
      pattern.virtuality match {
        case None => List.empty
        case Some(AbstractSP) => List.empty
        case Some(OverrideSP(range, kindRune)) => List(kindRune)
      }
    val runesFromDestructures =
      pattern.destructure.toList.flatten.flatMap(getDistinctOrderedRunesForPattern)
    (runesFromVirtuality ++ runesFromDestructures :+ pattern.coordRune).distinct
  }
}