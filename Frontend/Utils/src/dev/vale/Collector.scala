package dev.vale

trait Collector {
  def collect[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Iterable[R] = {
    Profiler.frame(() => {
      recursiveCollect(a, partialFunction)
    })
  }

  private def recursiveCollect[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Iterable[R] = {
    (if (partialFunction.isDefinedAt(a)) {
      Vector(partialFunction.apply(a))
    } else {
      Vector()
    }) ++
    (a match {
      case arr: Vector[Any] => arr.flatMap(recursiveCollect(_, partialFunction))
      case iterable: Iterable[Any] => iterable.flatMap(recursiveCollect(_, partialFunction))
      case p: Product => p.productIterator.toIterable.flatMap(recursiveCollect(_, partialFunction))
      case _ => Vector.empty
    })
  }

  def collectFirst[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Option[R] = {
    Profiler.frame(() => {
      recursiveCollectFirst(a, partialFunction)
    })
  }

  private def recursiveCollectFirst[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Option[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return Some(partialFunction.apply(a))
    }
    a match {
      case arr: Vector[Any] => {
        val opt: Option[R] = None
        arr.foldLeft(opt)({
          case (Some(x), _) => Some(x)
          case (None, next) => recursiveCollectFirst(next, partialFunction)
        })
      }
      case iterable: Iterable[Any] => {
        val opt: Option[R] = None
        iterable.foldLeft(opt)({
          case (Some(x), _) => Some(x)
          case (None, next) => recursiveCollectFirst(next, partialFunction)
        })
      }
      case p: Product => {
        val opt: Option[R] = None
        p.productIterator.foldLeft(opt)({
          case (Some(x), _) => Some(x)
          case (None, next) => recursiveCollectFirst(next, partialFunction)
        })
      }
      case _ => None
    }
  }

  implicit class ProgramWithExpect(program: Any) {
    def shouldHave[T](f: PartialFunction[Any, T]): T = {
      collectFirst(program, f) match {
        case None => vfail("Couldn't find the thing, in:\n" + program)
        case Some(t) => t
      }
    }
  }
}

object Collector extends Collector {
  def all[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Iterable[R] = {
    Collector.super.recursiveCollect(a, partialFunction)
  }
  def only[T, R](a: Any, partialFunction: PartialFunction[Any, R]): R = {
    all(a, partialFunction).toList match {
      case List() => vfail("No results, in: " + a)
      case List(only) => only
      case results => vfail(results)
    }
  }
  def onlyOf[T, R](a: Any, clazz: Class[R]): R = {
    only(a, {
      case x if clazz.isInstance(x) => x.asInstanceOf[R]
    })
  }
  def allOf[T, R](a: Any, clazz: Class[R]): Iterable[R] = {
    all(a, {
      case x if clazz.isInstance(x) => x.asInstanceOf[R]
    })
  }
  def first[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Option[R] = {
    Collector.super.recursiveCollectFirst(a, partialFunction)
  }
}
