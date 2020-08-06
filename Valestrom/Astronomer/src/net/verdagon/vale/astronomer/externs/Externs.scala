package net.verdagon.vale.astronomer.externs

import net.verdagon.vale.astronomer._
import net.verdagon.vale.parser.{CaptureP, FinalP}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.{CodeLocationS, CodeRuneS, ParameterS}

import scala.collection.immutable.List

object Externs {
  val externs =
    List[FunctionA](
        makeExtern("__print", List(("output", "str")), "void"),
        makeExtern("__getch", List(), "int"),
        makeExtern("__addIntInt", List(("a", "int"), ("b", "int")), "int"),
        makeExtern("__subFloatFloat", List(("a", "float"), ("b", "float")), "float"),
        makeExtern("__sqrt", List(("x", "float")), "float"),
        makeExtern("__addFloatFloat", List(("a", "float"), ("b", "float")), "float"),
        makeExtern("__addStrStr", List(("a", "str"), ("b", "str")), "str"),
        makeExtern("__multiplyIntInt", List(("lefft", "int"), ("right", "int")), "int"),
        makeExtern("__multiplyFloatFloat", List(("left", "float"), ("right", "float")), "float"),
        makeExtern("__mod", List(("left", "int"), ("right", "int")), "int"),
        makeExtern("__eqBoolBool", List(("left", "bool"), ("right", "bool")), "bool"),
        makeExtern("__eqIntInt", List(("left", "int"), ("right", "int")), "bool"),
        makeExtern("__eqStrStr", List(("left", "str"), ("right", "str")), "bool"),
        makeExtern("__castIntFloat", List(("left", "int")), "float"),
        makeExtern("__castFloatFloat", List(("left", "float")), "float"),
        makeExtern("__castIntInt", List(("left", "int")), "int"),
        makeExtern("__castFloatInt", List(("left", "float")), "int"),
        makeExtern("__castIntStr", List(("left", "int")), "str"),
        makeExtern("__castFloatStr", List(("left", "float")), "str"),
        makeExtern("__and", List(("left", "bool"), ("right", "bool")), "bool"),
        makeExtern("__not", List(("output", "bool")), "bool"),
        makeExtern("__negateInt", List(("left", "int")), "int"),
        makeExtern("__negateFloat", List(("left", "float")), "float"),
        makeExtern("__subtractIntInt", List(("left", "int"), ("right", "int")), "int"),
        makeExtern("__subtractFloatInt", List(("left", "float"), ("right", "int")), "float"),
        makeExtern("__subtractIntFloat", List(("left", "int"), ("right", "float")), "float"),
        makeExtern("__subtractFloatFloat", List(("left", "float"), ("right", "float")), "float"),
        makeExtern("__lessThanFloat", List(("left", "float"), ("right", "float")), "bool"),
        makeExtern("__lessThanInt", List(("left", "int"), ("right", "int")), "bool"),
        makeExtern("__lessThanOrEqInt", List(("left", "int"), ("right", "int")), "bool"),
        makeExtern("__greaterThanInt", List(("left", "int"), ("right", "int")), "bool"),
        makeExtern("__greaterThanFloat", List(("left", "float"), ("right", "float")), "bool"),
        makeExtern("__greaterThanOrEqInt", List(("left", "int"), ("right", "int")), "bool"))

  def makeExtern(functionName: String, params: List[(String, String)], retType: String): FunctionA = {
    makeSimpleFunction(
      FunctionNameA(functionName, CodeLocationS(-6, 0)),
      params,
      retType,
      ExternBodyA)
  }
}
