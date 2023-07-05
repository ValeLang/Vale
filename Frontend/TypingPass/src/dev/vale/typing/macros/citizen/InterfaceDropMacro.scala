package dev.vale.typing.macros.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA}
import dev.vale.postparsing.patterns.{AtomSP, CaptureS}
import dev.vale.postparsing.rules.{CallSR, CoerceToCoordSR, IRulexSR, LookupSR, RuneUsage}
import dev.vale.{Accumulator, Interner, Keywords, RangeS, StrI}
import dev.vale.postparsing._
import dev.vale.typing.ast.PrototypeT
import dev.vale.typing.env.{FunctionEnvEntry, IEnvEntry}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.IOnInterfaceDefinedMacro
import dev.vale.typing.names.{INameT, IdT, NameTranslator}
import dev.vale.typing.types.MutabilityT
import dev.vale.highertyping.FunctionA
import dev.vale.parsing.ast.MoveP
import dev.vale.postparsing._
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.names.FunctionTemplateNameT
import dev.vale.typing.types._
import dev.vale.typing.OverloadResolver

import scala.collection.mutable

class InterfaceDropMacro(
  interner: Interner,
  keywords: Keywords,
  nameTranslator: NameTranslator
) extends IOnInterfaceDefinedMacro {

  val macroName: StrI = keywords.DeriveInterfaceDrop

  override def getInterfaceSiblingEntries(interfaceName: IdT[INameT], interfaceA: InterfaceA): Vector[(IdT[INameT], FunctionEnvEntry)] = {
    def range(n: Int) = RangeS.internal(interner, n)
    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)


    val rules = new Accumulator[IRulexSR]()
    // Use the same rules as the original interface, see MDSFONARFO.
    interfaceA.rules.foreach(r => rules.add(r))
    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
    // Use the same runes as the original interface, see MDSFONARFO.
    interfaceA.runeToType.foreach(runeToType += _)

    val voidKindRune = MacroVoidKindRuneS()
    runeToType.put(voidKindRune, KindTemplataType())
    rules.add(LookupSR(range(-1672147),use(-64002, voidKindRune),interner.intern(CodeNameS(keywords.void))))
    val voidCoordRune = MacroVoidCoordRuneS()
    runeToType.put(voidCoordRune, CoordTemplataType())
    rules.add(CoerceToCoordSR(range(-1672147),use(-64002, voidCoordRune),use(-64002, voidKindRune)))

    val selfTemplateRune = MacroSelfKindTemplateRuneS()
    runeToType += (selfTemplateRune -> interfaceA.tyype)
    rules.add(
      LookupSR(
        interfaceA.name.range,
        RuneUsage(interfaceA.name.range, selfTemplateRune),
        interfaceA.name.getImpreciseName(interner)))

    val selfKindRune = MacroSelfKindRuneS()
    runeToType += (selfKindRune -> KindTemplataType())
    rules.add(
      CallSR(
        interfaceA.name.range,
        use(-64002, selfKindRune),
        RuneUsage(interfaceA.name.range, selfTemplateRune),
        interfaceA.genericParameters.map(_.rune).toVector))

    val selfCoordRune = MacroSelfCoordRuneS()
    runeToType += (selfCoordRune -> CoordTemplataType())
    rules.add(
      CoerceToCoordSR(
        interfaceA.name.range,
        RuneUsage(interfaceA.name.range, selfCoordRune),
        RuneUsage(interfaceA.name.range, selfKindRune)))

    // Use the same generic parameters as the interface, see MDSFONARFO.
    val functionGenericParameters = interfaceA.genericParameters

    val functionTemplataType =
      TemplateTemplataType(
        functionGenericParameters.map(_.rune.rune).map(runeToType),
        FunctionTemplataType())

    val dropFunctionA =
      FunctionA(
        interfaceA.name.range,
        interner.intern(FunctionNameS(keywords.drop, interfaceA.name.range.begin)),
        Vector(),
        functionTemplataType,
        functionGenericParameters,
        runeToType.toMap,
        Vector(
          ParameterS(
            range(-1340),
            Some(AbstractSP(range(-64002), true)),
            false,
            AtomSP(
              range(-1340),
              Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)), false)),
              Some(use(-64002, selfCoordRune)), None))),
        Some(use(-64002, voidCoordRune)),
        rules.buildArray().toVector,
        AbstractBodyS)

    Vector(
      interfaceName.copy(localName = nameTranslator.translateGenericFunctionName(dropFunctionA.name)) ->
        FunctionEnvEntry(dropFunctionA))
  }
}
