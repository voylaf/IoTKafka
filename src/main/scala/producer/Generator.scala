package com.github.voylaf
package producer

import java.time.LocalDate
import scala.util.Random

trait Generator {
  def article: Article
  def articles: Seq[Article]
}

object SimpleGenerator {
  def withSeed(seed: Long): Generator = new Generator {
    private val rand = new Random(seed)

    private val charset: IndexedSeq[Char] =
      ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

    private def randomString(length: Int): String =
      (1 to length).map(_ => charset(rand.nextInt(charset.length))).mkString

    private lazy val authors: Vector[Author] =
      (1 to 10).map(_ => Author(randomString(Random.between(5, 10)))).toVector

    def articles: LazyList[Article] =
      article #:: articles

    def article: Article =
      Article(
        id = randomString(16),
        title = randomString(10),
        content = randomString(100),
        created = LocalDate.now(),
        author = authors(rand.nextInt(authors.length))
      )
  }
}

object FancyGenerator {
  def withSeed(seed: Long): Generator = new Generator {
    private val rand = new Random(seed)

    private val firstNames = Vector("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank", "Ivy", "Jack")
    private val lastNames  = Vector("Smith", "Johnson", "Brown", "Taylor", "Anderson", "Thomas", "Moore", "Jackson", "White", "Harris")

    private val titleWords =
      Vector("Scala", "Kafka", "Streams", "Functional", "Programming", "Concurrency", "Performance", "Design", "Best", "Practices")
    private val loremWords = Vector(
      "lorem",
      "ipsum",
      "dolor",
      "sit",
      "amet",
      "consectetur",
      "adipiscing",
      "elit",
      "sed",
      "do",
      "eiusmod",
      "tempor",
      "incididunt",
      "ut",
      "labore",
      "et",
      "dolore",
      "magna",
      "aliqua"
    )

    private def randomFrom[T](seq: Seq[T]): T = seq(rand.nextInt(seq.length))

    private def fullName: String =
      s"${randomFrom(firstNames)} ${randomFrom(lastNames)}"

    private lazy val authors: Vector[Author] =
      (1 to 10).map(_ => Author(fullName)).toVector

    def articles: LazyList[Article] =
      article #:: articles

    def article: Article = {
      val title   = (1 to rand.between(2, 5)).map(_ => randomFrom(titleWords)).mkString(" ")
      val content = (1 to rand.between(30, 60)).map(_ => randomFrom(loremWords)).mkString(" ").capitalize + "."
      val created = LocalDate.now().minusDays(rand.nextInt(30))
      val id      = rand.alphanumeric.take(16).mkString

      Article(
        id = id,
        title = title,
        content = content,
        created = created,
        author = randomFrom(authors)
      )
    }
  }
}
