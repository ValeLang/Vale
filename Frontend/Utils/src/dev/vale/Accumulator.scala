package dev.vale

import scala.reflect.ClassTag

class Accumulator[T] {
  var elementsReversed: List[T] = List[T]()

  def isEmpty: Boolean = elementsReversed.isEmpty
  def size: Int = elementsReversed.size
  def head: T = elementsReversed.last
  def last: T = elementsReversed.head

  def add(element: T): Unit = {
    elementsReversed = element :: elementsReversed
  }
  def addAll[I <: Iterable[T]](c: I): Unit = {
    U.foreachIterable(c, x => add(x))
  }

  def buildArray()(implicit m: ClassTag[T]): Vector[T] = {
    val attributes = new Array[T](elementsReversed.length)
    var i = elementsReversed.length;
    while ( {
      elementsReversed match {
        case head :: tail => {
          i = i - 1
          attributes(i) = head
          elementsReversed = tail
          true
        }
        case Nil => false
      }
    }) {}
    vassert(i == 0)
    attributes.toVector
  }
}
