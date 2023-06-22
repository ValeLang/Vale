package dev.vale.postparsing

import dev.vale.postparsing.rules.{AugmentSR, IRulexSR, MaybeCoercingLookupSR, RuleScout, RuneUsage, TemplexScout}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import PostParser.{evalRange, noDeclarations, noVariableUses}
import dev.vale
import dev.vale.lexing.RangeL
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

sealed trait IFunctionParent

case class FunctionNoParent() extends IFunctionParent

case class ParentInterface(
  interfaceEnv: EnvironmentS,
  interfaceGenericParams: Vector[GenericParameterS],
  interfaceRules: Vector[IRulexSR],
  interfaceRuneToExplicitType: Map[IRuneS, ITemplataType]
) extends IFunctionParent

case class ParentFunction(
  parentStackFrame: StackFrame
) extends IFunctionParent

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

  def scoutFunction(
    file: FileCoordinate,
    functionP: FunctionP,
    maybeParent: IFunctionParent):
  (FunctionS, VariableUses) = {
    val FunctionP(range, headerP, maybeBody0) = functionP;
    val FunctionHeaderP(headerRange, maybeName, attrsP, maybeGenericParametersP, templateRulesP, maybeParamsP, returnP) = headerP
    val FunctionReturnP(retRange, maybeRetType) = returnP

    val headerRangeS = PostParser.evalRange(file, headerRange)
    val rangeS = PostParser.evalRange(file, range)
    val codeLocation = rangeS.begin
    val retRangeS = PostParser.evalRange(file, retRange)


    maybeParent match {
      case FunctionNoParent() =>
      case ParentInterface(_, _, _, _) =>
      case ParentFunction(_) => {
        vcurious(maybeGenericParametersP.isEmpty)
      }
    }

    val funcName =
      maybeParent match {
        case FunctionNoParent() | ParentInterface(_, _, _, _) => {
          val NameP(_, codeName) = vassertSome(maybeName)
          interner.intern(FunctionNameS(codeName, codeLocation))
        }
        case ParentFunction(_) => {
          vassert(maybeName.isEmpty)
          interner.intern(LambdaDeclarationNameS(codeLocation))
        }
      }

    val genericParametersP =
      maybeGenericParametersP
        .toVector
        .flatMap(_.params)

    val userSpecifiedIdentifyingRunes =
      genericParametersP
        .map({ case GenericParameterP(_, NameP(range, identifyingRuneName), _, _, _, _) =>
          rules.RuneUsage(rangeS, CodeRuneS(identifyingRuneName))
        })

    // See: Must Scan For Declared Runes First (MSFDRF)
    val userRunesFromRules =
      templateRulesP
        .toVector
        .flatMap(rules => RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rules.rules))
        .map({ case NameP(range, identifyingRuneName) => rules.RuneUsage(rangeS, CodeRuneS(identifyingRuneName)) })
    val userDeclaredRunes = (userSpecifiedIdentifyingRunes ++ userRunesFromRules).distinct

    maybeParent match {
      case ParentInterface(_, _, _, _) => vassert(userDeclaredRunes.isEmpty)
      case _ =>
    }

    val lidb = new LocationInDenizenBuilder(Vector())

    maybeParent match {
      case FunctionNoParent() =>
      case ParentFunction(_) =>
      case ParentInterface(_, _, _, _) => {
        maybeParamsP match {
          case None =>
          case Some(paramsP) => {
            if (!paramsP.params.exists(_.virtuality match { case Some(AbstractP(_)) => true case _ => false })) {
              throw CompileErrorExceptionS(InterfaceMethodNeedsSelf(rangeS))
            }
          }
        }
      }
    }

    val parentEnv =
      maybeParent match {
        case FunctionNoParent() => None
        case ParentFunction(parentStackFrame) => Some(parentStackFrame.parentEnv)
        case ParentInterface(interfaceEnv, _, _, _) => Some(interfaceEnv)
      }
    val isInterfaceInternalMethod =
      maybeParent match {
        case FunctionNoParent() => false
        case ParentFunction(parentStackFrame) => false
        case ParentInterface(_, _, _, _) => true
      }
    val functionEnv =
      postparsing.FunctionEnvironmentS(
        file, funcName, parentEnv, userDeclaredRunes.map(_.rune).toSet, maybeParamsP.size, isInterfaceInternalMethod)


    val ruleBuilder = ArrayBuffer[IRulexSR]()
    val runeToExplicitType = mutable.ArrayBuffer[(IRuneS, ITemplataType)]()

    val (defaultRegionRuneRangeS, defaultRegionRuneS, maybeRegionGenericParam) =
      maybeBody0.flatMap(_.maybeDefaultRegion) match {
        case None => {
          val regionRange = RangeS(headerRangeS.end, headerRangeS.end)
          val rune = DenizenDefaultRegionRuneS(funcName)
          vassert(!runeToExplicitType.exists(_._1 == rune))
          vregionmut() // Put this back in with regions
          // runeToExplicitType += ((rune, RegionTemplataType()))
          val implicitRegionGenericParam =
            GenericParameterS(
              regionRange, RuneUsage(regionRange, rune), RegionGenericParameterTypeS(ReadWriteRegionS), None)
          (regionRange, rune, Some(implicitRegionGenericParam))
        }
        case Some(RegionRunePT(regionRange, regionName)) => {
          val rune = CodeRuneS(vassertSome(regionName).str) // impl isolates
          if (!functionEnv.allDeclaredRunes().contains(rune)) {
            throw CompileErrorExceptionS(CouldntFindRuneS(PostParser.evalRange(file, range), rune.name.str))
          }
          (evalRange(file, regionRange), rune, None)
        }
      }

    maybeParent match {
      case FunctionNoParent() => {
        ruleScout.translateRulexes(
          functionEnv,
          lidb.child(),
          ruleBuilder,
          runeToExplicitType,
          defaultRegionRuneS,
          templateRulesP.toVector.flatMap(_.rules))
      }
      case ParentFunction(_) => {
        vassert(templateRulesP.isEmpty)
      }
      case ParentInterface(interfaceEnv, _, interfaceRules, interfaceRuneToExplicitType) => {
        ruleBuilder ++= interfaceRules
        runeToExplicitType ++= interfaceRuneToExplicitType
        ruleScout.translateRulexes(
          interfaceEnv,
          lidb.child(),
          ruleBuilder,
          runeToExplicitType,
          defaultRegionRuneS,
          templateRulesP.toVector.flatMap(_.rules))
      }
    }

//    val isPure =
//      functionP.header.attributes
//        .exists({ case PureAttributeP(_) => true case _ => false })
//    val isAdditive =
//      functionP.header.attributes
//        .exists({ case AdditiveAttributeP(_) => true case _ => false })

    // We'll add the implicit runes to the end, see IRRAE.
    val functionUserSpecifiedGenericParametersS =
      genericParametersP.zip(userSpecifiedIdentifyingRunes)
        .map({ case (g, r) =>
          PostParser.scoutGenericParameter(
            templexScout, functionEnv, lidb.child(), runeToExplicitType, ruleBuilder, defaultRegionRuneS, g, r)
        })

    val myStackFrameWithoutParams =
      StackFrame(file, funcName, functionEnv, None, defaultRegionRuneS, 0, noDeclarations)

    val paramsP =
      maybeParamsP.toVector.flatMap(_.params).map(param => {
        maybeParent match {
          case FunctionNoParent() | ParentInterface(_, _, _, _) => {
            // Should have been caught by LightFunctionMustHaveParamTypes error in parser,
            vassert(vassertSome(param.pattern).templex.nonEmpty)
          }
          case ParentFunction(_) =>
        }
        param
      })

    // We say PerhapsTypeless because we're in a lambda, they might be anonymous params
    // like in `(_) => { true }`
    // Later on, we'll make identifying runes for these.

    val explicitParamsS =
      paramsP
        .map({
          case ParameterP(rangeL, maybeAbstractP, maybePreChecked, maybeSelfBorrow, maybePattern) => {
            val rangeS = evalRange(file, rangeL)
            val maybeAbstractS =
              maybeAbstractP match {
                case None => None
                case Some(AbstractP(range)) => {
                  Some(AbstractSP(PostParser.evalRange(file, range), myStackFrameWithoutParams.parentEnv.isInterfaceInternalMethod))
                }
              }
            (maybeSelfBorrow, maybePattern) match {
              case (Some(selfBorrow), None) => {
                val rune = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
                runeToExplicitType += ((rune.rune, CoordTemplataType()))
                val patternS =
                  AtomSP(rangeS, Some(CaptureS(CodeVarNameS(keywords.self), false)), Some(rune), None)
                ParameterS(rangeS, maybeAbstractS, maybePreChecked.nonEmpty, patternS)
              }
              case (None, Some(patternP)) => {
                val patternPerhapsWithoutCoordRuneS =
                  patternScout.translatePattern(
                    myStackFrameWithoutParams,
                    lidb.child(),
                    ruleBuilder,
                    runeToExplicitType,
                    patternP)
                val patternS =
                  patternPerhapsWithoutCoordRuneS.coordRune match {
                    case None => {
                      val rune = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
                      runeToExplicitType += ((rune.rune, CoordTemplataType()))
                      patternPerhapsWithoutCoordRuneS.copy(coordRune = Some(rune))
                    }
                    case Some(_) => patternPerhapsWithoutCoordRuneS
                  }
                ParameterS(rangeS, maybeAbstractS, maybePreChecked.nonEmpty, patternS)
              }
            }
          }
        })
//    val explicitParamsS = explicitParamPatternsAndIdentifyingRunes.map(_._1).map(ParameterS)
//    val identifyingRunesFromExplicitParams = explicitParamPatternsAndIdentifyingRunes.flatMap(_._2)

    // Only if the function actually has a body
    val maybeCaptureDeclarations =
      maybeBody0 match {
        case None => None
        case Some(_) => {
          val firstParams =
            maybeParent match {
              case FunctionNoParent() => noDeclarations
              case ParentInterface(_, _, _, _) => noDeclarations
              case ParentFunction(_) => {
                // Every lambda has a closure as its first arg, even if its empty
                val closureParamName = interner.intern(ClosureParamNameS(rangeS.begin))
                val closureDeclaration =
                  VariableDeclarations(Vector(VariableDeclaration(closureParamName)))
                closureDeclaration
              }
            }
          Some(
            explicitParamsS
              .map(_.pattern)
              .map(pattern1 => {
                postparsing.VariableDeclarations(patternScout.getParameterCaptures(pattern1))
              })
              .foldLeft(firstParams)(_ ++ _))
        }
      }

    val maybeRetCoordRune =
      maybeRetType match {
        case None | Some(RegionRunePT(_, _)) => {
          maybeParent match {
            case ParentFunction(_) => {
              None // Infer the return
            }
            case FunctionNoParent() | ParentInterface(_, _, _, _) => {
              // If nothing's present, assume void
              val rangeS = PostParser.evalRange(file, retRange)
              val rune = rules.RuneUsage(rangeS, ImplicitRuneS(lidb.child().consume()))
              ruleBuilder +=
                MaybeCoercingLookupSR(
                  rangeS,
                  rune,
                  interner.intern(CodeNameS(keywords.void)))
              Some(rune)
            }
          }
        }
        case Some(retTypePT) => {
          templexScout.translateMaybeTypeIntoMaybeRune(
            maybeParent match {
              case FunctionNoParent() => functionEnv
              case ParentFunction(_) => functionEnv
              case ParentInterface(interfaceEnv, _, _, _) => interfaceEnv
            },
            lidb.child(),
            PostParser.evalRange(myStackFrameWithoutParams.file, retRange),
            ruleBuilder,
            runeToExplicitType,
            defaultRegionRuneS,
            Some(retTypePT))
        }
      }

    maybeRetCoordRune.foreach(retCoordRune => runeToExplicitType += ((retCoordRune.rune, CoordTemplataType())))

    maybeParent match {
      case FunctionNoParent() =>
      case ParentFunction(_) =>
      case ParentInterface(_, _, _, _) => {
        if (attrsP.collect({ case AbstractAttributeP(_) => true }).nonEmpty) {
          throw CompileErrorExceptionS(
            RangedInternalErrorS(rangeS, "Dont need abstract here"))
        }
      }
    }

    val extraGenericParamsFromParentS =
      (maybeParent match {
        case FunctionNoParent() => Vector()
        case ParentFunction(_) => Vector()
        case ParentInterface(_, interfaceGenericParams, _, _) => interfaceGenericParams
      })

    val (maybeBody1, variableUses, extraGenericParamsFromBodyS, maybeClosureParam, magicParams) =
      if (maybeParent match { case ParentInterface(_, _, _, _) => true case _ => false }) {
        val bodyS = AbstractBodyS
        (bodyS, noVariableUses, Vector(), None, Vector())
      } else if (attrsP.collectFirst({ case AbstractAttributeP(_) => }).nonEmpty) {
        val bodyS = AbstractBodyS
        (bodyS, noVariableUses, Vector(), None, Vector())
      } else if (attrsP.collectFirst({ case ExternAttributeP(_) => }).nonEmpty) {
        if (maybeBody0.nonEmpty) {
          throw CompileErrorExceptionS(ExternHasBody(PostParser.evalRange(file, range)))
        }
        val bodyS = ExternBodyS
        (bodyS, noVariableUses, Vector(), None, Vector())
      } else if (attrsP.collectFirst({ case BuiltinAttributeP(_, _) => }).nonEmpty) {
        val bodyS =
          GeneratedBodyS(
            attrsP.collectFirst({ case BuiltinAttributeP(_, generatorId) => generatorId }).head.str)
        (bodyS, noVariableUses, Vector(), None, Vector())
      } else {
        val captureDeclarations = vassertSome(maybeCaptureDeclarations)
        val bodyP =
          maybeBody0 match {
            case None => {
              throw CompileErrorExceptionS(
                postparsing.RangedInternalErrorS(rangeS, "Error: function has no body."))
            }
            case Some(x) => x
          }

        val parentStackFrame =
          maybeParent match {
            case FunctionNoParent() | ParentInterface(_, _, _, _) => None
            case ParentFunction(parentStackFrame) => Some(parentStackFrame)
          }
        val (body1, variableUses, lambdaMagicParamNames) =
          scoutBody(
            functionEnv,
            parentStackFrame,
            lidb.child(),
            defaultRegionRuneS,
            bodyP,
            // We hand these into scoutBody instead of assembling a StackFrame on our own
            // because we want StackFrame's to be made in one place, where we can centralize the
            // logic for tracking variable uses and so on.
            captureDeclarations)

        val (extraGenericParamsFromBodyS, maybeClosureParam, magicParams) =
          maybeParent match {
            case FunctionNoParent() | ParentInterface(_, _, _, _) => {
              if (lambdaMagicParamNames.nonEmpty) {
                throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Magic param (underscore) in a normal block!"))
              }
              if (body1.closuredNames.nonEmpty) {
                throw CompileErrorExceptionS(postparsing.RangedInternalErrorS(rangeS, "Internal error: Body closured names not empty?:\n" + body1.closuredNames))
              }
              (Vector(), None, Vector())
            }
            case ParentFunction(parentStackFrame) => {
              if (lambdaMagicParamNames.nonEmpty && (explicitParamsS.nonEmpty)) {
                throw CompileErrorExceptionS(
                  RangedInternalErrorS(rangeS, "Cant have a lambda with _ and params"))
              }

              val closureStructKindRune = ImplicitRuneS(lidb.child().consume())
              val closureStructRegionRune =
                ImplicitRegionRuneS(closureStructKindRune)
              val closureStructCoordRune = ImplicitRuneS(lidb.child().consume())

              val closureParamS =
                createClosureParam(
                  range,
                  funcName,
                  lidb,
                  ruleBuilder,
                  runeToExplicitType,
                  parentStackFrame,
                  closureStructRegionRune,
                  closureStructKindRune,
                  closureStructCoordRune)

              val magicParams =
                createMagicParameters(lidb, lambdaMagicParamNames, runeToExplicitType)

              val extraGenericParamsFromBodyS =
                // Lambdas identifying runes are determined by their magic params.
                // See: Lambdas Dont Need Explicit Identifying Runes (LDNEIR)
                magicParams.flatMap(param => {
                  val coordRune = vassertSome(param.pattern.coordRune)
//                  val implicitRegionRune =
//                    RuneUsage(
//                      param.pattern.range,
//                      ImplicitRegionRuneS(vassertSome(param.pattern.coordRune).rune))
                  List(
//                    GenericParameterS(
//                      param.pattern.range,
//                      implicitRegionRune,
//                      RegionTemplataType(),
//                      None,
//                      Vector(),
//                      None),
                    GenericParameterS(
                      param.pattern.range,
                      coordRune,
                      CoordGenericParameterTypeS(None, true, false),
                      None))
                })
              (extraGenericParamsFromBodyS, Some(closureParamS), magicParams)
            }
          }
        (CodeBodyS(body1), variableUses, extraGenericParamsFromBodyS, maybeClosureParam, magicParams)
      }

    val totalParamsS = maybeClosureParam.toVector ++ explicitParamsS ++ magicParams;

    val genericParametersS =
      extraGenericParamsFromParentS ++
        functionUserSpecifiedGenericParametersS ++
        extraGenericParamsFromBodyS// ++
        vregionmut() // Put back in regions
        //maybeRegionGenericParam
        //++ userSpecifiedRunesImplicitRegionRunesS

    val unfilteredRulesArray = ruleBuilder.toVector

    val unfilteredAttrsP = attrsP

    val filteredAttrs =
      (maybeParent match {
        case FunctionNoParent() => {
          unfilteredAttrsP.filter({ case AbstractAttributeP(_) => false case _ => true })
        }
        case ParentInterface(_, _, _, _) => unfilteredAttrsP
        case ParentFunction(_) => unfilteredAttrsP
      })
        //.filter({ case AdditiveAttributeP(_) => false case _ => true })

    val funcAttrsS =
      filteredAttrs.map({
        case AbstractAttributeP(_) => vwat() // Should have been filtered out, typingpass cares about abstract directly
        case AdditiveAttributeP(_) => AdditiveS
        case ExportAttributeP(_) => ExportS(file.packageCoordinate)
        case ExternAttributeP(_) => ExternS(file.packageCoordinate)
        case PureAttributeP(_) => PureS
        case BuiltinAttributeP(_, generatorName) => BuiltinS(generatorName.str)
        case x => vimpl(x.toString)
      })

    // Filter out any RuneParentEnvLookupSR rules, we don't want these methods to look up these runes
    // from the environment. See MKRFA.
    val rulesArray =
      maybeParent match {
        case FunctionNoParent() => unfilteredRulesArray
        case ParentFunction(_) => unfilteredRulesArray
        case ParentInterface(_, _, _, _) => {
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
        runeToExplicitType,
        rulesArray)

    postParser.checkIdentifiability(
      rangeS,
      genericParametersS.map(_.rune.rune),
      rulesArray)

    val tyype = TemplateTemplataType(genericParametersS.map(_.tyype.tyype), FunctionTemplataType())

    val functionS =
      FunctionS(
        PostParser.evalRange(file, range),
        funcName,
        funcAttrsS,
        genericParametersS,
        runeToPredictedType,
        tyype,
        totalParamsS,
        maybeRetCoordRune,
        rulesArray,
        maybeBody1)
    (functionS, variableUses)
  }

  private def createClosureParam(
    range: RangeL,
    funcName: IFunctionDeclarationNameS,
    lidb: LocationInDenizenBuilder,
    ruleBuilder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)],
    parentStackFrame: StackFrame,
    closureStructRegionRune: IRuneS,
    closureStructKindRune: IRuneS,
    closureStructCoordRune: IRuneS):
  ParameterS = {
    val closureParamPos = PostParser.evalPos(parentStackFrame.file, range.begin)
    val closureParamRange = RangeS(closureParamPos, closureParamPos)
    val closureParamName = interner.intern(ClosureParamNameS(closureParamRange.begin))

    runeToExplicitType += ((closureStructKindRune, KindTemplataType()))
    val closureStructName =
      interner.intern(LambdaStructDeclarationNameS(
        funcName match { case x @ LambdaDeclarationNameS(_) => x }))
    ruleBuilder +=
      LookupSR(
        closureParamRange,
        rules.RuneUsage(closureParamRange, closureStructKindRune),
        closureStructName.getImpreciseName(interner))

    runeToExplicitType += ((closureStructCoordRune, CoordTemplataType()))
    ruleBuilder +=
      CoerceToCoordSR(
        closureParamRange,
        RuneUsage(closureParamRange, closureStructCoordRune),
        RuneUsage(closureParamRange, closureStructKindRune))

    val closureParamTypeRune =
      rules.RuneUsage(closureParamRange, ImplicitRuneS(lidb.child().consume()))
    ruleBuilder +=
      AugmentSR(
        closureParamRange,
        closureParamTypeRune,
        Some(BorrowP),
        RuneUsage(closureParamRange, closureStructCoordRune))

    val capture = CaptureS(closureParamName, false)
    val closurePattern =
      AtomSP(closureParamRange, Some(capture), Some(closureParamTypeRune), None)
    ParameterS(closureParamRange, None, false, closurePattern)
  }

  private def createMagicParameters(
    lidb: LocationInDenizenBuilder,
    lambdaMagicParamNames: Vector[MagicParamNameS],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)]):
  Vector[ParameterS] = {
    lambdaMagicParamNames.map({
      case mpn@MagicParamNameS(codeLocation) => {
        val magicParamRange = vale.RangeS(codeLocation, codeLocation)
        val magicParamRune =
          rules.RuneUsage(magicParamRange, MagicParamRuneS(lidb.child().consume()))
        runeToExplicitType += ((magicParamRune.rune, CoordTemplataType()))
        val paramS =
          ParameterS(
            magicParamRange,
            None,
            false,
            AtomSP(
              magicParamRange,
              Some(patterns.CaptureS(mpn, false)), Some(magicParamRune), None))
        paramS
      }
    })
  }

  def scoutLambda(
    parentStackFrame: StackFrame,
    functionP: FunctionP):
  (FunctionS, VariableUses) = {
    val file = parentStackFrame.file
    scoutFunction(file, functionP, ParentFunction(parentStackFrame))
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
    contextRegion: IRuneS,
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
      contextRegion,
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

  def scoutInterfaceMember(
    parentInterface: ParentInterface,
    functionP: FunctionP):
  FunctionS = {
    val file = parentInterface.interfaceEnv.file
    val (functionS, variableUses) = scoutFunction(file, functionP, parentInterface)
    vassert(variableUses.uses.isEmpty)
    functionS
  }
}

