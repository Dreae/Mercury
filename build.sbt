name := "Mercury"

version := "1.0.0"

resolvers ++= Seq(
  "Scala Tools" at "https://oss.sonatype.org/content/groups/scala-tools/",
  "Maven Central Repo" at "https://repo1.maven.org/"
)

connectInput in run := true

fork in run := true

libraryDependencies ++= Seq(
  "io.netty" % "netty-all" % "4.0.23.Final" withJavadoc() withSources(),
  "com.typesafe" % "config" % "1.2.1"
)