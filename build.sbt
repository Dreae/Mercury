name := "Mercury"

version := "1.0.0"

resolvers ++= Seq(
  "Scala Tools" at "https://oss.sonatype.org/content/groups/scala-tools/"
)

connectInput in run := true

fork in run := true

libraryDependencies ++= Seq(
  "io.netty" % "netty-all" % "4.0.23.Final",
  "com.typesafe" % "config" % "1.2.1"
)