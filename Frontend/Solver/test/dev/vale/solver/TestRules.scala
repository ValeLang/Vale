package dev.vale.solver

import dev.vale.Err
import org.scalatest._

import scala.collection.immutable.Map

sealed trait IRule {
  def allRunes: Vector[Long]
  def allPuzzles: Vector[Vector[Long]]
}
case class Lookup(rune: Long, name: String) extends IRule {
  override def allRunes: Vector[Long] = Vector(rune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector())
}
case class Literal(rune: Long, value: String) extends IRule {
  override def allRunes: Vector[Long] = Vector(rune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector())
}
case class Equals(leftRune: Long, rightRune: Long) extends IRule {
  override def allRunes: Vector[Long] = Vector(leftRune, rightRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(leftRune), Vector(rightRune))
}
case class CoordComponents(coordRune: Long, ownershipRune: Long, kindRune: Long) extends IRule {
  override def allRunes: Vector[Long] = Vector(coordRune, ownershipRune, kindRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(coordRune), Vector(ownershipRune, kindRune))
}
case class OneOf(coordRune: Long, possibleValues: Vector[String]) extends IRule {
  override def allRunes: Vector[Long] = Vector(coordRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(coordRune))
}
case class Call(resultRune: Long, nameRune: Long, argRune: Long) extends IRule {
  override def allRunes: Vector[Long] = Vector(resultRune, nameRune, argRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(resultRune, nameRune), Vector(nameRune, argRune))
}
// See IRFU and SRCAMP for what this rule is doing
case class Send(senderRune: Long, receiverRune: Long) extends IRule {
  override def allRunes: Vector[Long] = Vector(receiverRune, senderRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(receiverRune))
}
case class Implements(subRune: Long, superRune: Long) extends IRule {
  override def allRunes: Vector[Long] = Vector(subRune, superRune)
  override def allPuzzles: Vector[Vector[Long]] = Vector(Vector(subRune, superRune))
}
case class Pack(resultRune: Long, memberRunes: Vector[Long]) extends IRule {
  override def allRunes: Vector[Long] = Vector(resultRune) ++ memberRunes
  override def allPuzzles: Vector[Vector[Long]] = {
    if (memberRunes.isEmpty) {
      Vector(Vector(resultRune))
    } else {
      Vector(Vector(resultRune), memberRunes)
    }
  }
}
