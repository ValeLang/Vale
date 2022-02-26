package net.verdagon.vale

import scala.collection.immutable.HashMap
import scala.collection.mutable

object Profiler {
  def frame[T](profilee: () => T): T = {
    profilee()
  }
}
