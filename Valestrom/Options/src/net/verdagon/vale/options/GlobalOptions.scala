package net.verdagon.vale.options

object GlobalOptions {
  def apply(): GlobalOptions = {
    GlobalOptions(false, true, false, false)
  }

  def test(): GlobalOptions = {
    GlobalOptions(true, true, true, true)
  }
}

case class GlobalOptions(
  sanityCheck: Boolean,
  useOptimizedSolver: Boolean,
  verboseErrors: Boolean,
  debugOutput: Boolean)
