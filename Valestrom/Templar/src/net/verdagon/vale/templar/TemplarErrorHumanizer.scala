package net.verdagon.vale.templar

import net.verdagon.vale.SourceCodeUtils.{humanizePos, lineContaining}
import net.verdagon.vale.astronomer.{ConstructorNameA, FunctionA, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, ImmConcreteDestructorNameA, ImmDropNameA, ImmInterfaceDestructorNameA, LambdaNameA, TopLevelCitizenDeclarationNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, InferFailure, Outscored, ScoutExpectedFunctionFailure, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.templata.{CoordTemplata, FunctionBanner2, IPotentialBanner}
import net.verdagon.vale.templar.types.ParamFilter
import net.verdagon.vale.vimpl

object TemplarErrorHumanizer {
  def humanize(
      verbose: Boolean,
      filenamesAndSources: List[(String, String)],
      err: ICompileErrorT):
  String = {
    err match {
      case CouldntFindMemberT(range, memberName) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find member " + memberName + "!"
      }
      case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Function " + printableName(filenamesAndSources, functionName) + " return type " + expectedReturnType + " doesn't match body's result: " + resultType
      }
      case CouldntFindFunctionToLoadT(range, GlobalFunctionFamilyNameA(name)) => {
        humanizePos(filenamesAndSources, range.file, range.begin.offset) +
          ": Couldn't find any function named `" + name + "`!"
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
                  "  " + TemplataNamer.getFullNameIdentifierName(potentialBanner.banner.fullName) + ":\n    " +
                    humanizeRejectionReason(
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
                      "  " + humanizeBanner(filenamesAndSources, banner) + "\n    " +
                        humanizeRejectionReason(verbose, filenamesAndSources, rejectedReason)
                    }
                  }) ++
                    rejectedReasonByFunction.map({
                      case (functionA, rejectedReason) => {
                        "  " + printableName(filenamesAndSources, functionA.name) + ":\n    " +
                          humanizeRejectionReason(verbose, filenamesAndSources, rejectedReason)
                      }
                    })).mkString("\n") + "\n"
              } else {
                ""
              })
          })
      }
    }
  }

  def humanizeBanner(
    filenamesAndSources: List[(String, String)],
    banner: FunctionBanner2):
  String = {
    banner.originFunction match {
      case None => vimpl()
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
      verbose: Boolean,
      filenamesAndSources: List[(String, String)],
      reason: IScoutExpectedFunctionFailureReason): String = {
    reason match {
      case WrongNumberOfArguments(supplied, expected) => {
        "Number of params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case WrongNumberOfTemplateArguments(supplied, expected) => {
        "Number of template params doesn't match! Supplied " + supplied + " but function takes " + expected
      }
      case SpecificParamDoesntMatch(index, reason) => "Param at index " + index + " doesn't match: " + reason
      case SpecificParamVirtualityDoesntMatch(index) => "Virtualities don't match at index " + index
      case Outscored() => "Outscored!"
      case InferFailure(reason) => {
        if (verbose) {
          if (reason.rootCauses.size == 1) {
            val rootCause = reason.rootCauses.head
            "Failed to infer: " +
              humanizePos(filenamesAndSources, rootCause.range.file, rootCause.range.begin.offset) + ": " +
              rootCause.message + "\n" +
              rootCause.inferences.templatasByRune.map({ case (key, value) => "    - " + key + " = " + value }).mkString("\n") + "\n" +
              rootCause.inferences.typeByRune
                .filter(x => !rootCause.inferences.templatasByRune.contains(x._1))
                .map({ case (rune, _) => "    - " + rune + " = unknown" }).mkString("\n") + "\n"
          } else {
            "(too many causes)"
          }
        } else {
          "(run with --verbose to see some incomprehensible details)"
        }
      }
    }
  }
}
