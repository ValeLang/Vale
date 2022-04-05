package dev.vale

import scala.collection.mutable
import scala.reflect.ClassTag

object U {
  def map[T, R](vec: Array[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Array[R] = {
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result
  }
  def map[T, R](vec: Vector[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Vector[R] = {
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
}
