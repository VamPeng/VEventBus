package com.vam.eventbus

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.vam.eventbus.annotation.Subscribe
import com.vam.eventbus.model.UserInfo

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    @Subscribe
    public fun testSubscribe(info: UserInfo) {
        Log.i("Vam >>> ", info.toString())
    }

}