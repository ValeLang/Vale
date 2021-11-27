package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Err, Ok, Result, vassert, vassertOne, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Map

object TestRuleSolver extends ISolveRule[IRule, Long, Unit, Unit, String, String] {

  def instantiateAncestorTemplate(descendants: Vector[String], ancestorTemplate: String): String = {
    // IRL, we may want to doublecheck that all descendants *can* instantiate as the ancestor template.
    val descendant = descendants.head
    (descendant, ancestorTemplate) match {
      case (x, y) if x == y => x
      case (x, y) if !x.contains(":") => y
      case ("Flamethrower:int", "IWeapon") => "IWeapon:int"
      case ("Rockets:int", "IWeapon") => "IWeapon:int"
      case other => vimpl(other)
    }
  }

  def getAncestors(descendant: String, includeSelf: Boolean): Vector[String] = {
    val selfAndAncestors =
      getTemplate(descendant) match {
        case "Firefly" => Vector("ISpaceship")
        case "Serenity" => Vector("ISpaceship")
        case "ISpaceship" => Vector()
        case "Flamethrower" => Vector("IWeapon")
        case "Rockets" => Vector("IWeapon")
        case "IWeapon" => Vector()
        case "int" => Vector()
        case other => vimpl(other)
      }
    selfAndAncestors ++ (if (includeSelf) List(descendant) else List())
  }

  // Turns eg Flamethrower:int into Flamethrower. Firefly just stays Firefly.
  def getTemplate(tyype: String): String = {
    if (tyype.contains(":")) tyype.split(":")(0) else tyype
  }

  override def complexSolve(state: Unit, env: Unit, stepState: IStepState[IRule, Long, String]):
  Result[Unit, ISolverError[Long, String, String]] = {
    val unsolvedRules = stepState.getUnsolvedRules()
    val receiverRunes = unsolvedRules.collect({ case Receive(receiverRune, _) => receiverRune })
    receiverRunes.foreach(receiver => {
      val receiveRules = unsolvedRules.collect({ case z @ Receive(r, _) if r == receiver => z })
      val callRules = unsolvedRules.collect({ case z @ Call(r, _, _) if r == receiver => z })
      val senderConclusions = receiveRules.map(_.senderRune).flatMap(stepState.getConclusion)
      val callTemplates = callRules.map(_.nameRune).flatMap(stepState.getConclusion)
      vassert(callTemplates.distinct.size <= 1)
      // If true, there are some senders/constraints we don't know yet, so lets be
      // careful to not assume between any possibilities below.
      val anyUnknownConstraints =
        (senderConclusions.size != receiveRules.size || callRules.size != callTemplates.size)
      solveReceives(senderConclusions, callTemplates, anyUnknownConstraints) match {
        case None => List()
        case Some(receiverInstantiation) => stepState.concludeRune(receiver, receiverInstantiation)
      }
    })
    Ok(())
  }

  override def solve(
    state: Unit, env: Unit, ruleIndex: Int, rule: IRule, stepState: IStepState[IRule, Long, String]):
  Result[Unit, ISolverError[Long, String, String]] = {
    rule match {
      case Equals(leftRune, rightRune) => {
        stepState.getConclusion(leftRune) match {
          case Some(left) => stepState.concludeRune(rightRune, left); Ok(())
          case None => stepState.concludeRune(leftRune, vassertSome(stepState.getConclusion(rightRune))); Ok(())
        }
      }
      case Lookup(rune, name) => {
        val value = name
        stepState.concludeRune(rune, value)
        Ok(())
      }
      case Literal(rune, literal) => {
        stepState.concludeRune(rune, literal)
        Ok(())
      }
      case OneOf(rune, literals) => {
        val literal = stepState.getConclusion(rune).get
        if (!literals.contains(literal)) {
          return Err(RuleError("conflict!"))
        }
        Ok(())
      }
      case CoordComponents(coordRune, ownershipRune, kindRune) => {
        stepState.getConclusion(coordRune) match {
          case Some(combined) => {
            val Array(ownership, kind) = combined.split("/")
            stepState.concludeRune(ownershipRune, ownership)
            stepState.concludeRune(kindRune, kind)
            Ok(())
          }
          case None => {
            (stepState.getConclusion(ownershipRune), stepState.getConclusion(kindRune)) match {
              case (Some(ownership), Some(kind)) => {
                stepState.concludeRune(coordRune, ownership + "/" + kind)
                Ok(())
              }
              case _ => vfail()
            }
          }
        }
      }
      case Pack(resultRune, memberRunes) => {
        stepState.getConclusion(resultRune) match {
          case Some(result) => {
            val parts = result.split(",")
            memberRunes.zip(parts).foreach({ case (rune, part) =>
              stepState.concludeRune(rune, part)
            })
            Ok(())
          }
          case None => {
            val result = memberRunes.map(stepState.getConclusion).map(_.get).mkString(",")
            stepState.concludeRune(resultRune, result)
            Ok(())
          }
        }
      }
      case Call(resultRune, nameRune, argRune) => {
        val maybeResult = stepState.getConclusion(resultRune)
        val maybeName = stepState.getConclusion(nameRune)
        val maybeArg = stepState.getConclusion(argRune)
        (maybeResult, maybeName, maybeArg) match {
          case (Some(result), Some(templateName), _) => {
            val prefix = templateName + ":"
            vassert(result.startsWith(prefix))
            stepState.concludeRune(argRune, result.slice(prefix.length, result.length))
            Ok(())
          }
          case (_, Some(templateName), Some(arg)) => {
            stepState.concludeRune(resultRune, (templateName + ":" + arg))
            Ok(())
          }
          case other => vwat(other)
        }
      }
      case Receive(receiverRune, senderRune) => {
        val receiver = vassertSome(stepState.getConclusion(receiverRune))
        if (receiver == "ISpaceship" || receiver == "IWeapon:int") {
          stepState.addRule(Implements(senderRune, receiverRune))
          Ok(())
        } else {
          // Not receiving into an interface, so sender must be the same
          stepState.concludeRune(senderRune, receiver)
          Ok(())
        }
      }
      case Implements(subRune, superRune) => {
        val sub = vassertSome(stepState.getConclusion(subRune))
        val suuper = vassertSome(stepState.getConclusion(superRune))
        (sub, suuper) match {
          case (x, y) if x == y => Ok(())
          case ("Firefly", "ISpaceship") => Ok(())
          case ("Serenity", "ISpaceship") => Ok(())
          case ("Flamethrower:int", "IWeapon:int") => Ok(())
          case other => vimpl(other)
        }
      }
    }
  }

  private def solveReceives(
    senders: Vector[String],
    callTemplates: Vector[String],
    anyUnknownConstraints: Boolean) = {
    val senderTemplates = senders.map(getTemplate)
    // Theoretically possible, not gonna handle it for this test
    vassert(callTemplates.toSet.size <= 1)

    // For example [Flamethrower, Rockets] becomes [[Flamethrower, IWeapon, ISystem], [Rockets, IWeapon, ISystem]]
    val senderAncestorLists = senderTemplates.map(getAncestors(_, true))
    // Calculates the intersection of them all, eg [IWeapon, ISystem]
    val commonAncestors = senderAncestorLists.reduce(_.intersect(_)).toSet
    // Filter by any call templates. eg if there's a X = ISystem:Y call, then we're now [ISystem]
    val commonAncestorsCallConstrained =
      if (callTemplates.isEmpty) commonAncestors else commonAncestors.intersect(callTemplates.toSet)
    // If there are multiple, like [IWeapon, ISystem], get rid of any that are parents of others, now [IWeapon].
    val commonAncestorsNarrowed = narrow(commonAncestorsCallConstrained, anyUnknownConstraints)
    if (commonAncestorsNarrowed.isEmpty) {
      None
    } else {
      val ancestorTemplate = commonAncestorsNarrowed.head
      val ancestorInstantiation = instantiateAncestorTemplate(senders, ancestorTemplate)
      Some(ancestorInstantiation)
    }
  }

  def narrow(
    ancestorTemplateUnnarrowed: Set[String],
    anyUnknownConstraints: Boolean):
  Set[String] = {
    val ancestorTemplate =
      if (ancestorTemplateUnnarrowed.size > 1) {
        if (anyUnknownConstraints) {
          // Theres some unknown constraints (calls, receives, isa, etc)
          // so we can't yet conclude what the narrowest one is.
          vfail()
        } else {
          // Then choose the narrowest one.
          // For our particular test data sets, this shortcut should work.
          ancestorTemplateUnnarrowed - "ISpaceship" - "IWeapon"
        }
      } else {
        ancestorTemplateUnnarrowed
      }
    vassert(ancestorTemplate.size <= 1)
    ancestorTemplate
  }

}
