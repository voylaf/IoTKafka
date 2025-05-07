package com.github

import com.github.voylaf.avro.{Article => AvroArticle}
import com.github.voylaf.domain.Article
import shapeless.{:+:, CNil}

package object voylaf {
  type ArticleLike = Article :+: AvroArticle :+: CNil
}
