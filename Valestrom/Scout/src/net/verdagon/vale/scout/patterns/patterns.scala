package net.verdagon.vale.scout.patterns

import net.verdagon.vale.parser.INameDeclarationP
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.rules.RuneUsage
import net.verdagon.vale.{RangeS, vcurious, vimpl, vpass}

import scala.collection.immutable.List

case class CaptureS(
  name: IVarNameS) {
  override def hashCode(): Int = vcurious()
}

case class AtomSP(
  range: RangeS,
  // This is an option because in PatternTemplar, if it's None, we'll explode the
  // expression into the destructure or throw the incoming thing away right now (see DIPRA),
  // and if it's Some, we'll make this variable an owning ref.
  // This is a CaptureS instead of a LocalS, which is slightly annoying for Templar, since it has to
  // remember the LocalSs in scope. But it'd be even more difficult for Scout to know the Used/NotUsed
  // etc up-front to include in the pattern.
  name: Option[CaptureS],
  virtuality: Option[VirtualitySP],
  coordRune: Option[RuneUsage],
  destructure: Option[Vector[AtomSP]]) {
  override def hashCode(): Int = vcurious()
  vpass()
}

sealed trait VirtualitySP

case class AbstractSP(
  range: RangeS,
  // True if this is defined inside an interface
  // False if this is a free function somewhere else
  isInternalMethod: Boolean
) extends VirtualitySP

case class OverrideSP(range: RangeS, kindRune: RuneUsage) extends VirtualitySP {
  override def hashCode(): Int = vcurious()
}
