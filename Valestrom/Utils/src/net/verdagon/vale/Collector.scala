package net.verdagon.vale

trait Collector {
  def recursiveCollect[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Vector[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return Vector(partialFunction.apply(a))
    }
    a match {
      case iterable: Iterable[Any] => iterable.flatMap(x => recursiveCollect(x, partialFunction)).toVector
      case p: Product => p.productIterator.flatMap(x => recursiveCollect(x, partialFunction)).toVector
      case _ => Vector.empty
    }
  }

  def recursiveCollectFirst[T, R](a: Any, partialFunction: PartialFunction[Any, R]): Option[R] = {
    if (partialFunction.isDefinedAt(a)) {
      return Some(partialFunction.apply(a))
    }
    a match {
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
      recursiveCollectFirst(program, f) match {
        case None => throw new AssertionError("Couldn't find the thing, in:\n" + program)
        case Some(t) => t
      }
    }
  }
}
