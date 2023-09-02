package dev.vale.benchmark

import dev.vale.highertyping.HigherTypingErrorHumanizer
import dev.vale.passmanager.FullCompilationOptions
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.typing.CompilerErrorHumanizer
import dev.vale.{Builtins, Err, FileCoordinate, FileCoordinateMap, Interner, Keywords, Ok, PackageCoordinate, Profiler, RunCompilation, Tests, Timer, U, repeatStr}

import scala.collection.immutable.List

object Benchmark {
  def go(useOptimization: Boolean): Long = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val timer = new Timer()
    timer.start()
    // Not really necessary, but it puts it at the top of the stack trace so we can more
    // easily merge all the Profiler.frame calls in IntelliJ
    Profiler.frame(() => {
      val compile =
        new RunCompilation(
          interner,
          keywords,
          Vector(
            PackageCoordinate.BUILTIN(interner, keywords),
            PackageCoordinate.TEST_TLD(interner, keywords)
          ),
          Builtins.getCodeMap(interner, keywords)
            .or(FileCoordinateMap.test(interner, Tests.loadExpected("programs/addret.vale")))
            .or(FileCoordinateMap.test(interner, Tests.loadExpected("programs/roguelike.vale"))),
//            .or(
//              FileCoordinateMap.test(
//                interner,
//                "exported func main() int {\nx = 0;" +
//                  repeatStr("set x = x + 1;", 100) +
//                  "return x;}")),
          //.or(Tests.getPackageToResourceResolver),
          FullCompilationOptions(
            GlobalOptions(
              sanityCheck = false,
              useOptimization,
              false,
              false),
            debugOut = (_) => {}))
      compile.getParseds() match {
        case Err(e) => println(ParseErrorHumanizer.humanizeFromMap(compile.getCodeMap().getOrDie().fileCoordToContents.toMap, FileCoordinate.test(interner), e.error))
        case Ok(t) =>
      }
//      compile.getScoutput() match {
//        case Err(e) => println(PostParserErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
//        case Ok(t) =>
//      }
//      compile.getAstrouts() match {
//        case Err(e) => println(HigherTypingErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
//        case Ok(t) =>
//      }
//      compile.getCompilerOutputs() match {
//        case Err(e) => println(CompilerErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), e))
//        case Ok(t) =>
//      }
    })
//    compile.getHamuts()
    timer.stop()
    timer.getNanosecondsSoFar()
  }

  def main(args: Vector[String]): Unit = {
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
      val timesForOldAndNew = U.makeArray(10, _ => (go(false), go(true)))
      val timesForOld = U.map(timesForOldAndNew, (x: (Long, Long)) => x._1)
      val timesForNew = U.map(timesForOldAndNew, (x: (Long, Long)) => x._2)
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
//      val warmupTime = (0 until 10000).map(_ => go(true)).sum
//      println("Warmed up (" + warmupTime + "ns)")
      println("Benchmarking...")
      val profiles = U.makeArray(10000, _ => go(true))
      val times = profiles
      val averageTime = times.sum / times.size
      println("Done benchmarking! Total: " + averageTime + " minimum: " + times.min)
    }

//    println(go(true).assembleResults())
  }
}
