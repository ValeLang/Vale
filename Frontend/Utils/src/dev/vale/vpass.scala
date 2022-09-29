package dev.vale

// Just here so we have something to attach debug breakpoints to
object vpass {
  def apply(): Unit = { }
}
object vbreak {
  def apply(): Unit = {
    vpass()
  }
}
