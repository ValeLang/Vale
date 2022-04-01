package dev.vale.templar.citizen

import dev.vale.astronomer.FunctionA
import dev.vale.scout.IFunctionDeclarationNameS
import dev.vale.scout.rules.RuneUsage
import dev.vale.templar.env.IEnvironment
import dev.vale.templar.{InferTemplar, InitialKnown, TemplarOptions, Temputs}
import dev.vale.templar.function.FunctionTemplar
import dev.vale.templar.names.NameTranslator
import dev.vale.templar.templata.{Conversions, FunctionTemplata, ITemplata, InterfaceTemplata, MutabilityTemplata, StructTemplata}
import dev.vale.templar.types.{InterfaceTT, MutabilityT, StructMemberT, StructTT}
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail}
import dev.vale.astronomer._
import dev.vale.templar.types._
import dev.vale.templar.templata._
import dev.vale.scout._
import dev.vale.templar._
import dev.vale.templar.ast._
import dev.vale.templar.citizen.StructTemplarMiddle
import dev.vale.templar.env._
import dev.vale.templar.names.AnonymousSubstructNameT
import dev.vale.{Interner, Profiler, RangeS, vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

class StructTemplarTemplateArgsLayer(
    opts: TemplarOptions,

    interner: Interner,
    nameTranslator: NameTranslator,
    inferTemplar: InferTemplar,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val middle = new StructTemplarMiddle(opts, interner, nameTranslator, ancestorHelper, delegate)

  def getStructRef(
    temputs: Temputs,
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

      temputs.structDeclared(interner.intern(StructTT(fullName))) match {
        case Some(structTT) => {
          (structTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != structA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryStructRef = interner.intern(StructTT(fullName))
          temputs.declareKind(temporaryStructRef)

          structA.maybePredictedMutability match {
            case None =>
            case Some(predictedMutability) => temputs.declareCitizenMutability(temporaryStructRef, Conversions.evaluateMutability(predictedMutability))
          }
          vassert(structA.identifyingRunes.size == templateArgs.size)
          val inferences =
            inferTemplar.solveExpectComplete(
              env,
              temputs,
              structA.rules,
              structA.runeToType,
              callRange,
              structA.identifyingRunes.map(_.rune).zip(templateArgs)
                .map({ case (a, b) => InitialKnown(RuneUsage(callRange, a), b) }),
              Vector())

          structA.maybePredictedMutability match {
            case None => {
              val MutabilityTemplata(mutability) = inferences(structA.mutabilityRune.rune)
              temputs.declareCitizenMutability(temporaryStructRef, mutability)
            }
            case Some(_) =>
          }

          middle.getStructRef(env, temputs, callRange, structA, inferences)
        }
      }
    })
  }

  def getInterfaceRef(
    temputs: Temputs,
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

      temputs.interfaceDeclared(interner.intern(InterfaceTT(fullName))) match {
        case Some(interfaceTT) => {
          (interfaceTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != interfaceA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryInterfaceRef = interner.intern(InterfaceTT(fullName))
          temputs.declareKind(temporaryInterfaceRef)


          interfaceA.maybePredictedMutability match {
            case None =>
            case Some(predictedMutability) => temputs.declareCitizenMutability(temporaryInterfaceRef, Conversions.evaluateMutability(predictedMutability))
          }
          vassert(interfaceA.identifyingRunes.size == templateArgs.size)

          val inferences =
            inferTemplar.solveExpectComplete(
              env,
              temputs,
              interfaceA.rules,
              interfaceA.runeToType,
              callRange,
              interfaceA.identifyingRunes.map(_.rune).zip(templateArgs)
                .map({ case (a, b) => InitialKnown(RuneUsage(callRange, a), b) }),
              Vector())

          interfaceA.maybePredictedMutability match {
            case None => {
              val MutabilityTemplata(mutability) = inferences(interfaceA.mutabilityRune.rune)
              temputs.declareCitizenMutability(temporaryInterfaceRef, mutability)
            }
            case Some(_) =>
          }

          middle.getInterfaceRef(env, temputs, callRange, interfaceA, inferences)
        }
      }
    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: IFunctionDeclarationNameS,
    functionS: FunctionA,
    members: Vector[StructMemberT]):
  (StructTT, MutabilityT, FunctionTemplata) = {
    middle.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }
}
