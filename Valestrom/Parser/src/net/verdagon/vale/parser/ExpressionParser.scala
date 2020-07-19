package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.{vassert, vcheck, vfail}

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils {
  private[parser] def templex: Parser[ITemplexPT]
  private[parser] def templateRulesPR: Parser[TemplateRulesP]
  private[parser] def atomPattern: Parser[PatternPP]
//  private[parser] def templex: Parser[ITemplexPT]

  private[parser] def specialOperators: Parser[StringP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("==") | pstr("!="))
  }

  private[parser] def lookup: Parser[LookupPE] = {
//    ("`" ~> "[^`]+".r <~ "`" ^^ (i => LookupPE(i, List()))) |
    (exprIdentifier ^^ (i => LookupPE(i, None)))
  }

  private[parser] def templateArgs: Parser[TemplateArgsP] = {
    pos ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ args ~ end => {
        TemplateArgsP(Range(begin, end), args)
      }
    }
  }

  private[parser] def templateSpecifiedLookup: Parser[LookupPE] = {
    (exprIdentifier <~ optWhite) ~ templateArgs ^^ {
      case name ~ templateArgs => LookupPE(name, Some(templateArgs))
    }
  }

  private[parser] def let: Parser[LetPE] = {
//    (opt(templateRulesPR) <~ optWhite) ~
        pos ~
        (atomPattern <~ white <~ "=" <~ white) ~
        (expression <~ optWhite <~ ";") ~
        pos ^^ {
      case begin ~ /*maybeTemplateRules ~*/ pattern ~ expr ~ end => {
        // We just threw away the topLevelRunes because let statements cant have them.
        LetPE(Range(begin, end), /*maybeTemplateRules.getOrElse(List())*/List(), pattern, expr)
      }
    }
  }

  private[parser] def lend: Parser[IExpressionPE] = {
    // TODO: split the 'a rule out when we implement regions
    pos ~ (("&"| ("'" ~ opt(exprIdentifier <~ optWhite) <~ "&") | ("'" ~ exprIdentifier)) ~> optWhite ~> postfixableExpressions) ~ pos ^^ {
      case begin ~ inner ~ end => LendPE(Range(begin, end), inner)
    }
  }

  private[parser] def weakLend: Parser[IExpressionPE] = {
    pos ~ ("&&" ~> optWhite ~> postfixableExpressions) ~ pos ^^ {
      case begin ~ inner ~ end => WeakLendPE(Range(begin, end), inner)
    }
  }

  private[parser] def not: Parser[IExpressionPE] = {
    pos ~ (pstr("not") <~ white) ~ postfixableExpressions ~ pos ^^ {
      case begin ~ not ~ expr ~ end => {
        FunctionCallPE(Range(begin, end), None, LookupPE(not, None), List(expr), BorrowP)
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

  private[parser] def swap: Parser[IExpressionPE] = {
    ("exch" ~> optWhite ~> (expression <~ optWhite <~ "," <~ optWhite) ~ (expression <~ optWhite)) ^^ {
      case leftExpr ~ rightExpr => SwapPE(leftExpr, rightExpr)
    }
  }

  private[parser] def bracedBlock: Parser[BlockPE] = {
    pos ~ ("{" ~> optWhite ~> blockExprs <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ exprs ~ end => BlockPE(Range(begin, end), exprs)
    }
  }

  private[parser] def eachOrEachI: Parser[FunctionCallPE] = {
    pos ~ (pstr("eachI") | pstr("each")) ~ (white ~> postfixableExpressions <~ white) ~ lambda ~ pos ^^ {
      case begin ~ eachI ~ collection ~ lam ~ end => {
        FunctionCallPE(Range(begin, end), None, LookupPE(eachI, None), List(collection, lam), BorrowP)
      }
    }
  }

  private[parser] def whiile: Parser[WhilePE] = {
    ("while" ~> optWhite ~> pos) ~ ("(" ~> optWhite ~> blockExprs <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ^^ {
      case condBegin ~ condExprs ~ condEnd ~ thenBlock => {
        WhilePE(BlockPE(Range(condBegin, condEnd), condExprs), thenBlock)
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
    ("if" ~> optWhite ~> pos) ~ ("(" ~> optWhite ~> expression <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ^^ {
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
    //   fn main() { {_ + _}(4 + 5) }
    // because it mistakenly successfully parses {_ + _} then dies on the next part.
    (mutate <~ optWhite <~ ";") | swap | let | mat | (destruct <~ optWhite <~ ";") | whiile | eachOrEachI | ifLadder | (expression <~ ";") | ("block" ~> optWhite ~> bracedBlock)
  }

  private[parser] def expressionElementLevel1: Parser[IExpressionPE] = {
    stringExpr |
      integer |
      bool |
      lambda |
      (pos ~ ("()" ~> pos) ^^ { // hack for shortcalling syntax highlighting
        case begin ~ end => FunctionCallPE(Range(begin, end), None, LookupPE(StringP(Range(begin, begin), ""), None), List(), BorrowP)
      }) |
      (packExpr ^^ {
        case List(only) => only
        case _ => vfail()
      }) |
      tupleExpr |
      templateSpecifiedLookup |
      (lookup ^^ {
        case l @ LookupPE(StringP(r, "_"), None) => MagicParamLookupPE(r)
        case other => other
      }) |
      (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(StringP(Range(begin, end), "..."), None) })
  }

  private[parser] def expressionLevel9: Parser[IExpressionPE] = {

    sealed trait IStep
    case class MethodCallStep(borrowContainer: Boolean, lookup: LookupPE, args: List[IExpressionPE]) extends IStep
    case class MemberAccessStep(name: LookupPE) extends IStep
    case class CallStep(callableTargetOwnership: OwnershipP, args: List[IExpressionPE]) extends IStep
    case class IndexStep(args: List[IExpressionPE]) extends IStep
    def step: Parser[IStep] = {
      def afterDot = {
        (integer ^^ { case IntLiteralPE(range, value) => LookupPE(StringP(range, value.toString), None) }) |
        templateSpecifiedLookup |
        lookup
      }

      (opt("^") ~ ("." ~> optWhite ~> afterDot) ~ opt(optWhite ~> packExpr) ^^ {
        case moveContainer ~ lookup ~ None => {
          vassert(moveContainer.isEmpty)
          MemberAccessStep(lookup)
        }
        case moveContainer ~ name ~ Some(args) => {
          MethodCallStep(moveContainer.isEmpty, name, args)
        }
      }) |
      (opt("^") ~ packExpr ^^ {
        case moveContainer ~ pack => CallStep(OwnP, pack)
      }) |
      (indexExpr ^^ IndexStep)
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
            case ((None, prev), MethodCallStep(borrowContainer, lookup, args)) => {
              (None, MethodCallPE(Range(begin, end), prev, borrowContainer, lookup, args))
            }
            case ((prevInline, prev), CallStep(borrowCallable, args)) => {
              (None, FunctionCallPE(Range(begin, end), prevInline, prev, args, borrowCallable))
            }
            case ((None, prev), MemberAccessStep(name)) => {
              (None, DotPE(Range(begin, end), prev, name))
            }
            case ((None, prev), IndexStep(args)) => {
              (None, DotCallPE(Range(begin, end), prev, args))
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
        (range, op: StringP, left, right) => FunctionCallPE(range, None, LookupPE(op, None), List(left, right), BorrowP))

    val withAddSubtract =
      binariableExpression(
        withMultDiv,
        white ~> (pstr("+") | pstr("-")) <~ white,
        (range, op: StringP, left, right) => FunctionCallPE(range, None, LookupPE(op, None), List(left, right), BorrowP))

    val withComparisons =
      binariableExpression(
        withAddSubtract,
        white ~> specialOperators <~ white,
        (range, op: StringP, left, right) => FunctionCallPE(range, None, LookupPE(op, None), List(left, right), BorrowP))

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
        withComparisons,
        not(white ~> pstr("=") <~ white) ~> (white ~> infixFunctionIdentifier <~ white),
        (range, funcName: StringP, left, right) => FunctionCallPE(range, None, LookupPE(funcName, None), List(left, right), BorrowP))

    withCustomBinaries |
    (specialOperators ^^ (op => LookupPE(op, None)))
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
    pos ~ existsMW("[]") ~ opt(patternPrototypeParams) ~ bracedBlock ~ pos ^^ {
      case begin ~ maybeCaptures ~ maybeParams ~ block ~ end => {
        LambdaPE(maybeCaptures, FunctionP(Range(begin, end), None, None, None, None, None, maybeParams, None, Some(block)))
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
        LambdaPE(None, FunctionP(Range(begin, end), None, None, None, None, None, Some(ParamsP(Range(begin, paramsEnd), List(param))), None, Some(body)))
      }
    }
  }
}
