name := "Frontend"

version := "1.0"

scalaVersion := "2.12.19"

// resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

// libraryDependencies += "com.typesafe.akka" % "akka-actor" % "2.0.2"



// for debugging sbt problems
logLevel := Level.Debug

// scalacOptions += "-deprecation"


// scalaSource in Compile := List((baseDirectory.value / "src2"))
(unmanagedSourceDirectories) in Compile := Seq(
    (baseDirectory.value / "Von" / "src"),
    (baseDirectory.value / "Utils" / "src"),
    (baseDirectory.value / "ParsingPass" / "src"),
    (baseDirectory.value / "LexingPass" / "src"),
    (baseDirectory.value / "HigherTypingPass" / "src"),
    (baseDirectory.value / "PassManager" / "src"),
    (baseDirectory.value / "SimplifyingPass" / "src"),
    (baseDirectory.value / "InstantiatingPass" / "src"),
    (baseDirectory.value / "Highlighter" / "src"),
    (baseDirectory.value / "CompileOptions" / "src"),
    (baseDirectory.value / "Builtins" / "src"),
    (baseDirectory.value / "FinalAST" / "src"),
    (baseDirectory.value / "Samples" / "src"),
    (baseDirectory.value / "PostParsingPass" / "src"),
    (baseDirectory.value / "Solver" / "src"),
    (baseDirectory.value / "TypingPass" / "src"),
    (baseDirectory.value / "TestVM" / "src"))
    // (baseDirectory.value / "src2"))

(unmanagedResourceDirectories) in Compile := Seq(
    baseDirectory.value / "Builtins" / "src" / "dev" / "vale" / "resources")

(unmanagedSourceDirectories) in Test := Seq(
    (baseDirectory.value / "Tests" / "src"),
    (baseDirectory.value / "Tests" / "test"),
    (baseDirectory.value / "IntegrationTests" / "test"),
    (baseDirectory.value / "Von" / "test"),
    (baseDirectory.value / "Utils" / "test"),
    (baseDirectory.value / "ParsingPass" / "test"),
    (baseDirectory.value / "LexingPass" / "test"),
    (baseDirectory.value / "HigherTypingPass" / "test"),
    (baseDirectory.value / "PassManager" / "test"),
    (baseDirectory.value / "SimplifyingPass" / "test"),
    (baseDirectory.value / "InstantiatingPass" / "test"),
    (baseDirectory.value / "Highlighter" / "test"),
    (baseDirectory.value / "CompileOptions" / "test"),
    (baseDirectory.value / "Builtins" / "test"),
    (baseDirectory.value / "FinalAST" / "test"),
    (baseDirectory.value / "Samples" / "test"),
    (baseDirectory.value / "PostParsingPass" / "test"),
    (baseDirectory.value / "Solver" / "test"),
    (baseDirectory.value / "TypingPass" / "test"),
    (baseDirectory.value / "TestVM" / "test"))

(unmanagedResourceDirectories) in Test := Seq(
    baseDirectory.value / "Tests" / "test" / "main" / "resources")

assemblyJarName in assembly := "Frontend.jar"
assemblyOutputPath in assembly := (baseDirectory.value / "Frontend.jar")
