package net.verdagon.vale.templar

import net.verdagon.vale.SourceCodeUtils.{lineAndCol, lineContaining}
import net.verdagon.vale.astronomer.GlobalFunctionFamilyNameA
import net.verdagon.vale.templar.OverloadTemplar.{IScoutExpectedFunctionFailureReason, InferFailure, Outscored, ScoutExpectedFunctionFailure, SpecificParamDoesntMatch, SpecificParamVirtualityDoesntMatch, WrongNumberOfArguments, WrongNumberOfTemplateArguments}
import net.verdagon.vale.templar.templata.CoordTemplata
import net.verdagon.vale.templar.types.ParamFilter
import net.verdagon.vale.vimpl

object TemplarErrorHumanizer {
  def humanize(verbose: Boolean, sources: List[String], err: ICompileErrorT): String = {
    err match {
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
                    humanizeRejectionReason(verbose, outscoredReason)
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
                      case (functionS, rejectedReason) => {
                        "  " + functionS.name + ":\n    " +
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

  private def humanizeRejectionReason(verbose: Boolean, reason: IScoutExpectedFunctionFailureReason): String = {
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
