package net.verdagon.vale.templar.macros.citizen

import net.verdagon.vale.astronomer.{FunctionA, InterfaceA}
import net.verdagon.vale.parser.{LoadAsPointerP, MoveP, ReadonlyP}
import net.verdagon.vale.scout._
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import net.verdagon.vale.scout.rules.{LookupSR, RuneUsage}
import net.verdagon.vale.templar.ast.PrototypeT
import net.verdagon.vale.templar.env.{FunctionEnvEntry, IEnvEntry, IEnvironment}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.macros.IOnInterfaceDefinedMacro
import net.verdagon.vale.templar.names.{FullNameT, FunctionTemplateNameT, INameT}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{OverloadTemplar, Templar, Temputs}
import net.verdagon.vale.{CodeLocationS, RangeS, vassert}

class InterfaceDropMacro(overloadTemplar: OverloadTemplar) extends IOnInterfaceDefinedMacro {

  val macroName: String = "DeriveInterfaceDrop"

  override def getInterfaceSiblingEntries(structName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
    val dropFunctionA =
      FunctionA(
        interfaceA.name.range,
        FunctionNameS(CallTemplar.DROP_FUNCTION_NAME, interfaceA.name.range.begin),
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
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), CodeRuneS("T")),SelfNameS()),
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), CodeRuneS("V")),CodeNameS("void"))),
        CodeBodyS(
          BodySE(RangeS.internal(-167213),
            Vector(),
            BlockSE(RangeS.internal(-167213),
              Vector(LocalS(CodeVarNameS("this"), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              Vector(
                FunctionCallSE(RangeS.internal(-167213),
                  OutsideLoadSE(RangeS.internal(-167213),
                    Array(),
                    CodeNameS(CallTemplar.VIRTUAL_DROP_FUNCTION_NAME),
                    None,
                    LoadAsPointerP(None)),
                  Vector(LocalLoadSE(RangeS.internal(-167213), CodeVarNameS("this"), MoveP))))))))

    val virtualDropFunctionA =
      FunctionA(
        interfaceA.range,
        FunctionNameS(CallTemplar.VIRTUAL_DROP_FUNCTION_NAME, interfaceA.range.begin),
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
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), CodeRuneS("T")),SelfNameS()),
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), CodeRuneS("V")),CodeNameS("void"))),
        GeneratedBodyS("abstractBody"))

    Vector(
      interfaceName.addStep(FunctionTemplateNameT(CallTemplar.DROP_FUNCTION_NAME, CodeLocationS.internal(-76))) ->
        FunctionEnvEntry(dropFunctionA),
      interfaceName.addStep(FunctionTemplateNameT(CallTemplar.VIRTUAL_DROP_FUNCTION_NAME, CodeLocationS.internal(-77))) ->
        FunctionEnvEntry(virtualDropFunctionA))
  }
}
