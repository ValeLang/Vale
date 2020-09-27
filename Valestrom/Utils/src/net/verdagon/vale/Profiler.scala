package net.verdagon.vale

import com.jprofiler.api.probe.embedded.{Split, SplitProbe}

import scala.collection.immutable.HashMap
import scala.collection.mutable

case class FinishedProfile(args: String, rootFrame: FinishedFrame)
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

class ValeSplitProbe extends SplitProbe {
  override def getName: String = "ValeSplitProbe"
  override def getDescription: String = "infer some things"
  override def isPayloads: Boolean = true
  override def isReentrant: Boolean = true
}

class InProgressProfile(name: String, args: String, var currentFrame: InProgressFrame) {
  def start() = {
    resume()
  }

  def resume() = {
    currentFrame.resume()
  }

  def pause() = {
    currentFrame.pause()
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
    try {
      val newProfile = new InProgressProfile(profileName, args, new InProgressFrame(profileName))
      currentProfile = Some(newProfile)
      newProfile.start()
      val result =
        try {
          profilee()
        } finally {
          newProfile.stop()
        }
      val finishedProfileRootFrame =
        FinishedProfile(
          args,
          FinishedFrame(newProfile.currentFrame.timer.nanosecondsSoFar, newProfile.currentFrame.children.toMap))

      // Don't add it to the parent frame's children, instead add it to the profiles list
      var profilesWithThisName = profiles.getOrElse(profileName, List())
      profilesWithThisName = finishedProfileRootFrame :: profilesWithThisName
      profiles += (profileName -> profilesWithThisName)

      result
    } finally {
      parentProfile.foreach(_.resume())
      currentProfile = parentProfile
    }
  }

  def childFrame[T](frameName: String, profilee: () => T): T = {
    val parentFrame = currentProfile.get.currentFrame
    parentFrame.pause()
    try {
      val newFrame = new InProgressFrame(frameName)
      currentProfile.get.currentFrame = newFrame
      newFrame.start()
      val result =
        try {
          profilee()
        } finally {
          newFrame.stop()
        }
      val finishedFrame = FinishedFrame(newFrame.timer.nanosecondsSoFar, newFrame.children.toMap)

      // Add it to the parent frame's children
      var childFramesWithThisName = parentFrame.children.getOrElse(frameName, List())
      childFramesWithThisName = finishedFrame :: childFramesWithThisName
      parentFrame.children += (frameName -> childFramesWithThisName)

      result
    } finally {
      parentFrame.resume()
      currentProfile.get.currentFrame = parentFrame
    }
  }

  def assembleResults(): String = {
    vassert(currentProfile.isEmpty)
    val builder = new mutable.StringBuilder()
    profiles.foreach({ case (name, profiles) =>
      builder.append(name + ": " + profiles.map(_.rootFrame.totalTime).sum / profiles.size + "\n")
      profiles.foreach(profile => {
        builder.append("  " + profile.args + ": ")
        printFrames(builder, 2, List(profile.rootFrame))
      })
    })
    builder.mkString
  }

  def printFrames(builder: mutable.StringBuilder, indent: Int, frames: List[FinishedFrame]): Unit = {
    builder.append(frames.map(_.totalTime).sum / frames.size + "\n")
    val combinedChildren = frames.flatMap(_.children).groupBy(_._1).mapValues(_.flatMap(_._2))
    combinedChildren.foreach({ case (childName, combinedChildren) =>
      builder.append("  ".repeat(indent * 2) + childName + ": ")
      printFrames(builder, indent + 1, combinedChildren)
    })
  }
}
