package dev.vale.typing.macros.citizen

import dev.vale.Interner
import dev.vale.highertyping.ImplA
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.FunctionEnvEntry
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.IOnImplDefinedMacro
import dev.vale.typing.names.{FullNameT, INameT, NameTranslator}
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules._
import dev.vale.postparsing._
import dev.vale.typing.ast._
import dev.vale.typing.env.FunctionEnvEntry
import dev.vale.typing.function.FunctionCompilerCore
import dev.vale.typing.names.FunctionTemplateNameT
import dev.vale.typing.templata.KindTemplata
import dev.vale.typing.types._
import dev.vale.typing.ArrayCompiler
import dev.vale._
import dev.vale.parsing.ast.MoveP

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
//                  interner.intern(CodeNameS(CallCompiler.DROP_FUNCTION_NAME)),
//                  None,
//                  LoadAsPointerP(None)),
//                Vector(LocalLoadSE(RangeS.internal(-1672137), interner.intern(CodeVarNameS("this")), MoveP)))))))
//    Vector((
//      implName.copy(last = nameTranslator.translateFunctionNameToTemplateName(funcNameA)),
//      FunctionEnvEntry(dropFunctionA)))
    Vector()
  }
}
