package dev.vale.templar.macros

import dev.vale.RangeS
import dev.vale.astronomer.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.templar.Temputs
import dev.vale.templar.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT}
import dev.vale.templar.env.{FunctionEnvironment, IEnvEntry}
import dev.vale.templar.names.{FullNameT, INameT}
import dev.vale.templar.types.{CoordT, MutabilityT}
import dev.vale.RangeS
import dev.vale.astronomer.FunctionA
import dev.vale.templar.ast._
import dev.vale.templar.env.IEnvEntry
import dev.vale.templar.names.CitizenTemplateNameT
import dev.vale.templar.types.InterfaceTT

trait IFunctionBodyMacro {
//  def generatorId: String

  def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
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
