package dev.vale.typing

import dev.vale.{FileCoordinate, FileCoordinateMap, RangeS, vimpl}
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{FailedSolve, IIncompleteOrFailedSolve, IncompleteSolve, RuleError, SolverErrorHumanizer}
import dev.vale.typing.types._
import dev.vale.SourceCodeUtils.{humanizePos, lineBegin, lineContaining, lineRangeContaining}
import dev.vale.highertyping.FunctionA
import PostParserErrorHumanizer._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.PostParserErrorHumanizer
import OverloadResolver.{FindFunctionFailure, IFindFunctionFailureReason, InferFailure, RuleTypeSolveFailure, SpecificParamDoesntMatchExactly, SpecificParamDoesntSend, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import dev.vale.highertyping.{FunctionA, HigherTypingErrorHumanizer}
import dev.vale.typing.ast.{AbstractT, FunctionBannerT, FunctionCalleeCandidate, HeaderCalleeCandidate, ICalleeCandidate, PrototypeT, SignatureT}
import dev.vale.typing.infer.{BadIsaSubKind, BadIsaSuperKind, CallResultWasntExpectedType, CantCheckPlaceholder, CantGetComponentsOfPlaceholderPrototype, CantShareMutable, CouldntFindFunction, CouldntResolveKind, ITypingPassSolverError, IsaFailed, KindIsNotConcrete, KindIsNotInterface, LookupFailed, NoAncestorsSatisfyCall, OneOfFailed, OwnershipDidntMatch, ReceivingDifferentOwnerships, ReturnTypeConflict, SendingNonCitizen, SendingNonIdenticalKinds, WrongNumberOfTemplateArgs}
import dev.vale.typing.names.{AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, CitizenNameT, CitizenTemplateNameT, CodeVarNameT, IdT, FunctionBoundNameT, FunctionBoundTemplateNameT, FunctionNameT, FunctionTemplateNameT, INameT, IVarNameT, InterfaceTemplateNameT, LambdaCallFunctionNameT, LambdaCallFunctionTemplateNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT, PlaceholderNameT, PlaceholderTemplateNameT, StructTemplateNameT}
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.templata.Conversions
import dev.vale.typing.types.CoordT
import dev.vale.RangeS
import dev.vale.typing.citizen.ResolveFailure

object CompilerErrorHumanizer {
  def humanize(
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      err: ICompileErrorT):
  String = {
    val errorStrBody =
      err match {
        case RangedInternalErrorT(range, message) => {
          "Internal error: " + message
        }
        case CouldntFindOverrideT(range, fff) => {
          "Couldn't find an override:\n" +
            humanizeFindFunctionFailure(verbose, codeMap, range, fff)
        }
        case NewImmRSANeedsCallable(range) => {
          "To make an immutable runtime-sized array, need two params: capacity int, plus lambda to populate that many elements."
        }
        case UnexpectedArrayElementType(range, expectedType, actualType) => {
          "Unexpected type for array element, tried to put a " + humanizeTemplata(codeMap, CoordTemplata(actualType)) + " into an array of " + humanizeTemplata(codeMap, CoordTemplata(expectedType))
        }
        case IndexedArrayWithNonInteger(range, tyype) => {
          "Indexed array with non-integer: " + humanizeTemplata(codeMap, CoordTemplata(tyype))
        }
        case CantUseReadonlyReferenceAsReadwrite(range) => {
          "Can't make readonly reference into a readwrite one!"
        }
        case CantReconcileBranchesResults(range, thenResult, elseResult) => {
          "If branches return different types: " + humanizeTemplata(codeMap, CoordTemplata(thenResult)) + " and " + humanizeTemplata(codeMap, CoordTemplata(elseResult))
        }
        case CantMoveOutOfMemberT(range, name) => {
          "Cannot move out of member (" + name + ")"
        }
        case CantMutateFinalMember(range, struct, memberName) => {
          "Cannot mutate final member '" + printableVarName(memberName.localName) + "' of container " + humanizeTemplata(codeMap, KindTemplata(struct))
        }
        case CantMutateFinalElement(range, coord) => {
          "Cannot change a slot in array " + humanizeTemplata(codeMap, CoordTemplata(coord)) + " to point to a different element; it's an array of final references."
        }
        case LambdaReturnDoesntMatchInterfaceConstructor(range) => {
          "Argument function return type doesn't match interface method param"
        }
        case CantUseUnstackifiedLocal(range, name) => {
          "Can't use local that was already moved:" + humanizeName(codeMap, name)
        }
        case CantUnstackifyOutsideLocalFromInsideWhile(range, name) => {
          "Can't move a local (" + name + ") from inside a while loop."
        }
        case CannotSubscriptT(range, tyype) => {
          "Cannot subscript type: " + humanizeTemplata(codeMap, KindTemplata(tyype)) + "!"
        }
        case CouldntConvertForReturnT(range, expectedType, actualType) => {
          "Couldn't convert " + humanizeTemplata(codeMap, CoordTemplata(actualType)) + " to expected return type " + humanizeTemplata(codeMap, CoordTemplata(expectedType))
        }
        case CouldntConvertForMutateT(range, expectedType, actualType) => {
          "Mutate couldn't convert " + actualType + " to expected destination type " + expectedType
        }
        case CouldntFindMemberT(range, memberName) => {
          "Couldn't find member " + memberName + "!"
        }
        case CouldntEvaluatImpl(range, eff) => {
          "Couldn't evaluate impl statement:\n" +
            humanizeCandidateAndFailedSolve(codeMap, eff match {
              case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
                IncompleteSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
                  steps, unsolvedRules, unknownRunes, incompleteConclusions)
              }
              case FailedCompilerSolve(steps, unsolvedRules, error) => {
                FailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
                  steps, unsolvedRules, error)
              }
            })
      }
        case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
          "Function " + printableName(codeMap, functionName) + " return type " + humanizeTemplata(codeMap, CoordTemplata(expectedReturnType)) + " doesn't match body's result: " + humanizeTemplata(codeMap, CoordTemplata(resultType))
        }
        case CouldntFindIdentifierToLoadT(range, name) => {
          "Couldn't find anything named `" + PostParserErrorHumanizer.humanizeImpreciseName(name) + "`!"
        }
        case NonReadonlyReferenceFoundInPureFunctionParameter(range, name) => {
          "Parameter `" + name + "` should be readonly, because it's in a pure function."
        }
        case CouldntFindTypeT(range, name) => {
          "Couldn't find any type named `" + name + "`!"
        }
        case CouldntNarrowDownCandidates(range, candidateRanges) => {
          "Multiple candidates for call:" +
            candidateRanges.map(range => "\n" + humanizePos(codeMap, range.begin) + ":\n  " + lineContaining(codeMap, range.begin)).mkString("")
        }
        case ImmStructCantHaveVaryingMember(range, structName, memberName) => {
          "Immutable struct (\"" + printableName(codeMap, structName) + "\") cannot have varying member (\"" + memberName + "\")."
        }
        case ImmStructCantHaveMutableMember(range, structName, memberName) => {
          "Immutable struct (\"" + printableName(codeMap, structName) + "\") cannot have mutable member (\"" + memberName + "\")."
        }
        case WrongNumberOfDestructuresError(range, actualNum, expectedNum) => {
          "Wrong number of receivers; receiving " + actualNum + " but should be " + expectedNum + "."
        }
        case CantDowncastUnrelatedTypes(range, sourceKind, targetKind, candidates) => {
          "Can't downcast `" + humanizeTemplata(codeMap, KindTemplata(sourceKind)) + "` to unrelated `" + humanizeTemplata(codeMap, KindTemplata(targetKind)) + "`"
        }
        case CantDowncastToInterface(range, targetKind) => {
          "Can't downcast to an interface (" + targetKind + ") yet."
        }
        case ArrayElementsHaveDifferentTypes(range, types) => {
          "Array's elements have different types: " + types.mkString(", ")
        }
        case ExportedFunctionDependedOnNonExportedKind(range, paackage, signature, nonExportedKind) => {
          "Exported function " + signature + " depends on kind " + nonExportedKind + " that wasn't exported from package " + paackage
        }
        case TypeExportedMultipleTimes(range, paackage, exports) => {
          "Type exported multiple times:" + exports.map(export => {
            val posStr = humanizePos(codeMap, export.range.begin)
            val line = lineContaining(codeMap, export.range.begin)
            s"\n  ${posStr}: ${line}"
          })
        }
        case ExternFunctionDependedOnNonExportedKind(range, paackage, signature, nonExportedKind) => {
          "Extern function " + signature + " depends on kind " + nonExportedKind + " that wasn't exported from package " + paackage
        }
        case ExportedImmutableKindDependedOnNonExportedKind(range, paackage, signature, nonExportedKind) => {
          "Exported kind " + signature + " depends on kind " + nonExportedKind + " that wasn't exported from package " + paackage
        }
        case InitializedWrongNumberOfElements(range, expectedNumElements, numElementsInitialized) => {
          "Supplied " + numElementsInitialized + " elements, but expected " + expectedNumElements + "."
        }
        case CouldntFindFunctionToCallT(range, fff) => {
          humanizeFindFunctionFailure(verbose, codeMap, range, fff)
        }
        case CouldntEvaluateFunction(range, eff) => {
          "Couldn't evaluate function:\n" +
          humanizeRejectionReason(verbose, codeMap, range, eff)
        }
        case FunctionAlreadyExists(oldFunctionRange, newFunctionRange, signature) => {
          "Function " + humanizeName(codeMap, signature) + " already exists! Previous declaration at:\n" +
            humanizePos(codeMap, oldFunctionRange.begin)
        }
        case AbstractMethodOutsideOpenInterface(range) => {
          "Open (non-sealed) interfaces can't have abstract methods defined outside the interface."
        }
        case IfConditionIsntBoolean(range, actualType) => {
          "If condition should be a bool, but was: " + actualType
        }
        case WhileConditionIsntBoolean(range, actualType) => {
          "If condition should be a bool, but was: " + actualType
        }
        case CantImplNonInterface(range, struct) => {
          "Can't extend a non-interface: " + struct
        }
        case TypingPassSolverError(range, failedSolve) => {
          val (text, lineBegins) =
            SolverErrorHumanizer.humanizeFailedSolve(
              codeMap,
              humanizeRune,
              humanizeTemplata,
              humanizeRuleError,
              (rule: IRulexSR) => rule.range,
              (rule: IRulexSR) => rule.runeUsages.map(usage => (usage.rune, usage.range)),
              (rule: IRulexSR) => rule.runeUsages.map(_.rune),
              PostParserErrorHumanizer.humanizeRule,
              failedSolve match {
                case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
                  IncompleteSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
                    steps, unsolvedRules, unknownRunes, incompleteConclusions)
                }
                case FailedCompilerSolve(steps, unsolvedRules, error) => {
                  FailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
                    steps, unsolvedRules, error)
                }
              })
          text
        }
        case HigherTypingInferError(range, err) => {
          HigherTypingErrorHumanizer.humanizeRuneTypeSolveError(codeMap, err)
        }
      }

//    val errorId = "T"
    err.range.reverse.map(range => {
      val posStr = humanizePos(codeMap, range.begin)
      val lineContents = lineContaining(codeMap, range.begin)
      f"At ${posStr}:\n${lineContents}\n"
    }).mkString("") +
    errorStrBody + "\n"
  }

  def humanizeResolveFailure(
    verbose: Boolean,
    codeMap: FileCoordinateMap[String],
    fff: ResolveFailure[KindT]):
  String = {
    val ResolveFailure(range, reason) = fff
    humanizeCandidateAndFailedSolve(codeMap, reason match {
      case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
        IncompleteSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
          steps, unsolvedRules, unknownRunes, incompleteConclusions)
      }
      case FailedCompilerSolve(steps, unsolvedRules, error) => {
        FailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
          steps, unsolvedRules, error)
      }
    })
  }

  def humanizeFindFunctionFailure(
    verbose: Boolean,
    codeMap: FileCoordinateMap[String],
    invocationRange: List[RangeS],
    fff: OverloadResolver.FindFunctionFailure): String = {

    val FindFunctionFailure(name, args, rejectedCalleeToReason) = fff
    "Couldn't find a suitable function " +
      PostParserErrorHumanizer.humanizeImpreciseName(name) +
      "(" +
      args.map({
        case tyype => humanizeTemplata(codeMap, CoordTemplata(tyype))
      }).mkString(", ") +
      "). " +
      (if (rejectedCalleeToReason.isEmpty) {
        "No function with that name exists.\n"
      } else {
        "Rejected candidates:\n\n" +
        rejectedCalleeToReason.zipWithIndex.map({ case ((candidate, reason), index) =>
          "Candidate " + (index + 1) + " (of " + rejectedCalleeToReason.size + "): " +
            humanizeCandidate(codeMap, candidate) +
            humanizeRejectionReason(verbose, codeMap, invocationRange, reason) + "\n\n"
        }).mkString("")
      })
  }

  def humanizeBanner(
    codeMap: FileCoordinateMap[String],
    banner: FunctionBannerT):
  String = {
    banner.originFunctionTemplata match {
      case None => "(internal)"
      case Some(x) => printableName(codeMap, x.function.name)
    }
  }

  private def printableName(
    codeMap: FileCoordinateMap[String],
    name: INameS):
  String = {
    name match {
      case CodeVarNameS(name) => name.str
      case TopLevelCitizenDeclarationNameS(name, codeLocation) => name.str
      case LambdaDeclarationNameS(codeLocation) => humanizePos(codeMap, codeLocation) + ": " + "(lambda)"
      case FunctionNameS(name, codeLocation) => humanizePos(codeMap, codeLocation) + ": " + name
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, range)) => humanizePos(codeMap, range.begin) + ": " + name
      case ImmConcreteDestructorNameS(_) => vimpl()
      case ImmInterfaceDestructorNameS(_) => vimpl()
//      case DropNameS(_) => vimpl()
    }
  }

  private def printableKindName(kind: KindT): String = {
    kind match {
      case IntT(bits) => "i" + bits
      case BoolT() => "bool"
      case FloatT() => "float"
      case StrT() => "str"
      case StructTT(f) => printableFullName(f)
    }
  }
  private def printableFullName(fullName2: IdT[INameT]): String = {
    fullName2.localName match {
      case CitizenNameT(humanName, templateArgs) => humanName + (if (templateArgs.isEmpty) "" else "<" + templateArgs.map(_.toString.mkString) + ">")
      case x => x.toString
    }
  }

  private def printableVarName(
    name: IVarNameT):
  String = {
    name match {
      case CodeVarNameT(n) => n.str
    }
  }

  private def getFile(functionA: FunctionA): FileCoordinate = {
    functionA.range.file
  }

  private def humanizeRejectionReason(
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      invocationrange: List[RangeS],
      reason: IFindFunctionFailureReason): String = {

    (reason match {
      case RuleTypeSolveFailure(RuneTypeSolveError(range, failedSolve)) => {
        SolverErrorHumanizer.humanizeFailedSolve(
          codeMap,
          humanizeRune,
          (codeMap, a: ITemplataType) => humanizeTemplataType(a),
          (codeMap, a: IRuneTypeRuleError) => PostParserErrorHumanizer.humanizeRuneTypeError(codeMap, a),
          (rule: IRulexSR) => rule.range,
          (rule: IRulexSR) => rule.runeUsages.map(usage => (usage.rune, usage.range)),
          (rule: IRulexSR) => rule.runeUsages.map(_.rune),
          PostParserErrorHumanizer.humanizeRule,
          failedSolve)._1
      }
      case WrongNumberOfArguments(supplied, expected) => {
        "Number of params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case WrongNumberOfTemplateArguments(supplied, expected) => {
        "Number of template params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case SpecificParamDoesntMatchExactly(index, arg, param) => {
          "Index " + index + " argument " + humanizeTemplata(codeMap, CoordTemplata(arg)) +
          " isn't the same exact type as expected parameter " + humanizeTemplata(codeMap, CoordTemplata(param))
      }
      case SpecificParamDoesntSend(index, arg, param) => {
          " Index " + index + " argument " + humanizeTemplata(codeMap, CoordTemplata(arg)) +
          " can't be given to expected parameter " + humanizeTemplata(codeMap, CoordTemplata(param))
      }
      case SpecificParamVirtualityDoesntMatch(index) => {
        "Virtualities don't match at index " + index
      }
//      case Outscored() => "Outscored!"
      case InferFailure(reason) => {
        humanizeCandidateAndFailedSolve(codeMap, reason match {
          case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
            IncompleteSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
              steps, unsolvedRules, unknownRunes, incompleteConclusions)
          }
          case FailedCompilerSolve(steps, unsolvedRules, error) => {
            FailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError](
              steps, unsolvedRules, error)
          }
        })
      }
    })
  }

  def humanizeRuleError(
    codeMap: FileCoordinateMap[String],
    error: ITypingPassSolverError
  ): String = {
    error match {
      case IsaFailed(sub, suuper) => {
        "Kind " + humanizeTemplata(codeMap, KindTemplata(sub)) + " does not implement interface " + humanizeTemplata(codeMap, KindTemplata(suuper))
      }
      case BadIsaSubKind(kind) => {
        "Kind " + humanizeTemplata(codeMap, KindTemplata(kind)) + " cannot be a sub-kind."
      }
      case CantGetComponentsOfPlaceholderPrototype(_) => {
        "Can't get components of placeholder."
      }
      case ReturnTypeConflict(_, expectedReturnType, actualPrototype) => {
        "Found function: " + humanizeName(codeMap, actualPrototype.fullName) + " which returns " + humanizeTemplata(codeMap, CoordTemplata(actualPrototype.returnType)) + " but expected return type of " + humanizeTemplata(codeMap, CoordTemplata(expectedReturnType))
      }
      case CantShareMutable(kind) => {
        "Can't share a mutable kind: " + humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case BadIsaSuperKind(kind) => {
        "Bad super kind in isa: " + humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case SendingNonIdenticalKinds(sendCoord, receiveCoord) => {
        "Sending non-identical kinds: " + humanizeTemplata(codeMap, CoordTemplata(sendCoord)) + " and " + humanizeTemplata(codeMap, CoordTemplata(receiveCoord))
      }
      case SendingNonCitizen(kind) => {
        "Sending non-struct non-interface Kind: " + humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case CantCheckPlaceholder(range) => {
        "Cant check a placeholder!"
      }
      case CouldntFindFunction(range, fff) => {
        "Couldn't find function to call: " + humanizeFindFunctionFailure(false, codeMap, range, fff)
      }
      case CouldntResolveKind(rf) => {
        "Couldn't find type: " + humanizeResolveFailure(false, codeMap, rf)
      }
      case WrongNumberOfTemplateArgs(expectedNumArgs) => {
        "Wrong number of template args, expected " + expectedNumArgs + "."
      }
      case LookupFailed(name) => "Couldn't find anything named: " + humanizeImpreciseName(name)
      case KindIsNotConcrete(kind) => {
        "Expected kind to be concrete, but was not. Kind: " + kind
      }
      case OneOfFailed(rule) => {
        "One-of rule failed."
      }
      case KindIsNotInterface(kind) => {
        "Expected kind to be interface, but was not. Kind: " + kind
      }
      case CallResultWasntExpectedType(expected, actual) => {
        "Expected an instantiation of " + humanizeTemplata(codeMap, expected) + " but got " + humanizeTemplata(codeMap, actual)
      }
      case OwnershipDidntMatch(coord, expectedOwnership) => {
        "Given type " + humanizeTemplata(codeMap, CoordTemplata(coord)) + " doesn't have expected ownership " + humanizeOwnership(Conversions.unevaluateOwnership(expectedOwnership))
      }
      case ReceivingDifferentOwnerships(params) => {
        "Received conflicting ownerships: " +
          params.map({ case (rune, coord) =>
            humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplata(coord))
          }).mkString(", ")
      }
      case NoAncestorsSatisfyCall(params) => {
        "No ancestors satisfy call: " +
          params.map({ case (rune, coord) =>
            humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplata(coord))
          }).mkString(", ")
      }
    }
  }

  def humanizeCandidateAndFailedSolve(
    codeMap: FileCoordinateMap[String],
    result: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata[ITemplataType], ITypingPassSolverError]):
  String = {
    val (text, lineBegins) =
      SolverErrorHumanizer.humanizeFailedSolve(
        codeMap,
        humanizeRune,
        humanizeTemplata,
        humanizeRuleError,
        (rule: IRulexSR) => rule.range,
        (rule: IRulexSR) => rule.runeUsages.map(usage => (usage.rune, usage.range)),
        (rule: IRulexSR) => rule.runeUsages.map(_.rune),
        PostParserErrorHumanizer.humanizeRule,
        result)
    text
  }

  def humanizeCandidate(codeMap: FileCoordinateMap[String], candidate: ICalleeCandidate) = {
    candidate match {
      case HeaderCalleeCandidate(header) => {
        humanizeName(codeMap, header.fullName)
      }
      case PrototypeTemplataCalleeCandidate(range, prototypeT) => {
        val begin = lineBegin(codeMap, range.begin)
        humanizePos(codeMap, begin) + ":\n" +
          lineContaining(codeMap, begin) + "\n"
      }
      case FunctionCalleeCandidate(ft) => {
        val begin = lineBegin(codeMap, ft.function.range.begin)
        humanizePos(codeMap, begin) + ":\n" +
        lineContaining(codeMap, begin) + "\n"
      }
    }
  }

  def humanizeTemplata(
    codeMap: FileCoordinateMap[String],
    templata: ITemplata[ITemplataType]):
  String = {
    templata match {
      case RuntimeSizedArrayTemplateTemplata() => "Array"
      case StaticSizedArrayTemplateTemplata() => "StaticArray"
      case InterfaceDefinitionTemplata(env, originInterface) => originInterface.name.name.str
      case StructDefinitionTemplata(env, originStruct) => PostParserErrorHumanizer.humanizeName(originStruct.name)
      case VariabilityTemplata(variability) => {
        variability match {
          case FinalT => "final"
          case VaryingT => "vary"
        }
      }
      case IntegerTemplata(value) => value.toString
      case MutabilityTemplata(mutability) => {
        mutability match {
          case MutableT => "mut"
          case ImmutableT => "imm"
        }
      }
      case OwnershipTemplata(ownership) => {
        ownership match {
          case OwnT => "own"
          case BorrowT => "borrow"
          case WeakT => "weak"
          case ShareT => "share"
        }
      }
      case PrototypeTemplata(range, prototype) => {
        humanizeName(codeMap, prototype.fullName)
      }
      case CoordTemplata(CoordT(ownership, kind)) => {
        (ownership match {
          case OwnT => ""
          case ShareT => ""
          case BorrowT => "&"
          case WeakT => "&&"
        }) +
          humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case KindTemplata(kind) => {
        kind match {
          case IntT(bits) => "i" + bits
          case BoolT() => "bool"
          case PlaceholderT(name) => "Kind$" + humanizeName(codeMap, name)
          case StrT() => "str"
          case NeverT(_) => "never"
          case VoidT() => "void"
          case FloatT() => "float"
          case OverloadSetT(_, name) => "(overloads: " + PostParserErrorHumanizer.humanizeImpreciseName(name) + ")"
          case InterfaceTT(name) => humanizeName(codeMap, name)
          case StructTT(name) => humanizeName(codeMap, name)
          case contentsRuntimeSizedArrayTT(mutability, elementType) => {
            "Array<" +
              humanizeTemplata(codeMap, mutability) + ", " +
              humanizeTemplata(codeMap, CoordTemplata(elementType)) + ">"
          }
          case contentsStaticSizedArrayTT(size, mutability, variability, elementType) => {
            "StaticArray<" +
              humanizeTemplata(codeMap, size) + ", " +
              humanizeTemplata(codeMap, mutability) + ", " +
              humanizeTemplata(codeMap, variability) + ", " +
              humanizeTemplata(codeMap, CoordTemplata(elementType)) + ">"
          }
        }
      }
      case CoordListTemplata(coords) => {
        "(" + coords.map(CoordTemplata).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case StringTemplata(value) => "\"" + value + "\""
      case PlaceholderTemplata(fullNameT, tyype) => {
        tyype match {
          case CoordTemplataType() => "$" + humanizeName(codeMap, fullNameT)
          case _ => humanizeTemplataType(tyype) + "$" + humanizeName(codeMap, fullNameT)
        }
      }
      case other => vimpl(other)
    }
  }

  def humanizeName[T <: INameT](
    codeMap: FileCoordinateMap[String],
    name: IdT[T]):
  String = {
    humanizeName(codeMap, name.localName)
  }

  def humanizeName(
    codeMap: FileCoordinateMap[String],
    name: INameT):
  String = {
    name match {
      case FunctionBoundTemplateNameT(humanName, codeLocation) => humanName.str
      case LambdaCallFunctionTemplateNameT(codeLocation, _) => "λF:" + humanizePos(codeMap, codeLocation)
      case LambdaCitizenTemplateNameT(codeLocation) => "λC:" + humanizePos(codeMap, codeLocation)
      case LambdaCallFunctionNameT(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          (if (templateArgs.nonEmpty) {
            "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">"
          } else {
            ""
          }) +
          "(" + parameters.map(CoordTemplata).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case FunctionBoundNameT(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          (if (templateArgs.nonEmpty) {
            "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">"
          } else {
            ""
          }) +
          "(" + parameters.map(CoordTemplata).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case PlaceholderNameT(template) => humanizeName(codeMap, template)
      case PlaceholderTemplateNameT(index) => "_" + index
      case CodeVarNameT(name) => name.str
      case LambdaCitizenNameT(template) => humanizeName(codeMap, template) + "<>"
      case FunctionTemplateNameT(humanName, codeLoc) => humanName.str
      case FunctionNameT(templateName, templateArgs, parameters) => {
        humanizeName(codeMap, templateName) +
          (if (templateArgs.nonEmpty) {
            "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">"
          } else {
            ""
          }) +
          (if (parameters.nonEmpty) {
            "(" + parameters.map(CoordTemplata).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
          } else {
            ""
          })
      }
      case CitizenNameT(humanName, templateArgs) => {
        humanizeName(codeMap, humanName) +
          (if (templateArgs.nonEmpty) {
            "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">"
          } else {
            ""
          })
      }
      case AnonymousSubstructNameT(interface, templateArgs) => {
        humanizeName(codeMap, interface) +
          "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">"
      }
      case AnonymousSubstructTemplateNameT(interface) => {
        humanizeName(codeMap, interface) + ".anonymous"
      }
      case StructTemplateNameT(humanName) => humanName.str
      case InterfaceTemplateNameT(humanName) => humanName.str
    }
  }

  def humanizeSignature(codeMap: FileCoordinateMap[String], signature: SignatureT): String = {
    humanizeName(codeMap, signature.fullName)
  }
}
