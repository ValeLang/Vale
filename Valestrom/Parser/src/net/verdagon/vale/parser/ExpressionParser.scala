package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.{vassert, vcheck, vcurious, vfail, vimpl}
import org.apache.commons.lang.StringEscapeUtils

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils with TemplexParser {
  private[parser] def templex: Parser[ITemplexPT]
  private[parser] def templateRulesPR: Parser[TemplateRulesP]
  private[parser] def atomPattern: Parser[PatternPP]
//  private[parser] def templex: Parser[ITemplexPT]

  private[parser] def comparisonOperators: Parser[NameP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("===") | pstr("==") | pstr("!="))
  }
  private[parser] def conjunctionOperators: Parser[NameP] = {
    (pstr("and") | pstr("or"))
  }

  private[parser] def lookup: Parser[LookupPE] = {
//    ("`" ~> "[^`]+".r <~ "`" ^^ (i => LookupPE(i, Vector.empty))) |
    (exprIdentifier ^^ (i => LookupPE(i, None)))
  }

  private[parser] def templateArgs: Parser[TemplateArgsP] = {
    pos ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
      case begin ~ args ~ end => {
        TemplateArgsP(Range(begin, end), args.toVector)
      }
    }
  }

  private[parser] def templateSpecifiedLookup: Parser[LookupPE] = {
    (exprIdentifier <~ optWhite) ~ templateArgs ^^ {
      case name ~ templateArgs => LookupPE(name, Some(templateArgs))
    }
  }

  case class BadLetBegin(range: Range) { override def hashCode(): Int = vcurious() }

  case class LetBegin(begin: Int, patternPP: PatternPP) { override def hashCode(): Int = vcurious() }

  private[parser] def letBegin: Parser[LetBegin] = {
    pos ~ (atomPattern <~ white <~ "=" <~ white) ^^ {
      case begin ~ pattern => LetBegin(begin, pattern)
    }
  }

  private[parser] def badLetBegin: Parser[BadLetBegin] = {
    pos ~ expressionLevel5 ~ (pos <~ white <~ "=" <~ white) ^^ {
      case begin ~ _ ~ end => BadLetBegin(Range(begin, end))
    }
  }

  private[parser] def let: Parser[LetPE] = {
//    (opt(templateRulesPR) <~ optWhite) ~
      letBegin ~
        (expression) ~
        pos ^^ {
      case LetBegin(begin, pattern) ~ expr ~ end => {
        // We just threw away the topLevelRunes because let statements cant have them.
        LetPE(Range(begin, end), /*maybeTemplateRules.getOrElse(Vector.empty)*/None, pattern, expr)
      }
    }
  }

  private[parser] def badLet: Parser[BadLetPE] = {
    //    (opt(templateRulesPR) <~ optWhite) ~
    badLetBegin ~
      (expression) ~
      pos ^^ {
      case BadLetBegin(range) ~ expr ~ end => {
        // We just threw away the topLevelRunes because let statements cant have them.
        BadLetPE(range)
      }
    }
  }

  private[parser] def lend: Parser[IExpressionPE] = {
    // TODO: split the 'a rule out when we implement regions
    pos ~
      (("*"| ("'" ~ opt(exprIdentifier <~ optWhite) <~ "*") | ("'" ~ exprIdentifier)) ~> opt("!") ~ (optWhite ~> postfixableExpressions)) ~
      pos ^^ {
      case begin ~ (maybeReadwrite ~ inner) ~ end => {
        LendPE(Range(begin, end), inner, LendConstraintP(Some(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP)))
      }
    }
  }

  private[parser] def weakLend: Parser[IExpressionPE] = {
    pos ~
      ("**" ~> optWhite ~> opt("!") ~ postfixableExpressions) ~
      pos ^^ {
      case begin ~ (maybeReadwrite ~ inner) ~ end => {
        LendPE(Range(begin, end), inner, LendWeakP(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP))
      }
    }
  }

  private[parser] def not: Parser[IExpressionPE] = {
    pos ~ (pstr("not") <~ white) ~ postfixableExpressions ~ pos ^^ {
      case begin ~ not ~ expr ~ end => {
        FunctionCallPE(Range(begin, end), None, Range(begin, begin), false, LookupPE(not, None), Vector(expr), LendConstraintP(Some(ReadonlyP)))
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
    pos ~ ("ret" ~> white ~> expression) ~ pos ^^ {
      case begin ~ sourceExpr ~ end => ReturnPE(Range(begin, end), sourceExpr)
    }
  }

  private[parser] def mutate: Parser[IExpressionPE] = {
    pos ~ (("set"|"mut") ~> white ~> expression <~ white <~ "=" <~ white) ~ expression ~ pos ^^ {
      case begin ~ destinationExpr ~ sourceExpr ~ end => MutatePE(Range(begin, end), destinationExpr, sourceExpr)
    }
  }

  private[parser] def bracedBlock: Parser[BlockPE] = {
    pos ~ ("{" ~> optWhite ~> blockExprs <~ optWhite <~ "}") ~ pos ^^ {
      case begin ~ exprs ~ end => BlockPE(Range(begin, end), exprs)
    }
  }

  private[parser] def eachOrEachI: Parser[FunctionCallPE] = {
    pos ~ (pstr("eachI") | pstr("each")) ~! (white ~> expressionLevel9 <~ white) ~ lambda ~ pos ^^ {
      case begin ~ eachI ~ collection ~ lam ~ end => {
        FunctionCallPE(
          Range(begin, end),
          None,
          Range(begin, begin),
          false,
          LookupPE(eachI, None),
          Vector(collection, lam),
          LendConstraintP(None))
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
        MatchPE(Range(begin, end), condExpr, lambdas.toVector)
      }
    }
  }

  private[parser] def ifPart: Parser[(BlockPE, BlockPE)] = {
    ("if" ~> optWhite ~> pos) ~ ("(" ~> optWhite ~> (let | badLet | expression) <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ^^ {
      case condBegin ~ condExpr ~ condEnd ~ thenLambda => {
        (BlockPE(Range(condBegin, condEnd), Vector(condExpr)), thenLambda)
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
            case None => BlockPE(Range(end, end), Vector(VoidPE(Range(end, end))))
            case Some(block) => block
          }
        val rootElseBlock =
          ifElses.foldRight(finalElse)({
            case ((condBlock, thenBlock), elseBlock) => {
              BlockPE(
                Range(condBlock.range.begin, thenBlock.range.end),
                Vector(
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
    //   fn main() int export { {_ + _}(4 + 5) }
    // because it mistakenly successfully parses {_ + _} then dies on the next part.
    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(NameP(Range(begin, end), "..."), None) }) |
    (mutate <~ optWhite <~ ";") |
    (let <~ optWhite <~ ";") |
    (badLet <~ optWhite <~ ";") |
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

  private[parser] def fourDigitHexNumber: Parser[Int] = {
    "([0-9a-fA-F]{4})".r ^^ { s =>
      Integer.parseInt(s, 16)
    }
  }

  private[parser] def shortStringPart: Parser[IExpressionPE] = {
//    ("\"" ~> failure("ended string")) |
    (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\t") }) |
    (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\r") }) |
    (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\n") }) |
    (pos ~ ("\\u" ~> fourDigitHexNumber) ~ pos ^^ { case begin ~ s ~ end => ConstantStrPE(Range(begin, end), s.toChar.toString) }) |
    (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\"") }) |
    (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\\") }) |
    (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "/") }) |
    (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "{") }) |
    (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "}") }) |
    (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\n") }) |
    (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\r") }) |
    ("{" ~> expression <~ "}") |
    (pos ~ (not("\\") ~> not("\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => ConstantStrPE(Range(begin, end), thing) })
  }

  private[parser] def shortStringExpr: Parser[IExpressionPE] = {
    pos ~ ("\"" ~> rep(shortStringPart) <~ "\"") ~ pos ^^ {
      case begin ~ parts ~ end => {
        simplifyStringInterpolate(StrInterpolatePE(Range(begin, end), parts.toVector))
      }
    }
  }

  private[parser] def longStringPart: Parser[IExpressionPE] = {
    ("\"\"\"" ~> failure("ended string")) |
    (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\t") }) |
    (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\r") }) |
    (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\n") }) |
    (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\"") }) |
    (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\\") }) |
    (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "/") }) |
    (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "{") }) |
    (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "}") }) |
    (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\n") }) |
    (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(Range(begin, end), "\r") }) |
    ("{" ~> expression <~ "}") |
    (pos ~ (not("\"\"\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => ConstantStrPE(Range(begin, end), thing) })
  }

  private[parser] def longStringExpr: Parser[IExpressionPE] = {
    pos ~ ("\"\"\"" ~> rep(longStringPart) <~ "\"\"\"") ~ pos ^^ {
      case begin ~ parts ~ end => {
        simplifyStringInterpolate(StrInterpolatePE(Range(begin, end), parts.toVector))
      }
    }
  }

  def simplifyStringInterpolate(stringExpr: StrInterpolatePE) = {
    def combine(previousReversed: List[IExpressionPE], next: List[IExpressionPE]): List[IExpressionPE] = {
      (previousReversed, next) match {
        case (ConstantStrPE(Range(prevBegin, _), prev) :: earlier, ConstantStrPE(Range(_, nextEnd), next) :: later) => {
          combine(ConstantStrPE(Range(prevBegin, nextEnd), prev + next) :: earlier, later)
        }
        case (earlier, next :: later) => {
          combine(next :: earlier, later)
        }
        case (earlier, Nil) => earlier
      }
    }

    val StrInterpolatePE(range, parts) = stringExpr

    combine(List.empty, parts.toList).reverse match {
      case Nil => ConstantStrPE(range, "")
      case List(s @ ConstantStrPE(_, _)) => s
      case multiple => StrInterpolatePE(range, multiple.toVector)
    }
  }

  private[parser] def stringExpr: Parser[IExpressionPE] = {
    longStringExpr | shortStringExpr
  }

  private[parser] def integerExpression: Parser[ConstantIntPE] = {
    pos ~ long ~ opt("i" ~> long) ~ pos ^^ {
      case begin ~ n ~ maybeBits ~ end => ConstantIntPE(Range(begin, end), n, maybeBits.getOrElse(32L).toInt)
    }
  }

  private[parser] def expressionElementLevel1: Parser[IExpressionPE] = {
    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(NameP(Range(begin, end), "..."), None) }) |
    stringExpr |
      integerExpression |
      bool |
      lambda |
      (pos ~ ("()" ~> pos) ^^ { // hack for shortcalling syntax highlighting
        case begin ~ end => {
          FunctionCallPE(
            Range(begin, end),
            None,
            Range(begin, begin),
            false,
            LookupPE(NameP(Range(begin, begin), ""), None),
            Vector.empty,
            LendConstraintP((None)))
        }
      }) |
//      callNamed |
//      callCallable |
      (pos ~ packExpr ~ pos ^^ {
        case begin ~ (p @ PackPE(_, Vector(inner))) ~ end => p
        case begin ~ inners ~ end => ShortcallPE(Range(begin, end), inners.inners)
      }) |
      arrayOrTuple |
      templateSpecifiedLookup |
      (lookup ^^ {
        case l @ LookupPE(NameP(r, "_"), None) => MagicParamLookupPE(r)
        case other => other
      })
  }

  private[parser] def expressionLevel5: Parser[IExpressionPE] = {

    sealed trait IStep
    case class MethodCallStep(
      stepRange: Range,
      operatorRange: Range,
      containerReadwrite: Boolean,
      isMapCall: Boolean,
      lookup: LookupPE,
      args: Vector[IExpressionPE]
    ) extends IStep { override def hashCode(): Int = vcurious() }
    case class MemberAccessStep(
      stepRange: Range,
      operatorRange: Range,
      isMapCall: Boolean,
      name: LookupPE
    ) extends IStep { override def hashCode(): Int = vcurious() }
    case class CallStep(
      stepRange: Range,
      operatorRange: Range,
      containerReadwrite: Boolean,
      isMapCall: Boolean,
      args: Vector[IExpressionPE]
    ) extends IStep { override def hashCode(): Int = vcurious() }
    case class IndexStep(
      stepRange: Range,
      args: Vector[IExpressionPE]
    ) extends IStep { override def hashCode(): Int = vcurious() }
    def step: Parser[IStep] = {
      def afterDot = {
        (integerExpression ^^ { case ConstantIntPE(range, value, bits) => LookupPE(NameP(range, value.toString), None) }) |
        templateSpecifiedLookup |
        lookup
      }

      ((pos <~ optWhite) ~ (opt("!") ~ opt("*") <~ ".") ~ (pos <~ optWhite) ~ (optWhite ~> afterDot) ~ opt(optWhite ~> packExpr) ~ pos ^^ {
        case begin ~ (readwriteContainer ~ mapCall) ~ opEnd ~ lookup ~ None ~ end => {
          MemberAccessStep(Range(begin, end), Range(begin, opEnd), mapCall.nonEmpty, lookup)
        }
        case begin ~ (readwriteContainer ~ mapCall) ~ opEnd ~ name ~ Some(args) ~ end => {
          MethodCallStep(
            Range(begin, end),
            Range(begin, opEnd),
            readwriteContainer.nonEmpty,
            mapCall.nonEmpty,
            name,
            args.inners)
        }
      }) |
      (pos ~ opt("!") ~ pos ~ packExpr ~ pos ^^ {
        case begin ~ readwriteContainer ~ opEnd ~ pack ~ end => {
          CallStep(
            Range(begin, end),
            Range(begin, opEnd),
            readwriteContainer.nonEmpty,
            false,
            pack.inners)
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
            case ((prevInline, prev), MethodCallStep(stepRange, operatorRange, subjectReadwrite, isMapCall, lookup, args)) => {
              val subjectLoadAs =
                (prev, subjectReadwrite) match {
                  case (PackPE(_, _), _) => UseP // This is like (moo).bork()
                  case (_, false) => LendConstraintP(Some(ReadonlyP)) // This is like moo.bork()
                  case (_, true) => LendConstraintP(Some(ReadwriteP)) // This is like moo!.bork()
                }
              (None, MethodCallPE(Range(begin, stepRange.end), prevInline, prev, operatorRange, subjectLoadAs, isMapCall, lookup, args))
            }
            case ((prevInline, prev), CallStep(stepRange, operatorRange, subjectReadwrite, isMapCall, args)) => {
              val subjectLoadAs =
                (prev, subjectReadwrite) match {
                  case (PackPE(_, _), false) => UseP // This is like (moo).bork()
                  case (PackPE(_, _), true) => UseP  // This is like (moo)!.bork(), which doesnt make much sense, the owned thing is already readwrite
                  case (_, false) => LendConstraintP(Some(ReadonlyP)) // This is like moo()
                  case (_, true) => LendConstraintP(Some(ReadwriteP)) // This is like moo!()
                }
              (None, FunctionCallPE(Range(begin, stepRange.end), prevInline, operatorRange, isMapCall, prev, args, subjectLoadAs))
            }
            case ((None, prev), MemberAccessStep(stepRange, operatorRange, isMapCall, name)) => {
              (None, DotPE(Range(begin, stepRange.end), prev, operatorRange, isMapCall, name.name))
            }
            case ((None, prev), IndexStep(stepRange, args)) => {
              (None, IndexPE(Range(begin, stepRange.end), prev, args))
            }
          })
        expr
      }
    })
  }

  private[parser] def expressionLevel9: Parser[IExpressionPE] = {
    weakLend | lend | not | expressionLevel5
  }

  // Parses expressions that can contain postfix operators, like function calling
  // or dots.
  private[parser] def postfixableExpressions: Parser[IExpressionPE] = {
    ret | mutate | ifLadder | expressionLevel9
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
        (range, op: NameP, left, right) => {
          FunctionCallPE(
            range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), Vector(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withAddSubtract =
      binariableExpression(
        withMultDiv,
        white ~> (pstr("+") | pstr("-")) <~ white,
        (range, op: NameP, left, right) => {
          FunctionCallPE(
            range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), Vector(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withCustomBinaries =
      binariableExpression(
        withAddSubtract,
        not(white ~> pstr("=") <~ white) ~>
          not(white ~> comparisonOperators <~ white) ~>
          not(white ~> conjunctionOperators <~ white) ~>
          (white ~> infixFunctionIdentifier <~ white),
        (range, funcName: NameP, left, right) => {
          FunctionCallPE(range, None, Range(funcName.range.begin, funcName.range.end), false, LookupPE(funcName, None), Vector(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withComparisons =
      binariableExpression(
        withCustomBinaries,
        not(white ~> conjunctionOperators <~ white) ~>
          white ~> comparisonOperators <~ white,
        (range, op: NameP, left, right) => {
          FunctionCallPE(range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), Vector(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withConjunctions =
      binariableExpression(
        withComparisons,
        white ~> conjunctionOperators <~ white,
        (range, op: NameP, left, right) => {
          op.str match {
            case "and" => AndPE(range, BlockPE(left.range, Vector(left)), BlockPE(right.range, Vector(right)))
            case "or" => OrPE(range, BlockPE(left.range, Vector(left)), BlockPE(right.range, Vector(right)))
          }
        })

    withConjunctions |
    (conjunctionOperators ^^ (op => LookupPE(op, None))) |
    (comparisonOperators ^^ (op => LookupPE(op, None)))
  }

  sealed trait StatementType
  case object FunctionReturnStatementType extends StatementType
  case object BlockReturnStatementType extends StatementType
  case object NormalResultStatementType extends StatementType

  // The boolean means it's a result or a return, it should be the last thing in the block.
  def statementOrResult: Parser[(IExpressionPE, Boolean)] = {
    pos ~ (opt("=" <~ optWhite | "ret" <~ white) ~ statement) ~ pos ^^ {
      case begin ~ (maybeResult ~ expr) ~ end => {
        (maybeResult, expr) match {
          case (None, expr) => (expr, false)
          case (Some("="), expr) => (expr, true)
          case (Some("ret"), expr) => (ReturnPE(Range(begin, end), expr), true)
        }
      }
    }
  }

  def multiStatementBlock: Parser[Vector[IExpressionPE]] = {
    // Can't just do a rep(statement <~ optWhite) ~ ("=" ~> optWhite ~> statement) because
    // "= doThings(a);" is actually a valid statement, which looks like doThings(=, a);
    rep1sep(statementOrResult, optWhite) ~ pos ^^ {
      case statements ~ end => {
        statements match {
          case Nil => Vector(VoidPE(Range(end, end)))
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
            exprs.toVector
          }
        }
      }
    }
  }

  def blockExprs: Parser[Vector[IExpressionPE]] = {
    multiStatementBlock |
      ((expression) ^^ { case e => Vector(e) }) |
      (pos ^^ { case p => Vector(VoidPE(Range(p, p)))}) |
      (pos ~ ("..." ~> pos) ^^ { case begin ~ end => Vector(VoidPE(Range(begin, end))) })
  }

  private[parser] def arrayOrTuple: Parser[IExpressionPE] = {
    // Static arrays have to come before tuples, because the beginning of static array looks kind of like a tuple.
    constructArrayExpr |
//    runtimeSizedArrayExpr |
    tuupleExpr
  }

  private[parser] def arraySize: Parser[IArraySizeP] = {
    ("*" ^^^ RuntimeSizedP) |
      (opt(templex) ^^ StaticSizedP)
  }

  private[parser] def arrayHeader: Parser[(Option[ITemplexPT], Option[ITemplexPT], IArraySizeP)] = {
    ((("[" ~> optWhite ~> opt(mutabilityAtomTemplex <~ optWhite)) ~
        (opt(variabilityAtomTemplex <~ optWhite)) ~
        (arraySize <~ optWhite <~ "]")) ^^ {
      case maybeMutability ~ maybeVariability ~ maybeSize => {
        (maybeMutability, maybeVariability, maybeSize)
      }
    }) |
    ((("[" ~> optWhite) ~>
      (opt(templex <~ optWhite) <~ "," <~ optWhite) ~
      (opt(templex <~ optWhite) <~ "," <~ optWhite) ~
      (arraySize <~ optWhite <~ "]")) ^^ {
      case maybeMutability ~ maybeVariability ~ maybeSize => {
        (maybeMutability, maybeVariability, maybeSize)
      }
    })
  }

  private[parser] def constructArrayExpr: Parser[IExpressionPE] = {
    pos ~
      (arrayHeader <~ optWhite) ~
      (("[" ~ (optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]")) |
        ("(" ~ (optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ ")"))) ~
      pos ^^ {
      case begin ~ ((maybeMutability, maybeVariability, size)) ~ (argsType ~ valueExprs) ~ end => {
        val initializingIndividualElements = argsType match { case "[" => true case "(" => false }
        ConstructArrayPE(
          Range(begin, end),
          maybeMutability,
          maybeVariability,
          size,
          initializingIndividualElements,
          valueExprs.toVector)
      }
    }
  }

  private[parser] def tuupleExpr: Parser[IExpressionPE] = {
    pos ~ ("[" ~> optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ a ~ end => {
        TuplePE(Range(begin, end), a.toVector)
      }
    }
  }

  private[parser] def packExpr: Parser[PackPE] = {
    pos ~ ("(" ~> optWhite ~> ("..." ^^^ Vector.empty | repsep(expression, optWhite ~> "," <~ optWhite)) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ inners ~ end => PackPE(Range(begin, end), inners.toVector)
    }
  }

  private[parser] def indexExpr: Parser[Vector[IExpressionPE]] = {
    "[" ~> optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]" ^^ (a => a.toVector)
  }

  private[parser] def lambda: Parser[LambdaPE] = {
    pos ~ existsMW("[]") ~ opt(patternPrototypeParams) ~ pos ~ bracedBlock ~ pos ^^ {
      case begin ~ maybeCaptures ~ maybeParams ~ headerEnd ~ block ~ end => {
        LambdaPE(
          maybeCaptures,
          FunctionP(
            Range(begin, end),
            FunctionHeaderP(
              Range(begin, headerEnd),
              None, Vector.empty, None, None, maybeParams, FunctionReturnP(Range(headerEnd, headerEnd), None, None)),
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
      case begin ~ params ~ end => ParamsP(Range(begin, end), params.toVector)
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
              None, Vector.empty, None, None,
              Some(ParamsP(Range(begin, paramsEnd), Vector(param))),
              FunctionReturnP(Range(end, end), None, None)), Some(body)))
      }
    }
  }
}
