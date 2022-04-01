package dev.vale.templar.macros.citizen

import dev.vale.Interner
import dev.vale.astronomer.ImplA
import dev.vale.templar.citizen.StructTemplar
import dev.vale.templar.env.FunctionEnvEntry
import dev.vale.templar.expression.CallTemplar
import dev.vale.templar.macros.IOnImplDefinedMacro
import dev.vale.templar.names.{FullNameT, INameT, NameTranslator}
import dev.vale.astronomer.FunctionA
import dev.vale.scout.patterns._
import dev.vale.scout.rules._
import dev.vale.scout._
import dev.vale.templar.ast._
import dev.vale.templar.env.FunctionEnvEntry
import dev.vale.templar.function.FunctionTemplarCore
import dev.vale.templar.names.FunctionTemplateNameT
import dev.vale.templar.templata.KindTemplata
import dev.vale.templar.types._
import dev.vale.templar.ArrayTemplar
import dev.vale._
import dev.vale.parser.ast.MoveP

class ImplDropMacro(
  interner: Interner,
  nameTranslator: NameTranslator,
) extends IOnImplDefinedMacro {
  override def getImplSiblingEntries(implName: FullNameT[INameT], implA: ImplA):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
//    val funcNameA =
//      interner.intern(OverrideVirtualDropFunctionDeclarationNameS(implA.name))
//    val dropFunctionA =
//      FunctionA(
//        implA.range,
//        funcNameA,
//        Vector(),
//        TemplateTemplataType(
//          implA.identifyingRunes.map(_.rune).map(implA.runeToType) :+ KindTemplataType,
//          FunctionTemplataType),
//        // See NIIRII for why we add the interface rune as an identifying rune.
//        implA.identifyingRunes :+ implA.interfaceKindRune,
//        implA.runeToType + (ImplDropCoordRuneS() -> CoordTemplataType) + (ImplDropVoidRuneS() -> CoordTemplataType),
//        Vector(
//          ParameterS(
//            AtomSP(
//              RangeS.internal(-1340),
//              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
//              Some(OverrideSP(RangeS.internal(-64002), RuneUsage(RangeS.internal(-64002), implA.interfaceKindRune.rune))),
//              Some(RuneUsage(RangeS.internal(-64002), ImplDropCoordRuneS())), None))),
//        Some(RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS())),
//        implA.rules ++
//        Vector(
//          CoerceToCoordSR(
//            RangeS.internal(-1672131),
//            RuneUsage(RangeS.internal(-167214), ImplDropCoordRuneS()),
//            RuneUsage(RangeS.internal(-167215), implA.structKindRune.rune)),
//          LookupSR(RangeS.internal(-1672132),RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS()),interner.intern(CodeNameS("void")))),
//        CodeBodyS(
//          BodySE(RangeS.internal(-1672133),
//            Vector(),
//            BlockSE(RangeS.internal(-1672134),
//              Vector(LocalS(interner.intern(CodeVarNameS("this")), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
//              FunctionCallSE(RangeS.internal(-1672135),
//                OutsideLoadSE(RangeS.internal(-1672136),
//                  Array(),
//                  interner.intern(CodeNameS(CallTemplar.DROP_FUNCTION_NAME)),
//                  None,
//                  LoadAsPointerP(None)),
//                Vector(LocalLoadSE(RangeS.internal(-1672137), interner.intern(CodeVarNameS("this")), MoveP)))))))
//    Vector((
//      implName.copy(last = nameTranslator.translateFunctionNameToTemplateName(funcNameA)),
//      FunctionEnvEntry(dropFunctionA)))
    Vector()
  }
}
