package dev.vale.postparsing

import dev.vale.postparsing.rules.{AugmentSR, IRulexSR, LookupSR, RuleScout, RuneUsage, TemplexScout}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import PostParser.noDeclarations
import dev.vale
import dev.vale.{FileCoordinate, Interner, RangeS, postparsing, vassertSome, vcurious, vimpl, vwat}
import dev.vale.parsing.ast.{AbstractAttributeP, AbstractP, BlockPE, BorrowP, BuiltinAttributeP, ExportAttributeP, ExternAttributeP, FunctionHeaderP, FunctionP, FunctionReturnP, IAttributeP, IdentifyingRuneP, NameP, PureAttributeP, RegionTypePR, RulePUtils, TypeRuneAttributeP, UseP}
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

class FunctionScout(
    postParser: PostParser,
    interner: Interner,
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
      interner
    )

  def scoutTopLevelFunction(file: FileCoordinate, functionP: FunctionP): FunctionS = {
    val FunctionP(
      range,
      FunctionHeaderP(_,
        Some(NameP(originalNameRange, originalCodeName)),
        attributes,
        userSpecifiedIdentifyingRuneNames,
        templateRulesP,
        paramsP,
        FunctionReturnP(retRange, maybeInferRet, maybeRetType)),
      maybeBody0
    ) = functionP

    val rangeS = PostParser.evalRange(file, retRange)
    val codeLocation = PostParser.evalPos(file, range.begin)
    val name =
      (file, originalCodeName) match {
        case (FileCoordinate("v", Vector("builtins", "arrays"), "arrays.vale"), "__free_replaced") => {
          interner.intern(postparsing.FreeDeclarationNameS(PostParser.evalPos(file, originalNameRange.begin)))
        }
        case (FileCoordinate("", Vector(), "arrays.vale"), "__free_replaced") => {
          interner.intern(FreeDeclarationNameS(PostParser.evalPos(file, originalNameRange.begin)))
        }
        case (_, n) => interner.intern(postparsing.FunctionNameS(n, codeLocation))
      }


    val lidb = new LocationInDenizenBuilder(Vector())

    val userSpecifiedIdentifyingRunes =
      userSpecifiedIdentifyingRuneNames
        .toVector
        .flatMap(_.runes)
        // Filter out any regions, we dont do those yet
        .filter({ case IdentifyingRuneP(_, _, attributes) => !attributes.exists({ case TypeRuneAttributeP(_, RegionTypePR) => true case _ => false }) })
        .map({ case IdentifyingRuneP(_, NameP(range, identifyingRuneName), _) =>
          rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName))
        })

    // See: Must Scan For Declared Runes First (MSFDRF)
    val userRunesFromRules =
      templateRulesP
        .toVector
        .flatMap(rules => RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rules.rules))
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(file, range), CodeRuneS(identifyingRuneName)) })
    val userDeclaredRunes = (userSpecifiedIdentifyingRunes ++ userRunesFromRules).distinct

    val functionEnv = postparsing.FunctionEnvironment(file, name, None, userDeclaredRunes.map(_.rune).toSet, paramsP.size, false)

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()
    ruleScout.translateRulexes(
      functionEnv,
      lidb.child(),
      ruleBuilder,
      runeToExplicitType,
      templateRulesP.toVector.flatMap(_.rules))

    val myStackFrameWithoutParams = postparsing.StackFrame(file, name, functionEnv, None, noDeclarations)

    val patternsP =
      paramsP.toVector.map(_.patterns).flatten.zipWithIndex.map({
        case (pattern, index) => {
          if (pattern.templex.isEmpty) {
            throw CompileErrorExceptionS(LightFunctionMustHaveParamTypes(rangeS, index))
          }
          pattern
        }
      })
    val explicitParamsPatterns1 =
      patternScout.scoutPatterns(
        myStackFrameWithoutParams, lidb.child(), ruleBuilder, runeToExplicitType, patternsP)

    val explicitParams1 = explicitParamsPatterns1.map(ParameterS)
    val captureDeclarations =
      explicitParams1
        .map(explicitParam1 => postparsing.VariableDeclarations(patternScout.getParameterCaptures(explicitParam1.pattern)))
        .foldLeft(noDeclarations)(_ ++ _)

    val maybeRetCoordRune =
      (maybeInferRet, maybeRetType) match {
        case (None, None) => {
          // If nothing's present, assume void
          val rangeS = PostParser.evalRange(file, retRange)
          val rune = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
          ruleBuilder += LookupSR(rangeS, rune, interner.intern(CodeNameS("void")))
          Some(rune)
        }
        case (Some(_), None) => None // Infer the return
        case (None, Some(retTypePT)) => {
          val rune =
            templexScout.translateMaybeTypeIntoMaybeRune(
              functionEnv,
              lidb,
              PostParser.evalRange(file, retRange),
              ruleBuilder,
              runeToExplicitType,
              Some(retTypePT))
          rune
        }
        case (Some(_), Some(_)) => throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(PostParser.evalRange(file, range), "Can't have return type and infer-return at the same time"))
      }
    maybeRetCoordRune.foreach(retCoordRune => runeToExplicitType.put(retCoordRune.rune, CoordTemplataType))

    val body1 =
      if (attributes.collectFirst({ case AbstractAttributeP(_) => }).nonEmpty) {
        AbstractBodyS
      } else if (attributes.collectFirst({ case ExternAttributeP(_) => }).nonEmpty) {
        if (maybeBody0.nonEmpty) {
          throw CompileErrorExceptionS(ExternHasBody(PostParser.evalRange(file, range)))
        }
        ExternBodyS
      } else if (attributes.collectFirst({ case BuiltinAttributeP(_, _) => }).nonEmpty) {
        GeneratedBodyS(attributes.collectFirst({ case BuiltinAttributeP(_, generatorId) => generatorId}).head.str)
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
            // We hand these into scoutBody instead of assembling a StackFrame on our own because we want
            // StackFrame's to be made in one place, where we can centralize the logic for tracking variable
            // uses and so on.
            captureDeclarations,
            false)
        if (magicParams.nonEmpty) {
          throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Magic param (underscore) in a normal block!"))
        }
        if (body1.closuredNames.nonEmpty) {
          throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Internal error: Body closured names not empty?:\n" + body1.closuredNames))
        }
        CodeBodyS(body1)
      }

    val attrsS = translateFunctionAttributes(file, attributes.filter({ case AbstractAttributeP(_) => false case _ => true}))

    val runeToPredictedType =
      postParser.predictRuneTypes(
        rangeS,
        userSpecifiedIdentifyingRunes.map(_.rune),
        runeToExplicitType.toMap,
        ruleBuilder.toArray)

    postParser.checkIdentifiability(
      rangeS,
      userSpecifiedIdentifyingRunes.map(_.rune),
      ruleBuilder.toArray)

    postparsing.FunctionS(
      PostParser.evalRange(file, range),
      name,
      attrsS,
      userSpecifiedIdentifyingRunes,
      runeToPredictedType,
      explicitParams1,
      maybeRetCoordRune,
      ruleBuilder.toArray,
      body1)
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
    val FunctionP(range,
      FunctionHeaderP(_,
        _, attrsP, userSpecifiedIdentifyingRuneNames, None, paramsP, FunctionReturnP(retRange, maybeInferRet, maybeRetType)),
      Some(body0)) = lambdaFunction0;
    val codeLocation = PostParser.evalPos(parentStackFrame.file, range.begin)

    vcurious(userSpecifiedIdentifyingRuneNames.isEmpty)

    val lambdaName = interner.intern(LambdaDeclarationNameS(/*parentStackFrame.name,*/ codeLocation))
    // Every lambda has a closure as its first arg, even if its empty
    val closureStructName = interner.intern(LambdaStructDeclarationNameS(lambdaName))

    val functionEnv =
      FunctionEnvironment(
        parentStackFrame.file,
        lambdaName,
        Some(parentStackFrame.parentEnv),
        Set(),
        paramsP.size,
        false)


    val myStackFrameWithoutParams = StackFrame(parentStackFrame.file, lambdaName, functionEnv, None, noDeclarations)

    val lidb = new LocationInDenizenBuilder(Vector())

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
        case a @ AtomSP(_, _, _, Some(_), _) => (a, None)
        case AtomSP(range, name, virtuality, None, destructure) => {
          val rune = rules.RuneUsage(range, ImplicitRuneS(lidb.child().consume()))
          runeToExplicitType.put(rune.rune, CoordTemplataType)
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
        paramDeclarations,
        true)

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
          case mpn @ MagicParamNameS(codeLocation) => {
            val magicParamRange = vale.RangeS(codeLocation, codeLocation)
            val magicParamRune = rules.RuneUsage(magicParamRange, MagicParamRuneS(lidb.child().consume()))
            runeToExplicitType.put(magicParamRune.rune,CoordTemplataType)
            val paramS =
              ParameterS(
                AtomSP(
                  magicParamRange,
                  Some(patterns.CaptureS(mpn)),None,Some(magicParamRune),None))
            paramS
          }
        })

    // Lambdas identifying runes are determined by their magic params.
    // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
    val identifyingRunes =
      identifyingRunesFromExplicitParams ++
      magicParams.map(param => vassertSome(param.pattern.coordRune))

    val totalParams = Vector(closureParamS) ++ explicitParams ++ magicParams;

    val maybeRetCoordRune =
      (maybeInferRet, maybeRetType) match {
        case (_, None) => None // Infer the return
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
    maybeRetCoordRune.foreach(retCoordRune => runeToExplicitType.put(retCoordRune.rune, CoordTemplataType))

    postParser.checkIdentifiability(
      closureParamRange,
      identifyingRunes.map(_.rune),
      ruleBuilder.toArray)

    val function1 =
      postparsing.FunctionS(
        PostParser.evalRange(parentStackFrame.file, range),
        lambdaName,
        translateFunctionAttributes(parentStackFrame.file, attrsP),
        identifyingRunes,
        runeToExplicitType.toMap,
        totalParams,
        maybeRetCoordRune,
        ruleBuilder.toArray,
        CodeBodyS(body1))
    (function1, variableUses)
  }

  // Returns:
  // - Body.
  // - Uses of parent variables.
  // - Magic params made/used inside.
  private def scoutBody(
    functionEnv: FunctionEnvironment,
    // This might be the block containing the lambda that we're evaluating now.
    parentStackFrame: Option[StackFrame],
    lidb: LocationInDenizenBuilder,
    body0: BlockPE,
    initialDeclarations: VariableDeclarations,
    resultRequested: Boolean):
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
        resultRequested,
        (stackFrame1, lidb, resultRequested) => {
          expressionScout.scoutExpressionAndCoerce(
            stackFrame1, lidb, body0.inner, UseP, resultRequested)
        })

    vcurious(
      childUses.uses.map(_.name).collect({ case mpn @ MagicParamNameS(_) => mpn }).isEmpty)
    val magicParamNames =
      selfUses.uses.map(_.name).collect({ case mpn @ MagicParamNameS(_) => mpn })
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

  def scoutInterfaceMember(
    interfaceEnv: Environment,
    interfaceIdentifyingRunes: Array[RuneUsage],
    interfaceRules: Array[IRulexSR],
    interfaceRuneToExplicitType: Map[IRuneS, ITemplataType],
    functionP: FunctionP): FunctionS = {
    val FunctionP(
      range,
      FunctionHeaderP(_,
        Some(NameP(_, codeName)),
        attrsP,
        userSpecifiedIdentifyingRuneNames,
        templateRulesP,
        maybeParamsP,
        FunctionReturnP(retRange, maybeInferRet, maybeRetType)),
      None) = functionP;
    val rangeS = PostParser.evalRange(interfaceEnv.file, range)
    val retRangeS = PostParser.evalRange(interfaceEnv.file, retRange)

    maybeParamsP match {
      case None =>
      case Some(paramsP) => {
        if (!paramsP.patterns.exists(_.virtuality match { case Some(AbstractP(_)) => true case _ => false })) {
          throw CompileErrorExceptionS(InterfaceMethodNeedsSelf(PostParser.evalRange(interfaceEnv.file, range)))
        }
      }
    }

    val codeLocation = PostParser.evalPos(interfaceEnv.file, range.begin)
    val funcName = interner.intern(FunctionNameS(codeName, codeLocation))
    val explicitIdentifyingRunes: Vector[RuneUsage] =
      userSpecifiedIdentifyingRuneNames
          .toVector
        .flatMap(_.runes)
        // Filter out any regions, we dont do those yet
        .filter({ case IdentifyingRuneP(_, _, attributes) => !attributes.exists({ case TypeRuneAttributeP(_, RegionTypePR) => true case _ => false }) })
        .map({ case IdentifyingRuneP(_, NameP(range, identifyingRuneName), _) => rules.RuneUsage(PostParser.evalRange(interfaceEnv.file, range), CodeRuneS(identifyingRuneName)) })

    // See: Must Scan For Declared Runes First (MSFDRF)
    val userRunesFromRules =
      templateRulesP
        .toVector
        .flatMap(rules => RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rules.rules))
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(PostParser.evalRange(interfaceEnv.file, range), CodeRuneS(identifyingRuneName)) })

    val userDeclaredRunes = (explicitIdentifyingRunes ++ userRunesFromRules).distinct
    val identifyingRunes = explicitIdentifyingRunes ++ interfaceIdentifyingRunes

    val lidb = new LocationInDenizenBuilder(Vector())

    val ruleBuilder = ArrayBuffer[IRulexSR]()
    ruleBuilder ++= interfaceRules
    val runeToExplicitType = mutable.HashMap[IRuneS, ITemplataType]()
    runeToExplicitType ++= interfaceRuneToExplicitType

    ruleScout.translateRulexes(interfaceEnv, lidb.child(), ruleBuilder, runeToExplicitType, templateRulesP.toVector.flatMap(_.rules))

    val functionEnv =
      postparsing.FunctionEnvironment(
        interfaceEnv.file, funcName, Some(interfaceEnv), userDeclaredRunes.map(_.rune).toSet, maybeParamsP.size, true)
    val myStackFrame = StackFrame(interfaceEnv.file, funcName, functionEnv, None, noDeclarations)
    val patternsS =
      patternScout.scoutPatterns(myStackFrame, lidb.child(), ruleBuilder, runeToExplicitType, maybeParamsP.toVector.flatMap(_.patterns))

    val paramsS = patternsS.map(ParameterS)

    val maybeReturnRune =
      (maybeInferRet, maybeRetType) match {
        case (None, None) => {
          // If nothing's present, assume void
          val rune = rules.RuneUsage(retRangeS, ImplicitRuneS(lidb.child().consume()))
          ruleBuilder += rules.LookupSR(retRangeS, rune, interner.intern(CodeNameS("void")))
          Some(rune)
        }
        case (Some(_), None) => {
          throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Can't infer the return type of an interface method!"))
        }
        case (None, Some(retTypePT)) => {
          templexScout.translateMaybeTypeIntoMaybeRune(
            interfaceEnv,
            lidb.child(),
            PostParser.evalRange(myStackFrame.file, retRange),
            ruleBuilder,
            runeToExplicitType,
            Some(retTypePT))
        }
        case (Some(_), Some(_)) => throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(PostParser.evalRange(myStackFrame.file, range), "Can't have return type and infer-return at the same time"))
      }
    maybeReturnRune.foreach(retCoordRune => runeToExplicitType.put(retCoordRune.rune, CoordTemplataType))

    if (attrsP.collect({ case AbstractAttributeP(_) => true  }).nonEmpty) {
      throw CompileErrorExceptionS(RangedInternalErrorS(PostParser.evalRange(interfaceEnv.file, range), "Dont need abstract here"))
    }

    postParser.checkIdentifiability(
      rangeS,
      identifyingRunes.map(_.rune),
      ruleBuilder.toArray)

    postparsing.FunctionS(
      PostParser.evalRange(functionEnv.file, range),
      funcName,
      translateFunctionAttributes(functionEnv.file, attrsP),
//      knowableValueRunes,
      identifyingRunes,
      runeToExplicitType.toMap,
//      localRunes,
//      maybePredictedType,
      paramsS,
      maybeReturnRune,
//      isTemplate,
      ruleBuilder.toArray,
      AbstractBodyS)
  }
}
