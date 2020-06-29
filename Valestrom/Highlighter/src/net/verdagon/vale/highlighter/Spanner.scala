package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.vimpl

sealed trait IClass
case object Prog extends IClass
case object W extends IClass
case object Abst extends IClass
case object Ext extends IClass
case object Fn extends IClass
case object Struct extends IClass
case object FnName extends IClass
case object StructName extends IClass
case object Membs extends IClass
case object Lend extends IClass
case object Memb extends IClass
case object Interface extends IClass
case object MembName extends IClass
case object Rules extends IClass
case object Rune extends IClass
case object IdentRunes extends IClass
case object IdentRune extends IClass
case object Params extends IClass
case object Pat extends IClass
case object Destructure extends IClass
case object Impl extends IClass
case object Capture extends IClass
case object CaptureName extends IClass
case object Block extends IClass
case object Num extends IClass
case object Str extends IClass
case object Bool extends IClass
case object Typ extends IClass
case object Call extends IClass
case object Ret extends IClass
case object If extends IClass
case object CallLookup extends IClass
case object Inl extends IClass
case object Lookup extends IClass
case object Seq extends IClass
case object Mut extends IClass
case object MemberAccess extends IClass
case object Let extends IClass
case object Lambda extends IClass
case object MagicParam extends IClass
case object TplArgs extends IClass
case object Comment extends IClass
case object Ownership extends IClass
case object Match extends IClass

case class Span(classs: IClass, range: Range, children: List[Span] = List())

object Spanner {
  def forProgram(program: Program0): Span = {
    Span(
      Prog,
      Range(Pos(1, 1), Pos(Int.MaxValue, Int.MaxValue)),
      program.topLevelThings.map({
        case TopLevelFunction(f) => forFunction(f)
        case TopLevelInterface(i) => forInterface(i)
        case TopLevelStruct(s) => forStruct(s)
        case TopLevelImpl(i) => forImpl(i)
      }))
  }

  def forInterface(i: InterfaceP): Span = {
    val InterfaceP(range, name, seealed, mutability, maybeIdentifyingRunes, templateRules, members) = i

    Span(
      Interface,
      range,
      Span(StructName, name.range, List()) ::
      members.map(forFunction))
  }

  def forImpl(i: ImplP): Span = {
    val ImplP(range, identifyingRunes, rules, struct, interface) = i
    Span(
      Impl,
      range,
      identifyingRunes.toList.map(forIdentifyingRunes) ++
      List(forTemplex(struct), forTemplex(interface)))
  }

  def forStruct(struct: StructP): Span = {
    val StructP(range, StringP(nameRange, _), _, _, maybeIdentifyingRunesP, maybeTemplateRulesP, StructMembersP(membersRange, members)) = struct

    Span(
      Struct,
      range,
      Span(StructName, nameRange, List()) ::
      maybeIdentifyingRunesP.toList.map(forIdentifyingRunes) ++
      maybeTemplateRulesP.toList.map(forTemplateRules) ++
      List(
        Span(
          Membs,
          membersRange,
          members.map(forStructContent))))
  }

  def forStructContent(c: IStructContent): Span = {
    c match {
      case m @ StructMemberP(_, _, _, _) => forMember(m)
      case StructMethodP(f) => forFunction(f)
    }
  }

  def forMember(member: StructMemberP): Span = {
    val StructMemberP(range, StringP(nameRange, _), _, tyype) = member
    Span(
      Memb,
      range,
      List(
        Span(MembName, nameRange, List()),
        forTemplex(tyype)))
  }

  def forFunction(function: FunctionP): Span = {
    val FunctionP(range, maybeName, isExtern, isAbstract, maybeUserSpecifiedIdentifyingRunes, templateRules, params, ret, body) = function

    Span(
      Fn,
      range,
      isExtern.toList.map(e => Span(Ext, e.range)) ++
      isAbstract.toList.map(e => Span(Abst, e.range)) ++
      maybeName.toList.map(n => Span(FnName, n.range)) ++
      maybeUserSpecifiedIdentifyingRunes.toList.map(forIdentifyingRunes) ++
      templateRules.toList.map(forTemplateRules) ++
      params.toList.map(forParams) ++
      ret.toList.map(forTemplex) ++
      body.toList.map(forBlock))
  }

  def forBlock(b: BlockPE): Span = {
    val BlockPE(range, elements) = b
    Span(Block, range, elements.map(forExpression))
  }

  def forExpression(e: IExpressionPE): Span = {
    e match {
      case IntLiteralPE(range, _) => Span(Num, range, List())
      case StrLiteralPE(StringP(range, _)) => Span(Str, range, List())
      case BoolLiteralPE(range, _) => Span(Bool, range, List())
      case VoidPE(range) => Span(W, range, List())
      case MagicParamLookupPE(range) => {
        Span(
          MagicParam,
          range,
          List())
      }
      case LambdaPE(captures, FunctionP(range, None, _, _, _, _, params, _, body)) => {
        Span(
          Lambda,
          range,
          params.toList.map(forParams) ++ body.toList.map(forBlock))
      }
      case LetPE(range, templateRules, pattern, expr) => {
        Span(
          Let,
          range,
          List(forPattern(pattern), forExpression(expr)))
      }
      case LookupPE(StringP(range, _), templateArgs) => {
        Span(Lookup, range, List())
      }
      case SequencePE(range, elements) => {
        Span(Seq, range, elements.map(forExpression))
      }
      case MutatePE(range, mutatee, expr) => {
        Span(Mut, range, List(forExpression(mutatee), forExpression(expr)))
      }
      case DotPE(range, left, member) => {
        Span(
          MemberAccess,
          range,
          List(forExpression(left), forExpression(member)))
      }
      case LendPE(range, expr) => {
        Span(
          Lend,
          range,
          List(forExpression(expr)))
      }
      case DotCallPE(range, callableExpr, argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: argSpans)
        Span(Call, range, allSpans)
      }
      case MethodCallPE(range, callableExpr, _, LookupPE(StringP(methodNameRange, _), maybeTemplateArgs), argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val methodSpan = Span(CallLookup, methodNameRange, List())
        val maybeTemplateArgsSpan = maybeTemplateArgs.toList.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: methodSpan :: (maybeTemplateArgsSpan ++ argSpans))
        Span(Call, range, allSpans)
      }
      case FunctionCallPE(range, inlRange, LookupPE(StringP(nameRange, _), maybeTemplateArgs), argExprs, _) => {
        val inlSpan = inlRange.toList.map(x => Span(Inl, x.range, List()))
        val callableSpan = Span(CallLookup, nameRange, List())
        val maybeTemplateArgsSpan = maybeTemplateArgs.toList.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans =
          (inlSpan ++ List(callableSpan) ++ maybeTemplateArgsSpan ++ argSpans)
            .sortWith(_.range.begin < _.range.begin)
        Span(Call, range, allSpans)
      }
      case FunctionCallPE(range, None, callableExpr, argExprs, _) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: argSpans).sortWith(_.range.begin < _.range.begin)
        Span(Call, range, allSpans)
      }
      case ReturnPE(range, expr) => {
        Span(Ret, range, List(forExpression(expr)))
      }
      case BlockPE(range, exprs) => {
        Span(
          Block,
          range,
          exprs.map(forExpression))
      }
      case MatchPE(range, condExpr, lambdas) => {
        Span(
          Match,
          range,
          forExpression(condExpr) :: lambdas.map(l => forFunction(l.function)))
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        Span(
          If,
          range,
          List(forExpression(condition), forExpression(thenBody), forExpression(elseBody)))
      }
      case other => vimpl(other.toString)
    }
  }

  def forParams(p: ParamsP): Span = {
    val ParamsP(range, params) = p
    Span(Params, range, params.map(forPattern))
  }

  def forPattern(p: PatternPP): Span = {
    val PatternPP(range, maybePreBorrow, capture, templex, maybeDestructure, virtuality) = p
    Span(
      Pat,
      range,
      maybePreBorrow.toList.map(b => Span(Lend, b.range, List())) ++
      capture.toList.map(forCapture) ++
      templex.toList.map(forTemplex) ++
      maybeDestructure.toList.map(forDestructure))
  }

  def forDestructure(d: DestructureP): Span = {
    val DestructureP(range, patterns) = d
    Span(
      Destructure,
      range,
      patterns.map(forPattern))
  }

  def forCapture(c: CaptureP): Span = {
    val CaptureP(range, name, _) = c
    val nameSpan =
      name match {
        case LocalNameP(StringP(nameRange, _)) => {
          Span(CaptureName, nameRange, List())
        }
        case ConstructingMemberNameP(StringP(nameRange, _)) => {
          Span(CaptureName, nameRange, List())
        }
      }
    Span(
      Capture,
      range,
      List(nameSpan))
  }

  def forTemplex(t: ITemplexPT): Span = {
    t match {
      case NameOrRunePT(StringP(range, _)) => {
        Span(Typ, range, List())
      }
      case InlinePT(range, inner) => {
        Span(Inl, range, List(forTemplex(inner)))
      }
      case OwnershippedPT(range, ownership, inner) => {
        Span(Ownership, range, List(forTemplex(inner)))
      }
      case RepeaterSequencePT(range, mutability, size, element) => {
        Span(
          Typ,
          range,
          List(forTemplex(size), forTemplex(element)))
      }
      case IntPT(range, value) => {
        Span(
          Num,
          range,
          List())
      }
      case CallPT(range, template, args) => {
        Span(
          TplArgs,
          range,
          forTemplex(template) :: args.map(forTemplex))
      }
      case other => vimpl(other.toString)
    }
  }


  def forTemplateArgs(argsP: TemplateArgsP): Span = {
    val TemplateArgsP(range, args) = argsP
    Span(
      TplArgs,
      range,
      args.map(forTemplex))
  }

  def forTemplateRules(rulesP: TemplateRulesP): Span = {
    val TemplateRulesP(range, rules) = rulesP
    Span(
      Rules,
      range,
      rules.map(forRulex))
  }

  def forRulex(rulex: IRulexPR): Span = {
    vimpl()
  }

  def forIdentifyingRunes(r: IdentifyingRunesP): Span = {
    val IdentifyingRunesP(range, runes) = r
    Span(
      IdentRunes,
      range,
      runes.map(rune => Span(IdentRune, rune.range)))
  }
}
