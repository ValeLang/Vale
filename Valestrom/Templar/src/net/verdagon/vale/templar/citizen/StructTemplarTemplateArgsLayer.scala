package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.rules.RuneUsage
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.ast.{LocationInFunctionEnvironment, PrototypeT}
import net.verdagon.vale.templar.citizen.{AncestorHelper, IStructTemplarDelegate, StructTemplarMiddle}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.names.{AnonymousSubstructNameT, FullNameT, ICitizenNameT, INameT, NameTranslator}
import net.verdagon.vale.{IProfiler, RangeS, vassert, vfail, vimpl, vwat}

import scala.collection.immutable.List

class StructTemplarTemplateArgsLayer(
    opts: TemplarOptions,
    profiler: IProfiler,
    inferTemplar: InferTemplar,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val middle = new StructTemplarMiddle(opts, profiler, ancestorHelper, delegate)

  def addBuiltInStructs(env: PackageEnvironment[INameT], temputs: Temputs): Unit = {
    middle.addBuiltInStructs(env, temputs)
  }

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    templateArgs: Vector[ITemplata]):
  (StructTT) = {
    profiler.newProfile("getStructRef", structTemplata.debugString + "<" + templateArgs.map(_.toString).mkString(", ") + ">", () => {
      val StructTemplata(env, structA) = structTemplata
      val structTemplateName = NameTranslator.translateCitizenName(structA.name)
      val structName = structTemplateName.makeCitizenName(templateArgs)
      val fullName = env.fullName.addStep(structName)
//      val fullName = env.fullName.addStep(structLastName)

      temputs.structDeclared(fullName) match {
        case Some(structTT) => {
          (structTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != structA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryStructRef = StructTT(fullName)
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
    profiler.newProfile("getInterfaceRef", interfaceTemplata.debugString + "<" + templateArgs.map(_.toString).mkString(", ") + ">", () => {
      val InterfaceTemplata(env, interfaceA) = interfaceTemplata
      val interfaceTemplateName = NameTranslator.translateCitizenName(interfaceA.name)
      val interfaceName = interfaceTemplateName.makeCitizenName(templateArgs)
      val fullName = env.fullName.addStep(interfaceName)
//      val fullName = env.fullName.addStep(interfaceLastName)

      temputs.interfaceDeclared(fullName) match {
        case Some(interfaceTT) => {
          (interfaceTT)
        }
        case None => {
          // not sure if this is okay or not, do we allow this?
          if (templateArgs.size != interfaceA.identifyingRunes.size) {
            vfail("wat?")
          }
          val temporaryInterfaceRef = InterfaceTT(fullName)
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

//  // Makes a struct to back a pack or tuple
//  def makeSeqOrPackUnerstruct(env: PackageEnvironment[INameT], temputs: Temputs, memberTypes2: Vector[CoordT], name: ICitizenNameT):
//  (StructTT, MutabilityT) = {
//    middle.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
//  }
}
