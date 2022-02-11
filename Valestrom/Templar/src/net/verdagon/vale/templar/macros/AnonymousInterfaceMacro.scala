package net.verdagon.vale.templar.macros

import net.verdagon.vale.{CodeLocationS, IProfiler, PackageCoordinate, RangeS, vassert, vassertOne, vassertSome, vfail, vimpl, vwat}
import net.verdagon.vale.astronomer.{ConstructorNameS, FunctionA, ImplA, ImplImpreciseNameS, InterfaceA, StructA}
import net.verdagon.vale.parser.ast.{FinalP, UseP}
import net.verdagon.vale.scout.{AnonymousSubstructMemberNameS, AnonymousSubstructMemberRuneS, AnonymousSubstructParentInterfaceRuneS, AnonymousSubstructParentInterfaceTemplateRuneS, AnonymousSubstructRuneS, AnonymousSubstructTemplateImpreciseNameS, AnonymousSubstructTemplateNameS, AnonymousSubstructTemplateRuneS, BlockSE, BodySE, CodeBodyS, CodeNameS, CodeRuneS, CoordTemplataType, DotSE, FunctionCallSE, FunctionNameS, FunctionTemplataType, GeneratedBodyS, GlobalFunctionFamilyNameS, IRuneS, ITemplataType, ImplDeclarationNameS, KindTemplataType, LocalLoadSE, LocalS, NormalStructMemberS, NotUsed, OwnershipTemplataType, ParameterS, PermissionTemplataType, RuneNameS, SelfKindRuneS, SelfKindTemplateRuneS, SelfNameS, SelfOwnershipRuneS, SelfPermissionRuneS, SelfRuneS, StructNameRuneS, TemplateTemplataType, TopLevelCitizenDeclarationNameS, Used, UserFunctionS}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules.{AugmentSR, CallSR, CoerceToCoordSR, CoordComponentsSR, EqualsSR, Equivalencies, IRulexSR, LookupSR, OwnershipLiteralSL, RuleScout, RuneUsage}
import net.verdagon.vale.templar.ast.{AbstractT, ArgLookupTE, BlockTE, ConstructTE, DiscardTE, FunctionCallTE, FunctionHeaderT, FunctionT, LocationInFunctionEnvironment, OverrideT, ParameterT, PrototypeT, ReferenceMemberLookupTE, ReturnTE, SoftLoadTE}
import net.verdagon.vale.templar.citizen.StructTemplar
import net.verdagon.vale.templar.env.{FunctionEnvEntry, FunctionEnvironment, IEnvEntry, IEnvironment, ImplEnvEntry, PackageEnvironment, StructEnvEntry, TemplataEnvEntry, TemplataLookupContext}
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplarCore}
import net.verdagon.vale.templar.macros.citizen.{ImplDropMacro, InterfaceFreeMacro, StructDropMacro, StructFreeMacro}
import net.verdagon.vale.templar.names.{AnonymousSubstructImplNameT, AnonymousSubstructLambdaNameT, AnonymousSubstructMemberNameT, AnonymousSubstructNameT, ConstructorNameT, FullNameT, FunctionNameT, ICitizenNameT, INameT, ImplDeclareNameT, NameTranslator, RuneNameT, TemplarTemporaryVarNameT}
import net.verdagon.vale.templar.templata.{CoordTemplata, ExternFunctionTemplata, InterfaceTemplata, KindTemplata, MutabilityTemplata}
import net.verdagon.vale.templar.{ArrayTemplar, CompileErrorExceptionT, IFunctionGenerator, LambdaReturnDoesntMatchInterfaceConstructor, OverloadTemplar, RangedInternalErrorT, Templar, TemplarOptions, Temputs, ast}
import net.verdagon.vale.templar.types.{CoordT, FinalT, ImmutableT, InterfaceTT, MutabilityT, MutableT, NeverT, ParamFilter, PointerT, ReadonlyT, ReadwriteT, ReferenceMemberTypeT, ShareT, StructDefinitionT, StructMemberT, StructTT}

import scala.collection.immutable.List
import scala.collection.mutable

class AnonymousInterfaceMacro(
  opts: TemplarOptions,
  profiler: IProfiler,
  overloadTemplar: OverloadTemplar,
  structTemplar: StructTemplar,
  structConstructorMacro: StructConstructorMacro,
  structDropMacro: StructDropMacro,
  structFreeMacro: StructFreeMacro,
  interfaceFreeMacro: InterfaceFreeMacro,
  implDropMacro: ImplDropMacro) extends IOnInterfaceDefinedMacro {

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

    val structNameS = AnonymousSubstructTemplateNameS(interfaceA.name)
    val structNameT = interfaceName.copy(last = NameTranslator.translateNameStep(structNameS))
    val structA = makeStruct(interfaceA, memberRunes, members, structNameS)

    val moreEntries =
        interfaceFreeMacro.getInterfaceSiblingEntries(structNameT, interfaceA) ++
        structConstructorMacro.getStructSiblingEntries(structConstructorMacro.macroName, structNameT, structA) ++
        structDropMacro.getStructSiblingEntries(structDropMacro.macroName, structNameT, structA) ++
        structFreeMacro.getStructSiblingEntries(structFreeMacro.macroName, structNameT, structA)

    val forwarderMethods =
      interfaceA.internalMethods.zip(memberRunes).zipWithIndex.map({ case ((method, rune), methodIndex) =>
        val name = structNameT.copy(last = NameTranslator.translateFunctionNameToTemplateName(method.name))
        (name, FunctionEnvEntry(makeForwarderFunction(structNameS, structA.tyype, structA, method, methodIndex)))
      })

    val rules =
      structA.rules :+
        LookupSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructTemplateRuneS()),
          structA.name.getImpreciseName) :+
        CallSR(
          structA.range,
          RuneUsage(structA.range, AnonymousSubstructRuneS()),
          RuneUsage(structA.range, AnonymousSubstructTemplateRuneS()),
          structA.identifyingRunes.toArray) :+
        LookupSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceTemplateRuneS()),
          interfaceA.name.getImpreciseName) :+
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

    val implA =
      ImplA(
        interfaceA.range,
        ImplDeclarationNameS(interfaceA.range.begin),
        // Just getting the template name (or the kind name if not template), see INSHN.
        ImplImpreciseNameS(RuleScout.getRuneKindTemplate(rules, structKindRuneS.rune)),
        structA.identifyingRunes,
        rules,
        runeToType,
        structKindRuneS,
        interfaceKindRuneS)
    val implNameT = structNameT.copy(last = NameTranslator.translateNameStep(implA.name))
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
        RuneUsage(abstractParamRange, SelfPermissionRuneS()),
        RuneUsage(abstractParamRange, AnonymousSubstructParentInterfaceTemplateRuneS()))
    val lookupStructTemplateRule =
      LookupSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, SelfKindTemplateRuneS()),
        AnonymousSubstructTemplateImpreciseNameS(structNameS.interfaceName.getImpreciseName))
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
        RuneUsage(abstractParamRange, SelfPermissionRuneS()),
        RuneUsage(abstractParamRange, SelfKindRuneS()))

    val newParam =
      ParameterS(
        AtomSP(
          abstractParamRange,
          Some(CaptureS(SelfNameS())),
          Some(OverrideSP(abstractParamRange, RuneUsage(abstractParamCoordRune.range, AnonymousSubstructParentInterfaceTemplateRuneS()))),
          Some(RuneUsage(abstractParamCoordRune.range, SelfRuneS())),
          None))

    val newParams = originalParams.updated(abstractParamIndex, newParam)

    val newBody =
      FunctionCallSE(
        methodRange,
        DotSE(
          methodRange,
          LocalLoadSE(methodRange, SelfNameS(), UseP),
          methodIndex.toString,
          false),
        // Params minus the abstract param
        (newParams.slice(0, abstractParamIndex) ++ newParams.slice(abstractParamIndex + 1, newParams.length))
          .map(param => vassertSome(param.pattern.name).name)
          .map(name => LocalLoadSE(methodRange, name, UseP)))

    FunctionA(
      methodRange,
      name,
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
          SelfPermissionRuneS() -> PermissionTemplataType,
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
