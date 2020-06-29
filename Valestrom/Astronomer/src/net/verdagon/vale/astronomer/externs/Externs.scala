package net.verdagon.vale.astronomer.externs

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.{CodeLocationS, CodeRuneS, ParameterS}

import scala.collection.immutable.List

object Externs {
  val externs =
    List[FunctionA](
        makeExtern("__print", List(("output", "Str")), "Void"),
        makeExtern("__getch", List(), "Int"),
        makeExtern("__addIntInt", List(("a", "Int"), ("b", "Int")), "Int"),
        makeExtern("__sqrt", List(("x", "Float")), "Float"),
        makeExtern("__addFloatFloat", List(("a", "Float"), ("b", "Float")), "Float"),
        makeExtern("__addStrStr", List(("a", "Str"), ("b", "Str")), "Str"),
        makeExtern("__multiplyIntInt", List(("left", "Int"), ("right", "Int")), "Int"),
        makeExtern("__multiplyFloatFloat", List(("left", "Float"), ("right", "Float")), "Float"),
        makeExtern("__mod", List(("left", "Int"), ("right", "Int")), "Int"),
        makeExtern("__eqBoolBool", List(("left", "Bool"), ("right", "Bool")), "Bool"),
        makeExtern("__eqIntInt", List(("left", "Int"), ("right", "Int")), "Bool"),
        makeExtern("__eqStrStr", List(("left", "Str"), ("right", "Str")), "Bool"),
        makeExtern("__castIntFloat", List(("left", "Int")), "Float"),
        makeExtern("__castFloatFloat", List(("left", "Float")), "Float"),
        makeExtern("__castIntInt", List(("left", "Int")), "Int"),
        makeExtern("__castFloatInt", List(("left", "Float")), "Int"),
        makeExtern("__castIntStr", List(("left", "Int")), "Str"),
        makeExtern("__castFloatStr", List(("left", "Float")), "Str"),
        makeExtern("__and", List(("left", "Bool"), ("right", "Bool")), "Bool"),
        makeExtern("__not", List(("output", "Bool")), "Bool"),
        makeExtern("__negateInt", List(("left", "Int")), "Int"),
        makeExtern("__negateFloat", List(("left", "Float")), "Float"),
        makeExtern("__subtractIntInt", List(("left", "Int"), ("right", "Int")), "Int"),
        makeExtern("__subtractFloatInt", List(("left", "Float"), ("right", "Int")), "Float"),
        makeExtern("__subtractIntFloat", List(("left", "Int"), ("right", "Float")), "Float"),
        makeExtern("__subtractFloatFloat", List(("left", "Float"), ("right", "Float")), "Float"),
        makeExtern("__lessThanFloat", List(("left", "Float"), ("right", "Float")), "Bool"),
        makeExtern("__lessThanInt", List(("left", "Int"), ("right", "Int")), "Bool"),
        makeExtern("__lessThanOrEqInt", List(("left", "Int"), ("right", "Int")), "Bool"),
        makeExtern("__greaterThanInt", List(("left", "Int"), ("right", "Int")), "Bool"),
        makeExtern("__greaterThanFloat", List(("left", "Float"), ("right", "Float")), "Bool"),
        makeExtern("__greaterThanOrEqInt", List(("left", "Int"), ("right", "Int")), "Bool"))

  def makeExtern(functionName: String, params: List[(String, String)], retType: String): FunctionA = {
    makeSimpleFunction(
      FunctionNameA(functionName, CodeLocationS(1, 1)),
      params,
      retType,
      ExternBodyA)
  }
}
