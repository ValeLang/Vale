package net.verdagon.vale

sealed trait Result[T, E] {
  def getOrDie(): T
}
case class Ok[T, E](t: T) extends Result[T, E] {
  override def getOrDie(): T = t
}
case class Err[T, E](e: E) extends Result[T, E] {
  override def getOrDie(): T = vfail("Called getOrDie on an Err:\n" + e)
}
