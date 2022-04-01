package dev.vale.templar.macros.citizen

import dev.vale.Interner
import dev.vale.astronomer.StructA
import dev.vale.templar.env.IEnvEntry
import dev.vale.templar.expression.CallTemplar
import dev.vale.templar.macros.IOnStructDefinedMacro
import dev.vale.templar.names.{FullNameT, INameT, NameTranslator}
import dev.vale.templar.types.MutabilityT
import dev.vale._
import dev.vale.astronomer.FunctionA
import dev.vale.scout._
import dev.vale.scout.patterns._
import dev.vale.scout.rules._
import dev.vale.templar.env
import dev.vale.templar.ast._
import dev.vale.templar.env.FunctionEnvEntry
import dev.vale.templar.macros.IOnImplDefinedMacro
import dev.vale.templar.names._
import dev.vale.templar.types.ParamFilter

class ImplFreeMacro(
  interner: Interner,
  nameTranslator: NameTranslator,
) extends IOnStructDefinedMacro {
  val macroName: String = "DeriveImplFree"
//  val generatorId = "freeImplGenerator"

  override def getStructSiblingEntries(macroName: String, structName: FullNameT[INameT], structA: StructA): Vector[(FullNameT[INameT], IEnvEntry)] = {
    Vector()
  }

  override def getStructChildEntries(macroName: String, structName: FullNameT[INameT], structA: StructA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
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
//        structA.identifyingRunes :+ RuneUsage(RangeS.internal(-64002), FreeOverrideInterfaceRuneS()),
//        structA.runeToType +
//          (ImplDropCoordRuneS() -> CoordTemplataType) +
//          (ImplDropVoidRuneS() -> CoordTemplataType) +
//          (FreeOverrideStructTemplateRuneS() -> structA.tyype) +
//          (FreeOverrideStructRuneS() -> KindTemplataType) +
//          (FreeOverrideInterfaceRuneS() -> KindTemplataType),
//        Vector(
//          ParameterS(
//            AtomSP(
//              RangeS.internal(-1340),
//              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//              Some(OverrideSP(RangeS.internal(-64002), RuneUsage(RangeS.internal(-64002), FreeOverrideInterfaceRuneS()))),
//              Some(RuneUsage(RangeS.internal(-64002), ImplDropCoordRuneS())), None))),
//        Some(RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS())),
//        structA.rules ++
//        Vector(
//          CoerceToCoordSR(
//            RangeS.internal(-1672138),
//            RuneUsage(RangeS.internal(-167215), ImplDropCoordRuneS()),
//            RuneUsage(RangeS.internal(-167214), FreeOverrideStructRuneS())),
//          structA.tyype match {
//            case KindTemplataType => {
//              EqualsSR(
//                RangeS.internal(-1672139),
//                RuneUsage(RangeS.internal(-167219), FreeOverrideStructRuneS()),
//                RuneUsage(RangeS.internal(-167219), FreeOverrideStructTemplateRuneS()))
//            }
//            case TemplateTemplataType(paramTypes, KindTemplataType) => {
//              CallSR(
//                RangeS.internal(-1672140),
//                RuneUsage(RangeS.internal(-167219), FreeOverrideStructRuneS()),
//                RuneUsage(RangeS.internal(-167219), FreeOverrideStructTemplateRuneS()),
//                structA.identifyingRunes.map(_.rune).map(r => RuneUsage(RangeS.internal(-167219), r)).toArray)
//            }
//          },
//          LookupSR(RangeS.internal(-1672141),RuneUsage(RangeS.internal(-64002), FreeOverrideStructTemplateRuneS()),structA.name.getImpreciseName(interner)),
//          LookupSR(RangeS.internal(-1672142),RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS()),interner.intern(CodeNameS("void")))),
////        GeneratedBodyS(generatorId))
//        CodeBodyS(
//          BodySE(RangeS.internal(-1672143),
//            Vector(),
//            BlockSE(RangeS.internal(-1672144),
//              Vector(LocalS(interner.intern(CodeVarNameS("this")), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
//              ReturnSE(RangeS.internal(-1672145), VoidSE(RangeS.internal(-167214)))))))
//    Vector((
//      structName.addStep(nameTranslator.translateFunctionNameToTemplateName(virtualFreeFunctionA.name)),
//      FunctionEnvEntry(virtualFreeFunctionA)))
    Vector()
  }
}
