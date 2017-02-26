package com.github.iamundef.api.utils

import com.google.appengine.api.datastore.DatastoreService
import com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService
import com.google.appengine.api.datastore.Transaction

inline fun <R> withDataStore(process: (DatastoreService) -> R): R = process(getDatastoreService())

inline fun <R> withTransaction(process: (DatastoreService, Transaction) -> R): R = withDataStore { dataStore ->
    dataStore.beginTransaction().let { txn ->
        try {
            process(dataStore, txn).apply { txn.commit() }
        } finally {
            if (txn.isActive) {
                txn.rollback()
            }
        }
    }
}
