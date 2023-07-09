package dev.vale.typing.function

import dev.vale.highertyping.FunctionA
import dev.vale.{Err, Interner, Keywords, Ok, Profiler, RangeS, U, vassert, vassertOne, vassertSome, vcheck, vcurious, vfail, vimpl, vwat}
import dev.vale.postparsing._
import dev.vale.postparsing.patterns.AtomSP
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, ConvertHelper, DeferredEvaluatingFunctionBody, RangedInternalErrorT, TemplataCompiler, TypingPassOptions, ast}
import dev.vale.typing.ast.{ArgLookupTE, ExternFunctionCallTE, ExternT, FunctionDefinitionT, FunctionHeaderT, IFunctionAttributeT, LocationInFunctionEnvironmentT, ParameterT, PrototypeT, PureT, ReferenceExpressionTE, ReturnTE, SignatureT, UserFunctionT}
import dev.vale.typing.env._
import dev.vale.typing.expression.CallCompiler
import dev.vale.typing.names.{ExternFunctionNameT, FunctionNameT, FunctionTemplateNameT, IFunctionNameT, IdT, NameTranslator, RuneNameT}
import dev.vale.typing.templata.CoordTemplataT
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.typing.types._
import dev.vale.typing.templata._
import dev.vale.typing.{ast, _}
import dev.vale.typing.ast._
import dev.vale.typing.citizen.ImplCompiler
import dev.vale.typing.env._

import scala.collection.immutable.{List, Set}

case class ResultTypeMismatchError(expectedType: CoordT, actualType: CoordT) { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; override def equals(obj: Any): Boolean = vcurious(); }

class FunctionCompilerCore(
    opts: TypingPassOptions,
    interner: Interner,
    keywords: Keywords,
    nameTranslator: NameTranslator,

    templataCompiler: TemplataCompiler,
    convertHelper: ConvertHelper,
    delegate: IFunctionCompilerDelegate) {
  val bodyCompiler = new BodyCompiler(opts, nameTranslator, templataCompiler, convertHelper, new IBodyCompilerDelegate {
    override def evaluateBlockStatements(
      coutputs: CompilerOutputs,
      startingNenv: NodeEnvironmentT,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironmentT,
      parentRanges: List[RangeS],
      callLocation: LocationInDenizen,
      exprs: BlockSE
    ): (ReferenceExpressionTE, Set[CoordT]) = {
      delegate.evaluateBlockStatements(coutputs, startingNenv, nenv, life, parentRanges, callLocation, exprs)
    }

    override def translatePatternList(
      coutputs: CompilerOutputs,
      nenv: NodeEnvironmentBox,
      life: LocationInFunctionEnvironmentT,
      parentRanges: List[RangeS],
      patterns1: Vector[AtomSP],
      patternInputExprs2: Vector[ReferenceExpressionTE]
    ): ReferenceExpressionTE = {
      delegate.translatePatternList(coutputs, nenv, life, parentRanges, patterns1, patternInputExprs2)
    }
  })

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def evaluateFunctionForHeader(
    fullEnv: FunctionEnvironmentT,
    coutputs: CompilerOutputs,
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    params2: Vector[ParameterT]):
  (FunctionHeaderT) = {
//    opts.debugOut("Evaluating function " + fullEnv.fullName)

//    val functionTemplateName = TemplataCompiler.getFunctionTemplate(fullEnv.fullName)
    val functionTemplateName = fullEnv.id

    val life = LocationInFunctionEnvironmentT(Vector())

    val isDestructor =
      params2.nonEmpty &&
        params2.head.tyype.ownership == OwnT &&
        (fullEnv.id.localName match {
          case FunctionNameT(humanName, _, _) if humanName == keywords.drop => true
          case _ => false
        })

    val maybeExport =
      fullEnv.function.attributes.collectFirst { case e@ExportS(_) => e }

    val signature2 = SignatureT(fullEnv.id);
    val maybeRetTemplata =
      fullEnv.function.maybeRetCoordRune match {
        case None => (None)
        case Some(retCoordRune) => {
          fullEnv.lookupNearestWithImpreciseName(interner.intern(RuneNameS(retCoordRune.rune)), Set(TemplataLookupContext)).headOption
        }
      }
    val maybeRetCoord =
      maybeRetTemplata match {
        case None => (None)
        case Some(CoordTemplataT(retCoord)) => {
          coutputs.declareFunctionReturnType(signature2, retCoord)
          (Some(retCoord))
        }
        case _ => throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Must be a coord!"))
      }

    val header =
      fullEnv.function.body match {
        case CodeBodyS(body) => {
          val attributesWithoutExport =
            fullEnv.function.attributes.filter({
              case ExportS(_) => false
              case _ => true
            })
          val attributesT = translateAttributes(attributesWithoutExport)

          maybeRetCoord match {
            case Some(returnCoord) => {
              val header =
                finalizeHeader(fullEnv, coutputs, attributesT, params2, returnCoord)

              coutputs.deferEvaluatingFunctionBody(
                DeferredEvaluatingFunctionBody(
                  header.toPrototype,
                  (coutputs) => {
                    finishFunctionMaybeDeferred(
                      coutputs, fullEnv, callRange, callLocation, life, attributesT, params2, isDestructor, Some(returnCoord))
                  }))

              (header)
            }
            case None => {
              val header =
                finishFunctionMaybeDeferred(
                  coutputs, fullEnv, callRange, callLocation, life, attributesT, params2, isDestructor, None)
              (header)
            }
          }
        }
        case ExternBodyS => {
          val retCoord = vassertSome(maybeRetCoord)
          val header =
            makeExternFunction(
              coutputs,
              fullEnv,
              fullEnv.function.range,
              translateFunctionAttributes(fullEnv.function.attributes),
              params2,
              retCoord,
              Some(FunctionTemplataT(fullEnv.parentEnv, fullEnv.function)))
          (header)
        }
        case AbstractBodyS | GeneratedBodyS(_) => {
          val generatorId =
            fullEnv.function.body match {
              case AbstractBodyS => keywords.abstractBody
              case GeneratedBodyS(generatorId) => generatorId
            }

          // Funny story... let's say we're current instantiating a constructor,
          // for example MySome<T>().
          // The constructor returns a MySome<T>, which means when we do the above
          // evaluating of the function body, we stamp the MySome<T> struct.
          // That ends up stamping the entire struct, including the constructor.
          // That's what we were originally here for, and evaluating the body above
          // just did it for us O_o
          // So, here we check to see if we accidentally already did it.
          //   opts.debugOut("doesnt this mean we have to do this in every single generated function?")
          //   coutputs.lookupFunction(signature2) match {
          //     case Some(function2) => {
          //       (function2.header)
          //     }
          //     case None => {
          //       val generator = vassertSome(fullEnv.globalEnv.nameToFunctionBodyMacro.get(generatorId))
          //       val (header, body) =
          //         generator.generateFunctionBody(
          //           fullEnv, coutputs, generatorId, life, callRange,
          //           Some(fullEnv.function), params2, maybeRetCoord)
          //
          //       coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
          //       val runeToFunctionBound = TemplataCompiler.assembleFunctionBoundToRune(fullEnv.templatas)
          //       coutputs.addFunction(FunctionT(header, runeToFunctionBound, body))
          //
          //       if (header.toSignature != signature2) {
          //         throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Generator made a function whose signature doesn't match the expected one!\n" +
          //           "Expected:  " + signature2 + "\n" +
          //           "Generated: " + header.toSignature))
          //       }
          //       (header)
          //     }
          //   }
          // Note from later: This might not be true anymore, since we have real generics.
          vassert(coutputs.lookupFunction(signature2).isEmpty)

          val generator = vassertSome(fullEnv.globalEnv.nameToFunctionBodyMacro.get(generatorId))
          val (header, body) =
            generator.generateFunctionBody(
              fullEnv, coutputs, generatorId, life, callRange, callLocation,
              Some(fullEnv.function), params2, maybeRetCoord)

          coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
          val neededFunctionBounds = TemplataCompiler.assembleRuneToFunctionBound(fullEnv.templatas)
          val neededImplBounds = TemplataCompiler.assembleRuneToImplBound(fullEnv.templatas)
          coutputs.addFunction(FunctionDefinitionT(header, neededFunctionBounds, neededImplBounds, body))

          if (header.toSignature != signature2) {
            throw CompileErrorExceptionT(RangedInternalErrorT(callRange, "Generator made a function whose signature doesn't match the expected one!\n" +
              "Expected:  " + signature2 + "\n" +
              "Generated: " + header.toSignature))
          }
          header
        }
      }

    // maybeExport match {
    //   case None =>
    //   case Some(exportPackageCoord) => {
    //     val exportedName =
    //       fullEnv.id.localName match {
    //         case FunctionNameT(FunctionTemplateNameT(humanName, _), _, _) => humanName
    //         case _ => vfail("Can't export something that doesn't have a human readable name!")
    //       }
    //     coutputs.addInstantiationBounds(header.toPrototype.id, InstantiationBoundArgumentsT(Map(), Map()))
    //     coutputs.addFunctionExport(
    //       fullEnv.function.range,
    //       header.toPrototype,
    //       exportPackageCoord.packageCoordinate,
    //       exportedName)
    //   }
    // }

    if (header.attributes.contains(PureT)) {
      //      header.params.foreach(param => {
      //        if (param.tyype.permission != ReadonlyT) {
      //          throw CompileErrorExceptionT(NonReadonlyReferenceFoundInPureFunctionParameter(fullEnv.function.range, param.name))
      //        }
      //      })
    }

    header
  }

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def getFunctionPrototypeForCall(
    fullEnv: FunctionEnvironmentT,
      coutputs: CompilerOutputs,
    callRange: List[RangeS],
      params2: Vector[ParameterT]):
  (PrototypeT) = {
    getFunctionPrototypeInnerForCall(
      fullEnv, fullEnv.id)
  }

  def getFunctionPrototypeInnerForCall(
    fullEnv: FunctionEnvironmentT,
    id: IdT[IFunctionNameT]):
  PrototypeT = {
    val retCoordRune = vassertSome(fullEnv.function.maybeRetCoordRune)
    val returnCoord =
      fullEnv.lookupNearestWithImpreciseName(
        interner.intern(RuneNameS(retCoordRune.rune)),
        Set(TemplataLookupContext))  match {
        case Some(CoordTemplataT(retCoord)) => retCoord
        case other => vwat(other)
      }
    PrototypeT(id, returnCoord)
  }

  def finalizeHeader(
      fullEnv: FunctionEnvironmentT,
      coutputs: CompilerOutputs,
      attributesT: Vector[IFunctionAttributeT],
      paramsT: Vector[ParameterT],
      returnCoord: CoordT) = {
    val header = FunctionHeaderT(fullEnv.id, attributesT, paramsT, returnCoord, Some(FunctionTemplataT(fullEnv.parentEnv, fullEnv.function)));
    coutputs.declareFunctionReturnType(header.toSignature, returnCoord)
    header
  }

  // By MaybeDeferred we mean that this function might be called later, to reduce reentrancy.
  private def finishFunctionMaybeDeferred(
      coutputs: CompilerOutputs,
      fullEnvSnapshot: FunctionEnvironmentT,
      callRange: List[RangeS],
      callLocation: LocationInDenizen,
      life: LocationInFunctionEnvironmentT,
      attributesT: Vector[IFunctionAttributeT],
      paramsT: Vector[ParameterT],
      isDestructor: Boolean,
      maybeExplicitReturnCoord: Option[CoordT]):
  FunctionHeaderT = {
    val (maybeEvaluatedRetCoord, body2) =
      bodyCompiler.declareAndEvaluateFunctionBody(
        FunctionEnvironmentBoxT(fullEnvSnapshot),
        coutputs, life, callRange, callLocation, fullEnvSnapshot.function, maybeExplicitReturnCoord, paramsT, isDestructor)

    val retCoord = vassertOne(maybeExplicitReturnCoord.toList ++ maybeEvaluatedRetCoord.toList)
    val header = finalizeHeader(fullEnvSnapshot, coutputs, attributesT, paramsT, retCoord)

    // Funny story... let's say we're current instantiating a constructor,
    // for example MySome<T>().
    // The constructor returns a MySome<T>, which means when we do the above
    // evaluating of the function body, we stamp the MySome<T> struct.
    // That ends up stamping the entire struct, including the constructor.
    // That's what we were originally here for, and evaluating the body above
    // just did it for us O_o
    // So, here we check to see if we accidentally already did it.
    // Note from later: this might not be true anymore now that we have real generics.
    //   coutputs.lookupFunction(header.toSignature) match {
    //     case None => {
    //       val functionBoundToRune = TemplataCompiler.assembleFunctionBoundToRune(fullEnv.templatas)
    //       val function2 = FunctionT(header, functionBoundToRune, body2);
    //       coutputs.addFunction(function2)
    //       (function2.header)
    //     }
    //     case Some(function2) => {
    //       (function2.header)
    //     }
    //   }
    vassert(coutputs.lookupFunction(header.toSignature).isEmpty)
    val neededFunctionBounds = TemplataCompiler.assembleRuneToFunctionBound(fullEnvSnapshot.templatas)
    val neededImplBounds = TemplataCompiler.assembleRuneToImplBound(fullEnvSnapshot.templatas)
    val function2 = FunctionDefinitionT(header, neededFunctionBounds, neededImplBounds, body2);
    coutputs.addFunction(function2)
    header
  }

  def translateAttributes(attributesA: Vector[IFunctionAttributeS]) = {
    attributesA.map({
      //      case ExportA(packageCoord) => Export2(packageCoord)
      case UserFunctionS => UserFunctionT
      case PureS => PureT
    })
  }

  def makeExternFunction(
      coutputs: CompilerOutputs,
      env: FunctionEnvironmentT,
      range: RangeS,
      attributes: Vector[IFunctionAttributeT],
      params2: Vector[ParameterT],
      returnType2: CoordT,
      maybeOrigin: Option[FunctionTemplataT]):
  (FunctionHeaderT) = {
    env.id.localName match {
      case FunctionNameT(FunctionTemplateNameT(humanName, _), Vector(), params) => {
        val header =
          ast.FunctionHeaderT(
            env.id,
            attributes,
//            Vector(RegionT(env.defaultRegion.localName, true)),
            params2,
            returnType2,
            maybeOrigin)

        val externFunctionId = IdT(env.id.packageCoord, Vector.empty, interner.intern(ExternFunctionNameT(humanName, params)))
        val externPrototype = PrototypeT(externFunctionId, header.returnType)

        coutputs.addInstantiationBounds(externPrototype.id, InstantiationBoundArgumentsT(Map(), Map()))

        val argLookups =
          header.params.zipWithIndex.map({ case (param2, index) => ArgLookupTE(index, param2.tyype) })
        val function2 =
          FunctionDefinitionT(
            header,
            Map(),
            Map(),
            ReturnTE(ExternFunctionCallTE(externPrototype, argLookups)))

        coutputs.declareFunctionReturnType(header.toSignature, header.returnType)
        coutputs.addFunction(function2)
        (header)
      }
      case _ => {
        throw CompileErrorExceptionT(RangedInternalErrorT(List(range), "Only human-named function can be extern!"))
      }
    }
  }

  def translateFunctionAttributes(a: Vector[IFunctionAttributeS]): Vector[IFunctionAttributeT] = {
    U.map[IFunctionAttributeS, IFunctionAttributeT](a, {
      case UserFunctionS => UserFunctionT
      case ExternS(packageCoord) => ExternT(packageCoord)
      case x => vimpl(x)
    })
  }


//  def makeImplDestructor(
//    env: FunctionEnvironment,
//    coutputs: CompilerOutputs,
//    maybeOriginFunction1: Option[FunctionA],
//    structDefT: StructDefinitionT,
//    interfaceTT: InterfaceTT,
//    structDestructor: PrototypeT,
//  ):
//  (FunctionHeaderT) = {
//    val ownership = if (structDefT.mutability == MutableT) OwnT else ShareT
//    val permission = if (structDefT.mutability == MutableT) ReadwriteT else ReadonlyT
//    val structTT = structDefT.getRef
//    val structType2 = CoordT(ownership, permission, structTT)
//
//    val destructor2 =
//      ast.FunctionT(
//        ast.FunctionHeaderT(
//          env.fullName,
//          Vector.empty,
//          Vector(ast.ParameterT(interner.intern(CodeVarNameT("self")), None, structType2)),
//          CoordT(ShareT, VoidT()),
//          maybeOriginFunction1),
//        BlockTE(
//            ReturnTE(
//              FunctionCallTE(
//                structDestructor,
//                Vector(ArgLookupTE(0, structType2))))))
//
//    // If this fails, then the signature the FunctionCompilerMiddleLayer made for us doesn't
//    // match what we just made
//    vassert(
//      coutputs.getDeclaredSignatureOrigin(
//        destructor2.header.toSignature).nonEmpty)
//
//    // we cant make the destructor here because they might have a user defined one somewhere
//
//      coutputs
//        .declareFunctionReturnType(destructor2.header.toSignature, destructor2.header.returnType)
//      coutputs.addFunction(destructor2);
//
//    vassert(
//      coutputs.getDeclaredSignatureOrigin(
//        destructor2.header.toSignature).nonEmpty)
//
//    (destructor2.header)
//  }
}
