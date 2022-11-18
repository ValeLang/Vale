package dev.vale.typing.macros

import dev.vale.{RangeS, StrI}
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT}
import dev.vale.typing.env.{FunctionEnvironment, IEnvEntry}
import dev.vale.typing.names.{IdT, INameT}
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
    structName: IdT[INameT], structA: StructA):
  Vector[(IdT[INameT], IEnvEntry)]

  def getStructChildEntries(
    macroName: StrI,
    structName: IdT[INameT],
    structA: StructA,
    mutability: ITemplata[MutabilityTemplataType]):
  Vector[(IdT[INameT], IEnvEntry)]
}

trait IOnInterfaceDefinedMacro {
  def getInterfaceSiblingEntries(
    interfaceName: IdT[INameT], interfaceA: InterfaceA):
  Vector[(IdT[INameT], IEnvEntry)]
}

trait IOnImplDefinedMacro {
  def getImplSiblingEntries(implName: IdT[INameT], implA: ImplA):
  Vector[(IdT[INameT], IEnvEntry)]
}
