package dev.vale.typing.macros.citizen

import dev.vale.{Interner, Keywords, _}
import dev.vale.highertyping.StructA
import dev.vale.typing.env.IEnvEntry
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.IOnStructDefinedMacro
import dev.vale.typing.names.{FullNameT, INameT, NameTranslator}
import dev.vale.typing.types.MutabilityT
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules._
import dev.vale.typing.env
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvEntry
import dev.vale.typing.macros.IOnImplDefinedMacro
import dev.vale.typing.names._
import dev.vale.typing.types.ParamFilter

class ImplFreeMacro(
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator,
) extends IOnStructDefinedMacro {
  val macroName: StrI = keywords.DeriveImplFree
//  val generatorId = "freeImplGenerator"

  override def getStructSiblingEntries(macroName: StrI, structName: FullNameT[INameT], structA: StructA): Vector[(FullNameT[INameT], IEnvEntry)] = {
    Vector()
  }

  override def getStructChildEntries(macroName: StrI, structName: FullNameT[INameT], structA: StructA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
//    if (mutability != ImmutableT) {
//      return Vector()
//    }
//    val virtualFreeFunctionA =
//      FunctionA(
//        structA.range,
//        interner.intern(OverrideVirtualFreeDeclarationNameS(structA.range.begin)),
//        Vector(),
//        TemplateTemplataType(
//          structA.identifyingRunes.map(_.rune).map(structA.runeToType) :+ KindTemplataType,
//          FunctionTemplataType),
//        structA.identifyingRunes :+ RuneUsage(RangeS.internal(interner, -64002), FreeOverrideInterfaceRuneS()),
//        structA.runeToType +
//          (ImplDropCoordRuneS() -> CoordTemplataType) +
//          (ImplDropVoidRuneS() -> CoordTemplataType) +
//          (FreeOverrideStructTemplateRuneS() -> structA.tyype) +
//          (FreeOverrideStructRuneS() -> KindTemplataType) +
//          (FreeOverrideInterfaceRuneS() -> KindTemplataType),
//        Vector(
//          ParameterS(
//            AtomSP(
//              RangeS.internal(interner, -1340),
//              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//              Some(OverrideSP(RangeS.internal(interner, -64002), RuneUsage(RangeS.internal(interner, -64002), FreeOverrideInterfaceRuneS()))),
//              Some(RuneUsage(RangeS.internal(interner, -64002), ImplDropCoordRuneS())), None))),
//        Some(RuneUsage(RangeS.internal(interner, -64002), ImplDropVoidRuneS())),
//        structA.rules ++
//        Vector(
//          CoerceToCoordSR(
//            RangeS.internal(interner, -1672138),
//            RuneUsage(RangeS.internal(interner, -167215), ImplDropCoordRuneS()),
//            RuneUsage(RangeS.internal(interner, -167214), FreeOverrideStructRuneS())),
//          structA.tyype match {
//            case KindTemplataType => {
//              EqualsSR(
//                RangeS.internal(interner, -1672139),
//                RuneUsage(RangeS.internal(interner, -167219), FreeOverrideStructRuneS()),
//                RuneUsage(RangeS.internal(interner, -167219), FreeOverrideStructTemplateRuneS()))
//            }
//            case TemplateTemplataType(paramTypes, KindTemplataType) => {
//              CallSR(
//                RangeS.internal(interner, -1672140),
//                RuneUsage(RangeS.internal(interner, -167219), FreeOverrideStructRuneS()),
//                RuneUsage(RangeS.internal(interner, -167219), FreeOverrideStructTemplateRuneS()),
//                structA.identifyingRunes.map(_.rune).map(r => RuneUsage(RangeS.internal(interner, -167219), r)).toArray)
//            }
//          },
//          LookupSR(RangeS.internal(interner, -1672141),RuneUsage(RangeS.internal(interner, -64002), FreeOverrideStructTemplateRuneS()),structA.name.getImpreciseName(interner)),
//          LookupSR(RangeS.internal(interner, -1672142),RuneUsage(RangeS.internal(interner, -64002), ImplDropVoidRuneS()),interner.intern(CodeNameS(StrI("void"))))),
////        GeneratedBodyS(generatorId))
//        CodeBodyS(
//          BodySE(RangeS.internal(interner, -1672143),
//            Vector(),
//            BlockSE(RangeS.internal(interner, -1672144),
//              Vector(LocalS(interner.intern(CodeVarNameS("this")), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
//              ReturnSE(RangeS.internal(interner, -1672145), VoidSE(RangeS.internal(interner, -167214)))))))
//    Vector((
//      structName.addStep(nameTranslator.translateFunctionNameToTemplateName(virtualFreeFunctionA.name)),
//      FunctionEnvEntry(virtualFreeFunctionA)))
    Vector()
  }
}
