package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.{CodeLocationS, CodeRuneS, ParameterS, RangeS}
import net.verdagon.vale.astronomer.externs.Externs._
import net.verdagon.vale.parser.{CaptureP, FinalP}
import net.verdagon.vale.scout.patterns.AtomSP

object makeSimpleFunction {
  def apply(
    name: FunctionNameA,
    params: List[(String, String)],
    retType: String,
    body: IBodyA):
  FunctionA = {
    val runeByType =
      params
        .map(_._2)
        .distinct
        .zipWithIndex
        .map({ case (tyype, index) => tyype -> ImplicitRuneA(name, index) })
        .toMap

    val paramsA =
      params.map({ case (paramName, tyype) => simpleParam(CodeVarNameA(paramName), runeByType(tyype)) })

    val returnRune = ReturnRuneA()
    val paramRules = runeByType.map({ case (tyype, rune) => simpleCoordRuneAR(rune, tyype) }).toList
    val allRules = simpleCoordRuneAR(returnRune, retType) :: paramRules

    val knowableRunes = runeByType.values.toSet[IRuneA] + returnRune
    val localRunes = runeByType.values.toSet[IRuneA] + returnRune

    FunctionA(
      RangeS.internal(-53),
      name,
      List(),
      FunctionTemplataType,
      knowableRunes,
      List(),
      localRunes,
      runeByType
        .map({ case (_, rune) => (rune, CoordTemplataType) })
        .toMap[IRuneA, ITemplataType] +
        (returnRune -> CoordTemplataType),
      paramsA,
      Some(returnRune),
      allRules,
      body)
  }


  def simpleCoordRuneAR(rune: IRuneA, name: String): EqualsAR = {
    EqualsAR(
      TemplexAR(RuneAT(rune, CoordTemplataType)),
      TemplexAR(NameAT(CodeTypeNameA(name), CoordTemplataType)))
  }
  def simpleParam(name: IVarNameA, rune: IRuneA): ParameterA = {
    ParameterA(AtomAP(CaptureA(name, FinalP), None, rune, None))
  }
}
