package dev.vale.typing.macros

import dev.vale.{RangeS, StrI}
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT}
import dev.vale.typing.env.{FunctionEnvironment, IEnvEntry}
import dev.vale.typing.names.{FullNameT, INameT}
import dev.vale.typing.types._
import dev.vale.RangeS
import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.postparsing.MutabilityTemplataType
import dev.vale.typing.ast._
import dev.vale.typing.env.IEnvEntry
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.templata.ITemplata
import dev.vale.typing.types.InterfaceTT

trait IFunctionBodyMacro {
//  def generatorId: String

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE)
}

trait IOnStructDefinedMacro {
  def getStructSiblingEntries(
    structName: FullNameT[INameT], structA: StructA):
  Vector[(FullNameT[INameT], IEnvEntry)]

  def getStructChildEntries(
    macroName: StrI,
    structName: FullNameT[INameT],
    structA: StructA,
    mutability: ITemplata[MutabilityTemplataType]):
  Vector[(FullNameT[INameT], IEnvEntry)]
}

trait IOnInterfaceDefinedMacro {
  def getInterfaceSiblingEntries(
    interfaceName: FullNameT[INameT], interfaceA: InterfaceA):
  Vector[(FullNameT[INameT], IEnvEntry)]
}

trait IOnImplDefinedMacro {
  def getImplSiblingEntries(implName: FullNameT[INameT], implA: ImplA):
  Vector[(FullNameT[INameT], IEnvEntry)]
}
