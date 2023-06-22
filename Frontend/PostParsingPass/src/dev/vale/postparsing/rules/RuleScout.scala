package dev.vale.postparsing.rules

import dev.vale.lexing.RangeL
import dev.vale.parsing.ast.{BoolTypePR, BuiltinCallPR, ComponentsPR, CoordListTypePR, CoordTypePR, EqualsPR, IRulexPR, ITypePR, IntPT, IntTypePR, KindTypePR, LocationTypePR, MutabilityTypePR, NameP, OrPR, OwnershipPT, OwnershipTypePR, PrototypeTypePR, TemplexPR, TypedPR, VariabilityTypePR}
import dev.vale.postparsing._
import dev.vale.{Interner, Keywords, StrI, vassert, vassertOne, vcurious, vfail, vimpl, vregionmut, vwat}
import dev.vale.parsing._
import dev.vale.parsing.ast._
import dev.vale.postparsing._
import dev.vale.postparsing.rules.RuleScout.translateType

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class RuleScout(interner: Interner, keywords: Keywords, templexScout: TemplexScout) {
  // Returns:
  // - new rules produced on the side while translating the given rules
  // - the translated versions of the given rules
  def translateRulexes(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    builder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)],
    contextRegion: IRuneS,
    rulesP: Vector[IRulexPR]):
  Vector[RuneUsage] = {
    rulesP.map(translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, _))
  }

  def translateRulex(
    env: IEnvironmentS,
    lidb: LocationInDenizenBuilder,
    builder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.ArrayBuffer[(IRuneS, ITemplataType)],
    contextRegion: IRuneS,
    rulex: IRulexPR):
  RuneUsage = {
    val evalRange = (range: RangeL) => PostParser.evalRange(env.file, range)

    rulex match {
      case EqualsPR(range, leftP, rightP) => {
        val rune = ImplicitRuneS(lidb.child().consume())
        builder +=
          rules.EqualsSR(
            evalRange(range),
            translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, leftP),
            translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, rightP))
        rules.RuneUsage(evalRange(range), rune)
      }
      case OrPR(range, possibilitiesP) => {
        val rune = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))

        val values =
          possibilitiesP
            .map({
              case TemplexPR(templex) => {
                templexScout.translateValueTemplex(templex) match {
                  case None => vfail("Or rules can only contain values for their possibilities.")
                  case Some(x) => x
                }
              }
              case _ => vfail("Or rules can only contain values for their possibilities.")
            })

        builder += rules.OneOfSR(evalRange(range), rune, values.toVector)
        rune
      }
      case ComponentsPR(range, tyype, componentsP) => {
        val rune = RuneUsage(PostParser.evalRange(env.file, range), ImplicitRuneS(lidb.child().consume()))
        runeToExplicitType += ((rune.rune, translateType(tyype)))
        tyype match {
          case CoordTypePR => {
            vregionmut() // Put back in with regions
            // if (componentsP.size != 3) {
            //   vfail("Ref rule should have three components! Found: " + componentsP.size)
            // }
            if (componentsP.size != 2) {
              vfail("Ref rule should have two components! Found: " + componentsP.size)
            }
            vregionmut() // Put back in with regions
            // val Vector(ownershipRuneS, regionRuneS, kindRuneS) =
            val Vector(ownershipRuneS, kindRuneS) =
              translateRulexes(env, lidb.child(), builder, runeToExplicitType, contextRegion, componentsP)
            builder +=
              CoordComponentsSR(
                PostParser.evalRange(env.file, range),
                rune,
                ownershipRuneS,
                kindRuneS)
          }
          case KindTypePR => {
            if (componentsP.size != 1) {
              vfail("Kind rule should have one component! Found: " + componentsP.size)
            }
            val Vector(mutabilityRuneS) =
              translateRulexes(
                env, lidb.child(), builder, runeToExplicitType, contextRegion, componentsP)
            builder +=
              KindComponentsSR(
                PostParser.evalRange(env.file, range),
                rune,
                mutabilityRuneS)
          }
          case PrototypeTypePR => {
            if (componentsP.size != 2) {
              vfail("Prot rule should have two components! Found: " + componentsP.size)
            }
            val Vector(paramsRuneS, returnRuneS) =
              translateRulexes(
                env, lidb.child(), builder, runeToExplicitType, contextRegion, componentsP)
            builder +=
              PrototypeComponentsSR(
                PostParser.evalRange(env.file, range),
                rune,
                paramsRuneS,
                returnRuneS)
          }
          case _ => {
            vfail("Invalid type for compnents rule: " + tyype)
          }
        }
        rune
      }
      case TypedPR(range, None, tyype) => {
        val rune = ImplicitRuneS(lidb.child().consume())
        runeToExplicitType += ((rune, translateType(tyype)))
        rules.RuneUsage(evalRange(range), rune)
      }
      case TypedPR(range, Some(NameP(_, runeName)), tyype) => {
        val rune = CodeRuneS(runeName)
        runeToExplicitType += ((rune, translateType(tyype)))
        rules.RuneUsage(evalRange(range), rune)
      }
      case TemplexPR(templex) => {
        templexScout.translateTemplex(env, lidb.child(), builder, contextRegion, templex)
      }
      case BuiltinCallPR(range, name, args) => {
        if (name.str == keywords.IS_INTERFACE) {
          vassert(args.length == 1)
          val argRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, args.head)

  //        val resultRune = ImplicitRuneS(lidb.child().consume())
          builder += IsInterfaceSR(evalRange(range), argRune)
  //        runeToExplicitType.put(resultRune, KindTemplataType())
          runeToExplicitType += ((argRune.rune, KindTemplataType()))

          rules.RuneUsage(evalRange(range), argRune.rune)
        } else if (name.str == keywords.IMPLEMENTS) {
          vassert(args.length == 2)
          val Vector(structRule, interfaceRule) = args
          val structRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, structRule)
          runeToExplicitType += ((structRune.rune, CoordTemplataType()))
          val interfaceRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, interfaceRule)
          runeToExplicitType += ((interfaceRune.rune, CoordTemplataType()))

          val resultRuneS = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
          runeToExplicitType += ((resultRuneS.rune, ImplTemplataType()))

          // Only appears in definition; filtered out when solving call site
          builder += rules.DefinitionCoordIsaSR(evalRange(range), resultRuneS, structRune, interfaceRune)
          // Only appears in call site; filtered out when solving definition
          builder += rules.CallSiteCoordIsaSR(evalRange(range), Some(resultRuneS), structRune, interfaceRune)

          rules.RuneUsage(evalRange(range), structRune.rune)
        } else if (name.str == keywords.REF_LIST_COMPOUND_MUTABILITY) {
          vassert(args.length == 1)
          val argRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, args.head)

          val resultRune = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
          builder += RefListCompoundMutabilitySR(evalRange(range), resultRune, argRune)
          runeToExplicitType += ((resultRune.rune, MutabilityTemplataType()))
          runeToExplicitType += ((argRune.rune, PackTemplataType(CoordTemplataType())))

          rules.RuneUsage(evalRange(range), resultRune.rune)
        } else if (name.str == keywords.Refs) {
          val argRunes =
            args.map(arg => {
              translateRulex(env, lidb.child(), builder, runeToExplicitType, contextRegion, arg)
            })

          val resultRune = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
          builder += rules.PackSR(evalRange(range), resultRune, argRunes.toVector)
          runeToExplicitType += ((resultRune.rune, PackTemplataType(CoordTemplataType())))

          rules.RuneUsage(evalRange(range), resultRune.rune)
        } else if (name.str == keywords.ANY) {
          val literals: Vector[ILiteralSL] =
            args.map({
              case TemplexPR(IntPT(_, i)) => IntLiteralSL(i)
              case TemplexPR(OwnershipPT(_, i)) => OwnershipLiteralSL(i)
              case other => vimpl(other)
            }).toVector

          val resultRune = rules.RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
          builder += rules.OneOfSR(evalRange(range), resultRune, literals)
          runeToExplicitType += ((resultRune.rune, vassertOne(literals.map(_.getType()).distinct)))

          rules.RuneUsage(evalRange(range), resultRune.rune)
        } else {
          throw new CompileErrorExceptionS(UnknownRuleFunctionS(evalRange(range), name.str.str))
        }
      }
    }
  }

}

object RuleScout {

  def translateType(tyype: ITypePR): ITemplataType = {
    tyype match {
      case PrototypeTypePR => PrototypeTemplataType()
      case IntTypePR => IntegerTemplataType()
      case BoolTypePR => BooleanTemplataType()
      case OwnershipTypePR => OwnershipTemplataType()
      case MutabilityTypePR => MutabilityTemplataType()
      case VariabilityTypePR => VariabilityTemplataType()
      case LocationTypePR => LocationTemplataType()
      case CoordTypePR => CoordTemplataType()
      case CoordListTypePR => PackTemplataType(CoordTemplataType())
      case KindTypePR => KindTemplataType()
      case RegionTypePR => RegionTemplataType()
    }
  }

  // Gets the template name (or the kind name if not template)
  def getRuneKindTemplate(rulesS: IndexedSeq[IRulexSR], rune: IRuneS) = {
    val equivalencies = new Equivalencies(rulesS)
    val structKindEquivalentRunes = equivalencies.getKindEquivalentRunes(rune)
    val templateRunes =
      rulesS.collect({
        case MaybeCoercingCallSR(_, resultRune, templateRune, _)
          if structKindEquivalentRunes.contains(resultRune.rune) => templateRune.rune
      })
    val runesToLookFor = structKindEquivalentRunes ++ templateRunes
    val templateNames =
      rulesS.collect({
        case MaybeCoercingLookupSR(_, rune, name) if runesToLookFor.contains(rune.rune) => name
      })
    vassert(templateNames.nonEmpty)
    vcurious(templateNames.size == 1)
    val templateName = templateNames.head
    templateName
  }
}

class Equivalencies(rules: IndexedSeq[IRulexSR]) {
  val runeToKindEquivalentRunes: mutable.HashMap[IRuneS, mutable.HashSet[IRuneS]] = mutable.HashMap()
  def markKindEquivalent(runeA: IRuneS, runeB: IRuneS): Unit = {
    runeToKindEquivalentRunes.getOrElseUpdate(runeA, mutable.HashSet()) += runeB
    runeToKindEquivalentRunes.getOrElseUpdate(runeB, mutable.HashSet()) += runeA
  }
  rules.foreach({
    case CoordComponentsSR(_, resultRune, _, kindRune) => markKindEquivalent(resultRune.rune, kindRune.rune)
    case KindComponentsSR(_, resultRune, _) =>
    case EqualsSR(_, left, right) => markKindEquivalent(left.rune, right.rune)
    case CallSR(range, resultRune, templateRune, args) =>
    case MaybeCoercingCallSR(range, resultRune, templateRune, args) =>
    case CallSiteCoordIsaSR(range, resultRune, subRune, superRune) =>
    case DefinitionCoordIsaSR(range, resultRune, subRune, superRune) =>
    case CoordSendSR(range, senderRune, receiverRune) =>
    case AugmentSR(range, resultRune, ownership, innerRune) => markKindEquivalent(resultRune.rune, innerRune.rune)
    case LiteralSR(range, rune, literal) =>
    case MaybeCoercingLookupSR(range, rune, name) =>
    case CoerceToCoordSR(range, coordRune, kindRune) => markKindEquivalent(coordRune.rune, kindRune.rune)
//    case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) =>
//    case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) =>
    case OneOfSR(range, rune, literals) =>
    case CallSiteFuncSR(range, resultRune, nameRune, paramsListRune, returnRune) =>
    case DefinitionFuncSR(range, resultRune, name, paramsListRune, returnRune) =>
    case ResolveSR(range, resultRune, name, paramsListRune, returnRune) =>
    case PackSR(range, resultRune, members) =>
    case PrototypeComponentsSR(range, resultRune, paramsRune, returnRune) =>
    case RefListCompoundMutabilitySR(range, resultRune, coordListRune) =>
    case other => vimpl(other)
  })

  private def findTransitivelyEquivalentInto(foundSoFar: mutable.HashSet[IRuneS], rune: IRuneS): Unit = {
    runeToKindEquivalentRunes.getOrElse(rune, Vector()).foreach(r => {
      if (!foundSoFar.contains(r)) {
        foundSoFar += r
        findTransitivelyEquivalentInto(foundSoFar, r)
      }
    })
  }

  def getKindEquivalentRunes(rune: IRuneS): Set[IRuneS] = {
    val set = mutable.HashSet[IRuneS]()
    set += rune
    findTransitivelyEquivalentInto(set, rune)
    set.toSet
  }

  def getKindEquivalentRunes(runes: Iterable[IRuneS]): Set[IRuneS] = {
    runes
      .map(getKindEquivalentRunes)
      .foldLeft(Set[IRuneS]())(_ ++ _)
  }
}
