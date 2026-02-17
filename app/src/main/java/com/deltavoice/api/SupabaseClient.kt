package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.SupabaseClient as SupabaseClientType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase client singleton
 */
object SupabaseClient {
    private var client: SupabaseClientType? = null
    
    fun getClient(): SupabaseClientType {
        if (client == null) {
            client = createSupabaseClient(
                supabaseUrl = SupabaseConfig.SUPABASE_URL,
                supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
            ) {
                install(Postgrest)
                install(Functions)
            }
        }
        return client!!
    }
    
    fun reset() {
        client = null
    }
}

