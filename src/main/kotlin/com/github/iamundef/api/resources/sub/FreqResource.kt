package com.github.iamundef.api.resources.sub

import com.github.iamundef.api.resources.processCirculations
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Produces(MediaType.APPLICATION_JSON)
class FreqResource(@PathParam("game") val game: String) {

    @GET
    @Path("{circulationCount}/{numberCount}")
    fun getFreq(@PathParam("circulationCount") circulationCount: Int,
                @PathParam("numberCount") numberCount: Int): Response = getFreq(circulationCount, 0, numberCount, 0)

    @GET
    @Path("{circulationCount}/{circulationOffset}/{numberCount}")
    fun getFreq(@PathParam("circulationCount") circulationCount: Int,
                @PathParam("circulationOffset") circulationOffset: Int,
                @PathParam("numberCount") numberCount: Int): Response = getFreq(circulationCount, circulationOffset,
            numberCount, 0)

    @GET
    @Path("{circulationCount}/{circulationOffset}/{numberCount}/{tupleOffset}")
    fun getFreq(@PathParam("circulationCount") circulationCount: Int,
                @PathParam("circulationOffset") circulationOffset: Int,
                @PathParam("numberCount") numberCount: Int,
                @PathParam("tupleOffset") tupleOffset: Int): Response {
        if (numberCount < 1) {
            throw BadRequestException()
        }
        return processCirculations(game, circulationCount, circulationOffset) {
            Response.ok(it.map { it.properties["numbers"] as List<*> }
                    .flatMap {
                        when {
                            tupleOffset > 0 -> it.drop(tupleOffset)
                            tupleOffset < 0 -> it.dropLast(-tupleOffset)
                            else -> it
                        }
                    }
                    .groupBy { it as Long }
                    .map { it.key to it.value.size }
                    .sortedByDescending { it.second }
                    .take(numberCount)
                    .sortedBy { it.first }
                    .toMap())
                    .build()
        }
    }
}