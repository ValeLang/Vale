package net.verdagon.vale

class Timer {
  var nanosecondsSoFar = 0L;
  var startTime = 0L;

  def start(): Unit = {
    vassert(startTime == 0)
    startTime = System.nanoTime();
  }

  def stop(): Unit = {
    vassert(startTime != 0)
    nanosecondsSoFar += System.nanoTime() - startTime;
    startTime = 0
  }

  def getNanosecondsSoFar(): Long = {
    vassert(startTime == 0)
    nanosecondsSoFar
  }
}