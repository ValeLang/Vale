package net.verdagon.vale.solver

import net.verdagon.vale.{Collector, Err, Ok, Result, vassert, vassertOne, vassertSome, vfail, vimpl, vwat}
import org.scalatest.{FunSuite, Matchers}

import scala.collection.immutable.Map

sealed trait IRule {
  def allRunes: Array[Long]
  def allPuzzles: Array[Array[Long]]
}
case class Lookup(rune: Long, name: String) extends IRule {
  override def allRunes: Array[Long] = Array(rune)
  override def allPuzzles: Array[Array[Long]] = Array(Array())
}
case class Literal(rune: Long, value: String) extends IRule {
  override def allRunes: Array[Long] = Array(rune)
  override def allPuzzles: Array[Array[Long]] = Array(Array())
}
case class Equals(leftRune: Long, rightRune: Long) extends IRule {
  override def allRunes: Array[Long] = Array(leftRune, rightRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(leftRune), Array(rightRune))
}
case class CoordComponents(coordRune: Long, ownershipRune: Long, kindRune: Long) extends IRule {
  override def allRunes: Array[Long] = Array(coordRune, ownershipRune, kindRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(coordRune), Array(ownershipRune, kindRune))
}
case class OneOf(coordRune: Long, possibleValues: Array[String]) extends IRule {
  override def allRunes: Array[Long] = Array(coordRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(coordRune))
}
case class Call(resultRune: Long, nameRune: Long, argRune: Long) extends IRule {
  override def allRunes: Array[Long] = Array(resultRune, nameRune, argRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(resultRune, nameRune), Array(nameRune, argRune))
}
// See IRFU and SRCAMP for what this rule is doing
case class Send(senderRune: Long, receiverRune: Long) extends IRule {
  override def allRunes: Array[Long] = Array(receiverRune, senderRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(receiverRune))
}
case class Implements(subRune: Long, superRune: Long) extends IRule {
  override def allRunes: Array[Long] = Array(subRune, superRune)
  override def allPuzzles: Array[Array[Long]] = Array(Array(subRune, superRune))
}
case class Pack(resultRune: Long, memberRunes: Array[Long]) extends IRule {
  override def allRunes: Array[Long] = Array(resultRune) ++ memberRunes
  override def allPuzzles: Array[Array[Long]] = {
    if (memberRunes.isEmpty) {
      Array(Array(resultRune))
    } else {
      Array(Array(resultRune), memberRunes)
    }
  }
}
