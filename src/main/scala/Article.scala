package com.github.voylaf

import java.time.LocalDate

final case class Article(
    id: String,
    title: String,
    content: String,
    created: LocalDate,
    author: Author
)

case class Author(name: String)
