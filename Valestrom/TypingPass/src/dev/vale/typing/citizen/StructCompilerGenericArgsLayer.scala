package dev.vale.typing.citizen

import dev.vale.highertyping.FunctionA
import dev.vale.postparsing.IFunctionDeclarationNameS
import dev.vale.postparsing.rules.RuneUsage
import dev.vale.typing.env.IEnvironment
import dev.vale.typing.{InferCompiler, InitialKnown, TypingPassOptions, CompilerOutputs}
import dev.vale.typing.function.FunctionCompiler
import dev.vale.typing.names.NameTranslator
import dev.vale.typing.templata.{Conversions, FunctionTemplata, ITemplata, InterfaceTemplata, MutabilityTemplata, StructTemplata}
import dev.vale.typing.types.{InterfaceTT, MutabilityT, StructMemberT, StructTT}
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail}
import dev.vale.highertyping._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.citizen.StructCompilerMiddle
import dev.vale.typing.env._
import dev.vale.typing.names.AnonymousSubstructNameT
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

class StructCompilerGenericArgsLayer(
    opts: TypingPassOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    inferCompiler: InferCompiler,
    ancestorHelper: AncestorHelper,
    delegate: IStructCompilerDelegate) {
  val middle = new StructCompilerMiddle(opts, interner, nameTranslator, ancestorHelper, delegate)

  def getStructRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    templateArgs: Vector[ITemplata]):
  (StructTT) = {
    Profiler.frame(() => {
      val StructTemplata(env, structA) = structTemplata
      val structTemplateName = nameTranslator.translateCitizenName(structA.name)
      val structName = structTemplateName.makeCitizenName(interner, templateArgs)
      val fullName = env.fullName.addStep(structName)
//      val fullName = env.fullName.addStep(structLastName)

      coutputs.structDeclared(interner.intern(StructTT(fullName))) match {
        case Some(structTT) => {
          (structTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != structA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryStructRef = interner.intern(StructTT(fullName))
          coutputs.declareKind(temporaryStructRef)

          structA.maybePredictedMutability match {
            case None =>
            case Some(predictedMutability) => coutputs.declareCitizenMutability(temporaryStructRef, Conversions.evaluateMutability(predictedMutability))
          }
          vassert(structA.identifyingRunes.size == templateArgs.size)
          val inferences =
            inferCompiler.solveExpectComplete(
              env,
              coutputs,
              structA.rules,
              structA.runeToType,
              callRange,
              structA.identifyingRunes.map(_.rune).zip(templateArgs)
                .map({ case (a, b) => InitialKnown(RuneUsage(callRange, a), b) }),
              Vector())

          structA.maybePredictedMutability match {
            case None => {
              val MutabilityTemplata(mutability) = inferences(structA.mutabilityRune.rune)
              coutputs.declareCitizenMutability(temporaryStructRef, mutability)
            }
            case Some(_) =>
          }

          middle.getStructRef(env, coutputs, callRange, structA, inferences)
        }
      }
    })
  }

  def getInterfaceRef(
    coutputs: CompilerOutputs,
    callRange: RangeS,
    interfaceTemplata: InterfaceTemplata,
    templateArgs: Vector[ITemplata]):
  (InterfaceTT) = {
    Profiler.frame(() => {
      val InterfaceTemplata(env, interfaceA) = interfaceTemplata
      val interfaceTemplateName = nameTranslator.translateCitizenName(interfaceA.name)
      val interfaceName = interfaceTemplateName.makeCitizenName(interner, templateArgs)
      val fullName = env.fullName.addStep(interfaceName)
//      val fullName = env.fullName.addStep(interfaceLastName)

      coutputs.interfaceDeclared(interner.intern(InterfaceTT(fullName))) match {
        case Some(interfaceTT) => {
          (interfaceTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != interfaceA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryInterfaceRef = interner.intern(InterfaceTT(fullName))
          coutputs.declareKind(temporaryInterfaceRef)


          interfaceA.maybePredictedMutability match {
            case None =>
            case Some(predictedMutability) => coutputs.declareCitizenMutability(temporaryInterfaceRef, Conversions.evaluateMutability(predictedMutability))
          }
          vassert(interfaceA.identifyingRunes.size == templateArgs.size)

          val inferences =
            inferCompiler.solveExpectComplete(
              env,
              coutputs,
              interfaceA.rules,
              interfaceA.runeToType,
              callRange,
              interfaceA.identifyingRunes.map(_.rune).zip(templateArgs)
                .map({ case (a, b) => InitialKnown(RuneUsage(callRange, a), b) }),
              Vector())

          interfaceA.maybePredictedMutability match {
            case None => {
              val MutabilityTemplata(mutability) = inferences(interfaceA.mutabilityRune.rune)
              coutputs.declareCitizenMutability(temporaryInterfaceRef, mutability)
            }
            case Some(_) =>
          }

          middle.getInterfaceRef(env, coutputs, callRange, interfaceA, inferences)
        }
      }
    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    coutputs: CompilerOutputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    middle.makeClosureUnderstruct(containingFunctionEnv, coutputs, name, functionS, members)
  }
}
