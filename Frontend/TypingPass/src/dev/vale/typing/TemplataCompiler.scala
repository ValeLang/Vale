package dev.vale.typing

import dev.vale.{CodeLocationS, Interner, Keywords, RangeS, vassert, vassertOne, vassertSome, vcurious, vfail, vimpl, vwat}
import dev.vale.postparsing.rules.{EqualsSR, IRulexSR, RuneUsage}
import dev.vale.postparsing._
import dev.vale.typing.env.{FunctionEnvironment, GeneralEnvironment, IEnvironment, TemplataEnvEntry, TemplataLookupContext, TemplatasStore}
import dev.vale.typing.names.{AnonymousSubstructNameT, CitizenNameT, ExportNameT, ExportTemplateNameT, FullNameT, FunctionBoundNameT, FunctionNameT, FunctionTemplateNameT, ICitizenNameT, ICitizenTemplateNameT, IFunctionNameT, IFunctionTemplateNameT, IImplNameT, IImplTemplateNameT, IInstantiationNameT, IInterfaceNameT, IInterfaceTemplateNameT, INameT, IStructNameT, IStructTemplateNameT, ISubKindNameT, ISubKindTemplateNameT, ISuperKindNameT, ISuperKindTemplateNameT, ITemplateNameT, ImplBoundNameT, ImplNameT, InterfaceNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT, NameTranslator, PlaceholderNameT, PlaceholderTemplateNameT, RawArrayNameT, RuneNameT, RuntimeSizedArrayNameT, StaticSizedArrayNameT, StructNameT}
import dev.vale.typing.templata._
import dev.vale.typing.types._
import dev.vale.highertyping._
import dev.vale.parsing.ast.ImmutableRuneAttributeP
import dev.vale.postparsing._
import dev.vale.typing._
import dev.vale.typing.ast.{PrototypeT}
import dev.vale.typing.citizen.{IResolveOutcome, ImplCompiler, IsParent, IsParentResult, IsntParent, ResolveSuccess}
import dev.vale.typing.templata.ITemplata.{expectInteger, expectKindTemplata, expectMutability, expectVariability}
import dev.vale.typing.types._
import dev.vale.typing.templata._

import scala.collection.immutable.{List, Map, Set}

// See SBITAFD, we need to register bounds for these new instantiations. This instructs us where
// to get those new bounds from.
sealed trait IBoundArgumentsSource
case object InheritBoundsFromTypeItself extends IBoundArgumentsSource
case class UseBoundsFromContainer(
  runeToFuncBound: Map[IRuneS, FullNameT[FunctionBoundNameT]],
  runeToImplBound: Map[IRuneS, FullNameT[ImplBoundNameT]],
  instantiationBoundArguments: InstantiationBoundArguments
) extends IBoundArgumentsSource

trait ITemplataCompilerDelegate {

  def isParent(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment,
    parentRanges: List[RangeS],
    subKindTT: ISubKindTT,
    superKindTT: ISuperKindTT):
  IsParentResult

  def resolveStruct(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    structTemplata: StructDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  IResolveOutcome[StructTT]

  def resolveInterface(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    // We take the entire templata (which includes environment and parents) so we can incorporate
    // their rules as needed
    interfaceTemplata: InterfaceDefinitionTemplata,
    uncoercedTemplateArgs: Vector[ITemplata[ITemplataType]]):
  IResolveOutcome[InterfaceTT]

  def resolveStaticSizedArrayKind(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    mutability: ITemplata[MutabilityTemplataType],
    variability: ITemplata[VariabilityTemplataType],
    size: ITemplata[IntegerTemplataType],
    type2: CoordT):
  StaticSizedArrayTT

  def resolveRuntimeSizedArrayKind(env: IEnvironment, state: CompilerOutputs, element: CoordT, arrayMutability: ITemplata[MutabilityTemplataType]): RuntimeSizedArrayTT
}

object TemplataCompiler {
  def getTopLevelDenizenFullName(
    fullName: FullNameT[INameT],
  ): FullNameT[IInstantiationNameT] = {
    // That said, some things are namespaced inside templates. If we have a `struct Marine` then we'll
    // also have a func drop within its namespace; we'll have a free function instance under a Marine
    // struct template. We want to grab the instance.
    val index =
    fullName.steps.indexWhere({
      case x : IInstantiationNameT => true
      case _ => false
    })
    vassert(index >= 0)
    val initSteps = fullName.steps.slice(0, index)
    val lastStep =
      fullName.steps(index) match {
        case x : IInstantiationNameT => x
        case _ => vwat()
      }
    FullNameT(fullName.packageCoord, initSteps, lastStep)
  }

  def getPlaceholderTemplataFullName(implPlaceholder: ITemplata[ITemplataType]) = {
    implPlaceholder match {
      case PlaceholderTemplata(n, _) => n
      case KindTemplata(PlaceholderT(n)) => n
      case CoordTemplata(CoordT(_, PlaceholderT(n))) => n
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

  def getFunctionTemplate(fullName: FullNameT[IFunctionNameT]): FullNameT[IFunctionTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getCitizenTemplate(fullName: FullNameT[ICitizenNameT]): FullNameT[ICitizenTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
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

  def getSuperTemplate(fullName: FullNameT[INameT]): FullNameT[INameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      getNameTemplate(last))
  }

  def getTemplate(fullName: FullNameT[IInstantiationNameT]): FullNameT[ITemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getSubKindTemplate(fullName: FullNameT[ISubKindNameT]): FullNameT[ISubKindTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getSuperKindTemplate(fullName: FullNameT[ISuperKindNameT]): FullNameT[ISuperKindTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getStructTemplate(fullName: FullNameT[IStructNameT]): FullNameT[IStructTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getInterfaceTemplate(fullName: FullNameT[IInterfaceNameT]): FullNameT[IInterfaceTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getExportTemplate(fullName: FullNameT[ExportNameT]): FullNameT[ExportTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getImplTemplate(fullName: FullNameT[IImplNameT]): FullNameT[IImplTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def getPlaceholderTemplate(fullName: FullNameT[PlaceholderNameT]): FullNameT[PlaceholderTemplateNameT] = {
    val FullNameT(packageCoord, initSteps, last) = fullName
    FullNameT(
      packageCoord,
      initSteps,//.map(getNameTemplate), // See GLIOGN for why we map the initSteps names too
      last.template)
  }

  def assembleRuneToFunctionBound(templatas: TemplatasStore): Map[IRuneS, FullNameT[FunctionBoundNameT]] = {
    templatas.entriesByNameT.toIterable.flatMap({
      case (RuneNameT(rune), TemplataEnvEntry(PrototypeTemplata(_, PrototypeT(FullNameT(packageCoord, initSteps, name @ FunctionBoundNameT(_, _, _)), returnType)))) => {
        Some(rune -> FullNameT(packageCoord, initSteps, name))
      }
      case _ => None
    }).toMap
  }

  def assembleRuneToImplBound(templatas: TemplatasStore): Map[IRuneS, FullNameT[ImplBoundNameT]] = {
    templatas.entriesByNameT.toIterable.flatMap({
      case (RuneNameT(rune), TemplataEnvEntry(IsaTemplata(_, FullNameT(packageCoord, initSteps, name @ ImplBoundNameT(_, _)), _, _))) => {
        Some(rune -> FullNameT(packageCoord, initSteps, name))
      }
      case _ => None
    }).toMap
  }

  def substituteTemplatasInCoord(coutputs: CompilerOutputs, interner: Interner, keywords: Keywords, substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])], boundArgumentsSource: IBoundArgumentsSource, coord: CoordT):
  CoordT = {
    val CoordT(ownership, kind) = coord
    substituteTemplatasInKind(coutputs, interner, keywords, substitutions, boundArgumentsSource, kind) match {
      case KindTemplata(kind) => CoordT(ownership, kind)
      case CoordTemplata(CoordT(innerOwnership, kind)) => {
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
        CoordT(resultOwnership, kind)
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
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    kind: KindT):
  ITemplata[ITemplataType] = {
    kind match {
      case IntT(bits) => KindTemplata(kind)
      case BoolT() => KindTemplata(kind)
      case StrT() => KindTemplata(kind)
      case FloatT() => KindTemplata(kind)
      case VoidT() => KindTemplata(kind)
      case NeverT(_) => KindTemplata(kind)
      case RuntimeSizedArrayTT(FullNameT(packageCoord, initSteps, RuntimeSizedArrayNameT(template, RawArrayNameT(mutability, elementType)))) => {
        KindTemplata(
          interner.intern(RuntimeSizedArrayTT(
            FullNameT(
              packageCoord,
              initSteps,
              interner.intern(RuntimeSizedArrayNameT(
                template,
                interner.intern(RawArrayNameT(
                  expectMutability(substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, mutability)),
                  substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, elementType)))))))))
      }
      case StaticSizedArrayTT(FullNameT(packageCoord, initSteps, StaticSizedArrayNameT(template, size, variability, RawArrayNameT(mutability, elementType)))) => {
        KindTemplata(
          interner.intern(StaticSizedArrayTT(
            FullNameT(
              packageCoord,
              initSteps,
              interner.intern(StaticSizedArrayNameT(
                template,
                expectInteger(substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, size)),
                expectVariability(substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, variability)),
                interner.intern(RawArrayNameT(
                  expectMutability(substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, mutability)),
                  substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, elementType)))))))))
      }
      case PlaceholderT(hayName @ FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(index))))
          if index < substitutions.length && hayName == substitutions(index)._1 => {
        substitutions(index)._2
      }
      case PlaceholderT(_) => KindTemplata(kind)
      case s @ StructTT(_) => KindTemplata(substituteTemplatasInStruct(coutputs, interner, keywords, substitutions, boundArgumentsSource, s))
      case s @ InterfaceTT(_) => KindTemplata(substituteTemplatasInInterface(coutputs, interner, keywords, substitutions, boundArgumentsSource, s))
    }
  }

  def substituteTemplatasInStruct(coutputs: CompilerOutputs, interner: Interner, keywords: Keywords, substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])], boundArgumentsSource: IBoundArgumentsSource, structTT: StructTT):
  StructTT = {
    val StructTT(FullNameT(packageCoord, initSteps, last)) = structTT
    val newStruct =
      interner.intern(
        StructTT(
          FullNameT(
            packageCoord,
            initSteps,
            last match {
              case AnonymousSubstructNameT(template, templateArgs) => {
                interner.intern(AnonymousSubstructNameT(
                  template,
                  templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata))))
              }
              case StructNameT(template, templateArgs) => {
                interner.intern(StructNameT(
                  template,
                  templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata))))
              }
              case LambdaCitizenNameT(template) => {
                interner.intern(LambdaCitizenNameT(template))
              }
            })))
    // See SBITAFD, we need to register bounds for these new instantiations.
    val instantiationBoundArgs =
      vassertSome(coutputs.getInstantiationBounds(structTT.fullName))
    coutputs.addInstantiationBounds(
      newStruct.fullName,
      translateInstantiationBounds(
        coutputs, interner, keywords, substitutions, boundArgumentsSource, instantiationBoundArgs))
    newStruct
  }

  private def translateInstantiationBounds(
    coutputs: CompilerOutputs,
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    instantiationBoundArgs: InstantiationBoundArguments):
  InstantiationBoundArguments = {
    boundArgumentsSource match {
      case InheritBoundsFromTypeItself => {
        val x =
          substituteTemplatasInBounds(
            coutputs,
            interner,
            keywords,
            substitutions,
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
      case UseBoundsFromContainer(containerRuneToFuncBound, containerRuneToImplBound, containerInstantiationBoundArgs) => {
        // Here, we're grabbing something inside a struct, like with the dot operator. We'll want to
        // make some instantiation bound args for this new type that our function knows about.
        // Luckily, we can use some bounds from the containing struct to satisfy its members bounds.

        val containerFuncBoundToBoundArg =
          containerInstantiationBoundArgs.runeToFunctionBoundArg.map({ case (rune, containerFuncBoundArg) =>
            vassertSome(containerRuneToFuncBound.get(rune)) -> containerFuncBoundArg
          })
        val containerImplBoundToBoundArg =
          containerInstantiationBoundArgs.runeToImplBoundArg.map({ case (rune, containerImplBoundArg) =>
            vassertSome(containerRuneToImplBound.get(rune)) -> containerImplBoundArg
          })
        InstantiationBoundArguments(
          instantiationBoundArgs.runeToFunctionBoundArg.mapValues(funcBoundArg => {
            funcBoundArg.fullName match {
              case FullNameT(packageCoord, initSteps, fbn@FunctionBoundNameT(_, _, _)) => {
                vassertSome(containerFuncBoundToBoundArg.get(FullNameT(packageCoord, initSteps, fbn)))
              }
              case _ => {
                // Not sure if this call is really necessary...
                substituteTemplatasInPrototype(coutputs, interner, keywords, substitutions, boundArgumentsSource, funcBoundArg)
              }
            }
          }),
          instantiationBoundArgs.runeToImplBoundArg.mapValues(implBoundArg => {
            implBoundArg match {
              case FullNameT(packageCoord, initSteps, ibn@ImplBoundNameT(_, _)) => {
                vassertSome(containerImplBoundToBoundArg.get(FullNameT(packageCoord, initSteps, ibn)))
              }
              case _ => {
                // Not sure if this call is really necessary...
                substituteTemplatasInImplFullName(coutputs, interner, keywords, substitutions, boundArgumentsSource, implBoundArg)
              }
            }
          }))
      }
    }
  }

  def substituteTemplatasInImplFullName(
    coutputs: CompilerOutputs,
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    implFullName: FullNameT[IImplNameT]):
  FullNameT[IImplNameT] = {
    val FullNameT(packageCoord, initSteps, last) = implFullName
    val newImplFullName =
      FullNameT(
        packageCoord,
        initSteps,
        last match {
          case in @ ImplNameT(template, templateArgs, subCitizen) => {
            interner.intern(ImplNameT(
              template,
              templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata)),
              expectKindTemplata(substituteTemplatasInKind(coutputs, interner, keywords, substitutions, boundArgumentsSource, subCitizen)).kind.expectCitizen()))
          }
          case other => vimpl(other)
        })

    val instantiationBoundArgs = vassertSome(coutputs.getInstantiationBounds(implFullName))
    // See SBITAFD, we need to register bounds for these new instantiations.
    coutputs.addInstantiationBounds(
      newImplFullName,
      translateInstantiationBounds(
        coutputs, interner, keywords, substitutions, boundArgumentsSource, instantiationBoundArgs))
    newImplFullName
  }

  def substituteTemplatasInBounds(
    coutputs: CompilerOutputs,
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    boundArgs: InstantiationBoundArguments):
  InstantiationBoundArguments = {
    val InstantiationBoundArguments(runeToFunctionBoundArg, runeToImplBoundArg) = boundArgs
    InstantiationBoundArguments(
      runeToFunctionBoundArg.mapValues(funcBoundArg => {
        substituteTemplatasInPrototype(coutputs, interner, keywords, substitutions, boundArgumentsSource, funcBoundArg)
      }),
      runeToImplBoundArg.mapValues(implBoundArg => {
        substituteTemplatasInImplFullName(
          coutputs, interner, keywords, substitutions, boundArgumentsSource, implBoundArg)
      }))
  }

  def substituteTemplatasInInterface(coutputs: CompilerOutputs, interner: Interner, keywords: Keywords, substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])], boundArgumentsSource: IBoundArgumentsSource, interfaceTT: InterfaceTT):
  InterfaceTT = {
    val InterfaceTT(FullNameT(packageCoord, initSteps, last)) = interfaceTT
    val newInterface =
      interner.intern(
        InterfaceTT(
          FullNameT(
            packageCoord,
            initSteps,
            last match {
              case InterfaceNameT(template, templateArgs) => {
                interner.intern(InterfaceNameT(
                  template,
                  templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata))))
              }
            })))
    // See SBITAFD, we need to register bounds for these new instantiations.
    val instantiationBoundArgs =
      vassertSome(coutputs.getInstantiationBounds(interfaceTT.fullName))
    coutputs.addInstantiationBounds(
      newInterface.fullName,
      translateInstantiationBounds(coutputs, interner, keywords, substitutions, boundArgumentsSource, instantiationBoundArgs))
    newInterface
  }

  def substituteTemplatasInTemplata(coutputs: CompilerOutputs, interner: Interner, keywords: Keywords, substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])], boundArgumentsSource: IBoundArgumentsSource, templata: ITemplata[ITemplataType]):
  ITemplata[ITemplataType] = {
    templata match {
      case CoordTemplata(c) => CoordTemplata(substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, c))
      case KindTemplata(k) => substituteTemplatasInKind(coutputs, interner, keywords, substitutions, boundArgumentsSource, k)
      case p @ PlaceholderTemplata(FullNameT(_, _, PlaceholderNameT(PlaceholderTemplateNameT(index))), _) => {
        if (index < substitutions.length && p.fullNameT == substitutions(index)._1) {
          substitutions(index)._2
        } else {
          vwat()
        }
      }
      case MutabilityTemplata(_) => templata
      case VariabilityTemplata(_) => templata
      case IntegerTemplata(_) => templata
      case BooleanTemplata(_) => templata
      case PrototypeTemplata(declarationRange, prototype) => {
        PrototypeTemplata(
          declarationRange,
          substituteTemplatasInPrototype(coutputs, interner, keywords, substitutions, boundArgumentsSource, prototype))
      }
      case other => vimpl(other)
    }
  }

  def substituteTemplatasInPrototype(
    coutputs: CompilerOutputs,
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    originalPrototype: PrototypeT):
  PrototypeT = {
    val PrototypeT(FullNameT(packageCoord, initSteps, funcName), returnType) = originalPrototype
    val substitutedTemplateArgs = funcName.templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata))
    val substitutedParams = funcName.parameters.map((coord: CoordT) => substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, coord))
    val substitutedReturnType = substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, returnType)
    val substitutedFuncName = funcName.template.makeFunctionName(interner, keywords, substitutedTemplateArgs, substitutedParams)
    val prototype = PrototypeT(FullNameT(packageCoord, initSteps, substitutedFuncName), substitutedReturnType)

    prototype.fullName.last match {
      case FunctionBoundNameT(template, templateArgs, parameters) => {
        // It's a function bound, it has no function bounds of its own.
        coutputs.addInstantiationBounds(prototype.fullName, InstantiationBoundArguments(Map(), Map()))
      }
      case _ => {
        // Not really sure if we're supposed to add bounds or something here.
        vassert(coutputs.getInstantiationBounds(prototype.fullName).nonEmpty)
      }
    }

    prototype
  }

  def substituteTemplatasInFunctionBoundFullName(
    coutputs: CompilerOutputs,
    interner: Interner,
    keywords: Keywords,
    substitutions: Vector[(FullNameT[PlaceholderNameT], ITemplata[ITemplataType])],
    boundArgumentsSource: IBoundArgumentsSource,
    original: FullNameT[FunctionBoundNameT]):
  FullNameT[FunctionBoundNameT] = {
    val FullNameT(packageCoord, initSteps, funcName) = original
    val substitutedTemplateArgs = funcName.templateArgs.map((templata: ITemplata[ITemplataType]) => substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata))
    val substitutedParams = funcName.parameters.map((coord: CoordT) => substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, coord))
//    val substitutedReturnType = substituteTemplatasInCoord(coutputs, interner, keywords, returnType, substitutions)
    val substitutedFuncName = funcName.template.makeFunctionName(interner, keywords, substitutedTemplateArgs, substitutedParams)
    val newFullName = FullNameT(packageCoord, initSteps, substitutedFuncName)

    // It's a function bound, it has no function bounds of its own.
    coutputs.addInstantiationBounds(newFullName, InstantiationBoundArguments(Map(), Map()))

    newFullName
  }

  trait IPlaceholderSubstituter {
    def substituteForCoord(coutputs: CompilerOutputs, coordT: CoordT): CoordT
    def substituteForInterface(coutputs: CompilerOutputs, interfaceTT: InterfaceTT): InterfaceTT
    def substituteForTemplata(coutputs: CompilerOutputs, coordT: ITemplata[ITemplataType]): ITemplata[ITemplataType]
    def substituteForPrototype(coutputs: CompilerOutputs, proto: PrototypeT): PrototypeT
  }
  def getPlaceholderSubstituter(
    interner: Interner,
    keywords: Keywords,
    // This is the Ship<WarpFuel>.
    name: FullNameT[IInstantiationNameT],
    boundArgumentsSource: IBoundArgumentsSource):
    // The Engine<T> is given later to the IPlaceholderSubstituter
  IPlaceholderSubstituter = {
    val topLevelDenizenFullName = getTopLevelDenizenFullName(name)
    val templateArgs = topLevelDenizenFullName.last.templateArgs
    val topLevelDenizenTemplateFullName = getTemplate(topLevelDenizenFullName)

    TemplataCompiler.getPlaceholderSubstituter(
      interner,
      keywords,
      topLevelDenizenTemplateFullName,
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
    interner: Interner,
    keywords: Keywords,
    // This is the Ship<WarpFuel>.
    templateName: FullNameT[ITemplateNameT],
    // The Engine<T> is given later to the IPlaceholderSubstituter
    templateArgs: Vector[ITemplata[ITemplataType]],
    boundArgumentsSource: IBoundArgumentsSource):
  IPlaceholderSubstituter = {
    val substitutions =
      templateArgs.zipWithIndex.map({ case (arg, index) =>
        val placeholderFullName = templateName.addStep(interner.intern(PlaceholderNameT(interner.intern(PlaceholderTemplateNameT(index)))))
        placeholderFullName -> arg
      }).toVector
    new IPlaceholderSubstituter {
      override def substituteForCoord(coutputs: CompilerOutputs, coordT: CoordT): CoordT = {
        TemplataCompiler.substituteTemplatasInCoord(coutputs, interner, keywords, substitutions, boundArgumentsSource, coordT)
      }
      override def substituteForInterface(coutputs: CompilerOutputs, interfaceTT: InterfaceTT): InterfaceTT = {
        TemplataCompiler.substituteTemplatasInInterface(coutputs, interner, keywords, substitutions, boundArgumentsSource, interfaceTT)
      }
      override def substituteForTemplata(coutputs: CompilerOutputs, templata: ITemplata[ITemplataType]): ITemplata[ITemplataType] = {
        TemplataCompiler.substituteTemplatasInTemplata(coutputs, interner, keywords, substitutions, boundArgumentsSource, templata)
      }
      override def substituteForPrototype(coutputs: CompilerOutputs, proto: PrototypeT): PrototypeT = {
        TemplataCompiler.substituteTemplatasInPrototype(coutputs, interner, keywords, substitutions, boundArgumentsSource, proto)
      }
    }
  }

//  // If you have a type (citizenTT) and it contains something (like a member) then
//  // you can use this function to figure out what the member looks like to you, the outsider.
//  // It will take out all the internal placeholders internal to the citizen, and replace them
//  // with what was given in citizenTT's template args.
//  def getTemplataTransformer(interner: Interner, coutputs: CompilerOutputs, citizenTT: ICitizenTT):
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
//      TemplataCompiler.substituteTemplatasInTemplata(coutputs, interner, keywords, templataToTransform, substitutions)
//    }
//  }


  def getReachableBounds(
    interner: Interner,
    keywords: Keywords,
    coutputs: CompilerOutputs,
    templata: ITemplata[ITemplataType]):
  Vector[PrototypeTemplata] = {
    val maybeMentionedKind =
      templata match {
        case KindTemplata(kind) => Some(kind)
        case CoordTemplata(CoordT(_, kind)) => Some(kind)
        case _ => None
      }
    maybeMentionedKind match {
      case Some(ICitizenTT(fullName)) => {
        val substituter =
          TemplataCompiler.getPlaceholderSubstituter(
            interner, keywords, fullName,
            // This function is all about gathering bounds from the incoming parameter types.
            InheritBoundsFromTypeItself)
        val citizenTemplateFullName = TemplataCompiler.getCitizenTemplate(fullName)
        val innerEnv = coutputs.getInnerEnvForType(citizenTemplateFullName)
        val reachablePrototypes =
          innerEnv
            .lookupAllWithImpreciseName(interner.intern(PrototypeNameS()), Set(TemplataLookupContext))
            .map({
              case PrototypeTemplata(range, prototype) => {
                PrototypeTemplata(range, substituter.substituteForPrototype(coutputs, prototype))
              }
              case other => vwat(other)
            })
        reachablePrototypes.toVector
      }
      case _ => Vector()
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
    callingEnv: IEnvironment,
    parentRanges: List[RangeS],
    sourcePointerType: CoordT,
    targetPointerType: CoordT):
  Boolean = {

    val CoordT(targetOwnership, targetType) = targetPointerType;
    val CoordT(sourceOwnership, sourceType) = sourcePointerType;

    // Note the Never case will short-circuit a true, regardless of the other checks (ownership)

    (sourceType, targetType) match {
      case (NeverT(_), _) => return true
      case (a, b) if a == b =>
      case (VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | contentsRuntimeSizedArrayTT(_, _) | contentsStaticSizedArrayTT(_, _, _, _), _) => return false
      case (_, VoidT() | IntT(_) | BoolT() | StrT() | FloatT() | contentsRuntimeSizedArrayTT(_, _) | contentsStaticSizedArrayTT(_, _, _, _)) => return false
      case (_, StructTT(_)) => return false
      case (a : ISubKindTT, b : ISuperKindTT) => {
        delegate.isParent(coutputs, callingEnv, parentRanges, a, b) match {
          case IsParent(_, _, _) =>
          case IsntParent(_) => return false
        }
      }
      case _ => {
        vfail("Dont know if we can convert from " + sourceType + " to " + targetType)
      }
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

  def pointifyKind(coutputs: CompilerOutputs, kind: KindT, ownershipIfMutable: OwnershipT): CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
    val ownership =
      mutability match {
        case PlaceholderTemplata(fullNameT, tyype) => vimpl()
        case MutabilityTemplata(MutableT) => ownershipIfMutable
        case MutabilityTemplata(ImmutableT) => ShareT
      }
    kind match {
      case a @ contentsRuntimeSizedArrayTT(_, _) => {
        CoordT(ownership, a)
      }
      case a @ contentsStaticSizedArrayTT(_, _, _, _) => {
        CoordT(ownership, a)
      }
      case s @ StructTT(_) => {
        CoordT(ownership, s)
      }
      case i @ InterfaceTT(_) => {
        CoordT(ownership, i)
      }
      case VoidT() => {
        CoordT(ShareT, VoidT())
      }
      case i @ IntT(_) => {
        CoordT(ShareT, i)
      }
      case FloatT() => {
        CoordT(ShareT, FloatT())
      }
      case BoolT() => {
        CoordT(ShareT, BoolT())
      }
      case StrT() => {
        CoordT(ShareT, StrT())
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

  def evaluateInterfaceTemplata(
    coutputs: CompilerOutputs,
    callingEnv: IEnvironment, // See CSSNCE
    callRange: List[RangeS],
    template: InterfaceDefinitionTemplata,
    templateArgs: Vector[ITemplata[ITemplataType]],
    expectedType: ITemplataType):
  (ITemplata[ITemplataType]) = {
    val uncoercedTemplata =
      delegate.resolveInterface(coutputs, callingEnv, callRange, template, templateArgs).expect().kind
    val templata =
      coerce(coutputs, callingEnv, callRange, KindTemplata(uncoercedTemplata), expectedType)
    (templata)
  }

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
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    name: INameT):
  (ITemplata[ITemplataType]) = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    vassertOne(env.lookupNearestWithName(name, Set(TemplataLookupContext)))
  }

  def lookupTemplata(
    env: IEnvironment,
    coutputs: CompilerOutputs,
    range: List[RangeS],
    name: IImpreciseNameS):
  Option[ITemplata[ITemplataType]] = {
    // Changed this from AnythingLookupContext to TemplataLookupContext
    // because this is called from StructCompiler to figure out its members.
    // We could instead pipe a lookup context through, if this proves problematic.
    val results = env.lookupNearestWithImpreciseName(name, Set(TemplataLookupContext))
    if (results.size > 1) {
      vfail()
    }
    results.headOption
  }

  def coerceKindToCoord(coutputs: CompilerOutputs, kind: KindT):
  CoordT = {
    val mutability = Compiler.getMutability(coutputs, kind)
    CoordT(
      mutability match {
        case MutabilityTemplata(MutableT) => OwnT
        case MutabilityTemplata(ImmutableT) => ShareT
        case PlaceholderTemplata(fullNameT, tyype) => OwnT
      },
      kind)
  }

  def coerce(
    coutputs: CompilerOutputs,
    env: IEnvironment,
    range: List[RangeS],
    templata: ITemplata[ITemplataType],
    tyype: ITemplataType):
  (ITemplata[ITemplataType]) = {
    if (templata.tyype == tyype) {
      templata
    } else {
      (templata, tyype) match {
        case (KindTemplata(kind), CoordTemplataType()) => {
          CoordTemplata(coerceKindToCoord(coutputs, kind))
        }
        case (st@StructDefinitionTemplata(declaringEnv, structA), KindTemplataType()) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.resolveStruct(coutputs, env, range, st, Vector.empty).expect().kind
          (KindTemplata(kind))
        }
        case (it@InterfaceDefinitionTemplata(declaringEnv, interfaceA), KindTemplataType()) => {
          if (interfaceA.isTemplate) {
            vfail("Can't coerce " + interfaceA.name + " to be a kind, is a template!")
          }
          val kind =
            delegate.resolveInterface(coutputs, env, range, it, Vector.empty).expect().kind
          (KindTemplata(kind))
        }
        case (st@StructDefinitionTemplata(declaringEnv, structA), CoordTemplataType()) => {
          if (structA.isTemplate) {
            vfail("Can't coerce " + structA.name + " to be a coord, is a template!")
          }
          val kind =
            delegate.resolveStruct(coutputs, env, range, st, Vector.empty).expect().kind
          val mutability = Compiler.getMutability(coutputs, kind)

          // Default ownership is own for mutables, share for imms
          val ownership =
            mutability match {
              case MutabilityTemplata(MutableT) => OwnT
              case MutabilityTemplata(ImmutableT) => ShareT
              case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
            }
          val coerced = CoordTemplata(CoordT(ownership, kind))
          (coerced)
        }
        case (it@InterfaceDefinitionTemplata(declaringEnv, interfaceA), CoordTemplataType()) => {
          if (interfaceA.isTemplate) {
            vfail("Can't coerce " + interfaceA.name + " to be a coord, is a template!")
          }
          val kind =
            delegate.resolveInterface(coutputs, env, range, it, Vector.empty).expect().kind
          val mutability = Compiler.getMutability(coutputs, kind)
          val coerced =
            CoordTemplata(
              CoordT(
                mutability match {
                  case MutabilityTemplata(MutableT) => OwnT
                  case MutabilityTemplata(ImmutableT) => ShareT
                  case PlaceholderTemplata(fullNameT, MutabilityTemplataType()) => vimpl()
                },
                kind))
          (coerced)
        }
        case _ => {
          vfail("Can't coerce a " + templata.tyype + " to be a " + tyype)
        }
      }
    }
  }

  def resolveStructTemplate(structTemplata: StructDefinitionTemplata): FullNameT[IStructTemplateNameT] = {
    val StructDefinitionTemplata(declaringEnv, structA) = structTemplata
    declaringEnv.fullName.addStep(nameTranslator.translateStructName(structA.name))
  }

  def resolveInterfaceTemplate(interfaceTemplata: InterfaceDefinitionTemplata): FullNameT[IInterfaceTemplateNameT] = {
    val InterfaceDefinitionTemplata(declaringEnv, interfaceA) = interfaceTemplata
    declaringEnv.fullName.addStep(nameTranslator.translateInterfaceName(interfaceA.name))
  }

  def resolveCitizenTemplate(citizenTemplata: CitizenDefinitionTemplata): FullNameT[ICitizenTemplateNameT] = {
    citizenTemplata match {
      case st @ StructDefinitionTemplata(_, _) => resolveStructTemplate(st)
      case it @ InterfaceDefinitionTemplata(_, _) => resolveInterfaceTemplate(it)
    }
  }

  def citizenIsFromTemplate(actualCitizenRef: ICitizenTT, expectedCitizenTemplata: ITemplata[ITemplataType]): Boolean = {
    val citizenTemplateFullName =
      expectedCitizenTemplata match {
        case st @ StructDefinitionTemplata(_, _) => resolveStructTemplate(st)
        case it @ InterfaceDefinitionTemplata(_, _) => resolveInterfaceTemplate(it)
        case KindTemplata(c : ICitizenTT) => TemplataCompiler.getCitizenTemplate(c.fullName)
        case CoordTemplata(CoordT(OwnT | ShareT, c : ICitizenTT)) => TemplataCompiler.getCitizenTemplate(c.fullName)
        case _ => return false
      }
    TemplataCompiler.getCitizenTemplate(actualCitizenRef.fullName) == citizenTemplateFullName
  }

  def createPlaceholder(
    coutputs: CompilerOutputs,
    env: IEnvironment,
    namePrefix: FullNameT[INameT],
    genericParam: GenericParameterS,
    index: Int,
    runeToType: Map[IRuneS, ITemplataType],
    registerWithCompilerOutputs: Boolean):
  ITemplata[ITemplataType] = {
    val immutable =
      genericParam.attributes.exists({
        case ImmutableRuneAttributeS(_) => true
        case _ => false
      })
    val runeType = vassertSome(runeToType.get(genericParam.rune.rune))
    createPlaceholderInner(
      coutputs, env, namePrefix, index, runeType, immutable, registerWithCompilerOutputs)
  }

  def createPlaceholderInner(
    coutputs: CompilerOutputs,
    env: IEnvironment,
    namePrefix: FullNameT[INameT],
    index: Int,
    runeType: ITemplataType,
    immutable: Boolean,
    registerWithCompilerOutputs: Boolean):
  ITemplata[ITemplataType] = {
    val placeholderFullName =
      namePrefix.addStep(
        interner.intern(PlaceholderNameT(
          interner.intern(PlaceholderTemplateNameT(index)))))
    val placeholderTemplateFullName =
      TemplataCompiler.getPlaceholderTemplate(placeholderFullName)

    val placeholderKindT = PlaceholderT(placeholderFullName)
    if (registerWithCompilerOutputs) {
      coutputs.declareType(placeholderTemplateFullName)

      val mutability = MutabilityTemplata(if (immutable) ImmutableT else MutableT)
      coutputs.declareTypeMutability(placeholderTemplateFullName, mutability)

      val placeholderEnv = GeneralEnvironment.childOf(interner, env, placeholderTemplateFullName)
      coutputs.declareTypeOuterEnv(placeholderTemplateFullName, placeholderEnv)
      coutputs.declareTypeInnerEnv(placeholderTemplateFullName, placeholderEnv)
    }

    runeType match {
      case KindTemplataType() => {
        KindTemplata(placeholderKindT)
      }
      // TODO: Not sure what to put here when we do regions. We might need to
      // flood the nearest region annotation downward, and then apply it if it's
      // a coord or something. Remembering that in every templex would be bothersome
      // though.
      // For now, we can manually add them.
      // So, I guess we could just assume the function's default region here then.
      case CoordTemplataType() => {
        val ownership =
          if (immutable) {
            ShareT
          } else {
            OwnT
          }
        CoordTemplata(CoordT(ownership, placeholderKindT))
      }
      case _ => PlaceholderTemplata(placeholderFullName, runeType)
    }
  }

}
