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

  def buildArray()(implicit m: ClassTag[T]): Array[T] = {
    val attributes = new Array[T](elementsReversed.length)
    var i = 0;
    while ( {
      elementsReversed match {
        case head :: tail => {
          attributes(i) = head
          elementsReversed = tail
          i = i + 1
          true
        }
        case Nil => false
      }
    }) {}
    attributes
  }
}
