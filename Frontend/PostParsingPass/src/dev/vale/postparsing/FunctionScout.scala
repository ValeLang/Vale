package dev.vale.postparsing

import dev.vale.postparsing.rules.{AugmentSR, IRulexSR, LookupSR, RuleScout, RuneUsage, TemplexScout}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import PostParser.noDeclarations
import dev.vale
import dev.vale.{FileCoordinate, Interner, Keywords, RangeS, postparsing, vassertSome, vcurious, vimpl, vwat}
import dev.vale.postparsing.patterns.{AtomSP, CaptureS, PatternScout}
import dev.vale.postparsing.patterns._
//import dev.vale.postparsing.predictor.{Conclusions, PredictorEvaluator}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
//import dev.vale.postparsing.predictor.Conclusions
import dev.vale.postparsing.rules._
//import dev.vale.postparsing.templatepredictor.PredictorEvaluator
import dev.vale._

import scala.collection.immutable.{List, Range}

case class ParentInterface(
  interfaceEnv: EnvironmentS,
  interfaceGenericParams: Vector[GenericParameterS],
  interfaceRules: Vector[IRulexSR],
  interfaceRuneToExplicitType: Map[IRuneS, ITemplataType])

class FunctionScout(
    postParser: PostParser,
    interner: Interner,
    keywords: Keywords,
    templexScout: TemplexScout,
    ruleScout: RuleScout) {
  val patternScout = new PatternScout(interner, templexScout)
  val expressionScout =
    new ExpressionScout(
      new IExpressionScoutDelegate {
        override def scoutLambda(parentStackFrame: StackFrame, lambdaFunction0: FunctionP): (FunctionS, VariableUses) = {
          FunctionScout.this.scoutLambda(parentStackFrame, lambdaFunction0)
        }
      },
      templexScout,
      ruleScout,
      patternScout,
      interner,
      keywords
    )

  def scoutFunction(file: FileCoordinate, functionP: FunctionP, maybeParentInterface: Option[ParentInterface]): FunctionS = {

    val FunctionP(range, headerP, maybeBody0) = functionP;
    val FunctionHeaderP(_, Some(NameP(_, codeName)), attrsP, maybeGenericParametersP, templateRulesP, maybeParamsP, returnP) = headerP
    val FunctionReturnP(retRange, maybeInferRet, maybeRetType) = returnP

    val rangeS = PostParser.evalRange(file, range)
    val retRangeS = PostParser.evalRange(file, retRange)

    val genericParametersP =
      maybeGenericParametersP
        .toVector
        .flatMap(_.params)
        // Filter out any regions, we dont do those yet
        .filter({
          case GenericParameterP(_, _, Some(GenericParameterTypeP(_, RegionTypePR)), _, _) => false
          case _ => true
        })

    val userSpecifiedIdentifyingRunes =
      genericParametersP
        .map({ case GenericParameterP(_, NameP(range, identifyingRuneName), _, _, _) =>
          rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName))
        })

    val codeLocation = PostParser.evalPos(file, range.begin)

    val lidb = new LocationInDenizenBuilder(Vector())

    // See: Must Scan For Declared Runes First (MSFDRF)
    val userRunesFromRules =
      templateRulesP
        .toVector
        .flatMap(rules => RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rules.rules))
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val userDeclaredRunes = (userSpecifiedIdentifyingRunes ++ userRunesFromRules).distinct

    val funcName = interner.intern(FunctionNameS(codeName, codeLocation))

    maybeParentInterface match {
      case None =>
      case Some(parentInterface) => {
        maybeParamsP match {
          case None =>
          case Some(paramsP) => {
            if (!paramsP.patterns.exists(_.virtuality match { case Some(AbstractP(_)) => true case _ => false })) {
              throw CompileErrorExceptionS(InterfaceMethodNeedsSelf(PostParser.evalRange(file, range)))
            }
          }
        }
      }
    }

    val ruleBuilder = ArrayBuffer[IRulexSR]()

    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

    val parentEnv =
      maybeParentInterface match {
        case None => None
        case Some(parentInterface) => {
          Some(parentInterface.interfaceEnv)
        }
      }
    val isInterfaceInternalMethod =
      maybeParentInterface match {
        case None => false
        case Some(_) => true
      }

    val functionEnv =
      postparsing.FunctionEnvironmentS(
        file, funcName, parentEnv, userDeclaredRunes.map(_.rune).toSet, maybeParamsP.size, isInterfaceInternalMethod)

    maybeParentInterface match {
      case None => {
        ruleScout.translateRulexes(
          functionEnv,
          lidb.child(),
          ruleBuilder,
          runeToExplicitType,
          templateRulesP.toVector.flatMap(_.rules))
      }
      case Some(parentInterface) => {
        ruleBuilder ++= parentInterface.interfaceRules
        runeToExplicitType ++= parentInterface.interfaceRuneToExplicitType
        ruleScout.translateRulexes(
          maybeParentInterface match {
            case None => functionEnv
            case Some(parentInterface) => parentInterface.interfaceEnv
          },
          lidb.child(),
          ruleBuilder,
          runeToExplicitType,
          templateRulesP.toVector.flatMap(_.rules))
      }
    }

    val functionUserSpecifiedGenericParametersS =
      genericParametersP.zip(userSpecifiedIdentifyingRunes)
        .map({ case (g, r) =>
          PostParser.scoutGenericParameter(
            templexScout, functionEnv, lidb.child(), runeToExplicitType, ruleBuilder, g, r)
        })

    val genericParametersS =
      functionUserSpecifiedGenericParametersS ++
        (maybeParentInterface match {
          case None => Vector()
          case Some(parentInterface) => parentInterface.interfaceGenericParams
        })

    val myStackFrameWithoutParams =
      StackFrame(file, funcName, functionEnv, None, noDeclarations)

    val paramPatternsP =
      maybeParamsP.toVector.flatMap(_.patterns).zipWithIndex.map({
        case (pattern, index) => {
          // Should have been caught by LightFunctionMustHaveParamTypes error in parser,
          vassert(pattern.templex.nonEmpty)
          pattern
        }
      })

    val explicitParamsPatterns1 =
      patternScout.scoutPatterns(
        myStackFrameWithoutParams, lidb.child(), ruleBuilder, runeToExplicitType, paramPatternsP)

    val explicitParams1 = explicitParamsPatterns1.map(ParameterS)

    // Only if the function actually has a body
    val maybeCaptureDeclarations =
      maybeBody0 match {
        case None => None
        case Some(body0) => {
          Some(
            explicitParams1
              .map(explicitParam1 => {
                postparsing.VariableDeclarations(
                  patternScout.getParameterCaptures(explicitParam1.pattern))
              })
              .foldLeft(noDeclarations)(_ ++ _))
        }
      }

    // Theres no such thing as this anymore?
    vassert(maybeInferRet.isEmpty)

    val maybeRetCoordRune =
      maybeRetType match {
        case None | Some(RegionRunePT(_, _)) => {
          // If nothing's present, assume void
          val rangeS = PostParser.evalRange(file, retRange)
          val rune = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
          ruleBuilder += LookupSR(rangeS, rune, interner.intern(CodeNameS(keywords.void)))
          Some(rune)
        }
        case Some(retTypePT) => {
          templexScout.translateMaybeTypeIntoMaybeRune(
            maybeParentInterface match {
              case None => functionEnv
              case Some(parentInterface) => parentInterface.interfaceEnv
            },
            lidb.child(),
            PostParser.evalRange(myStackFrameWithoutParams.file, retRange),
            ruleBuilder,
            runeToExplicitType,
            Some(retTypePT))
        }
      }

    maybeRetCoordRune.foreach(retCoordRune => runeToExplicitType.put(retCoordRune.rune, CoordTemplataType()))

    maybeParentInterface match {
      case None =>
      case Some(parentInterface) => {
        if (attrsP.collect({ case AbstractAttributeP(_) => true }).nonEmpty) {
          throw CompileErrorExceptionS(
            RangedInternalErrorS(PostParser.evalRange(file, range), "Dont need abstract here"))
        }
      }
    }

    val maybeBody1 =
      if (maybeParentInterface.nonEmpty || attrsP.collectFirst({ case AbstractAttributeP(_) => }).nonEmpty) {
        AbstractBodyS
      } else if (attrsP.collectFirst({ case ExternAttributeP(_) => }).nonEmpty) {
        if (maybeBody0.nonEmpty) {
          throw CompileErrorExceptionS(ExternHasBody(PostParser.evalRange(file, range)))
        }
        ExternBodyS
      } else if (attrsP.collectFirst({ case BuiltinAttributeP(_, _) => }).nonEmpty) {
        GeneratedBodyS(attrsP.collectFirst({ case BuiltinAttributeP(_, generatorId) => generatorId }).head.str)
      } else {
        val body =
          maybeBody0 match {
            case None => throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Error: function has no body."))
            case Some(x) => x
          }
        val (body1, _, magicParams) =
          scoutBody(
            functionEnv,
            None,
            lidb.child(),
            body,
            // We hand these into scoutBody instead of assembling a StackFrame on our own
            // because we want StackFrame's to be made in one place, where we can centralize the
            // logic for tracking variable uses and so on.
            vassertSome(maybeCaptureDeclarations))
        if (magicParams.nonEmpty) {
          throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Magic param (underscore) in a normal block!"))
        }
        if (body1.closuredNames.nonEmpty) {
          throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Internal error: Body closured names not empty?:\n" + body1.closuredNames))
        }
        CodeBodyS(body1)
      }

    val unfilteredRulesArray = ruleBuilder.toVector

    val unfilteredAttrsP = attrsP

    val filteredAttrs =
      maybeParentInterface match {
        case None => unfilteredAttrsP.filter({ case AbstractAttributeP(_) => false case _ => true })
        case Some(parentInterface) => unfilteredAttrsP
      }

    val funcAttrsS = translateFunctionAttributes(file, filteredAttrs)

    // Filter out any RuneParentEnvLookupSR rules, we don't want these methods to look up these runes
    // from the environment. See MKRFA.
    val rulesArray =
      maybeParentInterface match {
        case None => unfilteredRulesArray
        case Some(parentInterface) => {
          unfilteredRulesArray.filter({
            case RuneParentEnvLookupSR(_, _) => false
            case _ => true
          })
        }
      }

    val runeToPredictedType =
      postParser.predictRuneTypes(
        rangeS,
        userSpecifiedIdentifyingRunes.map(_.rune),
        runeToExplicitType.toMap,
        rulesArray)

    postParser.checkIdentifiability(
      rangeS,
      genericParametersS.map(_.rune.rune),
      rulesArray)

    FunctionS(
      PostParser.evalRange(file, range),
      funcName,
      funcAttrsS,
      genericParametersS,
      runeToPredictedType,
      explicitParams1,
      maybeRetCoordRune,
      rulesArray,
      maybeBody1)
  }

  def scoutInterfaceMember(
    parentInterface: ParentInterface,
    functionP: FunctionP): FunctionS = {
    val file = parentInterface.interfaceEnv.file
    val maybeParentInterface: Option[ParentInterface] = Some(parentInterface)

    scoutFunction(file, functionP, maybeParentInterface)
  }

  def translateFunctionAttributes(file: FileCoordinate, attrsP: Vector[IAttributeP]): Vector[IFunctionAttributeS] = {
    attrsP.map({
      case AbstractAttributeP(_) => vwat() // Should have been filtered out, typingpass cares about abstract directly
      case ExportAttributeP(_) => ExportS(file.packageCoordinate)
      case ExternAttributeP(_) => ExternS(file.packageCoordinate)
      case PureAttributeP(_) => PureS
      case BuiltinAttributeP(_, generatorName) => BuiltinS(generatorName.str)
      case x => vimpl(x.toString)
    })
  }

  def scoutLambda(
    parentStackFrame: StackFrame,
    lambdaFunction0: FunctionP):
  (FunctionS, VariableUses) = {
    val FunctionP(range, headerP, Some(body0)) = lambdaFunction0
    val FunctionHeaderP(_, _, attrsP, maybeGenericParametersP, None, paramsP, returnP) = headerP
    val FunctionReturnP(retRange, maybeInferRet, maybeRetType) = returnP
    val file = parentStackFrame.file

    val codeLocation = PostParser.evalPos(parentStackFrame.file, range.begin)

    vcurious(maybeGenericParametersP.isEmpty)

    val lambdaName = interner.intern(LambdaDeclarationNameS(/*parentStackFrame.name,*/ codeLocation))
    // Every lambda has a closure as its first arg, even if its empty
    val closureStructName = interner.intern(LambdaStructDeclarationNameS(lambdaName))

    val functionEnv =
      FunctionEnvironmentS(
        parentStackFrame.file,
        lambdaName,
        Some(parentStackFrame.parentEnv),
        Set(),
        paramsP.size,
        false)


    val myStackFrameWithoutParams = StackFrame(parentStackFrame.file, lambdaName, functionEnv, None, noDeclarations)

    val lidb = new LocationInDenizenBuilder(Vector())

    val genericParametersP =
      maybeGenericParametersP
        .toVector
        .flatMap(_.params)
        // Filter out any regions, we dont do those yet
        .filter({
          case GenericParameterP(_, _, Some(GenericParameterTypeP(_, RegionTypePR)), _, _) => false
          case _ => true
        })

    val userSpecifiedIdentifyingRunes =
      genericParametersP
        .map({ case GenericParameterP(_, NameP(range, identifyingRuneName), _, _, _) =>
          rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName))
        })

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()

    // We say PerhapsTypeless because they might be anonymous params like in `(_) => { true }`
    // Later on, we'll make identifying runes for these.
    val explicitParamPatternsPerhapsTypeless =
    patternScout.scoutPatterns(
      myStackFrameWithoutParams,
      lidb.child(),
      ruleBuilder,
      runeToExplicitType,
      paramsP.toVector.flatMap(_.patterns))

    val explicitParamPatternsAndIdentifyingRunes =
      explicitParamPatternsPerhapsTypeless.map({
        case a@AtomSP(_, _, _, Some(_), _) => (a, None)
        case AtomSP(range, name, virtuality, None, destructure) => {
          val rune = rules.RuneUsage(range, ImplicitRuneS(lidb.child().consume()))
          runeToExplicitType.put(rune.rune, CoordTemplataType())
          val newParam = patterns.AtomSP(range, name, virtuality, Some(rune), destructure)
          (newParam, Some(rune))
        }
      })
    val explicitParams = explicitParamPatternsAndIdentifyingRunes.map(_._1).map(ParameterS)
    val identifyingRunesFromExplicitParams = explicitParamPatternsAndIdentifyingRunes.flatMap(_._2)

    //    vassert(exportedTemplateParamNames.size == exportedTemplateParamNames.toSet.size)

    val closureParamName = interner.intern(ClosureParamNameS())

    val closureDeclaration =
      VariableDeclarations(Vector(VariableDeclaration(closureParamName)))

    val paramDeclarations =
      explicitParams.map(_.pattern)
        .map(pattern1 => postparsing.VariableDeclarations(patternScout.getParameterCaptures(pattern1)))
        .foldLeft(closureDeclaration)(_ ++ _)

    val (body1, variableUses, lambdaMagicParamNames) =
      scoutBody(
        functionEnv,
        Some(parentStackFrame),
        lidb.child(),
        body0,
        //        body0 match {
        //          case BlockPE(_, ReturnPE(_, _)) => body0
        //          case BlockPE(range, inner) => BlockPE(range, ReturnPE(range, inner))
        //        },
        paramDeclarations)

    if (lambdaMagicParamNames.nonEmpty && (explicitParams.nonEmpty)) {
      throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(PostParser.evalRange(parentStackFrame.file, range), "Cant have a lambda with _ and params"))
    }

    //    val closurePatternId = fate.nextPatternNumber();

    val closureParamPos = PostParser.evalPos(parentStackFrame.file, range.begin)
    val closureParamRange = RangeS(closureParamPos, closureParamPos)
    val closureStructRune = rules.RuneUsage(closureParamRange, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder +=
      LookupSR(
        closureParamRange, closureStructRune, closureStructName.getImpreciseName(interner))
    val closureParamTypeRune = rules.RuneUsage(closureParamRange, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder +=
      AugmentSR(
        closureParamRange,
        closureParamTypeRune,
        BorrowP,
        closureStructRune)

    val closureParamS =
      ParameterS(
        AtomSP(
          closureParamRange,
          Some(CaptureS(closureParamName)),
          None,
          Some(closureParamTypeRune),
          None))

    val magicParams =
      lambdaMagicParamNames.map({
        case mpn@MagicParamNameS(codeLocation) => {
          val magicParamRange = vale.RangeS(codeLocation, codeLocation)
          val magicParamRune = rules.RuneUsage(magicParamRange, MagicParamRuneS(lidb.child().consume()))
          runeToExplicitType.put(magicParamRune.rune, CoordTemplataType())
          val paramS =
            ParameterS(
              AtomSP(
                magicParamRange,
                Some(patterns.CaptureS(mpn)), None, Some(magicParamRune), None))
          paramS
        }
      })

    val genericParametersS =
      genericParametersP.zip(userSpecifiedIdentifyingRunes)
        .map({ case (g, r) =>
          PostParser.scoutGenericParameter(
            templexScout, functionEnv, lidb.child(), runeToExplicitType, ruleBuilder, g, r)
        }) ++
        // Lambdas identifying runes are determined by their magic params.
        // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
        magicParams.map(param => {
          GenericParameterS(param.pattern.range, vassertSome(param.pattern.coordRune), Vector(), None)
        })

    // Lambdas identifying runes are determined by their magic params.
    // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
    val identifyingRunes =
    identifyingRunesFromExplicitParams ++
      magicParams.map(param => vassertSome(param.pattern.coordRune))

    val totalParams = Vector(closureParamS) ++ explicitParams ++ magicParams;

    val maybeRetCoordRune =
      (maybeInferRet, maybeRetType) match {
        case (_, None | Some(RegionRunePT(_, _))) => None // Infer the return
        case (None, Some(retTypePT)) => {
          templexScout.translateMaybeTypeIntoMaybeRune(
            functionEnv,
            lidb.child(),
            PostParser.evalRange(myStackFrameWithoutParams.file, retRange),
            ruleBuilder,
            runeToExplicitType,
            Some(retTypePT))
        }
        case (Some(_), Some(_)) => throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(PostParser.evalRange(parentStackFrame.file, range), "Can't have return type and infer-return at the same time"))
      }
    maybeRetCoordRune.foreach(retCoordRune => runeToExplicitType.put(retCoordRune.rune, CoordTemplataType()))

    postParser.checkIdentifiability(
      closureParamRange,
      identifyingRunes.map(_.rune),
      ruleBuilder.toVector)

    val function1 =
      FunctionS(
        PostParser.evalRange(parentStackFrame.file, range),
        lambdaName,
        translateFunctionAttributes(parentStackFrame.file, attrsP),
        genericParametersS,
        runeToExplicitType.toMap,
        totalParams,
        maybeRetCoordRune,
        ruleBuilder.toVector,
        CodeBodyS(body1))
    (function1, variableUses)
  }

  // Returns:
  // - Body.
  // - Uses of parent variables.
  // - Magic params made/used inside.
  private def scoutBody(
    functionEnv: FunctionEnvironmentS,
    // This might be the block containing the lambda that we're evaluating now.
    parentStackFrame: Option[StackFrame],
    lidb: LocationInDenizenBuilder,
    body0: BlockPE,
    initialDeclarations: VariableDeclarations):
  (BodySE, VariableUses, Vector[MagicParamNameS]) = {
    val functionBodyEnv = functionEnv.child()

    // There's an interesting consequence of calling this function here...
    // If we have a lone lookup node, like "m = Marine(); m;" then that
    // 'm' will be turned into an expression, which means that's how it's
    // destroyed. So, thats how we destroy things before their time.
    val (block1, selfUses, childUses) =
    expressionScout.newBlock(
      functionBodyEnv,
      parentStackFrame,
      lidb.child(),
      PostParser.evalRange(functionBodyEnv.file, body0.range),
      initialDeclarations,
      (stackFrame1, lidb) => {
        expressionScout.scoutExpressionAndCoerce(
          stackFrame1, lidb, body0.inner, UseP)
      })

    vcurious(
      childUses.uses.map(_.name).collect({ case mpn@MagicParamNameS(_) => mpn }).isEmpty)
    val magicParamNames =
      selfUses.uses.map(_.name).collect({ case mpn@MagicParamNameS(_) => mpn })
    val magicParamVars = magicParamNames.map(n => VariableDeclaration(n))

    val magicParamLocals =
      magicParamVars.map({ declared =>
        LocalS(
          declared.name,
          selfUses.isBorrowed(declared.name),
          selfUses.isMoved(declared.name),
          selfUses.isMutated(declared.name),
          childUses.isBorrowed(declared.name),
          childUses.isMoved(declared.name),
          childUses.isMutated(declared.name))
      })
    val bodyRangeS = PostParser.evalRange(functionBodyEnv.file, body0.range)
    val block1WithParamLocals =
      BlockSE(bodyRangeS, block1.locals ++ magicParamLocals, block1.expr)

    val allUses =
      selfUses.combine(childUses, {
        case (None, other) => other
        case (other, None) => other
        case (Some(NotUsed), other) => other
        case (other, Some(NotUsed)) => other
        case (Some(Used), Some(Used)) => Some(Used)
      })

    // We're trying to figure out which variables from parent environments
    // we're using.
    // This is so we can remember in BodySE which variables we're using from
    // containing functions (so we can define the struct which we take in as
    // an implicit first parameter), and also so we can report those upward
    // so if we're using variables from our grandparent, our parent can know
    // that it needs to capture them for us.
    val usesOfParentVariables =
    allUses.uses.filter(use => {
      if (block1WithParamLocals.locals.exists(_.varName == use.name)) {
        // This is a use of a variable declared in this function.
        false
      } else {
        use.name match {
          case MagicParamNameS(_) => {
            // We're using a magic param, which we'll have in this function's params.
            false
          }
          case _ => {
            // This is a use of a variable from somewhere above.
            true
          }
        }
      }
    })

    val bodySE = postparsing.BodySE(PostParser.evalRange(functionBodyEnv.file, body0.range), usesOfParentVariables.map(_.name), block1WithParamLocals)
    (bodySE, VariableUses(usesOfParentVariables), magicParamNames)
  }

}

