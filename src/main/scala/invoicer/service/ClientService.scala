package invoicer
package service

import invoicer.dao.ClientDAO
import invoicer.model.Client

/** Logique métier autour des clients. */
final class ClientService:

  def list(): Seq[Client] = ClientDAO.findAll()

  def search(fragment: String): Seq[Client] =
    if fragment.trim.isEmpty then list()
    else ClientDAO.searchByName(fragment)

  def save(client: Client): Client =
    require(client.name.trim.nonEmpty, "Le nom du client est obligatoire")
    client.id match
      case Some(_) =>
        ClientDAO.update(client)
        client
      case None =>
        ClientDAO.insert(client)

  def delete(client: Client): Unit =
    client.id.foreach(ClientDAO.delete)
