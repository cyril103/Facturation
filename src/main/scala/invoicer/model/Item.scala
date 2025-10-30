package invoicer
package model

/** Article ou prestation facturable. */
final case class Item(
    id: Option[Int],
    name: String,
    unitPriceHt: BigDecimal,
    description: Option[String]
)
