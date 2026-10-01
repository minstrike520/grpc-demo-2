package com.example.grpcsim

import android.app.Application

/**
 * The gRPC connection lives at the application level (not on any screen),
 * so rotating the phone or recreating the activity never drops the connection.
 */
class SimApplication : Application() {

    lateinit var grpc: GrpcManager
        private set

    override fun onCreate() {
        super.onCreate()
        grpc = GrpcManager(this).also { it.start() }
    }
}