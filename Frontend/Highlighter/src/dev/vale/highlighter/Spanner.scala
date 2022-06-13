package dev.vale.highlighter

import dev.vale.lexing.RangeL
import dev.vale.parsing.ast
import dev.vale.parsing.ast.{AbstractAttributeP, AugmentPE, BinaryCallPE, BlockPE, BraceCallPE, CallPT, ConsecutorPE, ConstantBoolPE, ConstantIntPE, ConstantStrPE, ConstructArrayPE, ConstructingMemberNameDeclarationP, DestructPE, DestructureP, DotPE, EachPE, ExportAsP, ExportAttributeP, ExternAttributeP, FileP, FunctionCallPE, FunctionHeaderP, FunctionP, FunctionReturnP, IAttributeP, IExpressionPE, INameDeclarationP, IRulexPR, IStructContent, ITemplexPT, IdentifyingRunesP, IfPE, ImplP, ImportP, IndexPE, InlinePT, IntPT, InterfaceP, InterpretedPT, IterableNameDeclarationP, IterationOptionNameDeclarationP, IteratorNameDeclarationP, LambdaPE, LetPE, LocalNameDeclarationP, LookupNameP, LookupPE, MagicParamLookupPE, MethodCallPE, MutabilityPT, MutatePE, NameOrRunePT, NameP, NormalStructMemberP, NotPE, PackPE, ParamsP, PatternPP, PureAttributeP, RegionRunePT, ReturnPE, RuntimeSizedArrayPT, RuntimeSizedP, ShortcallPE, StaticSizedArrayPT, StaticSizedP, StrInterpolatePE, StructMembersP, StructMethodP, StructP, SubExpressionPE, TemplateArgsP, TemplateRulesP, TopLevelExportAsP, TopLevelFunctionP, TopLevelImplP, TopLevelImportP, TopLevelInterfaceP, TopLevelStructP, TuplePE, UnitP, VoidPE, WhilePE}
import dev.vale.{vcurious, vimpl}
import dev.vale.parsing.ast._
import dev.vale.parsing.{ast, _}
import dev.vale.vimpl

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
case object Point extends IClass
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
case object Import extends IClass
case object Export extends IClass
case object Capture extends IClass
case object CaptureName extends IClass
case object Block extends IClass
case object Num extends IClass
case object Str extends IClass
case object Bool extends IClass
case object Typ extends IClass
case object Destruct extends IClass
case object Call extends IClass
case object Consecutor extends IClass
case object Ret extends IClass
case object If extends IClass
case object While extends IClass
case object Paren extends IClass
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
case object Mutability extends IClass
case object Ownership extends IClass
case object Match extends IClass

case class Span(classs: IClass, range: RangeL, children: Vector[Span]) { override def hashCode(): Int = vcurious() }

object Spanner {
  def forProgram(program: FileP): Span = {
    makeSpan(
      Prog,
      RangeL(0, Int.MaxValue),
      program.denizens.map({
        case TopLevelFunctionP(f) => forFunction(f)
        case TopLevelInterfaceP(i) => forInterface(i)
        case TopLevelStructP(s) => forStruct(s)
        case TopLevelImplP(i) => forImpl(i)
        case TopLevelExportAsP(export) => forExport(export)
        case TopLevelImportP(impoort) => forImport(impoort)
      }).toVector)
  }

  def forInterface(i: InterfaceP): Span = {
    val InterfaceP(range, name, attributes, mutability, maybeIdentifyingRunes, maybeTemplateRulesP, members) = i

    makeSpan(
      Interface,
      range,
      Vector(makeSpan(StructName, name.range, Vector.empty)) ++
      members.map(forFunction))
  }

  def forImpl(i: ImplP): Span = {
    val ImplP(range, identifyingRunes, templateRules, struct, interface, attributes) = i
    makeSpan(
      Impl,
      range,
      identifyingRunes.toVector.map(forIdentifyingRunes) ++
      struct.toVector.map(forTemplex) ++
      Vector(forTemplex(interface)))
  }

  def forExport(i: ExportAsP): Span = {
    val ExportAsP(range, struct, exportedName) = i
    makeSpan(
      Export,
      range,
      Vector(forTemplex(struct)))
  }

  def forImport(i: ImportP): Span = {
    val ImportP(range, moduleName, packageSteps, importeeName) = i
    makeSpan(
      Import,
      range,
      Vector())
  }

  def forStruct(struct: StructP): Span = {
    val StructP(range, NameP(nameRange, _), _, _, maybeIdentifyingRunesP, maybeTemplateRulesP, StructMembersP(membersRange, members)) = struct

    makeSpan(
      Struct,
      range,
      Vector(makeSpan(StructName, nameRange, Vector.empty)) ++
      maybeIdentifyingRunesP.toVector.map(forIdentifyingRunes) ++
      maybeTemplateRulesP.toVector.map(forTemplateRules) ++
      Vector(
        makeSpan(
          Membs,
          membersRange,
          members.map(forStructContent))))
  }

  def forStructContent(c: IStructContent): Span = {
    c match {
      case m @ NormalStructMemberP(_, _, _, _) => forMember(m)
      case StructMethodP(f) => forFunction(f)
    }
  }

  def forMember(member: NormalStructMemberP): Span = {
    val NormalStructMemberP(range, NameP(nameRange, _), _, tyype) = member
    makeSpan(
      Memb,
      range,
      Vector(
        makeSpan(MembName, nameRange, Vector.empty),
        forTemplex(tyype)))
  }

  def forFunctionAttribute(functionAttributeP: IAttributeP): Span = {
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
      maybeInferRet.toVector.map({ case r @ RangeL(_, _) => makeSpan(Ret, r, Vector.empty) }) ++
      maybeRetType.toVector.map(forTemplex))
  }

  def forFunction(function: FunctionP): Span = {
    val FunctionP(range, FunctionHeaderP(_, maybeName, attributes, maybeUserSpecifiedIdentifyingRunes, maybeTemplateRulesP, params, ret), body) = function

    makeSpan(
      Fn,
      range,
      attributes.map(forFunctionAttribute) ++
      maybeName.toVector.map(n => makeSpan(FnName, n.range)) ++
      maybeUserSpecifiedIdentifyingRunes.toVector.map(forIdentifyingRunes) ++
//      templateRules.toVector.map(forTemplateRules) ++
      params.toVector.map(forParams) ++
      Vector(forFunctionReturn(ret)) ++
      body.toVector.map(forBlock))
  }

  def forBlock(b: BlockPE): Span = {
    val BlockPE(range, inner) = b
    makeSpan(Block, range, Vector(forExpression(inner)))
  }

  def forExpression(e: IExpressionPE): Span = {
    e match {
      case ConstantIntPE(range, _, _) => makeSpan(Num, range, Vector.empty)
      case ConstantStrPE(range, _) => makeSpan(Str, range, Vector.empty)
      case ConstantBoolPE(range, _) => makeSpan(Bool, range, Vector.empty)
      case VoidPE(range) => makeSpan(W, range, Vector.empty)
      case MagicParamLookupPE(range) => {
        makeSpan(
          MagicParam,
          range,
          Vector.empty)
      }
      case LambdaPE(captures, FunctionP(range, FunctionHeaderP(_, None, _, _, maybeTemplateRulesP, params, _), body)) => {
        makeSpan(
          Lambda,
          range,
          params.toVector.map(forParams) ++ body.toVector.map(forBlock))
      }
      case LetPE(range, pattern, expr) => {
        makeSpan(
          Let,
          range,
          Vector(forPattern(pattern), forExpression(expr)))
      }
      case LookupPE(lookupName, templateArgs) => {
        lookupName match {
          case LookupNameP(NameP(range, _)) => makeSpan(Lookup, range, Vector.empty)
          case other => vimpl(other)
        }
      }
      case TuplePE(range, elements) => {
        makeSpan(Seq, range, elements.map(forExpression))
      }
      case PackPE(range, elements) => {
        makeSpan(Seq, range, elements.map(forExpression))
      }
      case ConstructArrayPE(range, tyype, mutability, variability, size, initializingIndividualElements, args) => {
        makeSpan(
          ConstructArray,
          range,
          tyype.map(forTemplex).toVector ++
          mutability.map(forTemplex).toVector ++
          variability.map(forTemplex).toVector ++
          (size match {
            case RuntimeSizedP => Vector.empty
            case StaticSizedP(sizePT) => {
              sizePT.map(forTemplex).toVector
            }
          }) ++
          args.map(forExpression))
      }
      case MutatePE(range, mutatee, expr) => {
        makeSpan(Mut, range, Vector(forExpression(mutatee), forExpression(expr)))
      }
      case DestructPE(range, expr) => {
        makeSpan(Destruct, range, Vector(forExpression(expr)))
      }
      case StrInterpolatePE(range, parts) => {
        makeSpan(
          Str,
          range,
          parts.map(forExpression))
      }
      case DotPE(range, left, operatorRange, member) => {
        makeSpan(
          MemberAccess,
          range,
          Vector(forExpression(left), makeSpan(MemberAccess, operatorRange)) :+ makeSpan(Lookup, member.range, Vector.empty))
      }
      case AugmentPE(range, targetOwnership, expr) => {
        makeSpan(
          Point,
          range,
          Vector(forExpression(expr)))
      }
      case IndexPE(range, callableExpr, argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (Vector(callableSpan) ++ argSpans)
        makeSpan(Call, range, allSpans)
      }
      case MethodCallPE(range, callableExpr, operatorRange, LookupPE(lookup, maybeTemplateArgs), argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val methodSpan = makeSpan(CallLookup, lookup.range, Vector.empty)
        val maybeTemplateArgsSpan = maybeTemplateArgs.toVector.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (Vector(callableSpan, makeSpan(MemberAccess, operatorRange), methodSpan) ++ maybeTemplateArgsSpan ++ argSpans)
        makeSpan(Call, range, allSpans)
      }
      case BinaryCallPE(range, NameP(nameRange, _), leftExpr, rightExpr) => {
        val callableSpan = makeSpan(CallLookup, nameRange, Vector.empty)
        val allSpans =
          (Vector(callableSpan, forExpression(leftExpr), forExpression(rightExpr)))
            .sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case FunctionCallPE(range, operatorRange, LookupPE(LookupNameP(NameP(nameRange, _)), maybeTemplateArgs), argExprs) => {
        val opSpan = makeSpan(MemberAccess, operatorRange)
        val callableSpan = makeSpan(CallLookup, nameRange, Vector.empty)
        val maybeTemplateArgsSpan = maybeTemplateArgs.toVector.map(forTemplateArgs)
        val argSpans = argExprs.map(forExpression)
        val allSpans =
          (Vector(opSpan, callableSpan) ++ maybeTemplateArgsSpan ++ argSpans)
            .sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case NotPE(range, innerPE) => {
        makeSpan(Call, range, Vector(forExpression(innerPE)))
      }
      case BraceCallPE(range, operatorRange, subjectExpr, argExprs, _) => {
        val opSpan = makeSpan(MemberAccess, operatorRange)
        val callableSpan = forExpression(subjectExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans =
          (Vector(opSpan, callableSpan) ++ argSpans)
            .sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case FunctionCallPE(range, operatorRange, callableExpr, argExprs) => {
        val callableSpan = forExpression(callableExpr)
        val argSpans = argExprs.map(forExpression)
        val allSpans = (Vector(callableSpan) ++ argSpans).sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case c @ ConsecutorPE(inners) => {
        val innersSpans = inners.map(forExpression)
        makeSpan(Consecutor, c.range, innersSpans)
      }
      case ShortcallPE(range, argExprs) => {
        val argSpans = argExprs.map(forExpression)
        val allSpans = argSpans.sortWith(_.range.begin < _.range.begin)
        makeSpan(Call, range, allSpans)
      }
      case ReturnPE(range, expr) => {
        makeSpan(Ret, range, Vector(forExpression(expr)))
      }
      case BlockPE(range, inner) => {
        makeSpan(
          Block,
          range,
          Vector(forExpression(inner)))
      }
//      case MatchPE(range, condExpr, lambdas) => {
//        makeSpan(
//          Match,
//          range,
//          Vector(forExpression(condExpr)) ++ lambdas.map(l => forFunction(l.function)))
//      }
      case IfPE(range, condition, thenBody, elseBody) => {
        makeSpan(
          If,
          range,
          Vector(forExpression(condition), forExpression(thenBody), forExpression(elseBody)))
      }
      case WhilePE(range, condition, body) => {
        makeSpan(
          While,
          range,
          Vector(forExpression(condition), forExpression(body)))
      }
      case EachPE(range, entryPattern, inKeywordRange, iterableExpr, body) => {
        makeSpan(
          While,
          range,
          Vector(
            forPattern(entryPattern),
            forExpression(iterableExpr),
            forExpression(body)))
      }
      case SubExpressionPE(range, inner) => {
        makeSpan(
          Paren,
          range,
          Vector(
            forExpression(inner)))
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
      maybePreBorrow.toVector.map(b => makeSpan(Point, b.range, Vector.empty)) ++
      capture.toVector.map(forCapture) ++
      templex.toVector.map(forTemplex) ++
      maybeDestructure.toVector.map(forDestructure))
  }

  def forDestructure(d: DestructureP): Span = {
    val DestructureP(range, patterns) = d
    makeSpan(
      Destructure,
      range,
      patterns.map(forPattern))
  }

  def forCapture(c: INameDeclarationP): Span = {
    c match {
      case LocalNameDeclarationP(NameP(nameRange, _)) => {
        makeSpan(CaptureName, nameRange, Vector.empty)
      }
      case IterableNameDeclarationP(nameRange) => {
        makeSpan(CaptureName, nameRange, Vector.empty)
      }
      case IteratorNameDeclarationP(nameRange) => {
        makeSpan(CaptureName, nameRange, Vector.empty)
      }
      case IterationOptionNameDeclarationP(nameRange) => {
        makeSpan(CaptureName, nameRange, Vector.empty)
      }
      case ConstructingMemberNameDeclarationP(NameP(nameRange, _)) => {
        makeSpan(CaptureName, nameRange, Vector.empty)
      }
    }
  }

  def forTemplex(t: ITemplexPT): Span = {
    t match {
      case NameOrRunePT(NameP(range, _)) => {
        makeSpan(Typ, range, Vector.empty)
      }
      case InlinePT(range, inner) => {
        makeSpan(Inl, range, Vector(forTemplex(inner)))
      }
      case InterpretedPT(range, ownership, inner) => {
        makeSpan(Ownership, range, Vector(forTemplex(inner)))
      }
      case RuntimeSizedArrayPT(range, mutability, element) => {
        makeSpan(
          Typ,
          range,
          Vector(forTemplex(mutability), forTemplex(element)))
      }
      case StaticSizedArrayPT(range, mutability, variability, size, element) => {
        makeSpan(
          Typ,
          range,
          Vector(forTemplex(size), forTemplex(mutability), forTemplex(variability), forTemplex(element)))
      }
      case IntPT(range, value) => {
        makeSpan(
          Num,
          range,
          Vector.empty)
      }
      case CallPT(range, template, args) => {
        makeSpan(
          TplArgs,
          range,
          Vector(forTemplex(template)) ++ args.map(forTemplex))
      }
      case MutabilityPT(range, _) => {
        makeSpan(
          Mutability,
          range,
          Vector())
      }
      case RegionRunePT(range, _) => {
        makeSpan(
          Rune,
          range,
          Vector())
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
      Vector.empty)
  }

  def forIdentifyingRunes(r: IdentifyingRunesP): Span = {
    val IdentifyingRunesP(range, runes) = r
    makeSpan(
      IdentRunes,
      range,
      runes.map(rune => makeSpan(IdentRune, rune.range)))
  }

  def makeSpan(classs: IClass, range: RangeL, children: Vector[Span] = Vector.empty) = {
    val filteredAndSortedChildren =
      children
        .filter(s => s.range.begin != s.range.end)
        .sortWith(_.range.begin < _.range.begin)
    Span(classs, range, filteredAndSortedChildren)
  }
}
