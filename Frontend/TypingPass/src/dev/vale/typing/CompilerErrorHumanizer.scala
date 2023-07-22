package dev.vale.typing

import dev.vale._
import dev.vale.postparsing._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{FailedSolve, IIncompleteOrFailedSolve, IncompleteSolve, RuleError, SolverErrorHumanizer}
import dev.vale.typing.types._
import dev.vale.SourceCodeUtils.{humanizePos, lineBegin, lineContaining, lineRangeContaining, linesBetween}
import dev.vale.highertyping.FunctionA
import PostParserErrorHumanizer._
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.PostParserErrorHumanizer
import OverloadResolver._
import dev.vale.highertyping.{FunctionA, HigherTypingErrorHumanizer}
import dev.vale.typing.ast.{AbstractT, FunctionBannerT, FunctionCalleeCandidate, HeaderCalleeCandidate, ICalleeCandidate, PrototypeT, SignatureT}
import dev.vale.typing.infer.{BadIsaSubKind, BadIsaSuperKind, CallResultWasntExpectedType, CantCheckPlaceholder, CantGetComponentsOfPlaceholderPrototype, CantShareMutable, CouldntFindFunction, CouldntResolveKind, ITypingPassSolverError, IsaFailed, KindIsNotConcrete, KindIsNotInterface, LookupFailed, NoAncestorsSatisfyCall, OneOfFailed, OwnershipDidntMatch, ReceivingDifferentOwnerships, ReturnTypeConflict, SendingNonCitizen, SendingNonIdenticalKinds, WrongNumberOfTemplateArgs}
import dev.vale.typing.names._
import dev.vale.typing.templata._
import dev.vale.typing.ast._
import dev.vale.typing.templata.Conversions
import dev.vale.typing.types.CoordT
import dev.vale.typing.citizen.ResolveFailure

object CompilerErrorHumanizer {
  def humanize(
      verbose: Boolean,
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
      err: ICompileErrorT):
  String = {
    val errorStrBody =
      err match {
        case RangedInternalErrorT(range, message) => {
          "Internal error: " + message
        }
        case CouldntFindOverrideT(range, fff) => {
          "Couldn't find an override:\n" +
            humanizeFindFunctionFailure(verbose, codeMap, linesBetween, lineRangeContaining, lineContaining, range, fff)
        }
        case NewImmRSANeedsCallable(range) => {
          "To make an immutable runtime-sized array, need two params: capacity int, plus lambda to populate that many elements."
        }
        case CouldntSolveRuneTypesT(range, error) => {
          "Couldn't solve rune types:\n" +
            HigherTypingErrorHumanizer.humanizeRuneTypeSolveError(
              codeMap, linesBetween, lineRangeContaining, lineContaining, error)
        }
        case UnexpectedArrayElementType(range, expectedType, actualType) => {
          "Unexpected type for array element, tried to put a " + humanizeTemplata(codeMap, CoordTemplataT(actualType)) + " into an array of " + humanizeTemplata(codeMap, CoordTemplataT(expectedType))
        }
        case IndexedArrayWithNonInteger(range, tyype) => {
          "Indexed array with non-integer: " + humanizeTemplata(codeMap, CoordTemplataT(tyype))
        }
        case CantUseReadonlyReferenceAsReadwrite(range) => {
          "Can't make readonly reference into a readwrite one!"
        }
        case CouldntFindOverrideT(range, fff) => {
          "Couldn't find override: " + humanizeFindFunctionFailure(verbose, codeMap, linesBetween, lineRangeContaining, lineContaining, range, fff)
        }
        case CantReconcileBranchesResults(range, thenResult, elseResult) => {
          "If branches return different types: " + humanizeTemplata(codeMap, CoordTemplataT(thenResult)) + " and " + humanizeTemplata(codeMap, CoordTemplataT(elseResult))
        }
        case CantMoveOutOfMemberT(range, name) => {
          "Cannot move out of member (" + name + ")"
        }
        case CantMutateFinalMember(range, struct, memberName) => {
          "Cannot mutate final member '" + printableVarName(memberName) + "' of container " + humanizeTemplata(codeMap, KindTemplataT(struct))
        }
        case CantMutateFinalElement(range, coord) => {
          "Cannot change a slot in array " + humanizeTemplata(codeMap, CoordTemplataT(coord)) + " to point to a different element; it's an array of final references."
        }
        case LambdaReturnDoesntMatchInterfaceConstructor(range) => {
          "Argument function return type doesn't match interface method param"
        }
        case CantUseUnstackifiedLocal(range, name) => {
          "Can't use local that was already moved: " + humanizeName(codeMap, name)
        }
        case CantUnstackifyOutsideLocalFromInsideWhile(range, name) => {
          "Can't move a local (" + name + ") from inside a while loop."
        }
        case CannotSubscriptT(range, tyype) => {
          "Cannot subscript type: " + humanizeTemplata(codeMap, KindTemplataT(tyype)) + "!"
        }
        case CouldntConvertForReturnT(range, expectedType, actualType) => {
          "Couldn't convert " + humanizeTemplata(codeMap, CoordTemplataT(actualType)) + " to expected return type " + humanizeTemplata(codeMap, CoordTemplataT(expectedType))
        }
        case CouldntConvertForMutateT(range, expectedType, actualType) => {
          "Mutate couldn't convert " + actualType + " to expected destination type " + expectedType
        }
        case CouldntFindMemberT(range, memberName) => {
          "Couldn't find member " + memberName + "!"
        }
        case CouldntEvaluatImpl(range, eff) => {
          "Couldn't evaluate impl statement:\n" +
            humanizeCandidateAndFailedSolve(codeMap, linesBetween, lineRangeContaining, lineContaining, eff match {
              case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
                IncompleteSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
                  steps, unsolvedRules, unknownRunes, incompleteConclusions)
              }
              case FailedCompilerSolve(steps, unsolvedRules, error) => {
                FailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
                  steps, unsolvedRules, error)
              }
            })
      }
        case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
          "Function " + printableName(codeMap, functionName) + " return type " + humanizeTemplata(codeMap, CoordTemplataT(expectedReturnType)) + " doesn't match body's result: " + humanizeTemplata(codeMap, CoordTemplataT(resultType))
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
            candidateRanges.map(range => "\n" + codeMap(range.begin) + ":\n  " + lineContaining(range.begin)).mkString("")
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
          "Can't downcast `" + humanizeTemplata(codeMap, KindTemplataT(sourceKind)) + "` to unrelated `" + humanizeTemplata(codeMap, KindTemplataT(targetKind)) + "`"
        }
        case CantDowncastToInterface(range, targetKind) => {
          "Can't downcast to an interface (" + targetKind + ") yet."
        }
        case ArrayElementsHaveDifferentTypes(range, types) => {
          "Array's elements have different types: " + types.mkString(", ")
        }
        case ExportedFunctionDependedOnNonExportedKind(range, paackage, signature, nonExportedKind) => {
          "Exported function:\n" + humanizeSignature(codeMap, signature) + "\ndepends on kind:\n" + humanizeTemplata(codeMap, KindTemplataT(nonExportedKind)) + "\nthat wasn't exported from package " + SourceCodeUtils.humanizePackage(paackage)
        }
        case TypeExportedMultipleTimes(range, paackage, exports) => {
          "Type exported multiple times:" + exports.map(export => {
            val posStr = codeMap(export.range.begin)
            val line = lineContaining(export.range.begin)
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
          humanizeFindFunctionFailure(verbose, codeMap, linesBetween, lineRangeContaining, lineContaining, range, fff)
        }
        case CouldntEvaluateFunction(range, eff) => {
          "Couldn't evaluate function:\n" +
          humanizeRejectionReason(
            verbose, codeMap, linesBetween, lineRangeContaining, lineContaining, range, eff)
        }
        case FunctionAlreadyExists(oldFunctionRange, newFunctionRange, signature) => {
          "Function " + humanizeId(codeMap, signature) + " already exists! Previous declaration at:\n" +
            codeMap(oldFunctionRange.begin)
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
            SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
              codeMap,
              linesBetween,
              lineRangeContaining,
              lineContaining,
              humanizeRune,
              t => humanizeTemplata(codeMap, t),
              err => humanizeRuleError(codeMap, linesBetween, lineRangeContaining, lineContaining, err),
              (rule: IRulexSR) => rule.range,
              (rule: IRulexSR) => rule.runeUsages.map(usage => (usage.rune, usage.range)),
              (rule: IRulexSR) => rule.runeUsages.map(_.rune),
              PostParserErrorHumanizer.humanizeRule,
              failedSolve match {
                case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
                  IncompleteSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
                    steps, unsolvedRules, unknownRunes, incompleteConclusions)
                }
                case FailedCompilerSolve(steps, unsolvedRules, error) => {
                  FailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
                    steps, unsolvedRules, error)
                }
              })
          text
        }
        case HigherTypingInferError(range, err) => {
          HigherTypingErrorHumanizer.humanizeRuneTypeSolveError(
            codeMap, linesBetween, lineRangeContaining, lineContaining, err)
        }
      }

//    val errorId = "T"
    err.range.reverse.map(range => {
      val posStr = codeMap(range.begin)
      val lineContents = lineContaining(range.begin)
      f"At ${posStr}:\n${lineContents}\n"
    }).mkString("") +
    errorStrBody + "\n"
  }

  def humanizeResolveFailure(
    verbose: Boolean,
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    fff: ResolveFailure[KindT]):
  String = {
    val ResolveFailure(range, reason) = fff
    humanizeCandidateAndFailedSolve(codeMap, linesBetween, lineRangeContaining, lineContaining, reason match {
      case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
        IncompleteSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
          steps, unsolvedRules, unknownRunes, incompleteConclusions)
      }
      case FailedCompilerSolve(steps, unsolvedRules, error) => {
        FailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
          steps, unsolvedRules, error)
      }
    })
  }

  def humanizeFindFunctionFailure(
    verbose: Boolean,
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    invocationRange: List[RangeS],
    fff: OverloadResolver.FindFunctionFailure): String = {

    val FindFunctionFailure(name, args, rejectedCalleeToReason) = fff
    "Couldn't find a suitable function " +
      PostParserErrorHumanizer.humanizeImpreciseName(name) +
      "(" +
      args.map({
        case tyype => humanizeTemplata(codeMap, CoordTemplataT(tyype))
      }).mkString(", ") +
      "). " +
      (if (rejectedCalleeToReason.isEmpty) {
        "No function with that name exists.\n"
      } else {
        "Rejected candidates:\n\n" +
        rejectedCalleeToReason.zipWithIndex.map({ case ((candidate, reason), index) =>
          "Candidate " + (index + 1) + " (of " + rejectedCalleeToReason.size + "): " +
            humanizeCandidate(codeMap, lineRangeContaining, candidate) +
            humanizeRejectionReason(verbose, codeMap, linesBetween, lineRangeContaining, lineContaining, invocationRange, reason) + "\n\n"
        }).mkString("")
      })
  }

  def humanizeBanner(
    codeMap: CodeLocationS => String,
    banner: FunctionBannerT):
  String = {
    banner.originFunctionTemplata match {
      case None => "(internal)"
      case Some(x) => printableName(codeMap, x.function.name)
    }
  }

  private def printableName(
    codeMap: CodeLocationS => String,
    name: INameS):
  String = {
    name match {
      case CodeVarNameS(name) => name.str
      case TopLevelCitizenDeclarationNameS(name, codeLocation) => name.str
      case LambdaDeclarationNameS(codeLocation) => codeMap(codeLocation) + ": " + "(lambda)"
      case FunctionNameS(name, codeLocation) => codeMap(codeLocation) + ": " + name.str
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, range)) => codeMap(range.begin) + ": " + name.str
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
      case StructTT(f) => printableId(f)
    }
  }
  private def printableId(id: IdT[INameT]): String = {
    id.localName match {
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
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
      invocationrange: List[RangeS],
      reason: IFindFunctionFailureReason): String = {

    (reason match {
      case RuleTypeSolveFailure(RuneTypeSolveError(range, failedSolve)) => {
        SolverErrorHumanizer.humanizeFailedSolve(
          codeMap,
          linesBetween,
          lineRangeContaining,
          lineContaining,
          humanizeRune,
          (a: ITemplataType) => humanizeTemplataType(a),
          (a: IRuneTypeRuleError) => PostParserErrorHumanizer.humanizeRuneTypeError(codeMap, a),
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
          "Index " + index + " argument " + humanizeTemplata(codeMap, CoordTemplataT(arg)) +
          " isn't the same exact type as expected parameter " + humanizeTemplata(codeMap, CoordTemplataT(param))
      }
      case SpecificParamDoesntSend(index, arg, param) => {
          " Index " + index + " argument " + humanizeTemplata(codeMap, CoordTemplataT(arg)) +
          " can't be given to expected parameter " + humanizeTemplata(codeMap, CoordTemplataT(param))
      }
      case SpecificParamRegionDoesntMatch(rune, suppliedMutable, expectedMutable) => {
        " Generic param " + humanizeRune(rune) + " expected a " + expectedMutable + " region, but received a " + suppliedMutable + " region."
      }
      case SpecificParamVirtualityDoesntMatch(index) => {
        "Virtualities don't match at index " + index
      }
//      case Outscored() => "Outscored!"
      case InferFailure(reason) => {
        humanizeCandidateAndFailedSolve(codeMap, linesBetween, lineRangeContaining, lineContaining, reason match {
          case IncompleteCompilerSolve(steps, unsolvedRules, unknownRunes, incompleteConclusions) => {
            IncompleteSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
              steps, unsolvedRules, unknownRunes, incompleteConclusions)
          }
          case FailedCompilerSolve(steps, unsolvedRules, error) => {
            FailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
              steps, unsolvedRules, error)
          }
        })
      }
    })
  }

  def humanizeRuleError(
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    error: ITypingPassSolverError
  ): String = {
    error match {
      case IsaFailed(sub, suuper) => {
        "Kind " + humanizeTemplata(codeMap, KindTemplataT(sub)) + " does not implement interface " + humanizeTemplata(codeMap, KindTemplataT(suuper))
      }
      case BadIsaSubKind(kind) => {
        "Kind " + humanizeTemplata(codeMap, KindTemplataT(kind)) + " cannot be a sub-kind."
      }
      case CantGetComponentsOfPlaceholderPrototype(_) => {
        "Can't get components of placeholder."
      }
      case ReturnTypeConflict(_, expectedReturnType, actualPrototype) => {
        "Found function: " + humanizeId(codeMap, actualPrototype.id) + " which returns " + humanizeTemplata(codeMap, CoordTemplataT(actualPrototype.returnType)) + " but expected return type of " + humanizeTemplata(codeMap, CoordTemplataT(expectedReturnType))
      }
      case CantShareMutable(kind) => {
        "Can't share a mutable kind: " + humanizeTemplata(codeMap, KindTemplataT(kind))
      }
      case BadIsaSuperKind(kind) => {
        "Bad super kind in isa: " + humanizeTemplata(codeMap, KindTemplataT(kind))
      }
      case SendingNonIdenticalKinds(sendCoord, receiveCoord) => {
        "Sending non-identical kinds: " + humanizeTemplata(codeMap, CoordTemplataT(sendCoord)) + " and " + humanizeTemplata(codeMap, CoordTemplataT(receiveCoord))
      }
      case SendingNonCitizen(kind) => {
        "Sending non-struct non-interface Kind: " + humanizeTemplata(codeMap, KindTemplataT(kind))
      }
      case CantCheckPlaceholder(range) => {
        "Cant check a placeholder!"
      }
      case CouldntFindFunction(range, fff) => {
        "Couldn't find function to call: " +
          humanizeFindFunctionFailure(
            false, codeMap, linesBetween, lineRangeContaining, lineContaining, range, fff)
      }
      case CouldntResolveKind(rf) => {
        "Couldn't find type: " + humanizeResolveFailure(false, codeMap, linesBetween, lineRangeContaining, lineContaining, rf)
      }
      case WrongNumberOfTemplateArgs(expectedMinNumArgs, expectedMaxNumArgs) => {
        if (expectedMinNumArgs == expectedMaxNumArgs) {
          "Wrong number of template args, expected " + expectedMinNumArgs + "."
        } else {
          "Wrong number of template args, expected " + expectedMinNumArgs + " or " + expectedMaxNumArgs + "."
        }
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
        "Given type " + humanizeTemplata(codeMap, CoordTemplataT(coord)) + " doesn't have expected ownership " + humanizeOwnership(Conversions.unevaluateOwnership(expectedOwnership))
      }
      case ReceivingDifferentOwnerships(params) => {
        "Received conflicting ownerships: " +
          params.map({ case (rune, coord) =>
            humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplataT(coord))
          }).mkString(", ")
      }
      case NoAncestorsSatisfyCall(params) => {
        "No ancestors satisfy call: " +
          params.map({ case (rune, coord) =>
            humanizeRune(rune) + " = " + humanizeTemplata(codeMap, CoordTemplataT(coord))
          }).mkString(", ")
      }
    }
  }

  def humanizeCandidateAndFailedSolve(
    codeMap: CodeLocationS => String,
    linesBetween: (CodeLocationS, CodeLocationS) => Vector[RangeS],
    lineRangeContaining: (CodeLocationS) => RangeS,
    lineContaining: (CodeLocationS) => String,
    result: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError]):
  String = {
    val (text, lineBegins) =
      SolverErrorHumanizer.humanizeFailedSolve[IRulexSR, IRuneS, ITemplataT[ITemplataType], ITypingPassSolverError](
        codeMap,
        linesBetween,
        lineRangeContaining,
        lineContaining,
        humanizeRune,
        t => humanizeTemplata(codeMap, t),
        err => humanizeRuleError(codeMap, linesBetween, lineRangeContaining, lineContaining, err),
        (rule: IRulexSR) => rule.range,
        (rule: IRulexSR) => rule.runeUsages.map(usage => (usage.rune, usage.range)),
        (rule: IRulexSR) => rule.runeUsages.map(_.rune),
        PostParserErrorHumanizer.humanizeRule,
        result)
    text
  }

  def humanizeCandidate(
    codeMap: CodeLocationS => String,
    lineRangeContaining: (CodeLocationS) => RangeS,
    candidate: ICalleeCandidate) = {
    candidate match {
      case HeaderCalleeCandidate(header) => {
        humanizeId(codeMap, header.id)
      }
      case PrototypeTemplataCalleeCandidate(range, prototypeT) => {
        val begin = lineRangeContaining(range.begin).begin
        codeMap(begin) + ":\n" +
          lineRangeContaining(begin).begin + "\n"
      }
      case FunctionCalleeCandidate(ft) => {
        val begin = lineRangeContaining(ft.function.range.begin).begin
        codeMap(begin) + ":\n" +
          lineRangeContaining(begin).begin + "\n"
      }
    }
  }

  def humanizeTemplata(
    codeMap: CodeLocationS => String,
    templata: ITemplataT[ITemplataType]):
  String = {
    templata match {
      case RuntimeSizedArrayTemplateTemplataT() => "Array"
      case StaticSizedArrayTemplateTemplataT() => "StaticArray"
      case InterfaceDefinitionTemplataT(env, originInterface) => originInterface.name.name.str
      case StructDefinitionTemplataT(env, originStruct) => PostParserErrorHumanizer.humanizeName(originStruct.name)
      case VariabilityTemplataT(variability) => {
        variability match {
          case FinalT => "final"
          case VaryingT => "vary"
        }
      }
      case IntegerTemplataT(value) => value.toString
      case MutabilityTemplataT(mutability) => {
        mutability match {
          case MutableT => "mut"
          case ImmutableT => "imm"
        }
      }
      case OwnershipTemplataT(ownership) => {
        ownership match {
          case OwnT => "own"
          case BorrowT => "borrow"
          case WeakT => "weak"
          case ShareT => "share"
        }
      }
      case PrototypeTemplataT(range, prototype) => {
        humanizeId(codeMap, prototype.id)
      }
      case CoordTemplataT(coord) => {
        humanizeCoord(codeMap, coord)
      }
      case KindTemplataT(kind) => {
        humanizeKind(codeMap, kind)
      }
      case CoordListTemplataT(coords) => {
        "(" + coords.map(CoordTemplataT).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case StringTemplataT(value) => "\"" + value + "\""
      case PlaceholderTemplataT(id, tyype) => {
        tyype match {
          case CoordTemplataType() => "$" + humanizeId(codeMap, id)
          case _ => humanizeTemplataType(tyype) + "$" + humanizeId(codeMap, id)
        }
      }
      case other => vimpl(other)
    }
  }

  private def humanizeCoord(
      codeMap: CodeLocationS => String,
      coord: CoordT
  ) = {
    val CoordT(ownership, region, kind) = coord

    val ownershipStr =
      ownership match {
        case OwnT => ""
        case ShareT => ""
        case BorrowT => "&"
        case WeakT => "&&"
      }
    val kindStr = humanizeKind(codeMap, kind, Some(region))
    ownershipStr + kindStr
  }

  private def humanizeKind(
      codeMap: CodeLocationS => String,
      kind: KindT,
      containingRegion: Option[RegionT] = None
  ) = {
    kind match {
      case IntT(bits) => "i" + bits
      case BoolT() => "bool"
      case KindPlaceholderT(name) => "Kind$" + humanizeId(codeMap, name)
      case StrT() => "str"
      case NeverT(_) => "never"
      case VoidT() => "void"
      case FloatT() => "float"
      case OverloadSetT(_, name) => {
        "(overloads: " +
            PostParserErrorHumanizer.humanizeImpreciseName(name) +
            ")"
      }
      case InterfaceTT(name) => humanizeId(codeMap, name, containingRegion)
      case StructTT(name) => humanizeId(codeMap, name, containingRegion)
      case contentsRuntimeSizedArrayTT(mutability, elementType, region) => {
        "Array<" +
            humanizeTemplata(codeMap, mutability) + ", " +
            humanizeTemplata(codeMap, CoordTemplataT(elementType)) +
            ">"
      }
      case contentsStaticSizedArrayTT(size, mutability, variability, elementType, region) => {
        //        humanizeTemplata(codeMap, region) + "'" +
        "StaticArray<" +
            humanizeTemplata(codeMap, size) + ", " +
            humanizeTemplata(codeMap, mutability) + ", " +
            humanizeTemplata(codeMap, variability) + ", " +
            humanizeTemplata(codeMap, CoordTemplataT(elementType)) +
            ">"
      }
    }
  }

  def humanizeId[T <: INameT](
    codeMap: CodeLocationS => String,
    name: IdT[T],
    containingRegion: Option[RegionT] = None):
  String = {
    (if (name.initSteps.nonEmpty) {
      name.initSteps.map(n => humanizeName(codeMap, n)).mkString(".") + "."
    } else {
      ""
    }) +
      humanizeName(codeMap, name.localName, containingRegion)
  }

  def humanizeName(
    codeMap: CodeLocationS => String,
    name: INameT,
    containingRegion: Option[RegionT] = None):
  String = {
    name match {
      case AnonymousSubstructConstructorNameT(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          "<" + templateArgs.map(humanizeTemplata(codeMap, _)).mkString(", ") + ">" +
          "(" + parameters.map(CoordTemplataT).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case AnonymousSubstructConstructorTemplateNameT(substruct) => {
        "asc:" + humanizeName(codeMap, substruct)
      }
      case SelfNameT() => "self"
      case ImplTemplateNameT(codeLoc) => "implt:" + codeMap(codeLoc)
      case OverrideDispatcherTemplateNameT(implId) => "ovdt:" + humanizeId(codeMap, implId)
      case IteratorNameT(range) => "it:" + codeMap(range.begin)
      case IterableNameT(range) => "ib:" + codeMap(range.begin)
      case IterationOptionNameT(range) => "io:" + codeMap(range.begin)
      case ForwarderFunctionNameT(_, inner) => humanizeName(codeMap, inner)
      case ForwarderFunctionTemplateNameT(inner, index) => "fwd" + index + ":" + humanizeName(codeMap, inner)
      case MagicParamNameT(codeLoc) => "mp:" + codeMap(codeLoc)
      case ClosureParamNameT(codeLocation) => "λP:" + codeMap(codeLocation)
      case ConstructingMemberNameT(name) => "cm:" + name
      case TypingPassBlockResultVarNameT(life) => "b:" + life
      case TypingPassFunctionResultVarNameT() => "(result)"
      case TypingPassTemporaryVarNameT(life) => "t:" + life
      case FunctionBoundTemplateNameT(humanName, codeLocation) => humanName.str
      case LambdaCallFunctionTemplateNameT(codeLocation, _) => "λF:" + codeMap(codeLocation)
      case LambdaCitizenTemplateNameT(codeLocation) => "λC:" + codeMap(codeLocation)
      case LambdaCallFunctionNameT(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          humanizeGenericArgs(codeMap, templateArgs, None) +
          "(" + parameters.map(CoordTemplataT).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case FunctionBoundNameT(template, templateArgs, parameters) => {
        humanizeName(codeMap, template) +
          humanizeGenericArgs(codeMap, templateArgs, None) +
          "(" + parameters.map(CoordTemplataT).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case KindPlaceholderNameT(template) => humanizeName(codeMap, template)
      case KindPlaceholderTemplateNameT(index, rune) => humanizeRune(rune)
      case CodeVarNameT(name) => name.str
      case LambdaCitizenNameT(template) => humanizeName(codeMap, template) + "<>"
      case FunctionTemplateNameT(humanName, codeLoc) => humanName.str
      case ExternFunctionNameT(humanName, parameters) => humanName.str
      case FunctionNameT(templateName, templateArgs, parameters) => {
        humanizeName(codeMap, templateName) +
          humanizeGenericArgs(codeMap, templateArgs, containingRegion) +
          (if (parameters.nonEmpty) {
            "(" + parameters.map(CoordTemplataT).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
          } else {
            ""
          })
      }
      case CitizenNameT(humanName, templateArgs) => {
        humanizeName(codeMap, humanName) +
          humanizeGenericArgs(codeMap, templateArgs, containingRegion)
      }
      case RuntimeSizedArrayNameT(RuntimeSizedArrayTemplateNameT(), RawArrayNameT(mutability, elementType, region)) => {
        "[]<" +
          (mutability match {
            case MutabilityTemplataT(ImmutableT) => "imm"
            case MutabilityTemplataT(MutableT) => "mut"
            case other => humanizeTemplata(codeMap, other)
          }) + ", " +
          humanizeTemplata(codeMap, CoordTemplataT(elementType))
      }
      case StaticSizedArrayNameT(StaticSizedArrayTemplateNameT(), size, variability, RawArrayNameT(mutability, elementType, region)) => {
        "[]<" +
          humanizeTemplata(codeMap, size) + ", "
          humanizeTemplata(codeMap, mutability) + ", "
          humanizeTemplata(codeMap, variability) + ">"
          humanizeTemplata(codeMap, CoordTemplataT(elementType))
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

  private def humanizeGenericArgs(
    codeMap: CodeLocationS => String,
    templateArgs: Vector[ITemplataT[ITemplataType]],
    containingRegion: Option[RegionT]
  ) = {
    (
      if (templateArgs.nonEmpty) {
        "<" +
          (templateArgs.init.map(humanizeTemplata(codeMap, _)) ++
              templateArgs.lastOption.map(region => {
                containingRegion match {
                  case None => humanizeTemplata(codeMap, region)
                  case Some(r) => "_"
                }
              })).mkString(", ") +
          ">"
      } else {
        ""
      })
  }

  def humanizeSignature(codeMap: CodeLocationS => String, signature: SignatureT): String = {
    humanizeId(codeMap, signature.id)
  }
}
