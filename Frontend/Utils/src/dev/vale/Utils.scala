package dev.vale

import scala.collection.mutable
import scala.reflect.ClassTag

object U {
  def foreach[T](vec: Array[T], func: scala.Function1[T, Unit]): Unit = {
    var i = 0
    while (i < vec.length) {
      func(vec(i))
      i = i + 1
    }
  }
  def map[T, R](vec: Array[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Array[R] = {
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result
  }
  def mapVec[T, R](vec: Vector[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Vector[R] = {
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result.toVector
  }
  def makeArray[T, R](n: Int, func: Int => R)(implicit m: ClassTag[R]): Array[R] = {
    val result = new Array[R](n)
    var i = 0
    while (i < n) {
      result(i) = func(i)
      i = i + 1
    }
    result
  }
  def makeVec[T, R](n: Int, func: Int => R)(implicit m: ClassTag[R]): Vector[R] = {
    val result = new Array[R](n)
    var i = 0
    while (i < n) {
      result(i) = func(i)
      i = i + 1
    }
    result.toVector
  }
  def loop[T, R](n: Int, func: Int => R): Unit = {
    var i = 0
    while (i < n) {
      func(i)
      i = i + 1
    }
  }
  def sign(n: Long): Int = {
    if (n < 0) return -1
    if (n > 0) return 1
    0
  }
}
