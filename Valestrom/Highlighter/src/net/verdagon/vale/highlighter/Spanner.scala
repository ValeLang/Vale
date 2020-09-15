package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.vimpl

sealed trait IClass
case object Prog extends IClass
case object W extends IClass
case object Abst extends IClass
case object Ext extends IClass
case object Pure extends IClass
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
case object While extends IClass
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
case object Permission extends IClass
case object Match extends IClass

case class Span(classs: IClass, range: Range, children: List[Span])

object Spanner {
  def forProgram(program: FileP): Span = {
    makeSpan(
      Prog,
      Range(0, Int.MaxValue),
      program.topLevelThings.map({
        case TopLevelFunctionP(f) => forFunction(f)
        case TopLevelInterfaceP(i) => forInterface(i)
        case TopLevelStructP(s) => forStruct(s)
        case TopLevelImplP(i) => forImpl(i)
      }))
  }

  def forInterface(i: InterfaceP): Span = {
    val InterfaceP(range, name, seealed, mutability, maybeIdentifyingRunes, templateRules, members) = i

    makeSpan(
      Interface,
      range,
      makeSpan(StructName, name.range, List()) ::
      members.map(forFunction))
  }

  def forImpl(i: ImplP): Span = {
    val ImplP(range, identifyingRunes, rules, struct, interface) = i
    makeSpan(
      Impl,
      range,
      identifyingRunes.toList.map(forIdentifyingRunes) ++
      List(forTemplex(struct), forTemplex(interface)))
  }

  def forStruct(struct: StructP): Span = {
    val StructP(range, StringP(nameRange, _), _, _, maybeIdentifyingRunesP, maybeTemplateRulesP, StructMembersP(membersRange, members)) = struct

    makeSpan(
      Struct,
      range,
      makeSpan(StructName, nameRange, List()) ::
      maybeIdentifyingRunesP.toList.map(forIdentifyingRunes) ++
      maybeTemplateRulesP.toList.map(forTemplateRules) ++
      List(
        makeSpan(
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
    makeSpan(
      Memb,
      range,
      List(
        makeSpan(MembName, nameRange, List()),
        forTemplex(tyype)))
  }

  def forFunctionAttribute(functionAttributeP: IFunctionAttributeP): Span = {
    functionAttributeP match {
      case ExternAttributeP(range) => makeSpan(Ext, range)
      case AbstractAttributeP(range) => makeSpan(Abst, range)
      case PureAttributeP(range) => makeSpan(Pure, range)
    }
  }

  def forFunction(function: FunctionP): Span = {
    val FunctionP(range, FunctionHeaderP(_, maybeName, attributes, maybeUserSpecifiedIdentifyingRunes, templateRules, params, ret), body) = function

    makeSpan(
      Fn,
      range,
      attributes.map(forFunctionAttribute) ++
      maybeName.toList.map(n => makeSpan(FnName, n.range)) ++
      maybeUserSpecifiedIdentifyingRunes.toList.map(forIdentifyingRunes) ++
      templateRules.toList.map(forTemplateRules) ++
      params.toList.map(forParams) ++
      ret.toList.map(forTemplex) ++
      body.toList.map(forBlock))
  }

  def forBlock(b: BlockPE): Span = {
    val BlockPE(range, elements) = b
    makeSpan(Block, range, elements.map(forExpression))
  }

  def forExpression(e: IExpressionPE): Span = {
    e match {
      case IntLiteralPE(range, _) => makeSpan(Num, range, List())
      case StrLiteralPE(range, _) => makeSpan(Str, range, List())
      case BoolLiteralPE(range, _) => makeSpan(Bool, range, List())
      case VoidPE(range) => makeSpan(W, range, List())
      case MagicParamLookupPE(range) => {
        makeSpan(
          MagicParam,
          range,
          List())
      }
      case LambdaPE(captures, FunctionP(range, FunctionHeaderP(_, None, _, _, _, params, _), body)) => {
        makeSpan(
          Lambda,
          range,
          params.toList.map(forParams) ++ body.toList.map(forBlock))
      }
      case LetPE(range, templateRules, pattern, expr) => {
        makeSpan(
          Let,
          range,
          List(forPattern(pattern), forExpression(expr)))
      }
      case LookupPE(StringP(range, _), templateArgs) => {
        makeSpan(Lookup, range, List())
      }
      case SequencePE(range, elements) => {
        makeSpan(Seq, range, elements.map(forExpression))
      }
      case MutatePE(range, mutatee, expr) => {
        makeSpan(Mut, range, List(forExpression(mutatee), forExpression(expr)))
      }
      case DotPE(range, left, operatorRange, _, member) => {
        makeSpan(
          MemberAccess,
          range,
          List(forExpression(left), makeSpan(MemberAccess, operatorRange)) :+ makeSpan(Lookup, member.range, List()))
      }
      case LendPE(range, expr, targetOwnership) => {
        makeSpan(
          Lend,
          range,
          List(forExpression(expr)))
      }
      case IndexPE(range, callableExpr, argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: argSpans)
        makeSpan(Call, range, allSpans)
      }
      case MethodCallPE(range, callableExpr, operatorRange, _, _, LookupPE(StringP(methodNameRange, _), maybeTemplateArgs), argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val methodSpan = makeSpan(CallLookup, methodNameRange, List())
        val maybeTemplateArgsSpan = maybeTemplateArgs.toList.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: makeSpan(MemberAccess, operatorRange) :: methodSpan :: (maybeTemplateArgsSpan ++ argSpans))
        makeSpan(Call, range, allSpans)
      }
      case FunctionCallPE(range, inlRange, operatorRange, _, LookupPE(StringP(nameRange, _), maybeTemplateArgs), argExprs, _) => {
        val inlSpan = inlRange.toList.map(x => makeSpan(Inl, x.range, List()))
        val opSpan = makeSpan(MemberAccess, operatorRange)
        val callableSpan = makeSpan(CallLookup, nameRange, List())
        val maybeTemplateArgsSpan = maybeTemplateArgs.toList.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans =
          (inlSpan ++ List(opSpan, callableSpan) ++ maybeTemplateArgsSpan ++ argSpans)
            .sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case FunctionCallPE(range, None, operatorRange, _, callableExpr, argExprs, _) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: argSpans).sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case ReturnPE(range, expr) => {
        makeSpan(Ret, range, List(forExpression(expr)))
      }
      case BlockPE(range, exprs) => {
        makeSpan(
          Block,
          range,
          exprs.map(forExpression))
      }
      case MatchPE(range, condExpr, lambdas) => {
        makeSpan(
          Match,
          range,
          forExpression(condExpr) :: lambdas.map(l => forFunction(l.function)))
      }
      case IfPE(range, condition, thenBody, elseBody) => {
        makeSpan(
          If,
          range,
          List(forExpression(condition), forExpression(thenBody), forExpression(elseBody)))
      }
      case WhilePE(range, condition, body) => {
        makeSpan(
          While,
          range,
          List(forExpression(condition), forExpression(body)))
      }
      case other => vimpl(other.toString)
    }
  }

  def forParams(p: ParamsP): Span = {
    val ParamsP(range, params) = p
    makeSpan(Params, range, params.map(forPattern))
  }

  def forPattern(p: PatternPP): Span = {
    val PatternPP(range, maybePreBorrow, capture, templex, maybeDestructure, virtuality) = p
    makeSpan(
      Pat,
      range,
      maybePreBorrow.toList.map(b => makeSpan(Lend, b.range, List())) ++
      capture.toList.map(forCapture) ++
      templex.toList.map(forTemplex) ++
      maybeDestructure.toList.map(forDestructure))
  }

  def forDestructure(d: DestructureP): Span = {
    val DestructureP(range, patterns) = d
    makeSpan(
      Destructure,
      range,
      patterns.map(forPattern))
  }

  def forCapture(c: CaptureP): Span = {
    val CaptureP(range, name, _) = c
    val nameSpan =
      name match {
        case LocalNameP(StringP(nameRange, _)) => {
          makeSpan(CaptureName, nameRange, List())
        }
        case ConstructingMemberNameP(StringP(nameRange, _)) => {
          makeSpan(CaptureName, nameRange, List())
        }
      }
    makeSpan(
      Capture,
      range,
      List(nameSpan))
  }

  def forTemplex(t: ITemplexPT): Span = {
    t match {
      case NameOrRunePT(StringP(range, _)) => {
        makeSpan(Typ, range, List())
      }
      case InlinePT(range, inner) => {
        makeSpan(Inl, range, List(forTemplex(inner)))
      }
      case OwnershippedPT(range, ownership, inner) => {
        makeSpan(Ownership, range, List(forTemplex(inner)))
      }
      case PermissionedPT(range, permission, inner) => {
        makeSpan(Permission, range, List(forTemplex(inner)))
      }
      case RepeaterSequencePT(range, mutability, size, element) => {
        makeSpan(
          Typ,
          range,
          List(forTemplex(size), forTemplex(element)))
      }
      case IntPT(range, value) => {
        makeSpan(
          Num,
          range,
          List())
      }
      case CallPT(range, template, args) => {
        makeSpan(
          TplArgs,
          range,
          forTemplex(template) :: args.map(forTemplex))
      }
      case other => vimpl(other.toString)
    }
  }


  def forTemplateArgs(argsP: TemplateArgsP): Span = {
    val TemplateArgsP(range, args) = argsP
    makeSpan(
      TplArgs,
      range,
      args.map(forTemplex))
  }

  def forTemplateRules(rulesP: TemplateRulesP): Span = {
    val TemplateRulesP(range, rules) = rulesP
    makeSpan(
      Rules,
      range,
      rules.map(forRulex))
  }

  def forRulex(rulex: IRulexPR): Span = {
    vimpl()
  }

  def forIdentifyingRunes(r: IdentifyingRunesP): Span = {
    val IdentifyingRunesP(range, runes) = r
    makeSpan(
      IdentRunes,
      range,
      runes.map(rune => makeSpan(IdentRune, rune.range)))
  }

  def makeSpan(classs: IClass, range: Range, children: List[Span] = List()) = {
    val filteredAndSortedChildren =
      children
        .filter(s => s.range.begin != s.range.end)
        .sortWith(_.range.begin < _.range.begin)
    Span(classs, range, filteredAndSortedChildren)
  }
}
