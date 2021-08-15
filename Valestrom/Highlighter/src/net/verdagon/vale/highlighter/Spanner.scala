package net.verdagon.vale.highlighter

import net.verdagon.vale.parser._
import net.verdagon.vale.{vcurious, vimpl}

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
case object Destruct extends IClass
case object Call extends IClass
case object Ret extends IClass
case object If extends IClass
case object While extends IClass
case object CallLookup extends IClass
case object Inl extends IClass
case object Lookup extends IClass
case object Seq extends IClass
case object ConstructArray extends IClass
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

case class Span(classs: IClass, range: Range, children: List[Span]) { override def hashCode(): Int = vcurious() }

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
      makeSpan(StructName, name.range, List.empty) ::
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
    val StructP(range, NameP(nameRange, _), _, _, maybeIdentifyingRunesP, maybeTemplateRulesP, StructMembersP(membersRange, members)) = struct

    makeSpan(
      Struct,
      range,
      makeSpan(StructName, nameRange, List.empty) ::
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
    val StructMemberP(range, NameP(nameRange, _), _, tyype) = member
    makeSpan(
      Memb,
      range,
      List(
        makeSpan(MembName, nameRange, List.empty),
        forTemplex(tyype)))
  }

  def forFunctionAttribute(functionAttributeP: IFunctionAttributeP): Span = {
    functionAttributeP match {
      case ExternAttributeP(range) => makeSpan(Ext, range)
      case ExportAttributeP(range) => makeSpan(Ext, range)
      case AbstractAttributeP(range) => makeSpan(Abst, range)
      case PureAttributeP(range) => makeSpan(Pure, range)
    }
  }

  def forFunctionReturn(p: FunctionReturnP): Span = {
    val FunctionReturnP(range, maybeInferRet, maybeRetType) = p
    makeSpan(
      Ret,
      range,
      maybeInferRet.toList.map({ case UnitP(range) => makeSpan(Ret, range, List.empty) }) ++
      maybeRetType.toList.map(forTemplex))
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
      List(forFunctionReturn(ret)) ++
      body.toList.map(forBlock))
  }

  def forBlock(b: BlockPE): Span = {
    val BlockPE(range, elements) = b
    makeSpan(Block, range, elements.map(forExpression))
  }

  def forExpression(e: IExpressionPE): Span = {
    e match {
      case ConstantIntPE(range, _, _) => makeSpan(Num, range, List.empty)
      case ConstantStrPE(range, _) => makeSpan(Str, range, List.empty)
      case ConstantBoolPE(range, _) => makeSpan(Bool, range, List.empty)
      case VoidPE(range) => makeSpan(W, range, List.empty)
      case MagicParamLookupPE(range) => {
        makeSpan(
          MagicParam,
          range,
          List.empty)
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
      case LookupPE(NameP(range, _), templateArgs) => {
        makeSpan(Lookup, range, List.empty)
      }
      case TuplePE(range, elements) => {
        makeSpan(Seq, range, elements.map(forExpression))
      }
      case PackPE(range, elements) => {
        makeSpan(Seq, range, elements.map(forExpression))
      }
      case ConstructArrayPE(range, mutability, variability, size, initializingIndividualElements, args) => {
        makeSpan(
          ConstructArray,
          range,
          mutability.map(forTemplex).toList ++
          variability.map(forTemplex).toList ++
          (size match {
            case RuntimeSizedP => List.empty
            case StaticSizedP(sizePT) => {
              sizePT.map(forTemplex).toList
            }
          }) ++
          args.map(forExpression))
      }
      case MutatePE(range, mutatee, expr) => {
        makeSpan(Mut, range, List(forExpression(mutatee), forExpression(expr)))
      }
      case DestructPE(range, expr) => {
        makeSpan(Destruct, range, List(forExpression(expr)))
      }
      case StrInterpolatePE(range, parts) => {
        makeSpan(
          Str,
          range,
          parts.map(forExpression))
      }
      case DotPE(range, left, operatorRange, _, member) => {
        makeSpan(
          MemberAccess,
          range,
          List(forExpression(left), makeSpan(MemberAccess, operatorRange)) :+ makeSpan(Lookup, member.range, List.empty))
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
      case MethodCallPE(range, inline, callableExpr, operatorRange, _, _, LookupPE(NameP(methodNameRange, _), maybeTemplateArgs), argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val methodSpan = makeSpan(CallLookup, methodNameRange, List.empty)
        val maybeTemplateArgsSpan = maybeTemplateArgs.toList.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (callableSpan :: makeSpan(MemberAccess, operatorRange) :: methodSpan :: (maybeTemplateArgsSpan ++ argSpans))
        makeSpan(Call, range, allSpans)
      }
      case FunctionCallPE(range, inlRange, operatorRange, _, LookupPE(NameP(nameRange, _), maybeTemplateArgs), argExprs, _) => {
        val inlSpan = inlRange.toList.map(x => makeSpan(Inl, x.range, List.empty))
        val opSpan = makeSpan(MemberAccess, operatorRange)
        val callableSpan = makeSpan(CallLookup, nameRange, List.empty)
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
      case ShortcallPE(range, argExprs) => {
        val argSpans = argExprs.map(forExpression)
        val allSpans = argSpans.sortWith(_.range.begin < _.range.begin)
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
      maybePreBorrow.toList.map(b => makeSpan(Lend, b.range, List.empty)) ++
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
    val CaptureP(range, name) = c
    val nameSpan =
      name match {
        case LocalNameP(NameP(nameRange, _)) => {
          makeSpan(CaptureName, nameRange, List.empty)
        }
        case ConstructingMemberNameP(NameP(nameRange, _)) => {
          makeSpan(CaptureName, nameRange, List.empty)
        }
      }
    makeSpan(
      Capture,
      range,
      List(nameSpan))
  }

  def forTemplex(t: ITemplexPT): Span = {
    t match {
      case NameOrRunePT(NameP(range, _)) => {
        makeSpan(Typ, range, List.empty)
      }
      case InlinePT(range, inner) => {
        makeSpan(Inl, range, List(forTemplex(inner)))
      }
      case InterpretedPT(range, ownership, permission, inner) => {
        makeSpan(Ownership, range, List(forTemplex(inner)))
      }
      case RepeaterSequencePT(range, mutability, variability, size, element) => {
        makeSpan(
          Typ,
          range,
          List(forTemplex(size), forTemplex(element)))
      }
      case IntPT(range, value) => {
        makeSpan(
          Num,
          range,
          List.empty)
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
    makeSpan(
      Rules,
      rulex.range,
      List.empty)
  }

  def forIdentifyingRunes(r: IdentifyingRunesP): Span = {
    val IdentifyingRunesP(range, runes) = r
    makeSpan(
      IdentRunes,
      range,
      runes.map(rune => makeSpan(IdentRune, rune.range)))
  }

  def makeSpan(classs: IClass, range: Range, children: List[Span] = List.empty) = {
    val filteredAndSortedChildren =
      children
        .filter(s => s.range.begin != s.range.end)
        .sortWith(_.range.begin < _.range.begin)
    Span(classs, range, filteredAndSortedChildren)
  }
}
