package net.verdagon.vale.astronomer

import net.verdagon.vale.options.GlobalOptions
import net.verdagon.vale.parser.{CaptureP, FailedParse, FileP, ImmutableP, MutabilityP, MutableP}
import net.verdagon.vale.scout.{ExportS, ExternS, RuneTypeSolver, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.scout.patterns.{AbstractSP, AtomSP, CaptureS, OverrideSP}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{CodeLocationS, Err, FileCoordinateMap, IPackageResolver, Ok, PackageCoordinate, PackageCoordinateMap, RangeS, Result, vassert, vassertSome, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.List
import scala.collection.mutable

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
  override def hashCode(): Int = vcurious()

  val structsS: Vector[StructS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.structs)).toVector
  val interfacesS: Vector[InterfaceS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.interfaces)).toVector
  val implsS: Vector[ImplS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.impls)).toVector
  val functionsS: Vector[FunctionS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.implementedFunctions)).toVector
  val exportsS: Vector[ExportAsS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.exports)).toVector
  val imports: Vector[ImportS] = codeMap.moduleToPackagesToContents.values.flatMap(_.values.flatMap(_.imports)).toVector

  def addRunes(newruneToType: Map[IRuneS, ITemplataType]): Environment = {
    Environment(maybeName, maybeParentEnv, codeMap, runeToType ++ newruneToType)
  }
}

class Astronomer(globalOptions: GlobalOptions) {
  val primitives =
    Map(
      "int" -> KindTemplataType,
      "i64" -> KindTemplataType,
      "str" -> KindTemplataType,
      "bool" -> KindTemplataType,
      "float" -> KindTemplataType,
      "void" -> KindTemplataType,
      "__Never" -> KindTemplataType,
//      "IFunction1" -> TemplateTypeSR(Vector(MutabilityTypeSR, CoordTypeSR, CoordTypeSR), KindTypeSR),
      "Array" -> TemplateTemplataType(Vector(MutabilityTemplataType, CoordTemplataType), KindTemplataType))



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
    // the environment or anything, it lets templar to do that (because templar knows the actual types).
    // However, this means that when the lambda function gets to the astronomer, the astronomer doesn't
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
        .map(getStructType(astrouts, env, _))
    val nearInterfaceTypes =
      env.interfacesS
        .filter(interface => impreciseNameMatchesAbsoluteName(needleImpreciseNameS, interface.name))
        .map(getInterfaceType(astrouts, env, _))
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
    lookupTypes(astrouts, env, name) match {
      case Vector() => ErrorReporter.report(CouldntFindTypeA(range, name))
      case Vector(only) => only
      case others => {
        ErrorReporter.report(RangedInternalErrorA(range, "'" + name + "' has multiple types: " + others.mkString(", ")))
      }
    }
  }

  def getStructType(
    astrouts: Astrouts,
    env: Environment,
    structS: StructS):
  ITemplataType = {
    structS.maybePredictedType match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(structS.range.begin) match {
      case Some(Some(value)) => return value
      case _ =>
    }

    val struct = translateStruct(astrouts, env, structS)
    struct.tyype
  }

  def translateStruct(
    astrouts: Astrouts,
    env: Environment,
    structS: StructS):
  StructA = {
    val StructS(rangeS, nameS, attributesS, weakable, identifyingRunesS, runeToExplicitType, mutabilityRuneS, maybePredictedMutability, predictedRuneToType, maybePredictedType, rulesS, members) = structS

    astrouts.codeLocationToStruct.get(rangeS.begin) match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(rangeS.begin) match {
      // Weird because this means we already evaluated it, in which case we should have hit the above return
      case Some(Some(_)) => vwat()
      case Some(None) => {
        throw CompileErrorExceptionA(RangedInternalErrorA(rangeS, "Cycle in determining struct type!"))
      }
      case None =>
    }
    astrouts.codeLocationToMaybeType.put(rangeS.begin, None)

    val runeAToType =
      calculateRuneTypes(astrouts, rangeS, identifyingRunesS.map(_.rune), runeToExplicitType, Vector(), rulesS, env)

    // Shouldnt fail because we got a complete solve earlier
    val tyype = Scout.determineDenizenType(KindTemplataType, identifyingRunesS.map(_.rune), runeAToType).getOrDie()
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    val structA =
      StructA(
        rangeS,
        nameS,
        attributesS,
        weakable,
        mutabilityRuneS,
        maybePredictedMutability,
        tyype,
        identifyingRunesS,
        runeAToType,
        rulesS.toVector,
        members)
    astrouts.codeLocationToStruct.put(rangeS.begin, structA)
    structA
  }

  def getInterfaceType(
    astrouts: Astrouts,
    env: Environment,
    interfaceS: InterfaceS):
  ITemplataType = {
    interfaceS.maybePredictedType match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(interfaceS.range.begin) match {
      case Some(Some(value)) => return value
      case _ =>
    }

    val struct = translateInterface(astrouts, env, interfaceS)
    struct.tyype
  }

  def translateInterface(astrouts: Astrouts,  env: Environment, interfaceS: InterfaceS): InterfaceA = {
    val InterfaceS(rangeS, nameS, attributesS, weakable, identifyingRunesS, runeToExplicitType, mutabilityRuneS, maybePredictedMutability, predictedRuneToType, maybePredictedType, rulesS, internalMethodsS) = interfaceS

    astrouts.codeLocationToInterface.get(rangeS.begin) match {
      case Some(value) => return value
      case None =>
    }

    astrouts.codeLocationToMaybeType.get(rangeS.begin) match {
      // Weird because this means we already evaluated it, in which case we should have hit the above return
      case Some(Some(_)) => vwat()
      case Some(None) => {
        throw CompileErrorExceptionA(RangedInternalErrorA(rangeS, "Cycle in determining interface type!"))
      }
      case None =>
    }
    astrouts.codeLocationToMaybeType.put(rangeS.begin, None)

    val runeAToType =
      calculateRuneTypes(astrouts, rangeS, identifyingRunesS.map(_.rune), runeToExplicitType, Vector(), rulesS, env)

    // getOrDie because we should have gotten a complete solve
    val tyype = Scout.determineDenizenType(KindTemplataType, identifyingRunesS.map(_.rune), runeAToType).getOrDie()
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    val methodsEnv =
      env
        .addRunes(runeAToType)
    val internalMethodsA =
      internalMethodsS.map(method => {
        translateFunction(astrouts, methodsEnv, method)
      })

    val interfaceA =
      InterfaceA(
        rangeS,
        nameS,
        attributesS,
        weakable,
        mutabilityRuneS,
        maybePredictedMutability,
        tyype,
        //        knowableRunesS,
        identifyingRunesS,
        //        localRunesS,
        //        conclusions,
        runeAToType,
        rulesS.toVector,
        internalMethodsA)
    astrouts.codeLocationToInterface.put(rangeS.begin, interfaceA)
    interfaceA
  }

  def translateImpl(astrouts: Astrouts,  env: Environment, implS: ImplS): ImplA = {
    val ImplS(rangeS, nameS, identifyingRunesS, rulesS, runeToExplicitType, structKindRuneS, interfaceKindRuneS) = implS

    val runeSToType =
      calculateRuneTypes(
        astrouts,
        rangeS,
        identifyingRunesS.map(_.rune),
        runeToExplicitType + (structKindRuneS.rune -> KindTemplataType, interfaceKindRuneS.rune -> KindTemplataType),
        Vector(),
        rulesS,
        env)

    // getOrDie because we should have gotten a complete solve
    val tyype = Scout.determineDenizenType(KindTemplataType, identifyingRunesS.map(_.rune), runeSToType).getOrDie()
    astrouts.codeLocationToMaybeType.put(rangeS.begin, Some(tyype))

    ImplA(
      rangeS,
      nameS,
      // Just getting the template name (or the kind name if not template), see INSHN.
      ImplImpreciseNameS(RuleScout.getRuneKindTemplate(rulesS, structKindRuneS.rune)),
      identifyingRunesS,
      rulesS.toVector,
      runeSToType,
      structKindRuneS,
      interfaceKindRuneS)
  }

  def translateExport(astrouts: Astrouts,  env: Environment, exportS: ExportAsS): ExportAsA = {
    val ExportAsS(rangeS, rulesS, exportName, rune, exportedName) = exportS

    val runeSToType =
      calculateRuneTypes(
        astrouts,
        rangeS,
        Vector(),
        Map(rune.rune -> KindTemplataType),
        Vector(),
        rulesS,
        env)

    ExportAsA(rangeS, exportedName, rulesS.toVector, runeSToType, rune)
  }

  def translateFunction(astrouts: Astrouts, env: Environment, functionS: FunctionS): FunctionA = {
    val FunctionS(rangeS, nameS, attributesS, identifyingRunesS, runeToExplicitType, paramsS, maybeRetCoordRune, rulesS, bodyS) = functionS

    val runeAToType =
      calculateRuneTypes(astrouts, rangeS, identifyingRunesS.map(_.rune), runeToExplicitType, paramsS, rulesS, env)

    // Shouldnt fail because we got a complete solve on the rules
    val tyype = Scout.determineDenizenType(FunctionTemplataType, identifyingRunesS.map(_.rune), runeAToType).getOrDie()

    FunctionA(
      rangeS,
      nameS,
      attributesS ++ Vector(UserFunctionS),
      tyype,
      identifyingRunesS,
      runeAToType ++ env.runeToType,
      paramsS,
      maybeRetCoordRune,
      rulesS.toVector,
      bodyS)
  }

  private def calculateRuneTypes(
    astrouts: Astrouts,
    rangeS: RangeS,
    identifyingRunesS: Vector[IRuneS],
    runeToExplicitType: Map[IRuneS, ITemplataType],
    paramsS: Vector[ParameterS],
    rulesS: Array[IRulexSR],
    env: Environment):
  Map[IRuneS, ITemplataType] = {
    val runeSToPreKnownTypeA =
      runeToExplicitType ++
        paramsS.flatMap(_.pattern.coordRune.map(_.rune -> CoordTemplataType)).toMap
    val runeSToType =
      RuneTypeSolver.solve(
        globalOptions.sanityCheck,
        globalOptions.useOptimizedSolver,
        (n) => lookupType(astrouts, env, rangeS, n),
        rangeS,
        false, rulesS, identifyingRunesS, true, runeSToPreKnownTypeA) match {
        case Ok(t) => t
        case Err(e) => throw CompileErrorExceptionA(CouldntSolveRulesA(rangeS, e))
      }
    runeSToType
  }

  def translateProgram(
      codeMap: PackageCoordinateMap[ProgramS],
      primitives: Map[String, ITemplataType],
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

  def runAstronomer(separateProgramsS: FileCoordinateMap[ProgramS]):
  Either[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    val mergedProgramS =
      PackageCoordinateMap(
        separateProgramsS.moduleToPackagesToFilenameToContents.mapValues(packagesToFilenameToContents => {
          packagesToFilenameToContents.mapValues(filenameToContents => {
            ProgramS(
              filenameToContents.values.flatMap(_.structs).toVector,
              filenameToContents.values.flatMap(_.interfaces).toVector,
              filenameToContents.values.flatMap(_.impls).toVector,
              filenameToContents.values.flatMap(_.implementedFunctions).toVector,
              filenameToContents.values.flatMap(_.exports).toVector,
              filenameToContents.values.flatMap(_.imports).toVector)
          })
        }))

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
      val packageToImplsA = implsA.groupBy(_.name.codeLocation.file.packageCoordinate)
      val packageToExportsA = exportsA.groupBy(_.range.file.packageCoordinate)

      val allPackages =
        packageToStructsA.keySet ++
        packageToInterfacesA.keySet ++
        packageToFunctionsA.keySet ++
        packageToImplsA.keySet ++
        packageToExportsA.keySet
      val packageToContents =
        allPackages.toVector.map(paackage => {
          val contents =
            ProgramA(
              packageToStructsA.getOrElse(paackage, Vector.empty),
              packageToInterfacesA.getOrElse(paackage, Vector.empty),
              packageToImplsA.getOrElse(paackage, Vector.empty),
              packageToFunctionsA.getOrElse(paackage, Vector.empty),
              packageToExportsA.getOrElse(paackage, Vector.empty))
          (paackage -> contents)
        }).toMap
      val moduleToPackageToContents =
        packageToContents.keys.toVector.groupBy(_.module).mapValues(packageCoordinates => {
          packageCoordinates.map(packageCoordinate => {
            (packageCoordinate.packages -> packageToContents(packageCoordinate))
          }).toMap
        })
      Left(PackageCoordinateMap(moduleToPackageToContents))
    } catch {
      case CompileErrorExceptionA(err) => {
        Right(err)
      }
    }
  }
}

class AstronomerCompilation(
  globalOptions: GlobalOptions,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var scoutCompilation = new ScoutCompilation(globalOptions, packagesToBuild, packageToContentsResolver)
  var astroutsCache: Option[PackageCoordinateMap[ProgramA]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = scoutCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = scoutCompilation.getVpstMap()
  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = scoutCompilation.getScoutput()

  def getAstrouts(): Result[PackageCoordinateMap[ProgramA], ICompileErrorA] = {
    astroutsCache match {
      case Some(astrouts) => Ok(astrouts)
      case None => {
        new Astronomer(globalOptions).runAstronomer(scoutCompilation.expectScoutput()) match {
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
        vfail(AstronomerErrorHumanizer.humanize(scoutCompilation.getCodeMap().getOrDie(), e))
      }
    }
  }
}
