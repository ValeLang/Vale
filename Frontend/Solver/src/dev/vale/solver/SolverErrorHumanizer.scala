package dev.vale.solver

import dev.vale.{CodeLocationS, FileCoordinateMap, RangeS, repeatStr}
import dev.vale.SourceCodeUtils.{lineContaining, lineRangeContaining, linesBetween}
import dev.vale.RangeS

object SolverErrorHumanizer {
  def humanizeFailedSolve[Rule, RuneID, Conclusion, ErrType](
    codeMap: FileCoordinateMap[String],
    humanizeRune: RuneID => String,
    humanizeConclusion: (FileCoordinateMap[String], Conclusion) => String,
    humanizeRuleError: (FileCoordinateMap[String], ErrType) => String,
    getRuleRange: (Rule) => RangeS,
    getRuneUsages: (Rule) => Iterable[(RuneID, RangeS)],
    ruleToRunes: (Rule) => Iterable[RuneID],
    ruleToString: (Rule) => String,
    result: IIncompleteOrFailedSolve[Rule, RuneID, Conclusion, ErrType]):
  // Returns text and all line begins
  (String, Vector[CodeLocationS]) = {
    val errorBody =
      (result match {
        case IncompleteSolve(_, _, unknownRunes, _) => {
          "Couldn't solve some runes: "  + unknownRunes.toVector.map(humanizeRune).mkString(", ")
        }
        case FailedSolve(_, _, error) => {
          error match {
            case SolverConflict(rune, previousConclusion, newConclusion) => {
              "Conflict, thought rune " + humanizeRune(rune) + " was " + humanizeConclusion(codeMap, previousConclusion) + " but now concluding it's " + humanizeConclusion(codeMap, newConclusion)
            }
            case RuleError(err) => {
              humanizeRuleError(codeMap, err)
            }
          }
        }
      })

    val verbose = true
    val rulesToSummarize = result.unsolvedRules.filter(!getRuleRange(_).file.isInternal)

    val allLineBeginLocs =
      rulesToSummarize.flatMap(rule => {
        val range = getRuleRange(rule)
        val RangeS(begin, end) = range

        linesBetween(codeMap, begin, end)
          .map({ case (begin, _) => CodeLocationS(getRuleRange(rule).file, begin) })
      })
        .distinct
    val allRuneUsages = rulesToSummarize.flatMap(getRuneUsages).distinct
    val lineBeginLocToRuneUsage =
      allRuneUsages
        .map(runeUsage => {
          val usageBeginLine = lineRangeContaining(codeMap, runeUsage._2.begin)._1
          (usageBeginLine, runeUsage)
        })
        .groupBy(_._1)
        .mapValues(_.map(_._2))

    val incompleteConclusions = result.steps.flatMap(_.conclusions).toMap

    val textFromUserRules =
      allLineBeginLocs
        // Show the lines in order
        .sortBy(_.offset)
        .map({ case loc @ CodeLocationS(file, lineBegin) =>
          lineContaining(codeMap, loc) + "\n" +
            lineBeginLocToRuneUsage
              .getOrElse(lineBegin, Vector())
              // Show the runes from right to left
              .sortBy(-_._2.begin.offset)
              .map({ case (rune, range) =>
                val numSpaces = range.begin.offset - lineBegin
                val numArrows = Math.max(range.end.offset - range.begin.offset, 1)
                val runeName = humanizeRune(rune)
                  repeatStr(" ", numSpaces) + repeatStr("^", numArrows) + " " +
                    runeName + ": " +
                  incompleteConclusions.get(rune).map(humanizeConclusion(codeMap, _)).getOrElse("(unknown)") +
                  "\n"
              }).mkString("")
        }).mkString("")

    val textFromSteps =
      "Steps:\n" +
      result.steps.foldLeft(("", Set[RuneID]()))({
        case ((stringSoFar, previouslyPrintedConclusions), Step(complex, rules, addedRules, newConclusions)) => {
          val newString =
            "" +
              (if (!complex && rules.isEmpty) "Supplied:" else "") +
              (if (complex) "(complex)  " else "") +
              rules.map(_._2).map(ruleToString).mkString("  ") + "\n" +
              (newConclusions -- previouslyPrintedConclusions).map({ case (newRune, newConclusion) =>
                "  " + humanizeRune(newRune) + ": " + humanizeConclusion(codeMap, newConclusion) + "\n"
              }).mkString("") +
              addedRules.map("  added rule: " + ruleToString(_) + "\n").mkString("")
          (stringSoFar + newString, previouslyPrintedConclusions ++ newConclusions.keySet)
        }
      })._1 +
        result.unsolvedRules.map(unsolvedRule => {
          "Unsolved rule: " + ruleToString(unsolvedRule) + "\n"
        }).mkString("") +
        (if (result.unsolvedRunes.nonEmpty) {
          "Unsolved runes: " + result.unsolvedRunes.map(humanizeRune).mkString(" ")
        } else {
          ""
        })

    val text = errorBody + "\n" + textFromUserRules + textFromSteps
    (text, allLineBeginLocs.toVector)
  }

}
