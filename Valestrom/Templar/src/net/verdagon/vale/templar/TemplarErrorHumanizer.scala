package net.verdagon.vale.templar

import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining}
import net.verdagon.vale.astronomer.{AstronomerErrorHumanizer, CodeVarNameA, ConstructorNameA, FunctionA, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, INameA, ImmConcreteDestructorNameA, ImmDropNameA, ImmInterfaceDestructorNameA, LambdaNameA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, InferFailure, Outscored, ScoutExpectedFunctionFailure, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.infer.infer.{IConflictCause, InferSolveFailure}
import net.verdagon.vale.templar.templata.{CoordTemplata, FunctionBanner2, IPotentialBanner}
import net.verdagon.vale.templar.types.{Bool2, Constraint, Coord, Float2, Int2, Kind, Own, ParamFilter, Readonly, Readwrite, Share, Str2, StructRef2, Weak}
import net.verdagon.vale.{FileCoordinate, FileCoordinateMap, repeatStr, vimpl}

object TemplarErrorHumanizer {
  def humanize(
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      err: ICompileErrorT):
  String = {
    err match {
      case RangedInternalErrorT(range, message) => {
        humanizePos(codeMap, range.file, range.begin.offset) + " " + message
      }
      case CantUseReadonlyReferenceAsReadwrite(range) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Can't make readonly reference into a readwrite one!"
      }
      case CantMoveOutOfMemberT(range, name) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Cannot move out of member (" + name + ")"
      }
      case CantMutateFinalMember(range, fullName, memberName) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Cannot mutate final member '" + printableVarName(memberName.last) + "' of container " + printableFullName(fullName)
      }
      case CantMutateFinalLocal(range, localName) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Cannot mutate final local \"" + printableName(codeMap, localName) + "\"."
      }
      case LambdaReturnDoesntMatchInterfaceConstructor(range) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Argument function return type doesn't match interface method param"
      }
      case CantUseUnstackifiedLocal(range, name) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Can't use local that was already moved (" + name + ")"
      }
      case CannotSubscriptT(range, tyype) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Cannot subscript type: " + tyype + "!"
      }
      case CouldntConvertForReturnT(range, expectedType, actualType) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Couldn't convert " + actualType + " to expected return type " + expectedType
      }
      case CouldntConvertForMutateT(range, expectedType, actualType) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Mutate couldn't convert " + actualType + " to expected destination type " + expectedType
      }
      case CouldntFindMemberT(range, memberName) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Couldn't find member " + memberName + "!"
      }
      case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Function " + printableName(codeMap, functionName) + " return type " + expectedReturnType + " doesn't match body's result: " + resultType
      }
      case CouldntFindIdentifierToLoadT(range, name) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Couldn't find anything named `" + name + "`!"
      }
      case NonReadonlyReferenceFoundInPureFunctionParameter(range, name) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Parameter `" + name + "` should be readonly, because it's in a pure function."
      }
      case CouldntFindTypeT(range, name) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Couldn't find any type named `" + name + "`!"
      }
      case ImmStructCantHaveVaryingMember(range, structName, memberName) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Immutable struct (\"" + printableName(codeMap, structName) + "\") cannot have varying member (\"" + memberName + "\")."
      }
      case CouldntFindFunctionToCallT(range, ScoutExpectedFunctionFailure(name, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
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
          "):\n" + lineContaining(codeMap, range.file, range.end.offset) + "\n" +
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
                      codeMap,
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
                      "  " + humanizeBanner(codeMap, banner) + "\n" +
                        humanizeRejectionReason(2, verbose, codeMap, rejectedReason)
                    }
                  }) ++
                    rejectedReasonByFunction.map({
                      case (functionA, rejectedReason) => {
                        "  " + printableName(codeMap, functionA.name) + ":\n" +
                          humanizeRejectionReason(2, verbose, codeMap, rejectedReason)
                      }
                    })).mkString("\n") + "\n"
              } else {
                ""
              })
          })
      }
      case FunctionAlreadyExists(oldFunctionRange, newFunctionRange, signature) => {
        humanizePos(codeMap, newFunctionRange.file, newFunctionRange.begin.offset) +
          ": Function " + signature.fullName.last + " already exists! Previous declaration at:\n" +
          humanizePos(codeMap, oldFunctionRange.file, oldFunctionRange.begin.offset)
      }
      case IfConditionIsntBoolean(range, actualType) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": If condition should be a bool, but was: " + actualType
      }
      case WhileConditionIsntBoolean(range, actualType) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": If condition should be a bool, but was: " + actualType
      }
      case CantImplStruct(range, struct) => {
        humanizePos(codeMap, range.file, range.begin.offset) +
          ": Can't extend a struct: (" + struct + ")"
      }
      case InferAstronomerError(err) => {
        AstronomerErrorHumanizer.humanize(codeMap, err)
      }
    }
  }

  def humanizeBanner(
    codeMap: FileCoordinateMap[String],
    banner: FunctionBanner2):
  String = {
    banner.originFunction match {
      case None => "(internal)"
      case Some(x) => printableName(codeMap, x.name)
    }
  }

  private def printableName(
    codeMap: FileCoordinateMap[String],
    name: INameA):
  String = {
    name match {
      case CodeVarNameA(name) => name
      case TopLevelCitizenDeclarationNameA(name, codeLocation) => name
      case LambdaNameA(codeLocation) => humanizePos(codeMap, codeLocation.file, codeLocation.offset) + ": " + "(lambda)"
      case FunctionNameA(name, codeLocation) => humanizePos(codeMap, codeLocation.file, codeLocation.offset) + ": " + name
      case ConstructorNameA(TopLevelCitizenDeclarationNameA(name, codeLocation)) => humanizePos(codeMap, codeLocation.file, codeLocation.offset) + ": " + name
      case ImmConcreteDestructorNameA(_) => vimpl()
      case ImmInterfaceDestructorNameA(_) => vimpl()
      case ImmDropNameA(_) => vimpl()
    }
  }

  private def printableCoordName(coord: Coord): String = {
    val Coord(ownership, permission, kind) = coord
    (ownership match {
      case Share => ""
      case Own => ""
      case Constraint => "&"
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
      case StructRef2(f) => printableFullName(f)
    }
  }
  private def printableFullName(fullName2: FullName2[IName2]): String = {
    fullName2.last match {
      case CitizenName2(humanName, templateArgs) => humanName + (if (templateArgs.isEmpty) "" else "<" + templateArgs.map(_.toString.mkString) + ">")
      case x => x.toString
    }
  }

  private def printableVarName(
    name: IVarName2):
  String = {
    name match {
      case CodeVarName2(n) => n
    }
  }

//  private def getFile(potentialBanner: IPotentialBanner): FileCoordinate = {
//    getFile(potentialBanner.banner)
//  }

//  private def getFile(banner: FunctionBanner2): FileCoordinate = {
//    banner.originFunction.map(getFile).getOrElse(FileCoordinate.internal(-76))
//  }

  private def getFile(functionA: FunctionA): FileCoordinate = {
    functionA.range.file
  }

  private def humanizeRejectionReason(
      indentations: Int,
      verbose: Boolean,
      codeMap: FileCoordinateMap[String],
      reason: IScoutExpectedFunctionFailureReason): String = {
    reason match {
      case WrongNumberOfArguments(supplied, expected) => {
        repeatStr("  ", indentations) + "Number of params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case WrongNumberOfTemplateArguments(supplied, expected) => {
        repeatStr("  ", indentations) + "Number of template params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case SpecificParamDoesntMatch(index, reason) => repeatStr("  ", indentations) + "Param at index " + index + " doesn't match: " + reason
      case SpecificParamVirtualityDoesntMatch(index) => repeatStr("  ", indentations) + "Virtualities don't match at index " + index
      case Outscored() => repeatStr("  ", indentations) + "Outscored!"
      case InferFailure(reason) => {
        if (verbose) {
          repeatStr("  ", indentations) +
            "Failed to infer:\n" +
            humanizeConflictCause(indentations + 1, codeMap, reason)
        } else {
          "(run with --verbose to see some incomprehensible details)"
        }
      }
    }
  }

  def humanizeConflictCause(
    indentations: Int,
    codeMap: FileCoordinateMap[String],
    reason: IConflictCause):
  String = {
    repeatStr("  ", indentations) +
    humanizePos(codeMap, reason.range.file, reason.range.begin.offset) + ": " +
    reason.message + "\n" +
      reason.inferences.templatasByRune.map({ case (key, value) => repeatStr("  ", indentations) + "- " + key + " = " + value + "\n" }).mkString("") +
      reason.inferences.typeByRune
        .filter(x => !reason.inferences.templatasByRune.contains(x._1))
        .map({ case (rune, _) => "  ".repeat(indentations) + "- " + rune + " = unknown" + "\n" }).mkString("") +
      reason.causes.map(humanizeConflictCause(indentations + 1, codeMap, _)).mkString("")
  }
}
