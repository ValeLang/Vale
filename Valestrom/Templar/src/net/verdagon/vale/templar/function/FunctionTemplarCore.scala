package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{AbstractBodyS, Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar.{ast, _}
import net.verdagon.vale.templar.ast.{AbstractT, ArgLookupTE, BlockTE, ExternFunctionCallTE, ExternT, FunctionCallTE, FunctionHeaderT, FunctionT, IFunctionAttributeT, InterfaceFunctionCallTE, LocationInFunctionEnvironment, OverrideT, ParameterT, PrototypeT, PureT, ReferenceExpressionTE, ReturnTE, SignatureT, UserFunctionT}
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.expression.CallTemplar
import net.verdagon.vale.templar.names.{CodeVarNameT, ExternFunctionNameT, FullNameT, FunctionNameT, IFunctionNameT, NameTranslator}
import net.verdagon.vale.{Err, Profiler, Interner, Ok, RangeS, vassert, vassertOne, vassertSome, vcheck, vcurious, vfail, vimpl, vwat}

import scala.collection.immutable.{List, Set}

case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class FunctionTemplarCore(
    opts: TemplarOptions,

    interner: Interner,
    nameTranslator: NameTranslator,

    templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IFunctionTemplarDelegate) {
  val bodyTemplar = new BodyTemplar(opts, nameTranslator, templataTemplar, convertHelper, new IBodyTemplarDelegate {
    override def evaluateBlockStatements(
      temputs: Temputs,
      startingNenv: NodeEnvironment,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
      exprs: BlockSE
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      delegate.evaluateBlockStatements(temputs, startingNenv, nenv, life, exprs)
    }

    override def translatePatternList(
        temputs: Temputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironment,
        patterns1: Vector[AtomSP],
        patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      delegate.translatePatternList(temputs, nenv, life, patterns1, patternInputExprs2)
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
        case FunctionNameT(humanName, _, _) if humanName == CallTemplar.DROP_FUNCTION_NAME => true
        case _ => false
      })

    val maybeExport =
      startingFullEnv.function.attributes.collectFirst { case e@ExportS(_) => e }


    val header =
      startingFullEnv.function.body match {
        case CodeBodyS(body) => {
          declareAndEvaluateFunctionBodyAndAdd(
              startingFullEnv, fullEnv, temputs, life, params2, isDestructor)
        }
        case ExternBodyS => {
          val maybeRetCoord =
            fullEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(startingFullEnv.function.maybeRetCoordRune.get.rune)), Set(TemplataLookupContext)).headOption
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
        case AbstractBodyS | GeneratedBodyS(_) => {
          val generatorId =
            startingFullEnv.function.body match {
              case AbstractBodyS => "abstractBody"
              case GeneratedBodyS(generatorId) => generatorId
            }
          val signature2 = SignatureT(fullEnv.fullName);
          val maybeRetTemplata =
            startingFullEnv.function.maybeRetCoordRune match {
              case None => (None)
              case Some(retCoordRune) => {
                fullEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(retCoordRune.rune)), Set(TemplataLookupContext)).headOption
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
              val generator = vassertSome(fullEnv.globalEnv.nameToFunctionBodyMacro.get(generatorId))
              val header =
                generator.generateFunctionBody(
                  fullEnv.snapshot, temputs, generatorId, life, callRange,
                  Some(startingFullEnv.function), params2, maybeRetCoord)
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
          exportPackageCoord.packageCoordinate,
          exportedName)
      }
    }

    if (header.attributes.contains(PureT)) {
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
    paramsT: Vector[ParameterT],
    isDestructor: Boolean):
  FunctionHeaderT = {
    val functionFullName = fullEnv.fullName

    val attributesWithoutExport =
      startingFullEnv.function.attributes.filter({
        case ExportS(_) => false
        case _ => true
      })

    val attributesT = translateAttributes(attributesWithoutExport)

    val maybeExplicitReturnCoord =
      startingFullEnv.function.maybeRetCoordRune match {
        case Some(retCoordRune) => {
          startingFullEnv.lookupNearestWithImpreciseName(

              interner.intern(RuneNameS(retCoordRune.rune)),
              Set(TemplataLookupContext))  match {
            case Some(CoordTemplata(retCoord)) => Some(retCoord)
            case other => vwat(other)
          }
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
            life,
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
                life,
                attributesT,
                paramsT,
                isDestructor,
                maybeExplicitReturnCoord,
                Some(header))
            }))
        header
      }
    }
  }

  def finalizeHeader(
      fullEnv: FunctionEnvironmentBox,
      temputs: Temputs,
      attributesT: Vector[IFunctionAttributeT],
      paramsT: Vector[ParameterT],
      returnCoord: CoordT) = {
    val header = ast.FunctionHeaderT(fullEnv.fullName, attributesT, paramsT, returnCoord, Some(fullEnv.function));
    temputs.declareFunctionReturnType(header.toSignature, returnCoord)
    header
  }

  // By MaybeDeferred we mean that this function might be called later, to reduce reentrancy.
  private def finishFunctionMaybeDeferred(
      temputs: Temputs,
      fullEnvSnapshot: FunctionEnvironment,
      life: LocationInFunctionEnvironment,
      attributesT: Vector[IFunctionAttributeT],
      paramsT: Vector[ParameterT],
      isDestructor: Boolean,
      maybeExplicitReturnCoord: Option[CoordT],
      maybePreKnownHeader: Option[FunctionHeaderT]):
  FunctionHeaderT = {
    val fullEnv = FunctionEnvironmentBox(fullEnvSnapshot)
    val (maybeInferredReturnCoord, body2) =
      bodyTemplar.declareAndEvaluateFunctionBody(
        fullEnv, temputs, life, fullEnv.function, maybeExplicitReturnCoord, paramsT, isDestructor)

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

  def translateAttributes(attributesA: Vector[IFunctionAttributeS]) = {
    attributesA.map({
      //      case ExportA(packageCoord) => Export2(packageCoord)
      case UserFunctionS => UserFunctionT
      case PureS => PureT
    })
  }

  def makeExternFunction(
      temputs: Temputs,
      fullName: FullNameT[IFunctionNameT],
      range: RangeS,
      attributes: Vector[IFunctionAttributeT],
      params2: Vector[ParameterT],
      returnType2: CoordT,
      maybeOrigin: Option[FunctionA]):
  (FunctionHeaderT) = {
    fullName.last match {
      case FunctionNameT(humanName, Vector(), params) => {
        val header =
          ast.FunctionHeaderT(
            fullName,
            Vector(ExternT(range.file.packageCoordinate)) ++ attributes,
            params2,
            returnType2,
            maybeOrigin)

        val externFullName = FullNameT(fullName.packageCoord, Vector.empty, interner.intern(ExternFunctionNameT(humanName, params)))
        val externPrototype = PrototypeT(externFullName, header.returnType)
        temputs.addFunctionExtern(range, externPrototype, fullName.packageCoord, humanName)

        val argLookups =
          header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) })
        val function2 =
          ast.FunctionT(
            header,
            ReturnTE(ExternFunctionCallTE(externPrototype, argLookups)))

        temputs.declareFunctionReturnType(header.toSignature, header.returnType)
        temputs.addFunction(function2)
        (header)
      }
      case _ => throw CompileErrorExceptionT(RangedInternalErrorT(range, "Only human-named function can be extern!"))
    }
  }

  def translateFunctionAttributes(a: Vector[IFunctionAttributeS]): Vector[IFunctionAttributeT] = {
    a.map({
      case UserFunctionS => UserFunctionT
      case ExternS(packageCoord) => ExternT(packageCoord)
      case x => vimpl(x.toString)
    })
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
      ast.FunctionT(
        ast.FunctionHeaderT(
          env.fullName,
          Vector.empty,
          Vector(ast.ParameterT(interner.intern(CodeVarNameT("this")), Some(OverrideT(interfaceTT)), structType2)),
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
