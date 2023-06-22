package dev.vale.highertyping

import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.postparsing.{CoordTemplataType, GenericParameterS, ParameterS, PostParser, PostParserErrorHumanizer}
import dev.vale.{CodeLocationS, FileCoordinate, RangeS, vassert, vimpl}

class Printer(file: FileCoordinate) {
  val buffer = new StringBuilder()

  def pos: CodeLocationS = CodeLocationS(file, buffer.length)
  def +=(s: String): Unit = {
    buffer.append(s)
  }
}

object HigherTypedPrinter {
  def textify(printer: Printer, original: FunctionA): FunctionA = {
//    val FunctionA(_, funcName, attributes, funcType, genericParams, runeToType, params, maybeRetCoordRune, defaultRegionRune, rules, body) = original
//
//    vassert(attributes.isEmpty)
//
//    val funcBegin = printer.pos
//    printer += "\n\nfunc "
//    val nameBegin = printer.pos
//    printer += PostParserErrorHumanizer.humanizeName(funcName)
//    val nameEnd = printer.pos
//
//    val newGenericParams =
//      if (genericParams.isEmpty) {
//        Vector()
//      } else {
//        printer += "<"
//
//        val newGenericParams =
//          genericParams.map(genericParam => {
//            val GenericParameterS(_, runeUsage, genericType, default) = genericParam
//            val RuneUsage(_, rune) = runeUsage
//            vassert(attributes.isEmpty)
//
//            val genParamBegin = printer.pos
//            val genParamNameBegin = printer.pos
//            printer += PostParserErrorHumanizer.humanizeRune(rune)
//            val genParamNameEnd = printer.pos
//
//            vimpl() // rest of the stuff in generic type
//            genericType.tyype match {
//              case CoordTemplataType() => // Nothing, that's the default
//              case _ => printer += " " + PostParserErrorHumanizer.humanizeTemplataType(genericType.tyype)
//            }
//
//            val genParamEnd = printer.pos
//
//            vassert(default.isEmpty) // impl
//
//            GenericParameterS(
//              RangeS(genParamBegin, genParamEnd),
//              RuneUsage(RangeS(genParamNameBegin, genParamNameEnd), rune),
//              genericType,
//              default)
//          })
//
//        printer += ">"
//
//        newGenericParams
//      }
//
//    val newParams =
//      params.map(param => {
//        val ParameterS(maybePreChecked, AtomSP(_, maybeName, maybeVirtuality, maybeCoordRune, maybeDestructure)) = param
//
//        val paramBegin = printer.pos
//
//        val newMaybeVirtuality =
//          maybeVirtuality match {
//            case None => None
//            case Some(AbstractSP(_, isInternalMethod)) => {
//              val abstractBegin = printer.pos
//              printer += "virtual"
//              val abstractEnd = printer.pos
//              printer += " "
//              Some(AbstractSP(RangeS(abstractBegin, abstractEnd), isInternalMethod))
//            }
//          }
//
//        maybeName match {
//          case None => "_"
//          case Some(CaptureS(name)) => PostParserErrorHumanizer.humanizeName(name)
//        }
//
//        val newMaybeCoordRune = vimpl()
//        val newDestructure = vimpl()
//
//        val paramEnd = printer.pos
//
//        ParameterS(maybePreChecked, AtomSP(RangeS(paramBegin, paramEnd), maybeName, newMaybeVirtuality, newMaybeCoordRune, newDestructure))
//      })
//
    vimpl()
  }
}
