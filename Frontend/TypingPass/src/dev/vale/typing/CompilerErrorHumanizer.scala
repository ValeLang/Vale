package dev.vale.typing

import dev.vale.{FileCoordinate, FileCoordinateMap, RangeS, vimpl}
import dev.vale.postparsing.{CodeVarNameS, ConstructorNameS, FunctionNameS, INameS, IRuneS, IRuneTypeRuleError, ITemplataType, ImmConcreteDestructorNameS, ImmInterfaceDestructorNameS, LambdaDeclarationNameS, PostParserErrorHumanizer, RuneTypeSolveError, TopLevelCitizenDeclarationNameS}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.solver.{IIncompleteOrFailedSolve, SolverErrorHumanizer}
import dev.vale.typing.types.{BoolT, BorrowT, CoordT, FinalT, FloatT, ImmutableT, IntT, InterfaceTT, KindT, MutableT, NeverT, OverloadSetT, OwnT, OwnershipT, ParamFilter, RuntimeSizedArrayTT, ShareT, StaticSizedArrayTT, StrT, StructTT, VaryingT, VoidT, WeakT}
import dev.vale.SourceCodeUtils.{humanizePos, lineBegin, lineContaining, lineRangeContaining}
import dev.vale.highertyping.FunctionA
import PostParserErrorHumanizer.{humanizeImpreciseName, humanizeOwnership, humanizeRune, humanizeTemplataType}
import dev.vale.postparsing.rules.IRulexSR
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.solver.RuleError
import OverloadResolver.{FindFunctionFailure, IFindFunctionFailureReason, InferFailure, RuleTypeSolveFailure, SpecificParamDoesntMatchExactly, SpecificParamDoesntSend, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import dev.vale.highertyping.{FunctionA, HigherTypingErrorHumanizer}
import dev.vale.typing.ast.{AbstractT, FunctionBannerT, FunctionCalleeCandidate, HeaderCalleeCandidate, ICalleeCandidate, PrototypeT, SignatureT}
import dev.vale.typing.infer.{CallResultWasntExpectedType, CantShareMutable, CouldntFindFunction, ITypingPassSolverError, KindDoesntImplementInterface, KindIsNotConcrete, KindIsNotInterface, LookupFailed, NoAncestorsSatisfyCall, OneOfFailed, OwnershipDidntMatch, ReceivingDifferentOwnerships, SendingNonCitizen, SendingNonIdenticalKinds, WrongNumberOfTemplateArgs}
import dev.vale.typing.names.{AnonymousSubstructNameT, AnonymousSubstructTemplateNameT, CitizenNameT, CitizenTemplateNameT, CodeVarNameT, FullNameT, FunctionNameT, INameT, IVarNameT, LambdaCitizenNameT, LambdaCitizenTemplateNameT}
import dev.vale.typing.templata.{Conversions, CoordListTemplata, CoordTemplata, ITemplata, IntegerTemplata, InterfaceTemplata, KindTemplata, MutabilityTemplata, OwnershipTemplata, PrototypeTemplata, RuntimeSizedArrayTemplateTemplata, StaticSizedArrayTemplateTemplata, StringTemplata, StructTemplata, VariabilityTemplata}
import dev.vale.typing.ast._
import dev.vale.typing.infer.WrongNumberOfTemplateArgs
import dev.vale.typing.names.AnonymousSubstructNameT
import dev.vale.typing.templata.Conversions
import dev.vale.RangeS

object CompilerErrorHumanizer {
  def humanize(
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      err: ICompileErrorT):
  String = {
    val errorStrBody =
      err match {
        case RangedInternalErrorT(range, message) => { " " + message
        }
        case NewImmRSANeedsCallable(range) => {
          "To make an immutable runtime-sized array, need two params: capacity int, plus lambda to populate that many elements."
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
          "Cannot mutate final member '" + printableVarName(memberName.last) + "' of container " + humanizeTemplata(codeMap, KindTemplata(struct))
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
            candidateRanges.map(range => "\n" + humanizePos(codeMap, range.begin) + ":\n  " + lineContaining(codeMap, range.begin))
        }
        case ImmStructCantHaveVaryingMember(range, structName, memberName) => {
          "Immutable struct (\"" + printableName(codeMap, structName) + "\") cannot have varying member (\"" + memberName + "\")."
        }
        case WrongNumberOfDestructuresError(range, actualNum, expectedNum) => {
          "Wrong number of receivers; receiving " + actualNum + " but should be " + expectedNum + "."
        }
        case CantDowncastUnrelatedTypes(range, sourceKind, targetKind) => {
          "Can't downcast `" + sourceKind + "` to unrelated `" + targetKind + "`"
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
        case FunctionAlreadyExists(oldFunctionRange, newFunctionRange, signature) => {
          "Function " + humanizeSignature(codeMap, signature) + " already exists! Previous declaration at:\n" +
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
              failedSolve)
          text
        }
        case HigherTypingInferError(range, err) => {
          HigherTypingErrorHumanizer.humanize(codeMap, range, err)
        }
      }

    val posStr = humanizePos(codeMap, err.range.begin)
    val lineContents = lineContaining(codeMap, err.range.begin)
    val errorId = "T"
    f"${posStr} error ${errorId}\n${lineContents}\n${errorStrBody}\n"
  }

  def humanizeFindFunctionFailure(
    verbose: Boolean,
    codeMap: FileCoordinateMap[String],
    invocationRange: RangeS,
    fff: OverloadResolver.FindFunctionFailure): String = {

    val FindFunctionFailure(name, args, rejectedCalleeToReason) = fff
    "Couldn't find a suitable function " +
      PostParserErrorHumanizer.humanizeImpreciseName(name) +
      "(" +
      args.map({
        case ParamFilter(ownership, kind, Some(AbstractT())) => humanizeOwnership(ownership) + humanizeKind(codeMap, kind) + " abstract"
        case ParamFilter(ownership, kind, None) => humanizeOwnership(ownership) + humanizeKind(codeMap, kind)
      }).mkString(", ") +
      "). " +
      (if (rejectedCalleeToReason.isEmpty) {
        "No function with that name exists.\n"
      } else {
        "Rejected candidates:\n" +
        rejectedCalleeToReason.map({ case (candidate, reason) =>
            "\n" + humanizeCandidateAndRejectionReason(verbose, codeMap, invocationRange, candidate, reason) + "\n"
        }).mkString("")
      })
  }

  def humanizeBanner(
    codeMap: FileCoordinateMap[String],
    banner: FunctionBannerT):
  String = {
    banner.originFunction match {
      case None => "(internal)"
      case Some(x) => printableName(codeMap, x.name)
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
      case FunctionNameS(name, codeLocation) => humanizePos(codeMap, codeLocation) + ": " + name.str
      case ConstructorNameS(TopLevelCitizenDeclarationNameS(name, range)) => humanizePos(codeMap, range.begin) + ": " + name.str
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
  private def printableFullName(fullName2: FullNameT[INameT]): String = {
    fullName2.last match {
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

  private def humanizeCandidateAndRejectionReason(
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      invocationRange: RangeS,
      candidate: ICalleeCandidate,
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
        "\n" + humanizeCandidate(codeMap, candidate) + "\n" +
        "Number of params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case WrongNumberOfTemplateArguments(supplied, expected) => {
        "\n" + humanizeCandidate(codeMap, candidate) + "\n" +
        "Number of template params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case SpecificParamDoesntMatchExactly(index, paramFilter, param) => {
        "\n" + humanizeCandidate(codeMap, candidate) + "\n" +
          "Index " + index + " argument " + humanizeParamFilter(codeMap, paramFilter) +
          " isn't the same exact type as expected parameter " + humanizeTemplata(codeMap, CoordTemplata(param))
      }
      case SpecificParamDoesntSend(index, paramFilter, param) => {
        "\n" + humanizeCandidate(codeMap, candidate) + "\n" +
          " Index " + index + " argument " + humanizeParamFilter(codeMap, paramFilter) +
          " can't be given to expected parameter " + humanizeTemplata(codeMap, CoordTemplata(param))
      }
      case SpecificParamVirtualityDoesntMatch(index) => {
        "\n" + humanizeCandidate(codeMap, candidate) + "\n" +
        "Virtualities don't match at index " + index
      }
//      case Outscored() => "Outscored!"
      case InferFailure(reason) => {
        humanizeCandidateAndFailedSolve(codeMap, invocationRange, candidate, reason)
      }
    })
  }

  def humanizeRuleError(
    codeMap: FileCoordinateMap[String],
    error: ITypingPassSolverError
  ): String = {
    error match {
      case KindDoesntImplementInterface(sub, suuper) => {
        "Kind " + humanizeTemplata(codeMap, KindTemplata(sub)) + " does not implement interface " + humanizeTemplata(codeMap, KindTemplata(suuper))
      }
      case CantShareMutable(kind) => {
        "Can't share a mutable kind: " + humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case SendingNonIdenticalKinds(sendCoord, receiveCoord) => {
        "Sending non-identical kinds: " + humanizeTemplata(codeMap, CoordTemplata(sendCoord)) + " and " + humanizeTemplata(codeMap, CoordTemplata(receiveCoord))
      }
      case SendingNonCitizen(kind) => {
        "Sending non-struct non-interface Kind: " + humanizeTemplata(codeMap, KindTemplata(kind))
      }
      case CouldntFindFunction(range, fff) => {
        "Couldn't find function to call: " + humanizeFindFunctionFailure(false, codeMap, range, fff)
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
        "Given type " + humanizeTemplata(codeMap, CoordTemplata(coord)) + " doesn't have expected ownership " + humanizeOwnership(expectedOwnership)
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
    invocationRange: RangeS,
    candidate: ICalleeCandidate,
    result: IIncompleteOrFailedSolve[IRulexSR, IRuneS, ITemplata, ITypingPassSolverError]):
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

    (candidate match {
      case HeaderCalleeCandidate(header) => humanizeName(codeMap, header.fullName)
      case FunctionCalleeCandidate(ft) => {
//        if (ft.function.range.file.isInternal) {
//          ScoutErrorHumanizer.humanizeName(ft.function.name) + " (builtin " + ft.function.range.begin.offset + ")\n"
//        } else {
          val begin = lineBegin(codeMap, ft.function.range.begin)
          humanizePos(codeMap, begin) + ":\n" +
            (if (lineBegins.contains(begin)) {
              ""
            } else {
              lineContaining(codeMap, begin) + "\n"
            })
//        }
      }
    }) + text
  }

  def humanizeCandidate(codeMap: FileCoordinateMap[String], candidate: ICalleeCandidate) = {
    candidate match {
      case HeaderCalleeCandidate(header) => humanizeName(codeMap, header.fullName)
      case FunctionCalleeCandidate(ft) => {
        val begin = lineBegin(codeMap, ft.function.range.begin)
        humanizePos(codeMap, begin) + ":\n" +
        lineContaining(codeMap, begin) + "\n"
      }
    }
  }

  def humanizeParamFilter(
    codeMap: FileCoordinateMap[String],
    paramFilter: ParamFilter):
  String = {
    paramFilter.virtuality.map(_.toString + " ").getOrElse("") +
      humanizeOwnership(paramFilter.ownership) +
      humanizeKind(codeMap, paramFilter.kind)
  }

  def humanizeTemplata(
    codeMap: FileCoordinateMap[String],
    templata: ITemplata):
  String = {
    templata match {
      case RuntimeSizedArrayTemplateTemplata() => "Array"
      case StaticSizedArrayTemplateTemplata() => "StaticArray"
      case InterfaceTemplata(env, originInterface) => originInterface.name.name.str
      case StructTemplata(env, originStruct) => PostParserErrorHumanizer.humanizeName(originStruct.name)
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
      case PrototypeTemplata(PrototypeT(name, returnType)) => {
        humanizeName(codeMap, name)
      }
      case CoordTemplata(CoordT(ownership, kind)) => {
        humanizeOwnership(ownership) + humanizeKind(codeMap, kind)
      }
      case KindTemplata(kind) => {
        humanizeKind(codeMap, kind)
      }
      case CoordListTemplata(coords) => {
        "(" + coords.map(CoordTemplata).map(humanizeTemplata(codeMap, _)).mkString(", ") + ")"
      }
      case StringTemplata(value) => "\"" + value + "\""
      case other => vimpl(other)
    }
  }

  def humanizeName[T <: INameT](
    codeMap: FileCoordinateMap[String],
    name: FullNameT[T]):
  String = {
    humanizeName(codeMap, name.last)
  }

  def humanizeName(
    codeMap: FileCoordinateMap[String],
    name: INameT):
  String = {
    name match {
      case LambdaCitizenTemplateNameT(codeLocation) => {
        "λ:" + humanizePos(codeMap, codeLocation)
      }
      case CodeVarNameT(name) => name.str
      case LambdaCitizenNameT(template) => humanizeName(codeMap, template) + "<>"
      case FunctionNameT(humanName, templateArgs, parameters) => {
        humanName +
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
      case CitizenTemplateNameT(humanName) => humanName.str
    }
  }

  def humanizeSignature(codeMap: FileCoordinateMap[String], signature: SignatureT): String = {
    humanizeName(codeMap, signature.fullName)
  }

  def humanizeOwnership(p: OwnershipT) = {
    p match {
      case OwnT => "^"
      case ShareT => "@"
      case BorrowT => "&"
      case WeakT => "&&"
    }
  }

  def humanizeKind(codeMap: FileCoordinateMap[String], kind: KindT): String = {
    kind match {
      case IntT(bits) => "i" + bits
      case BoolT() => "bool"
      case StrT() => "str"
      case NeverT(_) => "never"
      case VoidT() => "void"
      case FloatT() => "float"
      case OverloadSetT(_, name) => "(overloads: " + PostParserErrorHumanizer.humanizeImpreciseName(name) + ")"
      case InterfaceTT(name) => humanizeName(codeMap, name)
      case StructTT(name) => humanizeName(codeMap, name)
      case RuntimeSizedArrayTT(mutability, elementType) => {
        "Array<" +
          humanizeTemplata(codeMap, MutabilityTemplata(mutability)) + ", " +
          humanizeTemplata(codeMap, CoordTemplata(elementType)) + ">"
      }
      case StaticSizedArrayTT(size, mutability, variability, elementType) => {
        "StaticArray<" +
          humanizeTemplata(codeMap, IntegerTemplata(size)) + ", " +
          humanizeTemplata(codeMap, MutabilityTemplata(mutability)) + ", " +
          humanizeTemplata(codeMap, VariabilityTemplata(variability)) + ", " +
          humanizeTemplata(codeMap, CoordTemplata(elementType)) + ">"
      }
    }
  }
}
