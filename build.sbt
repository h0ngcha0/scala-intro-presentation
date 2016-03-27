name         := """scala-intro-presentation"""
scalaVersion := "2.11.7"

libraryDependencies ++= {
  val akkaV       = "2.4.2"
  val scalaTestV  = "2.2.5"
  Seq(
    "org.scala-lang"    % "scala-compiler" % "2.11.7" % Compile,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV
  )
}

initialCommands in (Test, console) := """ammonite.repl.Main.run("")"""
