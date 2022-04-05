package dev.vale.postparsing

//import dev.vale.astronomer.{Astronomer, AstroutsBox, Environment, IRuneS, ITemplataType}
import dev.vale.options.GlobalOptions
import dev.vale.postparsing.patterns.PatternScout
import dev.vale.postparsing.rules.{IRulexSR, LiteralSR, MutabilityLiteralSL, RuleScout, RuneUsage, TemplexScout}
import dev.vale.{CodeLocationS, Err, FileCoordinate, FileCoordinateMap, IPackageResolver, Interner, Ok, PackageCoordinate, Profiler, RangeS, Result, postparsing, vassert, vcurious, vfail, vimpl, vpass, vwat}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import PostParser.determineDenizenType
import dev.vale.parsing.{FailedParse, ParserCompilation}
import dev.vale.parsing.ast.{AnonymousRunePT, BoolPT, CallPT, ExportAsP, ExportAttributeP, FileP, IAttributeP, IImpreciseNameP, ITemplexPT, ImplP, ImportP, InlinePT, IntPT, InterfaceP, InterpretedPT, IterableNameP, IterationOptionNameP, IteratorNameP, LocationPT, LookupNameP, MacroCallP, MutabilityP, MutabilityPT, NameOrRunePT, NameP, NormalStructMemberP, OwnershipPT, RangeP, RulePUtils, SealedAttributeP, StaticSizedArrayPT, StructMembersP, StructMethodP, StructP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImplP, TopLevelImportP, TopLevelInterfaceP, TopLevelStructP, TuplePT, VariadicStructMemberP, WeakableAttributeP}
//import dev.vale.postparsing.predictor.RuneTypeSolveError
import dev.vale.postparsing.rules._
import dev.vale.Err

import scala.collection.immutable.List
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class CompileErrorExceptionS(err: ICompileErrorS) extends RuntimeException { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait ICompileErrorS { def range: RangeS }
case class UnknownRuleFunctionS(range: RangeS, name: String) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class UnimplementedExpression(range: RangeS, expressionName: String) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntFindVarToMutateS(range: RangeS, name: String) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class StatementAfterReturnS(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ForgotSetKeywordError(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantUseThatLocalName(range: RangeS, name: String) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class ExternHasBody(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantInitializeIndividualElementsOfRuntimeSizedArray(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InitializingRuntimeSizedArrayRequiresSizeAndCallable(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InitializingStaticSizedArrayRequiresSizeAndCallable(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InitializingStaticSizedArrayFromCallableNeedsSizeTemplex(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantOwnershipInterfaceInImpl(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantOwnershipStructInImpl(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CantOverrideOwnershipped(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class VariableNameAlreadyExists(range: RangeS, name: IVarNameS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class InterfaceMethodNeedsSelf(range: RangeS) extends ICompileErrorS {
  vpass()
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
}
case class VirtualAndAbstractGoTogether(range: RangeS) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class CouldntSolveRulesS(range: RangeS, error: RuneTypeSolveError) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class IdentifyingRunesIncompleteS(range: RangeS, error: IdentifiabilitySolveError) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class RangedInternalErrorS(range: RangeS, message: String) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }
case class LightFunctionMustHaveParamTypes(range: RangeS, paramIndex: Int) extends ICompileErrorS { override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious() }

sealed trait IEnvironment {
  def file: FileCoordinate
  def name: INameS
  def allDeclaredRunes(): Set[IRuneS]
  def localDeclaredRunes(): Set[IRuneS]
}

// Someday we might split this into PackageEnvironment and CitizenEnvironment
case class Environment(
    file: FileCoordinate,
    parentEnv: Option[Environment],
    name: INameS,
    userDeclaredRunes: Set[IRuneS]
) extends IEnvironment {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def localDeclaredRunes(): Set[IRuneS] = {
    userDeclaredRunes
  }
  override def allDeclaredRunes(): Set[IRuneS] = {
    userDeclaredRunes ++ parentEnv.toVector.flatMap(_.allDeclaredRunes())
  }
}

case class FunctionEnvironment(
  file: FileCoordinate,
  name: IFunctionDeclarationNameS,
  parentEnv: Option[IEnvironment],
  // Contains all the identifying runes and otherwise declared runes from this function's rules.
  // These are important for knowing whether e.g. T is a type or a rune when we process all the runes.
  // See: Must Scan For Declared Runes First (MSFDRF)
  private val declaredRunes: Set[IRuneS],
  // So that when we run into a magic param, we can add this to the number of previous magic
  // params to get the final param index.
  numExplicitParams: Int,
  // Whether this is an abstract method inside defined inside an interface.
  // (Maybe we can instead determine this by looking at parentEnv?)
  isInterfaceInternalMethod: Boolean
) extends IEnvironment {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  override def localDeclaredRunes(): Set[IRuneS] = {
    declaredRunes
  }
  override def allDeclaredRunes(): Set[IRuneS] = {
    declaredRunes ++ parentEnv.toVector.flatMap(_.allDeclaredRunes()).toSet
  }
  def child(): FunctionEnvironment = {
    FunctionEnvironment(file, name, Some(this), Set(), numExplicitParams, false)
  }
}

case class StackFrame(
    file: FileCoordinate,
    name: IFunctionDeclarationNameS,
    parentEnv: FunctionEnvironment,
    maybeParent: Option[StackFrame],
    locals: VariableDeclarations) {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()
  def ++(newVars: VariableDeclarations): StackFrame = {
    StackFrame(file, name, parentEnv, maybeParent, locals ++ newVars)
  }
  def allDeclarations: VariableDeclarations = {
    locals ++ maybeParent.map(_.allDeclarations).getOrElse(PostParser.noDeclarations)
  }
  def findVariable(name: IImpreciseNameS): Option[IVarNameS] = {
    locals.find(name) match {
      case Some(fullNameS) => Some(fullNameS)
      case None => {
        maybeParent match {
          case None => None
          case Some(parent) => parent.findVariable(name)
        }
      }
    }
  }
}

object PostParser {
//  val VIRTUAL_DROP_FUNCTION_NAME = "vdrop"
  // Interface's drop function simply calls vdrop.
  // A struct's vdrop function calls the struct's drop function.

  def noVariableUses = VariableUses(Vector.empty)
  def noDeclarations = VariableDeclarations(Vector.empty)

  def evalRange(file: FileCoordinate, range: RangeP): RangeS = {
    RangeS(evalPos(file, range.begin), evalPos(file, range.end))
  }

  def evalPos(file: FileCoordinate, pos: Int): CodeLocationS = {
    CodeLocationS(file, pos)
  }

  def translateImpreciseName(interner: Interner, file: FileCoordinate, name: IImpreciseNameP): IImpreciseNameS = {
    name match {
      case LookupNameP(name) => interner.intern(CodeNameS(name.str))
      case IterableNameP(range) => IterableNameS(PostParser.evalRange(file,  range))
      case IteratorNameP(range) => IteratorNameS(PostParser.evalRange(file,  range))
      case IterationOptionNameP(range) => IterationOptionNameS(PostParser.evalRange(file,  range))
    }
  }

  // Err is the missing rune
  def determineDenizenType(
    templateResultType: ITemplataType,
    identifyingRunesS: Vector[IRuneS],
    runeAToType: Map[IRuneS, ITemplataType]):
  Result[ITemplataType, IRuneS] = {
    val isTemplate = identifyingRunesS.nonEmpty

    val tyype =
      if (isTemplate) {
        TemplateTemplataType(
          identifyingRunesS.map(identifyingRuneA => {
            runeAToType.get(identifyingRuneA) match {
              case None => return Err(identifyingRuneA)
              case Some(x) => x
            }
          }),
          templateResultType)
      } else {
        templateResultType
      }
    Ok(tyype)
  }

  def getHumanName(templex: ITemplexPT): IImpreciseNameS = {
    templex match {
      //      case NullablePT(_, inner) => getHumanName(inner)
      case InlinePT(_, inner) => getHumanName(inner)
      //      case PermissionedPT(_, permission, inner) => getHumanName(inner)
      case InterpretedPT(_, ownership, inner) => getHumanName(inner)
      case AnonymousRunePT(_) => vwat()
      case NameOrRunePT(NameP(_, name)) => CodeNameS(name)
      case CallPT(_, template, args) => getHumanName(template)
      case StaticSizedArrayPT(_, mutability, variability, size, element) => vwat()
      case TuplePT(_, members) => vwat()
      case IntPT(_, value) => vwat()
      case BoolPT(_, value) => vwat()
      case OwnershipPT(_, ownership) => vwat()
      case MutabilityPT(_, mutability) => vwat()
      case LocationPT(_, location) => vwat()
    }
  }

//  def knownEndsInVoid(expr: IExpressionSE): Boolean = {
//    expr match {
//      case VoidSE(_) => true
//      case ReturnSE(_, _) => true
//      case DestructSE(_, _) => true
//      case IfSE(_, _, thenBody, elseBody) => knownEndsInVoid(thenBody) && knownEndsInVoid(elseBody)
//      case WhileSE(_, _) => true
//    }
//  }

//  def pruneTrailingVoids(exprs: Vector[IExpressionSE]): Vector[IExpressionSE] = {
//    if (exprs.size >= 2) {
//      exprs.last match {
//        case VoidSE(_) => {
//          exprs.init.last match {
//            case ReturnSE(_, _) => return exprs.init
//            case VoidSE(_) => return pruneTrailingVoids(exprs.init)
//            case
//          }
//        }
//        case _ =>
//      }
//    }
//  }

  def consecutive(exprs: Vector[IExpressionSE]): IExpressionSE = {
    if (exprs.isEmpty) {
      vcurious()
    } else if (exprs.size == 1) {
      exprs.head
    } else {
      ConsecutorSE(
        exprs.flatMap({
          case ConsecutorSE(exprs) => exprs
          case other => List(other)
        }))
    }
  }
}

class PostParser(
    globalOptions: GlobalOptions,
    interner: Interner) {
  val templexScout = new TemplexScout(interner)
  val ruleScout = new RuleScout(templexScout)
  val functionScout = new FunctionScout(this, interner, templexScout, ruleScout)

  def scoutProgram(fileCoordinate: FileCoordinate, parsed: FileP): Result[ProgramS, ICompileErrorS] = {
    Profiler.frame(() => {
      try {
        val structsS = parsed.topLevelThings.collect({ case TopLevelStructP(s) => s }).map(scoutStruct(fileCoordinate, _));
        val interfacesS = parsed.topLevelThings.collect({ case TopLevelInterfaceP(i) => i }).map(scoutInterface(fileCoordinate, _));
        val implsS = parsed.topLevelThings.collect({ case TopLevelImplP(i) => i }).map(scoutImpl(fileCoordinate, _))
        val functionsS =
          parsed.topLevelThings
            .collect({ case TopLevelFunctionP(f) => f }).map(functionScout.scoutTopLevelFunction(fileCoordinate, _))
        val exportsS = parsed.topLevelThings.collect({ case TopLevelExportAsP(e) => e }).map(scoutExportAs(fileCoordinate, _))
        val importsS = parsed.topLevelThings.collect({ case TopLevelImportP(e) => e }).map(scoutImport(fileCoordinate, _))
        val programS = ProgramS(structsS, interfacesS, implsS, functionsS, exportsS, importsS)
        Ok(programS)
      } catch {
        case CompileErrorExceptionS(err) => Err(err)
      }
    })
  }

  private def scoutImpl(file: FileCoordinate, impl0: ImplP): ImplS = {
    val ImplP(range, identifyingRuneNames, maybeTemplateRulesP, maybeStruct, interface, attributes) = impl0

    interface match {
      case InterpretedPT(range, _, _) => {
        throw CompileErrorExceptionS(CantOwnershipInterfaceInImpl(PostParser.evalRange(file, range)))
      }
      case _ =>
    }

    maybeStruct match {
      case Some(InterpretedPT(range, _, _)) => {
        throw CompileErrorExceptionS(CantOwnershipStructInImpl(PostParser.evalRange(file, range)))
      }
      case _ =>
    }


    val templateRulesP = maybeTemplateRulesP.toVector.flatMap(_.rules)

    val codeLocation = PostParser.evalPos(file, range.begin)
    val implName = interner.intern(ImplDeclarationNameS(codeLocation))

    val identifyingRunes =
      identifyingRuneNames
        .toVector.flatMap(_.runes)
        .map(_.name)
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(templateRulesP)
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val userDeclaredRunes = identifyingRunes ++ runesFromRules

    val implEnv = postparsing.Environment(file, None, implName, userDeclaredRunes.map(_.rune).toSet)

    val lidb = new LocationInDenizenBuilder(Vector())
    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

    ruleScout.translateRulexes(implEnv, lidb.child(), ruleBuilder, runeToExplicitType, templateRulesP)

    val struct =
      maybeStruct match {
        case None => throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(PostParser.evalRange(file, range), "Impl needs struct!"))
        case Some(x) => x
      }

    val structRune =
      templexScout.translateMaybeTypeIntoRune(
        implEnv,
        lidb.child(),
        PostParser.evalRange(file, range),
        ruleBuilder,
        Some(struct))

    val interfaceRune =
      templexScout.translateMaybeTypeIntoRune(
        implEnv,
        lidb.child(),
        PostParser.evalRange(file, range),
        ruleBuilder,
        Some(interface))

    postparsing.ImplS(
      PostParser.evalRange(file, range),
      implName,
      identifyingRunes,
      ruleBuilder.toArray,
      runeToExplicitType.toMap,
      structRune,
      interfaceRune)
  }

  private def scoutExportAs(file: FileCoordinate, exportAsP: ExportAsP): ExportAsS = {
    val ExportAsP(range, templexP, exportedName) = exportAsP

    val pos = PostParser.evalPos(file, range.begin)
    val exportName = interner.intern(ExportAsNameS(pos))
    val exportEnv = Environment(file, None, exportName, Set())

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val lidb = new LocationInDenizenBuilder(Vector())
    val runeS = templexScout.translateTemplex(exportEnv, lidb, ruleBuilder, templexP)

    postparsing.ExportAsS(PostParser.evalRange(file, range), ruleBuilder.toArray, exportName, runeS, exportedName.str)
  }

  private def scoutImport(file: FileCoordinate, importP: ImportP): ImportS = {
    val ImportP(range, moduleName, packageNames, importeeName) = importP

    val pos = PostParser.evalPos(file, range.begin)

    postparsing.ImportS(PostParser.evalRange(file, range), moduleName.str, packageNames.map(_.str), importeeName.str)
  }

  private def predictMutability(rangeS: RangeS, mutabilityRuneS: IRuneS, rulesS: Array[IRulexSR]):
  Option[MutabilityP] = {
    val predictedMutabilities =
      rulesS.collect({
        case LiteralSR(_, runeS, MutabilityLiteralSL(mutability)) if runeS.rune == mutabilityRuneS => mutability
      })
    val predictedMutability =
      predictedMutabilities.size match {
        case 0 => None
        case 1 => Some(predictedMutabilities.head)
        case _ => throw CompileErrorExceptionS(RangedInternalErrorS(rangeS, "Too many mutabilities: " + predictedMutabilities.mkString("[", ", ", "]")))
      }
    predictedMutability
  }

  private def scoutStruct(file: FileCoordinate, head: StructP): StructS = {
    val StructP(rangeP, NameP(structNameRange, structHumanName), attributesP, mutabilityPT, maybeIdentifyingRunes, maybeTemplateRulesP, StructMembersP(_, members)) = head

    val structRangeS = PostParser.evalRange(file, rangeP)
    val structName = interner.intern(postparsing.TopLevelCitizenDeclarationNameS(structHumanName, PostParser.evalRange(file, structNameRange)))

    val lidb = new LocationInDenizenBuilder(Vector())

    val templateRulesP = maybeTemplateRulesP.toVector.flatMap(_.rules)

    val identifyingRunesS: Vector[RuneUsage] =
      maybeIdentifyingRunes
        .toVector.flatMap(_.runes).map(_.name)
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val runesFromRules =
      RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(templateRulesP)
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val userDeclaredRunes = identifyingRunesS ++ runesFromRules
    val structEnv = postparsing.Environment(file, None, structName, userDeclaredRunes.map(_.rune).toSet)

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

    val membersS =
      members.flatMap({
        case NormalStructMemberP(range, name, variability, memberType) => {
          val memberRune = templexScout.translateTemplex(structEnv, lidb.child(), ruleBuilder, memberType)
          runeToExplicitType.put(memberRune.rune, CoordTemplataType)
          Vector(NormalStructMemberS(PostParser.evalRange(structEnv.file, range), name.str, variability, memberRune))
        }
        case VariadicStructMemberP(range, variability, memberType) => {
          val memberRune = templexScout.translateTemplex(structEnv, lidb.child(), ruleBuilder, memberType)
          runeToExplicitType.put(memberRune.rune, CoordTemplataType)
          Vector(VariadicStructMemberS(PostParser.evalRange(structEnv.file, range), variability, memberRune))
        }
        case StructMethodP(_) => {
          // Implement struct methods one day
          Vector.empty
        }
      })

    ruleScout.translateRulexes(structEnv, lidb.child(), ruleBuilder, runeToExplicitType, templateRulesP)

    val mutabilityRuneS = templexScout.translateTemplex(structEnv, lidb.child(), ruleBuilder, mutabilityPT)
    runeToExplicitType.put(mutabilityRuneS.rune, MutabilityTemplataType)

    val rulesS = ruleBuilder.toArray

    val runeToPredictedType = predictRuneTypes(structRangeS, identifyingRunesS.map(_.rune), runeToExplicitType.toMap, rulesS)

    val predictedMutability = predictMutability(structRangeS, mutabilityRuneS.rune, rulesS)

    val maybePredictedType =
      determineDenizenType(KindTemplataType, identifyingRunesS.map(_.rune), runeToPredictedType) match {
        case Ok(x) => Some(x)
        case Err(e) => {
          vassert(e.isInstanceOf[IRuneS])
          None
        }
      }

    val weakable = attributesP.exists({ case w @ WeakableAttributeP(_) => true case _ => false })
    val attrsS = translateCitizenAttributes(file, attributesP.filter({ case WeakableAttributeP(_) => false case _ => true}))

//    val runeSToCanonicalRune = ruleBuilder.runeSToTentativeRune.mapValues(tentativeRune => tentativeRuneToCanonicalRune(tentativeRune))

    postparsing.StructS(
      structRangeS,
      structName,
      attrsS,
      weakable,
      identifyingRunesS,
      runeToExplicitType.toMap,
      mutabilityRuneS,
      predictedMutability,
      runeToPredictedType,
      maybePredictedType,
      rulesS,
      membersS)
  }

  def translateCitizenAttributes(file: FileCoordinate, attrsP: Vector[IAttributeP]): Vector[ICitizenAttributeS] = {
    attrsP.map({
      case ExportAttributeP(_) => ExportS(file.packageCoordinate)
      case SealedAttributeP(_) => SealedS
      case MacroCallP(range, dontCall, NameP(_, str)) => MacroCallS(PostParser.evalRange(file, range), dontCall, str)
      case x => vimpl(x.toString)
    })
  }


  def predictRuneTypes(
    rangeS: RangeS,
    identifyingRunesS: Vector[IRuneS],
    runeToExplicitType: Map[IRuneS, ITemplataType],
    rulesS: Array[IRulexSR]):
  Map[IRuneS, ITemplataType] = {
    Profiler.frame(() => {
      val runeSToLocallyPredictedTypes =
        new RuneTypeSolver(interner).solve(
          globalOptions.sanityCheck,
          globalOptions.useOptimizedSolver,
          (n) => vimpl(), rangeS, true, rulesS, identifyingRunesS, false, runeToExplicitType) match {
          case Ok(t) => t
          // This likely cannot happen because we aren't even asking for a complete solve.
          case Err(e) => throw CompileErrorExceptionS(CouldntSolveRulesS(rangeS, e))
        }
      runeSToLocallyPredictedTypes
    })
  }


  def checkIdentifiability(
    rangeS: RangeS,
    identifyingRunesS: Vector[IRuneS],
    rulesS: Array[IRulexSR]):
  Unit = {
    IdentifiabilitySolver.solve(
      globalOptions.sanityCheck,
      globalOptions.useOptimizedSolver,
      rangeS, rulesS, identifyingRunesS) match {
      case Ok(_) =>
      case Err(e) => throw CompileErrorExceptionS(IdentifyingRunesIncompleteS(rangeS, e))
    }
  }

  private def scoutInterface(
    file: FileCoordinate,
    containingInterfaceP: InterfaceP):
  InterfaceS = {
    val InterfaceP(interfaceRange, NameP(interfaceNameRangeS, interfaceHumanName), attributesP, mutabilityPT, maybeIdentifyingRunes, maybeRulesP, internalMethodsP) = containingInterfaceP
    val interfaceRangeS = PostParser.evalRange(file, interfaceRange)
    val interfaceFullName = interner.intern(postparsing.TopLevelCitizenDeclarationNameS(interfaceHumanName, PostParser.evalRange(file, interfaceNameRangeS)))
    val rulesP = maybeRulesP.toVector.flatMap(_.rules)

    val lidb = new LocationInDenizenBuilder(Vector())

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

    val explicitIdentifyingRunes: Vector[RuneUsage] =
      maybeIdentifyingRunes
        .toVector.flatMap(_.runes).map(_.name)
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val interfaceEnv = postparsing.Environment(file, None, interfaceFullName, explicitIdentifyingRunes.map(_.rune).toSet)

    ruleScout.translateRulexes(interfaceEnv, lidb.child(), ruleBuilder, runeToExplicitType, rulesP)

    val mutabilityRuneS = templexScout.translateTemplex(interfaceEnv, lidb.child(), ruleBuilder, mutabilityPT)


    val rulesS = ruleBuilder.toArray

    val runeToPredictedType = predictRuneTypes(interfaceRangeS, explicitIdentifyingRunes.map(_.rune), Map(), rulesS)

    val predictedMutability = predictMutability(interfaceRangeS, mutabilityRuneS.rune, rulesS)

    val maybePredictedType =
      determineDenizenType(KindTemplataType, explicitIdentifyingRunes.map(_.rune), runeToPredictedType) match {
        case Ok(x) => Some(x)
        case Err(e) => {
          vassert(e.isInstanceOf[IRuneS])
          None
        }
      }

    val internalMethodsS =
      internalMethodsP.map(
        functionScout.scoutInterfaceMember(
          interfaceEnv, explicitIdentifyingRunes.toArray, rulesS, runeToExplicitType.toMap, _))

    val weakable = attributesP.exists({ case w @ WeakableAttributeP(_) => true case _ => false })
    val attrsS = translateCitizenAttributes(file, attributesP.filter({ case WeakableAttributeP(_) => false case _ => true}))

    val interfaceS =
      postparsing.InterfaceS(
        PostParser.evalRange(file, interfaceRange),
        interfaceFullName,
        attrsS,
        weakable,
//        knowableValueRunes,
        explicitIdentifyingRunes,
        runeToExplicitType.toMap,
//        localRunes,
//        maybePredictedType,
        mutabilityRuneS,
        predictedMutability,
        runeToPredictedType,
        maybePredictedType,
//        isTemplate,
        rulesS,
//        runeSToCanonicalRune,
        internalMethodsS)

    interfaceS
  }

}

class ScoutCompilation(
  globalOptions: GlobalOptions,
  interner: Interner,
  packagesToBuild: Vector[PackageCoordinate],
  packageToContentsResolver: IPackageResolver[Map[String, String]]) {
  var parserCompilation = new ParserCompilation(globalOptions, interner, packagesToBuild, packageToContentsResolver)
  var scoutputCache: Option[FileCoordinateMap[ProgramS]] = None

  def getCodeMap(): Result[FileCoordinateMap[String], FailedParse] = parserCompilation.getCodeMap()
  def getParseds(): Result[FileCoordinateMap[(FileP, Vector[(Int, Int)])], FailedParse] = parserCompilation.getParseds()
  def getVpstMap(): Result[FileCoordinateMap[String], FailedParse] = parserCompilation.getVpstMap()

  def getScoutput(): Result[FileCoordinateMap[ProgramS], ICompileErrorS] = {
    scoutputCache match {
      case Some(scoutput) => Ok(scoutput)
      case None => {
        val scoutput =
          parserCompilation.expectParseds().map({ case (fileCoordinate, (code, commentsAndRanges)) =>
            new PostParser(globalOptions, interner).scoutProgram(fileCoordinate, code) match {
              case Err(e) => return Err(e)
              case Ok(p) => p
            }
          })
        scoutputCache = Some(scoutput)
        Ok(scoutput)
      }
    }
  }
  def expectScoutput(): FileCoordinateMap[ProgramS] = {
    getScoutput() match {
      case Ok(x) => x
      case Err(e) => vfail(PostParserErrorHumanizer.humanize(getCodeMap().getOrDie(), e))
    }
  }
}
