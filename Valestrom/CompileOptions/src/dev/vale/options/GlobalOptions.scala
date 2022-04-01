package dev.vale.options

object GlobalOptions {
  def apply(): GlobalOptions = {
    GlobalOptions(
      sanityCheck = false,
      useOptimizedSolver = true,
      verboseErrors = false,
      debugOutput = false)
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
