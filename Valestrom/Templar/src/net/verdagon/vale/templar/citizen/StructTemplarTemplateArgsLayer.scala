package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{vfail, vimpl, vwat}

import scala.collection.immutable.List

object StructTemplarTemplateArgsLayer {

  def getStructRef(
    temputs: TemputsBox,
    structTemplata: StructTemplata,
    templateArgs: List[ITemplata]):
  (StructRef2) = {
    val StructTemplata(env, structA) = structTemplata
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = structA.name
    val structTemplateName = NameTranslator.translateCitizenName(structA.name)
    val structLastName = structTemplateName.makeCitizenName(templateArgs)
    val fullName = env.fullName.addStep(structLastName)

    temputs.structDeclared(fullName) match {
      case Some(structRef2) => {
        (structRef2)
      }
      case None => {
        // not sure if this is okay or not, do we allow this?
        if (templateArgs.size != structA.identifyingRunes.size) {
          vfail("wat?")
        }
        val temporaryStructRef = StructRef2(fullName)
        temputs.declareStruct(temporaryStructRef)

        structA.maybePredictedMutability match {
          case None =>
          case Some(predictedMutability) => temputs.declareStructMutability(temporaryStructRef, Conversions.evaluateMutability(predictedMutability))
        }
        val result =
          InferTemplar.inferFromExplicitTemplateArgs(
            env,
            temputs,
            structA.identifyingRunes,
            structA.rules,
            structA.typeByRune,
            structA.localRunes,
            List(),
            None,
            templateArgs)

        val inferences =
          result match {
            case isf @ InferSolveFailure(_, _, _, _, _, _) => {
              vfail("Couldnt figure out template args! Cause:\n" + isf)
            }
            case InferSolveSuccess(i) => i
          }

        structA.maybePredictedMutability match {
          case None => {
            val MutabilityTemplata(mutability) = inferences.templatasByRune(NameTranslator.translateRune(structA.mutabilityRune))
            temputs.declareStructMutability(temporaryStructRef, mutability)
          }
          case Some(_) =>
        }

        StructTemplarMiddle.getStructRef(env, temputs, structA, inferences.templatasByRune)
      }
    }
  }

  def getInterfaceRef(
    temputs: TemputsBox,
    interfaceTemplata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (InterfaceRef2) = {
    val InterfaceTemplata(env, interfaceS) = interfaceTemplata
    val TopLevelCitizenDeclarationNameA(humanName, codeLocation) = interfaceS.name
    val interfaceTemplateName = NameTranslator.translateCitizenName(interfaceS.name)
    val interfaceLastName = interfaceTemplateName.makeCitizenName(templateArgs)
    val fullName = env.fullName.addStep(interfaceLastName)

    temputs.interfaceDeclared(fullName) match {
      case Some(interfaceRef2) => {
        (interfaceRef2)
      }
      case None => {
        // not sure if this is okay or not, do we allow this?
        if (templateArgs.size != interfaceS.identifyingRunes.size) {
          vfail("wat?")
        }
        val temporaryInterfaceRef = InterfaceRef2(fullName)
        temputs.declareInterface(temporaryInterfaceRef)


        interfaceS.maybePredictedMutability match {
          case None =>
          case Some(predictedMutability) => temputs.declareInterfaceMutability(temporaryInterfaceRef, Conversions.evaluateMutability(predictedMutability))
        }

        val result =
          InferTemplar.inferFromExplicitTemplateArgs(
            env,
            temputs,
            interfaceS.identifyingRunes,
            interfaceS.rules,
            interfaceS.typeByRune,
            interfaceS.localRunes,
            List(),
            None,
            templateArgs)
        val inferences =
          result match {
            case isf @ InferSolveFailure(_, _, _, _, _, _) => {
              vfail("Couldnt figure out template args! Cause:\n" + isf)
            }
            case InferSolveSuccess(i) => i
          }


        interfaceS.maybePredictedMutability match {
          case None => {
            val MutabilityTemplata(mutability) = inferences.templatasByRune(NameTranslator.translateRune(interfaceS.mutabilityRune))
            temputs.declareInterfaceMutability(temporaryInterfaceRef, mutability)
          }
          case Some(_) =>
        }

        StructTemplarMiddle.getInterfaceRef(env, temputs, interfaceS, inferences.templatasByRune)
      }
    }
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: TemputsBox,
    name: LambdaNameA,
    functionS: FunctionA,
    members: List[StructMember2]):
  (StructRef2, Mutability, FunctionTemplata) = {
    StructTemplarMiddle.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnerstruct(env: NamespaceEnvironment[IName2], temputs: TemputsBox, memberTypes2: List[Coord], name: ICitizenName2):
  (StructRef2, Mutability) = {
    StructTemplarMiddle.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  def makeAnonymousSubstruct(
    interfaceEnv: IEnvironment,
    temputs: TemputsBox,
    interfaceRef: InterfaceRef2,
    substructName: FullName2[AnonymousSubstructName2]):
  (StructRef2, Mutability) = {
    StructTemplarMiddle.makeAnonymousSubstruct(
      interfaceEnv,
      temputs,
      interfaceRef,
      substructName)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: TemputsBox,
    prototype: Prototype2,
    structFullName: FullName2[ICitizenName2]):
  StructRef2 = {
    StructTemplarMiddle.prototypeToAnonymousStruct(
      outerEnv, temputs, prototype, structFullName)
  }
}
