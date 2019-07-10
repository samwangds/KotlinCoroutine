package com.samwang.kotlincoroutine

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.IntRange
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity() {
    private lateinit var presenter: CoroutinePresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = CoroutinePresenter(this)
        setContentView(R.layout.activity_main)

        tv.setOnClickListener {
           val job =  presenter.loadDataAndUpdateProgress()
            printJobInfo(job)
        }
    }

    fun setText(text: String) {
        tv.text = text
    }

    fun updateProgress(@IntRange(from = 0, to = 100) progress: Int) {
        progressBar.progress = progress
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    private fun printJobInfo(job: Job) {
        Log.e("SAM", "job : $job" )
        presenter.job.children.forEach {
            Log.e("SAM", "presenter job children : $it" )
        }

    }

    /** 支持取消的代码
    var job: Job? = null

    tv.setOnClickListener {
    job?.let {
    it.cancel()
    } ?: let {
    job = presenter.loadDataAndUpdateProgress2()
    }
    }

     */
}
