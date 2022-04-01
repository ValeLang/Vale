package dev.vale.typing.macros

import dev.vale.RangeS
import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.typing.CompilerOutputs
import dev.vale.typing.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT}
import dev.vale.typing.env.{FunctionEnvironment, IEnvEntry}
import dev.vale.typing.names.{FullNameT, INameT}
import dev.vale.typing.types.{CoordT, MutabilityT}
import dev.vale.RangeS
import dev.vale.highertyping.FunctionA
import dev.vale.typing.ast._
import dev.vale.typing.env.IEnvEntry
import dev.vale.typing.names.CitizenTemplateNameT
import dev.vale.typing.types.InterfaceTT

trait IFunctionBodyMacro {
//  def generatorId: String

  def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction: Option[FunctionA],
    paramCoords: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT
}

trait IOnStructDefinedMacro {
  def getStructSiblingEntries(
    macroName: String, structName: FullNameT[INameT], structA: StructA):
  Vector[(FullNameT[INameT], IEnvEntry)]

  def getStructChildEntries(
    macroName: String, structName: FullNameT[INameT], structA: StructA, mutability: MutabilityT):
  Vector[(FullNameT[INameT], IEnvEntry)]
}

trait IOnInterfaceDefinedMacro {
  def getInterfaceSiblingEntries(
    interfaceName: FullNameT[INameT], interfaceA: InterfaceA):
  Vector[(FullNameT[INameT], IEnvEntry)]

  def getInterfaceChildEntries(
    interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT):
  Vector[(FullNameT[INameT], IEnvEntry)]
}

trait IOnImplDefinedMacro {
  def getImplSiblingEntries(implName: FullNameT[INameT], implA: ImplA):
  Vector[(FullNameT[INameT], IEnvEntry)]
}
