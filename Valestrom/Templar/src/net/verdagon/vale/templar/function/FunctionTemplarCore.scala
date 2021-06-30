package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{Err, IProfiler, Ok, vassert, vassertSome, vcheck, vcurious, vfail, vimpl}

import scala.collection.immutable.{List, Set}

case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT)

class FunctionTemplarCore(
    opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
  templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IFunctionTemplarDelegate) {
  val bodyTemplar = new BodyTemplar(opts, profiler, newTemplataStore, templataTemplar, convertHelper, new IBodyTemplarDelegate {
    override def evaluateBlockStatements(temputs: Temputs, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]): (List[ReferenceExpressionTE], Set[CoordT]) = {
      delegate.evaluateBlockStatements(temputs, startingFate, fate, exprs)
    }

    override def nonCheckingTranslateList(temputs: Temputs, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpressionTE]): List[ReferenceExpressionTE] = {
      delegate.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
    }
  })

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def evaluateFunctionForHeader(
    startingFullEnv: FunctionEnvironment,
      temputs: Temputs,
    callRange: RangeS,
      params2: List[ParameterT]):
  (FunctionHeaderT) = {
    val fullEnv = FunctionEnvironmentBox(startingFullEnv)

    opts.debugOut("Evaluating function " + fullEnv.fullName)

    val isDestructor =
      params2.nonEmpty &&
      params2.head.tyype.ownership == OwnT &&
      (startingFullEnv.fullName.last match {
        case FunctionNameT(humanName, _, _) if humanName == CallTemplar.MUT_DESTRUCTOR_NAME => true
        case _ => false
      })

    val header =
      startingFullEnv.function.body match {
        case CodeBodyA(body) => {
          declareAndEvaluateFunctionBodyAndAdd(
              startingFullEnv, fullEnv, temputs, BFunctionA(startingFullEnv.function, body), params2, isDestructor)
        }
        case AbstractBodyA => {
          val maybeRetCoord =
            startingFullEnv.function.maybeRetCoordRune match {
              case None => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Need return type for abstract function!"))
              case Some(r) => fullEnv.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(r), Set(TemplataLookupContext))
            }
          val retCoord =
            maybeRetCoord match {
              case None => vfail("wat")
              case Some(CoordTemplata(r)) => r
            }
          val header =
            makeInterfaceFunction(fullEnv.snapshot, temputs, Some(startingFullEnv.function), params2, retCoord)
          (header)
        }
        case ExternBodyA => {
          val maybeRetCoord =
            fullEnv.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(startingFullEnv.function.maybeRetCoordRune.get), Set(TemplataLookupContext))
          val retCoord =
            maybeRetCoord match {
              case None => vfail("wat")
              case Some(CoordTemplata(r)) => r
            }
          val header =
            makeExternFunction(
              temputs,
              fullEnv.fullName,
              startingFullEnv.function.range,
              translateFunctionAttributes(startingFullEnv.function.attributes),
              params2,
              retCoord,
              Some(startingFullEnv.function))
          (header)
        }
        case GeneratedBodyA(generatorId) => {
          val signature2 = SignatureT(fullEnv.fullName);
          val maybeRetTemplata =
            startingFullEnv.function.maybeRetCoordRune match {
              case None => (None)
              case Some(retCoordRune) => {
                fullEnv.getNearestTemplataWithAbsoluteName2(NameTranslator.translateRune(retCoordRune), Set(TemplataLookupContext))
              }
            }
          val maybeRetCoord =
            maybeRetTemplata match {
              case None => (None)
              case Some(CoordTemplata(retCoord)) => {
                temputs.declareFunctionReturnType(signature2, retCoord)
                (Some(retCoord))
              }
              case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Must be a coord!"))
            }

          // Funny story... let's say we're current instantiating a constructor,
          // for example MySome<T>().
          // The constructor returns a MySome<T>, which means when we do the above
          // evaluating of the function body, we stamp the MySome<T> struct.
          // That ends up stamping the entire struct, including the constructor.
          // That's what we were originally here for, and evaluating the body above
          // just did it for us O_o
          // So, here we check to see if we accidentally already did it.
          opts.debugOut("doesnt this mean we have to do this in every single generated function?")

          temputs.lookupFunction(signature2) match {
            case Some(function2) => {
              (function2.header)
            }
            case None => {
              val generator = opts.functionGeneratorByName(generatorId)
              val header =
                delegate.generateFunction(this, generator, fullEnv.snapshot, temputs, callRange, Some(startingFullEnv.function), params2, maybeRetCoord)
              if (header.toSignature != signature2) {
                throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Generator made a function whose signature doesn't match the expected one!\n" +
                "Expected:  " + signature2 + "\n" +
                "Generated: " + header.toSignature))
              }
              (header)
            }
          }
        }
      }


    if (header.attributes.contains(Pure2)) {
      header.params.foreach(param => {
        if (param.tyype.permission != ReadonlyT) {
          throw CompileErrorExceptionT(NonReadonlyReferenceFoundInPureFunctionParameter(startingFullEnv.function.range, param.name))
        }
      })
    }

    header
  }

  def declareAndEvaluateFunctionBodyAndAdd(
    startingEnv: FunctionEnvironment,
    funcOuterEnv: FunctionEnvironmentBox,
    temputs: Temputs,
    bfunction1: BFunctionA,
    params2: List[ParameterT],
    isDestructor: Boolean):
  FunctionHeaderT = {
    val BFunctionA(function1, _) = bfunction1;
    val functionFullName = funcOuterEnv.fullName

    profiler.childFrame("evaluate body", () => {
      function1.maybeRetCoordRune match {
        case None => {
          val banner = FunctionBannerT(Some(function1), functionFullName, params2)
          val (body2, returns) =
            bodyTemplar.evaluateFunctionBody(
              funcOuterEnv, temputs, bfunction1.origin.params, params2, bfunction1.body, isDestructor, None) match {
              case Err(ResultTypeMismatchError(expectedType, actualType)) => {
                throw CompileErrorExceptionT(BodyResultDoesntMatch(bfunction1.origin.range, function1.name, expectedType, actualType))

              }
              case Ok((body, returns)) => (body, returns)
            }

          vassert(returns.nonEmpty)
          if (returns.size > 1) {
            throw CompileErrorExceptionT(RangedInternalErrorT(bfunction1.body.range, "Can't infer return type because " + returns.size + " types are returned:" + returns.map("\n" + _)))
          }
          val returnType2 = returns.head

          temputs.declareFunctionReturnType(banner.toSignature, returnType2)
          val attributesA = translateAttributes(function1.attributes)
          val header = FunctionHeaderT(functionFullName, attributesA, params2, returnType2, Some(function1));

          vassert(funcOuterEnv.locals.startsWith(startingEnv.locals))

          // Funny story... let's say we're current instantiating a constructor,
          // for example MySome<T>().
          // The constructor returns a MySome<T>, which means when we do the above
          // evaluating of the function body, we stamp the MySome<T> struct.
          // That ends up stamping the entire struct, including the constructor.
          // That's what we were originally here for, and evaluating the body above
          // just did it for us O_o
          // So, here we check to see if we accidentally already did it.
          temputs.lookupFunction(header.toSignature) match {
            case None => {
              val function2 = FunctionT(header, body2);
              temputs.addFunction(function2)
              (function2.header)
            }
            case Some(function2) => {
              (function2.header)
            }
          }
        }
        case Some(expectedRetCoordRune) => {
          val CoordTemplata(expectedRetCoord) =
            vassertSome(
              funcOuterEnv.getNearestTemplataWithAbsoluteName2(
                NameTranslator.translateRune(expectedRetCoordRune),
                Set(TemplataLookupContext)))
          val header = FunctionHeaderT(functionFullName, translateAttributes(function1.attributes), params2, expectedRetCoord, Some(function1));
          temputs.declareFunctionReturnType(header.toSignature, expectedRetCoord)

          temputs.deferEvaluatingFunction(
            DeferredEvaluatingFunction(
              (temputs) => finishEvaluatingDeferredFunction(temputs, funcOuterEnv.snapshot, bfunction1, isDestructor, header)))

          header
        }
      }
    })
  }

  def finishEvaluatingDeferredFunction(
      temputs: Temputs,
      // This parameter might not actually be needed eventually; we could re-infer everything from what's present in
      // the function header. Long term, might be best to not remember the entire environment.
      funcOuterEnvSnapshot: FunctionEnvironment,
      bfunction1: BFunctionA,
      isDestructor: Boolean,
      header: FunctionHeaderT) = {

    val funcOuterEnv = FunctionEnvironmentBox(funcOuterEnvSnapshot)

    funcOuterEnv.setReturnType(Some(header.returnType))

    val (body2, returns) =
      bodyTemplar.evaluateFunctionBody(
        funcOuterEnv,
        temputs,
        bfunction1.origin.params,
        header.params,
        bfunction1.body,
        isDestructor,
        Some(header.returnType)) match {
        case Err(ResultTypeMismatchError(expectedType, actualType)) => {
          throw CompileErrorExceptionT(BodyResultDoesntMatch(bfunction1.origin.range, bfunction1.origin.name, expectedType, actualType))
        }
        case Ok((body, returns)) => (body, returns)
      }

    if (returns == Set(header.returnType)) {
      // Let it through, it returns the expected type.
    } else if (returns == Set(CoordT(ShareT, ReadonlyT, NeverT()))) {
      // Let it through, it returns a never but we expect something else, that's fine
    } else {
      throw CompileErrorExceptionT(RangedInternalErrorT(bfunction1.body.range, "In function " + header + ":\nExpected return type " + header.returnType + " but was " + returns))
    }

//    vassert(funcOuterEnv.locals.startsWith(startingEnv.locals))

    // Funny story... let's say we're current instantiating a constructor,
    // for example MySome<T>().
    // The constructor returns a MySome<T>, which means when we do the above
    // evaluating of the function body, we stamp the MySome<T> struct.
    // That ends up stamping the entire struct, including the constructor.
    // That's what we were originally here for, and evaluating the body above
    // just did it for us O_o
    // So, here we check to see if we accidentally already did it.
    temputs.lookupFunction(header.toSignature) match {
      case None => temputs.addFunction(FunctionT(header, body2))
      case Some(_) =>
    }
  }

  def translateAttributes(attributesA: List[IFunctionAttributeA]) = {
    attributesA.map({
      case ExportA(packageCoord) => Export2(packageCoord)
      case UserFunctionA => UserFunction2
      case PureA => Pure2
    })
  }

  def makeExternFunction(
      temputs: Temputs,
      fullName: FullNameT[IFunctionNameT],
      range: RangeS,
      attributes: List[IFunctionAttribute2],
      params2: List[ParameterT],
      returnType2: CoordT,
      maybeOrigin: Option[FunctionA]):
  (FunctionHeaderT) = {
    fullName.last match {
//      case FunctionName2("===", templateArgs, paramTypes) => {
//        vcheck(templateArgs.size == 1, () => CompileErrorExceptionT(RangedInternalErrorT(range, "=== should have 1 template params!")))
//        vcheck(paramTypes.size == 2, () => CompileErrorExceptionT(RangedInternalErrorT(range, "=== should have 2 params!")))
//        val List(tyype) = templateArgs
//        val List(leftParamType, rightParamType) = paramTypes
//        vassert(leftParamType == rightParamType, "=== left and right params should be same type")
//        vassert(leftParamType == tyype)
//        vassert(rightParamType == tyype)
//
//      }
      case FunctionNameT(humanName, List(), params) => {
        val header = FunctionHeaderT(fullName, Extern2(range.file.packageCoordinate) :: attributes, params2, returnType2, maybeOrigin)

        val externFullName = FullNameT(fullName.packageCoord, List(), ExternFunctionNameT(humanName, params))
        val externPrototype = PrototypeT(externFullName, header.returnType)
        temputs.addFunctionExtern(externPrototype, fullName.packageCoord, humanName)

        val argLookups =
          header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) })
        val function2 =
          FunctionT(
            header,
            ReturnTE(ExternFunctionCallTE(externPrototype, argLookups)))

        temputs.declareFunctionReturnType(header.toSignature, header.returnType)
        temputs.addFunction(function2)
        (header)
      }
      case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Only human-named function can be extern!"))
    }
  }

  def translateFunctionAttributes(a: List[IFunctionAttributeA]): List[IFunctionAttribute2] = {
    a.map({
      case UserFunctionA => UserFunction2
      case ExternA(packageCoord) => Extern2(packageCoord)
      case x => vimpl(x.toString)
    })
  }


  def makeInterfaceFunction(
    env: FunctionEnvironment,
    temputs: Temputs,
    origin: Option[FunctionA],
    params2: List[ParameterT],
    returnReferenceType2: CoordT):
  (FunctionHeaderT) = {
    vassert(params2.exists(_.virtuality == Some(AbstractT$)))
    val header =
      FunctionHeaderT(
        env.fullName,
        List(),
        params2,
        returnReferenceType2,
        origin)
    val function2 =
      FunctionT(
        header,
        BlockTE(
          List(
            ReturnTE(
              InterfaceFunctionCallTE(
                header,
                header.returnType,
                header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) }))))))

      temputs
        .declareFunctionReturnType(header.toSignature, returnReferenceType2)
      temputs.addFunction(function2)
    vassert(temputs.getDeclaredSignatureOrigin(env.fullName).nonEmpty)
    header
  }

  def makeImplDestructor(
    env: FunctionEnvironment,
    temputs: Temputs,
    maybeOriginFunction1: Option[FunctionA],
    structDef2: StructDefinitionT,
    interfaceRef2: InterfaceRefT,
    structDestructor: PrototypeT,
  ):
  (FunctionHeaderT) = {
    val ownership = if (structDef2.mutability == MutableT) OwnT else ShareT
    val permission = if (structDef2.mutability == MutableT) ReadwriteT else ReadonlyT
    val structRef2 = structDef2.getRef
    val structType2 = CoordT(ownership, permission, structRef2)

    val destructor2 =
      FunctionT(
        FunctionHeaderT(
          env.fullName,
          List(),
          List(ParameterT(CodeVarNameT("this"), Some(OverrideT(interfaceRef2)), structType2)),
          CoordT(ShareT, ReadonlyT, VoidT()),
          maybeOriginFunction1),
        BlockTE(
          List(
            ReturnTE(
              FunctionCallTE(
                structDestructor,
                List(ArgLookupTE(0, structType2)))))))

    // If this fails, then the signature the FunctionTemplarMiddleLayer made for us doesn't
    // match what we just made
    vassert(
      temputs.getDeclaredSignatureOrigin(
        destructor2.header.toSignature).nonEmpty)

    // we cant make the destructor here because they might have a user defined one somewhere

      temputs
        .declareFunctionReturnType(destructor2.header.toSignature, destructor2.header.returnType)
      temputs.addFunction(destructor2);

    vassert(
      temputs.getDeclaredSignatureOrigin(
        destructor2.header.toSignature).nonEmpty)

    (destructor2.header)
  }
}
