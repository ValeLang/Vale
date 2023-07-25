package dev.vale.typing.citizen

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing._
import dev.vale.postparsing.rules.{IRulexSR, RuneUsage}
import dev.vale.typing.env.IInDenizenEnvironmentT
import dev.vale.typing.{CompilerOutputs, InferCompiler, InitialKnown, TypingPassOptions}
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.{Accumulator, Err, Interner, Keywords, Ok, Profiler, RangeS, typing, vassert, vassertSome, vcurious, vfail, vimpl, vregionmut, vwat}
import dev.vale.highertyping._
import dev.vale.solver.{CompleteSolve, FailedSolve, IncompleteSolve}
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.env._

import scala.collection.immutable.{List, Set}

class StructCompilerGenericArgsLayer(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,
    templataCompiler: TemplataCompiler,
    inferCompiler: InferCompiler,
    delegate: IStructCompilerDelegate) {
  val core = new StructCompilerCore(opts, interner, keywords, nameTranslator, delegate)

  def resolveStruct(
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    structTemplata: StructDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  IResolveOutcome[StructTT] = {
    Profiler.frame(() => {
      val StructDefinitionTemplataT(declaringEnv, structA) = structTemplata
      val structTemplateName = nameTranslator.translateStructName(structA.name)

      // We no longer assume this:
      //   vassert(templateArgs.size == structA.genericParameters.size)
      // because we have default generic arguments now.

      val initialKnowns =
        structA.genericParameters.zip(templateArgs).map({ case (genericParam, templateArg) =>
          InitialKnown(RuneUsage(callRange.head, genericParam.rune.rune), templateArg)
        })

      val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
          structA.headerRules.toVector, structA.genericParameters, templateArgs.size)

      val contextRegion = RegionT()

      // Check if its a valid use of this template
      val envs = InferEnv(originalCallingEnv, callRange, callLocation, declaringEnv, contextRegion)
      val solver =
        inferCompiler.makeSolver(
          envs,
          coutputs,
          callSiteRules,
          structA.headerRuneToType,
          callRange,
          initialKnowns,
          Vector())
      inferCompiler.continue(envs, coutputs, solver) match {
        case Ok(()) =>
        case Err(x) => return ResolveFailure(callRange, ResolvingSolveFailedOrIncomplete(x))
      }
      val CompleteResolveSolve(_, inferences, runeToFunctionBound, Vector(), Vector()) =
        inferCompiler.checkResolvingConclusionsAndResolve(
          envs,
          coutputs,
          callRange,
          callLocation,
          structA.headerRuneToType,
          callSiteRules,
          Vector(),
          solver) match {
          case Ok(ccs) => ccs
          case Err(x) => return ResolveFailure(callRange, x)
        }

      val finalGenericArgs = structA.genericParameters.map(_.rune.rune).map(inferences)
      val structName = structTemplateName.makeStructName(interner, finalGenericArgs)
      val id = declaringEnv.id.addStep(structName)

      coutputs.addInstantiationBounds(id, runeToFunctionBound)
      val structTT = interner.intern(StructTT(id))

      ResolveSuccess(structTT)
    })
  }

  // See SFWPRL for how this is different from resolveInterface.
  def predictInterface(
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    interfaceTemplata: InterfaceDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  (InterfaceTT) = {
    Profiler.frame(() => {
      val InterfaceDefinitionTemplataT(declaringEnv, interfaceA) = interfaceTemplata
      val interfaceTemplateName = nameTranslator.translateInterfaceName(interfaceA.name)

      // We no longer assume this:
      //   vassert(templateArgs.size == structA.genericParameters.size)
      // because we have default generic arguments now.

      val initialKnowns =
        interfaceA.genericParameters.zip(templateArgs).map({ case (genericParam, templateArg) =>
          InitialKnown(RuneUsage(callRange.head, genericParam.rune.rune), templateArg)
        })

      val callSiteRules =
        TemplataCompiler.assemblePredictRules(
          interfaceA.genericParameters, templateArgs.size)
      val runesForPrediction =
        (interfaceA.genericParameters.map(_.rune.rune) ++
          callSiteRules.flatMap(_.runeUsages.map(_.rune))).toSet
      val runeToTypeForPrediction =
        runesForPrediction.toVector.map(r => r -> interfaceA.runeToType(r)).toMap

      val contextRegion = RegionT()

      // We're just predicting, see STCMBDP.
      val inferences =
        inferCompiler.partialSolve(
          InferEnv(originalCallingEnv, callRange, callLocation, declaringEnv, contextRegion),
          coutputs,
          callSiteRules,
          runeToTypeForPrediction,
          callRange,
          initialKnowns,
          Vector()) match {
          case Ok(i) => i
          case Err(e) => throw CompileErrorExceptionT(typing.TypingPassSolverError(callRange, e))
        }

      val finalGenericArgs = interfaceA.genericParameters.map(_.rune.rune).map(inferences)
      val interfaceName = interfaceTemplateName.makeInterfaceName(interner, finalGenericArgs)
      val id = declaringEnv.id.addStep(interfaceName)

      // Usually when we make an InterfaceTT we put the instantiation bounds into the coutputs,
      // but we unfortunately can't here because we're just predicting an interface; we'll
      // try to resolve it later and then put the bounds in. Hopefully this InterfaceTT doesn't
      // escape into the wild.
      val interfaceTT = interner.intern(InterfaceTT(id))
      interfaceTT
    })
  }

  // See SFWPRL for how this is different from resolveStruct.
  def predictStruct(
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    structTemplata: StructDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  (StructTT) = {
    Profiler.frame(() => {
      val StructDefinitionTemplataT(declaringEnv, structA) = structTemplata
      val structTemplateName = nameTranslator.translateStructName(structA.name)

      // We no longer assume this:
      //   vassert(templateArgs.size == structA.genericParameters.size)
      // because we have default generic arguments now.

      val initialKnowns =
        structA.genericParameters.zip(templateArgs).map({ case (genericParam, templateArg) =>
          InitialKnown(RuneUsage(vassertSome(callRange.headOption), genericParam.rune.rune), templateArg)
        })

      val callSiteRules =
        TemplataCompiler.assemblePredictRules(
          structA.genericParameters, templateArgs.size)
      val runesForPrediction =
        (structA.genericParameters.map(_.rune.rune) ++
          callSiteRules.flatMap(_.runeUsages.map(_.rune))).toSet
      val runeToTypeForPrediction =
        runesForPrediction.toVector.map(r => r -> structA.headerRuneToType(r)).toMap

      val contextRegion = RegionT()
      val inferences =
      // We're just predicting, see STCMBDP.
        inferCompiler.partialSolve(
          InferEnv(originalCallingEnv, callRange, callLocation, declaringEnv, contextRegion),
          coutputs,
          callSiteRules,
          runeToTypeForPrediction,
          callRange,
          initialKnowns,
          Vector()) match {
          case Ok(i) => i
          case Err(e) => throw CompileErrorExceptionT(typing.TypingPassSolverError(callRange, e))
        }

      val finalGenericArgs = structA.genericParameters.map(_.rune.rune).map(inferences)
      val structName = structTemplateName.makeStructName(interner, finalGenericArgs)
      val id = declaringEnv.id.addStep(structName)

      // Usually when we make an InterfaceTT we put the instantiation bounds into the coutputs,
      // but we unfortunately can't here because we're just predicting an interface; we'll
      // try to resolve it later and then put the bounds in. Hopefully this InterfaceTT doesn't
      // escape into the wild.
      val structTT = interner.intern(StructTT(id))
      structTT
    })
  }

  def resolveInterface(
    coutputs: CompilerOutputs,
    originalCallingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    interfaceTemplata: InterfaceDefinitionTemplataT,
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  IResolveOutcome[InterfaceTT] = {
    Profiler.frame(() => {
      val InterfaceDefinitionTemplataT(declaringEnv, interfaceA) = interfaceTemplata
      val interfaceTemplateName = nameTranslator.translateInterfaceName(interfaceA.name)

      // We no longer assume this:
      //   vassert(templateArgs.size == structA.genericParameters.size)
      // because we have default generic arguments now.

      val initialKnowns =
        interfaceA.genericParameters.zip(templateArgs).map({ case (genericParam, templateArg) =>
          InitialKnown(RuneUsage(callRange.head, genericParam.rune.rune), templateArg)
        })

      val callSiteRules =
        TemplataCompiler.assembleCallSiteRules(
          interfaceA.rules.toVector, interfaceA.genericParameters, templateArgs.size)

      val contextRegion = RegionT()

      // This checks to make sure it's a valid use of this template.
      val CompleteResolveSolve(_, inferences, runeToFunctionBound, Vector(), Vector()) =
        inferCompiler.solveForResolving(
        InferEnv(originalCallingEnv, callRange, callLocation, declaringEnv, contextRegion),
        coutputs,
        callSiteRules,
          interfaceA.runeToType,
          callRange,
        callLocation,
          initialKnowns,
          Vector(),
        Vector()) match {
          case Ok(ccs) => ccs
          case Err(x) => return ResolveFailure(callRange, x)
        }

      val finalGenericArgs = interfaceA.genericParameters.map(_.rune.rune).map(inferences)
      val interfaceName = interfaceTemplateName.makeInterfaceName(interner, finalGenericArgs)
      val id = declaringEnv.id.addStep(interfaceName)

      coutputs.addInstantiationBounds(id, runeToFunctionBound)
      val interfaceTT = interner.intern(InterfaceTT(id))

      ResolveSuccess(interfaceTT)
    })
  }

  def compileStruct(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    structTemplata: StructDefinitionTemplataT):
  Unit = {
    Profiler.frame(() => {
      val StructDefinitionTemplataT(declaringEnv, structA) = structTemplata
      val structTemplateName = nameTranslator.translateStructName(structA.name)
      val structTemplateId = declaringEnv.id.addStep(structTemplateName)

      // We declare the struct's outer environment in the precompile stage instead of here because
      // of MDATOEF. DO NOT SUBMIT update
      val outerEnv = coutputs.getOuterEnvForType(parentRanges, structTemplateId)


      // We're about to eagerly pre-compile the struct and make its inner env. We're not going to check yet that any of
      // its usages of other structs/interfaces/functions are correct.

      val allRulesS = structA.headerRules ++ structA.memberRules
      val allRuneToType = structA.headerRuneToType ++ structA.membersRuneToType
      val definitionRules = allRulesS.filter(InferCompiler.includeRuleInDefinitionSolve)

      val envs = InferEnv(outerEnv, List(structA.range), callLocation, outerEnv, RegionT())
      val solver =
        inferCompiler.makeSolver(
          envs, coutputs, definitionRules, allRuneToType, List(structA.range), Vector(), Vector())
      // Incrementally solve and add placeholders, see IRAGP.
      inferCompiler.incrementallySolve(
        envs, coutputs, solver,
        // Each step happens after the solver has done all it possibly can. Sometimes this can lead
        // to races, see RRBFS.
        (solver) => {
          TemplataCompiler.getFirstUnsolvedIdentifyingRune(structA.genericParameters, (rune) => solver.getConclusion(rune).nonEmpty) match {
            case None => false
            case Some((genericParam, index)) => {
              val placeholderPureHeight = vregionmut(None)
              // Make a placeholder for every argument even if it has a default, see DUDEWCD.
              val templata =
                templataCompiler.createPlaceholder(
                  coutputs, outerEnv, structTemplateId, genericParam, index, allRuneToType, placeholderPureHeight, true)
              solver.manualStep(Map(genericParam.rune.rune -> templata))
              true
            }
          }
        }) match {
        case Err(f@FailedCompilerSolve(_, _, err)) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(List(structA.range), f))
        }
        case Ok(true) =>
        case Ok(false) => // Incomplete, will be detected in the below expectCompleteSolve
      }
      val inferences =
        inferCompiler.interpretResults(allRuneToType, solver) match {
          case Err(f) => throw CompileErrorExceptionT(typing.TypingPassSolverError(List(structA.range), f))
          case Ok(c) => c
        }

      structA.maybePredictedMutability match {
        case None => {
          val mutability =
            ITemplataT.expectMutability(inferences(structA.mutabilityRune.rune))
          coutputs.declareTypeMutability(structTemplateId, mutability)
        }
        case Some(_) =>
      }

      val templateArgs = structA.genericParameters.map(_.rune.rune).map(inferences)

      val id = assembleStructName(structTemplateId, templateArgs)

      val innerEnv =
        CitizenEnvironmentT(
          outerEnv.globalEnv,
          outerEnv,
          structTemplateId,
          id,
          TemplatasStore(id, Map(), Map())
              .addEntries(
                interner,
                inferences.toVector
                    .map({ case (rune, templata) => (interner.intern(RuneNameT(rune)), TemplataEnvEntry(templata)) })))

      coutputs.declareTypeInnerEnv(structTemplateId, innerEnv)
      //
      // val CitizenEnvironmentT(globalEnv, parentEnv, templateId, id, templatas) = innerEnv
      // val inferences =
      //   templatas.entriesByNameT.map({
      //     case (RuneNameT(r), TemplataEnvEntry(t)) => r -> t
      //     case other => vwat(other)
      //   })
      //
      // val outerEnv = coutputs.getOuterEnvForType(structA.range :: parentRanges, structTemplateId)
      //
      // val allRulesS = structA.headerRules ++ structA.memberRules
      // val definitionRules = allRulesS.filter(InferCompiler.includeRuleInDefinitionSolve)

      val CompleteDefineSolve(_, _, _, _) =
        inferCompiler.checkDefiningConclusionsAndResolve(
          outerEnv, coutputs, structA.range :: parentRanges, callLocation, RegionT(), definitionRules, Vector(), inferences) match {
          case Err(f) => throw CompileErrorExceptionT(TypingPassDefiningError(structA.range :: parentRanges, DefiningResolveConclusionError(f)))
          case Ok(c) => c
        }

      core.compileStruct(outerEnv, innerEnv, coutputs, parentRanges, callLocation, structA)
    })
  }

  def compileInterface(
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    interfaceTemplata: InterfaceDefinitionTemplataT):
  Unit = {
    Profiler.frame(() => {
      val InterfaceDefinitionTemplataT(declaringEnv, interfaceA) = interfaceTemplata
      val interfaceTemplateName = nameTranslator.translateInterfaceName(interfaceA.name)
      val interfaceTemplateId = declaringEnv.id.addStep(interfaceTemplateName)

      // We declare the interface's outer environment in the precompile stage instead of here because
      // of MDATOEF.
      val outerEnv = coutputs.getOuterEnvForType(parentRanges, interfaceTemplateId)

      //      val fullName = env.fullName.addStep(interfaceLastName)

      val definitionRules = interfaceA.rules.filter(InferCompiler.includeRuleInDefinitionSolve)

      val envs = InferEnv(outerEnv, List(interfaceA.range), callLocation, outerEnv, RegionT())
      val solver =
        inferCompiler.makeSolver(
          envs, coutputs, definitionRules, interfaceA.runeToType, interfaceA.range :: parentRanges, Vector(), Vector())
      // Incrementally solve and add placeholders, see IRAGP.
      inferCompiler.incrementallySolve(
        envs, coutputs, solver,
        // Each step happens after the solver has done all it possibly can. Sometimes this can lead
        // to races, see RRBFS.
        (solver) => {
          TemplataCompiler.getFirstUnsolvedIdentifyingRune(interfaceA.genericParameters, (rune) => solver.getConclusion(rune).nonEmpty) match {
            case None => false
            case Some((genericParam, index)) => {
              // Make a placeholder for every argument even if it has a default, see DUDEWCD.
              val placeholderPureHeight = vregionmut(None)
              val templata =
                templataCompiler.createPlaceholder(
                  coutputs, outerEnv, interfaceTemplateId, genericParam, index, interfaceA.runeToType, placeholderPureHeight, true)
              solver.manualStep(Map(genericParam.rune.rune -> templata))
              true
            }
          }
        }) match {
        case Err(f @ FailedCompilerSolve(_, _, err)) => {
          throw CompileErrorExceptionT(typing.TypingPassSolverError(interfaceA.range :: parentRanges, f))
        }
        case Ok(true) =>
        case Ok(false) => // Incomplete, will be detected in the below expectCompleteSolve
      }
      val conclusions =
        inferCompiler.interpretResults(interfaceA.runeToType, solver) match {
          case Err(f) => throw CompileErrorExceptionT(typing.TypingPassSolverError(List(interfaceA.range), f))
          case Ok(c) => c
        }
      val CompleteDefineSolve(inferences, _, declaredBounds, reachableBoundsFromParamsAndReturn) =
        inferCompiler.checkDefiningConclusionsAndResolve(
          envs.originalCallingEnv, coutputs, interfaceA.range :: parentRanges, callLocation, envs.contextRegion, definitionRules, Vector(), conclusions) match {
          case Err(f) => throw CompileErrorExceptionT(TypingPassDefiningError(interfaceA.range :: parentRanges, DefiningResolveConclusionError(f)))
          case Ok(c) => c
        }

      interfaceA.maybePredictedMutability match {
        case None => {
          val mutability = ITemplataT.expectMutability(inferences(interfaceA.mutabilityRune.rune))
          coutputs.declareTypeMutability(interfaceTemplateId, mutability)
        }
        case Some(_) =>
      }

      val templateArgs = interfaceA.genericParameters.map(_.rune.rune).map(inferences)

      val id = assembleInterfaceName(interfaceTemplateId, templateArgs)

      val innerEnv =
        CitizenEnvironmentT(
          outerEnv.globalEnv,
          outerEnv,
          interfaceTemplateId,
          id,
          TemplatasStore(id, Map(), Map())
            .addEntries(
              interner,
              inferences.toVector
                .map({ case (rune, templata) => (interner.intern(RuneNameT(rune)), TemplataEnvEntry(templata)) })))

      coutputs.declareTypeInnerEnv(interfaceTemplateId, innerEnv)

      core.compileInterface(outerEnv, innerEnv, coutputs, parentRanges, callLocation, interfaceA)
    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: NodeEnvironmentT,
    coutputs: CompilerOutputs,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[NormalStructMemberT]):
  (StructTT, MutabilityT, FunctionTemplataT) = {
    core.makeClosureUnderstruct(
      containingFunctionEnv, coutputs, parentRanges, callLocation, name, functionS, members)
  }

  def assembleStructName(
    templateName: IdT[IStructTemplateNameT],
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  IdT[IStructNameT] = {
    templateName.copy(
      localName = templateName.localName.makeStructName(interner, templateArgs))
  }

  def assembleInterfaceName(
    templateName: IdT[IInterfaceTemplateNameT],
    templateArgs: Vector[ITemplataT[ITemplataType]]):
  IdT[IInterfaceNameT] = {
    templateName.copy(
      localName = templateName.localName.makeInterfaceName(interner, templateArgs))
  }
}
