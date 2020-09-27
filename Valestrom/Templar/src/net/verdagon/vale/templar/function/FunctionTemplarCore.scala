package net.verdagon.vale.templar.function

import net.verdagon.vale.astronomer._
import net.verdagon.vale.templar.types._
import net.verdagon.vale.templar.templata._
import net.verdagon.vale.scout.{Environment => _, FunctionEnvironment => _, IEnvironment => _, _}
import net.verdagon.vale.templar._
import net.verdagon.vale.templar.citizen.{AncestorHelper, StructTemplar}
import net.verdagon.vale.templar.env._
import net.verdagon.vale.templar.templata.TemplataTemplar
import net.verdagon.vale.{IProfiler, vassert, vassertSome, vcurious, vfail, vimpl}

import scala.collection.immutable.{List, Set}

class FunctionTemplarCore(
    opts: TemplarOptions,
  profiler: IProfiler,
  templataTemplar: TemplataTemplar,
    convertHelper: ConvertHelper,
    delegate: IFunctionTemplarDelegate) {
  val bodyTemplar = new BodyTemplar(opts, profiler, templataTemplar, convertHelper, new IBodyTemplarDelegate {
    override def evaluateBlockStatements(temputs: TemputsBox, startingFate: FunctionEnvironment, fate: FunctionEnvironmentBox, exprs: List[IExpressionAE]): (List[ReferenceExpression2], Set[Coord]) = {
      delegate.evaluateBlockStatements(temputs, startingFate, fate, exprs)
    }

    override def nonCheckingTranslateList(temputs: TemputsBox, fate: FunctionEnvironmentBox, patterns1: List[AtomAP], patternInputExprs2: List[ReferenceExpression2]): List[ReferenceExpression2] = {
      delegate.nonCheckingTranslateList(temputs, fate, patterns1, patternInputExprs2)
    }
  })

  // Preconditions:
  // - already spawned local env
  // - either no template args, or they were already added to the env.
  // - either no closured vars, or they were already added to the env.
  def evaluateFunctionForHeader(
    startingFullEnv: FunctionEnvironment,
      temputs: TemputsBox,
    callRange: RangeS,
      params2: List[Parameter2]):
  (FunctionHeader2) = {
    val fullEnv = FunctionEnvironmentBox(startingFullEnv)


    opts.debugOut("Evaluating function " + fullEnv.fullName)

    val isDestructor =
      params2.nonEmpty &&
      params2.head.tyype.ownership == Own &&
      (startingFullEnv.fullName.last match {
        case FunctionName2(humanName, _, _) if humanName == CallTemplar.MUT_DESTRUCTOR_NAME => true
        case _ => false
      })

    startingFullEnv.function.body match {
      case CodeBodyA(body) => {
        val (header, body2) =
          bodyTemplar.declareAndEvaluateFunctionBody(
            fullEnv, temputs, BFunctionA(startingFullEnv.function, body), params2, isDestructor)

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
        vassert(fullEnv.variables.startsWith(startingFullEnv.variables))
        val introducedLocals =
          fullEnv.variables
            .drop(startingFullEnv.variables.size)
            .collect({
              case x @ ReferenceLocalVariable2(_, _, _) => x
              case x @ AddressibleLocalVariable2(_, _, _) => x
            })

        temputs.lookupFunction(header.toSignature) match {
          case None => {
            val function2 = Function2(header, introducedLocals, body2);
            temputs.addFunction(function2)
            (function2.header)
          }
          case Some(function2) => {
            (function2.header)
          }
        }
      }
      case AbstractBodyA => {
        val maybeRetCoord =
          startingFullEnv.function.maybeRetCoordRune match {
            case None => vfail("Need return type for abstract function!")
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
            translateFunctionAttributes(startingFullEnv.function.attributes),
            params2,
            retCoord,
            Some(startingFullEnv.function))
        (header)
      }
      case GeneratedBodyA(generatorId) => {
        val signature2 = Signature2(fullEnv.fullName);
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
            case _ => vfail("Must be a coord!")
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
              vfail("Generator made a function whose signature doesn't match the expected one!\n" +
              "Expected:  " + signature2 + "\n" +
              "Generated: " + header.toSignature)
            }
            (header)
          }
        }
      }
    }
  }

  def makeExternFunction(
      temputs: TemputsBox,
      fullName: FullName2[IFunctionName2],
      attributes: List[IFunctionAttribute2],
      params2: List[Parameter2],
      returnType2: Coord,
      maybeOrigin: Option[FunctionA]):
  (FunctionHeader2) = {
    val header = FunctionHeader2(fullName, Extern2 :: attributes, params2, returnType2, maybeOrigin)

    val (humanName, params) =
      fullName.last match {
        case FunctionName2(humanName, List(), parameters) => (humanName, parameters)
        case _ => vfail("Only human-named function can be extern!")
      }
    val externFullName = FullName2(List(), ExternFunctionName2(humanName, params))
    val externPrototype = Prototype2(externFullName, header.returnType)
    temputs.addExternPrototype(externPrototype)

    val argLookups =
      header.params.zipWithIndex.map({ case (param2, index) => ArgLookup2(index, param2.tyype) })
    val function2 =
      Function2(
        header,
        List(),
        Return2(ExternFunctionCall2(externPrototype, argLookups)))

    temputs.declareFunctionReturnType(header.toSignature, header.returnType)
    temputs.addFunction(function2)
    (header)
  }

  def translateFunctionAttributes(a: List[IFunctionAttributeA]): List[IFunctionAttribute2] = {
    a.map({
      case UserFunctionA => UserFunction2
      case ExternA => Extern2
      case x => vimpl(x.toString)
    })
  }


  def makeInterfaceFunction(
    env: FunctionEnvironment,
    temputs: TemputsBox,
    origin: Option[FunctionA],
    params2: List[Parameter2],
    returnReferenceType2: Coord):
  (FunctionHeader2) = {
    vassert(params2.exists(_.virtuality == Some(Abstract2)))
    val header =
      FunctionHeader2(
        env.fullName,
        List(),
        params2,
        returnReferenceType2,
        origin)
    val function2 =
      Function2(
        header,
        List(),
        Block2(
          List(
            Return2(
              InterfaceFunctionCall2(
                header,
                header.returnType,
                header.params.zipWithIndex.map({ case (param2, index) => ArgLookup2(index, param2.tyype) }))))))

      temputs
        .declareFunctionReturnType(header.toSignature, returnReferenceType2)
      temputs.addFunction(function2)
    vassert(temputs.exactDeclaredSignatureExists(env.fullName))
    header
  }

  def makeImplDestructor(
    env: FunctionEnvironment,
    temputs: TemputsBox,
    maybeOriginFunction1: Option[FunctionA],
    structDef2: StructDefinition2,
    interfaceRef2: InterfaceRef2,
    structDestructor: Prototype2,
  ):
  (FunctionHeader2) = {
    val ownership = if (structDef2.mutability == Mutable) Own else Share
    val structRef2 = structDef2.getRef
    val structType2 = Coord(ownership, structRef2)

    val destructor2 =
      Function2(
        FunctionHeader2(
          env.fullName,
          List(),
          List(Parameter2(CodeVarName2("this"), Some(Override2(interfaceRef2)), structType2)),
          Coord(Share, Void2()),
          maybeOriginFunction1),
        List(),
        Block2(
          List(
            Return2(
              FunctionCall2(
                structDestructor,
                List(ArgLookup2(0, structType2)))))))

    // If this fails, then the signature the FunctionTemplarMiddleLayer made for us doesn't
    // match what we just made
    vassert(temputs.exactDeclaredSignatureExists(destructor2.header.toSignature))

    // we cant make the destructor here because they might have a user defined one somewhere

      temputs
        .declareFunctionReturnType(destructor2.header.toSignature, destructor2.header.returnType)
      temputs.addFunction(destructor2);

    vassert(temputs.exactDeclaredSignatureExists(destructor2.header.toSignature))

    (destructor2.header)
  }
}
