package net.verdagon.vale.parser

import net.verdagon.vale.parser.patterns.PatternParser
import net.verdagon.vale.{vassert, vcheck, vcurious, vfail}
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

  case class BadLetBegin(range: Range)

  case class LetBegin(begin: Int, patternPP: PatternPP)

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
        LetPE(Range(begin, end), /*maybeTemplateRules.getOrElse(List())*/None, pattern, expr)
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
      (("&"| ("'" ~ opt(exprIdentifier <~ optWhite) <~ "&") | ("'" ~ exprIdentifier)) ~> opt("!") ~ (optWhite ~> postfixableExpressions)) ~
      pos ^^ {
      case begin ~ (maybeReadwrite ~ inner) ~ end => {
        LendPE(Range(begin, end), inner, LendConstraintP(Some(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP)))
      }
    }
  }

  private[parser] def weakLend: Parser[IExpressionPE] = {
    pos ~
      ("&&" ~> optWhite ~> opt("!") ~ postfixableExpressions) ~
      pos ^^ {
      case begin ~ (maybeReadwrite ~ inner) ~ end => {
        LendPE(Range(begin, end), inner, LendWeakP(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP))
      }
    }
  }

  private[parser] def not: Parser[IExpressionPE] = {
    pos ~ (pstr("not") <~ white) ~ postfixableExpressions ~ pos ^^ {
      case begin ~ not ~ expr ~ end => {
        FunctionCallPE(Range(begin, end), None, Range(begin, begin), false, LookupPE(not, None), List(expr), LendConstraintP(Some(ReadonlyP)))
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
        FunctionCallPE(
          Range(begin, end),
          None,
          Range(begin, begin),
          false,
          LookupPE(eachI, None),
          List(collection, lam),
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
        MatchPE(Range(begin, end), condExpr, lambdas)
      }
    }
  }

  private[parser] def ifPart: Parser[(BlockPE, BlockPE)] = {
    ("if" ~> optWhite ~> pos) ~ ("(" ~> optWhite ~> (let | badLet | expression) <~ optWhite <~ ")") ~ (pos <~ optWhite) ~ bracedBlock ^^ {
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

  def simplifyStringInterpolate(stringExpr: StrInterpolatePE) = {
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

//  private[parser] def callCallable: Parser[IExpressionPE] = {
//    (pos <~ "(" <~ optWhite) ~
//      (expression <~ optWhite <~ ")" <~ optWhite) ~
//      packExpr ~
//      pos ^^ {
//      case begin ~ callable ~ args ~ end => {
//        FunctionCallPE(Range(begin, end), None, Range(begin, end), false, callable, args, LendConstraintP(None))
//      }
//    }
//  }
//
//  private[parser] def callNamed: Parser[IExpressionPE] = {
//    pos ~
//      (existsW("inl") <~ optWhite) ~
//      // We dont have the optWhite here because we dont want to allow spaces before calls.
//      // We dont want to allow moo (4) because we want each statements like this:
//      //   each moo (x){ println(x); }
//      ((templateSpecifiedLookup | lookup) /*SEE ABOVE <~ optWhite*/) ~
//      packExpr ~
//      pos ^^ {
//      case begin ~ maybeInl ~ callable ~ args ~ end => {
//        FunctionCallPE(Range(begin, end), maybeInl, Range(begin, end), false, callable, args, LendConstraintP(None))
//      }
//    }
//  }

  private[parser] def expressionElementLevel1: Parser[IExpressionPE] = {
    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(NameP(Range(begin, end), "..."), None) }) |
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
            LookupPE(NameP(Range(begin, begin), ""), None),
            List(),
            LendConstraintP((None)))
        }
      }) |
//      callNamed |
//      callCallable |
      (pos ~ packExpr ~ pos ^^ {
        case begin ~ (p @ PackPE(_, List(inner))) ~ end => p
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
      containerReadwrite: Boolean,
      isMapCall: Boolean,
      args: List[IExpressionPE]
    ) extends IStep
    case class IndexStep(
      stepRange: Range,
      args: List[IExpressionPE]
    ) extends IStep
    def step: Parser[IStep] = {
      def afterDot = {
        (integer ^^ { case IntLiteralPE(range, value) => LookupPE(NameP(range, value.toString), None) }) |
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
//              val subjectLoadAs =
//                (prev, subjectReadwrite) match {
//                  case (LookupPE(_, _), false) => LendConstraintP(Some(ReadonlyP)) // This is like moo.bork()
//                  case (LookupPE(_, _), true) => LendConstraintP(Some(ReadwriteP)) // This is like moo!.bork()
//                  case (DotPE(_, _, _, _, _), false) => LendConstraintP(Some(ReadonlyP)) // This is like foo.moo.bork()
//                  case (DotPE(_, _, _, _, _), true) => LendConstraintP(Some(ReadwriteP)) // This is like foo.moo!.bork()
//                  case (IndexPE(_, _, _), false) => LendConstraintP(Some(ReadonlyP)) // This is like foo[3].bork()
//                  case (IndexPE(_, _, _), true) => LendConstraintP(Some(ReadwriteP)) // This is like foo[3]!.bork()
//                  case (LendPE(_, LookupPE(_, _), _), false) => vcurious() // This shouldnt be possible in our syntax
//                  case (LendPE(_, LookupPE(_, _), _), true) => vcurious() // This shouldnt be possible in our syntax
//                  case (_, false) => UseP // this is like (moo).bork();
//                  case (_, true) => vcurious() // this is like (moo)!.bork(), which seems needless?
//                }

              val subjectLoadAs =
                (prev, subjectReadwrite) match {
                  case (PackPE(_, _), _) => UseP // This is like (moo).bork()
                  case (_, false) => LendConstraintP(Some(ReadonlyP)) // This is like moo.bork()
                  case (_, true) => LendConstraintP(Some(ReadwriteP)) // This is like moo!.bork()
                }
              (None, MethodCallPE(Range(begin, stepRange.end), prevInline, prev, operatorRange, subjectLoadAs, isMapCall, lookup, args))
            }
            case ((prevInline, prev), CallStep(stepRange, operatorRange, subjectReadwrite, isMapCall, args)) => {
//              val subjectLoadAs =
//                (prev, subjectReadwrite) match {
//                  case (LookupPE(_, _), false) => LendConstraintP(Some(ReadonlyP)) // This is like moo()
//                  case (LookupPE(_, _), true) => LendConstraintP(Some(ReadwriteP)) // This is like moo!()
//                  case (DotPE(_, _, _, _, _), false) => vcurious() // This would be like foo.moo(), but that should become a method call
//                  case (DotPE(_, _, _, _, _), true) => vcurious() // This would be like foo.moo!(), but that should become a method call
//                  case (IndexPE(_, _, _), false) => LendConstraintP(Some(ReadonlyP)) // This is like foo[3]()
//                  case (IndexPE(_, _, _), true) => LendConstraintP(Some(ReadwriteP)) // This is like foo[3]!()
//                  case (LendPE(_, LookupPE(_, _), _), false) => vcurious() // This shouldnt be possible in our syntax
//                  case (LendPE(_, LookupPE(_, _), _), true) => vcurious() // This shouldnt be possible in our syntax
//                  case (_, false) => UseP // this is like (moo)();
//                  case (_, true) => vcurious() // this is like (moo)!(), which seems needless?
//                }

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
            range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), List(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withAddSubtract =
      binariableExpression(
        withMultDiv,
        white ~> (pstr("+") | pstr("-")) <~ white,
        (range, op: NameP, left, right) => {
          FunctionCallPE(
            range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), List(left, right), LendConstraintP(Some(ReadonlyP)))
        })

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
        (range, funcName: NameP, left, right) => {
          FunctionCallPE(range, None, Range(funcName.range.begin, funcName.range.end), false, LookupPE(funcName, None), List(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withComparisons =
      binariableExpression(
        withCustomBinaries,
        not(white ~> conjunctionOperators <~ white) ~>
          white ~> comparisonOperators <~ white,
        (range, op: NameP, left, right) => {
          FunctionCallPE(range, None, Range(op.range.begin, op.range.begin), false, LookupPE(op, None), List(left, right), LendConstraintP(Some(ReadonlyP)))
        })

    val withConjunctions =
      binariableExpression(
        withComparisons,
        white ~> conjunctionOperators <~ white,
        (range, op: NameP, left, right) => {
          op.str match {
            case "and" => AndPE(range, BlockPE(left.range, List(left)), BlockPE(right.range, List(right)))
            case "or" => OrPE(range, BlockPE(left.range, List(left)), BlockPE(right.range, List(right)))
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

  private[parser] def arrayOrTuple: Parser[IExpressionPE] = {
    // Static arrays have to come before tuples, because the beginning of static array looks kind of like a tuple.
    constructArrayExpr |
//    runtimeArrayExpr |
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
          valueExprs)
      }
    }
  }

  private[parser] def tuupleExpr: Parser[IExpressionPE] = {
    pos ~ ("[" ~> optWhite ~> repsep(expression, optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
      case begin ~ (a:List[IExpressionPE]) ~ end => {
        TuplePE(Range(begin, end), a)
      }
    }
  }

  private[parser] def packExpr: Parser[PackPE] = {
    pos ~ ("(" ~> optWhite ~> ("..." ^^^ List() | repsep(expression, optWhite ~> "," <~ optWhite)) <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ inners ~ end => PackPE(Range(begin, end), inners)
    }
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
