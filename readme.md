# 协程
## 协程的概念
* 协程（Coroutine）提供了一种避免阻塞线程并用更简单、更可控的操作替代线程阻塞的方法：协程挂起
* 对线程的操作进一步抽象，使原来用“异步+回调”的方式写出来的复杂代码，简化成看似同步的方式。这样我们就可以按串行的思维去组织原本分散在不同上下文的逻辑，而不需要处理复杂的状态同步问题

协程的实现要维护一组局部状态，在重新进入协程前，保证这些状态不被改变，从而能顺利定位到之前的位置。
协程通过将复杂性放入库来简化异步编程。程序的逻辑可以在协程中顺序地表达，而底层库会为我们解决其异步性。该库可以将用户代码的相关部分包装为回调、订阅相关事件、在不同线程（甚至不同机器）上调度执行，而代码则保持如同顺序执行一样简单。

## Kotlin中的协程
* Kotlin 协程是一种用户态的轻量级线程
* Kotlin 是一门仅在标准库中提供最基本底层 API 以便各种其他库能够利用协程的语言
* async 与 await 在 Kotlin 中并不是关键字，甚至都不是标准库的一部分
* Kotlin 的 挂起函数 概念为异步操作提供了比 future 与 promise 更安全、更不易出错的抽象
* 为了使用协程以及按照本指南中的示例演练，需要添加对 kotlinx-coroutines-core 模块的依赖

## Hello World
协程可以用来解决嵌套回调、并发编程等。我们举一个例子，在没有协程时
```
Thread (
    Runnable {
        val result = doSomethingNeedTime() //耗时操作
        view.runOnUiThread {
            view.callBack(result)
            //如果这边要继续别的异步操作呢？就开始了传说中的地狱回调。。。
        }
    }
)
```
而有了协程之后
```
GlobalScope.launch { // 在后台启动一个新的协程并继续
  var result = ""
  withContext(Dispatchers.IO) { //切换上下文
    result = doSomethingNeedTime()
  }
  view.callBack(result)
  //这边可以直接继续做其它异步操作
}
```
关于demo的一些字段的讲解放在了最后的附录里面

## 协程构建器
* launch 函数可以启动一个协程
* 另一个函数构建是runBlocking
```
runBlocking {     // 但是这个表达式阻塞了主线程
    delay(2000L)  // ……我们延迟 2 秒
}
```

### 结构化的并发
上面这种用法的缺点：
* 使用 GlobalScope.launch 时，我们会创建一个顶层协程。虽然它很轻量，但它运行时仍会消耗一些内存资源
* 如果我们忘记保持对新启动的协程的引用，它还会继续运行
* 如果协程中的代码挂起了会怎么样（例如，我们错误地延迟了太长时间）
* 如果我们启动了太多的协程并导致内存不足会怎么样

总之  必须手动保持对所有已启动协程的引用并 join 之很容易出错。

有一个更好的解决办法。我们可以在代码中使用结构化并发。 我们可以在执行操作所在的指定作用域内启动协程， 而不是像通常使用线程（线程总是全局的）那样在 GlobalScope 中启动。

在我们的示例中，我们使用 runBlocking 协程构建器将 main 函数转换为协程。 包括 runBlocking 在内的每个协程构建器都将 CoroutineScope 的实例添加到其代码块所在的作用域中。 我们可以在这个作用域中启动协程而无需显式 join 之，因为外部协程（示例中的 runBlocking）直到在其作用域中启动的所有协程都执行完毕后才会结束。因此，可以将我们的示例简化为：
```
fun main() = runBlocking { // this: CoroutineScope
    launch { // 在 runBlocking 作用域中启动一个新协程
        delay(1000L)
        println("World!")
    }
    println("Hello,")
}
```

### 作用域构建器
除了由不同的构建器提供协程作用域之外，还可以使用 coroutineScope 构建器声明自己的作用域。它会创建一个协程作用域并且在所有已启动子协程执行完毕之前不会结束。

runBlocking 与 coroutineScope 的主要区别在于后者在等待所有子协程执行完毕时不会阻塞当前线程。

```
fun main() = runBlocking { // this: CoroutineScope
    launch {
        delay(200L)
        println("Task from runBlocking")
    }

    coroutineScope { // 创建一个协程作用域
        launch {
            delay(500L)
            println("Task from nested launch")
        }

        delay(100L)
        println("Task from coroutine scope") // 这一行会在内嵌 launch 之前输出
    }

    println("Coroutine scope is over") // 这一行在内嵌 launch 执行完毕后才输出
}
```


### 协程很轻量
启动了 10 万个协程，并且delay(1000)后，每个协程都输出一个点。 现在，尝试使用线程来实现。会发生什么？（很可能你的代码会产生某种内存不足的错误）

### 全局协程像守护线程
```
GlobalScope.launch {
    repeat(1000) { i ->
            println("I'm sleeping $i ...")
        delay(500L)
    }
}
delay(1300L) // 在延迟后退出
```
在 GlobalScope 中启动的活动协程并不会使进程保活。它们就像守护线程。

（一个守护线程是在后台执行并且不会阻止JVM终止的线程。当没有用户线程在运行的时候，JVM关闭程序并且退出。一个守护线程创建的子线程依然是守护线程。）

## 超时与取消
### 取消
* `job.cancel() `// 取消该作业
* `job.cancelAndJoin() ` //合并了对 cancel 以及 join 的调用。

一段协程代码必须协作才能被取消。 所有 kotlinx.coroutines 中的挂起函数都是 可被取消的 。它们检查协程的取消， 并在取消时抛出 CancellationException。 然而，如果协程正在执行计算任务，并且没有检查取消的话，那么它是不能被取消的，如下示例

```
val startTime = System.currentTimeMillis()
val job = launch(Dispatchers.Default) {
    var nextPrintTime = startTime
    var i = 0
    while (i < 5) { // 一个执行计算的循环，只是为了占用 CPU
        // 每秒打印消息两次
        if (System.currentTimeMillis() >= nextPrintTime) {
            println("job: I'm sleeping ${i++} ...")
            nextPrintTime += 500L
        }
    }
}
delay(1300L) // 等待一段时间
println("main: I'm tired of waiting!")
job.cancelAndJoin() // 取消一个作业并且等待它结束
println("main: Now I can quit.")

```
### 使计算代码可取消
* 定期调用挂起函数来检查取消。对于这种目的 yield 是一个好的选择
* 显式的检查取消状态 ：如  while (i < 5) 替换为 while (isActive)

isActive 是一个可以被使用在 CoroutineScope 中的扩展属性。

### 在 finally 中释放资源
我们通常使用如下的方法处理在被取消时抛出 CancellationException 的可被取消的挂起函数。比如说，try {……} finally {……} 表达式以及 Kotlin 的 use 函数一般在协程被取消的时候执行它们的终结动作
(Closeable.kt `fun <T : Closeable?, R> T.use` )

### 不能取消的代码块
在前一个例子中任何尝试在 finally 块中调用挂起函数的行为都会抛出 CancellationException，因为这里持续运行的代码是可以被取消的。通常，这并不是一个问题，所有良好的关闭操作（关闭一个文件、取消一个作业、或是关闭任何一种通信通道）通常都是非阻塞的，并且不会调用任何挂起函数。然而，在真实的案例中，当你需要挂起一个被取消的协程，你可以将相应的代码包装在 withContext(NonCancellable) {……} 中，并使用 withContext 函数以及 NonCancellable 上下文

### 超时
```
withTimeout(1300L) {
    ...
}
```
withTimeout 抛出了 TimeoutCancellationException，它是 CancellationException 的子类。

## 组合挂起
如果需要执行两个方法，且这两个方法没有依赖，可以并发进行，更快得到结果
```
suspend fun doSomethingUsefulOne(): Int {
    delay(1000L) // 假设我们在这里做了一些有用的事
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(1000L) // 假设我们在这里也做了一些有用的事
    return 29
}

val time = measureTimeMillis {
    val one = doSomethingUsefulOne()
    val two = doSomethingUsefulTwo()
    println("The answer is ${one + two}")
}
println("Completed in $time ms")
```
### 使用 async 并发
 async 就类似于 launch。它启动了一个单独的协程，这是一个轻量级的线程并与其它所有的协程一起并发的工作
```
val time = measureTimeMillis {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    println("The answer is ${one.await() + two.await()}")
}
println("Completed in $time ms")
```
## 协程上下文
*  协程总是运行在一些以 CoroutineContext 类型为代表的上下文中，它们被定义在了 Kotlin 的标准库里。
* 协程上下文是各种不同元素的集合。其中主元素是协程中的 Job, 也包括协程调度器

## 调度器
[coroutine-dispatcher](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/index.html)
确定了相应的协程在执行时使用一个或多个线程。

所有的协程构建器诸如 launch 和 async 接收一个可选的 CoroutineContext 参数，它可以被用来显式的为一个新协程或其它上下文元素指定一个调度器。

- Dispatchers.Unconfined 协程调度器会在程序运行到第一个挂起点时，在调用者线程中启动。挂起后，它将在挂起函数执行的线程中恢复，恢复的线程完全取决于该挂起函数在哪个线程执行。非受限调度器适合协程不消耗 CPU 时间也不更新任何限于特定线程的共享数据（如 UI）的情境。 安卓：`kotlinx.coroutines.experimental.Unconfined`，非受限调度器不应该被用在通常的代码中。
- 协程调度器默认承袭外部的 CoroutineScope 的调度器。 特别是 runBlocking 的默认协程调度器仅限于调用者线程，因此承袭它将会把执行限制在该线程中， 并具有可预测的 FIFO 调度的效果。
- launch等不传参时从启动它的CoroutineScope承袭上下文
- Default 没有指定时使用，基于Jvm的一个共享线程池
- Main 主线程，操作UI
- IO 为IO任务设计的一个共享线程池

### 在不同线程间跳转
* 使用launch等构建器时显式使用CoroutineContext指定上下文
* 在协程执行的代码块内部使用 withContext()方法切换上下文  

## 子协程
* 协程内通过自身的CoroutineScope创建的协程为子协程
* 子协程返回的Job是父协程的子任务
* 当一个父协程被取消时，所有它的子协程会被递归取消
* 当使用GlobalScope来启动一个协程时，不是子协程。它的作用域无关且是独立被启动的

## 协程VS线程
* 协程并不能取代线程，而是抽象于线程之上
* 线程是被分割的CPU资源，协程是组织好的代码流程
* 协程需要线程来承载运行，线程是协程的资源
* 协程直接利用执行器可以关联/使用任何线程或线程池
* 协程是编译器级别的，而线程是操作系统级别
* 协程通常是由编译器实现的机制，而线程虽然看起来也在语言的层次，但是是操作系统先有再暴露API给语言用
* 线程是抢占式的，而协程是非抢占式

### 优点
* CPU消耗低。与多线程、多进程等并发模型不同，协程依靠用户空间调度，而线程、进程则是依靠内核来进行调度。因此线程、进程间切换都需要从用户态进入内核态。而协程的切换完全是在用户态完成，程序只在用户空间内切换上下文，不再陷入内核来做线程切换，这样可以避免大量的用户空间和内核空间之间的数据拷贝，降低了CPU的消耗，从而大大减缓高并发场景时CPU瓶颈的窘境。
* 简化了多线程同步的复杂性。通常多个运行在同一调度器中的协程运行在一个线程内，这也消除掉了多线程同步等带来的编程复杂性。同一时刻同一调度器中的协程只有一个会处于运行状态。使用协程可以很简单地实现一个随时中断随时恢复的函数。Kotlin的协程库底层封装了丰富的协程函数，大大简化了并发编程的复杂度。
* 摆脱异步编程的一堆callback函数。使用协程，我们不再需要像异步编程时写那么一堆callback函数，代码结构不再支离破碎，整个代码逻辑上看上去和同步代码没什么区别，简单、易理解、优雅

### 内部实现机制
Continuation是一种描述程序的控制状态的抽象，它用一个数据结构来表示一个执行到指定位置的计算过程；这个数据结构可以由程序语言访问而不是隐藏在运行时环境中。

Continuation这个概念就协程来说就是协程保护的现场。而对于函数来说就是保存函数调用现场——Stack Frame值和寄存器，以供以后调用继续从Continuation处执行。换一个角度看，它也可以看作是非结构化Goto语句的函数表达。当我们执行Yield从协程返回的时候，需要保存的就是Continuation了。

Continuation在生成之后可作为控制结构使用，在调用时会从它所表示的控制点处恢复执行。One-shot continuation实现中，控制栈由栈段（Stack segment）组成的链表来表示，整个控制栈被构造成栈帧（Stack of frame）或活动记录（Activation record）。捕获Continuation时，当前栈段被保存到Continuation中，然后分配一个新的栈段。调用Continuation时，丢弃当前栈段，控制返回到之前保存的栈段。

创建一个协程同样包括分配一个单独的栈，但挂起和恢复协程的代价只比标准的函数调用略高。

Kotlin的协程完全通过编译技术实现（不需要来自VM或OS端的支持）。挂起机制是通过状态机来实现，其中的状态对应于挂起调用。在挂起时，对应的协程状态与局部变量等一起被存储在编译器生成的类的字段中。在恢复该协程时，恢复局部变量并且状态机从挂起点接着后面的状态继续执行。挂起的协程是作为Continuation对象来存储和传递，Continuation中持有协程挂起状态与局部变量。

刚看协程的时候觉得好神奇，挂起后又不阻塞线程，恢复后又能继续执行。现在我们知道了
果然所谓的岁月静好,不过是有人（底层库）替你负重前行

## 附录
### 参考资料
* [协程指南 - Kotlin 语言中文站](https://www.kotlincn.net/docs/reference/coroutines/coroutines-guide.html)
* [kotlinx.coroutines](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/)
* [android-architecture todo-mvp-kotlin-coroutines](https://github.com/dmytrodanylyk/android-architecture/tree/todo-mvp-kotlin-coroutines)
* 书籍《Kotlin 极简教程》-陈光剑 （出版时间17年9月，好像有一点点过时）

### Demo
[Github](https://github.com/samwangds/KotlinCoroutine)

开始写本文档时，还未开始协程实战，本文档及demo代码作为学习的笔记和总结。
demo 代码及本文档在后续实战中会继续完善。

### Demo知识点
* suspend 挂起函数，内部可以调用挂起函数 修饰符，有标记suspend的函数内部可以调用挂起函数，如delay
* with 在函数块内可以通过 this 指代该对象。返回值为函数块的最后一行或指定return表达式。适用于调用同一个类的多个方法时，可以省去类名重复，直接调用类的方法即可.
* withContext	Switches to a different context 函数来改变协程的上下文，而仍然驻留在相同的协程中。withContext执行完成返回时，上下文为 通过 CoroutineContext.plus 获得的
* launch 协程构建器
* CoroutineContext 类型为代表的上下文中，协程上下文是各种不同元素的集合。其中主元素是协程中的 Job
* CoroutineDispatcher 协程上下文包括了一个 协程调度器 , 它确定了相应的协程在执行时使用一个或多个线程。协程调度器可以将协程的执行局限在指定的线程中，调度它运行在线程池中或让它不受限的运行。
* [job](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/index.html) 概念上就是一个后台任务，可取消，在任务完成时生命周期结束。在CoroutineContext里，job代表的就是协程自己
* CoroutineScope 作用域，看源码可知，作用域里面包含了获取上下文的方法
* yield 检查取消
* CoroutineStart 协程的启动选项
* delay 作用类似Thread.sleep，区别在于delay是挂起函数，非阻塞
* Kotlin 标准库中的 use 函数来释放该线程。
* Job -> CoroutineContext.Element -> CoroutineContext isA的关系，启动协程后返回的Job持有该协程的引用

