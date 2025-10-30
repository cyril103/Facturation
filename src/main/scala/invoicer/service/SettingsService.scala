package invoicer
package service

import invoicer.dao.SettingsDAO

/** Service de gestion des paramètres généraux. */
final class SettingsService:

  def vatRate(): BigDecimal = SettingsDAO.vatRate()

  def updateVatRate(rate: BigDecimal): Unit =
    require(rate >= 0, "La TVA ne peut pas être négative")
    SettingsDAO.updateVatRate(rate)
