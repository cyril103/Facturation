package invoicer
package service

import invoicer.dao.CompanyDAO
import invoicer.model.Company

/** Service gérant les coordonnées de l'entreprise. */
final class CompanyService:

  private val defaultCompany =
    Company(None, "Mon Entreprise", None, None, None, None)

  def load(): Company =
    CompanyDAO.findCurrent().getOrElse(defaultCompany)

  def save(company: Company): Company =
    require(company.name.trim.nonEmpty, "Le nom de l'entreprise est obligatoire")
    CompanyDAO.save(company)
