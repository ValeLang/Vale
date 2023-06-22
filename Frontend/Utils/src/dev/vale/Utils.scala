package dev.vale

import scala.collection.mutable
import scala.reflect.ClassTag

object U {
  def foreach[T](vec: Vector[T], func: scala.Function1[T, Unit]): Unit = {
//    vec.foreach(func)
    var i = 0
    while (i < vec.length) {
      func(vec(i))
      i = i + 1
    }
  }
  def foreachI[T](vec: Vector[T], func: scala.Function2[Int, T, Unit]): Unit = {
    //    vec.zipWithIndex.foreach(func)
    var i = 0
    while (i < vec.length) {
      func(i, vec(i))
      i = i + 1
    }
  }
  def foreachIterable[T](vec: Iterable[T], func: scala.Function1[T, Unit]): Unit = {
    //    vec.foreach(func)
    val it = vec.iterator
    while (it.hasNext) {
      func(it.next())
    }
  }
  def map[T, R](vec: Vector[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Vector[R] = {
//    vec.map(func)
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result.toVector
  }
  def mapRange[R](start: Int, until: Int, func: scala.Function1[Int, R])(implicit m: ClassTag[R]): Vector[R] = {
//    (start until until).map(func).toVector
    val result = new Array[R](until - start)
    var i = 0
    while (i < until - start) {
      result(i) = func(start + i)
      i = i + 1
    }
    result.toVector
  }
  def mapWithIndex[T, R](vec: Vector[T], func: scala.Function2[Int, T, R])(implicit m: ClassTag[R]): Vector[R] = {
//    vec.zipWithIndex.map({ case (e, i) => func(i, e) })
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(i, vec(i))
      i = i + 1
    }
    result.toVector
  }
  def mapVec[T, R](vec: Vector[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Vector[R] = {
//    vec.map(func)
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result.toVector
  }
  def makeArray[T, R](n: Int, func: Int => R)(implicit m: ClassTag[R]): Vector[R] = {
//    (0 until n).map(func).toVector
    val result = new Array[R](n)
    var i = 0
    while (i < n) {
      result(i) = func(i)
      i = i + 1
    }
    result.toVector
  }
  def makeVec[T, R](n: Int, func: Int => R)(implicit m: ClassTag[R]): Vector[R] = {
//    (0 until n).map(func).toVector
    val result = new Array[R](n)
    var i = 0
    while (i < n) {
      result(i) = func(i)
      i = i + 1
    }
    result.toVector
  }
  def loop[T, R](n: Int, func: Int => R): Unit = {
//    (0 until n).foreach(func)
    var i = 0
    while (i < n) {
      func(i)
      i = i + 1
    }
  }
  def loop[T, R](start: Int, until: Int, func: Int => R): Unit = {
//    (start until until).foreach(func)
    var i = start
    while (i < until) {
      func(i)
      i = i + 1
    }
  }
  def sign(n: Long): Int = {
    if (n < 0) return -1
    if (n > 0) return 1
    0
  }
  def findIndexWhere[T](vec: Vector[T], func: scala.Function1[T, Boolean]): Option[Int] = {
    findIndexWhereFromUntil(vec, func, 0, vec.length)
  }
  def findIndexWhereFrom[T](vec: Vector[T], func: scala.Function1[T, Boolean], startIndex: Int): Option[Int] = {
    findIndexWhereFromUntil(vec, func, startIndex, vec.length)
  }
  def findIndexWhereFromUntil[T](vec: Vector[T], func: scala.Function1[T, Boolean], startIndex: Int, endIndex: Int): Option[Int] = {
//    vec.slice(0, 1).iterator.indexWhere(func) match {
//      case -1 => None
//      case other => Some(other)
//    }
    var i = startIndex
    while (i < endIndex) {
      if (func(vec(i))) {
        return Some(i)
      }
      i = i + 1
    }
    return None
  }

  def exists[T](vec: Vector[T], func: scala.Function1[T, Boolean]): Boolean = {
    exists(vec, func, 0, vec.length)
  }
  def exists[T](vec: Vector[T], func: scala.Function1[T, Boolean], startIndex: Int): Boolean = {
    exists(vec, func, startIndex, vec.length)
  }
  def exists[T](vec: Vector[T], func: scala.Function1[T, Boolean], startIndex: Int, endIndex: Int): Boolean = {
//    vec.exists(func)
    var i = startIndex
    while (i < endIndex) {
      if (func(vec(i))) {
        return true
      }
      i = i + 1
    }
    return false
  }
  def extract[T, Y](vec: Vector[T], func: scala.PartialFunction[T, Y]): (Vector[Y], Vector[T]) = {
    val result = mutable.ArrayBuffer[Y]()
    val remainder = mutable.ArrayBuffer[T]()
    var i = 0
    while (i < vec.size) {
      val el = vec(i)
      if (func.isDefinedAt(el)) {
        result += func(el)
      } else {
        remainder += el
      }
      i = i + 1
    }
    return (result.toVector, remainder.toVector)
  }

  def repeat[T](elem: T, n: Int): Vector[T] = {
    val result = mutable.ArrayBuffer[T]()
    (0 until n).foreach(i => {
      result += elem
    })
    result.toVector
  }
}
