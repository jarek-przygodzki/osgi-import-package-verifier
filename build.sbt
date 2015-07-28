import scala.util.Try

name := "oipv"

val gitDescription = Try(Process("git describe --all --always --dirty --long").lines.head
  .replace("heads/","").replace("-0-g","-")).getOrElse("unknown")

version := s"1.0-$gitDescription"

scalaVersion := "2.11.7"

libraryDependencies += "biz.aQute.bnd" % "bndlib" % "2.4.0" exclude("org.osgi", "org.osgi.core")
libraryDependencies += "org.eclipse.osgi" % "org.eclipse.osgi" % "3.7.1"
libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0"

scalacOptions ++= Seq("-encoding", "UTF-8")

enablePlugins(JavaAppPackaging)
