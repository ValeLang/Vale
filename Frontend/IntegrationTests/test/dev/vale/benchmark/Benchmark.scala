package dev.vale.benchmark

import dev.vale._
import dev.vale.highertyping.HigherTypingErrorHumanizer
import dev.vale.options.GlobalOptions
import dev.vale.parsing.ParseErrorHumanizer
import dev.vale.passmanager.FullCompilationOptions
import dev.vale.postparsing.PostParserErrorHumanizer
import dev.vale.typing.CompilerErrorHumanizer

object Benchmark {
  val roguelikeSrc =
    """
      |import hashmap.*;
      |import panicutils.*;
      |exported func main() int {
      |  m = HashMap<int, int>(IntHasher(), IntEquator());
      |  m.add(0, 100);
      |  m.add(4, 101);
      |  m.add(8, 102);
      |  m.add(12, 103);
      |  m.add(16, 104);
      |  m.add(20, 105);
      |  m.add(24, 106);
      |  m.add(28, 107);
      |  m.add(32, 108);
      |  m.add(36, 109);
      |  m.add(40, 110);
      |  m.add(44, 111);
      |  vassertEq(m.get(0).get(), 100, "val at 0 not 100!");
      |  vassertEq(m.get(4).get(), 101, "val at 1 not 101!");
      |  vassertEq(m.get(8).get(), 102, "val at 2 not 102!");
      |  vassertEq(m.get(12).get(), 103, "val at 3 not 103!");
      |  vassertEq(m.get(16).get(), 104, "val at 4 not 104!");
      |  vassertEq(m.get(20).get(), 105, "val at 5 not 105!");
      |  vassertEq(m.get(24).get(), 106, "val at 6 not 106!");
      |  vassertEq(m.get(28).get(), 107, "val at 7 not 107!");
      |  vassertEq(m.get(32).get(), 108, "val at 8 not 108!");
      |  vassertEq(m.get(36).get(), 109, "val at 9 not 109!");
      |  vassertEq(m.get(40).get(), 110, "val at 10 not 110!");
      |  vassertEq(m.get(44).get(), 111, "val at 11 not 111!");
      |  vassert(m.get(1337).isEmpty(), "expected nothing at 1337!");
      |  return m.get(44).get();
      |}
      |""".stripMargin
  def go(useOptimization: Boolean): Long = {
    val interner = new Interner()
    val keywords = new Keywords(interner)
    val timer = new Timer()
    timer.start()
    // Not really necessary, but it puts it at the top of the stack trace so we can more
    // easily merge all the Profiler.frame calls in IntelliJ

    Profiler.frame(() => {
      def thingy(path: String) = {
        val parts = path.split('/')
        val module = interner.intern(StrI(parts(0)))
        val filename = parts.last
        val packages = parts.slice(1, parts.length - 1).map(x => interner.intern(StrI(x))).toVector
        FileCoordinateMap.simple(
          interner.intern(FileCoordinate(interner.intern(PackageCoordinate(module, packages)), filename)),
          Tests.loadExpected(path))
      }

      val compile =
        new RunCompilation(
          interner,
          keywords,
          Vector(
            PackageCoordinate.BUILTIN(interner, keywords),
            PackageCoordinate.TEST_TLD(interner, keywords)
          ),
          Builtins.getCodeMap(interner, keywords)
            .or(thingy("hashmap/hashmap.vale"))
            .or(thingy("list/list.vale"))
            .or(thingy("printutils/printutils.vale"))
            .or(thingy("castutils/castutils.vale"))
            .or(thingy("math/math.vale"))
            .or(thingy("panicutils/panicutils.vale"))
            .or(thingy("array/make/make.vale"))
            .or(FileCoordinateMap.test(interner, roguelikeSrc)),
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
              true,
              false,
              false),
            debugOut = (_) => {}))
      compile.getParseds() //match {
      //   case Err(e) => println(ParseErrorHumanizer.humanizeFromMap(compile.getCodeMap().getOrDie().fileCoordToContents.toMap, FileCoordinate.test(interner), e.error))
      //   case Ok(t) =>
      // }
     compile.getScoutput() //match {
     //   case Err(e) => println(PostParserErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
     //   case Ok(t) =>
     // }
     compile.getAstrouts() //match {
     //   case Err(e) => println(HigherTypingErrorHumanizer.humanize(compile.getCodeMap().getOrDie(), e))
     //   case Ok(t) =>
     // }
     compile.getCompilerOutputs() //match {
     //   case Err(e) => println(CompilerErrorHumanizer.humanize(true, compile.getCodeMap().getOrDie(), e))
     //   case Ok(t) =>
     // }
    })
//    compile.getHamuts()
    timer.stop()
    timer.getNanosecondsSoFar()
  }

  def main(args: Array[String]): Unit = {
    val compareOptimization = true

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
      val profiles = U.makeArray(10, _ => go(true))
      val times = profiles
      val averageTime = times.sum / times.size
      println("Done benchmarking! Total: " + averageTime + " minimum: " + times.min)
    }

//    println(go(true).assembleResults())
  }
}
