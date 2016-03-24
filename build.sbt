name         := """scala-intro-presentation"""
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scala-lang"  % "scala-compiler" % "2.11.7" % Compile
)

initialCommands in (Test, console) := """ammonite.repl.Main.run("")"""
