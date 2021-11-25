//package net.verdagon.vale.astronomer
//
//import net.verdagon.vale.astronomer.ruletyper.{RuleTyperSolveFailure, RuleTyperSolveSuccess}
//import net.verdagon.vale.parser.{ConstraintP, WeakP}
////import net.verdagon.vale.scout.rules.{EqualsSR, IntegerTemplataType, MutabilityTemplataType, RuleFlattener, VariabilityTemplataType}
//import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
//import net.verdagon.vale.{Err, Ok, Result, vassertSome, vfail, vimpl}
//
////object ExpressionAstronomer {
////  def translateBlock(env: Environment, astrouts: AstroutsBox, blockS: BlockSE): BlockAE = {
////    val BlockSE(range, locals, exprsS) = blockS
////    val childEnv = env.addLocals(locals.map(translateLocalVariable))
////    val exprsA = exprsS.map(translateExpression(childEnv, astrouts, _))
////    BlockAE(range, exprsA)
////  }
////
////  def translateLocalVariable(varS: LocalS): LocalA = {
////    val LocalS(varNameS, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated) = varS
////    val varNameA = Astronomer.translateVarNameStep(varNameS)
////    LocalA(varNameA, selfBorrowed, selfMoved, selfMutated, childBorrowed, childMoved, childMutated)
////  }
////
////  def translateExpression(env: Environment, astrouts: AstroutsBox, iexprS: IExpressionSE): IExpressionAE = {
////    iexprS match {
////      case LetSE(range, rules, patternS, expr) => {
////        val exprA = translateExpression(env, astrouts, expr)
////        val patternA = Astronomer.translateAtom(env, patternS)
////        LetAE(range,rules, patternA, exprA)
////      }
////      case IfSE(range, conditionS, thenBodyS, elseBodyS) => {
////        val conditionA = translateBlock(env, astrouts, conditionS)
////        val thenBodyA = translateBlock(env, astrouts, thenBodyS)
////        val elseBodyA = translateBlock(env, astrouts, elseBodyS)
////        IfAE(range, conditionA, thenBodyA, elseBodyA)
////      }
////      case WhileSE(range, conditionS, bodyS) => {
////        val conditionA = translateBlock(env, astrouts, conditionS)
////        val bodyA = translateBlock(env, astrouts, bodyS)
////        WhileAE(range, conditionA, bodyA)
////      }
////      case DestructSE(range, innerS) => {
////        val exprA = translateExpression(env, astrouts, innerS)
////        DestructAE(range, exprA)
////      }
////      case ExprMutateSE(rangeS, mutateeS, exprS) => {
////        val conditionA = translateExpression(env, astrouts, mutateeS)
////        val bodyA = translateExpression(env, astrouts, exprS)
////        ExprMutateAE(rangeS, conditionA, bodyA)
////      }
////      case GlobalMutateSE(range, name, exprS) => {
////        val exprA = translateExpression(env, astrouts, exprS)
////        GlobalMutateAE(range, Astronomer.translateImpreciseName(name), exprA)
////      }
////      case LocalMutateSE(range, nameS, exprS) => {
////        val exprA = translateExpression(env, astrouts, exprS)
////        LocalMutateAE(range, Astronomer.translateVarNameStep(nameS), exprA)
////      }
////      case OwnershippedSE(range, innerExprS, targetOwnership) => {
////        val innerExprA = translateExpression(env, astrouts, innerExprS)
////        LendAE(range, innerExprA, targetOwnership)
////      }
////      case ReturnSE(range, innerExprS) => {
////        val innerExprA = translateExpression(env, astrouts, innerExprS)
////        (ReturnAE(range, innerExprA))
////      }
////      case blockS @ BlockSE(_, _, _) => translateBlock(env, astrouts, blockS)
////      case ArgLookupSE(range, index) => (ArgLookupAE(range, index))
////      case RepeaterBlockSE(range, exprS) => {
////        val exprA = translateExpression(env, astrouts, exprS)
////        (RepeaterBlockAE(range, exprA))
////      }
////      case RepeaterBlockIteratorSE(range, exprS) => {
////        val exprA = translateExpression(env, astrouts, exprS)
////        (RepeaterBlockIteratorAE(range, exprA))
////      }
////      case VoidSE(range) => VoidAE(range)
////      case TupleSE(range, elementsS) => {
////        val elementsA = elementsS.map(translateExpression(env, astrouts, _))
////        TupleAE(range, elementsA)
////      }
////      case StaticArrayFromValuesSE(range, rules, maybeMutabilityST, maybeVariabilityST, maybeSizeST, elementsS) => {
////        vimpl()
//////        val rules =
//////          ((maybeMutabilityST.toVector.map(mutabilityST => {
//////            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), mutabilityST)
//////          })) ++
//////          (maybeVariabilityST.toVector.map(variabilityST => {
//////            EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), variabilityST)
//////          })) ++
//////          (maybeSizeST.toVector.map(sizeST => {
//////            EqualsSR(range, TypedSR(range, ArraySizeImplicitRuneS(), IntTypeSR), sizeST)
//////          })))
//////
//////        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
//////        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
//////        val maybeSizeRuneA = maybeSizeST.map(_ => ArraySizeImplicitRuneA())
//////        val runesA = maybeMutabilityRuneA.toVector ++ maybeVariabilityRuneA.toVector ++ maybeSizeRuneA.toVector
//////
//////
//////        val (runeToIndex, runeToType, rulesA) = RuleFlattener.flattenAndCompileRules(rules)
//////        val conclusions =
//////          Astronomer.makeRuleTyper().solve(astrouts, env, rulesA, range) match {
//////            case Err(e) => throw CompileErrorExceptionA(e)
//////            case Ok(x) => runeToIndex.mapValues(index => vassertSome(x(index)))
//////          }
//////
//////
//////        //        val (conclusions, rulesA) =
////////          makeRuleTyper().solve(astrouts, env, rules, range, Vector.empty, Some(runesA.toSet)) match {
////////            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
////////            case (c, RuleTyperSolveSuccess(r)) => (c, r)
////////          }
//////
//////        val elementsA = elementsS.map(translateExpression(env, astrouts, _))
//////
//////        StaticArrayFromValuesAE(range, rulesA, conclusions, maybeSizeRuneA, maybeMutabilityRuneA, maybeVariabilityRuneA, elementsA)
////      }
////      case StaticArrayFromCallableSE(range, rules, maybeMutabilityST, maybeVariabilityST, sizeST, callableSE) => {
////        vimpl()
//////        val rules =
//////          ((maybeMutabilityST.toVector.map(mutabilityST => {
//////            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), mutabilityST)
//////          })) ++
//////            (maybeVariabilityST.toVector.map(variabilityST => {
//////              EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), variabilityST)
//////            })) ++
//////            Vector(EqualsSR(range, TypedSR(range, ArraySizeImplicitRuneS(), IntTypeSR), sizeST)))
//////
//////        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
//////        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
//////        val sizeRuneA = ArraySizeImplicitRuneA()
//////        val runesA = maybeMutabilityRuneA.toVector ++ maybeVariabilityRuneA.toVector ++ Vector(sizeRuneA)
//////
//////
//////        val (runeToIndex, runeToType, rulesA) = RuleFlattener.flattenAndCompileRules(rules)
//////        val conclusions =
//////          Astronomer.makeRuleTyper().solve(astrouts, env, rulesA, range) match {
//////            case Err(e) => throw CompileErrorExceptionA(e)
//////            case Ok(x) => runeToIndex.mapValues(index => vassertSome(x(index)))
//////          }
//////
//////        //        val (conclusions, rulesA) =
////////          makeRuleTyper().solve(astrouts, env, rules, range, Vector.empty, Some(runesA.toSet)) match {
////////            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
////////            case (c, RuleTyperSolveSuccess(r)) => (c, r)
////////          }
//////
//////        val callableAE = translateExpression(env, astrouts, callableSE)
//////
//////        StaticArrayFromCallableAE(range, rulesA, conclusions, sizeRuneA, maybeMutabilityRuneA, maybeVariabilityRuneA, callableAE)
////      }
////      case RuntimeArrayFromCallableSE(range, rules, maybeMutabilityST, maybeVariabilityST, sizeSE, callableSE) => {
////        vimpl()
//////        val rules =
//////          ((maybeMutabilityST.toVector.map(mutabilityST => {
//////            EqualsSR(range, TypedSR(range, ArrayMutabilityImplicitRuneS(), MutabilityTypeSR), mutabilityST)
//////          })) ++
//////          (maybeVariabilityST.toVector.map(variabilityST => {
//////            EqualsSR(range, TypedSR(range, ArrayVariabilityImplicitRuneS(), VariabilityTypeSR), variabilityST)
//////          })))
//////
//////        val maybeMutabilityRuneA = maybeMutabilityST.map(_ => ArrayMutabilityImplicitRuneA())
//////        val maybeVariabilityRuneA = maybeVariabilityST.map(_ => ArrayVariabilityImplicitRuneA())
//////        val runesA = maybeMutabilityRuneA.toVector ++ maybeVariabilityRuneA.toVector
//////
//////        val (runeToIndex, runeToType, rulesA) = RuleFlattener.flattenAndCompileRules(rules)
//////        val conclusions =
//////          Astronomer.makeRuleTyper().solve(astrouts, env, rulesA, range) match {
//////            case Err(e) => throw CompileErrorExceptionA(e)
//////            case Ok(x) => runeToIndex.mapValues(index => vassertSome(x(index)))
//////          }
//////
//////        //        val (conclusions, rulesA) =
////////          makeRuleTyper().solve(astrouts, env, rules, range, Vector.empty, Some(runesA.toSet)) match {
////////            case (_, rtsf @ RuleTyperSolveFailure(_, _, _, _)) => vfail(rtsf.toString)
////////            case (c, RuleTyperSolveSuccess(r)) => (c, r)
////////          }
//////
//////        val sizeAE = translateExpression(env, astrouts, sizeSE)
//////        val callableAE = translateExpression(env, astrouts, callableSE)
//////
//////        RuntimeArrayFromCallableAE(range, rulesA, conclusions, maybeMutabilityRuneA, maybeVariabilityRuneA, sizeAE, callableAE)
////      }
////      case RepeaterPackSE(range, exprS) => {
////        val elementsA = translateExpression(env, astrouts, exprS)
////        RepeaterPackAE(range, elementsA)
////      }
////      case RepeaterPackIteratorSE(range, exprS) => {
////        val exprA = translateExpression(env, astrouts, exprS)
////        RepeaterPackIteratorAE(range, exprA)
////      }
////      case RuneLookupSE(range, runeS) => {
////        val runeA = Astronomer.translateRune(runeS)
////        val tyype = env.lookupRune(runeA)
////        RuneLookupAE(range, runeA, tyype)
////      }
////      case ConstantIntSE(range, value, bits) => ConstantIntAE(range, value, bits)
////      case ConstantBoolSE(range, value) => ConstantBoolAE(range, value)
////      case ConstantStrSE(range, value) => ConstantStrAE(range, value)
////      case ConstantFloatSE(range, value) => ConstantFloatAE(range, value)
////      case FunctionSE(functionS) => {
////        val functionA = Astronomer.translateFunction(astrouts, env, functionS)
////        val lambdaName = functionA.name match { case n @ LambdaNameS(_) => n }
////        FunctionAE(lambdaName, functionA)
////      }
////      case DotSE(range, leftS, member, borrowContainer) => {
////        val leftA = translateExpression(env, astrouts, leftS)
////        DotAE(range, leftA, member, borrowContainer)
////      }
////      case DotCallSE(range, leftS, indexExprS) => {
////        val leftA = translateExpression(env, astrouts, leftS)
////        val indexExprA = translateExpression(env, astrouts, indexExprS)
////        IndexAE(range, leftA, indexExprA)
////      }
////      case FunctionCallSE(rangeS, callableExprS, argsExprsS) => {
////        val callableExprA = translateExpression(env, astrouts, callableExprS)
////        val argsExprsA = argsExprsS.map(translateExpression(env, astrouts, _))
////        FunctionCallAE(rangeS, callableExprA, argsExprsA)
////      }
////      case LocalLoadSE(range, name, targetOwnership) => {
////        LocalLoadAE(range, Astronomer.translateVarNameStep(name), targetOwnership)
////      }
////      case OutsideLoadSE(range, rules, name, None, targetOwnership) => {
////        OutsideLoadAE(range, name, targetOwnership)
////      }
////      case OutsideLoadSE(range, rules, name, Some(templateArgRunesS), targetOwnership) => {
////        // We don't translate the templexes, we can't until we know what the template expects.
////        TemplateSpecifiedLookupAE(range, name, rules, templateArgRunesS, targetOwnership)
////      }
////      case UnletSE(range, name) => UnletAE(range, name)
////    }
////  }
////
//////  def makeRuleTyper(): RuleTyperEvaluator[Environment, AstroutsBox, ITemplataType, ICompileErrorA] = {
//////    new RuleTyperEvaluator[Environment, AstroutsBox, ITemplataType, ICompileErrorA](
//////      new IRuleTyperEvaluatorDelegate[Environment, AstroutsBox, ITemplataType, ICompileErrorA] {
//////        override def solve(state: AstroutsBox, env: Environment, range: RangeS, rule: IRulexSR, runes: Map[Int, ITemplataType]): Result[Map[Int, ITemplataType], ICompileErrorA] = {
//////          vimpl()
//////        }
////////        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: CodeTypeNameS): (ITemplataType) = {
////////          Astronomer.lookupType(state, env, range, name)
////////        }
////////
////////        override def lookupType(state: AstroutsBox, env: Environment, range: RangeS, name: INameS): ITemplataType = {
////////          Astronomer.lookupType(state, env, range, name)
////////        }
//////      })
//////  }
////}
