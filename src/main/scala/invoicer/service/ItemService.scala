package invoicer
package service

import invoicer.dao.ItemDAO
import invoicer.model.Item

/** Gestion métier des articles/prestations. */
final class ItemService:

  def list(): Seq[Item] = ItemDAO.findAll()

  def search(fragment: String): Seq[Item] =
    if fragment.trim.isEmpty then list()
    else ItemDAO.searchByName(fragment)

  def save(item: Item): Item =
    require(item.name.trim.nonEmpty, "Le nom de l'article est obligatoire")
    require(item.unitPriceHt >= 0, "Le prix ne peut pas être négatif")
    item.id match
      case Some(_) =>
        ItemDAO.update(item)
        item
      case None =>
        ItemDAO.insert(item)

  def delete(item: Item): Unit =
    item.id.foreach { id =>
      if ItemDAO.isInUse(id) then
        throw IllegalStateException("Impossible de supprimer un article deja utilise dans une facture.")
      ItemDAO.delete(id)
    }
