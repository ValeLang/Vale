package net.verdagon.vale.parser

import net.verdagon.vale.{vcurious, vimpl}
import net.verdagon.von._

object Formatter {
  sealed trait IClass
  case object W extends IClass
  case object Ab extends IClass
  case object Ext extends IClass
  case object Fn extends IClass
  case object FnName extends IClass
  case object FnTplSep extends IClass
  case object Rune extends IClass

  sealed trait IElement
  object Span {
    def apply(classs: IClass, elements: IElement*): Span = {
      Span(classs, elements.toVector)
    }
  }
  case class Span(classs: IClass, elements: Vector[IElement]) extends IElement { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious(); }
  case class Text(string: String) extends IElement { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious(); }
}
