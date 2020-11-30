package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.{vassert, vcheck, vfail}
import org.apache.commons.lang.StringEscapeUtils

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils {
  private[parser] def templex: Parser[ITemplexPT]
  private[parser] def templateRulesPR: Parser[TemplateRulesP]
  private[parser] def atomPattern: Parser[PatternPP]
//  private[parser] def templex: Parser[ITemplexPT]

  private[parser] def comparisonOperators: Parser[StringP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("===") | pstr("==") | pstr("!="))
  }
  private[parser] def conjunctionOperators: Parser[StringP] = {
    (pstr("and") | pstr("or"))
  }

  private[parser] def memberLookup: Parser[LookupPE] = {
    (integer ^^ { case IntLiteralPE(range, value) => LookupPE(StringP(range, value.toString), None, BorrowP) }) |
    localLookup
  }

  private[parser] def localLookup: Parser[LookupPE] = {
    (opt("^") ~ opt("&&") ~ opt("&") ~ opt("*") ~ exprIdentifier ~ opt("^") ~ opt("&&") ~ opt("&") ~ opt("*")) ^^ {
      case preMove ~ preWeak ~ preConstraint ~ preRW ~ identifier ~ postMove ~ postWeak ~ postConstraint ~ postRW => {
        val move = preMove.nonEmpty || postMove.nonEmpty
        val constraint = preConstraint.nonEmpty || postConstraint.nonEmpty
        val weak = preWeak.nonEmpty || postWeak.nonEmpty
        val readwrite = preRW.nonEmpty || postRW.nonEmpty
        if (move && constraint) { failure("Can't both move and lend!") }
        if (move && weak) { failure("Can't both move and lend a weak!") }
        if (constraint && weak) { failure("Can't both lend a non-weak and lend a weak!") }
        val targetOwnership =
          if (move) {
            OwnP
          } else if (constraint) {
            BorrowP
          } else if (weak) {
            WeakP
          } else {
            // Normally the default is to lend, if theres no ownership sigils.
            // The one exception is _ for which we default move.
            if (identifier.str == "_") {
              OwnP
            } else {
              BorrowP
            }
          }
        LookupPE(identifier, None, targetOwnership)
      }
    }
  }

  private[parser] def templateArgs: Parser[TemplateArgsP] = {
    pos ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ args ~ end => {
        TemplateArgsP(Range(begin, end), args)
      }
    }
  }

  case class LetBegin(begin: Int, patternPP: PatternPP)

  private[parser] def letBegin: Parser[LetBegin] = {
    pos ~ (atomPattern <~ white <~ "=" <~ white) ^^ {
      case begin ~ pattern => LetBegin(begin, pattern)
    }
  }

  private[parser] def let: Parser[LetPE] = {
//    (opt(templateRulesPR) <~ optWhite) ~
      letBegin ~
        (expression) ~
        pos ^^ {
      case LetBegin(begin, pattern) ~ expr ~ end => {
        // We just threw away the topLevelRunes because let statements cant have them.
        LetPE(Range(begin, end), /*maybeTemplateRules.getOrElse(List())*/None, pattern, expr)
      }
    }
  }

  private[parser] def lend: Parser[IExpressionPE] = {
    // TODO: split the 'a rule out when we implement regions
    pos ~ (("&"| ("'" ~ opt(exprIdentifier <~ optWhite) <~ "&") | ("'" ~ exprIdentifier)) ~> optWhite ~> postfixableExpressions) ~ pos ^^ {
      case begin ~ inner ~ end => OwnershippedPE(Range(begin, end), inner, BorrowP)
    }
  }

  private[parser] def weakLend: Parser[IExpressionPE] = {
    pos ~ ("&&" ~> optWhite ~> postfixableExpressions) ~ pos ^^ {
      case begin ~ inner ~ end => OwnershippedPE(Range(begin, end), inner, WeakP)
    }
  }

  private[parser] def not: Parser[IExpressionPE] = {
    pos ~ (pstr("not") <~ white) ~ postfixableExpressions ~ pos ^^ {
      case begin ~ not ~ expr ~ end => {
        FunctionCallPE(Range(begin, end), None, Range(begin, begin), false, LookupPE(not, None, BorrowP), List(expr))
      }
    }
  }

  private[parser] def destruct: Parser[IExpressionPE] = {
    (pos <~ "destruct" <~ white) ~ expression ~ pos ^^ {
      case begin ~ inner ~ end => {
        DestructPE(Range(begin, end), inner)
      }
    }
  }

  private[parser] def ret: Parser[IExpressionPE] = {
    pos ~ ("ret" ~> optWhite ~> expression) ~ pos ^^ {
      case begin ~ sourceExpr ~ end => ReturnPE(Range(begin, end), sourceExpr)
    }
  }

  private[parser] def mutate: Parser[IExpressionPE] = {
    pos ~ ("mut" ~> white ~> expression <~ white <~ "=" <~ white) ~ expression ~ pos ^^ {
      case begin ~ destinationExpr ~ sourceExpr ~ end => MutatePE(Range(begin, end), destinationExpr, sourceExpr)
    }
  }

//  private[parser] def swap: Parser[IExpressionPE] = {
//    ("exch" ~> optWhite ~> (expression <~ optWhite <~ "," <~ optWhite) ~ (expression <~ optWhite)) ^^ {
//      case leftExpr ~ rightExpr => SwapPE(leftExpr, rightExpr)
//    }
//  }

  private[parser] def bracedBlock: Parser[BlockPE] = {
    pos ~ ("{" ~> optWhite ~> blockExprs <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ exprs ~ end => BlockPE(Range(begin, end), exprs)
    }
  }

  private[parser] def eachOrEachI: Parser[FunctionCallPE] = {
    pos ~ (pstr("eachI") | pstr("each")) ~! (white ~> expressionLevel9 <~ white) ~ lambda ~ pos ^^ {
      case begin ~ eachI ~ collection ~ lam ~ end => {
        FunctionCallPE(Range(begin, end), None, Range(begin, begin), false, LookupPE(eachI, None, BorrowP), List(collection, lam))
      }
    }
  }

  private[parser] def whiile: Parser[WhilePE] = {
    pos ~ ("while" ~>! optWhite ~> pos) ~ ("(" ~> optWhite ~> blockExprs <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ~ pos ^^ {
      case begin ~ condBegin ~ condExprs ~ condEnd ~ thenBlock ~ end => {
        WhilePE(Range(begin, end), BlockPE(Range(condBegin, condEnd), condExprs), thenBlock)
      }
    }
  }

  private[parser] def mat: Parser[MatchPE] = {
    pos ~
        ("mat" ~> white ~> postfixableExpressions <~ white) ~
        ("{" ~> optWhite ~> repsep(caase, optWhite) <~ optWhite <~ "}") ~
        pos ^^ {
      case begin ~ condExpr ~ lambdas ~ end => {
        MatchPE(Range(begin, end), condExpr, lambdas)
      }
    }
  }

  private[parser] def ifPart: Parser[(BlockPE, BlockPE)] = {
    ("if" ~> optWhite ~> pos) ~ ("(" ~> optWhite ~> (let | expression) <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ^^ {
      case condBegin ~ condExpr ~ condEnd ~ thenLambda => {
        (BlockPE(Range(condBegin, condEnd), List(condExpr)), thenLambda)
      }
    }
  }

  def ifLadder: Parser[IExpressionPE] = {
    pos ~
        ifPart ~
        rep(optWhite ~> "else" ~> optWhite ~> ifPart) ~
        opt(optWhite ~> "else" ~> optWhite ~> bracedBlock) ~
        pos ^^ {
      case begin ~ rootIf ~ ifElses ~ maybeElseBlock ~ end => {
        val finalElse: BlockPE =
          maybeElseBlock match {
            case None => BlockPE(Range(end, end), List(VoidPE(Range(end, end))))
            case Some(block) => block
          }
        val rootElseBlock =
          ifElses.foldRight(finalElse)({
            case ((condBlock, thenBlock), elseBlock) => {
              BlockPE(
                Range(condBlock.range.begin, thenBlock.range.end),
                List(
                  IfPE(
                    Range(condBlock.range.begin, thenBlock.range.end),
                    condBlock, thenBlock, elseBlock)))
            }
          })
        val (rootConditionLambda, rootThenLambda) = rootIf
        IfPE(Range(begin, end), rootConditionLambda, rootThenLambda, rootElseBlock)
      }
    }
  }

  def statement: Parser[IExpressionPE] = {
    // bracedBlock is at the end because we want to be able to parse "{print(_)}(4);" as an expression.
    // debt: "block" is here temporarily because we get ambiguities in this case:
    //   fn main() int { {_ + _}(4 + 5) }
    // because it mistakenly successfully parses {_ + _} then dies on the next part.
    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(StringP(Range(begin, end), "..."), None, BorrowP) }) |
    (mutate <~ optWhite <~ ";") |
    (let <~ optWhite <~ ";") |
    mat |
    (destruct <~ optWhite <~ ";") |
    whiile |
    eachOrEachI |
    ifLadder |
    (expression <~ ";") |
    blockStatement
  }

  private[parser] def blockStatement = {
    ("block" ~> optWhite ~> bracedBlock)
  }


  private[parser] def shortStringPart: Parser[IExpressionPE] = {
//    ("\"" ~> failure("ended string")) |
    (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\t") }) |
    (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\r") }) |
    (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\n") }) |
    (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\"") }) |
    (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\\") }) |
    (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "/") }) |
    (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "{") }) |
    (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "}") }) |
    (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\n") }) |
    (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\r") }) |
    ("{" ~> expression <~ "}") |
    (pos ~ (not("\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => StrLiteralPE(Range(begin, end), thing) })
  }

  private[parser] def shortStringExpr: Parser[IExpressionPE] = {
    pos ~ ("\"" ~> rep(shortStringPart) <~ "\"") ~ pos ^^ {
      case begin ~ parts ~ end => {
        simplifyStringInterpolate(StrInterpolatePE(Range(begin, end), parts))
      }
    }
  }

  private[parser] def longStringPart: Parser[IExpressionPE] = {
    ("\"\"\"" ~> failure("ended string")) |
    (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\t") }) |
    (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\r") }) |
    (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\n") }) |
    (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\"") }) |
    (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\\") }) |
    (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "/") }) |
    (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "{") }) |
    (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "}") }) |
    (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\n") }) |
    (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => StrLiteralPE(Range(begin, end), "\r") }) |
    ("{" ~> expression <~ "}") |
    (pos ~ (not("\"\"\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => StrLiteralPE(Range(begin, end), thing) })
  }

  private[parser] def longStringExpr: Parser[IExpressionPE] = {
    pos ~ ("\"\"\"" ~> rep(longStringPart) <~ "\"\"\"") ~ pos ^^ {
      case begin ~ parts ~ end => {
        simplifyStringInterpolate(StrInterpolatePE(Range(begin, end), parts))
      }
    }
  }

  def simplifyStringInterpolate(stringExpr: StrInterpolatePE): IExpressionPE = {
    def combine(previousReversed: List[IExpressionPE], next: List[IExpressionPE]): List[IExpressionPE] = {
      (previousReversed, next) match {
        case (StrLiteralPE(Range(prevBegin, _), prev) :: earlier, StrLiteralPE(Range(_, nextEnd), next) :: later) => {
          combine(StrLiteralPE(Range(prevBegin, nextEnd), prev + next) :: earlier, later)
        }
        case (earlier, next :: later) => {
          combine(next :: earlier, later)
        }
        case (earlier, Nil) => earlier
      }
    }

    val StrInterpolatePE(range, parts) = stringExpr

    combine(List(), parts).reverse match {
      case List() => StrLiteralPE(range, "")
      case List(s @ StrLiteralPE(_, _)) => s
      case multiple => StrInterpolatePE(range, multiple)
    }
  }

  private[parser] def stringExpr: Parser[IExpressionPE] = {
    longStringExpr | shortStringExpr
  }

  private[parser] def expressionElementLevel1: Parser[IExpressionPE] = {
    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(StringP(Range(begin, end), "..."), None, BorrowP) }) |
    stringExpr |
      integer |
      bool |
      lambda |
      (pos ~ ("()" ~> pos) ^^ { // hack for shortcalling syntax highlighting
        case begin ~ end => {
          FunctionCallPE(
            Range(begin, end),
            None,
            Range(begin, begin),
            false,
            LookupPE(StringP(Range(begin, begin), ""), None, BorrowP),
            List())
        }
      }) |
      (pos ~ packExpr ~ pos ^^ {
        case begin ~ List(inner) ~ end => inner
        case begin ~ inners ~ end => ShortcallPE(Range(begin, end), inners)
      }) |
      tupleExpr |
      (exprIdentifier ~ (optWhite ~> templateArgs)) ^^ {
        case name ~ templateArgs => LookupPE(name, Some(templateArgs), BorrowP)
      } |
      (localLookup ^^ {
        case LookupPE(StringP(r, "_"), None, targetOwnership) => MagicParamLookupPE(r, targetOwnership)
        case other => other
      })
  }

  private[parser] def expressionLevel9: Parser[IExpressionPE] = {

    sealed trait IStep
    case class MethodCallStep(
      stepRange: Range,
      operatorRange: Range,
      isMapCall: Boolean,
      lookup: LookupPE,
      args: List[IExpressionPE]
    ) extends IStep
    case class MemberAccessStep(
      stepRange: Range,
      operatorRange: Range,
      isMapCall: Boolean,
      name: LookupPE
    ) extends IStep
    case class CallStep(
      stepRange: Range,
      operatorRange: Range,
      isMapCall: Boolean,
      args: List[IExpressionPE]
    ) extends IStep
    case class IndexStep(
      stepRange: Range,
      args: List[IExpressionPE]
    ) extends IStep
    def step: Parser[IStep] = {
      (pos ~ opt("..") ~ pos ~ packExpr ~ pos ^^ {
        case begin ~ isMapCall ~ opEnd ~ pack ~ end => {
          CallStep(Range(begin, end), Range(begin, opEnd), isMapCall.nonEmpty, pack)
        }
      }) |
      ((pos <~ optWhite) ~ ("." ~> opt(".")) ~ pos ~ (optWhite ~> exprIdentifier) ~ opt(optWhite ~> templateArgs) ~ (optWhite ~> packExpr) ~ pos ^^ {
        case begin ~ mapCall ~ opEnd ~ name ~ maybeTemplateArgs ~ args ~ end => {
          MethodCallStep(Range(begin, end), Range(begin, opEnd), mapCall.nonEmpty, LookupPE(name, maybeTemplateArgs, BorrowP), args)
        }
      }) |
      ((pos <~ optWhite) ~ ("." ~> opt(".")) ~ pos ~ (optWhite ~> memberLookup) ~ pos ^^ {
        case begin ~ mapCall ~ opEnd ~ lookup ~ end => {
          MemberAccessStep(Range(begin, end), Range(begin, opEnd), mapCall.nonEmpty, lookup)
        }
      }) |
      ((pos <~ optWhite) ~ indexExpr ~ pos ^^ { case begin ~ i ~ end => IndexStep(Range(begin, end), i) })
    }

    // The float is up here because we don't want the 0.0 in [[4]].0.0 to be parsed as a float.
    float |
    // We dont have the optWhite here because we dont want to allow spaces before calls.
    // We dont want to allow moo (4) because we want each statements like this:
    //   each moo (x){ println(x); }
    (pos ~ existsW("inl") ~ expressionElementLevel1 ~ rep(/*SEE ABOVE optWhite ~> */step) ~ pos ^^ {
      case begin ~ maybeInline ~ first ~ restWithDots ~ end => {
        val (_, expr) =
          restWithDots.foldLeft((maybeInline, first))({
            case ((None, prev), MethodCallStep(stepRange, operatorRange, isMapCall, lookup, args)) => {
              (None, MethodCallPE(Range(begin, stepRange.end), prev, operatorRange, isMapCall, lookup, args))
            }
            case ((prevInline, prev), CallStep(stepRange, operatorRange, isMapCall, args)) => {
              (None, FunctionCallPE(Range(begin, stepRange.end), prevInline, operatorRange, isMapCall, prev, args))
            }
            case ((None, prev), MemberAccessStep(stepRange, operatorRange, isMapCall, name)) => {
              (None, DotPE(Range(begin, stepRange.end), prev, operatorRange, isMapCall, name.targetOwnership, name.name))
            }
            case ((None, prev), IndexStep(stepRange, args)) => {
              (None, IndexPE(Range(begin, stepRange.end), prev, args))
            }
          })
        expr
      }
    })
  }

  // Parses expressions that can contain postfix operators, like function calling
  // or dots.
  private[parser] def postfixableExpressions: Parser[IExpressionPE] = {
    ret | mutate | ifLadder | weakLend | lend | not | expressionLevel9
  }

  // Binariable = can have binary operators in it
  def binariableExpression[T](
      innerParser: Parser[IExpressionPE],
      binaryOperatorParser: Parser[T],
      combiner: (Range, T, IExpressionPE, IExpressionPE) => IExpressionPE): Parser[IExpressionPE] = {
    pos ~ innerParser ~ rep(binaryOperatorParser ~ innerParser ~ pos) ^^ {
      case begin ~ firstElement ~ restBinaryOperatorsAndElements => {
        val (_, resultExpr) =
          restBinaryOperatorsAndElements.foldLeft((begin, firstElement))({
            case ((previousResultBegin, previousResult), (operator ~ nextElement ~ nextElementEnd)) => {
              val range = Range(previousResultBegin, nextElementEnd)
              val combinedExpr = combiner(range, operator, previousResult, nextElement)
              (nextElementEnd, combinedExpr)
            }
          })
        resultExpr
      }
    }
  }

  def expression: Parser[IExpressionPE] = {
    // These extra white parsers are here otherwise we stop early when we're just looking for "<", in "<=".
    // Expecting the whitespace means we parse the entire operator.

    val withMultDiv =
      binariableExpression(
        postfixableExpressions,
        white ~> (pstr("*") | pstr("/")) <~ white,
        (range, op: StringP, left, right) => FunctionCallPE(range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None, BorrowP), List(left, right)))

    val withAddSubtract =
      binariableExpression(
        withMultDiv,
        white ~> (pstr("+") | pstr("-")) <~ white,
        (range, op: StringP, left, right) => FunctionCallPE(range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None, BorrowP), List(left, right)))

//    val withAnd =
//      binariableExpression(
//        withAddSubtract,
//        "and".r,
//        (_: String, left, right) => AndPE(left, right))
//
//    val withOr =
//      binariableExpression(
//        withAnd,
//        "or".r,
//        (_: String, left, right) => OrPE(left, right))

    val withCustomBinaries =
      binariableExpression(
        withAddSubtract,
        not(white ~> pstr("=") <~ white) ~>
          not(white ~> comparisonOperators <~ white) ~>
          not(white ~> conjunctionOperators <~ white) ~>
          (white ~> infixFunctionIdentifier <~ white),
        (range, funcName: StringP, left, right) => {
          FunctionCallPE(range, None, Range(funcName.range.begin, funcName.range.end), false, LookupPE(funcName, None, BorrowP), List(left, right))
        })

    val withComparisons =
      binariableExpression(
        withCustomBinaries,
        not(white ~> conjunctionOperators <~ white) ~>
          white ~> comparisonOperators <~ white,
        (range, op: StringP, left, right) => {
          FunctionCallPE(range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None, BorrowP), List(left, right))
        })

    val withConjunctions =
      binariableExpression(
        withComparisons,
        white ~> conjunctionOperators <~ white,
        (range, op: StringP, left, right) => {
          op.str match {
            case "and" => AndPE(range, BlockPE(left.range, List(left)), BlockPE(right.range, List(right)))
            case "or" => OrPE(range, BlockPE(left.range, List(left)), BlockPE(right.range, List(right)))
          }
        })

    withConjunctions |
    (conjunctionOperators ^^ (op => LookupPE(op, None, BorrowP))) |
    (comparisonOperators ^^ (op => LookupPE(op, None, BorrowP)))
  }

  sealed trait StatementType
  case object FunctionReturnStatementType extends StatementType
  case object BlockReturnStatementType extends StatementType
  case object NormalResultStatementType extends StatementType

  // The boolean means it's a result or a return, it should be the last thing in the block.
  def statementOrResult: Parser[(IExpressionPE, Boolean)] = {
    pos ~ (opt(("="|"ret") <~ optWhite) ~ statement) ~ pos ^^ {
      case begin ~ (maybeResult ~ expr) ~ end => {
        (maybeResult, expr) match {
          case (None, expr) => (expr, false)
          case (Some("="), expr) => (expr, true)
          case (Some("ret"), expr) => (ReturnPE(Range(begin, end), expr), true)
        }
      }
    }
  }

  def multiStatementBlock: Parser[List[IExpressionPE]] = {
    // Can't just do a rep(statement <~ optWhite) ~ ("=" ~> optWhite ~> statement) because
    // "= doThings(a);" is actually a valid statement, which looks like doThings(=, a);
    rep1sep(statementOrResult, optWhite) ~ pos ^^ {
      case statements ~ end => {
        statements match {
          case List() => List(VoidPE(Range(end, end)))
          case resultsOrStatements => {
            if (resultsOrStatements.init.exists(_._2)) {
              vfail("wot")
            }
            val exprs =
              if (resultsOrStatements.last._2) {
                resultsOrStatements.map(_._1)
              } else {
                resultsOrStatements.map(_._1) :+ VoidPE(Range(end, end))
              }
            exprs
          }
        }
      }
    }
  }

  def blockExprs: Parser[List[IExpressionPE]] = {
    multiStatementBlock |
      ((expression) ^^ { case e => List(e) }) |
      (pos ^^ { case p => List(VoidPE(Range(p, p)))}) |
      (pos ~ ("..." ~> pos) ^^ { case begin ~ end => List(VoidPE(Range(begin, end))) })
  }

  private[parser] def tupleExpr: Parser[IExpressionPE] = {
    pos ~ ("[" ~> optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ (a:List[IExpressionPE]) ~ end => {
        SequencePE(Range(begin, end), a)
      }
    }
  }

  private[parser] def packExpr: Parser[List[IExpressionPE]] = {
    "(" ~> optWhite ~> ("..." ^^^ List() | repsep(expression, optWhite ~> "," <~ optWhite)) <~ optWhite <~ ")"
  }

  private[parser] def indexExpr: Parser[List[IExpressionPE]] = {
    "[" ~> optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]"
  }
//
//  private[parser] def filledParamLambda: Parser[FunctionP] = {
//    pos ~ (patternPrototypeParams <~ optWhite) ~ opt(":" ~> optWhite ~> templex <~ optWhite) ~ bracedBlock ~ pos ^^ {
//      case begin ~ patternParams ~ maybeReturn ~ body ~ end =>
//        FunctionP(
//          Range(begin, end), None, None, None, None, None, Some(patternParams), maybeReturn, Some(body))
//    }
//  }
//
//  private[parser] def emptyParamLambda: Parser[FunctionP] = {
//    pos ~ (patternPrototypeParams <~ optWhite) ~ opt(":" ~> optWhite ~> templex <~ optWhite) ~ (pos <~ "{" <~ optWhite <~ "}") ~ pos ^^ {
//      case begin ~ patternParams ~ maybeReturn ~ bodyBegin ~ end =>
//        FunctionP(
//          Range(begin, end), None, None, None, None, None, Some(patternParams), maybeReturn,
//          Some(BlockPE(Range(bodyBegin, end), List(VoidPE(Range(end, end))))))
//    }
//  }
//
//  private[parser] def emptyParamLessLambda: Parser[BlockPE] = {
//    (pos <~ "{" <~ optWhite <~ "}") ~ pos ^^ {
//      case begin ~ end => BlockPE(Range(begin, end), List(VoidPE(Range(end, end))))
//    }
//  }

  private[parser] def lambda: Parser[LambdaPE] = {
    pos ~ existsMW("[]") ~ opt(patternPrototypeParams) ~ pos ~ bracedBlock ~ pos ^^ {
      case begin ~ maybeCaptures ~ maybeParams ~ headerEnd ~ block ~ end => {
        LambdaPE(
          maybeCaptures,
          FunctionP(
            Range(begin, end),
            FunctionHeaderP(
              Range(begin, headerEnd),
              None, List(), None, None, maybeParams, FunctionReturnP(Range(headerEnd, headerEnd), None, None)),
            Some(block)))
      }
    }
  }

  private[parser] def patternPrototypeParam: Parser[PatternPP] = {
    atomPattern ^^ {
      case pattern => pattern
    }
  }

  def patternPrototypeParams: Parser[ParamsP] = {
    pos ~ ("(" ~> optWhite ~> repsep(optWhite ~> patternPrototypeParam, optWhite ~> ",") <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ params ~ end => ParamsP(Range(begin, end), params)
    }
  }

  def caase: Parser[LambdaPE] = {
    pos ~ patternPrototypeParam ~ (pos <~ optWhite) ~ bracedBlock ~ pos ^^ {
      case begin ~ param ~ paramsEnd ~ body ~ end => {
        LambdaPE(
          None,
          FunctionP(
            Range(begin, end),
            FunctionHeaderP(
              Range(begin, paramsEnd),
              None, List(), None, None,
              Some(ParamsP(Range(begin, paramsEnd), List(param))),
              FunctionReturnP(Range(end, end), None, None)), Some(body)))
      }
    }
  }
}
