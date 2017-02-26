package com.github.iamundef.api.resources

import com.github.iamundef.api.utils.withTransaction
import com.google.api.client.extensions.appengine.http.UrlFetchTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.UrlEncodedContent
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Key
import com.google.appengine.api.ThreadManager
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.KeyFactory
import com.google.appengine.api.datastore.Query
import java.io.StringReader
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader

data class LoadedPage(@Key var status: String = "", @Key var data: String = "", @Key var stop: Boolean = true)

@Path("update")
class UpdateResource {

    companion object {
        private val logger = Logger.getLogger(UpdateResource::class.java.name)

        private val GAMES = listOf<String>("6x49", "6x45", "5x36")
        private val REQUEST_FACTORY: HttpRequestFactory = UrlFetchTransport().createRequestFactory {
            it.parser = JsonObjectParser(JacksonFactory())
        }
        private val HTML_BEGIN = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"" +
                " \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
        private val HTML_END = "</html>"
        private val NON_NUMBER_PATTERN = Regex("[^0-9]")
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun update(): Response {
        Executors.newCachedThreadPool(ThreadManager.currentRequestThreadFactory()).invokeAll(GAMES.map { game ->
            Callable<Unit> {
                withTransaction { dataStore, txn ->
                    val gameKey = KeyFactory.createKey("game", game)
                    dataStore.prepare(txn, Query(game, gameKey).setKeysOnly()
                            .addSort(Entity.KEY_RESERVED_PROPERTY, Query.SortDirection.DESCENDING))
                            .asList(FetchOptions.Builder.withLimit(1))
                            .firstOrNull()
                            .let { last ->
                                if (last == null) {
                                    dataStore.put(txn, Entity(gameKey))
                                }
                                var page = 1
                                while (true) {
                                    val loaded = load(game, page++).filter { last == null || it.key > last.key.id }
                                    if (loaded.isEmpty()) {
                                        break
                                    }
                                    loaded.forEach { circulation ->
                                        Entity(game, circulation.key, gameKey).let {
                                            it.setProperty("numbers", circulation.value)
                                            dataStore.put(txn, it)
                                        }
                                    }
                                    logger.info("Game: $game, Count of records loaded: ${loaded.size}")
                                }
                            }
                }
                logger.info("Game: $game updated")
            }
        })
        return Response.ok().build()
    }

    private fun load(game: String, page: Int): Map<Long, MutableList<Int>> = REQUEST_FACTORY
            .buildPostRequest(GenericUrl("http://www.stoloto.ru/draw-results/$game/load"),
                    UrlEncodedContent(mapOf("page" to page, "isMobile" to "true")))
            .execute()
            .parseAs(LoadedPage::class.java)
            .let {
                val result = HashMap<Long, MutableList<Int>>()
                if (!it.stop) {
                    val data = "${HTML_BEGIN}${it.data}${HTML_END}"
                    with(XMLInputFactory.newInstance().createXMLStreamReader(StringReader(data))) {
                        var isGameTag = false
                        var isNumberTag = false
                        var circulation: Long? = null
                        while (hasNext()) {
                            next()
                            if (eventType == XMLStreamReader.START_ELEMENT && (localName == "div"
                                    || localName == "span")) {
                                val classValue = getAttributeValue(null, "class")
                                if (classValue.contains("fields__game")) {
                                    circulation = null
                                    isGameTag = true
                                } else if (classValue.contains("fields__item")) {
                                    isNumberTag = true
                                }
                            } else if (eventType == XMLStreamReader.CHARACTERS) {
                                if (isGameTag) {
                                    circulation = NON_NUMBER_PATTERN.replace(text, "").toLong()
                                    isGameTag = false
                                } else if (isNumberTag && circulation != null) {
                                    result.getOrPut(circulation, { ArrayList<Int>() }).add(NON_NUMBER_PATTERN
                                            .replace(text, "")
                                            .toInt())
                                    isNumberTag = false
                                }
                            }
                        }
                    }
                }
                result
            }
}