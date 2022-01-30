package net.verdagon.vale.parser.old

import net.verdagon.vale.parser.ast.{AndPE, BlockPE, ConsecutorPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, DestructPE, DotPE, EachPE, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IArraySizeP, IExpressionPE, ITemplexPT, IfPE, IndexPE, LambdaPE, LetPE, LoadAsBorrowOrIfContainerIsPointerThenPointerP, LoadAsBorrowP, LoadAsPointerP, LoadAsWeakP, LoadPE, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutatePE, NameP, OrPE, PackPE, ParamsP, PatternPP, RangeP, ReadonlyP, ReadwriteP, ReturnPE, RuntimeSizedP, ShortcallPE, StaticSizedP, StrInterpolatePE, TemplateArgsP, TemplateRulesP, TuplePE, UseP, VoidPE, WhilePE}
import net.verdagon.vale.parser.ast
import net.verdagon.vale.vcurious

import scala.util.parsing.combinator.RegexParsers

trait ExpressionParser extends RegexParsers with ParserUtils with TemplexParser {
//  private[old] def templex: Parser[ITemplexPT]
//
//  private[old] def templateRulesPR: Parser[TemplateRulesP]
//
  private[parser] def atomPattern: Parser[PatternPP]
  //  private[old] def templex: Parser[ITemplexPT]

  private[old] def comparisonOperators: Parser[NameP] = {
    (pstr("<=>") | pstr("<=") | pstr("<") | pstr(">=") | pstr(">") | pstr("===") | pstr("==") | pstr("!="))
  }
//
//  private[old] def conjunctionOperators: Parser[NameP] = {
//    (pstr("and") | pstr("or"))
//  }
//
//  private[old] def lookup: Parser[LookupPE] = {
//    //    ("`" ~> "[^`]+".r <~ "`" ^^ (i => LookupPE(i, Vector.empty))) |
//    (exprIdentifier ^^ (i => LookupPE(LookupNameP(i), None)))
//  }
//
//  private[old] def templateArgs: Parser[TemplateArgsP] = {
//    pos ~ ("<" ~> optWhite ~> repsep(templex, optWhite ~> "," <~ optWhite) <~ optWhite <~ ">") ~ pos ^^ {
//      case begin ~ args ~ end => {
//        ast.TemplateArgsP(ast.RangeP(begin, end), args.toVector)
//      }
//    }
//  }
//
//  private[old] def templateSpecifiedLookup: Parser[LookupPE] = {
//    (exprIdentifier <~ optWhite) ~ templateArgs ^^ {
//      case name ~ templateArgs => ast.LookupPE(ast.LookupNameP(name), Some(templateArgs))
//    }
//  }
//
//  case class BadLetBegin(range: ast.RangeP) {
//    override def hashCode(): Int = vcurious()
//  }
//
//  case class LetBegin(begin: Int, patternPP: PatternPP) {
//    override def hashCode(): Int = vcurious()
//  }
//
//  private[old] def letBegin: Parser[LetBegin] = {
//    pos ~ (atomPattern <~ white <~ "=" <~ white) ^^ {
//      case begin ~ pattern => LetBegin(begin, pattern)
//    }
//  }
//
//  private[old] def badLetBegin: Parser[BadLetBegin] = {
//    pos ~ expressionLevel5(true) ~ (pos <~ white <~ "=" <~ white) ^^ {
//      case begin ~ _ ~ end => BadLetBegin(ast.RangeP(begin, end))
//    }
//  }
//
//  private[old] def let(allowLambda: Boolean): Parser[LetPE] = {
//    //    (opt(templateRulesPR) <~ optWhite) ~
//    letBegin ~
//      (expression(allowLambda)) ~
//      pos ^^ {
//      case LetBegin(begin, pattern) ~ expr ~ end => {
//        // We just threw away the topLevelRunes because let statements cant have them.
//        ast.LetPE(ast.RangeP(begin, end), /*maybeTemplateRules.getOrElse(Vector.empty)*/ None, pattern, expr)
//      }
//    }
//  }
//
//  //  private[old] def badLet(allowLambda: Boolean): Parser[BadLetPE] = {
//  //    //    (opt(templateRulesPR) <~ optWhite) ~
//  //    badLetBegin ~
//  //      (expression(allowLambda)) ~
//  //      pos ^^ {
//  //      case BadLetBegin(range) ~ expr ~ end => {
//  //        // We just threw away the topLevelRunes because let statements cant have them.
//  //        BadLetPE(range)
//  //      }
//  //    }
//  //  }
//
//  private[old] def borrow: Parser[IExpressionPE] = {
//    // TODO: split the 'a rule out when we implement regions
//    pos ~
//      (("&" | ("'" ~ opt(exprIdentifier <~ optWhite) <~ "&") | ("'" ~ exprIdentifier)) ~> opt("!") ~ (optWhite ~> postfixableExpressions(true))) ~
//      pos ^^ {
//      case begin ~ (maybeReadwrite ~ inner) ~ end => {
//        LoadPE(ast.RangeP(begin, end), inner, LoadAsBorrowP(Some(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP)))
//      }
//    }
//  }
//
//  private[old] def point: Parser[IExpressionPE] = {
//    // TODO: split the 'a rule out when we implement regions
//    pos ~
//      (("*" | ("'" ~ opt(exprIdentifier <~ optWhite) <~ "*") | ("'" ~ exprIdentifier)) ~> opt("!") ~ (optWhite ~> postfixableExpressions(true))) ~
//      pos ^^ {
//      case begin ~ (maybeReadwrite ~ inner) ~ end => {
//        LoadPE(ast.RangeP(begin, end), inner, LoadAsPointerP(Some(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP)))
//      }
//    }
//  }
//
//  private[old] def weakPoint: Parser[IExpressionPE] = {
//    pos ~
//      ("**" ~> optWhite ~> opt("!") ~ postfixableExpressions(true)) ~
//      pos ^^ {
//      case begin ~ (maybeReadwrite ~ inner) ~ end => {
//        LoadPE(ast.RangeP(begin, end), inner, LoadAsWeakP(if (maybeReadwrite.nonEmpty) ReadwriteP else ReadonlyP))
//      }
//    }
//  }
//
//  private[old] def not: Parser[IExpressionPE] = {
//    pos ~ (pstr("not") <~ white) ~ postfixableExpressions(true) ~ pos ^^ {
//      case begin ~ not ~ expr ~ end => {
//        FunctionCallPE(ast.RangeP(begin, end), ast.RangeP(begin, begin), LookupPE(ast.LookupNameP(not), None), Vector(expr), false)
//      }
//    }
//  }
//
//  private[old] def destruct: Parser[IExpressionPE] = {
//    (pos <~ "destruct" <~ white) ~ expression(true) ~ pos ^^ {
//      case begin ~ inner ~ end => {
//        DestructPE(ast.RangeP(begin, end), inner)
//      }
//    }
//  }
//
//  private[old] def ret: Parser[IExpressionPE] = {
//    pos ~ ("ret" ~> white ~> expression(true)) ~ pos ^^ {
//      case begin ~ sourceExpr ~ end => ReturnPE(ast.RangeP(begin, end), sourceExpr)
//    }
//  }
//
//  private[old] def mutate: Parser[IExpressionPE] = {
//    pos ~ (("set" | "mut") ~> white ~> expression(true) <~ white <~ "=" <~ white) ~ expression(true) ~ pos ^^ {
//      case begin ~ destinationExpr ~ sourceExpr ~ end => MutatePE(ast.RangeP(begin, end), destinationExpr, sourceExpr)
//    }
//  }
//
//  private[old] def bracedBlock: Parser[BlockPE] = {
//    pos ~ ("{" ~> optWhite ~> blockExprs(true) <~ optWhite <~ "}") ~ pos ^^ {
//      case begin ~ exprs ~ end => BlockPE(ast.RangeP(begin, end), exprs)
//    }
//  }
//
//  private[old] def eachOrEachI: Parser[FunctionCallPE] = {
//    (pos <~ opt("parallel" <~ white)) ~
//      (pstr("eachI") | pstr("each")) ~!
//      (white ~> expressionLevel9(false) <~ white) ~
//      lambda ~
//      pos ^^ {
//      case begin ~ eachI ~ collection ~ lam ~ end => {
//        ast.FunctionCallPE(
//          ast.RangeP(begin, end),
//          ast.RangeP(begin, begin),
//          LookupPE(ast.LookupNameP(eachI), None),
//          Vector(collection, lam),
//          false)
//      }
//    }
//  }
//
//  private[old] def foreach: Parser[EachPE] = {
//    (pos <~ opt("parallel" <~ white) <~ pstr("foreach") <~ white) ~!
//      (atomPattern <~ white) ~ (pos <~ "in") ~ (pos <~ white) ~
//      (expressionLevel9(false) <~ optWhite) ~
//      bracedBlock ~
//      pos ^^ {
//      case begin ~ pattern ~ inBegin ~ inEnd ~ iterableExpr ~ block ~ end => {
//        ast.EachPE(
//          ast.RangeP(begin, end),
//          pattern,
//          ast.RangeP(inBegin, inEnd),
//          iterableExpr,
//          block)
//      }
//    }
//  }
//
//  private[old] def whiile: Parser[WhilePE] = {
//    pos ~ ("while" ~>! optWhite ~> pos) ~ blockExprs(false) ~ (pos <~ optWhite) ~ bracedBlock ~ pos ^^ {
//      case begin ~ condBegin ~ condExprs ~ condEnd ~ thenBlock ~ end => {
//        ast.WhilePE(ast.RangeP(begin, end), BlockPE(ast.RangeP(condBegin, condEnd), condExprs), thenBlock)
//      }
//    }
//  }
//
////  private[old] def mat: Parser[MatchPE] = {
////    pos ~
////      ("mat" ~> white ~> postfixableExpressions(false) <~ white) ~
////      ("{" ~> optWhite ~> repsep(caase, optWhite) <~ optWhite <~ "}") ~
////      pos ^^ {
////      case begin ~ condExpr ~ lambdas ~ end => {
////        ast.MatchPE(ast.RangeP(begin, end), condExpr, lambdas.toVector)
////      }
////    }
////  }
//
//  private[old] def ifPart: Parser[(IExpressionPE, BlockPE)] = {
//    ("if" ~> optWhite ~> pos) ~ blockExprs(false) ~ (pos <~ optWhite) ~ bracedBlock ^^ {
//      case condBegin ~ condExprs ~ condEnd ~ thenLambda => {
//        (condExprs, thenLambda)
//      }
//    }
//  }
//
//  private[old] def ifLadder: Parser[IExpressionPE] = {
//    pos ~
//      ifPart ~
//      rep(optWhite ~> "else" ~> optWhite ~> ifPart) ~
//      opt(optWhite ~> "else" ~> optWhite ~> bracedBlock) ~
//      pos ^^ {
//      case begin ~ rootIf ~ ifElses ~ maybeElseBlock ~ end => {
//        val finalElse: BlockPE =
//          maybeElseBlock match {
//            case None => BlockPE(ast.RangeP(end, end), VoidPE(ast.RangeP(end, end)))
//            case Some(block) => block
//          }
//        val rootElseBlock =
//          ifElses.foldRight(finalElse)({
//            case ((condBlock, thenBlock), elseBlock) => {
//              BlockPE(
//                ast.RangeP(condBlock.range.begin, thenBlock.range.end),
//                IfPE(
//                  ast.RangeP(condBlock.range.begin, thenBlock.range.end),
//                  condBlock, thenBlock, elseBlock))
//            }
//          })
//        val (rootConditionLambda, rootThenLambda) = rootIf
//        ast.IfPE(ast.RangeP(begin, end), rootConditionLambda, rootThenLambda, rootElseBlock)
//      }
//    }
//  }
//
//  private[old] def statement(allowLambda: Boolean): Parser[IExpressionPE] = {
//    // bracedBlock is at the end because we want to be able to parse "{print(_)}(4);" as an expression.
//    // debt: "block" is here temporarily because we get ambiguities in this case:
//    //   fn main() int export { {_ + _}(4 + 5) }
//    // because it mistakenly successfully parses {_ + _} then dies on the next part.
//    (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(LookupNameP(NameP(ast.RangeP(begin, end), "...")), None) }) |
//      (mutate <~ optWhite <~ ";") |
//      (let(allowLambda) <~ optWhite <~ ";") |
//      //    (badLet(allowLambda) <~ optWhite <~ ";") |
////      mat |
//      (destruct <~ optWhite <~ ";") |
//      whiile |
//      foreach |
//      eachOrEachI |
//      ifLadder |
//      (expression(allowLambda) <~ ";") |
//      blockStatement
//  }
//
//  private[old] def blockStatement = {
//    ("block" ~> optWhite ~> bracedBlock)
//  }
//
//  private[old] def fourDigitHexNumber: Parser[Int] = {
//    "([0-9a-fA-F]{4})".r ^^ { s =>
//      Integer.parseInt(s, 16)
//    }
//  }
//
//  private[old] def shortStringPart: Parser[IExpressionPE] = {
//    //    ("\"" ~> failure("ended string")) |
//    (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\t") }) |
//      (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\r") }) |
//      (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\n") }) |
//      (pos ~ ("\\u" ~> fourDigitHexNumber) ~ pos ^^ { case begin ~ s ~ end => ConstantStrPE(ast.RangeP(begin, end), s.toChar.toString) }) |
//      (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\"") }) |
//      (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\\") }) |
//      (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "/") }) |
//      (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "{") }) |
//      (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "}") }) |
//      (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\n") }) |
//      (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\r") }) |
//      ("{" ~> expression(true) <~ "}") |
//      (pos ~ (not("\\") ~> not("\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => ConstantStrPE(ast.RangeP(begin, end), thing) })
//  }
//
//  private[old] def shortStringExpr: Parser[IExpressionPE] = {
//    pos ~ ("\"" ~> rep(shortStringPart) <~ "\"") ~ pos ^^ {
//      case begin ~ parts ~ end => {
//        simplifyStringInterpolate(StrInterpolatePE(ast.RangeP(begin, end), parts.toVector))
//      }
//    }
//  }
//
//  private[old] def longStringPart: Parser[IExpressionPE] = {
//    ("\"\"\"" ~> failure("ended string")) |
//      (pos ~ "\\t" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\t") }) |
//      (pos ~ "\\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\r") }) |
//      (pos ~ "\\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\n") }) |
//      (pos ~ "\\\"" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\"") }) |
//      (pos ~ "\\\\" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\\") }) |
//      (pos ~ "\\/" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "/") }) |
//      (pos ~ "\\{" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "{") }) |
//      (pos ~ "\\}" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "}") }) |
//      (pos ~ "\n" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\n") }) |
//      (pos ~ "\r" ~ pos ^^ { case begin ~ _ ~ end => ConstantStrPE(ast.RangeP(begin, end), "\r") }) |
//      ("{" ~> expression(true) <~ "}") |
//      (pos ~ (not("\"\"\"") ~> ".".r) ~ pos ^^ { case begin ~ thing ~ end => ConstantStrPE(ast.RangeP(begin, end), thing) })
//  }
//
//  private[old] def longStringExpr: Parser[IExpressionPE] = {
//    pos ~ ("\"\"\"" ~> rep(longStringPart) <~ "\"\"\"") ~ pos ^^ {
//      case begin ~ parts ~ end => {
//        simplifyStringInterpolate(StrInterpolatePE(ast.RangeP(begin, end), parts.toVector))
//      }
//    }
//  }
//
//  private[old] def simplifyStringInterpolate(stringExpr: StrInterpolatePE) = {
//    def combine(previousReversed: List[IExpressionPE], next: List[IExpressionPE]): List[IExpressionPE] = {
//      (previousReversed, next) match {
//        case (ConstantStrPE(RangeP(prevBegin, _), prev) :: earlier, ConstantStrPE(RangeP(_, nextEnd), next) :: later) => {
//          combine(ConstantStrPE(RangeP(prevBegin, nextEnd), prev + next) :: earlier, later)
//        }
//        case (earlier, next :: later) => {
//          combine(next :: earlier, later)
//        }
//        case (earlier, Nil) => earlier
//      }
//    }
//
//    val StrInterpolatePE(range, parts) = stringExpr
//
//    combine(List.empty, parts.toList).reverse match {
//      case Nil => ConstantStrPE(range, "")
//      case List(s@ConstantStrPE(_, _)) => s
//      case multiple => StrInterpolatePE(range, multiple.toVector)
//    }
//  }
//
//  private[old] def stringExpr: Parser[IExpressionPE] = {
//    longStringExpr | shortStringExpr
//  }
//
//  private[old] def integerExpression: Parser[ConstantIntPE] = {
//    pos ~ long ~ opt("i" ~> long) ~ pos ^^ {
//      case begin ~ n ~ maybeBits ~ end => ConstantIntPE(RangeP(begin, end), n, maybeBits.getOrElse(32L).toInt)
//    }
//  }
//
//  private[old] def expressionElementLevel1(allowLambda: Boolean): Parser[IExpressionPE] = {
//    def shortcall: Parser[IExpressionPE] = {
//      (pos ~ ("()" ~> pos) ^^ { // hack for shortcalling syntax highlighting
//        case begin ~ end => {
//          FunctionCallPE(
//            RangeP(begin, end),
//            RangeP(begin, begin),
//            LookupPE(LookupNameP(NameP(RangeP(begin, begin), "")), None),
//            Vector.empty,
//            false)
//        }
//      }) |
//        (pos ~ packExpr ~ pos ^^ {
//          case begin ~ (p@PackPE(_, Vector(inner))) ~ end => p
//          case begin ~ inners ~ end => ShortcallPE(RangeP(begin, end), inners.inners)
//        })
//    }
//
//    def ellipsis: Parser[IExpressionPE] = {
//      (pos ~ ("..." ~> pos) ^^ { case begin ~ end => LookupPE(LookupNameP(NameP(RangeP(begin, end), "...")), None) })
//    }
//
//    ellipsis |
//      stringExpr |
//      integerExpression |
//      bool |
//      (if (allowLambda) lambda else failure("Lambda not allowed here")) |
//      shortcall |
//      arrayOrTuple |
//      templateSpecifiedLookup |
//      (lookup ^^ {
//        case l@LookupPE(LookupNameP(NameP(r, "_")), None) => MagicParamLookupPE(r)
//        case other => other
//      })
//  }
//
//  private[old] def expressionLevel5(allowLambda: Boolean): Parser[IExpressionPE] = {
//    sealed trait IStep
//    case class MethodCallStep(
//      stepRange: RangeP,
//      operatorRange: RangeP,
//      containerReadwrite: Boolean,
//      isMapCall: Boolean,
//      lookup: LookupPE,
//      args: Vector[IExpressionPE]
//    ) extends IStep {
//      override def hashCode(): Int = vcurious()
//    }
//    case class MemberAccessStep(
//      stepRange: RangeP,
//      operatorRange: RangeP,
//      isMapCall: Boolean,
//      name: LookupPE
//    ) extends IStep {
//      override def hashCode(): Int = vcurious()
//    }
//    case class CallStep(
//      stepRange: RangeP,
//      operatorRange: RangeP,
//      containerReadwrite: Boolean,
//      isMapCall: Boolean,
//      args: Vector[IExpressionPE]
//    ) extends IStep {
//      override def hashCode(): Int = vcurious()
//    }
//    case class IndexStep(
//      stepRange: RangeP,
//      args: Vector[IExpressionPE]
//    ) extends IStep {
//      override def hashCode(): Int = vcurious()
//    }
//
//    def step: Parser[IStep] = {
//      def afterDot = {
//        (integerExpression ^^ { case ConstantIntPE(range, value, bits) => LookupPE(LookupNameP(NameP(range, value.toString)), None) }) |
//          templateSpecifiedLookup |
//          lookup
//      }
//
//      ((pos <~ optWhite) ~ (opt("!") ~ opt("*") <~ ".") ~ (pos <~ optWhite) ~ (optWhite ~> afterDot) ~ opt(optWhite ~> packExpr) ~ pos ^^ {
//        case begin ~ (readwriteContainer ~ mapCall) ~ opEnd ~ lookup ~ None ~ end => {
//          MemberAccessStep(RangeP(begin, end), RangeP(begin, opEnd), mapCall.nonEmpty, lookup)
//        }
//        case begin ~ (readwriteContainer ~ mapCall) ~ opEnd ~ name ~ Some(args) ~ end => {
//          MethodCallStep(
//            RangeP(begin, end),
//            RangeP(begin, opEnd),
//            readwriteContainer.nonEmpty,
//            mapCall.nonEmpty,
//            name,
//            args.inners)
//        }
//      }) |
//        (pos ~ opt("!") ~ pos ~ packExpr ~ pos ^^ {
//          case begin ~ readwriteContainer ~ opEnd ~ pack ~ end => {
//            CallStep(
//              RangeP(begin, end),
//              RangeP(begin, opEnd),
//              readwriteContainer.nonEmpty,
//              false,
//              pack.inners)
//          }
//        }) |
//        ((pos <~ optWhite) ~ indexExpr ~ pos ^^ { case begin ~ i ~ end => IndexStep(RangeP(begin, end), i) })
//    }
//
//    // The float is up here because we don't want the 0.0 in [[4]].0.0 to be parsed as a float.
//    float |
//      // We dont have the optWhite here because we dont want to allow spaces before calls.
//      // We dont want to allow moo (4) because we want each statements like this:
//      //   each moo (x){ println(x); }
//      (pos ~ existsW("inl") ~ expressionElementLevel1(allowLambda) ~ rep(/*SEE ABOVE optWhite ~> */ step) ~ pos ^^ {
//        case begin ~ maybeInline ~ first ~ restWithDots ~ end => {
//          val (_, expr) =
//            restWithDots.foldLeft((maybeInline, first))({
//              case ((prevInline, prev), MethodCallStep(stepRange, operatorRange, subjectReadwrite, isMapCall, lookup, args)) => {
//                val subjectLoadAs =
//                  (prev, subjectReadwrite) match {
//                    case (PackPE(_, _), _) => UseP // This is like (moo).bork()
//                    case (_, false) => LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadonlyP)) // This is like moo.bork()
//                    case (_, true) => LoadAsBorrowOrIfContainerIsPointerThenPointerP(Some(ReadwriteP)) // This is like moo!.bork()
//                  }
//                (None, MethodCallPE(RangeP(begin, stepRange.end), prev, operatorRange, false, lookup, args))
//              }
//              case ((prevInline, prev), CallStep(stepRange, operatorRange, subjectReadwrite, isMapCall, args)) => {
//                val subjectLoadAs =
//                  (prev, subjectReadwrite) match {
//                    case (PackPE(_, _), false) => UseP // This is like (moo).bork()
//                    case (PackPE(_, _), true) => UseP // This is like (moo)!.bork(), which doesnt make much sense, the owned thing is already readwrite
//                    case (_, false) => LoadAsBorrowP(Some(ReadonlyP)) // This is like moo()
//                    case (_, true) => LoadAsBorrowP(Some(ReadwriteP)) // This is like moo!()
//                  }
//                (None, ast.FunctionCallPE(RangeP(begin, stepRange.end), operatorRange, prev, args, false))
//              }
//              case ((None, prev), MemberAccessStep(stepRange, operatorRange, isMapCall, LookupPE(LookupNameP(name), _))) => {
//                (None, DotPE(RangeP(begin, stepRange.end), prev, operatorRange, name))
//              }
//              case ((None, prev), IndexStep(stepRange, args)) => {
//                (None, IndexPE(RangeP(begin, stepRange.end), prev, args))
//              }
//            })
//          expr
//        }
//      })
//  }
//
//  private[old] def expressionLevel9(allowLambda: Boolean): Parser[IExpressionPE] = {
//    weakPoint | borrow | point | not | expressionLevel5(allowLambda)
//  }
//
//  // Parses expressions that can contain postfix operators, like function calling
//  // or dots.
//  private[old] def postfixableExpressions(allowLambda: Boolean): Parser[IExpressionPE] = {
//    ret | mutate | ifLadder | foreach | expressionLevel9(allowLambda)
//  }
//
//  // Binariable = can have binary operators in it
//  private[old] def binariableExpression[T](
//    innerParser: Parser[IExpressionPE],
//    binaryOperatorParser: Parser[T],
//    combiner: (RangeP, T, IExpressionPE, IExpressionPE) => IExpressionPE): Parser[IExpressionPE] = {
//    pos ~ innerParser ~ rep(binaryOperatorParser ~ innerParser ~ pos) ^^ {
//      case begin ~ firstElement ~ restBinaryOperatorsAndElements => {
//        val (_, resultExpr) =
//          restBinaryOperatorsAndElements.foldLeft((begin, firstElement))({
//            case ((previousResultBegin, previousResult), (operator ~ nextElement ~ nextElementEnd)) => {
//              val range = RangeP(previousResultBegin, nextElementEnd)
//              val combinedExpr = combiner(range, operator, previousResult, nextElement)
//              (nextElementEnd, combinedExpr)
//            }
//          })
//        resultExpr
//      }
//    }
//  }
//
//  private[old] def expression(allowLambda: Boolean): Parser[IExpressionPE] = {
//    // These extra white parsers are here otherwise we stop early when we're just looking for "<", in "<=".
//    // Expecting the whitespace means we parse the entire operator.
//
//    val withRange =
//      binariableExpression(
//        postfixableExpressions(allowLambda),
//        optWhite ~> pstr("..") <~ optWhite,
//        (range, op: NameP, left, right) => {
//          FunctionCallPE(
//            range, RangeP(op.range.begin, op.range.begin), LookupPE(LookupNameP(NameP(op.range, "range")), None), Vector(left, right), false)
//        })
//
//    val withMultDiv =
//      binariableExpression(
//        withRange,
//        white ~> (pstr("*") | pstr("/")) <~ white,
//        (range, op: NameP, left, right) => {
//          FunctionCallPE(
//            range, RangeP(op.range.begin, op.range.begin), LookupPE(LookupNameP(op), None), Vector(left, right), false)
//        })
//
//    val withAddSubtract =
//      binariableExpression(
//        withMultDiv,
//        white ~> (pstr("+") | pstr("-")) <~ white,
//        (range, op: NameP, left, right) => {
//          FunctionCallPE(
//            range, RangeP(op.range.begin, op.range.begin), LookupPE(LookupNameP(op), None), Vector(left, right), false)
//        })
//
//    val withCustomBinaries =
//      binariableExpression(
//        withAddSubtract,
//        not(white ~> pstr("=") <~ white) ~>
//          not(white ~> comparisonOperators <~ white) ~>
//          not(white ~> conjunctionOperators <~ white) ~>
//          (white ~> infixFunctionIdentifier <~ white),
//        (range, funcName: NameP, left, right) => {
//          FunctionCallPE(range, RangeP(funcName.range.begin, funcName.range.end), LookupPE(LookupNameP(funcName), None), Vector(left, right), false)
//        })
//
//    val withComparisons =
//      binariableExpression(
//        withCustomBinaries,
//        not(white ~> conjunctionOperators <~ white) ~>
//          white ~> comparisonOperators <~ white,
//        (range, op: NameP, left, right) => {
//          FunctionCallPE(range, RangeP(op.range.begin, op.range.begin), LookupPE(LookupNameP(op), None), Vector(left, right), false)
//        })
//
//    val withConjunctions =
//      binariableExpression(
//        withComparisons,
//        white ~> conjunctionOperators <~ white,
//        (range, op: NameP, left, right) => {
//          op.str match {
//            case "and" => AndPE(range, left, BlockPE(right.range, right))
//            case "or" => OrPE(range, left, BlockPE(right.range, right))
//          }
//        })
//
//    withConjunctions |
//      (conjunctionOperators ^^ (op => LookupPE(LookupNameP(op), None))) |
//      (comparisonOperators ^^ (op => LookupPE(LookupNameP(op), None)))
//  }
//
//  sealed trait StatementType
//
//  case object FunctionReturnStatementType extends StatementType
//
//  case object BlockReturnStatementType extends StatementType
//
//  case object NormalResultStatementType extends StatementType
//
//  //  // The boolean means it's a result or a return, it should be the last thing in the block.
//  //  def statementOrResult(allowLambda: Boolean): Parser[(IExpressionPE, Boolean)] = {
//  //    pos ~ (opt("=" <~ optWhite | "ret" <~ white) ~ statement(allowLambda)) ~ pos ^^ {
//  //      case begin ~ (maybeResult ~ expr) ~ end => {
//  //        (maybeResult, expr) match {
//  //          case (None, expr) => (expr, false)
//  //          case (Some("="), expr) => (expr, true)
//  //          case (Some("ret"), expr) => (ReturnPE(Range(begin, end), expr), true)
//  //        }
//  //      }
//  //    }
//  //  }
//
//  private[old] def multiStatementBlock(allowLambda: Boolean): Parser[IExpressionPE] = {
//    // Can't just do a rep(statement <~ optWhite) ~ ("=" ~> optWhite ~> statement) because
//    // "= doThings(a);" is actually a valid statement, which looks like doThings(=, a);
//    rep1sep(statement(allowLambda), optWhite ~> ";" <~ optWhite) ~ exists(optWhite ~> ";") ~ pos ^^ {
//      case exprs ~ false ~ end => {
//        ConsecutorPE(exprs.toVector)
//      }
//      case exprs ~ true ~ end => {
//        ConsecutorPE((exprs :+ VoidPE(RangeP(end, end))).toVector)
//      }
//    }
//  }
//
//  private[old] def blockExprs(allowLambda: Boolean): Parser[IExpressionPE] = {
//    multiStatementBlock(allowLambda) |
//      expression(allowLambda) |
//      (pos ~ ("..." ~> pos) ^^ { case begin ~ end => VoidPE(RangeP(begin, end)) }) |
//      (pos ^^ { case p => VoidPE(RangeP(p, p)) })
//  }
//
//  private[old] def arrayOrTuple: Parser[IExpressionPE] = {
//    // Static arrays have to come before tuples, because the beginning of static array looks kind of like a tuple.
//    constructArrayExpr |
//      //    runtimeSizedArrayExpr |
//      tuupleExpr
//  }
//
//  private[old] def arraySize: Parser[IArraySizeP] = {
//    ("*" ^^^ RuntimeSizedP) |
//      (opt(templex) ^^ StaticSizedP)
//  }
//
//  private[old] def arrayHeader: Parser[(Option[ITemplexPT], Option[ITemplexPT], IArraySizeP)] = {
//    ((("[" ~> optWhite ~> opt(mutabilityAtomTemplex <~ optWhite)) ~
//      (opt(variabilityAtomTemplex <~ optWhite)) ~
//      (arraySize <~ optWhite <~ "]")) ^^ {
//      case maybeMutability ~ maybeVariability ~ maybeSize => {
//        (maybeMutability, maybeVariability, maybeSize)
//      }
//    }) |
//      ((("[" ~> optWhite) ~>
//        (opt(templex <~ optWhite) <~ "," <~ optWhite) ~
//        (opt(templex <~ optWhite) <~ "," <~ optWhite) ~
//        (arraySize <~ optWhite <~ "]")) ^^ {
//        case maybeMutability ~ maybeVariability ~ maybeSize => {
//          (maybeMutability, maybeVariability, maybeSize)
//        }
//      })
//  }
//
//  private[old] def constructArrayExpr: Parser[IExpressionPE] = {
//    pos ~
//      (arrayHeader <~ optWhite) ~
//      (("[" ~ (optWhite ~> repsep(expression(true), optWhite ~> "," <~ optWhite) <~ optWhite <~ "]")) |
//        ("(" ~ (optWhite ~> repsep(expression(true), optWhite ~> "," <~ optWhite) <~ optWhite <~ ")"))) ~
//      pos ^^ {
//      case begin ~ ((maybeMutability, maybeVariability, size)) ~ (argsType ~ valueExprs) ~ end => {
//        val initializingIndividualElements = argsType match {
//          case "[" => true
//          case "(" => false
//        }
//        ConstructArrayPE(
//          RangeP(begin, end),
//          None,
//          maybeMutability,
//          maybeVariability,
//          size,
//          initializingIndividualElements,
//          valueExprs.toVector)
//      }
//    }
//  }
//
//  private[old] def tuupleExpr: Parser[IExpressionPE] = {
//    pos ~ ("[" ~> optWhite ~> repsep(expression(true), optWhite ~> "," <~ optWhite) <~ optWhite <~ "]") ~ pos ^^ {
//      case begin ~ a ~ end => {
//        TuplePE(RangeP(begin, end), a.toVector)
//      }
//    }
//  }
//
//  private[old] def packExpr: Parser[PackPE] = {
//    pos ~ ("(" ~> optWhite ~> ("..." ^^^ Vector.empty | repsep(expression(true), optWhite ~> "," <~ optWhite)) <~ optWhite <~ ")") ~ pos ^^ {
//      case begin ~ inners ~ end => PackPE(RangeP(begin, end), inners.toVector)
//    }
//  }
//
//  private[old] def indexExpr: Parser[Vector[IExpressionPE]] = {
//    "[" ~> optWhite ~> repsep(expression(true), optWhite ~> "," <~ optWhite) <~ optWhite <~ "]" ^^ (a => a.toVector)
//  }
//
//  private[old] def lambda: Parser[LambdaPE] = {
//    pos ~ existsMW("[]") ~ opt(patternPrototypeParams) ~ pos ~ bracedBlock ~ pos ^^ {
//      case begin ~ maybeCaptures ~ maybeParams ~ headerEnd ~ block ~ end => {
//        ast.LambdaPE(
//          maybeCaptures,
//          FunctionP(
//            RangeP(begin, end),
//            FunctionHeaderP(
//              RangeP(begin, headerEnd),
//              None, Vector.empty, None, None, maybeParams, FunctionReturnP(RangeP(headerEnd, headerEnd), None, None)),
//            Some(block)))
//      }
//    }
//  }
//
  private[old] def patternPrototypeParam: Parser[PatternPP] = {
    atomPattern ^^ {
      case pattern => pattern
    }
  }

  private[parser] def patternPrototypeParams: Parser[ParamsP] = {
    pos ~ ("(" ~> optWhite ~> repsep(optWhite ~> patternPrototypeParam, optWhite ~> ",") <~ optWhite <~ ")") ~ pos ^^ {
      case begin ~ params ~ end => ast.ParamsP(RangeP(begin, end), params.toVector)
    }
  }
//
//  private[old] def caase: Parser[LambdaPE] = {
//    pos ~ patternPrototypeParam ~ (pos <~ optWhite) ~ bracedBlock ~ pos ^^ {
//      case begin ~ param ~ paramsEnd ~ body ~ end => {
//        LambdaPE(
//          None,
//          ast.FunctionP(
//            RangeP(begin, end),
//            FunctionHeaderP(
//              RangeP(begin, paramsEnd),
//              None, Vector.empty, None, None,
//              Some(ast.ParamsP(RangeP(begin, paramsEnd), Vector(param))),
//              FunctionReturnP(RangeP(end, end), None, None)), Some(body)))
//      }
//    }
//  }
}
