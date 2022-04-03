package dev.vale.benchmark

import dev.vale.highertyping.HigherTypingErrorHumanizer
import dev.vale.passmanager.FullCompilationOptions
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.typing.CompilerErrorHumanizer
import dev.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, Ok, PackageCoordinate, RunCompilation, Tests, Timer}
import dev.vale.Err

import scala.collection.immutable.List

object Benchmark {
  def go(useOptimization: Boolean): Long = {
    val timer = new Timer()
    timer.start()
    val compile =
      new RunCompilation(
        Vector(PackageCoordinate.BUILTIN, PackageCoordinate.TEST_TLD),
        Builtins.getCodeMap()
//          .or(FileCoordinateMap.test(Tests.loadExpected("programs/addret.vale")))
          .or(FileCoordinateMap.test(Tests.loadExpected("programs/roguelike.vale")))
          .or(Tests.getPackageToResourceResolver),
        FullCompilationOptions(
          GlobalOptions(false, useOptimization, false, false),
          debugOut = (_) => {}))
    compile.getParseds() match {
      case Err(e) => println(ParseErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), FileCoordinate.test, e.error))
      case Ok(t) =>
    }
//    compile.getScoutput() match {
//      case Err(e) => println(ScoutErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
//      case Ok(t) =>
//    }
//    compile.getAstrouts() match {
//      case Err(e) => println(AstronomerErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
//      case Ok(t) =>
//    }
//    compile.getCompilerOutputs() match {
//      case Err(e) => println(CompilerErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), e))
//      case Ok(t) =>
//    }
//    compile.getHamuts()
    timer.stop()
    timer.getNanosecondsSoFar()
  }

  def main(args: Array[String]): Unit = {
    val compareOptimization = false

    println("Starting benchmarking...")
    if (compareOptimization) {
      // Do one run, since the first run always takes longer due to class loading, loading things into the instruction
      // cache, etc.
      // Eventually, we'll want a benchmarking program that does take these things into account, such as by invoking the
      // driver over and over, because class loading, icache, etc are all totally valid things that we care about.
      // For now though, this will do.
      go(true)
      go(false)
      val timesForOldAndNew = (0 until 10).map(_ => (go(false), go(true)))
      val timesForOld = timesForOldAndNew.map(_._1)
      val timesForNew = timesForOldAndNew.map(_._2)
      val averageTimeForOld = timesForOld.sum / timesForOld.size
      val averageTimeForNew = timesForNew.sum / timesForNew.size
      println("Done benchmarking! Old: " + averageTimeForOld + ", New: " + averageTimeForNew)
    } else {
//      // Do one run, since the first run always takes longer due to class loading, loading things into the instruction
//      // cache, etc.
//      // Eventually, we'll want a benchmarking program that does take these things into account, such as by invoking the
//      // driver over and over, because class loading, icache, etc are all totally valid things that we care about.
//      // For now though, this will do.
//      println("Warming up...")
//      val warmupTime = (0 until 10).map(_ => go(true)).sum
//      println("Warmed up (" + warmupTime + "ns)")
//      println("Benchmarking...")

      val profiles = (0 until 200).map(_ => go(true))
      val times = profiles
      val averageTime = times.sum / times.size
      println("Done benchmarking! Total: " + averageTime)
    }

//    println(go(true).assembleResults())
  }
}
