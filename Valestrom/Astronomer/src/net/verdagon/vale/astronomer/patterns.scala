package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{CaptureP, VariabilityP}
import net.verdagon.vale.scout.RangeS

import scala.collection.immutable.List

case class AtomAP(
  range: RangeS,
  // This is an Option so Templar can destroy incoming ignored vars immediately, see DIPRA.
  capture: Option[LocalA],
  virtuality: Option[VirtualityAP],
  coordRune: IRuneA,
  destructure: Option[List[AtomAP]])

sealed trait VirtualityAP
case object AbstractAP extends VirtualityAP
case class OverrideAP(range: RangeS, kindRune: IRuneA) extends VirtualityAP

object PatternSUtils {
  def getDistinctOrderedRunesForPattern(pattern: AtomAP): List[IRuneA] = {
    val runesFromVirtuality =
      pattern.virtuality match {
        case None => List()
        case Some(AbstractAP) => List()
        case Some(OverrideAP(range, kindRune)) => List(kindRune)
      }
    val runesFromDestructures =
      pattern.destructure.toList.flatten.flatMap(getDistinctOrderedRunesForPattern)
    (runesFromVirtuality ++ runesFromDestructures :+ pattern.coordRune).distinct
  }

}