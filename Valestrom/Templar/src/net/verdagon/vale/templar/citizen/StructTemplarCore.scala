package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.{FinalP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionResult, ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, FunctionTemplarMiddleLayer, FunctionTemplarOrdinaryOrTemplatedLayer}
import net.verdagon.vale._
import net.verdagon.vale.templar.expression.CallTemplar

import scala.collection.immutable.List

class StructTemplarCore(
    opts: TemplarOptions,
    profiler: IProfiler,
    newTemplataStore: () => TemplatasStore,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  def addBuiltInStructs(env: PackageEnvironment[INameT], temputs: Temputs): Unit = {
    val emptyTupleFullName = Program2.emptyTupleStructRef.fullName
    val emptyTupleEnv = PackageEnvironment(Some(env), emptyTupleFullName, newTemplataStore())
    val structDefT = StructDefinitionT(emptyTupleFullName, List(), false, ImmutableT, List.empty, false)
    temputs.declareStruct(structDefT.getRef)
    temputs.declareStructMutability(structDefT.getRef, ImmutableT)
    temputs.declareStructEnv(structDefT.getRef, emptyTupleEnv)
    temputs.add(structDefT)
    // Normally after adding a struct we would add its destructor. Void is the only one we don't
    // have a destructor for.

    temputs.declarePack(List.empty, structDefT.getRef)
  }

  def makeStruct(
    // The environment that the struct was defined in.
    structRunesEnv: PackageEnvironment[INameT],
    temputs: Temputs,
    struct1: StructA,
    coercedFinalTemplateArgs: List[ITemplata]):
  (StructDefinitionT) = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = struct1.name
    val fullName = structRunesEnv.fullName.addStep(CitizenNameT(humanName, coercedFinalTemplateArgs))
    val temporaryStructRef = StructRefT(fullName)

    val structInnerEnv =
      PackageEnvironment(
        Some(structRunesEnv),
        fullName,
        newTemplataStore())
    // when we have structs that contain functions, add this back in
//        struct1.members
//          .map(_.origin)
//          .map(FunctionEnvEntry)
//          .groupBy(_.function.name))


      temputs
        .declareStructEnv(
          temporaryStructRef,
          structInnerEnv)

    val members = makeStructMembers(structInnerEnv, temputs, struct1.members)

    val mutability =
      structInnerEnv.getNearestTemplataWithAbsoluteName2(
        NameTranslator.translateRune(struct1.mutabilityRune),
        Set(TemplataLookupContext)) match {
        case Some(MutabilityTemplata(m)) => m
        case Some(_) => vwat()
        case None => vwat()
      }

    if (mutability == ImmutableT) {
      members.zipWithIndex.foreach({ case (member, index) =>
      if (member.variability == VaryingT) {
          throw CompileErrorExceptionT(
            ImmStructCantHaveVaryingMember(
              struct1.members(index).range,
              struct1.name,
              struct1.members(index).name))
        }
      })
    }

    val structDefT =
      StructDefinitionT(
        fullName,
        translateCitizenAttributes(struct1.attributes),
        struct1.weakable,
        mutability,
        members,
        false)

    temputs.add(structDefT);

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == ImmutableT) {
      temputs.addDestructor(
        structDefT.getRef,
        delegate.makeImmConcreteDestructor(temputs, structInnerEnv, structDefT.getRef))
    }

    profiler.childFrame("struct ancestor interfaces", () => {
      val implementedInterfaceRefs2 =
        ancestorHelper.getParentInterfaces(temputs, structDefT.getRef);

      implementedInterfaceRefs2.foreach({
        case (implementedInterfaceRefT) => {
          structDefT.mutability match {
            case MutableT => {
              val sefResult =
                delegate.scoutExpectedFunctionForPrototype(
                  structInnerEnv,
                  temputs,
                  struct1.range,
                  GlobalFunctionFamilyNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME),
                  List.empty,
                  List(ParamFilter(CoordT(OwnT,ReadwriteT, structDefT.getRef), Some(OverrideT(implementedInterfaceRefT)))),
                  List.empty,
                  true)
              sefResult match {
                case ScoutExpectedFunctionSuccess(_) =>
                case ScoutExpectedFunctionFailure(_, _, _, _, _) => {
                  throw CompileErrorExceptionT(RangedInternalErrorT(struct1.range, sefResult.toString))
                }
              }
            }
            case ImmutableT => {
              // If it's immutable, make sure there's a zero-arg destructor.
              delegate.getImmInterfaceDestructorOverride(temputs, structInnerEnv, structDefT.getRef, implementedInterfaceRefT)
            }
          }
        }
      })

      val ancestorInterfaces =
        ancestorHelper.getAncestorInterfaces(temputs, temporaryStructRef)

      ancestorInterfaces.foreach({
        case (ancestorInterface) => {
          val interfaceDefinition2 = temputs.lookupInterface(ancestorInterface)
          if (structDefT.weakable != interfaceDefinition2.weakable) {
            throw WeakableImplingMismatch(structDefT.weakable, interfaceDefinition2.weakable)
          }
          temputs.addImpl(temporaryStructRef, ancestorInterface)
        }
      })

      structDefT
    })
  }

  def translateCitizenAttributes(attrs: List[ICitizenAttributeA]): List[ICitizenAttribute2] = {
    attrs.map({
      case ExportA(packageCoord) => Export2(packageCoord)
      case x => vimpl(x.toString)
    })
  }

  // Takes a IEnvironment because we might be inside a:
  // struct<T> Thing<T> {
  //   t: T;
  // }
  // which means we need some way to know what T is.
  def makeInterface(
    interfaceRunesEnv: PackageEnvironment[INameT],
    temputs: Temputs,
    interfaceA: InterfaceA,
    coercedFinalTemplateArgs2: List[ITemplata]):
  (InterfaceDefinitionT) = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = interfaceA.name
    val fullName = interfaceRunesEnv.fullName.addStep(CitizenNameT(humanName, coercedFinalTemplateArgs2))
    val temporaryInferfaceRef = InterfaceRefT(fullName)

    val interfaceInnerEnv0 =
      PackageEnvironment(
        Some(interfaceRunesEnv),
        fullName,
        newTemplataStore())
    val interfaceInnerEnv1 =
      interfaceInnerEnv0.addEntries(
        opts.useOptimization,
        interfaceA.identifyingRunes.zip(coercedFinalTemplateArgs2)
          .map({ case (rune, templata) => (NameTranslator.translateRune(rune), List(TemplataEnvEntry(templata))) })
          .toMap)
    val interfaceInnerEnv2 =
      interfaceInnerEnv1.addEntries(
        opts.useOptimization,
        interfaceA.internalMethods
          .map(internalMethod => {
            val functionName = NameTranslator.translateFunctionNameToTemplateName(internalMethod.name)
            (functionName -> List(FunctionEnvEntry(internalMethod)))
          })
          .toMap[INameT, List[IEnvEntry]])
    val interfaceInnerEnv = interfaceInnerEnv2

    temputs
      .declareInterfaceEnv(
        temporaryInferfaceRef,
        interfaceInnerEnv)

    val internalMethods2 =
      interfaceA.internalMethods.map(internalMethod => {
        delegate.evaluateOrdinaryFunctionFromNonCallForHeader(
          temputs,
          internalMethod.range,
          FunctionTemplata(
            interfaceInnerEnv,
            internalMethod))
      })

    val mutability =
      interfaceInnerEnv.getNearestTemplataWithAbsoluteName2(
        NameTranslator.translateRune(interfaceA.mutabilityRune),
        Set(TemplataLookupContext)) match {
        case Some(MutabilityTemplata(m)) => m
        case Some(_) => vwat()
        case None => vwat()
      }

    val interfaceDef2 =
      InterfaceDefinitionT(
        fullName,
        translateCitizenAttributes(interfaceA.attributes),
        interfaceA.weakable,
        mutability,
        internalMethods2)
    temputs.add(interfaceDef2)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == ImmutableT) {
      temputs.addDestructor(
        interfaceDef2.getRef,
        delegate.getImmInterfaceDestructor(temputs, interfaceInnerEnv, interfaceDef2.getRef))
    }

    profiler.childFrame("interface ancestor interfaces", () => {
      val _ = ancestorHelper.getParentInterfaces(temputs, temporaryInferfaceRef)

      //
      //      interface1.internalMethods.foldLeft(temputs)({
      //        case (ntvFunction1) => {
      //          if (ntvFunction1.isTemplate) {
      //            // Do nothing, can't evaluate it now
      //            temputs
      //          } else {
      //            FunctionTemplar.evaluateOrdinaryLightFunctionFromNonCallForTemputs(
      //              temputs,
      //              FunctionTemplata(interfaceInnerEnv, ntvFunction1))
      //          }
      //        }
      //      })
    })

    (interfaceDef2)
  }

  private def makeStructMembers(env: IEnvironment, temputs: Temputs, members: List[StructMemberA]): (List[StructMemberT]) = {
    members match {
      case Nil => (Nil)
      case head1 :: tail1 => {
        val head2 = makeStructMember(env, temputs, head1);
        val tail2 = makeStructMembers(env, temputs, tail1);
        (head2 :: tail2)
      }
    }
  }

  private def makeStructMember(
    env: IEnvironment,
    temputs: Temputs,
    member: StructMemberA):
  (StructMemberT) = {
    val CoordTemplata(coord) = vassertSome(env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(member.typeRune), Set(TemplataLookupContext)))
    (StructMemberT(CodeVarNameT(member.name), Conversions.evaluateVariability(member.variability), ReferenceMemberTypeT(coord)))
  }

//  // Makes a functor for the given prototype.
//  def functionToLambda(
//    outerEnv: IEnvironment,
//    temputs: Temputs,
//    header: FunctionHeader2):
//  structRefT = {
//    val mutability = Immutable
//
//    val nearName = FunctionScout.CLOSURE_STRUCT_NAME // For example "__Closure<main>:lam1"
//    val fullName = FullName2(header.fullName.steps :+ NamePart2(nearName, Some(List.empty), None, None))
//
//    val structRef = structRefT(fullName)
//
//    // We declare the function into the environment that we use to compile the
//    // struct, so that those who use the struct can reach into its environment
//    // and see the function and use it.
//    // See CSFMSEO and SAFHE.
//    val structEnv =
//      PackageEnvironment(
//        Some(outerEnv),
//        fullName,
//        Map(
//          CallTemplar.CALL_FUNCTION_NAME -> List(TemplataEnvEntry(ExternFunctionTemplata(header))),
//          nearName -> List(TemplataEnvEntry(KindTemplata(structRef))),
//          FunctionScout.CLOSURE_STRUCT_ENV_ENTRY_NAME -> List(TemplataEnvEntry(KindTemplata(structRef)))))
//
//    temputs.declareStruct(structRef);
//    temputs.declareStructMutability(structRef, mutability)
//    temputs.declareStructEnv(structRef, structEnv);
//
//    val closureStructDefinition = StructDefinition2(fullName, mutability, List.empty, true);
//    temputs.add(closureStructDefinition)
//
//    val closuredVarsStructRef = closureStructDefinition.getRef;
//
//    closuredVarsStructRef
//  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: LambdaNameA,
    functionA: FunctionA,
    members: List[StructMemberT]):
  (StructRefT, MutabilityT, FunctionTemplata) = {
    val isMutable =
      members.exists({ case StructMemberT(name, variability, tyype) =>
        if (variability == VaryingT) {
          true
        } else {
          tyype match {
            case AddressMemberTypeT(reference) => true
            case ReferenceMemberTypeT(reference) => {
              reference.ownership match {
                case OwnT | ConstraintT | WeakT => true
                case ShareT => false
              }
            }
          }
        }
      })
    val mutability = if (isMutable) MutableT else ImmutableT

    val nearName = LambdaCitizenNameT(NameTranslator.translateCodeLocation(name.codeLocation))
    val fullName = containingFunctionEnv.fullName.addStep(nearName)

    val structRef = StructRefT(fullName)

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val structEnv =
      PackageEnvironment(
        Some(containingFunctionEnv),
        fullName,
        newTemplataStore()
          .addEntries(
            opts.useOptimization,
            Map(
              FunctionTemplateNameT(CallTemplar.CALL_FUNCTION_NAME, CodeLocationT.internal(-14)) -> List(FunctionEnvEntry(functionA)),
              nearName -> List(TemplataEnvEntry(KindTemplata(structRef))),
              ClosureParamNameT() -> List(TemplataEnvEntry(KindTemplata(structRef))))))
    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata =
        FunctionTemplata(
          structEnv,
          functionA)

    temputs.declareStruct(structRef);
    temputs.declareStructMutability(structRef, mutability)
    temputs.declareStructEnv(structRef, structEnv);

    val closureStructDefinition = StructDefinitionT(fullName, List.empty, false, mutability, members, true);
    temputs.add(closureStructDefinition)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == ImmutableT) {
      temputs.addDestructor(
        closureStructDefinition.getRef,
        delegate.getImmConcreteDestructor(temputs, structEnv, closureStructDefinition.getRef))
    }

    val closuredVarsStructRef = closureStructDefinition.getRef;

    (closuredVarsStructRef, mutability, functionTemplata)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnderstruct(
    outerEnv: PackageEnvironment[INameT],
    temputs: Temputs,
    memberCoords: List[CoordT],
    name: ICitizenNameT):
  (StructRefT, MutabilityT) = {
    temputs.getPackType(memberCoords) match {
      case Some(structRefT) => return (structRefT, temputs.lookupStruct(structRefT).mutability)
      case None =>
    }
    val packMutability = StructTemplar.getCompoundTypeMutability(memberCoords)
    val members =
      memberCoords.zipWithIndex.map({
        case (pointerType, index) => StructMemberT(CodeVarNameT(index.toString), FinalT, ReferenceMemberTypeT(pointerType))
      })

    val fullName = outerEnv.fullName.addStep(TupleNameT(memberCoords))
    val structInnerEnv =
      PackageEnvironment(
        Some(outerEnv),
        fullName,
        newTemplataStore())

    val newStructDef = StructDefinitionT(structInnerEnv.fullName, List.empty, false, packMutability, members, false);
    if (memberCoords.isEmpty && packMutability != ImmutableT)
      vfail("curiosity")

    temputs.declareStruct(newStructDef.getRef);
    temputs.declareStructMutability(newStructDef.getRef, packMutability)
    temputs.declareStructEnv(newStructDef.getRef, structInnerEnv);
    temputs.add(newStructDef)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (packMutability == ImmutableT) {
      temputs.addDestructor(
        newStructDef.getRef,
        delegate.getImmConcreteDestructor(temputs, structInnerEnv, newStructDef.getRef))
    }

    temputs.declarePack(memberCoords, newStructDef.getRef);

    (newStructDef.getRef, packMutability)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  // This doesnt make a constructor. We could add that if we wanted to.
  def makeAnonymousSubstruct(
      interfaceEnv: IEnvironment,
      temputs: Temputs,
    range: RangeS,
      anonymousSubstructName: FullNameT[AnonymousSubstructNameT],
      interfaceRef: InterfaceRefT):
  (StructRefT, MutabilityT) = {
    val callables = anonymousSubstructName.last.callables

    val interfaceDef = temputs.lookupInterface(interfaceRef)

    // We don't do:
    //   val mutability = getCompoundTypeMutability(temputs, callables)
    // because we want the mutability of the receiving interface. For example,
    // we want to be able to do something like:
    //   f = IFunction1<mut, Int, Int>({_})
    // which wouldnt work if we just did the compound mutability of the closureds
    // (which is imm in this case).
    val mutability = temputs.lookupMutability(interfaceRef)

    // Dont want any mutables in our immutable interface's substruct
    if (mutability == ImmutableT) {
      if (StructTemplar.getCompoundTypeMutability(callables) == MutableT) {
        throw CompileErrorExceptionT(RangedInternalErrorT(range, "Trying to make a mutable anonymous substruct of an immutable interface!"))
      }
    }

    val structRef = StructRefT(anonymousSubstructName)

    val forwarderFunctionHeaders =
      interfaceDef.internalMethods.zipWithIndex.map({
        case (FunctionHeaderT(superFunctionName, _, superParams, superReturnType, _), index) => {
          val params =
            superParams.map({
              case ParameterT(name, Some(AbstractT$), CoordT(ownership, permission, ir)) => {
                vassert(ir == interfaceRef)
                ParameterT(name, Some(OverrideT(interfaceRef)), CoordT(ownership, permission, structRef))
              }
              case otherParam => otherParam
            })

          val FunctionNameT(humanName, _, _) = superFunctionName.last
          val fowarderName =
            anonymousSubstructName.addStep(FunctionNameT(humanName, List.empty, params.map(_.tyype)))
          val forwarderHeader =
            FunctionHeaderT(
              fowarderName,
              List.empty,
              params,
              superReturnType,
              None)

          temputs.declareFunctionSignature(range, forwarderHeader.toSignature, None)
          forwarderHeader
        }
      })

    val structInnerEnvEntries =
      forwarderFunctionHeaders
        .map(header => {
          (header.fullName.last -> TemplataEnvEntry(ExternFunctionTemplata(header)))
        })
        .groupBy(_._1)
        .mapValues(_.map(_._2))
        .toMap ++
      Map(
        ImplDeclareNameT(NameTranslator.getImplNameForName(opts.useOptimization, interfaceRef).get.subCitizenHumanName, CodeLocationT.internal(-15)) -> List(TemplataEnvEntry(ExternImplTemplata(structRef, interfaceRef))),
        // This is used later by the interface constructor generator to know what interface to impl.
        AnonymousSubstructParentInterfaceRuneT() -> List(TemplataEnvEntry(KindTemplata(interfaceRef))),
        AnonymousSubstructImplNameT() -> List(TemplataEnvEntry(ExternImplTemplata(structRef, interfaceRef))))
    val structInnerEnv =
      PackageEnvironment(
        Some(interfaceEnv),
        anonymousSubstructName,
        newTemplataStore().addEntries(opts.useOptimization, structInnerEnvEntries))


    temputs.addImpl(structRef, interfaceRef)

    temputs.declareStruct(structRef)
    temputs.declareStructMutability(structRef, mutability)
    temputs.declareStructEnv(structRef, structInnerEnv)

    vassert(interfaceDef.internalMethods.size == callables.size)

    val structDef =
      StructDefinitionT(
        anonymousSubstructName,
        List.empty,
        interfaceDef.weakable,
        mutability,
        callables.zipWithIndex.map({ case (lambda, index) =>
          StructMemberT(AnonymousSubstructMemberNameT(index), FinalT, ReferenceMemberTypeT(lambda))
        }),
        false)
    temputs.add(structDef)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == ImmutableT) {
      temputs.addDestructor(
        structDef.getRef,
        delegate.getImmConcreteDestructor(temputs, structInnerEnv, structDef.getRef))
    }

    forwarderFunctionHeaders.zip(callables).zipWithIndex.foreach({
      case ((forwarderHeader, lambda), methodIndex) => {
        val localVariables =
          forwarderHeader.params.map(param => {
            ReferenceLocalVariableT(forwarderHeader.fullName.addStep(param.name), FinalT, param.tyype)
          })

        // The args for the call inside the forwarding function.
        val lambdaCoord = CoordT(if (lambda.ownership == ShareT) ShareT else ConstraintT, lambda.permission, lambda.kind)
        val forwardedCallArgs = (lambdaCoord :: forwarderHeader.paramTypes.tail).map(ParamFilter(_, None))

//        start here
        // since IFunction has a drop() method, its looking for a drop() for the
        // lambda we gave it. but its immutable, so it needs no drop... or wait,
        // maybe imms have drops?

        val lambdaFunctionPrototype =
          delegate.scoutExpectedFunctionForPrototype(
            interfaceEnv, // Shouldnt matter here, because the callables themselves should have a __call
            temputs,
            range,
            GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME),
            List.empty,
            forwardedCallArgs,
            List.empty,
            true) match {
            case seff@ScoutExpectedFunctionFailure(_, _, _, _, _) => throw CompileErrorExceptionT(RangedInternalErrorT(range, seff.toString))
            case ScoutExpectedFunctionSuccess(prototype) => prototype
          }

        val structParamCoord =
          CoordT(
            if (structDef.mutability == ImmutableT) ShareT else ConstraintT,
            forwarderHeader.paramTypes.head.permission,
            structDef.getRef)
        val methodCoord = structDef.members(methodIndex).tyype.reference
        val loadSelfResultPermission = Templar.intersectPermission(methodCoord.permission, structParamCoord.permission)
//        val loadSelfResultCoord = methodCoord.copy(permission = loadSelfResultPermission)

        val loadedThisObjOwnership = if (methodCoord.ownership == ShareT) ShareT else ConstraintT
        val loadedThisObjPermission = if (methodCoord.ownership == ShareT) ReadonlyT else ReadwriteT
        val argExpressions =
          SoftLoadTE(
            ReferenceMemberLookupTE(
              range,
              ArgLookupTE(0, structParamCoord),
              structDef.fullName.addStep(structDef.members(methodIndex).name),
              methodCoord,
              loadSelfResultPermission,
              FinalT),
            loadedThisObjOwnership,
            loadedThisObjPermission) ::
          forwarderHeader.params.tail.zipWithIndex.map({ case (param, index) =>
            ArgLookupTE(index + 1, param.tyype)
          })

        if (lambdaFunctionPrototype.returnType.kind != NeverT() &&
          forwarderHeader.returnType != lambdaFunctionPrototype.returnType) {
          throw CompileErrorExceptionT(LambdaReturnDoesntMatchInterfaceConstructor(range))
        }

        val forwarderFunction =
          FunctionT(
            forwarderHeader,
            localVariables,
            BlockTE(
                ReturnTE(
                  FunctionCallTE(lambdaFunctionPrototype, argExpressions))))
        temputs.addFunction(forwarderFunction)
      }
    })

    (structRef, mutability)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    prototype: PrototypeT,
    structFullName: FullNameT[ICitizenNameT]):
  StructRefT = {
    val structRef = StructRefT(structFullName)

    temputs.declareStruct(structRef)
    temputs.declareStructMutability(structRef, ImmutableT)

    val forwarderParams =
      ParameterT(
        TemplarTemporaryVarNameT(-1),
        None,
        CoordT(
          ShareT,
          ReadonlyT,
          structRef)) ::
      prototype.paramTypes.zipWithIndex.map({ case (paramType, index) =>
        ParameterT(TemplarTemporaryVarNameT(index), None, paramType)
      })
    val forwarderHeader =
      FunctionHeaderT(
        structFullName.addStep(FunctionNameT(CallTemplar.CALL_FUNCTION_NAME, List.empty, forwarderParams.map(_.tyype))),
        List.empty,
        forwarderParams,
        prototype.returnType,
        None)
    temputs.declareFunctionSignature(range, forwarderHeader.toSignature, None)

    val structInnerEnv =
      PackageEnvironment(
        Some(outerEnv),
        structFullName,
        newTemplataStore().addEntries(
          opts.useOptimization,
          Map(forwarderHeader.fullName.last -> List(TemplataEnvEntry(ExternFunctionTemplata(forwarderHeader))))))
    temputs.declareStructEnv(structRef, structInnerEnv)

    val structDef =
      StructDefinitionT(
        structFullName,
        List.empty,
        false,
        ImmutableT,
        List.empty,
        false)
    temputs.add(structDef)

    // If it's immutable, make sure there's a zero-arg destructor.
//    if (mutability == Immutable) {
    temputs.addDestructor(
      structDef.getRef,
      delegate.getImmConcreteDestructor(temputs, structInnerEnv, structDef.getRef))
//    }

    val forwarderFunction =
      FunctionT(
        forwarderHeader,
        List.empty,
        BlockTE(
          Templar.consecutive(
            List(
              DiscardTE(ArgLookupTE(0, CoordT(ShareT, ReadonlyT, structRef))),
              ReturnTE(
                FunctionCallTE(
                  prototype,
                  forwarderHeader.params.tail.zipWithIndex.map({ case (param, index) =>
                    ArgLookupTE(index + 1, param.tyype)
                  })))))))
    temputs.addFunction(forwarderFunction)

    structRef
  }

  def makeStructConstructor(
    temputs: Temputs,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinitionT,
    constructorFullName: FullNameT[IFunctionNameT]):
  FunctionHeaderT = {
    vassert(constructorFullName.last.parameters.size == structDef.members.size)
    val constructorParams =
      structDef.members.map({
        case StructMemberT(name, _, ReferenceMemberTypeT(reference)) => {
          ParameterT(name, None, reference)
        }
      })
    val constructorReturnOwnership = if (structDef.mutability == MutableT) OwnT else ShareT
    val constructorReturnPermission = if (structDef.mutability == MutableT) ReadwriteT else ReadonlyT
    val constructorReturnType = CoordT(constructorReturnOwnership, constructorReturnPermission, structDef.getRef)
    // not virtual because how could a constructor be virtual
    val constructor2 =
      FunctionT(
        FunctionHeaderT(
          constructorFullName,
          List.empty,
          constructorParams,
          constructorReturnType,
          maybeConstructorOriginFunctionA),
        List.empty,
        BlockTE(
            ReturnTE(
              ConstructTE(
                structDef.getRef,
                constructorReturnType,
                constructorParams.zipWithIndex.map({ case (p, index) => ArgLookupTE(index, p.tyype) })))))

    // we cant make the destructor here because they might have a user defined one somewhere
    temputs.declareFunctionReturnType(constructor2.header.toSignature, constructor2.header.returnType)
    temputs.addFunction(constructor2);

    vassert(
      temputs.getDeclaredSignatureOrigin(
        constructor2.header.fullName).nonEmpty)

    (constructor2.header)
  }
}
