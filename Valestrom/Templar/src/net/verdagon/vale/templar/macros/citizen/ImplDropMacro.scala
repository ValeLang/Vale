package net.verdagon.vale.templar.macros.citizen

import net.verdagon.vale.astronomer.{FunctionA, ImplA}
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout._
import net.verdagon.vale.templar.ast.{FunctionHeaderT, LocationInFunctionEnvironment, ParameterT}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment}
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplarCore}
import net.verdagon.vale.templar.macros.IOnImplDefinedMacro
import net.verdagon.vale.templar.names.{FullNameT, FunctionTemplateNameT, INameT, NameTranslator}
import net.verdagon.vale.templar.templata.{CoordTemplata, KindTemplata}
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.{ArrayTemplar, IFunctionGenerator, Temputs}
import net.verdagon.vale._
import net.verdagon.vale.parser.ast.{LoadAsPointerP, MoveP}
import net.verdagon.vale.templar.expression.CallTemplar

class ImplDropMacro(
  interner: Interner,
  nameTranslator: NameTranslator,
) extends IOnImplDefinedMacro {
  override def getImplSiblingEntries(implName: FullNameT[INameT], implA: ImplA):
  Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    val funcNameA =
      interner.intern(OverrideVirtualDropFunctionDeclarationNameS(implA.name))
    val dropFunctionA =
      FunctionA(
        implA.range,
        funcNameA,
        Vector(),
        TemplateTemplataType(
          implA.identifyingRunes.map(_.rune).map(implA.runeToType) :+ KindTemplataType,
          FunctionTemplataType),
        // See NIIRII for why we add the interface rune as an identifying rune.
        implA.identifyingRunes :+ implA.interfaceKindRune,
        implA.runeToType + (ImplDropCoordRuneS() -> CoordTemplataType) + (ImplDropVoidRuneS() -> CoordTemplataType),
        Vector(
          ParameterS(
            AtomSP(
              RangeS.internal(-1340),
              Some(CaptureS(interner.intern(CodeVarNameS("this")))),
              Some(OverrideSP(RangeS.internal(-64002), RuneUsage(RangeS.internal(-64002), implA.interfaceKindRune.rune))),
              Some(RuneUsage(RangeS.internal(-64002), ImplDropCoordRuneS())), None))),
        Some(RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS())),
        implA.rules ++
        Vector(
          CoerceToCoordSR(
            RangeS.internal(-167213),
            RuneUsage(RangeS.internal(-167214), ImplDropCoordRuneS()),
            RuneUsage(RangeS.internal(-167215), implA.structKindRune.rune)),
          LookupSR(RangeS.internal(-167213),RuneUsage(RangeS.internal(-64002), ImplDropVoidRuneS()),interner.intern(CodeNameS("void")))),
        CodeBodyS(
          BodySE(RangeS.internal(-167213),
            Vector(),
            BlockSE(RangeS.internal(-167213),
              Vector(LocalS(interner.intern(CodeVarNameS("this")), NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
              FunctionCallSE(RangeS.internal(-167213),
                OutsideLoadSE(RangeS.internal(-167213),
                  Array(),
                  interner.intern(CodeNameS(CallTemplar.DROP_FUNCTION_NAME)),
                  None,
                  LoadAsPointerP(None)),
                Vector(LocalLoadSE(RangeS.internal(-167213), interner.intern(CodeVarNameS("this")), MoveP)))))))
    Vector((
      implName.copy(last = nameTranslator.translateFunctionNameToTemplateName(funcNameA)),
      FunctionEnvEntry(dropFunctionA)))
  }
}
