package dev.vale.typing

import dev.vale._
import dev.vale.postparsing.rules.{EqualsSR, IRulexSR, RuneUsage}
import dev.vale.postparsing._
import dev.vale.typing.env._
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.parsing.ast._
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.ast._
import dev.vale.typing.citizen._
import dev.vale.typing.templata.ITemplataT._
import dev.vale.typing.types._
import dev.vale.typing.templata._

import scala.annotation.tailrec
import scala.collection.immutable.{List, Map, Set}

// See SBITAFD, we need to register bounds for these new instantiations. This instructs us where
// to get those new bounds from.
sealed trait IBoundArgumentsSource
case object InheritBoundsFromTypeItself extends IBoundArgumentsSource
case class UseBoundsFromContainer(
  instantiationBoundParams: InstantiationBoundArgumentsT[FunctionBoundNameT, ImplBoundNameT],
  instantiationBoundArguments: InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT]
) extends IBoundArgumentsSource

trait ITemplataCompilerDelegate {

  def isParent(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT):
  IsParentResult

  def resolveStruct(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    structTemplata: StructDefinitionTemplataT,
    uncoercedTemplateArgs: Vector[ITemplataT[ITemplataType]]
  ):
  IResolveOutcome[StructTT]

  def resolveInterface(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT, // See CSSNCE
    callRange: List[RangeS],
    callLocation: LocationInDenizen,
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceDefinitionTemplataT,
    uncoercedTemplateArgs: Vector[ITemplataT[ITemplataType]]
  ):
  IResolveOutcome[InterfaceTT]
}

object TemplataCompiler {
  def getTopLevelDenizenId(
    id: IdT[INameT],
  ): IdT[IInstantiationNameT] = {
    // That said, some things are namespaced inside templates. If we have a `struct Marine` then
    // we'll also have a func drop within its namespace; we'll have a free function instance under
    // a Marine struct template. We want to grab the instance.
    val index =
    id.steps.indexWhere({
      case x : IInstantiationNameT => true
      case _ => false
    })
    vassert(index >= 0)
    val initSteps = id.steps.slice(0, index)
    val lastStep =
      id.steps(index) match {
        case x : IInstantiationNameT => x
        case _ => vwat()
      }
    IdT(id.packageCoord, initSteps, lastStep)
  }

  def getPlaceholderTemplataId(implPlaceholder: ITemplataT[ITemplataType]): IdT[IPlaceholderNameT] = {
    implPlaceholder match {
      case PlaceholderTemplataT(n, _) => n
      case KindTemplataT(KindPlaceholderT(n)) => n
      case CoordTemplataT(CoordT(_, _, KindPlaceholderT(n))) => n
      case other => vwat(other)
    }
  }

  // See SFWPRL
  def assemblePredictRules(genericParameters: Vector[GenericParameterS], numExplicitTemplateArgs: Int): Vector[IRulexSR] = {
    genericParameters.zipWithIndex.flatMap({ case (genericParam, index) =>
      if (index >= numExplicitTemplateArgs) {
        genericParam.default match {
          case Some(x) => {
            x.rules :+
              EqualsSR(genericParam.range, genericParam.rune, RuneUsage(genericParam.range, x.resultRune))
          }
          case None => Vector()
        }
      } else {
        Vector()
      }
    })
  }

  def assembleCallSiteRules(rules: Vector[IRulexSR], genericParameters: Vector[GenericParameterS], numExplicitTemplateArgs: Int): Vector[IRulexSR] = {
    rules.filter(InferCompiler.includeRuleInCallSiteSolve) ++
      (genericParameters.zipWithIndex.flatMap({ case (genericParam, index) =>
        if (index >= numExplicitTemplateArgs) {
          genericParam.default match {
            case Some(x) => x.rules
            case None => Vector()
          }
        } else {
          Vector()
        }
      }))
  }

  def getFunctionTemplate(id: IdT[IFunctionNameT]): IdT[IFunctionTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getCitizenTemplate(id: IdT[ICitizenNameT]): IdT[ICitizenTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getNameTemplate(name: INameT): INameT = {
    name match {
      case x : IInstantiationNameT => x.template
      case _ => name
    }
  }

  def getSuperTemplate(id: IdT[INameT]): IdT[INameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      getNameTemplate(last))
  }

  // Removes lambda citizens / lambda calls from the end, so we get the root function.
  def getRootSuperTemplate(interner: Interner, id: IdT[INameT]): IdT[INameT] = {
    @tailrec
    def removeTrailingLambdas(tentativeId: IdT[INameT]): IdT[INameT] = {
      val containsLambda =
        tentativeId.steps.exists {
          case LambdaCitizenTemplateNameT(_) => true
          case LambdaCallFunctionTemplateNameT(_, _) => true
          case OverrideDispatcherCaseNameT(_) => true
          case _ => false
        }
      if (containsLambda) { // We do this logic because lambdas sometimes have FunctionNameT after them.
        // Recurse
        removeTrailingLambdas(tentativeId.initId(interner))
      } else {
        tentativeId
      }
    }
    removeTrailingLambdas(getSuperTemplate(id))
  }

  def getTemplate(id: IdT[IInstantiationNameT]): IdT[ITemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getSubKindTemplate(id: IdT[ISubKindNameT]): IdT[ISubKindTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getSuperKindTemplate(id: IdT[ISuperKindNameT]): IdT[ISuperKindTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getStructTemplate(id: IdT[IStructNameT]): IdT[IStructTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getInterfaceTemplate(id: IdT[IInterfaceNameT]): IdT[IInterfaceTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getExportTemplate(id: IdT[ExportNameT]): IdT[ExportTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getExternTemplate(id: IdT[ExternNameT]): IdT[ExternTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps, //.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getImplTemplate(id: IdT[IImplNameT]): IdT[IImplTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getPlaceholderTemplate(id: IdT[KindPlaceholderNameT]): IdT[KindPlaceholderTemplateNameT] = {
    val IdT(packageCoord, initSteps, last) = id
    IdT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def assembleRuneToFunctionBound(templatas: TemplatasStore): Map[IRuneS, PrototypeT[FunctionBoundNameT]] = {
    templatas.entriesByNameT.toIterable.flatMap({
      case (RuneNameT(rune), TemplataEnvEntry(PrototypeTemplataT(PrototypeT(IdT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)), returnType)))) => {
        Some(rune -> PrototypeT(IdT(packageCoord, initSteps, name), returnType))
      }
      case _ => None
    }).toMap
  }

  def assembleRuneToImplBound(templatas: TemplatasStore): Map[IRuneS, IdT[ImplBoundNameT]] = {
    templatas.entriesByNameT.toIterable.flatMap({
      case (RuneNameT(rune), TemplataEnvEntry(IsaTemplataT(_, IdT(packageCoord, initSteps, name @ ImplBoundNameT(_, _)), _, _))) => {
        Some(rune -> IdT(packageCoord, initSteps, name))
      }
      case _ => None
    }).toMap
  }

  def substituteTemplatasInCoord(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    coord: CoordT):
  CoordT = {
    val CoordT(ownership, originalRegion, kind) = coord
    val resultRegion = vregionmut(originalRegion)
    substituteTemplatasInKind(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, kind) match {
      case KindTemplataT(kind) => CoordT(ownership, resultRegion, kind)
      case CoordTemplataT(CoordT(innerOwnership, innerRegion, kind)) => {
        val resultOwnership =
          (ownership, innerOwnership) match {
            case (ShareT, _) => ShareT
            case (_, ShareT) => ShareT
            case (OwnT, OwnT) => OwnT
            case (OwnT, BorrowT) => BorrowT
            case (BorrowT, OwnT) => BorrowT
            case (BorrowT, BorrowT) => BorrowT
            case _ => vimpl()
          }
        CoordT(resultOwnership, resultRegion, kind)
      }
    }

  }

  // This returns an ITemplata because...
  // Let's say we have a parameter that's a Coord(own, $_0).
  // $_0 is a PlaceholderT(0), which means it's a standing for whatever the first template arg is.
  // Let's say the first template arg is a CoordTemplata containing &Ship.
  // We're in the weird position of turning a PlaceholderT kind into a &Ship coord!
  // That's why we have to return an ITemplata, because it could be a coord or a kind.
  def substituteTemplatasInKind(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    kind: KindT):
  ITemplataT[ITemplataType] = {
    kind match {
      case IntT(bits) => KindTemplataT(kind)
      case BoolT() => KindTemplataT(kind)
      case StrT() => KindTemplataT(kind)
      case FloatT() => KindTemplataT(kind)
      case VoidT() => KindTemplataT(kind)
      case NeverT(_) => KindTemplataT(kind)
      case RuntimeSizedArrayTT(IdT(
      packageCoord,
      initSteps,
      RuntimeSizedArrayNameT(template, RawArrayNameT(mutability, elementType, region)))) => {
        KindTemplataT(
          interner.intern(RuntimeSizedArrayTT(
            IdT(
              packageCoord,
              initSteps,
              interner.intern(RuntimeSizedArrayNameT(
                template,
                interner.intern(RawArrayNameT(
                  expectMutability(substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, mutability)),
                  substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, elementType),
                  RegionT()))))))))
      }
      case StaticSizedArrayTT(IdT(packageCoord, initSteps, StaticSizedArrayNameT(template, size, variability, RawArrayNameT(mutability, elementType, region)))) => {
        KindTemplataT(
          interner.intern(StaticSizedArrayTT(
            IdT(
              packageCoord,
              initSteps,
              interner.intern(StaticSizedArrayNameT(
                template,
                expectInteger(substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, size)),
                expectVariability(substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, variability)),
                interner.intern(RawArrayNameT(
                  expectMutability(substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, mutability)),
                  substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, elementType),
                  RegionT()))))))))
      }
      case p @ KindPlaceholderT(id @ IdT(_, _, KindPlaceholderNameT(KindPlaceholderTemplateNameT(index, rune)))) => {
        if (id.initId(interner) == needleTemplateName) {
          newSubstitutingTemplatas(index)
        } else {
          KindTemplataT(kind)
        }
      }
      case s @ StructTT(_) => KindTemplataT(substituteTemplatasInStruct(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, s))
      case s @ InterfaceTT(_) => KindTemplataT(substituteTemplatasInInterface(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, s))
    }
  }

  def substituteTemplatasInStruct(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],
    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    structTT: StructTT):
  StructTT = {
    val StructTT(IdT(packageCoord, initSteps, last)) = structTT
    val newStruct =
      interner.intern(
        StructTT(
          IdT(
            packageCoord,
            initSteps,
            last match {
              case AnonymousSubstructNameT(template, templateArgs) => {
                interner.intern(AnonymousSubstructNameT(
                  template,
                  templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))))
              }
              case StructNameT(template, templateArgs) => {
                interner.intern(StructNameT(
                  template,
                  templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))))
              }
              case LambdaCitizenNameT(template) => {
                interner.intern(LambdaCitizenNameT(template))
              }
            })))
    // See SBITAFD, we need to register bounds for these new instantiations.
    val instantiationBoundArgs =
      vassertSome(coutputs.getInstantiationBounds(structTT.id))
    coutputs.addInstantiationBounds(
      sanityCheck,
      interner,
      originalCallingDenizenId,
      newStruct.id,
      translateInstantiationBounds(
        coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, instantiationBoundArgs))
    newStruct
  }

  private def translateInstantiationBounds(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    instantiationBoundArgs: InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT]):
  InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT] = {
    boundArgumentsSource match {
      case InheritBoundsFromTypeItself => {
        val x =
          substituteTemplatasInBounds(
            coutputs,
            sanityCheck,
            interner,
            keywords,
            originalCallingDenizenId,
            needleTemplateName,
            newSubstitutingTemplatas,
            boundArgumentsSource,
            instantiationBoundArgs)
        // If we're inside:
        //   MyList.drop<MyList.drop$0>(MyList<MyList.drop$0>)void
        // we might be reaching into that MyList struct, which contains a:
        //   Opt<MyList<MyList$0>>
        // and we want to turn it into a:
        //   Opt<MyList<MyList.drop$0>>
        // We'll need to create some bound args for that MyList<MyList.drop$0>.
        // First, we take the original bound args for
        //   MyList<MyList.drop$0>
        // which was
        //   _2114 -> MyList.bound:drop<>(^MyList$0)void
        // and we can just substitute it to:
        //   _2114 -> MyList.bound:drop<>(^MyList.bound:drop$0)void
        // This is the bound that MyList.drop will look for.
        x
      }
      case UseBoundsFromContainer(containerInstantiationBoundParams, containerInstantiationBoundArgs) => {
        // Here, we're grabbing something inside a struct, like with the dot operator. We'll want to
        // make some instantiation bound args for this new type that our function knows about.
        // Luckily, we can use some bounds from the containing struct to satisfy its members bounds.

        val containerFuncBoundToBoundArg =
          containerInstantiationBoundArgs.runeToBoundPrototype.map({ case (rune, containerFuncBoundArg) =>
            vassertSome(containerInstantiationBoundParams.runeToBoundPrototype.get(rune)) -> containerFuncBoundArg
          })
        val containerImplBoundToBoundArg =
          containerInstantiationBoundArgs.runeToBoundImpl.map({ case (rune, containerImplBoundArg) =>
            vassertSome(containerInstantiationBoundParams.runeToBoundImpl.get(rune)) -> containerImplBoundArg
          })
        InstantiationBoundArgumentsT.make(
          instantiationBoundArgs.runeToBoundPrototype.mapValues(funcBoundArg => {
            funcBoundArg match {
              case PrototypeT(IdT(packageCoord, initSteps, fbn@FunctionBoundNameT(_, _, _)), returnType) => {
                vassertSome(
                  containerFuncBoundToBoundArg.get(PrototypeT(IdT(packageCoord, initSteps, fbn), returnType)))
              }
              case _ => {
                // Not sure if this call is really necessary...
                substituteTemplatasInPrototype(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, funcBoundArg)
              }
            }
          }),
          instantiationBoundArgs.runeToCitizenRuneToReachablePrototype.map({ case (calleeRune, InstantiationReachableBoundArgumentsT(citizenRuneToReachablePrototype)) =>
            calleeRune ->
                InstantiationReachableBoundArgumentsT(
                  citizenRuneToReachablePrototype.map({ case (citizenRune, reachablePrototype) =>
                    citizenRune ->
                        (reachablePrototype match {
                          case PrototypeT(IdT(packageCoord, initSteps, fbn@FunctionBoundNameT(_, _, _)), returnType) => {
                            vassertSome(containerFuncBoundToBoundArg.get(PrototypeT(IdT(packageCoord, initSteps, fbn), returnType)))
                          }
                          case _ => {
                            // Not sure if this call is really necessary...
                            substituteTemplatasInPrototype(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, reachablePrototype)
                          }
                        })
                  }))
          }),
          instantiationBoundArgs.runeToBoundImpl.mapValues(implBoundArg => {
            implBoundArg match {
              case IdT(packageCoord, initSteps, ibn@ImplBoundNameT(_, _)) => {
                vassertSome(containerImplBoundToBoundArg.get(IdT(packageCoord, initSteps, ibn)))
              }
              case _ => {
                // Not sure if this call is really necessary...
                substituteTemplatasInImplId(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, implBoundArg)
              }
            }
          }))
      }
    }
  }

  def substituteTemplatasInImplId[T <: IImplNameT](
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    implId: IdT[T]):
  IdT[T] = {
    val IdT(packageCoord, initSteps, last) = implId
    val substitutedImplId =
      IdT(
        packageCoord,
        initSteps,
        last match {
          case in @ ImplNameT(template, templateArgs, subCitizen) => {
            interner.intern(ImplNameT(
              template,
              templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata)),
              expectKindTemplata(substituteTemplatasInKind(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, subCitizen)).kind.expectCitizen()))
          }
          case other => vimpl(other)
        })

    val instantiationBoundArgs = vassertSome(coutputs.getInstantiationBounds(implId))
    // See SBITAFD, we need to register bounds for these new instantiations.
    coutputs.addInstantiationBounds(
      sanityCheck,
      interner,
      originalCallingDenizenId,
      substitutedImplId,
      translateInstantiationBounds(
        coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, instantiationBoundArgs))

    vassert(substitutedImplId.localName.getClass.equals(implId.localName.getClass))
    vassert(substitutedImplId.getClass.equals(implId.getClass))
    val result = substitutedImplId.asInstanceOf[IdT[T]]
    assert(result != null)
    return result
  }

  def substituteTemplatasInBounds(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    boundArgs: InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT]):
  InstantiationBoundArgumentsT[IFunctionNameT, IImplNameT] = {
    val InstantiationBoundArgumentsT(runeToFunctionBoundArg, callerKindRuneToReachableBoundArguments, runeToImplBoundArg) = boundArgs
    InstantiationBoundArgumentsT.make(
      runeToFunctionBoundArg.mapValues({ case funcBoundArg =>
        substituteTemplatasInPrototype(
          coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, funcBoundArg)
      }),
      callerKindRuneToReachableBoundArguments.map({ case (callerRune, InstantiationReachableBoundArgumentsT(citizenRuneToReachablePrototype)) =>
        callerRune ->
            InstantiationReachableBoundArgumentsT(
              citizenRuneToReachablePrototype.map({ case (citizenRune, reachablePrototype) =>
                citizenRune ->
                  substituteTemplatasInPrototype(
                    coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, reachablePrototype)
              }))
      }),
      runeToImplBoundArg.mapValues(implBoundArg => {
        substituteTemplatasInImplId(
          coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, implBoundArg)
      }))
  }

  def substituteTemplatasInInterface(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    interfaceTT: InterfaceTT):
  InterfaceTT = {
    val InterfaceTT(IdT(packageCoord, initSteps, last)) = interfaceTT
    val newInterface =
      interner.intern(
        InterfaceTT(
          IdT(
            packageCoord,
            initSteps,
            last match {
              case InterfaceNameT(template, templateArgs) => {
                interner.intern(InterfaceNameT(
                  template,
                  templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))))
              }
            })))
    // See SBITAFD, we need to register bounds for these new instantiations.
    val instantiationBoundArgs =
      vassertSome(coutputs.getInstantiationBounds(interfaceTT.id))
    coutputs.addInstantiationBounds(
      sanityCheck,
      interner,
      originalCallingDenizenId,
      newInterface.id,
      translateInstantiationBounds(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, instantiationBoundArgs))
    newInterface
  }

  def substituteTemplatasInTemplata(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    templata: ITemplataT[ITemplataType]):
  ITemplataT[ITemplataType] = {
    templata match {
      case CoordTemplataT(c) => CoordTemplataT(substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, c))
      case KindTemplataT(k) => substituteTemplatasInKind(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, k)
      case PlaceholderTemplataT(id @ IdT(_, _, pn), _) => {
        if (id.initId(interner) == needleTemplateName) {
          newSubstitutingTemplatas(pn.index)
        } else {
          templata
        }
      }
      case MutabilityTemplataT(_) => templata
      case VariabilityTemplataT(_) => templata
      case IntegerTemplataT(_) => templata
      case BooleanTemplataT(_) => templata
      case PrototypeTemplataT(prototype) => {
        PrototypeTemplataT(
          substituteTemplatasInPrototype(
            coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, prototype))
      }
      case other => vimpl(other)
    }
  }

  def substituteTemplatasInPrototype[T <: IFunctionNameT](
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],
    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    originalPrototype: PrototypeT[T]):
  PrototypeT[T] = {
    val PrototypeT(IdT(packageCoord, initSteps, funcName), returnType) = originalPrototype
    val substitutedTemplateArgs = funcName.templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))
    val substitutedParams = funcName.parameters.map((coord: CoordT) => substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, coord))
    val substitutedReturnType = substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, returnType)
    val substitutedFuncName = funcName.template.makeFunctionName(interner, keywords, substitutedTemplateArgs, substitutedParams)
    val tentativeId = IdT(packageCoord, initSteps, substitutedFuncName)

    val perhapsImportedId =
      tentativeId.localName match {
        case n @ FunctionBoundNameT(_, _, _) => {
          // Always import a seen function bound into our own environment, see MFBFDP.
          val importedId = originalCallingDenizenId.addStep(n)
          // It's a function bound, it has no function bounds of its own.
          coutputs.addInstantiationBounds(
            sanityCheck,
            interner,
            originalCallingDenizenId,
            importedId,
            InstantiationBoundArgumentsT.make[IFunctionNameT, IImplNameT](Map(), Map(), Map()))
          importedId
      }
      case _ => {
        // Not really sure if we're supposed to add bounds or something here.
          vassert(coutputs.getInstantiationBounds(tentativeId).nonEmpty)
          tentativeId
      }
    }
    val prototype = PrototypeT(perhapsImportedId, substitutedReturnType)

    vassert(substitutedFuncName.getClass.equals(funcName.getClass))
    vassert(originalPrototype.getClass.equals(prototype.getClass))
    val result = prototype.asInstanceOf[PrototypeT[T]]
    assert(result != null)
    return result
  }

  def substituteTemplatasInFunctionBoundId(
    coutputs: CompilerOutputs,
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    needleTemplateName: IdT[ITemplateNameT],
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource,
    original: IdT[FunctionBoundNameT]):
  IdT[FunctionBoundNameT] = {
    val IdT(packageCoord, initSteps, funcName) = original
    val substitutedTemplateArgs =
      funcName.templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))
    val substitutedParams = funcName.parameters.map((coord: CoordT) => substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, coord))
//    val substitutedReturnType = substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, returnType, substitutions)
    val substitutedFuncName = funcName.template.makeFunctionName(interner, keywords, substitutedTemplateArgs, substitutedParams)
    val newId = IdT(packageCoord, initSteps, substitutedFuncName)

    // It's a function bound, it has no function bounds of its own.
    coutputs.addInstantiationBounds(
      sanityCheck,
      interner,
      originalCallingDenizenId,
      newId,
      InstantiationBoundArgumentsT.make(Map(), Map(), Map()))

    newId
  }

  // def substituteTemplatasInFunctionReachableId(
  //     coutputs: CompilerOutputs,
  //     sanityCheck: Boolean, interner: Interner,
  //     keywords: Keywords,
  //     needleTemplateName: IdT[ITemplateNameT],
  //     newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
  //     boundArgumentsSource: IBoundArgumentsSource,
  //     original: IdT[ReachableFunctionNameT]):
  // IdT[ReachableFunctionNameT] = {
  //   val IdT(packageCoord, initSteps, funcName) = original
  //   val substitutedTemplateArgs =
  //     funcName.templateArgs.map((templata: ITemplataT[ITemplataType]) => substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata))
  //   val substitutedParams = funcName.parameters.map((coord: CoordT) => substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, coord))
  //   //    val substitutedReturnType = substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, returnType, substitutions)
  //   val substitutedFuncName = funcName.template.makeFunctionName(interner, keywords, substitutedTemplateArgs, substitutedParams)
  //   val newId = IdT(packageCoord, initSteps, substitutedFuncName)
  //
  //   // It's a function bound, it has no function bounds of its own.
  //   coutputs.addInstantiationBounds(
  //     interner,
  //     originalCallingDenizenId,
  //     newId,
  //     InstantiationBoundArgumentsT(Map(), Map(), Map()))
  //
  //   newId
  // }

  trait IPlaceholderSubstituter {
    def substituteForCoord(coutputs: CompilerOutputs, coordT: CoordT): CoordT
    def substituteForInterface(coutputs: CompilerOutputs, interfaceTT: InterfaceTT): InterfaceTT
    def substituteForTemplata(coutputs: CompilerOutputs, coordT: ITemplataT[ITemplataType]): ITemplataT[ITemplataType]
    def substituteForPrototype[T <: IFunctionNameT](coutputs: CompilerOutputs, proto: PrototypeT[T]): PrototypeT[T]
    def substituteForImplId[T <: IImplNameT](coutputs: CompilerOutputs, implId: IdT[T]): IdT[T]
  }
  def getPlaceholderSubstituter(
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],

    // This is the Ship<WarpFuel>.
    name: IdT[IInstantiationNameT],
    boundArgumentsSource: IBoundArgumentsSource):
    // The Engine<T> is given later to the IPlaceholderSubstituter
  IPlaceholderSubstituter = {
    val topLevelDenizenId = getTopLevelDenizenId(name)
    val templateArgs = topLevelDenizenId.localName.templateArgs
    val topLevelDenizenTemplateId = getTemplate(topLevelDenizenId)

    TemplataCompiler.getPlaceholderSubstituter(
      sanityCheck,
      interner,
      keywords,
      originalCallingDenizenId,
      topLevelDenizenTemplateId,
      templateArgs,
      boundArgumentsSource)
  }

  // Let's say you have the line:
  //   myShip.engine
  // You need to somehow combine these two bits of knowledge:
  // - You have a Ship<WarpFuel>
  // - Ship<T> contains an Engine<T>.
  // To get back an Engine<WarpFuel>. This is the function that does that.
  def getPlaceholderSubstituter(
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],
    // This is the Ship.
    needleTemplateName: IdT[ITemplateNameT],
    // This is the <WarpFuel>.
    newSubstitutingTemplatas: Vector[ITemplataT[ITemplataType]],
    // The Engine<T> is given later to the IPlaceholderSubstituter
    boundArgumentsSource: IBoundArgumentsSource):
  IPlaceholderSubstituter = {
    new IPlaceholderSubstituter {
      override def substituteForCoord(coutputs: CompilerOutputs, coordT: CoordT): CoordT = {
        TemplataCompiler.substituteTemplatasInCoord(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, coordT)
      }
      override def substituteForInterface(coutputs: CompilerOutputs, interfaceTT: InterfaceTT): InterfaceTT = {
        TemplataCompiler.substituteTemplatasInInterface(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, interfaceTT)
      }
      override def substituteForTemplata(coutputs: CompilerOutputs, templata: ITemplataT[ITemplataType]): ITemplataT[ITemplataType] = {
        TemplataCompiler.substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, templata)
      }
      override def substituteForPrototype[T <: IFunctionNameT](coutputs: CompilerOutputs, proto: PrototypeT[T]): PrototypeT[T] = {
        TemplataCompiler.substituteTemplatasInPrototype(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, proto)
      }
      override def substituteForImplId[T <: IImplNameT](coutputs: CompilerOutputs, implId: IdT[T]): IdT[T] = {
        TemplataCompiler.substituteTemplatasInImplId(coutputs, sanityCheck, interner, keywords, originalCallingDenizenId, needleTemplateName, newSubstitutingTemplatas, boundArgumentsSource, implId)
      }
    }
  }

//  // If you have a type (citizenTT) and it contains something (like a member) then
//  // you can use this function to figure out what the member looks like to you, the outsider.
//  // It will take out all the internal placeholders internal to the citizen, and replace them
//  // with what was given in citizenTT's template args.
//  def getTemplataTransformer(sanityCheck: Boolean, interner: Interner, coutputs: CompilerOutputs, citizenTT: ICitizenTT):
//  (ITemplata[ITemplataType]) => ITemplata[ITemplataType] = {
//    val citizenTemplateFullName = TemplataCompiler.getCitizenTemplate(citizenTT.fullName)
//    val citizenTemplateDefinition = coutputs.lookupCitizen(citizenTemplateFullName)
//    vassert(
//      citizenTT.fullName.last.templateArgs.size ==
//        citizenTemplateDefinition.placeholderedCitizen.fullName.last.templateArgs.size)
//    val substitutions =
//      citizenTT.fullName.last.templateArgs
//        .zip(citizenTemplateDefinition.placeholderedCitizen.fullName.last.templateArgs)
//        .flatMap({
//          case (arg, p @ PlaceholderTemplata(_, _)) => Some((p, arg))
//          case _ => None
//        }).toVector
//    (templataToTransform: ITemplata[ITemplataType]) => {
//      TemplataCompiler.substituteTemplatasInTemplata(coutputs, sanityCheck, interner, keywords, templataToTransform, substitutions)
//    }
//  }


  def getReachableBounds(
    sanityCheck: Boolean, interner: Interner,
    keywords: Keywords,
    originalCallingDenizenId: IdT[ITemplateNameT],
    coutputs: CompilerOutputs,
    citizen: ICitizenTT):
  InstantiationReachableBoundArgumentsT[FunctionBoundNameT] = {
    val substituter =
      TemplataCompiler.getPlaceholderSubstituter(
        sanityCheck, interner, keywords,
        originalCallingDenizenId,
        citizen.id,
        // This function is all about gathering bounds from the incoming parameter types.
        InheritBoundsFromTypeItself)
    val citizenTemplateId = TemplataCompiler.getCitizenTemplate(citizen.id)
    val innerEnv = coutputs.getInnerEnvForType(citizenTemplateId)
    InstantiationReachableBoundArgumentsT(
      innerEnv
          .templatas
          .entriesByNameT
          .collect({
            case (RuneNameT(rune), TemplataEnvEntry(PrototypeTemplataT(PrototypeT(IdT(packageCoord, initSteps, FunctionBoundNameT(FunctionBoundTemplateNameT(humanName), templateArgs, params)), returnType)))) => {
              val prototype = PrototypeT(IdT(packageCoord, initSteps, interner.intern(FunctionBoundNameT(interner.intern(FunctionBoundTemplateNameT(humanName)), templateArgs, params))), returnType)
              rune -> substituter.substituteForPrototype(coutputs, prototype)
            }
          })
          .toMap)
  }

  def getFirstUnsolvedIdentifyingRune(
    genericParameters: Vector[GenericParameterS],
    isSolved: IRuneS => Boolean):
  Option[(GenericParameterS, Int)] = {
    genericParameters
      .zipWithIndex
      .map({ case (genericParam, index) =>
        (genericParam, index, isSolved(genericParam.rune.rune))
      })
      .filter(!_._3)
      .map({ case (genericParam, index, false) => (genericParam, index) })
      .headOption
  }

  def createRuneTypeSolverEnv(parentEnv: IInDenizenEnvironmentT): IRuneTypeSolverEnv = {
    new IRuneTypeSolverEnv {
      override def lookup(
          range: RangeS,
          nameS: IImpreciseNameS
      ): Result[IRuneTypeSolverLookupResult, IRuneTypingLookupFailedError] = {
        nameS match {
          case LambdaStructImpreciseNameS(_) => {
            vregionmut() // Take out with regions
            Ok(TemplataLookupResult(KindTemplataType()))
            // Put back in with regions
            // Ok(TemplataLookupResult(TemplateTemplataType(Vector(RegionTemplataType()), KindTemplataType())))
          }
          case _ => {
            parentEnv.lookupNearestWithImpreciseName(nameS, Set(TemplataLookupContext)) match {
              case Some(CitizenDefinitionTemplataT(environment, a)) => {
                Ok(CitizenRuneTypeSolverLookupResult(a.tyype, a.genericParameters))
              }
              case Some(x) => Ok(TemplataLookupResult(x.tyype))
              case None => Err(RuneTypingCouldntFindType(range, nameS))
            }
          }
        }
      }
    }
  }
}

class TemplataCompiler(
  interner: Interner,
  opts: TypingPassOptions,

  nameTranslator: NameTranslator,
  delegate: ITemplataCompilerDelegate) {

  def isTypeConvertible(
    coutputs: CompilerOutputs,
    callingEnv: IInDenizenEnvironmentT,
    parentRanges: List[RangeS],
    callLocation: LocationInDenizen,
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  Boolean = {

    val CoordT(targetOwnership, targetRegion, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourceRegion, sourceType) = sourcePointerType;

    // Note the Never case will short-circuit a true, regardless of the other checks (ownership)

    (sourceType, targetType) match {
      case (NeverT(_), _) => return true
      case (a, b) if a == b =>
      case (VoidT() |
            IntT(_) |
            BoolT() |
            StrT() |
            FloatT() |
            contentsRuntimeSizedArrayTT(_, _, _) |
            contentsStaticSizedArrayTT(_, _, _, _, _), _) => {
        return false
      }
      case (_, VoidT() |
               IntT(_) |
               BoolT() |
               StrT() |
               FloatT() |
               contentsRuntimeSizedArrayTT(_, _, _) |
               contentsStaticSizedArrayTT(_, _, _, _, _)) => {
        return false
      }
      case (_, StructTT(_)) => return false
      case (a : ISubKindTT, b : ISuperKindTT) => {
        delegate.isParent(coutputs, callingEnv, parentRanges, callLocation, a, b) match {
          case IsParent(_, _, _) =>
          case IsntParent(_) => return false
        }
      }
      case _ => {
        vfail("Dont know if we can convert from " + sourceType + " to " + targetType)
      }
    }

    if (sourceRegion != targetRegion) {
      return false
    }

    (sourceOwnership, targetOwnership) match {
      case (a, b) if a == b =>
      // At some point maybe we should automatically convert borrow to pointer and vice versa
      // and perhaps automatically promote borrow or pointer to weak?
      case (OwnT, BorrowT) => return false
      case (OwnT, WeakT) => return false
      case (OwnT, ShareT) => return false
      case (BorrowT, OwnT) => return false
      case (BorrowT, WeakT) => return false
      case (BorrowT, ShareT) => return false
      case (WeakT, OwnT) => return false
      case (WeakT, BorrowT) => return false
      case (WeakT, ShareT) => return false
      case (ShareT, BorrowT) => return false
      case (ShareT, WeakT) => return false
      case (ShareT, OwnT) => return false
    }

    true
  }

  def pointifyKind(
    coutputs: CompilerOutputs,
    kind: KindT,
    region: RegionT,
    ownershipIfMutable: OwnershipT
  ): CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
    val ownership =
      mutability match {
        case PlaceholderTemplataT(idT, tyype) => vimpl()
        case MutabilityTemplataT(MutableT) => ownershipIfMutable
        case MutabilityTemplataT(ImmutableT) => ShareT
      }
    kind match {
      case a@contentsRuntimeSizedArrayTT(_, _, _) => {
        CoordT(ownership, region, a)
      }
      case a@contentsStaticSizedArrayTT(_, _, _, _, _) => {
        CoordT(ownership, region, a)
      }
      case s @ StructTT(_) => {
        CoordT(ownership, region, s)
      }
      case i @ InterfaceTT(_) => {
        CoordT(ownership, region, i)
      }
      case VoidT() => {
        CoordT(ShareT, region, VoidT())
      }
      case i @ IntT(_) => {
        CoordT(ShareT, region, i)
      }
      case FloatT() => {
        CoordT(ShareT, region, FloatT())
      }
      case BoolT() => {
        CoordT(ShareT, region, BoolT())
      }
      case StrT() => {
        CoordT(ShareT, region, StrT())
      }
    }
  }

//  def evaluateStructTemplata(
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    template: StructTemplata,
//    templateArgs: Vector[ITemplata[ITemplataType]],
//    expectedType: ITemplataType):
//  (ITemplata[ITemplataType]) = {
//    val uncoercedTemplata =
//      delegate.resolveStruct(coutputs, callRange, template, templateArgs)
//    val templata =
//      coerce(coutputs, callRange, KindTemplata(uncoercedTemplata), expectedType)
//    (templata)
//  }

//  def evaluateBuiltinTemplateTemplata(
//    env: IEnvironment,
//    coutputs: CompilerOutputs,
//    range: List[RangeS],
//    template: RuntimeSizedArrayTemplateTemplata,
//    templateArgs: Vector[ITemplata[ITemplataType]],
//    expectedType: ITemplataType):
//  (ITemplata[ITemplataType]) = {
//    val Vector(m, CoordTemplata(elementType)) = templateArgs
//    val mutability = ITemplata.expectMutability(m)
//    val arrayKindTemplata = delegate.getRuntimeSizedArrayKind(env, coutputs, elementType, mutability)
//    val templata =
//      coerce(coutputs, callingEnv, range, KindTemplata(arrayKindTemplata), expectedType)
//    (templata)
//  }

//  def getStaticSizedArrayKind(
//    env: IEnvironment,
//    coutputs: CompilerOutputs,
//    callRange: List[RangeS],
//    mutability: ITemplata[MutabilityTemplataType],
//    variability: ITemplata[VariabilityTemplataType],
//    size: ITemplata[IntegerTemplataType],
//    element: CoordT,
//    expectedType: ITemplataType):
//  (ITemplata[ITemplataType]) = {
//    val uncoercedTemplata =
//      delegate.getStaticSizedArrayKind(env, coutputs, mutability, variability, size, element)
//    val templata =
//      coerce(coutputs, callingEnv, callRange, KindTemplata(uncoercedTemplata), expectedType)
//    (templata)
//  }

  def lookupTemplata(
    env: IEnvironmentT,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    name: INameT):
  (ITemplataT[ITemplataType]) = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    vassertOne(env.lookupNearestWithName(name, Set(TemplataLookupContext)))
  }

  def lookupTemplata(
    env: IEnvironmentT,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    name: IImpreciseNameS):
  Option[ITemplataT[ITemplataType]] = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    val results = env.lookupNearestWithImpreciseName(name, Set(TemplataLookupContext))
    if (results.size > 1) {
      vfail()
    }
    results.headOption
  }

  def coerceKindToCoord(coutputs: CompilerOutputs, kind: KindT, region: RegionT):
  CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
    CoordT(
      mutability match {
        case MutabilityTemplataT(MutableT) => OwnT
        case MutabilityTemplataT(ImmutableT) => ShareT
        case PlaceholderTemplataT(idT, tyype) => OwnT
      },
      region,
      kind)
  }

  def coerceToCoord(
    coutputs: CompilerOutputs,
    env: IInDenizenEnvironmentT,
    range: List[RangeS],
    templata: ITemplataT[ITemplataType],
    region: RegionT):
  (ITemplataT[ITemplataType]) = {
    if (templata.tyype == CoordTemplataType()) {
      vcurious()
      templata
    } else {
      templata match {
        case KindTemplataT(kind) => {
          CoordTemplataT(coerceKindToCoord(coutputs, kind, region))
        }
        case st@StructDefinitionTemplataT(declaringEnv, structA) => {
          vcurious()
//          if (structA.isTemplate) {
//            vfail("Can't coerce " + structA.name + " to be a coord, is a template!")
//          }
//          val kind =
//            delegate.resolveStruct(coutputs, env, range, st, Vector.empty).expect().kind
//          val mutability = Compiler.getMutability(coutputs, kind)
//
//          // Default ownership is own for mutables, share for imms
//          val ownership =
//            mutability match {
//              case MutabilityTemplata(MutableT) => OwnT
//              case MutabilityTemplata(ImmutableT) => ShareT
//              case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
//            }
//          val coerced = CoordTemplata(CoordT(ownership, kind))
//          (coerced)
        }
        case it@InterfaceDefinitionTemplataT(declaringEnv, interfaceA) => {
          vcurious()
//          if (interfaceA.isTemplate) {
//            vfail("Can't coerce " + interfaceA.name + " to be a coord, is a template!")
//          }
//          val kind =
//            delegate.resolveInterface(coutputs, env, range, it, Vector.empty).expect().kind
//          val mutability = Compiler.getMutability(coutputs, kind)
//          val coerced =
//            CoordTemplata(
//              CoordT(
//                mutability match {
//                  case MutabilityTemplata(MutableT) => OwnT
//                  case MutabilityTemplata(ImmutableT) => ShareT
//                  case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
//                },
//                kind))
//          (coerced)
        }
        case _ => {
          vfail("Can't coerce a " + templata.tyype + " to be a coord!")
        }
      }
    }
  }

  def resolveStructTemplate(structTemplata: StructDefinitionTemplataT): IdT[IStructTemplateNameT] = {
    val StructDefinitionTemplataT(declaringEnv, structA) = structTemplata
    declaringEnv.id.addStep(nameTranslator.translateStructName(structA.name))
  }

  def resolveInterfaceTemplate(interfaceTemplata: InterfaceDefinitionTemplataT): IdT[IInterfaceTemplateNameT] = {
    val InterfaceDefinitionTemplataT(declaringEnv, interfaceA) = interfaceTemplata
    declaringEnv.id.addStep(nameTranslator.translateInterfaceName(interfaceA.name))
  }

  def resolveCitizenTemplate(citizenTemplata: CitizenDefinitionTemplataT): IdT[ICitizenTemplateNameT] = {
    citizenTemplata match {
      case st @ StructDefinitionTemplataT(_, _) => resolveStructTemplate(st)
      case it @ InterfaceDefinitionTemplataT(_, _) => resolveInterfaceTemplate(it)
    }
  }

  def citizenIsFromTemplate(actualCitizenRef: ICitizenTT, expectedCitizenTemplata: ITemplataT[ITemplataType]): Boolean = {
    val citizenTemplateId =
      expectedCitizenTemplata match {
        case st @ StructDefinitionTemplataT(_, _) => resolveStructTemplate(st)
        case it @ InterfaceDefinitionTemplataT(_, _) => resolveInterfaceTemplate(it)
        case KindTemplataT(c : ICitizenTT) => TemplataCompiler.getCitizenTemplate(c.id)
        case CoordTemplataT(CoordT(OwnT | ShareT, _, c : ICitizenTT)) => TemplataCompiler.getCitizenTemplate(c.id)
        case _ => return false
      }
    TemplataCompiler.getCitizenTemplate(actualCitizenRef.id) == citizenTemplateId
  }

  def createPlaceholder(
      coutputs: CompilerOutputs,
      env: IInDenizenEnvironmentT,
      namePrefix: IdT[INameT],
      genericParam: GenericParameterS,
      index: Int,
      runeToType: Map[IRuneS, ITemplataType],
      currentHeight: Option[Int],
      registerWithCompilerOutputs: Boolean
  ):
  ITemplataT[ITemplataType] = {
    val runeType = vassertSome(runeToType.get(genericParam.rune.rune))
    val rune = genericParam.rune.rune

    runeType match {
      case KindTemplataType() => {
        val (kindMutable, regionMutable) =
          genericParam.tyype match {
            case CoordGenericParameterTypeS(coordRegion, kindMutable, regionMutable) => {
              (if (kindMutable) OwnT else ShareT, regionMutable)
            }
            case _ => (OwnT, false)
          }
        createKindPlaceholderInner(
          coutputs, env, namePrefix, index, rune, kindMutable, registerWithCompilerOutputs)
      }
      case CoordTemplataType() => {
        val (kindMutable, regionMutability) =
          genericParam.tyype match {
            case CoordGenericParameterTypeS(coordRegion, kindMutable, regionMutable) => {
              (if (kindMutable) OwnT else ShareT, if (regionMutable) ReadWriteRegionS else ReadOnlyRegionS)
            }
            case _ => (OwnT, ReadOnlyRegionS)
          }
        createCoordPlaceholderInner(
          coutputs,
          env,
          namePrefix,
          index,
          rune,
          currentHeight,
          regionMutability,
          kindMutable,
          registerWithCompilerOutputs)
      }
      case otherType => {
        createNonKindNonRegionPlaceholderInner(namePrefix, index, rune, otherType)
      }
    }
  }

  def createCoordPlaceholderInner(
      coutputs: CompilerOutputs,
      env: IInDenizenEnvironmentT,
      namePrefix: IdT[INameT],
      index: Int,
      rune: IRuneS,
      currentHeight: Option[Int],
      regionMutability: IRegionMutabilityS,
      kindOwnership: OwnershipT,
      registerWithCompilerOutputs: Boolean
  ): CoordTemplataT = {
    val regionPlaceholderTemplata = RegionT()

    val kindPlaceholderT =
      createKindPlaceholderInner(
        coutputs, env, namePrefix, index, rune, kindOwnership, registerWithCompilerOutputs)

    CoordTemplataT(CoordT(kindOwnership, regionPlaceholderTemplata, kindPlaceholderT.kind))
  }

  def createKindPlaceholderInner(
      coutputs: CompilerOutputs,
      env: IInDenizenEnvironmentT,
      namePrefix: IdT[INameT],
      index: Int,
      rune: IRuneS,
      kindOwnership: OwnershipT,
      registerWithCompilerOutputs: Boolean):
  KindTemplataT = {
    val kindPlaceholderId =
      namePrefix.addStep(
        interner.intern(KindPlaceholderNameT(
          interner.intern(KindPlaceholderTemplateNameT(index, rune)))))
    val kindPlaceholderTemplateId =
      TemplataCompiler.getPlaceholderTemplate(kindPlaceholderId)

    if (registerWithCompilerOutputs) {
      coutputs.declareType(kindPlaceholderTemplateId)

      val mutability =
        MutabilityTemplataT(
          kindOwnership match {
            case OwnT => MutableT
            case ShareT => ImmutableT
          })
      coutputs.declareTypeMutability(kindPlaceholderTemplateId, mutability)

      val placeholderEnv = GeneralEnvironmentT.childOf(interner, env, kindPlaceholderTemplateId, kindPlaceholderTemplateId)
      coutputs.declareTypeOuterEnv(kindPlaceholderTemplateId, placeholderEnv)
      coutputs.declareTypeInnerEnv(kindPlaceholderTemplateId, placeholderEnv)
    }

    KindTemplataT(KindPlaceholderT(kindPlaceholderId))
  }

  def createNonKindNonRegionPlaceholderInner[T <: ITemplataType](
      namePrefix: IdT[INameT],
      index: Int,
      rune: IRuneS,
      tyype: T):
  PlaceholderTemplataT[T] = {
    val idT = namePrefix.addStep(interner.intern(NonKindNonRegionPlaceholderNameT(index, rune)))
    PlaceholderTemplataT(idT, tyype)
  }
}
