package dev.vale.typing.macros

import dev.vale.highertyping.{FunctionA, ImplA, InterfaceA, StructA}
import dev.vale._
import dev.vale.parsing.ast.{BorrowP, FinalP, OwnP, UseP}
import dev.vale.postparsing.patterns._
import dev.vale.postparsing._
import dev.vale.postparsing.rules._
import dev.vale.typing.{OverloadResolver, TypingPassOptions}
import dev.vale.typing.citizen.StructCompiler
import dev.vale.typing.env.{FunctionEnvEntry, IEnvEntry, ImplEnvEntry, StructEnvEntry}
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.macros.citizen._
import dev.vale.typing.names._
import dev.vale.typing.types.MutabilityT
import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.patterns._
import dev.vale.typing.ast._
import dev.vale.typing.env.PackageEnvironmentT
import dev.vale.typing.function.FunctionCompilerCore
import dev.vale.typing.macros.citizen.StructDropMacro
import dev.vale.typing.names.AnonymousSubstructImplNameT
import dev.vale.typing.templata.ExternFunctionTemplataT
import dev.vale.typing.ast
import dev.vale.typing.types.CoordT

import scala.collection.immutable.List
import scala.collection.mutable

class AnonymousInterfaceMacro(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    overloadCompiler: OverloadResolver,
    structCompiler: StructCompiler,
    structConstructorMacro: StructConstructorMacro,
    structDropMacro: StructDropMacro
) extends IOnInterfaceDefinedMacro {

  val macroName: StrI = keywords.DeriveAnonymousSubstruct

//  val generatorId: String = "interfaceConstructorGenerator"

//  override def getInterfaceChildEntries(interfaceName: FullNameT[INameT], interfaceA: InterfaceA, mutability: MutabilityT): Vector[(FullNameT[INameT], IEnvEntry)] = {
//    vimpl()
//  }

  override def getInterfaceSiblingEntries(interfaceName: IdT[INameT], interfaceA: InterfaceA): Vector[(IdT[INameT], IEnvEntry)] = {
    if (interfaceA.attributes.contains(SealedS)) {
      return Vector()
    }

    val memberRunes =
      interfaceA.internalMethods.zipWithIndex.map({ case (method, index) =>
        RuneUsage(RangeS(method.range.begin, method.range.begin), AnonymousSubstructMemberRuneS(interfaceA.name, method.name))
      })
    val members =
      interfaceA.internalMethods.zip(memberRunes).zipWithIndex.map({ case ((method, rune), index) =>
        NormalStructMemberS(method.range, interner.intern(StrI(index.toString)), FinalP, rune)
      })

    val structNameS = interner.intern(AnonymousSubstructTemplateNameS(interfaceA.name))
    val structNameT = interfaceName.copy(localName = nameTranslator.translateNameStep(structNameS))
    val structA = makeStruct(interfaceA, memberRunes, members, structNameS)

    val moreEntries =
//        interfaceFreeMacro.getInterfaceSiblingEntries(interfaceName, interfaceA) ++
        structConstructorMacro.getStructSiblingEntries(structNameT, structA) ++
        structDropMacro.getStructSiblingEntries(structNameT, structA)// ++
        //structFreeMacro.getStructSiblingEntries(structNameT, structA)

    val forwarderMethods =
      interfaceA.internalMethods.zip(memberRunes).zipWithIndex.map({ case ((method, rune), methodIndex) =>
        val name = structNameT.copy(localName = nameTranslator.translateGenericFunctionName(method.name))
        (name, FunctionEnvEntry(makeForwarderFunction(structNameS, interfaceA, structA, method, methodIndex)))
      })

    val rules =
      //structA.headerRules ++
      structA.memberRules ++
      Vector(
        LookupSR(
          structA.range,
          RuneUsage(structA.range, AnonymousSubstructTemplateRuneS()),
          structA.name.getImpreciseName(interner)),
        CallSR(
          structA.range,
          RuneUsage(structA.range, AnonymousSubstructKindRuneS()),
          RuneUsage(structA.range, AnonymousSubstructTemplateRuneS()),
          structA.genericParameters.map(_.rune).toVector),
        LookupSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceTemplateRuneS()),
          interfaceA.name.getImpreciseName(interner)),
        CallSR(
          interfaceA.range,
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceKindRuneS()),
          RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceTemplateRuneS()),
          interfaceA.genericParameters.map(_.rune).toVector))
    val runeToType =
      structA.genericParameters.map(_.rune.rune)
        .map(rune => rune -> vassertSome(structA.headerRuneToType.get(rune)))
        .toMap ++
//      structA.headerRuneToType ++
      structA.membersRuneToType ++
      Vector(
        (AnonymousSubstructKindRuneS() -> KindTemplataType()),
        (AnonymousSubstructTemplateRuneS() -> structA.tyype),
        (AnonymousSubstructParentInterfaceKindRuneS() -> KindTemplataType()),
        (AnonymousSubstructParentInterfaceTemplateRuneS() -> interfaceA.tyype))
    val structKindRuneS = RuneUsage(interfaceA.range, AnonymousSubstructKindRuneS())
    val interfaceKindRuneS = RuneUsage(interfaceA.range, AnonymousSubstructParentInterfaceKindRuneS())

    val implNameS = interner.intern(AnonymousSubstructImplDeclarationNameS(interfaceA.name))
//    val implImpreciseNameS = interner.intern(ImplImpreciseNameS(RuleScout.getRuneKindTemplate(rules, structKindRuneS.rune)))

    val implA =
      ImplA(
        interfaceA.range,
        implNameS,
//        // Just getting the template name (or the kind name if not template), see INSHN.
//        implImpreciseNameS,
        structA.genericParameters,
        rules.toVector,
        runeToType,
        structKindRuneS,
        structA.name.getImpreciseName(interner),
        interfaceKindRuneS,
        interfaceA.name.getImpreciseName(interner))
    val implNameT = structNameT.copy(localName = nameTranslator.translateNameStep(implA.name))
//    val implSiblingEntries =
//      implDropMacro.getImplSiblingEntries(implNameT, implA)

    Vector[(IdT[INameT], IEnvEntry)](
      (structNameT, StructEnvEntry(structA)),
      (implNameT, ImplEnvEntry(implA))) ++
      moreEntries ++
      forwarderMethods
  }

  private def mapRunes(rule: IRulexSR, func: IRuneS => IRuneS): IRulexSR = {
    rule match {
      case LookupSR(range, RuneUsage(a, rune), name) => LookupSR(range, RuneUsage(a, func(rune)), name)
      case MaybeCoercingLookupSR(range, RuneUsage(a, rune), name) => LookupSR(range, RuneUsage(a, func(rune)), name)
      case RuneParentEnvLookupSR(range, RuneUsage(a, rune)) => RuneParentEnvLookupSR(range, RuneUsage(a, func(rune)))
      case EqualsSR(range, RuneUsage(a, left), RuneUsage(b, right)) => EqualsSR(range, RuneUsage(a, func(left)), RuneUsage(b, func(right)))
      case DefinitionCoordIsaSR(range, RuneUsage(z, result), RuneUsage(a, sub), RuneUsage(b, suuper)) => DefinitionCoordIsaSR(range, RuneUsage(z, func(result)), RuneUsage(a, func(sub)), RuneUsage(b, func(suuper)))
      case CallSiteCoordIsaSR(range, maybeResult, RuneUsage(a, sub), RuneUsage(b, suuper)) => {
        CallSiteCoordIsaSR(
          range,
          maybeResult.map({ case RuneUsage(z, result) => RuneUsage(z, func(result)) }),
          RuneUsage(a, func(sub)),
          RuneUsage(b, func(suuper)))
      }
      case KindComponentsSR(range, RuneUsage(a, resultRune), RuneUsage(b, mutabilityRune)) => KindComponentsSR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(mutabilityRune)))
      case CoordComponentsSR(range, RuneUsage(a, resultRune), RuneUsage(b, ownershipRune), RuneUsage(c, kindRune)) => CoordComponentsSR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(ownershipRune)), RuneUsage(c, func(kindRune)))
      case PrototypeComponentsSR(range, RuneUsage(a, resultRune), RuneUsage(b, paramsRune), RuneUsage(c, returnRune)) => PrototypeComponentsSR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(paramsRune)), RuneUsage(c, func(returnRune)))
      case ResolveSR(range, RuneUsage(a, resultRune), name, RuneUsage(b, paramsListRune), RuneUsage(c, returnRune)) => ResolveSR(range, RuneUsage(a, func(resultRune)), name, RuneUsage(b, func(paramsListRune)), RuneUsage(c, func(returnRune)))
      case CallSiteFuncSR(range, RuneUsage(a, resultRune), name, RuneUsage(b, paramsListRune), RuneUsage(c, returnRune)) => CallSiteFuncSR(range, RuneUsage(a, func(resultRune)), name, RuneUsage(b, func(paramsListRune)), RuneUsage(c, func(returnRune)))
      case DefinitionFuncSR(range, RuneUsage(a, resultRune), name, RuneUsage(b, paramsListRune), RuneUsage(c, returnRune)) => DefinitionFuncSR(range, RuneUsage(a, func(resultRune)), name, RuneUsage(b, func(paramsListRune)), RuneUsage(c, func(returnRune)))
      case OneOfSR(range, RuneUsage(a, rune), literals) => OneOfSR(range, RuneUsage(a, func(rune)), literals)
      case IsConcreteSR(range, RuneUsage(a, rune)) => IsConcreteSR(range, RuneUsage(a, func(rune)))
      case IsInterfaceSR(range, RuneUsage(a, rune)) => IsInterfaceSR(range, RuneUsage(a, func(rune)))
      case IsStructSR(range, RuneUsage(a, rune)) => IsStructSR(range, RuneUsage(a, func(rune)))
      case CoerceToCoordSR(range, RuneUsage(a, coordRune), RuneUsage(b, kindRune)) => CoerceToCoordSR(range, RuneUsage(a, func(coordRune)), RuneUsage(b, func(kindRune)))
      case LiteralSR(range, RuneUsage(a, rune), literal) => LiteralSR(range, RuneUsage(a, func(rune)), literal)
      case AugmentSR(range, RuneUsage(a, resultRune), ownership, RuneUsage(b, innerRune)) => AugmentSR(range, RuneUsage(a, func(resultRune)), ownership, RuneUsage(b, func(innerRune)))
      case MaybeCoercingCallSR(range, RuneUsage(a, resultRune), RuneUsage(b, templateRune), args) => MaybeCoercingCallSR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(templateRune)), args.map({ case RuneUsage(c, rune) => RuneUsage(c, func(rune)) }))
      case CallSR(range, RuneUsage(a, resultRune), RuneUsage(b, templateRune), args) => CallSR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(templateRune)), args.map({ case RuneUsage(c, rune) => RuneUsage(c, func(rune)) }))
      case PackSR(range, RuneUsage(a, resultRune), members) => PackSR(range, RuneUsage(a, resultRune), members.map({ case RuneUsage(c, rune) => RuneUsage(c, func(rune)) }))
//      case StaticSizedArraySR(range, RuneUsage(a, resultRune), RuneUsage(b, mutabilityRune), RuneUsage(c, variabilityRune), RuneUsage(d, sizeRune), RuneUsage(e, elementRune)) => StaticSizedArraySR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(mutabilityRune)), RuneUsage(c, func(variabilityRune)), RuneUsage(d, func(sizeRune)), RuneUsage(e, func(elementRune)))
//      case RuntimeSizedArraySR(range, RuneUsage(a, resultRune), RuneUsage(b, mutabilityRune), RuneUsage(c, elementRune)) => RuntimeSizedArraySR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(mutabilityRune)), RuneUsage(c, func(elementRune)))
      case RefListCompoundMutabilitySR(range, RuneUsage(a, resultRune), RuneUsage(b, coordListRune)) => RefListCompoundMutabilitySR(range, RuneUsage(a, func(resultRune)), RuneUsage(b, func(coordListRune)))
      case other => vimpl(other)
    }
  }

  private def inheritedMethodRune(interfaceA: InterfaceA, method: FunctionA, rune: IRuneS): IRuneS = {
    AnonymousSubstructMethodInheritedRuneS(interfaceA.name, method.name, rune)
  }

  private def makeStruct(interfaceA: InterfaceA, memberRunes: Vector[RuneUsage], members: Vector[NormalStructMemberS], structTemplateNameS: AnonymousSubstructTemplateNameS) = {
    def range(n: Int) = RangeS.internal(interner, n)
    def use(n: Int, rune: IRuneS) = RuneUsage(range(n), rune)

    // For this interface:
    //
    //   #!DeriveInterfaceDrop
    //   sealed interface Bork<A Ref, B Ref> {
    //     func bork(virtual self &Bork<A Ref, B Ref>, a Opt<A>) B;
    //   }
    //
    // We're trying to make a struct with a bunch of callables:
    //
    //   #!DeriveStructDrop
    //   struct IBorkForwarder<A Ref, B Ref, Lam>
    //       where func drop(Lam)void, func __call(&Lam, Opt<A>)B {
    //     lam Lam;
    //   }

    val rulesBuilder = new Accumulator[IRulexSR]()
    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()

    interfaceA.rules.foreach(x => rulesBuilder.add(x))

    runeToType ++= interfaceA.runeToType
    runeToType ++= memberRunes.map(_.rune -> CoordTemplataType())

    val voidKindRune = AnonymousSubstructVoidKindRuneS()
    runeToType.put(voidKindRune, KindTemplataType())
    rulesBuilder.add(LookupSR(range(-1672147),use(-64002, voidKindRune),interner.intern(CodeNameS(keywords.void))))
    val voidCoordRune = AnonymousSubstructVoidCoordRuneS()
    runeToType.put(voidCoordRune, CoordTemplataType())
    rulesBuilder.add(CoerceToCoordSR(range(-1672147),use(-64002, voidCoordRune),use(-64002, voidKindRune)))

    val structGenericParams =
      interfaceA.genericParameters ++
        memberRunes.map(mr => GenericParameterS(mr.range, mr, CoordGenericParameterTypeS(vregionmut(None), true, false), None))

    interfaceA.internalMethods.zip(memberRunes).zipWithIndex.foreach({ case ((internalMethod, memberRune), methodIndex) =>
      val methodRuneToType =
        internalMethod.runeToType.map({ case (methodRune, tyype) =>
          inheritedMethodRune(interfaceA, internalMethod, methodRune) -> tyype
        })
      runeToType ++= methodRuneToType
      val methodRules =
        internalMethod.rules.map(rule => mapRunes(rule, methodRune => {
          inheritedMethodRune(interfaceA, internalMethod, methodRune)
        }))
      rulesBuilder.addAll(methodRules)

      val returnRune = {
        val originalRetRune = vassertSome(internalMethod.maybeRetCoordRune)
        RuneUsage(
          originalRetRune.range,
          inheritedMethodRune(interfaceA, internalMethod, originalRetRune.rune))
      }

      // Now we make the __call bound, which involves figuring out the params and return runes and
      // assembling a call rule for it.
      {
        val selfBorrowCoordRuneS =
          AnonymousSubstructMethodSelfBorrowCoordRuneS(interfaceA.name, internalMethod.name)
        runeToType += selfBorrowCoordRuneS -> CoordTemplataType()
        rulesBuilder.add(
          AugmentSR(internalMethod.range, RuneUsage(internalMethod.range, selfBorrowCoordRuneS), Some(BorrowP), memberRune))

        val paramRunes =
          internalMethod.params.map({
            case ParameterS(_, None, _, AtomSP(range, name, coordRune, destructure)) => {
              RuneUsage(range, inheritedMethodRune(interfaceA, internalMethod, vassertSome(coordRune).rune))
            }
            case ParameterS(_, Some(_), _, AtomSP(range, name, coordRune, destructure)) => {
              RuneUsage(range, selfBorrowCoordRuneS)
            }
          })
        val methodParamsListRune =
          RuneUsage(internalMethod.range, AnonymousSubstructFunctionBoundParamsListRuneS(interfaceA.name, internalMethod.name))
        rulesBuilder.add(PackSR(internalMethod.range, methodParamsListRune, paramRunes.toVector))
        runeToType.put(methodParamsListRune.rune, PackTemplataType(CoordTemplataType()))

        // the struct runes are guaranteed to line up with the interface runes...
        // but not necessarily this function's runes.
        // we need to grab the owner

        // Let's say we had a:
        //
        //   func bork<X Ref, Y Ref>(virtual self &IBork<X, Y>, Opt<X>) Y;
        //
        // our bound will probably look like:
        //
        //   func __call(&Lam, Opt<A>)B
        //
        // we need to make a IBork<B, A> = IBork<X, Y> to connect those two worlds of runes.
        val interfaceParam =
          vassertOne(internalMethod.params.filter(_.virtuality.nonEmpty))
        val originalInterfaceCoordRune = vassertSome(interfaceParam.pattern.coordRune).rune
        val interfaceCoordRune =
          RuneUsage(interfaceParam.range, inheritedMethodRune(interfaceA, internalMethod, vassertSome(interfaceParam.pattern.coordRune).rune))
        runeToType.put(interfaceCoordRune.rune, CoordTemplataType())

        val methodInterfaceCoordRune =
          RuneUsage(
            interfaceParam.range,
            inheritedMethodRune(interfaceA, internalMethod,
              vassertOne(
                internalMethod.rules.collect({
                  case AugmentSR(_, resultRune, ownership, innerRune) if resultRune.rune == originalInterfaceCoordRune => {
                    innerRune.rune
                  }
                }))))

//        val methodInterfaceKindRune =
//          RuneUsage(interfaceParam.range, AnonymousSubstructFunctionInterfaceKindRune(interfaceA.name, internalMethod.name))
//        runeToType.put(methodInterfaceKindRune.rune, KindTemplataType())

//        val methodInterfaceOwnershipRune =
//          RuneUsage(interfaceParam.range, AnonymousSubstructFunctionInterfaceOwnershipRune(interfaceA.name, internalMethod.name))
//        runeToType.put(methodInterfaceOwnershipRune.rune, OwnershipTemplataType())

        val methodInterfaceTemplateRune =
          RuneUsage(interfaceParam.range, AnonymousSubstructFunctionInterfaceTemplateRune(interfaceA.name, internalMethod.name))
        runeToType.put(methodInterfaceTemplateRune.rune, interfaceA.tyype)

        val methodInterfaceKindRune =
          RuneUsage(interfaceParam.range, AnonymousSubstructFunctionInterfaceKindRune(interfaceA.name, internalMethod.name))
        runeToType.put(methodInterfaceKindRune.rune, KindTemplataType())

        rulesBuilder.add(
          LookupSR(interfaceParam.range, methodInterfaceTemplateRune, interfaceA.name.getImpreciseName(interner)))
//        rulesBuilder.add(
//          CoordComponentsSR(interfaceParam.range, interfaceCoordRune, methodInterfaceOwnershipRune, methodInterfaceKindRune))
        rulesBuilder.add(
          CallSR(interfaceParam.range, methodInterfaceKindRune, methodInterfaceTemplateRune, interfaceA.genericParameters.map(_.rune).toVector))
        rulesBuilder.add(
          CoerceToCoordSR(interfaceParam.range, methodInterfaceCoordRune, methodInterfaceKindRune))

        val methodPrototypeRune =
          RuneUsage(
            internalMethod.range,
            AnonymousSubstructFunctionBoundPrototypeRuneS(interfaceA.name, internalMethod.name))
        rulesBuilder.add(
          DefinitionFuncSR(
            internalMethod.range, methodPrototypeRune, keywords.underscoresCall, methodParamsListRune, returnRune))
        rulesBuilder.add(
          CallSiteFuncSR(
            internalMethod.range, methodPrototypeRune, keywords.underscoresCall, methodParamsListRune, returnRune))
        rulesBuilder.add(
          ResolveSR(
            internalMethod.range, methodPrototypeRune, keywords.underscoresCall, methodParamsListRune, returnRune))
        runeToType.put(methodPrototypeRune.rune, PrototypeTemplataType())
      }

      // Now we make the drop bound, which involves figuring out the params and return runes and
      // assembling a call rule for it.
      {
        val selfOwnCoordRuneS =
          AnonymousSubstructMethodSelfOwnCoordRuneS(interfaceA.name, internalMethod.name)
        runeToType += selfOwnCoordRuneS -> CoordTemplataType()
        rulesBuilder.add(
          AugmentSR(internalMethod.range, RuneUsage(internalMethod.range, selfOwnCoordRuneS), Some(OwnP), memberRune))

        val dropParamsListRune =
          RuneUsage(internalMethod.range, AnonymousSubstructDropBoundParamsListRuneS(interfaceA.name, internalMethod.name))
        rulesBuilder.add(
          PackSR(
            internalMethod.range,
            dropParamsListRune,
            Vector(RuneUsage(internalMethod.range, selfOwnCoordRuneS))))
        runeToType.put(dropParamsListRune.rune, PackTemplataType(CoordTemplataType()))

        val dropPrototypeRune =
          RuneUsage(
            internalMethod.range,
            AnonymousSubstructDropBoundPrototypeRuneS(interfaceA.name, internalMethod.name))
        rulesBuilder.add(
          DefinitionFuncSR(
            internalMethod.range, dropPrototypeRune, keywords.drop, dropParamsListRune, RuneUsage(internalMethod.range, voidCoordRune)))
        rulesBuilder.add(
          CallSiteFuncSR(
            internalMethod.range, dropPrototypeRune, keywords.drop, dropParamsListRune, RuneUsage(internalMethod.range, voidCoordRune)))
        rulesBuilder.add(
          ResolveSR(
            internalMethod.range, dropPrototypeRune, keywords.drop, dropParamsListRune, RuneUsage(internalMethod.range, voidCoordRune)))
        runeToType.put(dropPrototypeRune.rune, PrototypeTemplataType())
      }
    })

    StructA(
      interfaceA.range,
      structTemplateNameS,
      Vector(),
      false,
      interfaceA.mutabilityRune,
      interfaceA.maybePredictedMutability,
      TemplateTemplataType(
        interfaceA.tyype.paramTypes ++ memberRunes.map(_ => CoordTemplataType()),
        KindTemplataType()),
      structGenericParams,
      runeToType.toMap,
      rulesBuilder.buildArray(),
      Map(),
      Vector(),
      members)
  }

  private def makeForwarderFunction(
    structNameS: AnonymousSubstructTemplateNameS,
    interface: InterfaceA,
    struct: StructA,
    method: FunctionA,
    methodIndex: Int):
  FunctionA = {
    val structType = struct.tyype
    val FunctionA(methodRange, name, attributes, methodOriginalType, methodOriginalIdentifyingRunes, methodOriginalRuneToType, originalParams, maybeRetCoordRune, methodOriginalRules, body) = method

    vassert(struct.genericParameters.map(_.rune).startsWith(methodOriginalIdentifyingRunes.map(_.rune)))
    val genericParams = struct.genericParameters

    val runeToType = mutable.HashMap[IRuneS, ITemplataType]()
    runeToType ++= struct.headerRuneToType
    runeToType ++= struct.membersRuneToType

    val selfOwnershipRune = SelfOwnershipRuneS()
    runeToType.put(selfOwnershipRune, OwnershipTemplataType())
    val interfaceRune = AnonymousSubstructParentInterfaceTemplateRuneS()
    runeToType.put(interfaceRune, KindTemplataType())
    val selfKindRune = SelfKindRuneS()
    runeToType.put(selfKindRune, KindTemplataType())
    val selfCoordRune = SelfRuneS()
    runeToType.put(selfCoordRune, CoordTemplataType())
    val selfKindTemplateRune = SelfKindTemplateRuneS()
    runeToType.put(selfKindTemplateRune, structType)

    val rules = new Accumulator[IRulexSR]()
//    rules.addAll(methodOriginalRules)
    rules.addAll(struct.headerRules.toIterable)
    rules.addAll(struct.memberRules.toIterable)

    val abstractParamIndex =
      originalParams.indexWhere(param => {
        param.virtuality match {
          case Some(AbstractSP(_, _)) => true
          case _ => false
        }
      })
    vassert(abstractParamIndex >= 0)
    val abstractParam = originalParams(abstractParamIndex)
    val abstractParamRange = abstractParam.pattern.range
    val abstractParamCoordRune =
      RuneUsage(
        abstractParamRange,
        inheritedMethodRune(interface, method, vassertSome(abstractParam.pattern.coordRune).rune)) // https://github.com/ValeLang/Vale/issues/370

    val destructuringInterfaceRule =
      CoordComponentsSR(
        abstractParamRange,
        abstractParamCoordRune,
        RuneUsage(abstractParamRange, selfOwnershipRune),
        RuneUsage(abstractParamRange, interfaceRune))

    rules.add(destructuringInterfaceRule)
    val lookupStructTemplateRule =
      LookupSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, selfKindTemplateRune),
        interner.intern(AnonymousSubstructTemplateImpreciseNameS(structNameS.interfaceName.getImpreciseName(interner))))
    rules.add(lookupStructTemplateRule)
    val lookupStructRule =
      CallSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, selfKindRune),
        RuneUsage(abstractParamRange, selfKindTemplateRune),
        genericParams.map(_.rune).toVector)
    rules.add(lookupStructRule)

    val assemblingStructRule =
      CoordComponentsSR(
        abstractParamRange,
        RuneUsage(abstractParamRange, selfCoordRune),
        RuneUsage(abstractParamRange, selfOwnershipRune),
        RuneUsage(abstractParamRange, selfKindRune))
    rules.add(assemblingStructRule)

    val newParams =
      originalParams.map({
        case ParameterS(_, Some(_), _, AtomSP(_, _, Some(_), _)) => {
          ParameterS(
            abstractParamRange,
            None, //Some(OverrideSP(abstractParamRange, RuneUsage(abstractParamCoordRune.range, AnonymousSubstructParentInterfaceTemplateRuneS()))),
            false,
            AtomSP(
              abstractParamRange,
              Some(CaptureS(interner.intern(SelfNameS()), false)),
              Some(RuneUsage(abstractParamCoordRune.range, selfCoordRune)),
              None))
        }
        case p @ ParameterS(_, None, _, a @ AtomSP(_, _, Some(RuneUsage(runeRange, oldRune)), _)) => {
          val rune = RuneUsage(runeRange, inheritedMethodRune(interface, method, oldRune))
          p.copy(pattern = a.copy(coordRune = Some(rune)))
        }
      })

    val newBody =
      FunctionCallSE(
        methodRange,
        vregionmut(LocationInDenizen(Vector())),
        DotSE(
          methodRange,
          LocalLoadSE(methodRange, interner.intern(SelfNameS()), UseP),
          interner.intern(StrI(methodIndex.toString)),
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
        methodOriginalType.paramTypes ++ struct.genericParameters.map(_ => CoordTemplataType()),
        FunctionTemplataType()),
      genericParams,
      runeToType.toMap,
      newParams,
      maybeRetCoordRune.map({ case RuneUsage(range, retCoordRune) =>
        RuneUsage(range, inheritedMethodRune(interface, method, retCoordRune))
      }),
      rules.buildArray().toVector,
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
