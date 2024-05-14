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

  def foreachArr[T](vec: Array[T], func: scala.Function1[T, Unit]): Unit = {
    //    vec.foreach(func)
    var i = 0
    while (i < vec.length) {
      func(vec(i))
      i = i + 1
    }
  }

  def foreachMap[K, V](map: Map[K, V], func: scala.Function2[K, V, Unit]): Unit = {
    // TODO(optimize): this is probably slow?
    map.foreach({ case (k, v) =>
      func(k, v)
    })
  }

  def foreachIArr[T](arr: Array[T], func: scala.Function2[Int, T, Unit]): Unit = {
    var i = 0
    while (i < arr.length) {
      func(i, arr(i))
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

  def foreachIFrom[T](vec: Vector[T], start: Int, func: scala.Function2[Int, T, Unit]): Unit = {
    //    vec.zipWithIndex.foreach(func)
    var i = start
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

  def filterIterable[T](vec: Iterable[T], func: scala.Function1[T, Boolean])(implicit m: ClassTag[T]): Array[T] = {
    val result = mutable.ArrayBuffer[T]()
    val it = vec.iterator
    while (it.hasNext) {
      val value = it.next()
      if (func(value)) {
        result += value
      }
    }
    result.toArray
  }

  def map2Arr[T, Y, R](arr1: Array[T], arr2: Array[Y], func: scala.Function2[T, Y, R])(implicit m: ClassTag[R]): Vector[R] = {
    vassert(arr1.length == arr2.length)
    var i = 0
    val result = new Array[R](arr1.length)
    while (i < arr1.length) {
      result(i) = func(arr1(i), arr2(i))
      i = i + 1
    }
    result.toVector
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

  def mapArr[T, R](vec: Array[T], func: scala.Function1[T, R])(implicit m: ClassTag[R]): Array[R] = {
    //    vec.map(func)
    val result = new Array[R](vec.size)
    var i = 0
    while (i < vec.size) {
      result(i) = func(vec(i))
      i = i + 1
    }
    result
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

  def unionMapsExpectDisjoint[K, V](a: Map[K, V], b: Map[K, V]): Map[K, V] = {
    val result = mutable.HashMap[K, V]()
    U.foreachMap[K, V](a, (k, v) => {
      result.put(k, v)
    })
    U.foreachMap[K, V](b, (k, v) => {
      result.put(k, v)
    })
    vassert(result.size == a.size + b.size)
    result.toMap
  }

  def unionMapsExpectNoConflict[K, V](a: Map[K, V], b: Map[K, V], equator: (V, V) => Boolean): Map[K, V] = {
    val result = mutable.HashMap[K, V]()
    U.foreachMap[K, V](a, (k, v) => {
      result.put(k, v)
    })
    U.foreachMap[K, V](b, (k, v) => {
      result.get(k) match {
        case None =>
        case Some(previousValue) => vassert(equator(v, previousValue))
      }
      result.put(k, v)
    })
    result.toMap
  }

  def filterOut[T, Y](
      in: Array[T],
      filter: PartialFunction[T, Y])
      (implicit m1: ClassTag[T], m2: ClassTag[Y]):
  (Array[Y], Array[T]) = {
    val nonmatching = new Array[T](in.length)
    var nonmatchingI = 0
    val matching = new Array[Y](in.length)
    var matchingI = 0
    for (x <- in) {
      if (filter.isDefinedAt(x)) {
        matching(matchingI) = filter(x);
        matchingI = matchingI + 1
      } else {
        nonmatching(nonmatchingI) = x;
        nonmatchingI = nonmatchingI + 1
      }
    }
    (matching.slice(0, matchingI), nonmatching.slice(0, nonmatchingI))
  }

//  def concat[T](a: Array[T], b: Array[T])(implicit m1: ClassTag[T]): Array[T] = {
//    val arr = new Array[T](a.length + b.length)
//    var i = 0
//    for (x <- a) {
//      arr(i) = x
//      i = i + 1
//    }
//    for (x <- b) {
//      arr(i) = x
//      i = i + 1
//    }
//    arr
//  }

  def replaceAll(original: String, replacements: Map[String, String]): String = {
    var str = original
    for ((from, to) <- replacements) {
      str = str.replace(from, to)
    }
    str
  }

  // Get all possible versions of originalMap where the keys are the same
  // but the value for each is randomized.
  def scrambles[T, Y](originalMap: Map[T, Y]): Array[Map[T, Y]] = {
    val originalKeys = originalMap.keys
    val originalVals = originalMap.values.toList

    val valsPermuted = originalVals.permutations.toList
    val keysRepeated = U.repeat(originalKeys, valsPermuted.size)

    keysRepeated.zip(valsPermuted)
        .map({ case (keys, vals) => keys.zip(vals).toMap })
        .toArray // TODO: optimize
  }
}
