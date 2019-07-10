package com.samwang.kotlincoroutine

import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class CoroutinePresenter(private val view: MainActivity): CoroutineScope {
    //job用于控制协程,后面launch{}启动的协程
    val job:Job by lazy { Job() }

    //继承CoroutineScope必须初始化coroutineContext变量
    // 这个是标准写法,+其实是plus方法前面表示job,用于控制协程,后面是Dispatchers,指定启动的线程
    override val coroutineContext: CoroutineContext // 实现 CoroutineScope 接口
        get() = job + Dispatchers.Main

    // 1次异步操作
    fun aysncLoadData() = launch(Dispatchers.Main) {
        view.setText("Start")
        Log.e("SAM", " Start ======= ")
        var value = ""
        withContext(Dispatchers.IO) {
            Thread.sleep(2000)         //耗时操作
            value = "END"
        }

        view.setText(value)
        Log.e("SAM", " END ======= ")

    }

    fun loadDataAndUpdateProgress() = launch {
        updateStartEndView()
        withContext(Dispatchers.IO) {
            repeat(10){
                Thread.sleep(500)
                withContext(coroutineContext) {
                    Log.e("SAM", " update $it")
                    view.updateProgress(it * 10 + 10)
                }
            }
        }

        updateStartEndView(false)
    }

    // 针对 loadDataAndUpdateProgress 的另一种写法，实际开发这种情况较少（多任务互相依赖，并总体更新一个进度的情况）
    fun loadDataAndUpdateProgress2() = launch {
        updateStartEndView()
        repeat(10) {
            withContext(Dispatchers.IO) {
                Thread.sleep(500)
            }
            Log.e("SAM", " update $it")
            view.updateProgress(it * 10 + 10)
        }
        updateStartEndView(false)
    }

    fun onDestroy() {
        job.cancel() //取消了这个总的协程、也会递归取消所有子协程
    }

    private fun updateStartEndView(isStart: Boolean = true) {
        if (isStart) {
            view.setText("Start")
            Log.e("SAM", " Start ======= ")
        } else {
            view.setText("End")
            Log.e("SAM", " End ======= ")
        }
    }

    // 普通线程操作
    fun aysncLoadDataByThread() {
        view.setText("Start")
        var value: String
        Thread (
            Runnable {
                Thread.sleep(2000)
                value = "END"
                view.runOnUiThread {
                    view.setText(value)
                }
            }
        )
    }


}