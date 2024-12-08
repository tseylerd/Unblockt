// Copyright 2024 Dmitrii Tseiler. Licensed under the PolyForm Perimeter License 1.0.0 (https://polyformproject.org/licenses/perimeter/1.0.0)

package tse.unblockt.ls.protocol

import tse.unblockt.ls.rpc.RPCMethodCall
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.jvmErasure

interface LanguageClient {
    companion object {
        operator fun invoke(data: ClientData, send: suspend (RPCMethodCall<*, *>) -> Any?): LanguageClient {
            return LanguageClientImpl(data, send)
        }
    }

    val data: ClientData

    suspend fun <T> workspace(call: Workspace.() -> T): T
    suspend fun <T> unblockt(call: Unblockt.() -> T): T
    suspend fun <T> window(call: Window.() -> T): T
    suspend fun registerCapability(call: DataBag<RegistrationParams>.() -> Unit)
    suspend fun progress(call: DataBag<ProgressParams>.() -> Unit)

    interface Factory {
        fun create(send: suspend (RPCMethodCall<*, *>) -> Any?): LanguageClient

        object Default : Factory {
            override fun create(send: suspend (RPCMethodCall<*, *>) -> Any?): LanguageClient {
                return LanguageClient(ClientData("vscode"), send)
            }
        }
    }

    data class ClientData(val name: String)
}

interface Workspace {
    fun <T> semanticTokens(call: ClientSemanticTokens.() -> T): T
    fun applyEdit(call: DataBag<ApplyEditsParams>.() -> Unit): ApplyEditsResult
}

interface Unblockt {
    fun <T> messages(call: Messages.() -> T): T
    fun status(call: (DataBag<HealthStatusInformation>).() -> Unit)
}

interface Messages {
    fun <T> gradle(call: Gradle.() -> T): T
}

interface Gradle {
    fun message(call: DataBag<MessageParams>.() -> Unit)
}

interface Window {
    fun workDoneProgress(call: DataBag<ProgressParams>.() -> Unit)
}

interface ClientSemanticTokens {
    fun refresh(call: DataBag<Unit>.() -> Unit)
}

interface DataBag<T : Any> {
    var data: T?
}

private class LanguageClientImpl(
    override val data: LanguageClient.ClientData,
    private val send: suspend (RPCMethodCall<*, *>) -> Any?,
) : LanguageClient {
    override suspend fun <T> workspace(call: Workspace.() -> T): T {
        return callClientMethod { ctx ->
            ctx.called("workspace")
            val workspaceImpl = WorkspaceImpl(ctx)
            workspaceImpl.call()
        }
    }

    override suspend fun registerCapability(call: DataBag<RegistrationParams>.() -> Unit) {
        return callClientMethod { ctx ->
            ctx.called("client")
            ctx.called("registerCapability")
            val db = DataBagImpl<RegistrationParams>()
            db.call()
            ctx.pass(db.data)
        }
    }

    override suspend fun <T> unblockt(call: Unblockt.() -> T): T {
        return callClientMethod { ctx ->
            ctx.called("unblockt")
            val kontrolImpl = UnblocktImpl(ctx)
            kontrolImpl.call()
        }
    }

    override suspend fun <T> window(call: Window.() -> T): T {
        return callClientMethod { ctx ->
            ctx.called("window")
            WindowImpl(ctx).call()
        }
    }

    override suspend fun progress(call: DataBag<ProgressParams>.() -> Unit) {
        callClientMethod<Unit> { ctx ->
            ctx.called("progress")
            ctx.thisIsNotification()
            val bag = DataBagImpl<ProgressParams>()
            bag.call()
            ctx.pass(bag.data)
            ctx.expect(Unit::class)
        }
    }

    private suspend fun <T> callClientMethod(call: (CallContext) -> Unit): T {
        val ctx = CallContext()
        call(ctx)
        val result = ctx.build()

        @Suppress("UNCHECKED_CAST")
        val clazz: KClass<Any>? = result.data?.let { it::class } as? KClass<Any>
        val callToMake = RPCMethodCall(result.method, result.data, clazz, result.responseClass, result.isNotification)
        val sent = send(callToMake)
        @Suppress("UNCHECKED_CAST")
        return sent as T
    }
}

private class WorkspaceImpl(private val context: CallContext) : Workspace {
    override fun <T> semanticTokens(call: ClientSemanticTokens.() -> T): T {
        context.called("semanticTokens")

        val tokens = ClientSemanticTokensImpl(context)
        return tokens.call()
    }

    override fun applyEdit(call: DataBag<ApplyEditsParams>.() -> Unit): ApplyEditsResult {
        context.called(::applyEdit)

        val bag = DataBagImpl<ApplyEditsParams>()
        bag.call()
        context.pass(bag.data)
        return ApplyEditsResult(false, null, null)
    }
}

private class UnblocktImpl(private val context: CallContext): Unblockt {
    override fun <T> messages(call: Messages.() -> T): T {
        context.called("messages")
        val messagesImpl = MessagesImpl(context)
        return messagesImpl.call()
    }

    override fun status(call: DataBag<HealthStatusInformation>.() -> Unit) {
        context.called("status")
        val db = DataBagImpl<HealthStatusInformation>()
        db.call()
        context.pass(db.data)
        context.thisIsNotification()
    }
}

private class MessagesImpl(private val context: CallContext) : Messages {
    override fun <T> gradle(call: Gradle.() -> T): T {
        context.called("gradle")
        val gradleImpl = GradleImpl(context)
        return gradleImpl.call()
    }
}

private class GradleImpl(private val context: CallContext) : Gradle {
    override fun message(call: DataBag<MessageParams>.() -> Unit) {
        context.called("message")
        val bag = DataBagImpl<MessageParams>()
        bag.call()
        context.pass(bag.data)
        context.thisIsNotification()
    }
}
private class WindowImpl(private val context: CallContext) : Window {
    override fun workDoneProgress(call: DataBag<ProgressParams>.() -> Unit) {
        context.called("workDoneProgress")

        val dataBag = DataBagImpl<ProgressParams>()
        dataBag.call()
        context.pass(dataBag.data)
    }
}

private class ClientSemanticTokensImpl(private val context: CallContext) : ClientSemanticTokens {
    override fun refresh(call: DataBag<Unit>.() -> Unit) {
        context.called(this::refresh)
        context.thisIsNotification()
    }
}

private class DataBagImpl<T : Any> : DataBag<T> {
    override var data: T? = null
}

private class CallContext {
    private val functions = mutableListOf<String>()
    private var data: Any? = null
    private var responseClass: KClass<*> = Unit::class
    private var notification: Boolean = false

    fun called(method: KFunction<*>) {
        called(method.name)
        expect(method.returnType.jvmErasure)
    }

    fun called(name: String) {
        functions += name
    }

    fun pass(data: Any?) {
        this.data = data
    }

    fun expect(clazz: KClass<*>) {
        this.responseClass = clazz
    }

    fun build(): Call {
        return Call(
            method = when {
                functions.size == 1 && notification -> "\$/${functions.single()}"
                else -> functions.joinToString("/")
            },
            data = data,
            isNotification = notification,
            responseClass = responseClass,
        )
    }

    fun thisIsNotification() {
        notification = true
    }

    data class Call(
        val method: String,
        val data: Any?,
        val isNotification: Boolean = false,
        val responseClass: KClass<*> = Unit::class,
    )
}