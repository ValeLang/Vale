package dev.vale.typing.macros.citizen

import dev.vale.highertyping.{FunctionA, InterfaceA}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.rules.{CallSR, IRulexSR, LookupSR, RuneUsage}
import dev.vale.{Accumulator, Interner, Keywords, RangeS, StrI}
import dev.vale.postparsing._
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

  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], FunctionEnvEntry)] = {
    def range(n: Int) = RangeS.internal(interner, n)
    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)


    val rules = new Accumulator[IRulexSR]()
    // Use the same rules as the original interface, see MDSFONARFO.
    interfaceA.rules.foreach(r => rules.add(r))
    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
    // Use the same runes as the original interface, see MDSFONARFO.
    interfaceA.runeToType.foreach(runeToType += _)

    val vooid = MacroVoidRuneS()
    runeToType.put(vooid, CoordTemplataType())
    rules.add(LookupSR(range(-1672147),use(-64002, vooid),interner.intern(CodeNameS(keywords.void))))

    val interfaceNameRune = StructNameRuneS(interfaceA.name)
    runeToType += (interfaceNameRune -> interfaceA.tyype)

    val self = MacroSelfRuneS()
    runeToType += (self -> CoordTemplataType())
    rules.add(
      LookupSR(
        interfaceA.name.range,
        RuneUsage(interfaceA.name.range, interfaceNameRune),
        interfaceA.name.getImpreciseName(interner)))
    rules.add(
      CallSR(
        interfaceA.name.range,
        use(-64002, self),
        RuneUsage(interfaceA.name.range, interfaceNameRune),
        interfaceA.genericParameters.map(_.rune).toVector))

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
            AtomSP(
              range(-1340),
              Some(CaptureS(interner.intern(CodeVarNameS(keywords.thiss)))),
              Some(AbstractSP(range(-64002), true)),
              Some(use(-64002, self)), None))),
        Some(use(-64002, vooid)),
        rules.buildArray().toVector,
        AbstractBodyS)

    Vector(
      interfaceName.copy(last = nameTranslator.translateGenericFunctionName(dropFunctionA.name)) ->
        FunctionEnvEntry(dropFunctionA))
  }

//  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
//    Vector()
//  }
}
