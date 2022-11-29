package dev.vale.highertyping

import dev.vale
import dev.vale.highertyping.HigherTypingPass.explicifyLookups
import dev.vale.lexing.{FailedParse, RangeL}
import dev.vale.{CodeLocationS, Err, FileCoordinateMap, IPackageResolver, Interner, Keywords, Ok, PackageCoordinate, PackageCoordinateMap, Profiler, RangeS, Result, StrI, highertyping, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ast.{FileP, OwnP}
import dev.vale.postparsing.rules.{IRulexSR, RuleScout}
import dev.vale.postparsing._
import dev.vale.postparsing.RuneTypeSolver
import dev.vale.postparsing.rules._

import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class Astrouts(
  codeLocationToMaybeType: mutable.HashMap[CodeLocationS, Option[ITemplataType]],
  codeLocationToStruct: mutable.HashMap[CodeLocationS, StructA],
  codeLocationToInterface: mutable.HashMap[CodeLocationS, InterfaceA])

// Environments dont have an AbsoluteName, because an environment can span multiple
// files.
case class Environment(
    maybeName: Option[INameS],
    maybeParentEnv: Option[Environment],
    codeMap: PackageCoordinateMap[ProgramS],
    runeToType: Map[IRuneS, ITemplataType]) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  val structsS: Vector[StructS] = codeMap.packageCoordToContents.values.flatMap(_.structs).toVector
  val interfacesS: Vector[InterfaceS] = codeMap.packageCoordToContents.values.flatMap(_.interfaces).toVector
  val implsS: Vector[ImplS] = codeMap.packageCoordToContents.values.flatMap(_.impls).toVector
  val functionsS: Vector[FunctionS] = codeMap.packageCoordToContents.values.flatMap(_.implementedFunctions).toVector
  val exportsS: Vector[ExportAsS] = codeMap.packageCoordToContents.values.flatMap(_.exports).toVector
  val imports: Vector[ImportS] = codeMap.packageCoordToContents.values.flatMap(_.imports).toVector

  def addRunes(newruneToType: Map[IRuneS, ITemplataType]): Environment = {
    Environment(maybeName, maybeParentEnv, codeMap, runeToType ++ newruneToType)
  }
}

object HigherTypingPass {

  def explicifyLookups(
    env: (RangeS, IImpreciseNameS) => ITemplataType,
    runeAToType: mutable.HashMap[IRuneS, ITemplataType],
    ruleBuilder: ArrayBuffer[IRulexSR],
    allRulesWithImplicitlyCoercingLookupsS: Vector[IRulexSR]):
  Unit = {
    // Only two rules' results can be coerced: LookupSR and CallSR.
    // Let's look for those and rewrite them to put an explicit coercion in there.
    allRulesWithImplicitlyCoercingLookupsS.foreach({
      case rule @ CallSR(range, resultRune, templateRune, args) => {
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
            ruleBuilder += CallSR(range, kindRune, templateRune, args)
            ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
          }
          case _ => vimpl()
        }
      }
      case rule@LookupSR(range, resultRune, name) => {
        val expectedType = vassertSome(runeAToType.get(resultRune.rune))
        val actualType = env(range, name)
        (actualType, expectedType) match {
          case (x, y) if x == y => {
            ruleBuilder += rule
          }
          case (KindTemplataType(), CoordTemplataType()) => {
            val kindRune = RuneUsage(range, ImplicitCoercionKindRuneS(range, resultRune.rune))
            runeAToType.put(kindRune.rune, KindTemplataType())
            ruleBuilder += LookupSR(range, kindRune, name)
            ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
          }
          case (ttt @ TemplateTemplataType(_, KindTemplataType()), KindTemplataType()) => {
            val templateRune = RuneUsage(range, ImplicitCoercionTemplateRuneS(range, resultRune.rune))
            runeAToType.put(templateRune.rune, ttt)
            ruleBuilder += LookupSR(range, templateRune, name)
            ruleBuilder += CallSR(range, resultRune, templateRune, Vector())
          }
          case (ttt @ TemplateTemplataType(_, KindTemplataType()), CoordTemplataType()) => {
            val templateRune = RuneUsage(range, ImplicitCoercionTemplateRuneS(range, resultRune.rune))
            val kindRune = RuneUsage(range, ImplicitCoercionKindRuneS(range, resultRune.rune))
            runeAToType.put(templateRune.rune, ttt)
            runeAToType.put(kindRune.rune, KindTemplataType())
            ruleBuilder += LookupSR(range, templateRune, name)
            ruleBuilder += CallSR(range, kindRune, templateRune, Vector())
            ruleBuilder += CoerceToCoordSR(range, resultRune, kindRune)
          }
          case _ => {
            throw CompileErrorExceptionA(
              highertyping.RangedInternalErrorA(
                range, "Unexpected coercion from " + actualType + " to " + expectedType))
          }
        }
      }
      case rule => ruleBuilder += rule
    })
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
      keywords.StaticArray -> TemplateTemplataType(Vector(IntegerTemplataType(), MutabilityTemplataType(), VariabilityTemplataType(), CoordTemplataType()), KindTemplataType()))



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

  def lookupTypes(astrouts: Astrouts, env: Environment, needleImpreciseNameS: IImpreciseNameS): Vector[ITemplataType] = {
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
          case Some(x) => return Vector(x)
          case None =>
        }
      }
      case _ =>
    }

    needleImpreciseNameS match {
      case RuneNameS(rune) => {
        env.runeToType.get(rune) match {
          case Some(tyype) => return Vector(tyype)
          case None =>
        }
      }
      case _ =>
    }

    val nearStructTypes =
      env.structsS
        .filter(interface => impreciseNameMatchesAbsoluteName(needleImpreciseNameS, interface.name))
        .map(_.tyype)
    val nearInterfaceTypes =
      env.interfacesS
        .filter(interface => impreciseNameMatchesAbsoluteName(needleImpreciseNameS, interface.name))
        .map(_.tyype)
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

  def lookupType(astrouts: Astrouts,  env: Environment, range: RangeS, name: IImpreciseNameS): ITemplataType = {
    lookupTypes(astrouts, env, name).distinct match {
      case Vector() => ErrorReporter.report(CouldntFindTypeA(range, name))
      case Vector(only) => only
      case others => {
        ErrorReporter.report(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + others.mkString(", ")))
      }
    }
  }

  def translateStruct(
    astrouts: Astrouts,
    env: Environment,
    structS: StructS):
  StructA = {
    val StructS(rangeS, nameS, attributesS, weakable, genericParametersS, mutabilityRuneS, maybePredictedMutability, tyype, headerRuneToExplicitType, headerPredictedRuneToType, headerRulesWithImplicitlyCoercingLookupsS, membersRuneToExplicitType, membersPredictedRuneToType, memberRulesWithImplicitlyCoercingLookupsS, members) = structS

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
        astrouts, rangeS, genericParametersS.map(_.rune.rune), allRuneToExplicitType, Vector(), allRulesWithImplicitlyCoercingLookupsS, env)

    val runeAToType =
      mutable.HashMap[IRuneS, ITemplataType]((runeAToTypeWithImplicitlyCoercingLookupsS.toSeq): _*)
    // We've now calculated all the types of all the runes, but the LookupSR rules are still a bit
    // loose. We intentionally ignored the types of the things they're looking up, so we could know
    // what types we *expect* them to be, so we could coerce.
    // That coercion is good, but lets make it more explicit.

    val headerRulesBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, headerRulesBuilder, headerRulesWithImplicitlyCoercingLookupsS)
    val headerRulesExplicitS = headerRulesBuilder.toVector

    val memberRulesBuilder = ArrayBuffer[IRulexSR]()
    explicifyLookups(
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, memberRulesBuilder, memberRulesWithImplicitlyCoercingLookupsS)
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
    env: Environment,
    interfaceS: InterfaceS):
  ITemplataType = {
    interfaceS.tyype
  }

  def translateInterface(astrouts: Astrouts,  env: Environment, interfaceS: InterfaceS): InterfaceA = {
    val InterfaceS(rangeS, nameS, attributesS, weakable, genericParametersS, runeToExplicitType, mutabilityRuneS, maybePredictedMutability, predictedRuneToType, tyype, rulesWithImplicitlyCoercingLookupsS, internalMethodsS) = interfaceS

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
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS)

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

  def translateImpl(astrouts: Astrouts,  env: Environment, implS: ImplS): ImplA = {
    val ImplS(rangeS, nameS, identifyingRunesS, rulesWithImplicitlyCoercingLookupsS, runeToExplicitType, tyype, structKindRuneS, subCitizenImpreciseName, interfaceKindRuneS, superInterfaceImpreciseName) = implS

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
    explicifyLookups(
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS)

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

  def translateExport(astrouts: Astrouts,  env: Environment, exportS: ExportAsS): ExportAsA = {
    val ExportAsS(rangeS, rulesWithImplicitlyCoercingLookupsS, exportName, rune, exportedName) = exportS

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
    explicifyLookups(
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS)

    highertyping.ExportAsA(rangeS, exportedName, ruleBuilder.toVector, runeAToType.toMap, rune)
  }

  def translateFunction(astrouts: Astrouts, env: Environment, functionS: FunctionS): FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, identifyingRunesS, runeToExplicitType, tyype, paramsS, maybeRetCoordRune, rulesWithImplicitlyCoercingLookupsS, bodyS) = functionS

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
    explicifyLookups(
      (range, name) => lookupType(astrouts, env, range, name),
      runeAToType, ruleBuilder, rulesWithImplicitlyCoercingLookupsS)

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
    env: Environment):
  Map[IRuneS, ITemplataType] = {
    val runeSToPreKnownTypeA =
      runeToExplicitType ++
        paramsS.flatMap(_.pattern.coordRune.map(_.rune -> CoordTemplataType())).toMap
    val runeSToType =
      new RuneTypeSolver(interner).solve(
        globalOptions.sanityCheck,
        globalOptions.useOptimizedSolver,
        (n) => lookupType(astrouts, env, rangeS, n),
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
    val env = Environment(None, None, codeMap, Map())

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
        vfail(HigherTypingErrorHumanizer.humanize(scoutCompilation.getCodeMap().getOrDie(), e))
      }
    }
  }
}
