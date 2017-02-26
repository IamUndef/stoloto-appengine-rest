package com.github.iamundef.api.resources.sub

import com.github.iamundef.api.resources.processCirculations
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Produces(MediaType.APPLICATION_JSON)
class LinesResource(@PathParam("game") val game: String) {

    @GET
    @Path("{circulationCount}/{circulationOffset}/{numberCount}")
    fun getLines(@PathParam("circulationCount") circulationCount: Int,
                 @PathParam("circulationOffset") circulationOffset: Int,
                 @PathParam("numberCount") numberCount: Int): Response {
        if (numberCount < 1) {
            throw BadRequestException()
        }
        return processCirculations(game, circulationCount, circulationOffset) {
            Response.ok(it.map { it.properties["numbers"] as List<*> }
                    .flatMap { (0 until it.size).map { i -> i + 1 to it[i] as Long } }
                    .groupBy { it.first }
                    .mapValues {
                        it.value.groupBy { it.second }.map { it.key to it.value.size }
                                .sortedByDescending { it.second }
                                .take(numberCount)
                                .sortedBy { it.first }
                                .toMap()
                    })
                    .build()
        }
    }
}