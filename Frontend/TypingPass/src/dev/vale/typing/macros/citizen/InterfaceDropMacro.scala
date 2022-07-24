package dev.vale.typing.macros.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.rules.{LookupSR, RuneUsage}
import dev.vale.{Interner, Keywords, RangeS, StrI}
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

class InterfaceDropMacro(
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator
) extends IOnInterfaceDefinedMacro {

  val macroName: StrI = keywords.DeriveInterfaceDrop

  override def getInterfaceSiblingEntries(structName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    Vector()
  }

  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
    val dropFunctionA =
      FunctionA(
        interfaceA.name.range,
        interner.intern(FunctionNameS(keywords.drop, interfaceA.name.range.begin)),
        Vector(),
        TemplateTemplataType(Vector(CoordTemplataType), FunctionTemplataType),
        Vector(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.T))),
        Map(CodeRuneS(keywords.T) -> CoordTemplataType, CodeRuneS(keywords.V) -> CoordTemplataType),
        Vector(
          ParameterS(
            AtomSP(
              RangeS.internal(interner, -1340),
              Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)))),
              Some(AbstractSP(RangeS.internal(interner, -64002), true)),
              Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.T))), None))),
        Some(RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.V))),
        Vector(
          LookupSR(RangeS.internal(interner, -1672146),RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.T)),interner.intern(SelfNameS())),
          LookupSR(RangeS.internal(interner, -1672147),RuneUsage(RangeS.internal(interner, -64002), CodeRuneS(keywords.V)),interner.intern(CodeNameS(keywords.void)))),
        AbstractBodyS)

    Vector(
      interfaceName.addStep(nameTranslator.translateFunctionNameToTemplateName(dropFunctionA.name)) ->
        FunctionEnvEntry(dropFunctionA))
  }
}
