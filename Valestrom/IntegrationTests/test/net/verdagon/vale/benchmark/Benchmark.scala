package net.verdagon.vale.benchmark

import net.verdagon.vale.driver.{Compilation, CompilationOptions}
import net.verdagon.vale.{Profiler, Samples}

object Benchmark {
  def go(useOptimization: Boolean): Profiler = {
    val profiler = new Profiler()
    val compile = Compilation.multiple(
      List(
        Samples.get("libraries/printutils.vale"),
        Samples.get("libraries/castutils.vale"),
        Samples.get("libraries/utils.vale"),
        Samples.get("libraries/opt.vale"),
        Samples.get("libraries/list.vale"),
        Samples.get("libraries/strings.vale"),
        Samples.get("programs/scratch.vale")),
      CompilationOptions(
        debugOut = (_) => {},
        profiler = profiler,
        useOptimization = useOptimization))
    compile.getHamuts()
    profiler
  }

  def main(args: Array[String]): Unit = {
    println("Starting benchmarking...")
    // Do one run, since the first run always takes longer due to class loading, loading things into the instruction
    // cache, etc.
    // Eventually, we'll want a benchmarking program that does take these things into account, such as by invoking the
    // driver over and over, because class loading, icache, etc are all totally valid things that we care about.
    // For now though, this will do.
    go(true)
    go(false)
    val timesForOldAndNew = (0 until 10).map(_ => (go(false).totalNanoseconds, go(true).totalNanoseconds))
    val timesForOld = timesForOldAndNew.map(_._1)
    val timesForNew = timesForOldAndNew.map(_._2)
    val averageTimeForOld = timesForOld.sum / timesForOld.size
    val averageTimeForNew = timesForNew.sum / timesForNew.size
    println("Done benchmarking! Old: " + averageTimeForOld + ", New: " + averageTimeForNew)

//    println(go(true).assembleResults())
  }
}
