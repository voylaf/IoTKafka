package com.github.voylaf
package domain

import KafkaCodecs.KafkaSerdeProviderOps
import avro.{Article => AvroArticle, Author => AvroAuthor}

import cats.effect.Sync
import io.circe.generic.auto._

import java.time.LocalDate
import scala.language.implicitConversions

trait KafkaModel

final case class Article(
    id: String,
    title: String,
    content: String,
    created: LocalDate,
    author: Author
) extends KafkaModel

case class Author(name: String)

object Article {
  implicit def fromAvroArticle(avroArticle: AvroArticle): Article = {
    Article(
      avroArticle.id,
      avroArticle.title,
      avroArticle.content,
      avroArticle.created,
      Author(avroArticle.author.name)
    )
  }

  def toAvroArticle(article: Article): AvroArticle = {
    AvroArticle(
      article.id,
      article.title,
      article.content,
      article.created,
      AvroAuthor(article.author.name)
    )
  }

  implicit def articleSerdeSupport[F[_]: Sync]: SerdeSupport[F, Article] =
    new SerdeSupport[F, Article] {
      def circe: KafkaSerdeProvider[F, String, Article] =
        KafkaCodecs.circeSerdeProvider[F, String, Article]

      def avro(schemaRegistryUrl: String): KafkaSerdeProvider[F, String, Article] =
        KafkaCodecs.avroSerdeProvider[F, String, AvroArticle](schemaRegistryUrl)
          .contramap[Article](
            to = Article.toAvroArticle,
            from = Article.fromAvroArticle
          )
    }
}
