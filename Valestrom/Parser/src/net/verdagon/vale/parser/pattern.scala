package net.verdagon.vale.parser

import net.verdagon.vale.{vassert, vcheck, vcurious, vimpl}

import scala.collection.immutable.List
import scala.util.parsing.input.Positional

sealed trait IVirtualityP
case class AbstractP(range: Range) extends IVirtualityP
case class OverrideP(range: Range, tyype: ITemplexPT) extends IVirtualityP { override def hashCode(): Int = vcurious() }

case class PatternPP(
    range: Range,
    preBorrow: Option[UnitP],
    capture: Option[CaptureP],

    // If they just have a destructure, this will probably be a ManualSequence(None).
    // If they have just parens, this will probably be a Pack(None).
    // Let's be careful to not allow destructuring packs without Pack here, see MEDP.
    templex: Option[ITemplexPT],

    // Eventually, add an ellipsis: Boolean field here... except we also have
    // to account for the difference between a: T... and a...: T (in one, T is a
    // single type and in the other, T is a pack of types). And we might also want
    // to account for nested parens, like struct Fn:((#Params...), (#Rets...))

    destructure: Option[DestructureP],
    virtuality: Option[IVirtualityP]) extends Positional

case class DestructureP(
  range: Range,
  patterns: Vector[PatternPP]) { override def hashCode(): Int = vcurious() }

sealed trait ICaptureNameP
case class LocalNameP(name: NameP) extends ICaptureNameP { override def hashCode(): Int = vcurious() }
case class ConstructingMemberNameP(name: NameP) extends ICaptureNameP { override def hashCode(): Int = vcurious() }

case class CaptureP(
    range: Range,
    name: ICaptureNameP) { override def hashCode(): Int = vcurious() }

object Patterns {
  object capturedWithTypeRune {
    def unapply(arg: PatternPP): Option[(String, String)] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)))), Some(NameOrRunePT(NameP(_, kindRune))), None, None) => Some((name, kindRune))
        case _ => None
      }
    }
  }
  object withType {
    def unapply(arg: PatternPP): Option[ITemplexPT] = {
      arg.templex
    }
  }
  object capture {
    def unapply(arg: PatternPP): Option[String] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)))), None, None, None) => Some(name)
        case _ => None
      }
    }
  }
  object fromEnv {
    def unapply(arg: PatternPP): Option[String] = {
      arg match {
        case PatternPP(_, _, None, Some(NameOrRunePT(NameP(_, kindName))), None, None) => Some(kindName)
        case _ => None
      }
    }
  }
  object withDestructure {
    def unapply(arg: PatternPP): Option[Vector[PatternPP]] = {
      arg.destructure match {
        case None => None
        case Some(DestructureP(_, patterns)) => Some(patterns)
      }
    }
  }
  object capturedWithType {
    def unapply(arg: PatternPP): Option[(String, ITemplexPT)] = {
      arg match {
        case PatternPP(_, _, Some(CaptureP(_, LocalNameP(NameP(_, name)))), Some(templex), None, None) => Some((name, templex))
        case _ => None
      }
    }
  }
}
