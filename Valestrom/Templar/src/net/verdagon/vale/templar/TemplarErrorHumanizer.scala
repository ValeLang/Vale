package net.verdagon.vale.templar

import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining}
import net.verdagon.vale.astronomer.{AstronomerErrorHumanizer, ConstructorNameA, FunctionA, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, ImmConcreteDestructorNameA, ImmDropNameA, ImmInterfaceDestructorNameA, LambdaNameA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, InferFailure, Outscored, ScoutExpectedFunctionFailure, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.infer.infer.{IConflictCause, InferSolveFailure}
import net.verdagon.vale.templar.templata.{CoordTemplata, FunctionBanner2, IPotentialBanner}
import net.verdagon.vale.templar.types.{Bool2, Borrow, Coord, Float2, Int2, Kind, Own, ParamFilter, Readonly, Readwrite, Share, Str2, StructRef2, Weak}
import net.verdagon.vale.vimpl

object TemplarErrorHumanizer {
  def humanize(
      verbose: Boolean,
      filenamesAndSources: List[(String, String)],
      err: ICompileErrorT):
  String = {
    err match {
      case RangedInternalErrorT(range, message) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) + " " + message
      }
      case CantUseReadonlyReferenceAsReadwrite(range) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Can't make readonly reference into a readwrite one!"
      }
      case CantMoveOutOfMemberT(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Cannot move out of member (" + name + ")"
      }
      case CantMutateFinalMember(range, structRef2, memberName) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Cannot mutate final member '" + printableVarName(memberName.last) + "' of struct " + printableKindName(structRef2)
      }
      case CantMutateFinalLocal(range, localName) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Cannot mutate final local '" + localName
      }
      case LambdaReturnDoesntMatchInterfaceConstructor(range) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Argument function return type doesn't match interface method param"
      }
      case CantUseUnstackifiedLocal(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Can't use local that was already moved (" + name + ")"
      }
      case CannotSubscriptT(range, tyype) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Cannot subscript type: " + tyype + "!"
      }
      case CouldntConvertForReturnT(range, expectedType, actualType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't convert " + actualType + " to expected return type " + expectedType
      }
      case CouldntConvertForMutateT(range, expectedType, actualType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Mutate couldn't convert " + actualType + " to expected destination type " + expectedType
      }
      case CouldntFindMemberT(range, memberName) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find member " + memberName + "!"
      }
      case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Function " + printableName(filenamesAndSources, functionName) + " return type " + expectedReturnType + " doesn't match body's result: " + resultType
      }
      case CouldntFindIdentifierToLoadT(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find anything named `" + name + "`!"
      }
      case NonReadonlyReferenceFoundInPureFunctionParameter(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Parameter `" + name + "` should be readonly, because it's in a pure function."
      }
      case CouldntFindTypeT(range, name) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find any type named `" + name + "`!"
      }
      case CouldntFindFunctionToCallT(range, ScoutExpectedFunctionFailure(name, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find a suitable function named `" +
          (name match {
            case GlobalFunctionFamilyNameA(humanName) => humanName
            case other => other.toString
          }) +
          "` with args (" +
          (if (args.collectFirst({ case ParamFilter(tyype, Some(_)) => }).nonEmpty) {
            vimpl()
          } else {
            args.map({ case ParamFilter(tyype, None) => TemplataNamer.getReferenceIdentifierName(tyype) }).mkString(", ")
          }) +
          "):\n" + lineContaining(filenamesAndSources, range.file, range.end.offset) + "\n" +
          (if (outscoredReasonByPotentialBanner.size + rejectedReasonByBanner.size + rejectedReasonByFunction.size == 0) {
            "No function with that name exists.\nPerhaps you forget to include a file in the command line?\n"
          } else {
            (if (outscoredReasonByPotentialBanner.nonEmpty) {
              "Outscored candidates:\n" + outscoredReasonByPotentialBanner.map({
                case (potentialBanner, outscoredReason) => {
                  "  " + TemplataNamer.getFullNameIdentifierName(potentialBanner.banner.fullName) + ":\n" +
                    humanizeRejectionReason(
                      2,
                      verbose,
                      filenamesAndSources,
                      outscoredReason)
                }
              }).mkString("\n")
            } else {
              ""
            }) + "\n" +
              (if (rejectedReasonByBanner.size + rejectedReasonByFunction.size > 0) {
                "Rejected candidates:\n" +
                  (rejectedReasonByBanner.map({
                    case (banner, rejectedReason) => {
                      "  " + humanizeBanner(filenamesAndSources, banner) + "\n" +
                        humanizeRejectionReason(2, verbose, filenamesAndSources, rejectedReason)
                    }
                  }) ++
                    rejectedReasonByFunction.map({
                      case (functionA, rejectedReason) => {
                        "  " + printableName(filenamesAndSources, functionA.name) + ":\n" +
                          humanizeRejectionReason(2, verbose, filenamesAndSources, rejectedReason)
                      }
                    })).mkString("\n") + "\n"
              } else {
                ""
              })
          })
      }
      case FunctionAlreadyExists(oldFunctionRange, newFunctionRange, signature) => {
        humanizePos(filenamesAndSources, newFunctionRange.file, newFunctionRange.begin.offset) +
          ": Function " + signature.fullName.last + " already exists! Previous declaration at:\n" +
          humanizePos(filenamesAndSources, oldFunctionRange.file, oldFunctionRange.begin.offset)
      }
      case IfConditionIsntBoolean(range, actualType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": If condition should be a bool, but was: " + actualType
      }
      case WhileConditionIsntBoolean(range, actualType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": If condition should be a bool, but was: " + actualType
      }
      case CantImplStruct(range, struct) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Can't extend a struct: (" + struct + ")"
      }
      case InferAstronomerError(err) => {
        AstronomerErrorHumanizer.humanize(filenamesAndSources, err)
      }
    }
  }

  def humanizeBanner(
    filenamesAndSources: List[(String, String)],
    banner: FunctionBanner2):
  String = {
    banner.originFunction match {
      case None => "(internal)"
      case Some(x) => printableName(filenamesAndSources, x.name)
    }
  }

  private def printableName(
    filenamesAndSources: List[(String, String)],
    functionName: IFunctionDeclarationNameA):
  String = {
    functionName match {
      case LambdaNameA(codeLocation) => humanizePos(filenamesAndSources, codeLocation.file, codeLocation.offset) + ": " + "(lambda)"
      case FunctionNameA(name, codeLocation) => humanizePos(filenamesAndSources, codeLocation.file, codeLocation.offset) + ": " + name
      case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => humanizePos(filenamesAndSources, codeLocation.file, codeLocation.offset) + ": " + name
      case ImmConcreteDestructorNameA() => vimpl()
      case ImmInterfaceDestructorNameA() => vimpl()
      case ImmDropNameA() => vimpl()
    }
  }

  private def printableCoordName(coord: Coord): String = {
    val Coord(ownership, permission, kind) = coord
    (ownership match {
      case Share => ""
      case Own => ""
      case Borrow => "&"
      case Weak => "&&"
    }) +
    (permission match {
      case Readonly => ""
      case Readwrite => "!"
    }) +
    printableKindName(kind)
  }

  private def printableKindName(kind: Kind): String = {
    kind match {
      case Int2() => "int"
      case Bool2() => "bool"
      case Float2() => "float"
      case Str2() => "str"
      case StructRef2(FullName2(_, CitizenName2(humanName, templateArgs))) => humanName + (if (templateArgs.isEmpty) "" else "<" + templateArgs.map(_.toString.mkString) + ">")
    }
  }

  private def printableVarName(
    name: IVarName2):
  String = {
    name match {
      case CodeVarName2(n) => n
    }
  }

  private def getFile(potentialBanner: IPotentialBanner): Int = {
    getFile(potentialBanner.banner)
  }

  private def getFile(banner: FunctionBanner2): Int = {
    banner.originFunction.map(getFile).getOrElse(-76)
  }

  private def getFile(functionA: FunctionA): Int = {
    functionA.range.file
  }

  private def humanizeRejectionReason(
      indentations: Int,
      verbose: Boolean,
      filenamesAndSources: List[(String, String)],
      reason: IScoutExpectedFunctionFailureReason): String = {
    reason match {
      case WrongNumberOfArguments(supplied, expected) => {
        "  ".repeat(indentations) + "Number of params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case WrongNumberOfTemplateArguments(supplied, expected) => {
        "  ".repeat(indentations) + "Number of template params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case SpecificParamDoesntMatch(index, reason) => "  ".repeat(indentations) + "Param at index " + index + " doesn't match: " + reason
      case SpecificParamVirtualityDoesntMatch(index) => "  ".repeat(indentations) + "Virtualities don't match at index " + index
      case Outscored() => "  ".repeat(indentations) + "Outscored!"
      case InferFailure(reason) => {
        if (verbose) {
          "  ".repeat(indentations) +
            "Failed to infer:\n" +
            humanizeConflictCause(indentations + 1, filenamesAndSources, reason)
        } else {
          "(run with --verbose to see some incomprehensible details)"
        }
      }
    }
  }

  def humanizeConflictCause(
    indentations: Int,
    filenamesAndSources: List[(String, String)],
    reason: IConflictCause):
  String = {
    "  ".repeat(indentations) +
    humanizePos(filenamesAndSources, reason.range.file, reason.range.begin.offset) + ": " +
    reason.message + "\n" +
      reason.inferences.templatasByRune.map({ case (key, value) => "  ".repeat(indentations) + "- " + key + " = " + value + "\n" }).mkString("") +
      reason.inferences.typeByRune
        .filter(x => !reason.inferences.templatasByRune.contains(x._1))
        .map({ case (rune, _) => "  ".repeat(indentations) + "- " + rune + " = unknown" + "\n" }).mkString("") +
      reason.causes.map(humanizeConflictCause(indentations + 1, filenamesAndSources, _)).mkString("")
  }
}
