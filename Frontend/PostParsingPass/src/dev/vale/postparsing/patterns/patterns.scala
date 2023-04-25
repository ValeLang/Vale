package dev.vale.postparsing.patterns

import dev.vale.postparsing.IVarNameS
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.{RangeS, vcurious, vpass}
import dev.vale.postparsing._
import dev.vale.RangeS

import scala.collection.immutable.List

case class CaptureS(
    name: IVarNameS,
    mutate: Boolean) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}

case class AtomSP(
  range: RangeS,
  // This is an option because in PatternCompiler, if it's None, we'll explode the
  // expression into the destructure or throw the incoming thing away right now (see DIPRA),
  // and if it's Some, we'll make this variable an owning ref.
  // This is a CaptureS instead of a LocalS, which is slightly annoying for Compiler, since it has to
  // remember the LocalSs in scope. But it'd be even more difficult for Scout to know the Used/NotUsed
  // etc up-front to include in the pattern.
  name: Option[CaptureS],
  virtuality: Option[AbstractSP],
  coordRune: Option[RuneUsage],
  destructure: Option[Vector[AtomSP]]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  vpass()
}

case class AbstractSP(
  range: RangeS,
  // True if this is defined inside an interface
  // False if this is a free function somewhere else
  isInternalMethod: Boolean
)
