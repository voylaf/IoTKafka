package com.github.voylaf
package domain

import java.time.LocalDate
import avro.{Article => AvroArticle, Author => AvroAuthor}

final case class Article(
    id: String,
    title: String,
    content: String,
    created: LocalDate,
    author: Author
)

case class Author(name: String)

object Article {
  def fromAvroArticle(avroArticle: AvroArticle): Article = {
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
}
