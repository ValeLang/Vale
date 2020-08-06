package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.parser.{FinalP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.{DestructorTemplar, FunctionTemplar, FunctionTemplarCore, FunctionTemplarMiddleLayer, FunctionTemplarOrdinaryOrTemplatedLayer}
import net.verdagon.vale._

import scala.collection.immutable.List

object StructTemplarCore {
  def addBuiltInStructs(env: NamespaceEnvironment[IName2], temputs: TemputsBox): StructRef2 = {
    val emptyTupleFullName = FullName2(List(), TupleName2(List()))
    val emptyTupleEnv = NamespaceEnvironment(Some(env), emptyTupleFullName, Map())
    val structDef2 = StructDefinition2(emptyTupleFullName, false, false, Immutable, List(), false)
    temputs.declareStruct(structDef2.getRef)
    temputs.declareStructMutability(structDef2.getRef, Immutable)
    temputs.declareStructEnv(structDef2.getRef, emptyTupleEnv)
    temputs.add(structDef2)
    // Normally after adding a struct we would add its destructor. Void is the only one we don't
    // have a destructor for.

    temputs.declarePack(List(), structDef2.getRef)
    (structDef2.getRef)
  }

  def maakeStruct(
    // The environment that the struct was defined in.
    structRunesEnv: NamespaceEnvironment[IName2],
    temputs: TemputsBox,
    struct1: StructA,
    coercedFinalTemplateArgs: List[ITemplata]):
  (StructDefinition2) = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = struct1.name
    val export = struct1.`export`
    val fullName = structRunesEnv.fullName.addStep(CitizenName2(humanName, coercedFinalTemplateArgs))
    val temporaryStructRef = StructRef2(fullName)

    val structInnerEnv =
      NamespaceEnvironment(
        Some(structRunesEnv),
        fullName,
        Map())
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

    val structDef2 = StructDefinition2(fullName, export, struct1.weakable, mutability, members, false)

    temputs.add(structDef2);

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == Immutable) {
      DestructorTemplar.getImmConcreteDestructor(temputs, structInnerEnv, structDef2.getRef)
    }


    val implementedInterfaceRefs2 =
      ImplTemplar.getParentInterfaces(temputs, structDef2.getRef);

    implementedInterfaceRefs2.foreach({
      case (implementedInterfaceRefT) => {
        structDef2.mutability match {
          case Mutable => {
            val sefResult =
              OverloadTemplar.scoutExpectedFunctionForPrototype(
                structInnerEnv,
                temputs,
                GlobalFunctionFamilyNameA(CallTemplar.MUT_INTERFACE_DESTRUCTOR_NAME),
                List(),
                List(ParamFilter(Coord(Own, structDef2.getRef), Some(Override2(implementedInterfaceRefT)))),
                List(),
                true)
            sefResult match {
              case ScoutExpectedFunctionSuccess(_) =>
              case ScoutExpectedFunctionFailure(_, _, _, _, _) => {
                vfail(sefResult.toString)
              }
            }
          }
          case Immutable => {
            // If it's immutable, make sure there's a zero-arg destructor.
            DestructorTemplar.getImmInterfaceDestructorOverride(temputs, structInnerEnv, structDef2.getRef, implementedInterfaceRefT)
          }
        }
      }
    })

    val ancestorInterfaces =
      ImplTemplar.getAncestorInterfaces(temputs, temporaryStructRef)

    ancestorInterfaces.foreach({
      case (ancestorInterface) => {
        if (structDef2.weakable) {
          val interfaceDefinition2 = temputs.lookupInterface(ancestorInterface)
          vcheck(interfaceDefinition2.weakable, WeakableStructImplementingNonWeakableInterface)
        }
        temputs.addImpl(temporaryStructRef, ancestorInterface)
      }
    })

    structDef2
  }

  // Takes a IEnvironment because we might be inside a:
  // struct<T> Thing<T> {
  //   t: T;
  // }
  // which means we need some way to know what T is.
  def makeInterface(
    interfaceRunesEnv: NamespaceEnvironment[IName2],
    temputs: TemputsBox,
    interfaceA: InterfaceA,
    coercedFinalTemplateArgs2: List[ITemplata]):
  (InterfaceDefinition2) = {
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = interfaceA.name
    val fullName = interfaceRunesEnv.fullName.addStep(CitizenName2(humanName, coercedFinalTemplateArgs2))
    val temporaryInferfaceRef = InterfaceRef2(fullName)

    val interfaceInnerEnv0 =
      NamespaceEnvironment(
        Some(interfaceRunesEnv),
        fullName,
        Map())
    val interfaceInnerEnv1 =
      interfaceInnerEnv0.addEntries(
        interfaceA.identifyingRunes.zip(coercedFinalTemplateArgs2)
          .map({ case (rune, templata) => (NameTranslator.translateRune(rune), List(TemplataEnvEntry(templata))) })
          .toMap)
    val interfaceInnerEnv2 =
      interfaceInnerEnv1.addEntries(
        interfaceA.internalMethods
          .map(internalMethod => {
            val functionName = NameTranslator.translateFunctionNameToTemplateName(internalMethod.name)
            (functionName -> List(FunctionEnvEntry(internalMethod)))
          })
          .toMap[IName2, List[IEnvEntry]])
    val interfaceInnerEnv = interfaceInnerEnv2

    temputs
      .declareInterfaceEnv(
        temporaryInferfaceRef,
        interfaceInnerEnv)

    val internalMethods2 =
      interfaceA.internalMethods.map(internalMethod => {
        FunctionTemplar.evaluateOrdinaryFunctionFromNonCallForHeader(
          temputs,
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
      InterfaceDefinition2(
        fullName,
        interfaceA.weakable,
        mutability,
        internalMethods2)
    temputs.add(interfaceDef2)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == Immutable) {
      DestructorTemplar.getImmInterfaceDestructor(temputs, interfaceInnerEnv, interfaceDef2.getRef)
    }

    val _ = ImplTemplar.getParentInterfaces(temputs, temporaryInferfaceRef)

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

    (interfaceDef2)
  }

  private def makeStructMembers(env: IEnvironment, temputs: TemputsBox, members: List[StructMemberA]): (List[StructMember2]) = {
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
    temputs: TemputsBox,
    member: StructMemberA):
  (StructMember2) = {
    val CoordTemplata(coord) = vassertSome(env.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(member.typeRune), Set(TemplataLookupContext)))
    (StructMember2(CodeVarName2(member.name), Conversions.evaluateVariability(member.variability), ReferenceMemberType2(coord)))
  }

//  // Makes a functor for the given prototype.
//  def functionToLambda(
//    outerEnv: IEnvironment,
//    temputs: TemputsBox,
//    header: FunctionHeader2):
//  StructRef2 = {
//    val mutability = Immutable
//
//    val nearName = FunctionScout.CLOSURE_STRUCT_NAME // For example "__Closure<main>:lam1"
//    val fullName = FullName2(header.fullName.steps :+ NamePart2(nearName, Some(List()), None, None))
//
//    val structRef = StructRef2(fullName)
//
//    // We declare the function into the environment that we use to compile the
//    // struct, so that those who use the struct can reach into its environment
//    // and see the function and use it.
//    // See CSFMSEO and SAFHE.
//    val structEnv =
//      NamespaceEnvironment(
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
//    val closureStructDefinition = StructDefinition2(fullName, mutability, List(), true);
//    temputs.add(closureStructDefinition)
//
//    val closuredVarsStructRef = closureStructDefinition.getRef;
//
//    closuredVarsStructRef
//  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: TemputsBox,
    name: LambdaNameA,
    functionA: FunctionA,
    members: List[StructMember2]):
  (StructRef2, Mutability, FunctionTemplata) = {
    val mutability =
      getCompoundTypeMutability(temputs, members.map(_.tyype.reference))

    val nearName = LambdaCitizenName2(NameTranslator.translateCodeLocation(name.codeLocation))
    val fullName = containingFunctionEnv.fullName.addStep(nearName)

    val structRef = StructRef2(fullName)

    // We declare the function into the environment that we use to compile the
    // struct, so that those who use the struct can reach into its environment
    // and see the function and use it.
    // See CSFMSEO and SAFHE.
    val structEnv =
      NamespaceEnvironment(
        Some(containingFunctionEnv),
        fullName,
        Map(
          FunctionTemplateName2(CallTemplar.CALL_FUNCTION_NAME, CodeLocation2(-14, 0)) -> List(FunctionEnvEntry(functionA)),
          nearName -> List(TemplataEnvEntry(KindTemplata(structRef))),
          ClosureParamName2() -> List(TemplataEnvEntry(KindTemplata(structRef)))))
    // We return this from the function in case we want to eagerly compile it (which we do
    // if it's not a template).
    val functionTemplata =
        FunctionTemplata(
          structEnv,
          functionA)

    temputs.declareStruct(structRef);
    temputs.declareStructMutability(structRef, mutability)
    temputs.declareStructEnv(structRef, structEnv);

    val closureStructDefinition = StructDefinition2(fullName, false, false, mutability, members, true);
    temputs.add(closureStructDefinition)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == Immutable) {
      DestructorTemplar.getImmConcreteDestructor(temputs, structEnv, closureStructDefinition.getRef)
    }

    val closuredVarsStructRef = closureStructDefinition.getRef;

    (closuredVarsStructRef, mutability, functionTemplata)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnderstruct(
    outerEnv: NamespaceEnvironment[IName2],
    temputs: TemputsBox,
    memberCoords: List[Coord],
    name: ICitizenName2):
  (StructRef2, Mutability) = {
    temputs.packTypes.get(memberCoords) match {
      case Some(structRef2) => return (structRef2, temputs.lookupStruct(structRef2).mutability)
      case None =>
    }
    val packMutability = getCompoundTypeMutability(temputs, memberCoords)
    val members =
      memberCoords.zipWithIndex.map({
        case (pointerType, index) => StructMember2(CodeVarName2(index.toString), Final, ReferenceMemberType2(pointerType))
      })

    val fullName = outerEnv.fullName.addStep(TupleName2(memberCoords))
    val structInnerEnv =
      NamespaceEnvironment(
        Some(outerEnv),
        fullName,
        Map())

    val newStructDef = StructDefinition2(structInnerEnv.fullName, false, false, packMutability, members, false);
    if (memberCoords.isEmpty && packMutability != Immutable)
      vfail("curiosity")

    temputs.declareStruct(newStructDef.getRef);
    temputs.declareStructMutability(newStructDef.getRef, packMutability)
    temputs.declareStructEnv(newStructDef.getRef, structInnerEnv);
    temputs.add(newStructDef)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (packMutability == Immutable) {
      DestructorTemplar.getImmConcreteDestructor(temputs, structInnerEnv, newStructDef.getRef)
    }

    temputs.declarePack(memberCoords, newStructDef.getRef);

    (newStructDef.getRef, packMutability)
  }

  def getCompoundTypeMutability(temputs: TemputsBox, memberTypes2: List[Coord])
  : Mutability = {
    val membersOwnerships = memberTypes2.map(_.ownership)
    val allMembersImmutable = membersOwnerships.isEmpty || membersOwnerships.toSet == Set(Share)
    if (allMembersImmutable) Immutable else Mutable
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  // This doesnt make a constructor. We could add that if we wanted to.
  def makeAnonymousSubstruct(
      interfaceEnv: IEnvironment,
      temputs: TemputsBox,
      anonymousSubstructName: FullName2[AnonymousSubstructName2],
      interfaceRef: InterfaceRef2):
  (StructRef2, Mutability) = {
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
    if (mutability == Immutable) {
      if (getCompoundTypeMutability(temputs, callables) == Mutable) {
        vfail()
      }
    }

    val structRef = StructRef2(anonymousSubstructName)

    val forwarderFunctionHeaders =
      interfaceDef.internalMethods.zipWithIndex.map({
        case (FunctionHeader2(superFunctionName, _, _, superParams, superReturnType, _), index) => {
          val params =
            superParams.map({
              case Parameter2(name, Some(Abstract2), Coord(ownership, ir)) => {
                vassert(ir == interfaceRef)
                Parameter2(name, Some(Override2(interfaceRef)), Coord(ownership, structRef))
              }
              case otherParam => otherParam
            })

          val FunctionName2(humanName, _, _) = superFunctionName.last
          val fowarderName =
            anonymousSubstructName.addStep(FunctionName2(humanName, List(), params.map(_.tyype)))
          val forwarderHeader =
            FunctionHeader2(
              fowarderName,
              false,
              false,
              params,
              superReturnType,
              None)

          temputs.declareFunctionSignature(forwarderHeader.toSignature, None)
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
        ImplDeclareName2(CodeLocation2(-15, 0)) -> List(TemplataEnvEntry(ExternImplTemplata(structRef, interfaceRef))),
        // This is used later by the interface constructor generator to know what interface to impl.
        AnonymousSubstructParentInterfaceRune2() -> List(TemplataEnvEntry(KindTemplata(interfaceRef))),
        AnonymousSubstructImplName2() -> List(TemplataEnvEntry(ExternImplTemplata(structRef, interfaceRef))))
    val structInnerEnv =
      NamespaceEnvironment(
        Some(interfaceEnv),
        anonymousSubstructName,
        structInnerEnvEntries.toMap)


    temputs.addImpl(structRef, interfaceRef)

    temputs.declareStruct(structRef)
    temputs.declareStructMutability(structRef, mutability)
    temputs.declareStructEnv(structRef, structInnerEnv)

    vassert(interfaceDef.internalMethods.size == callables.size)

    val structDef =
      StructDefinition2(
        anonymousSubstructName,
        false,
        interfaceDef.weakable,
        mutability,
        callables.zipWithIndex.map({ case (lambda, index) =>
          StructMember2(AnonymousSubstructMemberName2(index), Final, ReferenceMemberType2(lambda))
        }),
        false)
    temputs.add(structDef)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == Immutable) {
      DestructorTemplar.getImmConcreteDestructor(temputs, structInnerEnv, structDef.getRef)
    }

    forwarderFunctionHeaders.zip(callables).zipWithIndex.foreach({
      case ((forwarderHeader, lambda), methodIndex) => {
        val localVariables =
          forwarderHeader.params.map(param => {
            ReferenceLocalVariable2(forwarderHeader.fullName.addStep(param.name), Final, param.tyype)
          })

        // The args for the call inside the forwarding function.
        val forwardedCallArgs =
          (Coord(if (lambda.ownership == Share) Share else Borrow, lambda.referend) ::
          forwarderHeader.paramTypes.tail).map(ParamFilter(_, None))

//        start here
        // since IFunction has a drop() method, its looking for a drop() for the
        // lambda we gave it. but its immutable, so it needs no drop... or wait,
        // maybe imms have drops?

        val lambdaFunctionPrototype =
          OverloadTemplar.scoutExpectedFunctionForPrototype(
            interfaceEnv, // Shouldnt matter here, because the callables themselves should have a __call
            temputs,
            GlobalFunctionFamilyNameA(CallTemplar.CALL_FUNCTION_NAME),
            List(),
            forwardedCallArgs,
            List(),
            true) match {
            case seff@ScoutExpectedFunctionFailure(_, _, _, _, _) => vfail(seff.toString)
            case ScoutExpectedFunctionSuccess(prototype) => prototype
          }

        val argExpressions =
          SoftLoad2(
            ReferenceMemberLookup2(
              ArgLookup2(
                0,
                Coord(
                  if (structDef.mutability == Immutable) Share else Borrow,
                  structDef.getRef)),
              structDef.fullName.addStep(structDef.members(methodIndex).name),
              structDef.members(methodIndex).tyype.reference),
            if (structDef.members(methodIndex).tyype.reference.ownership == Share) Share else Borrow) ::
          forwarderHeader.params.tail.zipWithIndex.map({ case (param, index) =>
            ArgLookup2(index + 1, param.tyype)
          })

        val forwarderFunction =
          Function2(
            forwarderHeader,
            localVariables,
            Block2(
              List(
                Return2(
                  FunctionCall2(lambdaFunctionPrototype, argExpressions)))))
        temputs.addFunction(forwarderFunction)
      }
    })

    (structRef, mutability)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    prototype: Prototype2,
    structFullName: FullName2[ICitizenName2]):
  StructRef2 = {
    val mutability = Immutable

    val structRef = StructRef2(structFullName)

    temputs.declareStruct(structRef)
    temputs.declareStructMutability(structRef, mutability)

    val forwarderParams =
      Parameter2(TemplarTemporaryVarName2(-1), None, Coord(if (mutability == Immutable) Share else Borrow, structRef)) ::
      prototype.paramTypes.zipWithIndex.map({ case (paramType, index) =>
        Parameter2(TemplarTemporaryVarName2(index), None, paramType)
      })
    val forwarderHeader =
      FunctionHeader2(
        structFullName.addStep(FunctionName2(CallTemplar.CALL_FUNCTION_NAME, List(), forwarderParams.map(_.tyype))),
        false,
        false,
        forwarderParams,
        prototype.returnType,
        None)
    temputs.declareFunctionSignature(forwarderHeader.toSignature, None)

    val structInnerEnv =
      NamespaceEnvironment(
        Some(outerEnv),
        structFullName,
        Map(forwarderHeader.fullName.last -> List(TemplataEnvEntry(ExternFunctionTemplata(forwarderHeader)))))
    temputs.declareStructEnv(structRef, structInnerEnv)

    val structDef =
      StructDefinition2(
        structFullName,
        false,
        false,
        mutability,
        List(),
        false)
    temputs.add(structDef)

    // If it's immutable, make sure there's a zero-arg destructor.
    if (mutability == Immutable) {
      DestructorTemplar.getImmConcreteDestructor(temputs, structInnerEnv, structDef.getRef)
    }

    val forwarderFunction =
      Function2(
        forwarderHeader,
        List(),
        Block2(
          List(
            Discard2(ArgLookup2(0, Coord(Share, structRef))),
            Return2(
              FunctionCall2(
                prototype,
                forwarderHeader.params.tail.zipWithIndex.map({ case (param, index) =>
                  ArgLookup2(index + 1, param.tyype)
                }))))))
    temputs.addFunction(forwarderFunction)

    structRef
  }

  def makeStructConstructor(
    temputs: TemputsBox,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinition2,
    constructorFullName: FullName2[IFunctionName2]):
  FunctionHeader2 = {
    val constructorParams =
      structDef.members.map({
        case StructMember2(name, _, ReferenceMemberType2(reference)) => {
          Parameter2(name, None, reference)
        }
      })
    val constructorReturnOwnership = if (structDef.mutability == Mutable) Own else Share
    val constructorReturnType = Coord(constructorReturnOwnership, structDef.getRef)
    // not virtual because how could a constructor be virtual
    val constructor2 =
      Function2(
        FunctionHeader2(
          constructorFullName,
          false, false,
          constructorParams,
          constructorReturnType,
          maybeConstructorOriginFunctionA),
        List(),
        Block2(
          List(
            Return2(
              Construct2(
                structDef.getRef,
                Coord(if (structDef.mutability == Mutable) Own else Share, structDef.getRef),
                constructorParams.zipWithIndex.map({ case (p, index) => ArgLookup2(index, p.tyype) }))))))

    // we cant make the destructor here because they might have a user defined one somewhere
    temputs.declareFunctionReturnType(constructor2.header.toSignature, constructor2.header.returnType)
    temputs.addFunction(constructor2);

    vassert(temputs.exactDeclaredSignatureExists(constructor2.header.fullName))

    (constructor2.header)
  }
}
