package com.github.iamundef.api.resources

import com.github.iamundef.api.resources.sub.FreqResource
import com.github.iamundef.api.resources.sub.LinesResource
import com.github.iamundef.api.utils.withDataStore
import com.google.appengine.api.datastore.DatastoreNeedIndexException
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.Query
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

inline fun <R> processCirculations(game: String, circulationCount: Int, circulationOffset: Int,
                                   process: (List<Entity>) -> R): R {
    if (circulationCount < 1 || circulationOffset < 0) {
        throw BadRequestException()
    }
    return (withDataStore { dataStore ->
        try {
            dataStore.prepare(Query(game).setKeysOnly()
                    .addSort(Entity.KEY_RESERVED_PROPERTY, Query.SortDirection.DESCENDING))
                    .asQueryResultList(FetchOptions.Builder.withLimit(circulationOffset))
                    .cursor
                    .let {
                        dataStore.prepare(Query(game)
                                .addSort(Entity.KEY_RESERVED_PROPERTY, Query.SortDirection.DESCENDING))
                                .asList(FetchOptions.Builder.withLimit(circulationCount).startCursor(it))
                                .let {
                                    when {
                                        it.isEmpty() -> null
                                        else -> it
                                    }
                                }
                    }
        } catch (ignore: DatastoreNeedIndexException) {
            null
        }
    } ?: throw NotFoundException()).let { process(it) }
}

@Path("game/{game}")
@Produces(MediaType.APPLICATION_JSON)
class GameResource(@PathParam("game") val game: String) {

    @GET
    fun getLast(): Response = processCirculations(game, 1, 0) {
        Response.ok(mapOf("circulation" to it.first().key.id, "numbers" to it.first().properties["numbers"])).build()
    }

    @GET
    @Path("{count}")
    fun getLast(@PathParam("count") count: Int): Response = processCirculations(game, count, 0) {
        Response.ok(it.map { mapOf("circulation" to it.key.id, "numbers" to it.properties["numbers"]) }).build()
    }

    @Path("freq")
    fun getFreq(): Class<FreqResource> = FreqResource::class.java

    @Path("lines")
    fun getLines(): Class<LinesResource> = LinesResource::class.java
}