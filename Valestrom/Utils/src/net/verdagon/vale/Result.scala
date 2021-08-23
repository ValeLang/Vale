package net.verdagon.vale

sealed trait Result[T, E] {
  def getOrDie(): T
  def expectErr(): E
}
case class Ok[T, E](t: T) extends Result[T, E] {
  override def hashCode(): Int = vcurious()

  override def getOrDie(): T = t
  override def expectErr(): E = vfail("Called expectErr on an Ok")
}
case class Err[T, E](e: E) extends Result[T, E] {
  override def hashCode(): Int = vcurious()

  override def getOrDie(): T = vfail("Called getOrDie on an Err:\n" + e)
  override def expectErr(): E = e
}
