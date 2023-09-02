package dev.vale.options

object GlobalOptions {
  def apply(): GlobalOptions = {
    GlobalOptions(
      sanityCheck = false,
      useOverloadIndex = false,
      useOptimizedSolver = true,
      verboseErrors = false,
      debugOutput = false)
  }

  def test(): GlobalOptions = {
    GlobalOptions(true, false, true, true, true)
  }
}

case class GlobalOptions(
  sanityCheck: Boolean,
  useOverloadIndex: Boolean,
  useOptimizedSolver: Boolean,
  verboseErrors: Boolean,
  debugOutput: Boolean)
