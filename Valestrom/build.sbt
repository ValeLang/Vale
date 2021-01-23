name := "Valestrom"

version := "1.0"

scalaVersion := "2.12.6"

// resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

// libraryDependencies += "com.typesafe.akka" % "akka-actor" % "2.0.2"

// for debugging sbt problems
logLevel := Level.Debug

// scalacOptions += "-deprecation"


// scalaSource in Compile := List((baseDirectory.value / "src2"))
(unmanagedSourceDirectories) in Compile := Seq(
    (baseDirectory.value / "Von" / "src"),
    (baseDirectory.value / "Utils" / "src"),
    (baseDirectory.value / "Parser" / "src"),
    (baseDirectory.value / "Astronomer" / "src"),
    (baseDirectory.value / "Driver" / "src"),
    (baseDirectory.value / "Hammer" / "src"),
    (baseDirectory.value / "Highlighter" / "src"),
    (baseDirectory.value / "Hinputs" / "src"),
    (baseDirectory.value / "Metal" / "src"),
    (baseDirectory.value / "Samples" / "src"),
    (baseDirectory.value / "Scout" / "src"),
    (baseDirectory.value / "Templar" / "src"),
    (baseDirectory.value / "Templata" / "src"),
    (baseDirectory.value / "Vivem" / "src"))
    // (baseDirectory.value / "src2"))

unmanagedJars in Compile += (baseDirectory.value / "lib" / "scala-parser-combinators_2.12-1.1.1.jar")

(unmanagedResourceDirectories) in Compile := Seq(
    baseDirectory.value / "Samples" / "test" / "main" / "resources")
