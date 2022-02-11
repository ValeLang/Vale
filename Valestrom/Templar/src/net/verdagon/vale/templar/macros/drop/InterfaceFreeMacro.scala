package net.verdagon.vale.templar.macros.drop

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA, VirtualFreeDeclarationNameS, VirtualFreeImpreciseNameS}
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{LookupSR, RuneUsage}
import net.verdagon.vale.templar.ast.{ArgLookupTE, BlockTE, FunctionCallTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, ParameterT, PrototypeT, ReturnTE, VoidLiteralTE}
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment, FunctionEnvironmentBox, IEnvironment}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.macros.{IFunctionBodyMacro, IOnInterfaceDefinedMacro}
import net.verdagon.vale.templar.names.{FreeTemplateNameT, FullNameT, FunctionTemplateNameT, INameT, VirtualFreeTemplateNameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{OverloadTemplar, Templar, Temputs, env}
import net.verdagon.vale.{CodeLocationS, RangeS, vassert}

class InterfaceFreeMacro(overloadTemplar: OverloadTemplar) extends IOnInterfaceDefinedMacro with IFunctionBodyMacro {

  val generatorId = "interfaceFreeGenerator"

  val macroName: String = "DeriveInterfaceFree"

  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(
    interfaceName: FullNameT[INameT],
    interfaceA: InterfaceA,
    mutability: MutabilityT):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    if (mutability == ImmutableT) {
      val freeFunctionNameS = FreeDeclarationNameS(interfaceA.range.begin)
      val freeFunctionA =
        FunctionA(
          interfaceA.name.range,
          freeFunctionNameS,
          Vector(),
          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
          Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
          Vector(
            ParameterS(
              AtomSP(
                RangeS.internal(-1340),
                Some(CaptureS(CodeVarNameS("this"))),
                None,
                Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
          Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
          Vector(
            LookupSR(RangeS.internal(-167213), RuneUsage(RangeS.internal(-64002), CodeRuneS("T")), SelfNameS()),
            LookupSR(RangeS.internal(-167213), RuneUsage(RangeS.internal(-64002), CodeRuneS("V")), CodeNameS("void"))),
          GeneratedBodyS(generatorId))

      val virtualFreeFunctionNameS = VirtualFreeDeclarationNameS(interfaceA.range.begin)
      val virtualFreeFunctionA =
        FunctionA(
          interfaceA.range,
          virtualFreeFunctionNameS,
          Vector(),
          TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
          Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
          Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
          Vector(
            ParameterS(
              AtomSP(
                RangeS.internal(-1340),
                Some(CaptureS(CodeVarNameS("this"))),
                Some(AbstractSP(RangeS.internal(-1340), true)),
                Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
          Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
          Vector(
            LookupSR(RangeS.internal(-167213), RuneUsage(RangeS.internal(-64002), CodeRuneS("T")), SelfNameS()),
            LookupSR(RangeS.internal(-167213), RuneUsage(RangeS.internal(-64002), CodeRuneS("V")), CodeNameS("void"))),
          GeneratedBodyS("abstractBody"))

      Vector(
        interfaceName.addStep(FreeTemplateNameT(freeFunctionNameS.codeLocationS)) ->
          FunctionEnvEntry(freeFunctionA),
        interfaceName.addStep(VirtualFreeTemplateNameT(virtualFreeFunctionNameS.codeLoc)) ->
          FunctionEnvEntry(virtualFreeFunctionA))
    } else {
      Vector()
    }
  }

  override def generateFunctionBody(
    env: FunctionEnvironment,
    temputs: Temputs,
    generatorId: String,
    life: LocationInFunctionEnvironment,
    callRange: RangeS,
    originFunction1: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  FunctionHeaderT = {
    val Vector(paramCoord @ CoordT(ShareT, ReadonlyT, InterfaceTT(_))) = params2.map(_.tyype)

    val ret = CoordT(ShareT, ReadonlyT, VoidT())
    val header = FunctionHeaderT(env.fullName, Vector.empty, params2, ret, originFunction1)

    temputs.declareFunctionReturnType(header.toSignature, header.returnType)

    val virtualFreePrototype =
      overloadTemplar.findFunction(
        env, temputs, callRange, VirtualFreeImpreciseNameS(), Vector(), Array(),
        Vector(ParamFilter(paramCoord, None)), Vector(), true)

    val expr = FunctionCallTE(virtualFreePrototype, Vector(ArgLookupTE(0, paramCoord)))

    val function2 = FunctionT(header, BlockTE(Templar.consecutive(Vector(expr, ReturnTE(VoidLiteralTE())))))
    temputs.addFunction(function2)
    function2.header}
}
