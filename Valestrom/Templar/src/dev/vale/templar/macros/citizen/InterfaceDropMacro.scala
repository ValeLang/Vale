package dev.vale.templar.macros.citizen

import dev.vale.astronomer.{FunctionA, InterfaceA}
import dev.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.scout.rules.{LookupSR, RuneUsage}
import dev.vale.{Interner, RangeS}
import dev.vale.scout.{AbstractBodyS, CodeNameS, CodeRuneS, CodeVarNameS, CoordTemplataType, FunctionNameS, FunctionTemplataType, ParameterS, SelfNameS, TemplateTemplataType}
import dev.vale.templar.ast.PrototypeT
import dev.vale.templar.env.{FunctionEnvEntry, IEnvEntry}
import dev.vale.templar.expression.CallTemplar
import dev.vale.templar.macros.IOnInterfaceDefinedMacro
import dev.vale.templar.names.{FullNameT, INameT, NameTranslator}
import dev.vale.templar.types.MutabilityT
import dev.vale.astronomer.FunctionA
import dev.vale.parser.ast.MoveP
import dev.vale.scout._
import dev.vale.scout.patterns.AbstractSP
import dev.vale.scout.rules.LookupSR
import dev.vale.templar.env.IEnvironment
import dev.vale.templar.names.FunctionTemplateNameT
import dev.vale.templar.types._
import dev.vale.templar.OverloadTemplar
import dev.vale.RangeS

class InterfaceDropMacro(
  interner: Interner,
  nameTranslator: NameTranslator
) extends IOnInterfaceDefinedMacro {

  val macroName: String = "DeriveInterfaceDrop"

  override def getInterfaceSiblingEntries(structName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
    val dropFunctionA =
      FunctionA(
        interfaceA.name.range,
        interner.intern(FunctionNameS(CallTemplar.DROP_FUNCTION_NAME, interfaceA.name.range.begin)),
        Vector(),
        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
        Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
        Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
        Vector(
          ParameterS(
            AtomSP(
              RangeS.internal(-1340),
              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
              Some(AbstractSP(RangeS.internal(-64002), true)),
              Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
        Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
        Vector(
          LookupSR(RangeS.internal(-1672146),RuneUsage(RangeS.internal(-64002), CodeRuneS("T")),interner.intern(SelfNameS())),
          LookupSR(RangeS.internal(-1672147),RuneUsage(RangeS.internal(-64002), CodeRuneS("V")),interner.intern(CodeNameS("void")))),
        AbstractBodyS)
//        CodeBodyS(
//          BodySE(RangeS.internal(-1672148),
//            Vector(),
//            BlockSE(RangeS.internal(-1672149),
//              Vector(LocalS(interner.intern(CodeVarNameS("this")), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
//              FunctionCallSE(RangeS.internal(-1672150),
//                OutsideLoadSE(RangeS.internal(-1672151),
//                  Array(),
//                  interner.intern(CodeNameS(Scout.VIRTUAL_DROP_FUNCTION_NAME)),
//                  None,
//                  LoadAsPointerP(None)),
//                Vector(LocalLoadSE(RangeS.internal(-1672152), interner.intern(CodeVarNameS("this")), MoveP)))))))
//
//    val virtualDropFunctionNameA =
//      interner.intern(AbstractVirtualDropFunctionDeclarationNameS(interfaceA.name))
//    val virtualDropFunctionA =
//      FunctionA(
//        interfaceA.range,
//        virtualDropFunctionNameA,
//        Vector(),
//        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
//        Vector(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))),
//        Map(CodeRuneS("T") -> CoordTemplataType, CodeRuneS("V") -> CoordTemplataType),
//        Vector(
//          ParameterS(
//            AtomSP(
//              RangeS.internal(-1340),
//              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//              Some(AbstractSP(RangeS.internal(-1340), true)),
//              Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("T"))), None))),
//        Some(RuneUsage(RangeS.internal(-64002), CodeRuneS("V"))),
//        Vector(
//          LookupSR(RangeS.internal(-1672153),RuneUsage(RangeS.internal(-64002), CodeRuneS("T")),interner.intern(SelfNameS())),
//          LookupSR(RangeS.internal(-1672154),RuneUsage(RangeS.internal(-64002), CodeRuneS("V")),interner.intern(CodeNameS("void")))),
//        GeneratedBodyS("abstractBody"))

    Vector(
      interfaceName.addStep(nameTranslator.translateFunctionNameToTemplateName(dropFunctionA.name)) ->
        FunctionEnvEntry(dropFunctionA))//,
//      interfaceName.addStep(nameTranslator.translateFunctionNameToTemplateName(virtualDropFunctionA.name)) ->
//        FunctionEnvEntry(virtualDropFunctionA))
  }
}
