package com.silicovegas.wombcare.core.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bridges an RTDB [Query] to a cold [Flow] that re-emits on every change and detaches its
 * listener when the collector goes away. This is the one place a `ValueEventListener` is
 * created, so nothing else has to remember to remove one — a leaked listener is a silent
 * battery/quota drain the compiler can't catch.
 *
 * A permission-denied (rules) error completes the flow rather than throwing into the
 * collector: for a doctor whose access was just revoked, "the data stopped" is the correct
 * outcome, not a crash.
 */
fun Query.valueFlow(): Flow<DataSnapshot> = callbackFlow {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            trySend(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            close() // revoked access / rules denial ends the stream cleanly
        }
    }
    addValueEventListener(listener)
    awaitClose { removeEventListener(listener) }
}
