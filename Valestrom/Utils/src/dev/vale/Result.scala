package dev.vale

object Result {
  def split[T, E](results: Iterable[Result[T, E]]): (Iterable[T], Iterable[E]) = {
    (results.collect({ case Ok(t) => t }), results.collect({ case Err(e) => e }))
  }
}

sealed trait Result[+T, +E] {
  def getOrDie(): T
  def expectErr(): E
  def map[Y](func: (T) => Y): Result[Y, E]
  def mapError[Y](func: (E) => Y): Result[T, Y]
}
case class Ok[T, E](t: T) extends Result[T, E] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def getOrDie(): T = t
  override def expectErr(): E = vfail("Called expectErr on an Ok")

  override def mapError[Y](func: E => Y): Result[T, Y] = Ok(t)
  override def map[Y](func: T => Y): Result[Y, E] = Ok(func(t))
}
case class Err[T, E](e: E) extends Result[T, E] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  override def getOrDie(): T = vfail("Called getOrDie on an Err:\n" + e)
  override def expectErr(): E = e
  override def mapError[Y](func: E => Y): Result[T, Y] = Err(func(e))
  override def map[Y](func: T => Y): Result[Y, E] = Err(e)
}
