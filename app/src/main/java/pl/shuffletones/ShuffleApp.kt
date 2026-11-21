package pl.shuffletones

import android.app.Application

class ShuffleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
