package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.ExpressionScout.NormalResult
import net.verdagon.vale.scout.Scout.noDeclarations
import net.verdagon.vale.scout.patterns._
import net.verdagon.vale.scout.predictor.Conclusions
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.scout.templatepredictor.PredictorEvaluator
import net.verdagon.vale._

import scala.collection.immutable.{List, Range}

//// Fate is short for Function State. It tracks how many magic params have been used.
//// This is similar to StackFrame, which tracks all the locals that have been declared.
//
//// Maybe we should combine them?
//// As of this writing, we use fate to keep track of magic params, and StackFrame at the
//// block level receives (via return type) declarations from the individual expressions
//// to accumulate them itself.
//case class ScoutFate(private val magicParams: Set[MagicParamNameS]) {
//  def addMagicParam(magicParam: MagicParamNameS): ScoutFate = {
//    ScoutFate(magicParams + magicParam)
//  }
//  def countMagicParams() = magicParams.size
//}
//
//case class ScoutFateBox(var fate: ScoutFate) {
//  def addMagicParam(codeLocation: MagicParamNameS): Unit = {
//    fate = fate.addMagicParam(codeLocation)
//  }
//  def countMagicParams(): Int = fate.countMagicParams()
//}

object FunctionScout {
//  // All closure structs start with this
//  val CLOSURE_STRUCT_NAME = "__Closure:"
//  // In a closure's environment, we also have this. This lets us easily know
//  // what the StructRef for a given closure is.
//  val CLOSURE_STRUCT_ENV_ENTRY_NAME = "__Closure"
//  // Name of anonymous substructs. They're more identified by their CodeLocation though.
//  val ANONYMOUS_SUBSTRUCT_NAME = "__AnonymousSubstruct"

  def scoutTopLevelFunction(file: String, functionP: FunctionP): FunctionS = {
    val FunctionP(
      range,
      Some(StringP(_, codeName)),
      isExtern,
      isAbstract,
      userSpecifiedIdentifyingRuneNames,
      templateRulesP,
      paramsP,
      maybeRetPT,
      maybeBody0
    ) = functionP
    val codeLocation = Scout.evalPos(range.begin)
    val name = FunctionNameS(codeName, codeLocation)

    val userSpecifiedIdentifyingRunes =
      userSpecifiedIdentifyingRuneNames
        .toList
        .flatMap(_.runes)
        .map({ case StringP(_, identifyingRuneName) => CodeRuneS(identifyingRuneName) })
    val userRunesFromRules =
      templateRulesP
        .toList
        .flatMap(rules => RulePUtils.getOrderedRuneDeclarationsFromRulexesWithDuplicates(rules.rules))
        .map(identifyingRuneName => CodeRuneS(identifyingRuneName))
    val userDeclaredRunes = userSpecifiedIdentifyingRunes ++ userRunesFromRules

    val functionEnv = FunctionEnvironment(name, None, userDeclaredRunes.toSet, paramsP.size)

    val rate = RuleStateBox(RuleState(name, 0))
    val userRulesS =
      RuleScout.translateRulexes(
        rate, functionEnv.allUserDeclaredRunes(), templateRulesP.toList.flatMap(_.rules))

    val myStackFrameWithoutParams = StackFrame(name, functionEnv, None, noDeclarations)
    val (implicitRulesFromPatterns, explicitParamsPatterns1) =
      PatternScout.scoutPatterns(myStackFrameWithoutParams, rate, paramsP.toList.flatMap(_.patterns))

    val explicitParams1 = explicitParamsPatterns1.map(ParameterS)
    val captureDeclarations =
      explicitParams1
        .map(explicitParam1 => VariableDeclarations(PatternScout.getParameterCaptures(explicitParam1.pattern)))
        .foldLeft(noDeclarations)(_ ++ _)
    val myStackFrame = StackFrame(name, functionEnv, None, noDeclarations)

    val (implicitRulesFromRet, maybeRetCoordRune) =
      PatternScout.translateMaybeTypeIntoMaybeRune(
        userDeclaredRunes.toSet,
        rate,
        maybeRetPT,
        CoordTypePR)

    val rulesS = userRulesS ++ implicitRulesFromPatterns ++ implicitRulesFromRet

    //    vassert(exportedTemplateParamNames.size == exportedTemplateParamNames.toSet.size)

    val body1 =
      if (isAbstract.nonEmpty) {
        AbstractBody1
      } else if (isExtern.nonEmpty) {
        ExternBody1
      } else {
        vassert(maybeBody0.nonEmpty)
        val (body1, _, List()) = scoutBody(myStackFrame, maybeBody0.get, captureDeclarations)
        vassert(body1.closuredNames.isEmpty)
        CodeBody1(body1)
      }

    val allRunes =
      PredictorEvaluator.getAllRunes(
        userSpecifiedIdentifyingRunes,
        rulesS,
        explicitParams1.map(_.pattern),
        maybeRetCoordRune)
    val Conclusions(knowableValueRunes, predictedTypeByRune) =
      PredictorEvaluator.solve(
        Set(),
        rulesS,
        explicitParams1.map(_.pattern))

    val localRunes = allRunes
    val unknowableRunes = allRunes -- knowableValueRunes

    // This cant be:
    //   val identifyingRunes =
    //     userSpecifiedIdentifyingRunes ++ (unknowableRunes -- userSpecifiedIdentifyingRunes)
    // because for example if we had:
    //   fn moo<T>(m &T) { m.hp }
    // then userSpecifiedIdentifyingRunes would be
    //   CodeRuneS("T")
    // and unknowableRunes would be
    //   Set(CodeRuneS("T"), ImplicitRuneS(0), ImplicitRuneS(1))
    // and we'd end up with identifyingRunes as
    //   List(CodeRuneS("T"), ImplicitRuneS(0), ImplicitRuneS(1))
    // So, what we instead want is like... the original causes of unknowable runes.
    // I think thats just user specified ones, and implicit template runes from params,
    // and magic param runes.
//    val topLevelImplicitRunesS =
//      paramsP.zip(explicitParamsPatterns1).flatMap({
//        case (paramP, explicitParamPatternS) => {
//          if (paramP.templex.isEmpty) {
//            List(explicitParamPatternS.coordRune)
//          } else {
//            List()
//          }
//        }
//      })

    val identifyingParamCoordRunes =
      explicitParamsPatterns1
        .map(_.coordRune)
        .filter(!knowableValueRunes.contains(_))
        .filter(!userSpecifiedIdentifyingRunes.contains(_))
    val identifyingRunes = userSpecifiedIdentifyingRunes ++ identifyingParamCoordRunes

    val isTemplate = identifyingRunes.nonEmpty

    val maybePredictedType =
      if (isTemplate) {
        if ((identifyingRunes.toSet -- predictedTypeByRune.keySet).isEmpty) {
          Some(TemplateTypeSR(identifyingRunes.map(predictedTypeByRune), FunctionTypeSR))
        } else {
          None
        }
      } else {
        Some(FunctionTypeSR)
      }

    FunctionS(
      name,
      knowableValueRunes,
      identifyingRunes,
      localRunes,
      maybePredictedType,
      explicitParams1,
      maybeRetCoordRune,
      isTemplate,
      rulesS,
      body1)
  }

  def scoutLambda(
      parentStackFrame: StackFrame,
      lambdaFunction0: FunctionP):
  (FunctionS, VariableUses) = {
    val FunctionP(range, _, None, None, userSpecifiedIdentifyingRuneNames, None, paramsP, maybeRetPT, Some(body0)) = lambdaFunction0;
    val codeLocation = Scout.evalPos(range.begin)
    val userSpecifiedIdentifyingRunes: List[IRuneS] =
      userSpecifiedIdentifyingRuneNames
        .toList
        .flatMap(_.runes)
        .map({ case StringP(_, identifyingRuneName) => CodeRuneS(identifyingRuneName) })

    val lambdaName = LambdaNameS(/*parentStackFrame.name,*/ codeLocation)
    // Every lambda has a closure as its first arg, even if its empty
    val closureStructName = LambdaStructNameS(lambdaName)

    val rate = RuleStateBox(RuleState(lambdaName, 0))

    val functionEnv =
      FunctionEnvironment(
        lambdaName,
        Some(parentStackFrame.parentEnv),
        userSpecifiedIdentifyingRunes.toSet,
        paramsP.size)

    val myStackFrameWithoutParams = StackFrame(lambdaName, functionEnv, None, noDeclarations)

    val (implicitRulesFromParams, explicitParamPatterns1) =
      PatternScout.scoutPatterns(
        myStackFrameWithoutParams,
        rate,
        paramsP.toList.flatMap(_.patterns));
    val explicitParams1 = explicitParamPatterns1.map(ParameterS)
//    vassert(exportedTemplateParamNames.size == exportedTemplateParamNames.toSet.size)

    val closureParamName = ClosureParamNameS()

    val closureDeclaration =
      VariableDeclarations(List(VariableDeclaration(closureParamName, FinalP)))

    val paramDeclarations =
      explicitParams1.map(_.pattern)
        .map(pattern1 => VariableDeclarations(PatternScout.getParameterCaptures(pattern1)))
        .foldLeft(closureDeclaration)(_ ++ _)

    val myStackFrame = StackFrame(lambdaName, parentStackFrame.parentEnv, Some(parentStackFrame), noDeclarations)

    val (body1, variableUses, lambdaMagicParamNames) =
      scoutBody(
        myStackFrame,
        body0,
        paramDeclarations)

    if (lambdaMagicParamNames.nonEmpty && (explicitParams1.nonEmpty)) {
      vfail("Cant have a lambda with _ and params");
    }

//    val closurePatternId = fate.nextPatternNumber();

    val closureParamTypeRune = rate.newImplicitRune()
    val rulesFromClosureParam =
      List(
        EqualsSR(
          TypedSR(closureParamTypeRune,CoordTypeSR),
          TemplexSR(OwnershippedST(BorrowP,AbsoluteNameST(closureStructName)))))
    val closureParamS = ParameterS(AtomSP(CaptureS(closureParamName,FinalP),None,closureParamTypeRune,None))

    val (magicParamsRules, magicParams) =
        lambdaMagicParamNames.map({
          case mpn @ MagicParamNameS(codeLocation) => {
            val magicParamRune = MagicParamRuneS(codeLocation)
            val ruleS = TypedSR(magicParamRune,CoordTypeSR)
            val paramS = ParameterS(AtomSP(CaptureS(mpn,FinalP),None,magicParamRune,None))
            (ruleS, paramS)
          }
        })
        .unzip

    val userSpecifiedAndMagicParamRunes =
      (
        userSpecifiedIdentifyingRunes ++
        // Patterns can't define runes
        // filledParamsP.flatMap(PatternPUtils.getOrderedIdentifyingRunesFromPattern) ++
        // Magic params always go on the end
        magicParams.map(_.pattern.coordRune)
      ).distinct

    val totalParams = closureParamS :: explicitParams1 ++ magicParams;

    val (implicitRulesFromReturn, maybeRetCoordRune) =
      PatternScout.translateMaybeTypeIntoMaybeRune(
        parentStackFrame.parentEnv.allUserDeclaredRunes(),
        rate,
        maybeRetPT,
        CoordTypePR)

    val rulesS = rulesFromClosureParam ++ magicParamsRules ++ implicitRulesFromParams ++ implicitRulesFromReturn

    val allRunes =
      PredictorEvaluator.getAllRunes(
        userSpecifiedAndMagicParamRunes,
        rulesS,
        explicitParams1.map(_.pattern),
        maybeRetCoordRune)
    val Conclusions(knowableValueRunes, predictedTypeByRune) =
      PredictorEvaluator.solve(
        parentStackFrame.parentEnv.allUserDeclaredRunes(),
        rulesS,
        explicitParams1.map(_.pattern))


    val localRunes = allRunes -- myStackFrame.parentEnv.allUserDeclaredRunes()
    val unknowableRunes = allRunes -- knowableValueRunes


    // This cant be:
    //   val identifyingRunes =
    //     userSpecifiedIdentifyingRunes ++ (unknowableRunes -- userSpecifiedIdentifyingRunes)
    // because for example if we had:
    //   fn moo<T>(m &T) { m.hp }
    // then userSpecifiedIdentifyingRunes would be
    //   CodeRuneS("T")
    // and unknowableRunes would be
    //   Set(CodeRuneS("T"), ImplicitRuneS(0), ImplicitRuneS(1))
    // and we'd end up with identifyingRunes as
    //   List(CodeRuneS("T"), ImplicitRuneS(0), ImplicitRuneS(1))
    // So, what we instead want is like... the original causes of unknowable runes.
    // I think thats just user specified ones, and implicit template runes from params,
    // and magic param runes.
//    val topLevelImplicitRunesS =
//      paramsP.zip(explicitParams1.map(_.pattern)).flatMap({
//        case (paramP, explicitParamPatternS) => {
//          if (paramP.templex.isEmpty) {
//            List(explicitParamPatternS.coordRune)
//          } else {
//            List()
//          }
//        }
//      })

    val identifyingParamCoordRunes =
      explicitParamPatterns1
        .map(_.coordRune)
        .filter(!knowableValueRunes.contains(_))
        .filter(!userSpecifiedIdentifyingRunes.contains(_))
    val identifyingRunes = userSpecifiedIdentifyingRunes ++ magicParams.map(_.pattern.coordRune) ++ identifyingParamCoordRunes

    val isTemplate = identifyingRunes.nonEmpty

    val maybePredictedType =
      if (isTemplate) {
        if (identifyingRunes.isEmpty) {
          Some(TemplateTypeSR(identifyingRunes.map(predictedTypeByRune), FunctionTypeSR))
        } else {
          None
        }
      } else {
        Some(FunctionTypeSR)
      }

    val function1 =
      FunctionS(
        lambdaName,
        knowableValueRunes,
        identifyingRunes,
        localRunes,
        maybePredictedType,
        totalParams,
        maybeRetCoordRune,
        isTemplate,
        rulesS,
        CodeBody1(body1))
    (function1, variableUses)
  }

  // Returns:
  // - Body.
  // - Uses of parent variables.
  // - Magic params made/used inside.
  private def scoutBody(
    stackFrame: StackFrame,
    body0: BlockPE,
    paramDeclarations: VariableDeclarations):
  (BodySE, VariableUses, List[MagicParamNameS]) = {
    // There's an interesting consequence of calling this function here...
    // If we have a lone lookup node, like "m = Marine(); m;" then that
    // 'm' will be turned into an expression, which means that's how it's
    // destroyed. So, thats how we destroy things before their time.
    val (NormalResult(block1), selfUses, childUses) =
      ExpressionScout.scoutBlock(
        stackFrame,
        body0,
        // Aren't making a new stack frame because we already had some nice params populated
        // stackFrame.
        paramDeclarations)

    vcurious(
      childUses.uses.map(_.name).collect({ case mpn @ MagicParamNameS(_) => mpn }).isEmpty)
    val magicParamNames =
      selfUses.uses.map(_.name).collect({ case mpn @ MagicParamNameS(_) => mpn })
    val magicParamVars = magicParamNames.map(n => VariableDeclaration(n, FinalP))

    val magicParamLocals =
      magicParamVars.map({ declared =>
        LocalVariable1(
          declared.name,
          declared.variability,
          selfUses.isBorrowed(declared.name),
          selfUses.isMoved(declared.name),
          selfUses.isMutated(declared.name),
          childUses.isBorrowed(declared.name),
          childUses.isMoved(declared.name),
          childUses.isMutated(declared.name))
      })
    val block1WithParamLocals = BlockSE(block1.locals ++ magicParamLocals, block1.exprs)

    val allUses =
      selfUses.combine(childUses, {
        case (None, other) => other
        case (other, None) => other
        case (Some(NotUsed), other) => other
        case (other, Some(NotUsed)) => other
        // If we perhaps use it, and children perhaps use it, then say maybe used.
        case (Some(MaybeUsed), Some(MaybeUsed)) => Some(MaybeUsed)
        // If a child uses it, then count it as used
        case (Some(MaybeUsed), Some(Used)) => Some(Used)
        // If a child uses it, then count it as used
        case (Some(Used), Some(MaybeUsed)) => Some(Used)
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

    val bodySE = BodySE(usesOfParentVariables.map(_.name), block1WithParamLocals)
    (bodySE, VariableUses(usesOfParentVariables), magicParamNames)
  }

  def scoutInterfaceMember(interfaceEnv: Environment, functionP: FunctionP): FunctionS = {
    val FunctionP(
      range,
      Some(StringP(_, codeName)),
      None,
      _, // Ignore whether it thinks it's abstract or not
      userSpecifiedIdentifyingRuneNames,
      templateRulesP,
      paramsP,
      maybeReturnType,
      None) = functionP;
    val codeLocation = Scout.evalPos(range.begin)
    val funcName = FunctionNameS(codeName, codeLocation)
    val userSpecifiedIdentifyingRunes: List[IRuneS] =
      userSpecifiedIdentifyingRuneNames
          .toList
        .flatMap(_.runes)
        .map({ case StringP(_, identifyingRuneName) => CodeRuneS(identifyingRuneName) })

    val rate = RuleStateBox(RuleState(funcName, 0))

    val userRulesS = RuleScout.translateRulexes(rate, interfaceEnv.allUserDeclaredRunes(), templateRulesP.toList.flatMap(_.rules))

    val functionEnv = FunctionEnvironment(funcName, Some(interfaceEnv), userSpecifiedIdentifyingRunes.toSet, paramsP.size)
    val myStackFrame = StackFrame(funcName, functionEnv, None, noDeclarations)
    val (implicitRulesFromParams, patternsS) =
      PatternScout.scoutPatterns(myStackFrame, rate, paramsP.toList.flatMap(_.patterns))

    val paramsS = patternsS.map(ParameterS)

    val (implicitRulesFromRet, maybeReturnRune) =
      PatternScout.translateMaybeTypeIntoMaybeRune(
        interfaceEnv.allUserDeclaredRunes(),
        rate,
        maybeReturnType,
        CoordTypePR)

    val rulesS = userRulesS ++ implicitRulesFromParams ++ implicitRulesFromRet

    val allRunes =
      PredictorEvaluator.getAllRunes(
        userSpecifiedIdentifyingRunes,
        rulesS,
        paramsS.map(_.pattern),
        maybeReturnRune)
    val Conclusions(knowableValueRunes, predictedTypeByRune) =
      PredictorEvaluator.solve(
        interfaceEnv.allUserDeclaredRunes(),
        rulesS,
        paramsS.map(_.pattern))

    val localRunes = allRunes -- interfaceEnv.allUserDeclaredRunes()
    val unknowableRunes = allRunes -- knowableValueRunes

    val identifyingParamCoordRunes =
      patternsS
        .map(_.coordRune)
        .filter(!knowableValueRunes.contains(_))
        .filter(!userSpecifiedIdentifyingRunes.contains(_))
    val identifyingRunes = userSpecifiedIdentifyingRunes ++ identifyingParamCoordRunes
    val isTemplate = identifyingRunes.nonEmpty
    vassert(!isTemplate) // interface members cant be templates

    val maybePredictedType =
      if (isTemplate) {
        if ((identifyingRunes.toSet -- predictedTypeByRune.keySet).isEmpty) {
          Some(TemplateTypeSR(identifyingRunes.map(predictedTypeByRune), FunctionTypeSR))
        } else {
          None
        }
      } else {
        Some(FunctionTypeSR)
      }

    FunctionS(
      funcName,
      knowableValueRunes,
      identifyingRunes,
      localRunes,
      maybePredictedType,
      paramsS,
      maybeReturnRune,
      isTemplate,
      rulesS,
      AbstractBody1);
  }

//
//  // returns seq num, new parameter, exported template names from typeless params, and capture names
//  private def scoutParameter(
//      fate: ScoutFate,
//      rulesS0: TemplateRulesS,
//      param0: ParameterPP):
//  (ScoutFate, TemplateRulesS, ParameterS) = {
//    val ParameterPP(maybeName, maybeVirtualityP, maybeCoordPP) = param0;
//
//
//
//    patternP match {
//      // Has name and type
//      case ParameterPP(Some(CaptureP(name, _)), _, Some(CoordPP(_, maybeOwnershipP, ReferendSP(None, None)))) => {
//        val patternId = fate.nextPatternNumber()
//
//        val param = ParameterS(patternId, name, pattern1)
//        (rulesS0, param)
//      }
//      // Has name, has no type
//      case ParameterSP(_, CoordSP(None, OwnershipSP(None, None), ReferendSP(None, None)), ValueSP(Some(CaptureS(name, _)), _)) => {
//        val templateParamTypeNumber = fate.nextTypeNumber()
//        val newTemplateParamName = "__T" + templateParamTypeNumber;
//        val patternId = fate.nextPatternNumber()
//        val param = ParameterS(patternId, name, pattern1)
//        (param, List(newTemplateParamName))
//      }
//      // Has no name, has type
//      case ParameterSP(_, CoordSP(Some(_), OwnershipSP(None, None), ReferendSP(None, None)), ValueSP(None, _)) => {
//        val num = fate.nextPatternNumber()
//        val name = "__arg_" + num
//        val patternId = fate.nextPatternNumber()
//        val param = ParameterS(patternId, name, pattern1)
//        (param, List())
//      }
//      // Has no name nor type
//      case ParameterSP(_, CoordSP(None, OwnershipSP(None, None), ReferendSP(None, None)), ValueSP(None, _)) => {
//        val num = fate.nextPatternNumber()
//        val name = "__arg_" + num
//        val templateParamTypeNumber = fate.nextTypeNumber()
//        val newTemplateParamName = "__T" + templateParamTypeNumber;
//        val patternId = fate.nextPatternNumber()
//        val param = ParameterS(patternId, name, pattern1)
//        (param, List(newTemplateParamName))
//      }
//      case _ => {
//        vfail("curiosity") // when does this happen
//        val num = fate.nextPatternNumber()
//        val name = "__arg_" + num
//        val patternId = fate.nextPatternNumber()
//        val param = ParameterS(patternId, name, pattern1)
//        (param, List())
//      }
//    }
//  }
}
