package net.verdagon.vale.templar

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.ITemplexS
import net.verdagon.vale.templar.OverloadTemplar.{ScoutExpectedFunctionFailure, ScoutExpectedFunctionSuccess}
import net.verdagon.vale.templar.citizen.{ImplTemplar, StructTemplar}
import net.verdagon.vale.templar.env.{IEnvironment, ILookupContext, TemplataLookupContext}
import net.verdagon.vale.templar.infer._
import net.verdagon.vale.templar.infer.infer.{IInferSolveResult, InferSolveFailure, InferSolveSuccess}
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.{vassertSome, vfail, vimpl}

import scala.collection.immutable.List

object InferTemplar {
  private def solve(
    env: IEnvironment,
    state: TemputsBox,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    directInputs: Map[IRuneA, ITemplata],
    paramAtoms: List[AtomAP],
    maybeParamInputs: Option[List[ParamFilter]],
    checkAllRunesPresent: Boolean,
  ): (IInferSolveResult) = {
    Inferer.solve[IEnvironment, TemputsBox](
      makeEvaluateDelegate(),
      env,
      state,
      translateRules(rules),
      typeByRune.map({ case (key, value) => NameTranslator.translateRune(key) -> value}),
      localRunes.map(NameTranslator.translateRune),
      directInputs.map({ case (key, value) => NameTranslator.translateRune(key) -> value}),
      paramAtoms,
      maybeParamInputs,
      checkAllRunesPresent)
  }

  private def makeEvaluateDelegate(): IInfererDelegate[IEnvironment, TemputsBox] = {
    new IInfererDelegate[IEnvironment, TemputsBox] {
      override def evaluateType(
        env: IEnvironment,
        temputs: TemputsBox,
        type1: ITemplexA
      ): (ITemplata) = {
        TemplataTemplar.evaluateTemplex(env, temputs, type1)
      }

      override def lookupMemberTypes(state: TemputsBox, kind: Kind, expectedNumMembers: Int): Option[List[Coord]] = {
        val underlyingStructRef2 =
          kind match {
            case sr @ StructRef2(_) => sr
            case TupleT2(_, underlyingStruct) => underlyingStruct
            case PackT2(_, underlyingStruct) => underlyingStruct
            case _ => return None
          }
        val structDef2 = state.lookupStruct(underlyingStructRef2)
        val structMemberTypes = structDef2.members.map(_.tyype.reference)
        Some(structMemberTypes)
      }

      override def getMutability(state: TemputsBox, kind: Kind): Mutability = {
        Templar.getMutability(state, kind)
      }

      override def lookupTemplata(env: IEnvironment, name: IName2): ITemplata = {
        // We can only ever lookup types by name in expression context,
        // otherwise we have no idea what List<Str> means; it could
        // mean a list of strings or a list of the Str(:Int)Str function.
        env.getNearestTemplataWithAbsoluteName2(name, Set[ILookupContext](TemplataLookupContext)) match {
          case None => vfail("Couldn't find anything with name: " + name)
          case Some(x) => x
        }
      }

      override def lookupTemplataImprecise(env: IEnvironment, name: IImpreciseNameStepA): ITemplata = {
        env.getNearestTemplataWithName(name, Set[ILookupContext](TemplataLookupContext)) match {
          case None => vfail("Couldn't find anything with name: " + name)
          case Some(x) => x
        }
      }

      override def getPackKind(env: IEnvironment, state: TemputsBox, members: List[Coord]): (PackT2, Mutability) = {
        PackTemplar.makePackType(env.globalEnv, state, members)
      }

      override def getArraySequenceKind(env: IEnvironment, state: TemputsBox, mutability: Mutability, size: Int, element: Coord): (ArraySequenceT2) = {
        ArrayTemplar.makeArraySequenceType(env, state, mutability, size, element)
      }

      override def evaluateInterfaceTemplata(
        state: TemputsBox,
        templata: InterfaceTemplata,
        templateArgs: List[ITemplata]):
      (Kind) = {
        StructTemplar.getInterfaceRef(state, templata, templateArgs)
      }

      override def evaluateStructTemplata(
        state: TemputsBox,
        templata: StructTemplata,
        templateArgs: List[ITemplata]):
      (Kind) = {
        StructTemplar.getStructRef(state, templata, templateArgs)
      }

      override def getAncestorInterfaceDistance(temputs: TemputsBox, descendantCitizenRef: CitizenRef2, ancestorInterfaceRef: InterfaceRef2):
      (Option[Int]) = {
        ImplTemplar.getAncestorInterfaceDistance(temputs, descendantCitizenRef, ancestorInterfaceRef)
      }

      override def getAncestorInterfaces(temputs: TemputsBox, descendantCitizenRef: CitizenRef2): (Set[InterfaceRef2]) = {
        ImplTemplar.getAncestorInterfaces(temputs, descendantCitizenRef)
      }

      override def getMemberCoords(state: TemputsBox, structRef: StructRef2): List[Coord] = {
        StructTemplar.getMemberCoords(state, structRef)
      }

      override def citizenIsFromTemplate(state: TemputsBox, citizen: CitizenRef2, template: ITemplata): (Boolean) = {
        StructTemplar.citizenIsFromTemplate(state, citizen, template)
      }


      override def getInterfaceTemplataType(it: InterfaceTemplata): ITemplataType = {
        it.originInterface.tyype
      }

      override def getStructTemplataType(st: StructTemplata): ITemplataType = {
        st.originStruct.tyype
      }

      override def structIsClosure(state: TemputsBox, structRef: StructRef2): Boolean = {
        val structDef = state.structDefsByRef(structRef)
        structDef.isClosure
      }

      // A simple interface is one that has only one method
      def getSimpleInterfaceMethod(state: TemputsBox, interfaceRef: InterfaceRef2): Prototype2 = {
        val interfaceDef2 = state.lookupInterface(interfaceRef)
        if (interfaceDef2.internalMethods.size != 1) {
          vfail("Interface is not simple!")
        }
        interfaceDef2.internalMethods.head.toPrototype
      }

      override def resolveExactSignature(env: IEnvironment, state: TemputsBox, name: String, coords: List[Coord]): Prototype2 = {
        OverloadTemplar.scoutExpectedFunctionForPrototype(env, state, GlobalFunctionFamilyNameA(name), List(), coords.map(ParamFilter(_, None)), List(), true) match {
          case sef @ ScoutExpectedFunctionFailure(humanName, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction) => {
            vfail(sef.toString)
          }
          case ScoutExpectedFunctionSuccess(prototype) => prototype
        }
      }
    }
  }

  // No incoming types needed (like manually specified template args, or argument coords from a call).
  // This is for when we want to figure out the types for an ordinary function like
  //   fn sum(a: Int, b: Int)Int { }
  // which, remember, actually *does* have rules:
  //   fn sum
  //   rules(#1 = Int, #2 = Int, #3 = Int)
  //   (a: #1, b: #2) #3 { ...}
  def inferOrdinaryRules(
    env0: IEnvironment,
    temputs: TemputsBox,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
  ): (Map[IRune2, ITemplata]) = {
    solve(env0, temputs, rules, typeByRune, localRunes, Map(), List(), None, true) match {
      case (InferSolveSuccess(inferences)) => {
        (inferences.templatasByRune)
      }
      case (isf @ InferSolveFailure(_, _, _, _, _, _)) => {
        vfail("Conflict in determining ordinary rules' runes: " + isf)
      }
    }
  }

  def inferFromExplicitTemplateArgs(
    env0: IEnvironment,
    temputs: TemputsBox,
    identifyingRunes: List[IRuneA],
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    patterns1: List[AtomAP],
    maybeRetRune: Option[IRuneA],
    explicits: List[ITemplata],
  ): (IInferSolveResult) = {
    if (identifyingRunes.size != explicits.size) {
      vfail("Wrong number of template args!")
    }

    solve(
      env0,
      temputs,
      rules,
      typeByRune,
      localRunes,
      identifyingRunes.zip(explicits).toMap,
      patterns1,
      None,
      true)
  }

  def inferFromArgCoords(
    env0: IEnvironment,
    temputs: TemputsBox,
    identifyingRunes: List[IRuneA],
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    patterns1: List[AtomAP],
    maybeRetRune: Option[IRuneA],
    alreadySpecifiedTemplateArgs: List[ITemplata],
    patternInputCoords: List[ParamFilter]
  ): (IInferSolveResult) = {

    solve(
      env0,
      temputs,
      rules,
      typeByRune,
      localRunes,
      // Note: this two things we're zipping are of different length, that's fine.
      identifyingRunes.zip(alreadySpecifiedTemplateArgs).toMap,
      patterns1,
      Some(patternInputCoords),
      true)
  }

  def translateRules(rs: List[IRulexAR]): List[IRulexTR] = {
    rs.map(translateRule)
  }

  def translateRule(rulexA: IRulexAR): IRulexTR = {
    rulexA match {
      case EqualsAR(left, right) => EqualsTR(translateRule(left), translateRule(right))
      case TemplexAR(templex) => TemplexTR(translateTemplex(templex))
      case ComponentsAR(tyype, componentsA) => ComponentsTR(tyype, componentsA.map(translateRule))
      case OrAR(possibilities) => OrTR(possibilities.map(translateRule))
      case CallAR(name, args, resultType) => CallTR(name, args.map(translateRule), resultType)
//      case CoordListAR(rules) => CoordListTR(rules.map(translateRule))
      case _ => vimpl()
    }
  }

  def translateTemplex(templexA: ITemplexA): ITemplexT = {
    templexA match {
      case RuneAT(rune, resultType) => RuneTT(NameTranslator.translateRune(rune), resultType)
      case NameAT(name, resultType) => NameTT(name, resultType)
      case OwnershipAT(ownership) => OwnershipTT(ownership)
      case OwnershippedAT(ownership, inner) => OwnershippedTT(ownership, translateTemplex(inner))
      case AbsoluteNameAT(name, resultType) => AbsoluteNameTT(name, resultType)
      case CallAT(template, args, resultType) => CallTT(translateTemplex(template), args.map(translateTemplex), resultType)
      case MutabilityAT(m) => MutabilityTT(m)
      case RepeaterSequenceAT(mutability, size, element, resultType) => RepeaterSequenceTT(translateTemplex(mutability), translateTemplex(size), translateTemplex(element), resultType)
//      case PackAT(members, resultType) => PackTT(members.map(translateTemplex), resultType)
      case IntAT(value) => IntTT(value)
      case StringAT(value) => StringTT(value)
      case CoordListAT(elements) => CoordListTT(elements.map(translateTemplex))
      case _ => vimpl(templexA.toString)
    }
  }
}
