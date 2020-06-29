package net.verdagon.vale.parser

import net.verdagon.vale.vimpl
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
      Span(classs, elements.toList)
    }
  }
  case class Span(classs: IClass, elements: List[IElement]) extends IElement
  case class Text(string: String) extends IElement
//
//  def w(str: String) = Span(W, str)
//  val s = w(" ")
//  val ls = List(s)
//  def may(b: Boolean, span: Span*): List[Span] = {
//    if (b) span.toList else List()
//  }
//
//  def toHTML(element: IElement): String = {
//    element match {
//      case Text(str) => str
//      case Span(classs, elements) => {
//        s"""<span class="${classs.toString}">""" + elements.map(toHTML).mkString("") + "</span>"
//      }
//    }
//  }
//
//  def repsep(begin: List[Span], end: List[Span], sep: List[Span], items: List[List[Span]]): List[Span] = {
//    if (items.isEmpty) {
//      begin ++ end
//    } else {
//      begin ++ items.init.flatMap(x => x ++ sep) ++ items.last ++ end
//    }
//  }
//
//  def printFunctionSingleLine(function: FunctionP): List[Span] = {
//    val FunctionP(range, Some(name), isExtern, isAbstract, maybeUserSpecifiedIdentifyingRunes, templateRules, params, ret, body) = function
//
//    Span(
//      Fn,
//      may(isExtern, Span(Ext, "extern"), s) ++
//      may(isAbstract, Span(Ab, "abstract"), s) ++
//      List(Span(Fn, "fn"), s, Span(FnName, name)) ++
//      maybeUserSpecifiedIdentifyingRunes.toList.flatMap(items => {
//        repsep(
//          List(Span(FnTplSep, "<")),
//          List(Span(FnTplSep, ">")),
//          List(Span(FnTplSep, ","), s),
//          items.map(x => List(Span(Rune, x))))
//      }))
//  }
//
//  def formatBlock(block: BlockPE): IVonData = {
//    val BlockPE(elements) = block
//    VonObject("Block", None, Vector(
//      VonMember("elements", VonArray(None, elements.map(formatExpression).toVector))))
//  }
//
//  def formatExpression(expr: IExpressionPE): IVonData = {
//    expr match {
//      case LookupPE(name, templateArgs) => {
//        VonObject("Block", None, Vector(
//          VonMember("name", VonStr(name)),
//          VonMember("templateArgs", VonArray(None, templateArgs.map(formatTemplexPT).toVector))))
//      }
//    }
//  }
//
//  def formatTemplexPT(templex: ITemplexPT): IVonData = {
//    vimpl()
//  }
//
//  def formatTemplexPT(templex: ITemplexPT): IVonData = {
//    vimpl()
//  }
//
//  def formatPattern(param: PatternPP): IVonData = {
//    vimpl()
//  }
//
//  def formatRulexPR(rulex: IRulexPR): IVonData = {
//    vimpl()
//  }
//
//  def formatStruct(struct: StructP): IVonData = {
//    vimpl()
//  }
//
//  def formatInterface(interface: InterfaceP): IVonData = {
//    vimpl()
//  }
//
//  def formatImpl(impl: ImplP): IVonData = {
//    vimpl()
//  }
//
//  def formatOptional[T](opt: Option[T], func: (T) => IVonData): IVonData = {
//    opt match {
//      case None => VonObject("None", None, Vector())
//      case Some(value) => VonObject("Some", None, Vector(VonMember("value", func(value))))
//    }
//  }
}
