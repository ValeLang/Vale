package net.verdagon.vale.astronomer.builtins

import net.verdagon.vale.astronomer._
import net.verdagon.vale.astronomer.externs.Externs.makeExtern
import net.verdagon.vale.parser.{FinalP, OwnP}
import net.verdagon.vale.scout.{CodeLocationS, LocalVariable1, NotUsed, RangeS, Used}

import scala.collection.immutable.List

object Forwarders {
  val forwarders =
    List[FunctionA](
      makeForwarder("print", List(("output", "str")), "void", "__print"),
      makeForwarder("getch", List(), "int", "__getch"),
      makeForwarder("+", List(("a", "int"), ("b", "int")), "int", "__addIntInt"),
      makeForwarder("+", List(("a", "float"), ("b", "float")), "float", "__addFloatFloat"),
      makeForwarder("+", List(("a", "str"), ("b", "str")), "str", "__addStrStr"),
      makeForwarder("*", List(("left", "int"), ("right", "int")), "int", "__multiplyIntInt"),
      makeForwarder("*", List(("left", "float"), ("right", "float")), "float", "__multiplyFloatFloat"),
      makeForwarder("mod", List(("left", "int"), ("right", "int")), "int", "__mod"),
      makeForwarder("==", List(("left", "bool"), ("right", "bool")), "bool", "__eqBoolBool"),
      makeForwarder("==", List(("left", "int"), ("right", "int")), "bool", "__eqIntInt"),
      makeForwarder("==", List(("left", "str"), ("right", "str")), "bool", "__eqStrStr"),
      makeForwarder("float", List(("left", "int")), "float", "__castIntFloat"),
      makeForwarder("int", List(("left", "float")), "int", "__castFloatInt"),
      makeForwarder("str", List(("left", "int")), "str", "__castIntStr"),
      makeForwarder("str", List(("left", "bool")), "str", "__castBoolStr"),
      makeForwarder("str", List(("left", "float")), "str", "__castFloatStr"),
      makeForwarder("and", List(("left", "bool"), ("right", "bool")), "bool", "__and"),
      makeForwarder("not", List(("output", "bool")), "bool", "__not"),
      makeForwarder("-", List(("left", "int")), "int", "__negateInt"),
      makeForwarder("sqrt", List(("x", "float")), "float", "__sqrt"),
      makeForwarder("-", List(("left", "float")), "float", "__negateFloat"),
      makeForwarder("-", List(("left", "int"), ("right", "int")), "int", "__subtractIntInt"),
      makeForwarder("-", List(("left", "float"), ("right", "float")), "float", "__subtractFloatFloat"),
      makeForwarder("<", List(("left", "float"), ("right", "float")), "bool", "__lessThanFloat"),
      makeForwarder(">", List(("left", "float"), ("right", "float")), "bool", "__greaterThanFloat"),
      makeForwarder("<", List(("left", "int"), ("right", "int")), "bool", "__lessThanInt"),
      makeForwarder(">", List(("left", "int"), ("right", "int")), "bool", "__greaterThanInt"),
      makeForwarder("<=", List(("left", "int"), ("right", "int")), "bool", "__lessThanOrEqInt"),
      makeForwarder(">=", List(("left", "int"), ("right", "int")), "bool", "__greaterThanOrEqInt"))

  def makeForwarder(functionName: String, params: List[(String, String)], ret: String, callee: String): FunctionA = {
    val name = FunctionNameA(functionName, CodeLocationS.internal(-33))
    makeSimpleFunction(
      name,
      params,
      ret,
      CodeBodyA(
        BodyAE(
          List(),
          BlockAE(
            params.map(_._1).map(param => {
              LocalVariableA(CodeVarNameA(param), FinalP, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)
            }),
            List(
              FunctionCallAE(
                RangeS.internal(-35),
                FunctionLoadAE(GlobalFunctionFamilyNameA(callee)),
                params.map(param => LocalLoadAE(CodeVarNameA(param._1), OwnP))))))))
  }
}
