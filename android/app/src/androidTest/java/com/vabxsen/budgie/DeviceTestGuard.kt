package com.vabxsen.budgie

import com.google.firebase.auth.FirebaseAuth
import org.junit.Assume.assumeTrue

/** Device tests replace the collection, so they skip rather than touch a signed-in account. */
fun assumeSignedOut() =
    assumeTrue(
        "Sign out of Budgie before running device tests; they replace the collection.",
        FirebaseAuth.getInstance().currentUser == null,
    )
