package net.verdagon.vale.scout.rules

import net.verdagon.vale.parser._
import net.verdagon.vale.parser.ast.{BoolTypePR, BuiltinCallPR, ComponentsPR, CoordListTypePR, CoordTypePR, EqualsPR, IRulexPR, ITypePR, IntPT, IntTypePR, KindTypePR, LocationTypePR, MutabilityTypePR, NameP, OrPR, OwnershipPT, OwnershipTypePR, PermissionTypePR, PrototypeTypePR, RangeP, TemplexPR, TypedPR, VariabilityTypePR}
import net.verdagon.vale.scout.{IEnvironment, Environment => _, FunctionEnvironment => _, _}
import net.verdagon.vale.{vassert, vassertOne, vassertSome, vcurious, vfail, vimpl}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class RuleScout(templexScout: TemplexScout) {
  // Returns:
  // - new rules produced on the side while translating the given rules
  // - the translated versions of the given rules
  def translateRulexes(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    builder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.HashMap[IRuneS, ITemplataType],
    rulesP: Vector[IRulexPR]):
  Vector[RuneUsage] = {
    rulesP.map(translateRulex(env, lidb.child(), builder, runeToExplicitType, _))
  }

  def translateRulex(
    env: IEnvironment,
    lidb: LocationInDenizenBuilder,
    builder: ArrayBuffer[IRulexSR],
    runeToExplicitType: mutable.HashMap[IRuneS, ITemplataType],
    rulex: IRulexPR):
  RuneUsage = {
    val evalRange = (range: RangeP) => Scout.evalRange(env.file, range)

    rulex match {
      case EqualsPR(range, leftP, rightP) => {
        val rune = ImplicitRuneS(lidb.child().consume())
        builder +=
          EqualsSR(
            evalRange(range),
            translateRulex(env, lidb.child(), builder, runeToExplicitType, leftP),
            translateRulex(env, lidb.child(), builder, runeToExplicitType, rightP))
        RuneUsage(evalRange(range), rune)
      }
      case OrPR(range, possibilitiesP) => {
        val rune = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))

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

        builder += OneOfSR(evalRange(range), rune, values.toArray)
        rune
      }
      case ComponentsPR(range, tyype, componentsP) => {
        val rune = RuneUsage(Scout.evalRange(env.file, range), ImplicitRuneS(lidb.child().consume()))
        runeToExplicitType.put(rune.rune, translateType(tyype))
        tyype match {
          case CoordTypePR => {
            if (componentsP.size != 3) {
              vfail("Ref rule should have three components! Found: " + componentsP.size)
            }
            val Vector(ownershipRuneS, permissionRuneS, kindRuneS) =
              translateRulexes(env, lidb.child(), builder, runeToExplicitType, componentsP)
            builder +=
              CoordComponentsSR(
                Scout.evalRange(env.file, range),
                rune,
                ownershipRuneS,
                permissionRuneS,
                kindRuneS)
          }
          case KindTypePR => {
            if (componentsP.size != 3) {
              vfail("Kind rule should have one component! Found: " + componentsP.size)
            }
            val Vector(mutabilityRuneS) =
              translateRulexes(env, lidb.child(), builder, runeToExplicitType, componentsP)
            builder +=
              KindComponentsSR(
                Scout.evalRange(env.file, range),
                rune,
                mutabilityRuneS)
          }
          case PrototypeTypePR => {
            if (componentsP.size != 3) {
              vfail("Ref rule should have three components! Found: " + componentsP.size)
            }
            val Vector(nameRuneS, paramListRuneS, returnRuneS) =
              translateRulexes(env, lidb.child(), builder, runeToExplicitType, componentsP)
            builder +=
              PrototypeComponentsSR(
                Scout.evalRange(env.file, range),
                rune,
                nameRuneS,
                paramListRuneS,
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
        runeToExplicitType.put(rune, translateType(tyype))
        RuneUsage(evalRange(range), rune)
      }
      case TypedPR(range, Some(NameP(_, runeName)), tyype) => {
        val rune = CodeRuneS(runeName)
        runeToExplicitType.put(rune, translateType(tyype))
        RuneUsage(evalRange(range), rune)
      }
      case TemplexPR(templex) => templexScout.translateTemplex(env, lidb.child(), builder, templex)
      case BuiltinCallPR(range, name, args) => {
        name.str match {
          case "isInterface" => {
            vassert(args.length == 1)
            val argRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, args.head)

    //        val resultRune = ImplicitRuneS(lidb.child().consume())
            builder += IsInterfaceSR(evalRange(range), argRune)
    //        runeToExplicitType.put(resultRune, KindTemplataType)
            runeToExplicitType.put(argRune.rune, KindTemplataType)

            RuneUsage(evalRange(range), argRune.rune)
          }
          case "implements" => {
            vassert(args.length == 2)
            val Vector(structRule, interfaceRule) = args
            val structRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, structRule)
            runeToExplicitType.put(structRune.rune, CoordTemplataType)
            val interfaceRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, interfaceRule)
            runeToExplicitType.put(interfaceRune.rune, CoordTemplataType)

            builder += CoordIsaSR(evalRange(range), structRune, interfaceRune)

            RuneUsage(evalRange(range), structRune.rune)
          }
          case "refListCompoundMutability" => {
            vassert(args.length == 1)
            val argRune = translateRulex(env, lidb.child(), builder, runeToExplicitType, args.head)

            val resultRune = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            builder += RefListCompoundMutabilitySR(evalRange(range), resultRune, argRune)
            runeToExplicitType.put(resultRune.rune, MutabilityTemplataType)
            runeToExplicitType.put(argRune.rune, PackTemplataType(CoordTemplataType))

            RuneUsage(evalRange(range), resultRune.rune)
          }
          case "Refs" => {
            val argRunes =
              args.map(arg => {
                translateRulex(env, lidb.child(), builder, runeToExplicitType, arg)
              })

            val resultRune = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            builder += PackSR(evalRange(range), resultRune, argRunes.toArray)
            runeToExplicitType.put(resultRune.rune, PackTemplataType(CoordTemplataType))

            RuneUsage(evalRange(range), resultRune.rune)
          }
          case "any" => {
            val literals: Array[ILiteralSL] =
              args.map({
                case TemplexPR(IntPT(_, i)) => IntLiteralSL(i)
                case TemplexPR(OwnershipPT(_, i)) => OwnershipLiteralSL(i)
                case other => vimpl(other)
              }).toArray

            val resultRune = RuneUsage(evalRange(range), ImplicitRuneS(lidb.child().consume()))
            builder += OneOfSR(evalRange(range), resultRune, literals)
            runeToExplicitType.put(resultRune.rune, vassertOne(literals.map(_.getType()).distinct))

            RuneUsage(evalRange(range), resultRune.rune)
          }
          case other => {
            throw new CompileErrorExceptionS(UnknownRuleFunctionS(evalRange(range), other))
          }
        }
      }
    }
  }

  def translateType(tyype: ITypePR): ITemplataType = {
    tyype match {
      case PrototypeTypePR => PrototypeTemplataType
      case IntTypePR => IntegerTemplataType
      case BoolTypePR => BooleanTemplataType
      case OwnershipTypePR => OwnershipTemplataType
      case MutabilityTypePR => MutabilityTemplataType
      case VariabilityTypePR => VariabilityTemplataType
      case PermissionTypePR => PermissionTemplataType
      case LocationTypePR => LocationTemplataType
      case CoordTypePR => CoordTemplataType
      case CoordListTypePR => PackTemplataType(CoordTemplataType)
      case KindTypePR => KindTemplataType
    }
  }

  def collectAllRunesNonDistinct(
    destination: mutable.ArrayBuffer[IRuneS],
    runeToExplicitType: mutable.HashMap[IRuneS, ITemplataType],
    rulex: IRulexPR):
  Unit = {
    rulex match {
      case EqualsPR(_, leftP, rightP) => {
        collectAllRunesNonDistinct(destination, runeToExplicitType, leftP)
        collectAllRunesNonDistinct(destination, runeToExplicitType, rightP)
      }
      case OrPR(_, possibilitiesP) =>
      case ComponentsPR(_, tyype, componentsP) =>
      case TypedPR(_, None, tyype) =>
      case TypedPR(_, Some(NameP(_, runeName)), tyype) => {
        val rune = CodeRuneS(runeName)
        destination += rune
        runeToExplicitType.put(rune, translateType(tyype))
      }
      case TemplexPR(innerPR) => // Do nothing, we can't declare runes inside templexes
    }
  }
}

object RuleScout {
  // Gets the template name (or the kind name if not template)
  def getRuneKindTemplate(rulesS: IndexedSeq[IRulexSR], rune: IRuneS) = {
    val equivalencies = new Equivalencies(rulesS)
    val structKindEquivalentRunes = equivalencies.getKindEquivalentRunes(rune)
    val templateRunes =
      rulesS.collect({
        case CallSR(_, resultRune, templateRune, _)
          if structKindEquivalentRunes.contains(resultRune.rune) => templateRune.rune
      })
    val runesToLookFor = structKindEquivalentRunes ++ templateRunes
    val templateNames =
      rulesS.collect({
        case LookupSR(_, rune, name) if runesToLookFor.contains(rune.rune) => name
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
    case CoordComponentsSR(_, resultRune, _, _, kindRune) => markKindEquivalent(resultRune.rune, kindRune.rune)
    case EqualsSR(_, left, right) => markKindEquivalent(left.rune, right.rune)
    case CallSR(range, resultRune, templateRune, args) =>
    case CoordIsaSR(range, subRune, superRune) =>
    case CoordSendSR(range, senderRune, receiverRune) =>
    case AugmentSR(range, resultRune, ownership, permission, innerRune) => markKindEquivalent(resultRune.rune, innerRune.rune)
    case LiteralSR(range, rune, literal) =>
    case LookupSR(range, rune, name) =>
    case CoerceToCoordSR(range, coordRune, kindRune) => markKindEquivalent(coordRune.rune, kindRune.rune)
    case StaticSizedArraySR(range, resultRune, mutabilityRune, variabilityRune, sizeRune, elementRune) =>
    case RuntimeSizedArraySR(range, resultRune, mutabilityRune, elementRune) =>
    case OneOfSR(range, rune, literals) =>
    case PrototypeComponentsSR(range, resultRune, nameRune, paramsListRune, returnRune) =>
    case PackSR(range, resultRune, members) =>
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
