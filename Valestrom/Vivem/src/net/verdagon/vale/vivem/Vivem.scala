package net.verdagon.vale.vivem

import java.io.PrintStream
import net.verdagon.vale.metal.{InlineH, ProgramH, ReadonlyH, ShareH}
import net.verdagon.vale.{FileCoordinateMap, IPackageResolver, PackageCoordinateMap, Result, vassert, vfail, vimpl, vpass}
import net.verdagon.von.IVonData

import scala.collection.immutable.List

case class PanicException() extends Throwable { val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash; }
case class ConstraintViolatedException(msg: String) extends Throwable {
  val hash = runtime.ScalaRunTime._hashCode(this); override def hashCode(): Int = hash;
  vpass()
}

object Vivem {
  def executeWithPrimitiveArgs(
      programH: ProgramH,
      externalArgumentKinds: Vector[PrimitiveKindV],
      vivemDout: PrintStream,
      stdin: () => String,
      stdout: String => Unit): IVonData = {
    val heap = new Heap(vivemDout)
    val argReferences =
      externalArgumentKinds.map(argKind => {
        heap.add(ShareH, InlineH, ReadonlyH, argKind);
      });
    innerExecute(programH, argReferences, heap, vivemDout, stdin, stdout)
  }

  def executeWithHeap(
      programH: ProgramH,
      inputHeap: Heap,
      inputArgumentReferences: Vector[ReferenceV],
      vivemDout: PrintStream,
      stdin: () => String,
      stdout: String => Unit):
  IVonData = {
    vassert(inputHeap.countUnreachableAllocations(inputArgumentReferences) == 0)
    innerExecute(programH, inputArgumentReferences, inputHeap, vivemDout, stdin, stdout)
  }

  def emptyStdin() = {
    vfail("Empty stdin!")
  }

  def nullStdout(str: String) = {
  }
  def regularStdout(str: String) = {
    print(str)
  }

  def stdinFromList(stdinList: Vector[String]) = {
    var remainingStdin = stdinList
    val stdin = (() => {
      vassert(remainingStdin.nonEmpty)
      val result = remainingStdin.head
      remainingStdin = remainingStdin.tail
      result
    })
    stdin
  }

  def stdoutCollector(): (StringBuilder, String => Unit) = {
    val stdoutput = new StringBuilder()
    val func = (str: String) => { print(str); stdoutput.append(str); }: Unit
    (stdoutput, func)
  }

  def innerExecute(
      programH: ProgramH,
      argumentReferences: Vector[ReferenceV],
      heap: Heap,
      vivemDout: PrintStream,
      stdin: () => String,
      stdout: String => Unit): IVonData = {
    val main =
      programH.packages.flatMap({ case (packageCoord, paackage) =>
        paackage.exportNameToFunction.get("main").map(prototype => paackage.functions.find(_.prototype == prototype).get).toVector
      }).flatten.toVector match {
        case Vector() => vfail()
        case Vector(m) => m
        case other => vfail(other)
      }

    val callId = CallId(0, main.prototype)

    vivemDout.print("Making stack frame")
    vivemDout.println()

    val (calleeCallId, retuurn) =
      FunctionVivem.executeFunction(programH, stdin, stdout, heap, argumentReferences, main)
    val returnRef = retuurn.returnRef

    vivemDout.print("Ending program")

    val von = heap.toVon(returnRef)
    ExpressionVivem.discard(programH, heap, stdout, stdin, calleeCallId, main.prototype.returnType, returnRef)
    vivemDout.println()
    println("Checking for leaks")
    heap.checkForLeaks()
    vivemDout.println()
    von
  }
}
