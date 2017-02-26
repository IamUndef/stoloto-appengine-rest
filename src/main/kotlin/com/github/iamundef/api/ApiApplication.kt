package com.github.iamundef.api

import com.github.iamundef.api.resources.GameResource
import com.github.iamundef.api.resources.UpdateResource
import javax.ws.rs.core.Application

class ApiApplication : Application() {

    override fun getClasses(): MutableSet<Class<*>> {
        return mutableSetOf(UpdateResource::class.java, GameResource::class.java)
    }
}
