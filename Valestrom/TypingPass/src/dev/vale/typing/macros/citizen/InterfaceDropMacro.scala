package dev.vale.typing.macros.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.rules.{LookupSR, RuneUsage}
import dev.vale.{Interner, RangeS}
import dev.vale.postparsing.{AbstractBodyS, CodeNameS, CodeRuneS, CodeVarNameS, CoordTemplataType, FunctionNameS, FunctionTemplataType, ParameterS, SelfNameS, TemplateTemplataType}
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.env.{FunctionEnvEntry, IEnvEntry}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.IOnInterfaceDefinedMacro
import dev.vale.typing.names.{FullNameT, INameT, NameTranslator}
import dev.vale.typing.types.MutabilityT
import dev.vale.highertyping.FunctionA
import dev.vale.parsing.ast.MoveP
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AbstractSP
import dev.vale.postparsing.rules.LookupSR
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.names.FunctionTemplateNameT
import dev.vale.typing.types._
import dev.vale.typing.OverloadResolver
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
        interner.intern(FunctionNameS(CallCompiler.DROP_FUNCTION_NAME, interfaceA.name.range.begin)),
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
