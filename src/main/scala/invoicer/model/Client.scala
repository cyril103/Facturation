package invoicer
package model

/** Représente un client de l'entreprise. */
final case class Client(
    id: Option[Int],
    name: String,
    address: Option[String],
    email: Option[String],
    phone: Option[String],
    siret: Option[String]
)
