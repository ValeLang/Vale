package net.verdagon.vale

import scala.collection.immutable.HashMap
import scala.collection.mutable

case class FinishedProfile(args: String, profileTotalNanoseconds: Long, rootFrame: FinishedFrame)
case class FinishedFrame(nanoseconds: Long, children: Map[String, List[FinishedFrame]]) {
  def totalTime: Long = nanoseconds + children.map(_._2.map(_.totalTime).sum).sum
}

trait IProfiler {
  def newProfile[T](profileName: String, args: String, profilee: () => T): T
  def childFrame[T](frameName: String, profilee: () => T): T
}

class NullProfiler extends IProfiler {
  override def newProfile[T](profileName: String, args: String, profilee: () => T): T = profilee()
  override def childFrame[T](frameName: String, profilee: () => T): T = profilee()
}

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

class InProgressProfile(name: String, args: String, var currentFrame: InProgressFrame) {
  val timer = new Timer()

  def start() = {
    resume()
  }

  def resume() = {
    timer.start()
    currentFrame.resume()
  }

  def pause() = {
    currentFrame.pause()
    timer.stop()
  }

  def stop() = {
    pause()
  }
}

class InProgressFrame(name: String) {
  val timer = new Timer()
  val children = new mutable.HashMap[String, List[FinishedFrame]]

  def start() = {
    resume()
  }

  def resume() = {
    timer.start()
  }

  def pause() = {
    timer.stop()
  }

  def stop() = {
    pause()
  }
}

class Profiler extends IProfiler {
  var profiles = new mutable.HashMap[String, List[FinishedProfile]]()

  var currentProfile: Option[InProgressProfile] = None

  def newProfile[T](profileName: String, args: String, profilee: () => T): T = {
    val parentProfile = currentProfile
    parentProfile.foreach(_.pause())

    val newProfile = new InProgressProfile(profileName, args, new InProgressFrame(profileName))
    currentProfile = Some(newProfile)
    newProfile.start()
    val result = profilee()
    newProfile.stop()
    val finishedProfileRootFrame =
      FinishedProfile(
        args,
        newProfile.timer.nanosecondsSoFar,
        FinishedFrame(newProfile.currentFrame.timer.nanosecondsSoFar, newProfile.currentFrame.children.toMap))

    // Don't add it to the parent frame's children, instead add it to the profiles list
    var profilesWithThisName = profiles.getOrElse(profileName, List.empty)
    profilesWithThisName = finishedProfileRootFrame :: profilesWithThisName
    profiles += (profileName -> profilesWithThisName)

    parentProfile.foreach(_.resume())
    currentProfile = parentProfile

    result
  }

  def childFrame[T](frameName: String, profilee: () => T): T = {
    val parentFrame = currentProfile.get.currentFrame
    parentFrame.pause()

    val newFrame = new InProgressFrame(frameName)
    currentProfile.get.currentFrame = newFrame
    newFrame.start()
    val result = profilee()
    newFrame.stop()

    val finishedFrame = FinishedFrame(newFrame.timer.nanosecondsSoFar, newFrame.children.toMap)

    // Add it to the parent frame's children
    var childFramesWithThisName = parentFrame.children.getOrElse(frameName, List.empty)
    childFramesWithThisName = finishedFrame :: childFramesWithThisName
    parentFrame.children += (frameName -> childFramesWithThisName)

    parentFrame.resume()
    currentProfile.get.currentFrame = parentFrame

    result
  }

  def totalNanoseconds = {
    vassert(currentProfile.isEmpty)
    profiles.flatMap(_._2.map(_.profileTotalNanoseconds)).sum
  }

  def assembleResults(): String = {
    vassert(currentProfile.isEmpty)
    val builder = new mutable.StringBuilder()
    profiles.foreach({ case (name, profiles) =>
      builder.append(name + " overall: avg " + profiles.map(_.profileTotalNanoseconds).sum / profiles.size + " sum " + profiles.map(_.profileTotalNanoseconds).sum + ". ")
      printFrames(builder, 0, "root", profiles.map(_.rootFrame))
    })
    builder.append("Total over all profiles: " + totalNanoseconds)
    builder.mkString
  }

  def printFrames(builder: mutable.StringBuilder, indent: Int, name: String, frames: List[FinishedFrame]): Unit = {
    builder.append(repeatStr("  ", indent * 2) + name + ": avg " + frames.map(_.totalTime).sum / frames.size + " sum " + frames.map(_.totalTime).sum + "\n")
    val combinedChildren = frames.flatMap(_.children).groupBy(_._1).mapValues(_.flatMap(_._2))
    combinedChildren.foreach({ case (childName, combinedChildren) =>
      printFrames(builder, indent + 1, childName, combinedChildren)
    })
  }
}
