package dev.vale.templar.macros

import dev.vale.{RangeS, vassert, vassertSome}
import dev.vale.astronomer.FunctionA
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{AbstractT, ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, InterfaceFunctionCallTE, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.templar.env.FunctionEnvironment
import dev.vale.templar.types.CoordT
import dev.vale.templar.ast
import dev.vale.templar.ast._
import dev.vale.RangeS

class AbstractBodyMacro() extends IFunctionBodyMacro {
  val generatorId: String = "abstractBody"

  override def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {

    val returnReferenceType2 = vassertSome(maybeRetCoord)

    vassert(params2.exists(_.virtuality == Some(AbstractT())))
    val header =
      FunctionHeaderT(
        env.fullName,
        Vector.empty,
        params2,
        returnReferenceType2,
        originFunction)
    val function2 =
      FunctionT(
        header,
        BlockTE(
          ReturnTE(
            InterfaceFunctionCallTE(
              header,
              header.returnType,
              header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) })))))

    temputs
      .declareFunctionReturnType(header.toSignature, returnReferenceType2)
    temputs.addFunction(function2)
    vassert(temputs.getDeclaredSignatureOrigin(env.fullName).nonEmpty)
    header
  }
}
