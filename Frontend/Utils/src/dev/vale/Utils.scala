package dev.vale

import scala.collection.mutable
import scala.reflect.ClassTag

object U {
  def foreach[T](vec: Array[T], func: scala.Function1[T, Unit]): Unit = {
//    vec.foreach(func)
    var i = 0
    while (i < vec.length) {
      func(vec(i))
      i = i + 1
    }
  }
  def map[T, R](vec: Array[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Array[R] = {
//    vec.map(func)
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result
  }
  def mapRange[R](start: Int, until: Int, func: scala.Function1[Int, R])(implicit m: ClassTag[R]): Array[R] = {
//    (start until until).map(func).toArray
    val result = new Array[R](until - start)
    var i = start
    while (i < until) {
      result(i) = func(i)
      i = i + 1
    }
    result
  }
  def mapWithIndex[T, R](vec: Array[T], func: scala.Function2[Int, T, R])(implicit m: ClassTag[R]): Array[R] = {
//    vec.zipWithIndex.map({ case (e, i) => func(i, e) })
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(i, vec(i))
      i = i + 1
    }
    result
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
  def makeArray[T, R](n: Int, func: Int => R)(implicit m: ClassTag[R]): Array[R] = {
//    (0 until n).map(func).toArray
    val result = new Array[R](n)
    var i = 0
    while (i < n) {
      result(i) = func(i)
      i = i + 1
    }
    result
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
  def findIndexWhere[T](vec: Array[T], func: scala.Function1[T, Boolean]): Option[Int] = {
    findIndexWhereFromUntil(vec, func, 0, vec.length)
  }
  def findIndexWhereFrom[T](vec: Array[T], func: scala.Function1[T, Boolean], startIndex: Int): Option[Int] = {
    findIndexWhereFromUntil(vec, func, startIndex, vec.length)
  }
  def findIndexWhereFromUntil[T](vec: Array[T], func: scala.Function1[T, Boolean], startIndex: Int, endIndex: Int): Option[Int] = {
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

  def exists[T](vec: Array[T], func: scala.Function1[T, Boolean]): Boolean = {
    exists(vec, func, 0, vec.length)
  }
  def exists[T](vec: Array[T], func: scala.Function1[T, Boolean], startIndex: Int): Boolean = {
    exists(vec, func, startIndex, vec.length)
  }
  def exists[T](vec: Array[T], func: scala.Function1[T, Boolean], startIndex: Int, endIndex: Int): Boolean = {
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
}
