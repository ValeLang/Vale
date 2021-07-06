package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{CaptureP, VariabilityP}
import net.verdagon.vale.scout.RangeS

import scala.collection.immutable.List

case class AtomAP(
  range: RangeS,
  capture: LocalA,
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
        case None => List.empty
        case Some(AbstractAP) => List.empty
        case Some(OverrideAP(range, kindRune)) => List(kindRune)
      }
    val runesFromDestructures =
      pattern.destructure.toList.flatten.flatMap(getDistinctOrderedRunesForPattern)
    (runesFromVirtuality ++ runesFromDestructures :+ pattern.coordRune).distinct
  }

}