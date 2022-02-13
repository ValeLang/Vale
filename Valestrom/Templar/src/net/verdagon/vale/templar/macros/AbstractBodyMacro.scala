package net.verdagon.vale.templar.macros

import net.verdagon.vale.astronomer.FunctionA
import net.verdagon.vale.templar.{Temputs, ast}
import net.verdagon.vale.templar.ast._
import net.verdagon.vale.templar.env.FunctionEnvironment
import net.verdagon.vale.templar.types.CoordT
import net.verdagon.vale.{RangeS, vassert, vassertSome}

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
      ast.FunctionHeaderT(
        env.fullName,
        Vector.empty,
        params2,
        returnReferenceType2,
        originFunction)
    val function2 =
      ast.FunctionT(
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
