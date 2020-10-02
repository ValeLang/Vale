package net.verdagon.vale.templar.citizen

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.function.FunctionTemplar
import net.verdagon.vale.templar.infer.infer.{InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.{IProfiler, vfail, vimpl, vwat}

import scala.collection.immutable.List

class StructTemplarTemplateArgsLayer(
    opts: TemplarOptions,
    profiler: IProfiler,
    newTemplataStore: () => TemplatasStore,
    inferTemplar: InferTemplar,
    ancestorHelper: AncestorHelper,
    delegate: IStructTemplarDelegate) {
  val middle = new StructTemplarMiddle(opts, profiler, newTemplataStore, ancestorHelper, delegate)

  def addBuiltInStructs(env: NamespaceEnvironment[IName2], temputs: Temputs): Unit = {
    middle.addBuiltInStructs(env, temputs)
  }

  def makeStructConstructor(
    temputs: Temputs,
    maybeConstructorOriginFunctionA: Option[FunctionA],
    structDef: StructDefinition2,
    constructorFullName: FullName2[IFunctionName2]):
  FunctionHeader2 = {
    middle.makeStructConstructor(temputs, maybeConstructorOriginFunctionA, structDef, constructorFullName)
  }

  def getStructRef(
    temputs: Temputs,
    callRange: RangeS,
    structTemplata: StructTemplata,
    templateArgs: List[ITemplata]):
  (StructRef2) = {
    profiler.newProfile("getStructRef", structTemplata.debugString + "<" + templateArgs.map(_.toString).mkString(", ") + ">", () => {
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
            inferTemplar.inferFromExplicitTemplateArgs(
              env,
              temputs,
              structA.identifyingRunes,
              structA.rules,
              structA.typeByRune,
              structA.localRunes,
              List(),
              None,
              callRange,
              templateArgs)

          val inferences =
            result match {
              case isf@InferSolveFailure(_, _, _, _, _, _, _) => {
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

          middle.getStructRef(env, temputs, callRange, structA, inferences.templatasByRune)
        }
      }
    })
  }

  def getInterfaceRef(
    temputs: Temputs,
    callRange: RangeS,
    interfaceTemplata: InterfaceTemplata,
    templateArgs: List[ITemplata]):
  (InterfaceRef2) = {
    profiler.newProfile("getInterfaceRef", interfaceTemplata.debugString + "<" + templateArgs.map(_.toString).mkString(", ") + ">", () => {
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
            inferTemplar.inferFromExplicitTemplateArgs(
              env,
              temputs,
              interfaceS.identifyingRunes,
              interfaceS.rules,
              interfaceS.typeByRune,
              interfaceS.localRunes,
              List(),
              None,
              callRange,
              templateArgs)
          val inferences =
            result match {
              case isf@InferSolveFailure(_, _, _, _, _, _, _) => {
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

          middle.getInterfaceRef(env, temputs, callRange, interfaceS, inferences.templatasByRune)
        }
      }
    })
  }

  // Makes a struct to back a closure
  def makeClosureUnderstruct(
    containingFunctionEnv: IEnvironment,
    temputs: Temputs,
    name: LambdaNameA,
    functionS: FunctionA,
    members: List[StructMember2]):
  (StructRef2, Mutability, FunctionTemplata) = {
    middle.makeClosureUnderstruct(containingFunctionEnv, temputs, name, functionS, members)
  }

  // Makes a struct to back a pack or tuple
  def makeSeqOrPackUnerstruct(env: NamespaceEnvironment[IName2], temputs: Temputs, memberTypes2: List[Coord], name: ICitizenName2):
  (StructRef2, Mutability) = {
    middle.makeSeqOrPackUnderstruct(env, temputs, memberTypes2, name)
  }

  // Makes an anonymous substruct of the given interface, with the given lambdas as its members.
  def makeAnonymousSubstruct(
    interfaceEnv: IEnvironment,
    temputs: Temputs,
    range: RangeS,
    interfaceRef: InterfaceRef2,
    substructName: FullName2[AnonymousSubstructName2]):
  (StructRef2, Mutability) = {
    middle.makeAnonymousSubstruct(
      interfaceEnv,
      temputs,
      range,
      interfaceRef,
      substructName)
  }

  // Makes an anonymous substruct of the given interface, which just forwards its method to the given prototype.
  def prototypeToAnonymousStruct(
    outerEnv: IEnvironment,
    temputs: Temputs,
    prototype: Prototype2,
    structFullName: FullName2[ICitizenName2]):
  StructRef2 = {
    middle.prototypeToAnonymousStruct(
      outerEnv, temputs, prototype, structFullName)
  }
}
