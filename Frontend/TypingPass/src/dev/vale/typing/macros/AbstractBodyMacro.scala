package dev.vale.typing.macros

import dev.vale.{Keywords, RangeS, StrI, vassert, vassertSome}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{AbstractT, ArgLookupTE, BlockTE, FunctionHeaderT, FunctionT, InterfaceFunctionCallTE, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.FunctionEnvironment
import dev.vale.typing.types.CoordT
import dev.vale.typing.ast
import dev.vale.typing.ast._

class AbstractBodyMacro(keywords: Keywords) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.abstractBody

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
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

    coutputs
      .declareFunctionReturnType(header.toSignature, returnReferenceType2)
    coutputs.addFunction(function2)
    vassert(coutputs.getDeclaredSignatureOrigin(env.fullName).nonEmpty)
    header
  }
}
