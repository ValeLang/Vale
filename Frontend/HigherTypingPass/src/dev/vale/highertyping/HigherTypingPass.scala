package dev.vale.highertyping

import dev.vale
import dev.vale.highertyping.HigherTypingPass.explicifyLookups
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.{CodeLocationS, Err, FileCoordinateMap, IPackageResolver, Interner, Keywords, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, RangeS, Result, SourceCodeUtils, StrI, highertyping, vassert, vassertSome, vcurious, vfail, vimpl, vregionmut, vwat}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, OwnP}
import dev.vale.postparsing.rules.{IRulexSR, RuleScout}
import dev.vale.postparsing._
import dev.vale.postparsing.RuneTypeSolver
import dev.vale.postparsing.rules._
import dev.vale.solver.RuleError

import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class Astrouts(
  codeLocationToMaybeType: mutable.HashMap[CodeLocationS, Option[ITemplataType]],
  codeLocationToStruct: mutable.HashMap[CodeLocationS, StructA],
  codeLocationToInterface: mutable.HashMap[CodeLocationS, InterfaceA])

// Environments dont have an AbsoluteName, because an environment can span multiple
// files.
case class EnvironmentA(
    maybeName: Option[INameS],
    maybeParentEnv: Option[EnvironmentA],
    codeMap: PackageCoordinateMap[ProgramS],
    runeToType: Map[IRuneS, ITemplataType]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  val structsS: Vector[StructS] = codeMap.packageCoordToContents.values.flatMap(_.structs).toVector
  val interfacesS: Vector[InterfaceS] = codeMap.packageCoordToContents.values.flatMap(_.interfaces).toVector
  val implsS: Vector[ImplS] = codeMap.packageCoordToContents.values.flatMap(_.impls).toVector
  val functionsS: Vector[FunctionS] = codeMap.packageCoordToContents.values.flatMap(_.implementedFunctions).toVector
  val exportsS: Vector[ExportAsS] = codeMap.packageCoordToContents.values.flatMap(_.exports).toVector
  val imports: Vector[ImportS] = codeMap.packageCoordToContents.values.flatMap(_.imports).toVector

  def addRunes(newruneToType: Map[IRuneS, ITemplataType]): EnvironmentA = {
    EnvironmentA(maybeName, maybeParentEnv, codeMap, runeToType ++ newruneToType)
  }
}

object HigherTypingPass {
  def explicifyLookups(
    // We take in this instead of an EnvironmentA because the typing pass calls this method too.
    env: IRuneTypeSolverEnv,
    runeAToType: mutable.HashMap[IRuneS, ITemplataType],
    ruleBuilder: ArrayBuffer[IRulexSR],
    allRulesWithImplicitlyCoercingLookupsS: Vector[IRulexSR]):
  Result[Unit, IRuneTypingLookupFailedError] = {
    // Only two rules' results can be coerced: LookupSR and CallSR.
    // Let's look for those and rewrite them to put an explicit coercion in there.
    allRulesWithImplicitlyCoercingLookupsS.foreach({
      case rule @ MaybeCoercingCallSR(range, resultRune, templateRune, args) => {
        val expectedType = vassertSome(runeAToType.get(resultRune.rune))
        val actualType =
          vassertSome(runeAToType.get(templateRune.rune)) match {
            case TemplateTemplataType(_, returnType) => returnType
            case _ => vwat()
          }
        (actualType, expectedType) match {
          case (x, y) if x == y => {
            ruleBuilder += rule
          }
          case (KindTemplataType(), CoordTemplataType()) => {
            val kindRune = RuneUsage(range, ImplicitCoercionKindRuneS(range, resultRune.rune))
            runeAToType.put(kindRune.rune, KindTemplataType())
            ruleBuilder += MaybeCoercingCallSR(range, kindRune, templateRune, args)
            ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
          }
          case _ => vimpl()
        }
      }
      case rule@MaybeCoercingLookupSR(range, resultRune, name) => {
        val desiredType = vassertSome(runeAToType.get(resultRune.rune))
        val actualLookupResult =
          env.lookup(range, name) match {
            case Err(e) => return Err(e)
            case Ok(x) => x
          }

        actualLookupResult match {
          case PrimitiveRuneTypeSolverLookupResult(tyype) => {
            desiredType match {
              case CoordTemplataType() => {
                coerceKindLookupToCoord(
                  runeAToType, ruleBuilder, range, resultRune, name)
              }
              case KindTemplataType() => {
                ruleBuilder += rule
              }
              case TemplateTemplataType(paramTypes, returnType) => {
                vassert(paramTypes.nonEmpty) // impl, if it's empty we might need to do some coercing.
                ruleBuilder += LookupSR(range, resultRune, name)
              }
              case _ => return Err(FoundPrimitiveDidntMatchExpectedType(List(range), desiredType, tyype))
            }
          }
          case CitizenRuneTypeSolverLookupResult(tyype, genericParams) => {
            desiredType match {
              case KindTemplataType() => {
                coerceKindTemplateLookupToKind(
                  runeAToType, ruleBuilder, range, resultRune, name, tyype)
              }
              case CoordTemplataType() => {
                coerceKindTemplateLookupToCoord(
                  runeAToType, ruleBuilder, range, resultRune, name, tyype)
              }
              case TemplateTemplataType(paramTypes, returnType) => {
                vassert(paramTypes.nonEmpty) // impl, if it's empty we might need to do some coercing.
                ruleBuilder += LookupSR(range, resultRune, name)
              }
              case _ => return Err(FoundTemplataDidntMatchExpectedTypeA(List(range), desiredType, tyype))
            }
          }
          case TemplataLookupResult(actualType) => {
            (actualType, desiredType) match {
              case (x, y) if x == y => {
                ruleBuilder += rule
              }
              case (KindTemplataType(), CoordTemplataType()) => {
                coerceKindLookupToCoord(
                  runeAToType, ruleBuilder, range, resultRune, name)
              }
              case (actualTemplateType @ TemplateTemplataType(_, KindTemplataType()), KindTemplataType()) => {
                coerceKindTemplateLookupToKind(
                  runeAToType, ruleBuilder, range, resultRune, name, actualTemplateType)
              }
              case (actualTemplateType @ TemplateTemplataType(_, KindTemplataType()), CoordTemplataType()) => {
                coerceKindTemplateLookupToCoord(
                  runeAToType, ruleBuilder, range, resultRune, name, actualTemplateType)
              }
              case _ => {
                throw CompileErrorExceptionA(
                  highertyping.RangedInternalErrorA(
                    range, "Unexpected coercion from " + actualType + " to " + desiredType))
              }
            }
          }
        }
      }
      case rule => {
        ruleBuilder += rule
      }
    })
    Ok(())
  }

  private def coerceKindLookupToCoord(
    runeAToType: mutable.HashMap[IRuneS, ITemplataType],
    ruleBuilder: ArrayBuffer[IRulexSR],
    range: RangeS,
    resultRune: RuneUsage,
    name: IImpreciseNameS
  ) = {
    val kindRune = RuneUsage(range, ImplicitCoercionKindRuneS(range, resultRune.rune))
    runeAToType.put(kindRune.rune, KindTemplataType())
    ruleBuilder += MaybeCoercingLookupSR(range, kindRune, name)
    ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
  }

  private def coerceKindTemplateLookupToKind(
    runeAToType: mutable.HashMap[IRuneS, ITemplataType],
    ruleBuilder: ArrayBuffer[IRulexSR],
    range: RangeS,
    resultRune: RuneUsage,
    name: IImpreciseNameS,
    actualTemplateType: TemplateTemplataType):
  Unit = {
    val templateRune = RuneUsage(range, ImplicitCoercionTemplateRuneS(range, resultRune.rune))
    runeAToType.put(templateRune.rune, actualTemplateType)
    ruleBuilder += MaybeCoercingLookupSR(range, templateRune, name)
    ruleBuilder += MaybeCoercingCallSR(range, resultRune, templateRune, Vector())
  }

  private def coerceKindTemplateLookupToCoord(
    runeAToType: mutable.HashMap[IRuneS, ITemplataType],
    ruleBuilder: ArrayBuffer[IRulexSR],
    range: RangeS,
    resultRune: RuneUsage,
    name: IImpreciseNameS,
    ttt: TemplateTemplataType):
  Unit = {
    val templateRune = RuneUsage(range, ImplicitCoercionTemplateRuneS(range, resultRune.rune))
    val kindRune = RuneUsage(range, ImplicitCoercionKindRuneS(range, resultRune.rune))
    runeAToType.put(templateRune.rune, ttt)
    runeAToType.put(kindRune.rune, KindTemplataType())
    ruleBuilder += MaybeCoercingLookupSR(range, templateRune, name)
    ruleBuilder += MaybeCoercingCallSR(range, kindRune, templateRune, Vector())
    ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
  }
}

class HigherTypingPass(globalOptions: GlobalOptions, interner: Interner, keywords: Keywords) {
  val primitives =
    Map(
      keywords.int -> KindTemplataType(),
      keywords.i64 -> KindTemplataType(),
      keywords.str -> KindTemplataType(),
      keywords.bool -> KindTemplataType(),
      keywords.float -> KindTemplataType(),
      keywords.void -> KindTemplataType(),
      keywords.__Never -> KindTemplataType(),
      keywords.Array -> TemplateTemplataType(Vector(MutabilityTemplataType(), CoordTemplataType()), KindTemplataType()),
      keywords.StaticArray -> TemplateTemplataType(Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()), KindTemplataType())
      // Put in with regions
      // keywords.Array -> TemplateTemplataType(Vector(MutabilityTemplataType(), CoordTemplataType(), RegionTemplataType()), KindTemplataType()),
      // keywords.StaticArray -> TemplateTemplataType(Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType(), RegionTemplataType()), KindTemplataType())
      )

  vregionmut() // Change the above Array/StaticArray templata types to have regions in them



  // Returns whether the imprecise name could be referring to the absolute name.
  // See MINAAN for what we're doing here.
  def impreciseNameMatchesAbsoluteName(
    needleImpreciseNameS: IImpreciseNameS,
    absoluteName: INameS):
  Boolean = {
    (needleImpreciseNameS, absoluteName) match {
      case (CodeNameS(humanNameB), TopLevelCitizenDeclarationNameS(humanNameA, _)) => humanNameA == humanNameB
      case (RuneNameS(a), _) => false
      case other => vimpl(other)
    }
  }

  def lookupTypes(
    astrouts: Astrouts,
    env: EnvironmentA,
    needleImpreciseNameS: IImpreciseNameS):
  Vector[IRuneTypeSolverLookupResult] = {
    // See MINAAN for what we're doing here.

    // When the scout comes across a lambda, it doesn't put the e.g. main:lam1:__Closure struct into
    // the environment or anything, it lets typingpass to do that (because typingpass knows the actual types).
    // However, this means that when the lambda function gets to the higher typer, the higher typer doesn't
    // know what to do with it.
    needleImpreciseNameS match {
      case CodeNameS(_) =>
      case RuneNameS(_) =>
    }

    needleImpreciseNameS match {
      case CodeNameS(nameStr) => {
        primitives.get(nameStr) match {
          case Some(x) => return Vector(PrimitiveRuneTypeSolverLookupResult(x))
          case None =>
        }
      }
      case _ =>
    }

    needleImpreciseNameS match {
      case RuneNameS(rune) => {
        env.runeToType.get(rune) match {
          case Some(tyype) => return Vector(TemplataLookupResult(tyype))
          case None =>
        }
      }
      case _ =>
    }

    val nearStructTypes =
      env.structsS
        .filter(interface => impreciseNameMatchesAbsoluteName(needleImpreciseNameS, interface.name))
        .map(x => CitizenRuneTypeSolverLookupResult(x.tyype, x.genericParams))
    val nearInterfaceTypes =
      env.interfacesS
        .filter(interface => impreciseNameMatchesAbsoluteName(needleImpreciseNameS, interface.name))
        .map(x => CitizenRuneTypeSolverLookupResult(x.tyype, x.genericParams))
    val result = nearStructTypes ++ nearInterfaceTypes

    if (result.nonEmpty) {
      result
    } else {
      env.maybeParentEnv match {
        case None => Vector.empty
        case Some(parentEnv) => lookupTypes(astrouts, parentEnv, needleImpreciseNameS)
      }
    }
  }

  def lookupType(
    astrouts: Astrouts,
    env: EnvironmentA,
    range: RangeS,
    name: IImpreciseNameS):
  Result[IRuneTypeSolverLookupResult, ILookupFailedErrorA] = {
    lookupTypes(astrouts, env, name).distinct match {
      case Vector() => Err(CouldntFindTypeA(range, name))
      case Vector(only) => Ok(only)
      case others => Err(TooManyMatchingTypesA(range, name))
    }
  }

  def translateStruct(
    astrouts: Astrouts,
    env: EnvironmentA,
    structS: StructS):
  StructA = {
    val StructS(rangeS, nameS, attributesS, weakable, genericParametersS, mutabilityRuneS, maybePredictedMutability, tyype, headerRuneToExplicitType, headerPredictedRuneToType, headerRulesWithImplicitlyCoercingLookupsS, membersRuneToExplicitType, membersPredictedRuneToType, memberRulesWithImplicitlyCoercingLookupsS, members) = structS

    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }

    astrouts.codeLocationToStruct.get(rangeS.begin) match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(rangeS.begin) match {
      // Weird because this means we already evaluated it, in which case we should have hit the above return
      case Some(Some(_)) => vwat()
      case Some(None) => {
        throw CompileErrorExceptionA(highertyping.RangedInternalErrorA(rangeS, "Cycle in determining struct type!"))
      }
      case None =>
    }
    astrouts.codeLocationToMaybeType.put(rangeS.begin, None)

    val allRulesWithImplicitlyCoercingLookupsS =
      headerRulesWithImplicitlyCoercingLookupsS ++ memberRulesWithImplicitlyCoercingLookupsS
    val allRuneToExplicitType = headerRuneToExplicitType ++ membersRuneToExplicitType
    val runeAToTypeWithImplicitlyCoercingLookupsS =
      calculateRuneTypes(
        astrouts,
        rangeS,
        genericParametersS.map(_.rune.rune),
        allRuneToExplicitType,
        Vector(),
        allRulesWithImplicitlyCoercingLookupsS,
        env)

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.

    val headerRulesBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(
      runeTypingEnv, runeAToType, headerRulesBuilder, headerRulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }
    val headerRulesExplicitS = headerRulesBuilder.toVector

    val memberRulesBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(
      runeTypingEnv, runeAToType, memberRulesBuilder, memberRulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }
    val memberRulesExplicitS = memberRulesBuilder.toVector

    val runesInHeader: Set[IRuneS] =
      (genericParametersS.map(_.rune.rune) ++
        genericParametersS.flatMap(_.default).flatMap(_.rules.map(_.runeUsages.map(_.rune))).flatten ++
        headerRulesExplicitS.flatMap(_.runeUsages.map(_.rune))).toSet
    val headerRuneAToType = runeAToType.toMap.filter(x => runesInHeader.contains(x._1))
    val membersRuneAToType = runeAToType.toMap.filter(x => !runesInHeader.contains(x._1))

    // Shouldnt fail because we got a complete solve earlier
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    val structA =
      highertyping.StructA(
        rangeS,
        nameS,
        attributesS,
        weakable,
        mutabilityRuneS,
        maybePredictedMutability,
        tyype,
        genericParametersS,
        headerRuneAToType,
        headerRulesExplicitS,
        membersRuneAToType,
        memberRulesExplicitS,
        members)
    astrouts.codeLocationToStruct.put(rangeS.begin, structA)
    structA
  }

  def getInterfaceType(
    astrouts: Astrouts,
    env: EnvironmentA,
    interfaceS: InterfaceS):
  ITemplataType = {
    interfaceS.tyype
  }

  def translateInterface(astrouts: Astrouts,  env: EnvironmentA, interfaceS: InterfaceS): InterfaceA = {
    val InterfaceS(rangeS, nameS, attributesS, weakable, genericParametersS, runeToExplicitType, mutabilityRuneS, maybePredictedMutability, predictedRuneToType, tyype, rulesWithImplicitlyCoercingLookupsS, internalMethodsS) = interfaceS

    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }

    astrouts.codeLocationToInterface.get(rangeS.begin) match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(rangeS.begin) match {
      // Weird because this means we already evaluated it, in which case we should have hit the above return
      case Some(Some(_)) => vwat()
      case Some(None) => {
        throw CompileErrorExceptionA(highertyping.RangedInternalErrorA(rangeS, "Cycle in determining interface type!"))
      }
      case None =>
    }
    astrouts.codeLocationToMaybeType.put(rangeS.begin, None)

    val runeAToTypeWithImplicitlyCoercingLookups =
      calculateRuneTypes(astrouts, rangeS, genericParametersS.map(_.rune.rune), runeToExplicitType, Vector(), rulesWithImplicitlyCoercingLookupsS, env)

    // getOrDie because we should have gotten a complete solve
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    val methodsEnv =
      env
        .addRunes(runeAToTypeWithImplicitlyCoercingLookups)
    val internalMethodsA =
      internalMethodsS.map(method => {
        translateFunction(astrouts, methodsEnv, method)
      })


    val runeAToTypeWithImplicitlyCoercingLookupsS =
      calculateRuneTypes(
        astrouts, rangeS, genericParametersS.map(_.rune.rune), runeToExplicitType, Vector(), rulesWithImplicitlyCoercingLookupsS, env)

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.
    val ruleBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(
      runeTypingEnv, runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }

    val interfaceA =
      highertyping.InterfaceA(
        rangeS,
        nameS,
        attributesS,
        weakable,
        mutabilityRuneS,
        maybePredictedMutability,
        tyype,
        //        knowableRunesS,
        genericParametersS,
        //        localRunesS,
        //        conclusions,
        runeAToType.toMap,
        ruleBuilder.toVector,
        internalMethodsA)
    astrouts.codeLocationToInterface.put(rangeS.begin, interfaceA)
    interfaceA
  }

  def translateImpl(astrouts: Astrouts,  env: EnvironmentA, implS: ImplS): ImplA = {
    val ImplS(rangeS, nameS, identifyingRunesS, rulesWithImplicitlyCoercingLookupsS, runeToExplicitType, tyype, structKindRuneS, subCitizenImpreciseName, interfaceKindRuneS, superInterfaceImpreciseName) = implS

    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }

    val runeAToTypeWithImplicitlyCoercingLookupsS =
      calculateRuneTypes(
        astrouts,
        rangeS,
        identifyingRunesS.map(_.rune.rune),
        runeToExplicitType + (structKindRuneS.rune -> KindTemplataType(), interfaceKindRuneS.rune -> KindTemplataType()),
        Vector(),
        rulesWithImplicitlyCoercingLookupsS,
        env)

    // getOrDie because we should have gotten a complete solve
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.
    val ruleBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(runeTypingEnv, runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }

    highertyping.ImplA(
      rangeS,
      nameS,
      identifyingRunesS,
      ruleBuilder.toVector,
      runeAToType.toMap,
      structKindRuneS,
      subCitizenImpreciseName,
      interfaceKindRuneS,
      superInterfaceImpreciseName)
  }

  def translateExport(astrouts: Astrouts,  env: EnvironmentA, exportS: ExportAsS): ExportAsA = {
    val ExportAsS(rangeS, rulesWithImplicitlyCoercingLookupsS, exportName, rune, exportedName) = exportS

    val defaultRegionRune = ExportDefaultRegionRuneS(exportName)

    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }

    val runeAToTypeWithImplicitlyCoercingLookupsS =
      calculateRuneTypes(
        astrouts,
        rangeS,
        Vector(),
        Map(rune.rune -> KindTemplataType()),
        Vector(),
        rulesWithImplicitlyCoercingLookupsS,
        env)

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.
    val ruleBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(runeTypingEnv, runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }

    ExportAsA(
      rangeS,
      exportedName,
      ruleBuilder.toVector,
      runeAToType.toMap,
      rune)
  }

  def translateFunction(astrouts: Astrouts, env: EnvironmentA, functionS: FunctionS): FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, identifyingRunesS, runeToExplicitType, tyype, paramsS, maybeRetCoordRune, rulesWithImplicitlyCoercingLookupsS, bodyS) = functionS

    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }

    val runeAToTypeWithImplicitlyCoercingLookupsS =
      calculateRuneTypes(
        astrouts, rangeS, identifyingRunesS.map(_.rune.rune), runeToExplicitType, paramsS, rulesWithImplicitlyCoercingLookupsS, env)

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.
    val ruleBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(runeTypingEnv, runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS) match {
      case Err(RuneTypingTooManyMatchingTypes(range, name)) => throw CompileErrorExceptionA(TooManyMatchingTypesA(range, name))
      case Err(RuneTypingCouldntFindType(range, name)) => throw CompileErrorExceptionA(CouldntFindTypeA(range, name))
      case Ok(()) =>
    }

    highertyping.FunctionA(
      rangeS,
      nameS,
      attributesS ++ Vector(UserFunctionS),
      tyype,
      identifyingRunesS,
      runeAToType.toMap ++ env.runeToType,
      paramsS,
      maybeRetCoordRune,
      ruleBuilder.toVector,
      bodyS)
  }

  private def calculateRuneTypes(
    astrouts: Astrouts,
    rangeS: RangeS,
    identifyingRunesS: Vector[IRuneS],
    runeToExplicitType: Map[IRuneS, ITemplataType],
    paramsS: Vector[ParameterS],
    rulesS: Vector[IRulexSR],
    env: EnvironmentA):
  Map[IRuneS, ITemplataType] = {
    val runeTypingEnv =
      new IRuneTypeSolverEnv {
        override def lookup(
          range: RangeS,
          name: IImpreciseNameS
        ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
          lookupType(astrouts, env, rangeS, name).mapError({
            case TooManyMatchingTypesA(range, name) => RuneTypingTooManyMatchingTypes(range, name)
            case CouldntFindTypeA(range, name) => RuneTypingCouldntFindType(range, name)
          })
        }
      }
    val runeSToPreKnownTypeA =
      runeToExplicitType ++
        paramsS.flatMap(_.pattern.coordRune.map(_.rune -> CoordTemplataType())).toMap
    val runeSToType =
      new RuneTypeSolver(interner).solve(
        globalOptions.sanityCheck,
        globalOptions.useOptimizedSolver,
        runeTypingEnv,
        List(rangeS),
        false, rulesS, identifyingRunesS, true, runeSToPreKnownTypeA) match {
        case Ok(t) => t
        case Err(e) => throw CompileErrorExceptionA(CouldntSolveRulesA(rangeS, e))
      }
    runeSToType
  }

  def translateProgram(
      codeMap: PackageCoordinateMap[ProgramS],
      primitives: Map[StrI, ITemplataType],
      suppliedFunctions: Vector[FunctionA],
      suppliedInterfaces: Vector[InterfaceA]):
  ProgramA = {
    val env = EnvironmentA(None, None, codeMap, Map())

    // If something is absence from the map, we haven't started evaluating it yet
    // If there is a None in the map, we started evaluating it
    // If there is a Some in the map, we know the type
    // If we are asked to evaluate something but there is already a None in the map, then we are
    // caught in a cycle.
    val astrouts =
      Astrouts(
        mutable.HashMap[CodeLocationS, Option[ITemplataType]](),
        mutable.HashMap[CodeLocationS, StructA](),
        mutable.HashMap[CodeLocationS, InterfaceA]())

    val structsA = env.structsS.map(translateStruct(astrouts, env, _))

    val interfacesA = env.interfacesS.map(translateInterface(astrouts, env, _))

    val implsA = env.implsS.map(translateImpl(astrouts, env, _))

    val functionsA = env.functionsS.map(translateFunction(astrouts, env, _))

    val exportsA = env.exportsS.map(translateExport(astrouts, env, _))

    ProgramA(structsA, suppliedInterfaces ++ interfacesA, implsA, suppliedFunctions ++ functionsA, exportsA)
  }

  def runPass(separateProgramsS: FileCoordinateMap[ProgramS]):
  Either[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    Profiler.frame(() => {
      val mergedProgramS =
        PackageCoordinateMap[ProgramS]()
      separateProgramsS.packageCoordToFileCoords.foreach({ case (packageCoord, fileCoords) =>
        val programsS = fileCoords.map(separateProgramsS.fileCoordToContents)
        mergedProgramS.put(
          packageCoord,
          ProgramS(
            programsS.flatMap(_.structs).toVector,
            programsS.flatMap(_.interfaces).toVector,
            programsS.flatMap(_.impls).toVector,
            programsS.flatMap(_.implementedFunctions).toVector,
            programsS.flatMap(_.exports).toVector,
            programsS.flatMap(_.imports).toVector))
      })

      //    val orderedModules = orderModules(mergedProgramS)

      try {
        val suppliedFunctions = Vector()
        val suppliedInterfaces = Vector()
        val ProgramA(structsA, interfacesA, implsA, functionsA, exportsA) =
          translateProgram(
            mergedProgramS, primitives, suppliedFunctions, suppliedInterfaces)

        val packageToStructsA = structsA.groupBy(_.range.begin.file.packageCoordinate)
        val packageToInterfacesA = interfacesA.groupBy(_.name.range.begin.file.packageCoordinate)
        val packageToFunctionsA = functionsA.groupBy(_.name.packageCoordinate)
        val packageToImplsA = implsA.groupBy(_.name.packageCoordinate)
        val packageToExportsA = exportsA.groupBy(_.range.file.packageCoordinate)

        val allPackages =
          packageToStructsA.keySet ++
            packageToInterfacesA.keySet ++
            packageToFunctionsA.keySet ++
            packageToImplsA.keySet ++
            packageToExportsA.keySet
        val packageToContents = mutable.HashMap[PackageCoordinate, ProgramA]()
        allPackages.foreach(paackage => {
          val contents =
            ProgramA(
              packageToStructsA.getOrElse(paackage, Vector.empty),
              packageToInterfacesA.getOrElse(paackage, Vector.empty),
              packageToImplsA.getOrElse(paackage, Vector.empty),
              packageToFunctionsA.getOrElse(paackage, Vector.empty),
              packageToExportsA.getOrElse(paackage, Vector.empty))
          packageToContents.put(paackage, contents)
        })
        Left(vale.PackageCoordinateMap(packageToContents))
      } catch {
        case CompileErrorExceptionA(err) => {
          Right(err)
        }
      }
    })
  }
}

class HigherTypingCompilation(
  globalOptions: GlobalOptions,
  val interner: Interner,
  val keywords: Keywords,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var scoutCompilation = new ScoutCompilation(globalOptions, interner, keywords, packagesToBuild, packageToContentsResolver)
  var astroutsCache: Option[PackageCoordinateMap[ProgramA]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[RangeL])], FailedParse] = scoutCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = scoutCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    astroutsCache match {
      case Some(astrouts) => Ok(astrouts)
      case None => {
        new HigherTypingPass(globalOptions, interner, keywords).runPass(scoutCompilation.expectScoutput()) match {
          case Right(err) => Err(err)
          case Left(astrouts) => {
            astroutsCache = Some(astrouts)
            Ok(astrouts)
          }
        }
      }
    }
  }
  def expectAstrouts(): PackageCoordinateMap[ProgramA] = {
    getAstrouts() match {
      case Ok(x) => x
      case Err(e) => {
        val codeMap = scoutCompilation.getCodeMap().getOrDie()
        vfail(
          HigherTypingErrorHumanizer.humanize(
            SourceCodeUtils.humanizePos(codeMap, _),
            SourceCodeUtils.linesBetween(codeMap, _, _),
            SourceCodeUtils.lineRangeContaining(codeMap, _),
            SourceCodeUtils.lineContaining(codeMap, _),
            e))
      }
    }
  }
}
