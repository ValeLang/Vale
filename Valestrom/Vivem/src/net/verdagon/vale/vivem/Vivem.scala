package net.verdagon.vale.vivem

import java.io.PrintStream

import net.verdagon.vale.metal.{ProgramH, ShareH}
import net.verdagon.vale.{vassert, vfail, vimpl}
import net.verdagon.von.IVonData

case class PanicException() extends Throwable

object Vivem {
  def executeWithPrimitiveArgs(
      programH: ProgramH,
      externalArgumentReferends: Vector[PrimitiveReferendV],
      vivemDout: PrintStream,
      stdin: () => String,
      stdout: String => Unit): IVonData = {
    val heap = new Heap(vivemDout)
    val argReferences =
      externalArgumentReferends.map(argReferend => {
        heap.add(ShareH, argReferend);
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

  def stdinFromList(stdinList: List[String]) = {
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
    val main = programH.main

    val callId = CallId(0, main.prototype)

    vivemDout.print("Making stack frame")
    vivemDout.println()

    val (calleeCallId, retuurn) =
      FunctionVivem.executeFunction(programH, stdin, stdout, heap, argumentReferences, main)
    val returnRef = retuurn.returnRef

    vivemDout.print("Ending program")

    val von = heap.toVon(returnRef)
    ExpressionVivem.dropReferenceIfNonOwning(programH, heap, stdout, stdin, calleeCallId, returnRef)
    vivemDout.println()
    println("Checking for leaks")
    heap.checkForLeaks()
    vivemDout.println()
    von
  }
}
