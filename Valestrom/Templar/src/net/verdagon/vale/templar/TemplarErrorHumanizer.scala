package net.verdagon.vale.templar

import net.verdagon.vale.SourceCodeUtils.{lineAndCol, lineContaining}
import net.verdagon.vale.astronomer.{ConstructorNameA, FunctionA, FunctionNameA, GlobalFunctionFamilyNameA, IFunctionDeclarationNameA, ImmConcreteDestructorNameA, ImmDropNameA, ImmInterfaceDestructorNameA, LambdaNameA}
import net.verdagon.vale.scout.RangeS
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, InferFailure, Outscored, ScoutExpectedFunctionFailure, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.templata.{CoordTemplata, FunctionBanner2, IPotentialBanner}
import net.verdagon.vale.templar.types.ParamFilter
import net.verdagon.vale.vimpl

object TemplarErrorHumanizer {
  def humanize(verbose: Boolean, sources: List[String], err: ICompileErrorT): String = {
    err match {
      case BodyResultDoesntMatch(range, functionName, expectedReturnType, resultType) => {
        lineAndCol(sources(range.file), range.begin.offset) +
          ": Function " + printableName(functionName) + " return type " + expectedReturnType + " doesn't match body's result: " + resultType
      }
      case CouldntFindFunctionToCallT(range, ScoutExpectedFunctionFailure(name, args, outscoredReasonByPotentialBanner, rejectedReasonByBanner, rejectedReasonByFunction)) => {
        lineAndCol(sources(range.file), range.begin.offset) +
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
          "):\n" + lineContaining(sources(range.file), range.end.offset) + "\n" +
          (if (outscoredReasonByPotentialBanner.size + rejectedReasonByBanner.size + rejectedReasonByFunction.size == 0) {
            "No function with that name exists.\nPerhaps you forget to include a file in the command line?\n"
          } else {
            (if (outscoredReasonByPotentialBanner.nonEmpty) {
              "Outscored candidates:\n" + outscoredReasonByPotentialBanner.map({
                case (potentialBanner, outscoredReason) => {
                  "  " + TemplataNamer.getFullNameIdentifierName(potentialBanner.banner.fullName) + ":\n    " +
                    humanizeRejectionReason(
                      verbose,
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
                      "  " + TemplataNamer.getFullNameIdentifierName(banner.fullName) + "\n    " +
                        humanizeRejectionReason(verbose, rejectedReason)
                    }
                  }) ++
                    rejectedReasonByFunction.map({
                      case (functionA, rejectedReason) => {
                        "  " + functionA.name + ":\n    " +
                          humanizeRejectionReason(verbose, rejectedReason)
                      }
                    })).mkString("\n") + "\n"
              } else {
                ""
              })
          })
      }
    }
  }

  private def printableName(functionName: IFunctionDeclarationNameA): String = {
    functionName match {
      case LambdaNameA(codeLocation) => vimpl()
      case FunctionNameA(name, codeLocation) =>name
      case ConstructorNameA(tlcd) => vimpl()
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
          // TODO: At some point, take a structured reason that we can pretty print.
          reason.toString
        } else {
          "(run with --verbose to see some incomprehensible details)"
        }
      }
    }
  }
}
