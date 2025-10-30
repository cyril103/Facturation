package invoicer
package model

/** Représente les coordonnées de l'entreprise émettrice. */
final case class Company(
    id: Option[Int],
    name: String,
    address: Option[String],
    email: Option[String],
    phone: Option[String],
    siret: Option[String]
)
