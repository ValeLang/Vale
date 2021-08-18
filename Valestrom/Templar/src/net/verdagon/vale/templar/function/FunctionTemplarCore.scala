package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata.{IFunctionAttribute2, TemplataTemplar, _}
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.{Err, IProfiler, Ok, vassert, vassertOne, vassertSome, vcheck, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Set}

case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }

class FunctionTemplarCore(
    opts: TemplarOptions,
  profiler: IProfiler,
  newTemplataStore: () => TemplatasStore,
  templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IFunctionTemplarDelegate) {
  val bodyTemplar = new BodyTemplar(opts, profiler, newTemplataStore, templataTemplar, convertHelper, new IBodyTemplarDelegate {
    override def evaluateBlockStatements(
      temputs: Temputs,
      startingFate: FunctionEnvironment,
      fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
      exprs: Vector[IExpressionAE]
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      delegate.evaluateBlockStatements(temputs, startingFate, fate, life, exprs)
    }

    override def translatePatternList(
        temputs: Temputs,
        fate: FunctionEnvironmentBox,
      life: LocationInFunctionEnvironment,
        patterns1: Vector[AtomAP],
        patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      delegate.translatePatternList(temputs, fate, life, patterns1, patternInputExprs2)
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
      params2: Vector[ParameterT]):
  (FunctionHeaderT) = {
    val fullEnv = FunctionEnvironmentBox(startingFullEnv)

    opts.debugOut("Evaluating function " + fullEnv.fullName)

    val life = LocationInFunctionEnvironment(Vector())

    val isDestructor =
      params2.nonEmpty &&
      params2.head.tyype.ownership == OwnT &&
      (startingFullEnv.fullName.last match {
        case FunctionNameT(humanName, _, _) if humanName == CallTemplar.MUT_DESTRUCTOR_NAME => true
        case _ => false
      })

    val maybeExport =
      startingFullEnv.function.attributes.collectFirst { case e@ExportA(_) => e }


    val header =
      startingFullEnv.function.body match {
        case CodeBodyA(body) => {
          declareAndEvaluateFunctionBodyAndAdd(
              startingFullEnv, fullEnv, temputs, life, BFunctionA(startingFullEnv.function, body), params2, isDestructor)
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
                delegate.generateFunction(
                  this, generator, fullEnv.snapshot, temputs, life, callRange, Some(startingFullEnv.function), params2, maybeRetCoord)
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

    maybeExport match {
      case None =>
      case Some(exportPackageCoord) => {
        val exportedName =
          startingFullEnv.fullName.last match {
            case FunctionNameT(humanName, _, _) => humanName
            case _ => vfail("Can't export something that doesn't have a human readable name!")
          }
        temputs.addFunctionExport(
          startingFullEnv.function.range,
          header.toPrototype,
          exportPackageCoord.packageCoord,
          exportedName)
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
    startingFullEnv: FunctionEnvironment,
    fullEnv: FunctionEnvironmentBox,
    temputs: Temputs,
    life: LocationInFunctionEnvironment,
    bfunction1: BFunctionA,
    paramsT: Vector[ParameterT],
    isDestructor: Boolean):
  FunctionHeaderT = {
    val BFunctionA(function1, _) = bfunction1;
    val functionFullName = fullEnv.fullName

    val attributesWithoutExport =
      startingFullEnv.function.attributes.filter({
        case ExportA(_) => false
        case _ => true
      })

    profiler.childFrame("evaluate body", () => {
      val attributesT = translateAttributes(attributesWithoutExport)

      val maybeExplicitReturnCoord =
        startingFullEnv.function.maybeRetCoordRune match {
          case Some(retCoordRune) => {
            val maybeRetTemplata =
              startingFullEnv.getNearestTemplataWithAbsoluteName2(
                NameTranslator.translateRune(retCoordRune),
                Set(TemplataLookupContext))
            val retCoord =
              maybeRetTemplata match {
                case None => vwat()
                case Some(CoordTemplata(retCoord)) => retCoord
                case Some(_) => vwat()
              }
            Some(retCoord)
          }
          case None => None
        }

      maybeExplicitReturnCoord match {
        case None => {
          opts.debugOut("Eagerly evaluating function: " + functionFullName)
          val header =
            finishFunctionMaybeDeferred(
              temputs,
              fullEnv.snapshot,
              startingFullEnv,
              life,
              bfunction1,
              attributesT,
              paramsT,
              isDestructor,
              maybeExplicitReturnCoord,
              None)
          header
        }
        case Some(explicitReturnCoord) => {
          fullEnv.setReturnType(Some(explicitReturnCoord))
          val header = finalizeHeader(fullEnv, temputs, attributesT, paramsT, explicitReturnCoord)
          opts.debugOut("Deferring function: " + header.fullName)
          temputs.deferEvaluatingFunction(
            DeferredEvaluatingFunction(
              header.toPrototype,
              (temputs) => {
                opts.debugOut("Finishing function: " + header.fullName)
                finishFunctionMaybeDeferred(
                  temputs,
                  fullEnv.snapshot,
                  startingFullEnv,
                  life,
                  bfunction1,
                  attributesT,
                  paramsT,
                  isDestructor,
                  maybeExplicitReturnCoord,
                  Some(header))
              }))
          header
        }
      }
    })
  }

  def finalizeHeader(
      fullEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      attributesT: Vector[IFunctionAttribute2],
      paramsT: Vector[ParameterT],
      returnCoord: CoordT) = {
    val header = FunctionHeaderT(fullEnv.fullName, attributesT, paramsT, returnCoord, Some(fullEnv.function));
    temputs.declareFunctionReturnType(header.toSignature, returnCoord)
    header
  }

  // By MaybeDeferred we mean that this function might be called later, to reduce reentrancy.
  private def finishFunctionMaybeDeferred(
      temputs: Temputs,
      fullEnvSnapshot: FunctionEnvironment,
      startingFullEnvSnapshot: FunctionEnvironment,
      life: LocationInFunctionEnvironment,
      bfunction1: BFunctionA,
      attributesT: Vector[IFunctionAttribute2],
      paramsT: Vector[ParameterT],
      isDestructor: Boolean,
      maybeExplicitReturnCoord: Option[CoordT],
      maybePreKnownHeader: Option[FunctionHeaderT]):
  FunctionHeaderT = {
    val fullEnv = FunctionEnvironmentBox(fullEnvSnapshot)
    val (maybeInferredReturnCoord, body2) =
      bodyTemplar.declareAndEvaluateFunctionBody(
        fullEnv, temputs, life, BFunctionA(fullEnv.function, bfunction1.body), maybeExplicitReturnCoord, paramsT, isDestructor)

    val maybePostKnownHeader =
      maybeInferredReturnCoord match {
        case None => None
        case Some(explicitReturnCoord) => {
          Some(finalizeHeader(fullEnv, temputs, attributesT, paramsT, explicitReturnCoord))
        }
      }

    val header = vassertOne(maybePreKnownHeader.toVector ++ maybePostKnownHeader.toVector)

    // Funny story... let's say we're current instantiating a constructor,
    // for example MySome<T>().
    // The constructor returns a MySome<T>, which means when we do the above
    // evaluating of the function body, we stamp the MySome<T> struct.
    // That ends up stamping the entire struct, including the constructor.
    // That's what we were originally here for, and evaluating the body above
    // just did it for us O_o
    // So, here we check to see if we accidentally already did it.

    // Get the variables by diffing the function environment.
    // Remember, the near env contains closure variables, which we
    // don't care about here. So find the difference between the near
    // env and our latest env.
    vassert(fullEnv.locals.startsWith(startingFullEnvSnapshot.locals))

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

  def translateAttributes(attributesA: Vector[IFunctionAttributeA]) = {
    attributesA.map({
      //      case ExportA(packageCoord) => Export2(packageCoord)
      case UserFunctionA => UserFunction2
      case PureA => Pure2
    })
  }

  def makeExternFunction(
      temputs: Temputs,
      fullName: FullNameT[IFunctionNameT],
      range: RangeS,
      attributes: Vector[IFunctionAttribute2],
      params2: Vector[ParameterT],
      returnType2: CoordT,
      maybeOrigin: Option[FunctionA]):
  (FunctionHeaderT) = {
    fullName.last match {
//      case FunctionName2("===", templateArgs, paramTypes) => {
//        vcheck(templateArgs.size == 1, () => CompileErrorExceptionT(RangedInternalErrorT(range, "=== should have 1 template params!")))
//        vcheck(paramTypes.size == 2, () => CompileErrorExceptionT(RangedInternalErrorT(range, "=== should have 2 params!")))
//        val Vector(tyype) = templateArgs
//        val Vector(leftParamType, rightParamType) = paramTypes
//        vassert(leftParamType == rightParamType, "=== left and right params should be same type")
//        vassert(leftParamType == tyype)
//        vassert(rightParamType == tyype)
//
//      }
      case FunctionNameT(humanName, Vector(), params) => {
        val header =
          FunctionHeaderT(
            fullName,
            Vector(Extern2(range.file.packageCoordinate)) ++ attributes,
            params2,
            returnType2,
            maybeOrigin)

        val externFullName = FullNameT(fullName.packageCoord, Vector.empty, ExternFunctionNameT(humanName, params))
        val externPrototype = PrototypeT(externFullName, header.returnType)
        temputs.addFunctionExtern(range, externPrototype, fullName.packageCoord, humanName)

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

  def translateFunctionAttributes(a: Vector[IFunctionAttributeA]): Vector[IFunctionAttribute2] = {
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
    params2: Vector[ParameterT],
    returnReferenceType2: CoordT):
  (FunctionHeaderT) = {
    vassert(params2.exists(_.virtuality == Some(AbstractT$)))
    val header =
      FunctionHeaderT(
        env.fullName,
        Vector.empty,
        params2,
        returnReferenceType2,
        origin)
    val function2 =
      FunctionT(
        header,
        BlockTE(
            ReturnTE(
              InterfaceFunctionCallTE(
                header,
                header.returnType,
                header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) })))))

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
    structDefT: StructDefinitionT,
    interfaceTT: InterfaceTT,
    structDestructor: PrototypeT,
  ):
  (FunctionHeaderT) = {
    val ownership = if (structDefT.mutability == MutableT) OwnT else ShareT
    val permission = if (structDefT.mutability == MutableT) ReadwriteT else ReadonlyT
    val structTT = structDefT.getRef
    val structType2 = CoordT(ownership, permission, structTT)

    val destructor2 =
      FunctionT(
        FunctionHeaderT(
          env.fullName,
          Vector.empty,
          Vector(ParameterT(CodeVarNameT("this"), Some(OverrideT(interfaceTT)), structType2)),
          CoordT(ShareT, ReadonlyT, VoidT()),
          maybeOriginFunction1),
        BlockTE(
            ReturnTE(
              FunctionCallTE(
                structDestructor,
                Vector(ArgLookupTE(0, structType2))))))

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
