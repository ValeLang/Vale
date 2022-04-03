package dev.vale.typing.macros

import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale.{Interner, RangeS, vassert, vassertSome, vimpl}
import dev.vale.parsing.ast.{FinalP, UseP}
import dev.vale.postparsing.patterns.{AbstractSP, AtomSP, CaptureS}
import dev.vale.postparsing.{AnonymousSubstructImplDeclarationNameS, AnonymousSubstructMemberRuneS, AnonymousSubstructParentInterfaceRuneS, AnonymousSubstructParentInterfaceTemplateRuneS, AnonymousSubstructRuneS, AnonymousSubstructTemplateImpreciseNameS, AnonymousSubstructTemplateNameS, AnonymousSubstructTemplateRuneS, BlockSE, BodySE, CodeBodyS, CoordTemplataType, DotSE, ForwarderFunctionDeclarationNameS, FunctionCallSE, FunctionTemplataType, ITemplataType, ImplImpreciseNameS, KindTemplataType, LocalLoadSE, LocalS, NormalStructMemberS, NotUsed, OwnershipTemplataType, ParameterS, SelfKindRuneS, SelfKindTemplateRuneS, SelfNameS, SelfOwnershipRuneS, SelfRuneS, TemplateTemplataType, Used}
import dev.vale.postparsing.rules.{CallSR, CoordComponentsSR, LookupSR, RuleScout, RuneUsage}
import dev.vale.typing.{OverloadResolver, TypingPassOptions}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{FunctionEnvEntry, IEnvEntry, ImplEnvEntry, StructEnvEntry}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.citizen.{ImplDropMacro, InterfaceFreeMacro, StructDropMacro, StructFreeMacro}
import dev.vale.typing.names.{FullNameT, INameT, NameTranslator}
import dev.vale.typing.types.MutabilityT
import dev.vale.{CodeLocationS, Interner, PackageCoordinate, Profiler, RangeS, vassert, vassertOne, vassertSome, vfail, vimpl, vwat}
import dev.vale.highertyping.FunctionA
import dev.vale.parsing.ast.UseP
import dev.vale.postparsing.ImplImpreciseNameS
import dev.vale.postparsing.patterns._
import dev.vale.postparsing.rules.Equivalencies
import dev.vale.typing.ast._
import dev.vale.typing.env.PackageEnvironment
import dev.vale.typing.function.FunctionCompilerCore
import dev.vale.typing.macros.citizen.StructDropMacro
import dev.vale.typing.names.AnonymousSubstructImplNameT
import dev.vale.typing.templata.ExternFunctionTemplata
import dev.vale.typing.ast
import dev.vale.typing.types.ParamFilter

import scala.collection.immutable.List
import scala.collection.mutable

class AnonymousInterfaceMacro(
    opts: TypingPassOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    overloadCompiler: OverloadResolver,
    structCompiler: StructCompiler,
    structConstructorMacro: StructConstructorMacro,
    structDropMacro: StructDropMacro,
    structFreeMacro: StructFreeMacro,
    interfaceFreeMacro: InterfaceFreeMacro,
    implDropMacro: ImplDropMacro
) extends IOnInterfaceDefinedMacro {

  val macroName: String = "DeriveAnonymousSubstruct"

//  val generatorId: String = "interfaceConstructorGenerator"

  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
    vimpl()
  }

  override def getInterfaceSiblingEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA): Vector[(FullNameT[INameT], IEnvEntry)] = {
    val memberRunes =
      interfaceA.internalMethods.zipWithIndex.map({ case (method, index) =>
        RuneUsage(RangeS(method.range.begin, method.range.begin), AnonymousSubstructMemberRuneS(index))
      })
    val members =
      interfaceA.internalMethods.zip(memberRunes).zipWithIndex.map({ case ((method, rune), index) =>
        NormalStructMemberS(method.range, index.toString, FinalP, rune)
      })

    val structNameS = interner.intern(AnonymousSubstructTemplateNameS(interfaceA.name))
    val structNameT = interfaceName.copy(last = nameTranslator.translateNameStep(structNameS))
    val structA = makeStruct(interfaceA, memberRunes, members, structNameS)

    val moreEntries =
        interfaceFreeMacro.getInterfaceSiblingEntries(structNameT, interfaceA) ++
        structConstructorMacro.getStructSiblingEntries(structConstructorMacro.macroName, structNameT, structA) ++
        structDropMacro.getStructSiblingEntries(structDropMacro.macroName, structNameT, structA) ++
        structFreeMacro.getStructSiblingEntries(structFreeMacro.macroName, structNameT, structA)

    val forwarderMethods =
      interfaceA.internalMethods.zip(memberRunes).zipWithIndex.map({ case ((method, rune), methodIndex) =>
        val name = structNameT.copy(last = nameTranslator.translateFunctionNameToTemplateName(method.name))
        (name, FunctionEnvEntry(makeForwarderFunction(structNameS, structA.tyype, structA, method, methodIndex)))
      })

    val rules =
      structA.rules :+
        LookupSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructTemplateRuneS()),
          structA.name.getImpreciseName(interner)) :+
        CallSR(
          structA.range,
          RuneUsage(structA.range, AnonymousSubstructRuneS()),
          RuneUsage(structA.range, AnonymousSubstructTemplateRuneS()),
          structA.identifyingRunes.toArray) :+
        LookupSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceTemplateRuneS()),
          interfaceA.name.getImpreciseName(interner)) :+
        CallSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceRuneS()),
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceTemplateRuneS()),
          interfaceA.identifyingRunes.toArray)
    val runeToType =
      structA.runeToType +
        (AnonymousSubstructRuneS() -> KindTemplataType) +
        (AnonymousSubstructTemplateRuneS() -> structA.tyype) +
        (AnonymousSubstructParentInterfaceRuneS() -> KindTemplataType) +
        (AnonymousSubstructParentInterfaceTemplateRuneS() -> interfaceA.tyype)
    val structKindRuneS = RuneUsage(interfaceA.range, AnonymousSubstructRuneS())
    val interfaceKindRuneS = RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceRuneS())

    val implNameS = interner.intern(AnonymousSubstructImplDeclarationNameS(interfaceA.name))
    val implImpreciseNameS = interner.intern(ImplImpreciseNameS(RuleScout.getRuneKindTemplate(rules, structKindRuneS.rune)))

    val implA =
      ImplA(
        interfaceA.range,
        implNameS,
        // Just getting the template name (or the kind name if not template), see INSHN.
        implImpreciseNameS,
        structA.identifyingRunes,
        rules,
        runeToType,
        structKindRuneS,
        interfaceKindRuneS)
    val implNameT = structNameT.copy(last = nameTranslator.translateNameStep(implA.name))
    val implSiblingEntries =
      implDropMacro.getImplSiblingEntries(implNameT, implA)

    Vector[(FullNameT[INameT], IEnvEntry)](
      (structNameT, StructEnvEntry(structA)),
      (implNameT, ImplEnvEntry(implA))) ++
      moreEntries ++
      forwarderMethods ++
      implSiblingEntries
  }

  private def makeStruct(interfaceA: InterfaceA, memberRunes: Vector[RuneUsage], members: Vector[NormalStructMemberS], structTemplateNameS: AnonymousSubstructTemplateNameS) = {
    StructA(
      interfaceA.range,
      structTemplateNameS,
      Vector(),
      false,
      interfaceA.mutabilityRune,
      interfaceA.maybePredictedMutability,
      TemplateTemplataType(
        (interfaceA.tyype match {
          case KindTemplataType => Vector()
          case TemplateTemplataType(paramTypes, KindTemplataType) => paramTypes
        }) ++ memberRunes.map(_ => CoordTemplataType),
        KindTemplataType),
      interfaceA.identifyingRunes ++ memberRunes,
      interfaceA.runeToType ++ memberRunes.map(_.rune -> CoordTemplataType),
      interfaceA.rules,
      members)
  }

  private def makeForwarderFunction(
    structNameS: AnonymousSubstructTemplateNameS,
    structType: ITemplataType,
    struct: StructA,
    method: FunctionA,
    methodIndex: Int):
  FunctionA = {
    val FunctionA(methodRange, name, attributes, methodOriginalType, methodOriginalIdentifyingRunes, methodOriginalRuneToType, originalParams, maybeRetCoordRune, rules, body) = method

    vassert(struct.identifyingRunes.map(_.rune).startsWith(methodOriginalIdentifyingRunes.map(_.rune)))
    val identifyingRunes = struct.identifyingRunes

    val runeToType = methodOriginalRuneToType ++ struct.runeToType

    val abstractParamIndex =
      originalParams.indexWhere(param => {
        param.pattern.virtuality match {
          case Some(AbstractSP(_, _)) => true
          case _ => false
        }
      })
    vassert(abstractParamIndex >= 0)
    val abstractParam = originalParams(abstractParamIndex)
    val abstractParamCoordRune = vassertSome(abstractParam.pattern.coordRune) // https://github.com/ValeLang/Vale/issues/370
    val abstractParamRange = abstractParam.pattern.range

    val destructuringInterfaceRule =
      CoordComponentsSR(
        abstractParamRange,
        abstractParamCoordRune,
        RuneUsage(abstractParamRange, SelfOwnershipRuneS()),
        RuneUsage(abstractParamRange, AnonymousSubstructParentInterfaceTemplateRuneS()))
    val lookupStructTemplateRule =
      LookupSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, SelfKindTemplateRuneS()),
        interner.intern(AnonymousSubstructTemplateImpreciseNameS(structNameS.interfaceName.getImpreciseName(interner))))
    val lookupStructRule =
      CallSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, SelfKindRuneS()),
        RuneUsage(abstractParamRange, SelfKindTemplateRuneS()),
        identifyingRunes.toArray)

    val assemblingStructRule =
      CoordComponentsSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, SelfRuneS()),
        RuneUsage(abstractParamRange, SelfOwnershipRuneS()),
        RuneUsage(abstractParamRange, SelfKindRuneS()))

    val newParam =
      ParameterS(
        AtomSP(
          abstractParamRange,
          Some(CaptureS(interner.intern(SelfNameS()))),
          None,//Some(OverrideSP(abstractParamRange, RuneUsage(abstractParamCoordRune.range, AnonymousSubstructParentInterfaceTemplateRuneS()))),
          Some(RuneUsage(abstractParamCoordRune.range, SelfRuneS())),
          None))

    val newParams = originalParams.updated(abstractParamIndex, newParam)

    val newBody =
      FunctionCallSE(
        methodRange,
        DotSE(
          methodRange,
          LocalLoadSE(methodRange, interner.intern(SelfNameS()), UseP),
          methodIndex.toString,
          false),
        // Params minus the abstract param
        (newParams.slice(0, abstractParamIndex) ++ newParams.slice(abstractParamIndex + 1, newParams.length))
          .map(param => vassertSome(param.pattern.name).name)
          .map(name => LocalLoadSE(methodRange, name, UseP)))

    FunctionA(
      methodRange,
      interner.intern(ForwarderFunctionDeclarationNameS(name, methodIndex)),
      attributes,
      TemplateTemplataType(
        (methodOriginalType match {
          case FunctionTemplataType => Vector()
          case TemplateTemplataType(paramTypes, FunctionTemplataType) => paramTypes
        }) ++ struct.identifyingRunes.map(_ => CoordTemplataType),
        FunctionTemplataType),
      identifyingRunes,
      runeToType ++
        Vector(
          SelfRuneS() -> CoordTemplataType,
          SelfKindRuneS() -> KindTemplataType,
          SelfKindTemplateRuneS() -> structType,
          SelfOwnershipRuneS() -> OwnershipTemplataType,
          AnonymousSubstructParentInterfaceTemplateRuneS() -> KindTemplataType),
      newParams,
      maybeRetCoordRune,
      rules ++ Vector(destructuringInterfaceRule, lookupStructRule, lookupStructTemplateRule, assemblingStructRule),
      CodeBodyS(
        BodySE(
          methodRange,
          Vector(),
          BlockSE(
            methodRange,
            newParams.map(param => vassertSome(param.pattern.name).name).map(LocalS(_, NotUsed, Used, NotUsed, NotUsed, NotUsed, NotUsed)),
            newBody))))
  }
}
