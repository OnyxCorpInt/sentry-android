package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.protocol.SentryId
import io.sentry.core.protocol.User
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.util.Queue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HubTest {

    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        file = Files.createTempDirectory("sentry-disk-cache-test").toAbsolutePath().toFile()
    }

    @AfterTest
    fun shutdown() {
        Files.delete(file.toPath())
    }

    @Test
    fun `when no dsn available, ctor throws illegal arg`() {
        val ex = assertFailsWith<IllegalArgumentException> { Hub(SentryOptions()) }
        assertEquals("Hub requires a DSN to be instantiated. Considering using the NoOpHub is no DSN is available.", ex.message)
    }

    @Test
    fun `when a root hub is initialized, integrations are registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.addIntegration(integrationMock)
        val expected = Hub(options)
        verify(integrationMock).register(expected, options)
    }

    @Test
    fun `when hub is cloned, integrations are not registered`() {
        val integrationMock = mock<Integration>()
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        options.addIntegration(integrationMock)
        val expected = Hub(options)
        verify(integrationMock).register(expected, options)
        expected.clone()
        verifyNoMoreInteractions(integrationMock)
    }

    @Test
    fun `when hub is cloned, scope changes are isolated`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val hub = Hub(options)
        var firstScope: Scope? = null
        hub.configureScope {
            firstScope = it
            it.setTag("hub", "a")
        }
        var cloneScope: Scope? = null
        val clone = hub.clone()
        clone.configureScope {
            cloneScope = it
            it.setTag("hub", "b")
        }
        assertEquals("a", firstScope!!.tags["hub"])
        assertEquals("b", cloneScope!!.tags["hub"])
    }

    @Test
    fun `when hub is initialized, breadcrumbs are capped as per options`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.maxBreadcrumbs = 5
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        (1..10).forEach { _ -> sut.addBreadcrumb(Breadcrumb(), null) }
        var actual = 0
        sut.configureScope {
            actual = it.breadcrumbs.size
        }
        assertEquals(options.maxBreadcrumbs, actual)
    }

    @Test
    fun `when beforeBreadcrumb returns null, crumb is dropped`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback {
            _: Breadcrumb, _: Any? -> null }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.addBreadcrumb(Breadcrumb(), null)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(0, breadcrumbs!!.size)
    }

    @Test
    fun `when beforeBreadcrumb modifies crumb, crumb is stored modified`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        val expected = "expected"
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb: Breadcrumb, _: Any? -> breadcrumb.message = expected; breadcrumb; }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val crumb = Breadcrumb()
        crumb.message = "original"
        sut.addBreadcrumb(crumb)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.first().message)
    }

    @Test
    fun `when beforeBreadcrumb is null, crumb is stored`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = null
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val expected = Breadcrumb()
        sut.addBreadcrumb(expected)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        assertEquals(expected, breadcrumbs!!.single())
    }

    @Test
    fun `when beforeSend throws an exception, breadcrumb adds an entry to the data field with exception message and stacktrace`() {
        val exception = Exception("test")
        val sw = StringWriter()
        exception.printStackTrace(PrintWriter(sw))
        val stacktrace = sw.toString()

        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { _: Breadcrumb, _: Any? -> throw exception }
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)

        val actual = Breadcrumb()
        sut.addBreadcrumb(actual)

        assertEquals("test", actual.data["sentry:message"])
        assertEquals(stacktrace, actual.data["sentry:stacktrace"])
    }

    @Test
    fun `when initialized, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when addBreadcrumb is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        var breadcrumbs: Queue<Breadcrumb>? = null
        sut.configureScope { breadcrumbs = it.breadcrumbs }
        sut.close()
        sut.addBreadcrumb(Breadcrumb())
        assertTrue(breadcrumbs!!.isEmpty())
    }

    @Test
    fun `when flush is called on disabled client, no-op`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.flush(1000)
        verify(mockClient, never()).flush(1000)
    }

    @Test
    fun `when flush is called, client flush gets called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.flush(1000)
        verify(mockClient).flush(1000)
    }

    //region captureEvent tests
    @Test
    fun `when captureEvent is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.captureEvent(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureEvent is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureEvent(SentryEvent())
        verify(mockClient, never()).captureEvent(any(), any())
    }

    @Test
    fun `when captureEvent is called with a valid argument, captureEvent on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val event = SentryEvent()
        val hint = { }
        sut.captureEvent(event, hint)
        verify(mockClient, times(1)).captureEvent(eq(event), any(), eq(hint))
    }
    //endregion

    //region captureMessage tests
    @Test
    fun `when captureMessage is called and event is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.captureMessage(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureMessage is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureMessage("test")
        verify(mockClient, never()).captureMessage(any(), any())
    }

    @Test
    fun `when captureMessage is called with a valid message, captureMessage on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureMessage("test")
        verify(mockClient, times(1)).captureMessage(any(), any(), any())
    }

    @Test
    fun `when captureMessage is called, level is INFO by default`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.captureMessage("test")
        verify(mockClient, times(1)).captureMessage(eq("test"), eq(SentryLevel.INFO), any())
    }
    //endregion

    //region captureException tests
    @Test
    fun `when captureException is called and exception is null, lastEventId is empty`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        sut.captureException(null)
        assertEquals(SentryId.EMPTY_ID, sut.lastEventId)
    }

    @Test
    fun `when captureException is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.captureException(Throwable())
        verify(mockClient, never()).captureException(any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument and hint, captureException on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureException(Throwable(), Object())
        verify(mockClient, times(1)).captureException(any(), any(), any())
    }

    @Test
    fun `when captureException is called with a valid argument but no hint, captureException on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.captureException(Throwable())
        verify(mockClient, times(1)).captureException(any(), any(), isNull())
    }
    //endregion

    //region close tests
    @Test
    fun `when close is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)
        sut.close()

        sut.close()
        verify(mockClient, times(1)).close() // 1 to close, but next one wont be recorded
    }

    @Test
    fun `when close is called and client is alive, close on the client should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        sut.close()
        verify(mockClient, times(1)).close()
    }
    //endregion

    //region withScope tests
    @Test
    fun `when withScope is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.withScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when withScope is called with alive client, run should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()

        sut.withScope(scopeCallback)
        verify(scopeCallback, times(1)).run(any())
    }
    //endregion

    //region configureScope tests
    @Test
    fun `when configureScope is called on disabled client, do nothing`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()
        sut.close()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, never()).run(any())
    }

    @Test
    fun `when configureScope is called with alive client, run should be called`() {
        val options = SentryOptions()
        options.cacheDirPath = file.absolutePath
        options.dsn = "https://key@sentry.io/proj"
        options.setSerializer(mock())
        val sut = Hub(options)
        val mockClient = mock<ISentryClient>()
        sut.bindClient(mockClient)

        val scopeCallback = mock<ScopeCallback>()

        sut.configureScope(scopeCallback)
        verify(scopeCallback, times(1)).run(any())
    }
    //endregion

    @Test
    fun `when integration is registered, hub is enabled`() {
        val mock = mock<Integration>()
        val options = SentryOptions().apply {
            addIntegration(mock)
            dsn = "https://key@sentry.io/proj"
            cacheDirPath = file.absolutePath
            setSerializer(mock())
        }
        doAnswer {
            val hub = it.arguments[0] as IHub
            assertTrue(hub.isEnabled)
        }.whenever(mock).register(any(), eq(options))
        Hub(options)
        verify(mock, times(1)).register(any(), eq(options))
    }

    //region setLevel tests
    @Test
    fun `when setLevel is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setLevel(SentryLevel.INFO)
        assertNull(scope?.level)
    }

    @Test
    fun `when setLevel is called, level is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setLevel(SentryLevel.INFO)
        assertEquals(SentryLevel.INFO, scope?.level)
    }
    //endregion

    //region setTransaction tests
    @Test
    fun `when setTransaction is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setTransaction("test")
        assertNull(scope?.transaction)
    }

    @Test
    fun `when setTransaction is called, transaction is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setTransaction("test")
        assertEquals("test", scope?.transaction)
    }
    //endregion

    //region setUser tests
    @Test
    fun `when setUser is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setUser(User())
        assertNull(scope?.user)
    }

    @Test
    fun `when setUser is called, user is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        val user = User()
        hub.setUser(user)
        assertEquals(user, scope?.user)
    }
    //endregion

    //region setFingerprint tests
    @Test
    fun `when setFingerprint is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        val fingerprint = listOf("abc")
        hub.setFingerprint(fingerprint)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called with null parameter, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setFingerprint(null)
        assertEquals(0, scope?.fingerprint?.count())
    }

    @Test
    fun `when setFingerprint is called, fingerprint is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        val fingerprint = listOf("abc")
        hub.setFingerprint(fingerprint)
        assertEquals(1, scope?.fingerprint?.count())
    }
    //endregion

    //region clearBreadcrumbs tests
    @Test
    fun `when clearBreadcrumbs is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())

        hub.close()

        hub.clearBreadcrumbs()
        assertEquals(1, scope?.breadcrumbs?.count())
    }

    @Test
    fun `when clearBreadcrumbs is called, clear breadcrumbs`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope?.breadcrumbs?.count())
        hub.clearBreadcrumbs()
        assertEquals(0, scope?.breadcrumbs?.count())
    }
    //endregion

    //region setTag tests
    @Test
    fun `when setTag is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setTag("test", "test")
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called with null parameters, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setTag(null, null)
        assertEquals(0, scope?.tags?.count())
    }

    @Test
    fun `when setTag is called, tag is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setTag("test", "test")
        assertEquals(1, scope?.tags?.count())
    }
    //endregion

    //region setExtra tests
    @Test
    fun `when setExtra is called on disabled client, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }
        hub.close()

        hub.setExtra("test", "test")
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called with null parameters, do nothing`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setExtra(null, null)
        assertEquals(0, scope?.extras?.count())
    }

    @Test
    fun `when setExtra is called, extra is set`() {
        val hub = generateHub()
        var scope: Scope? = null
        hub.configureScope {
            scope = it
        }

        hub.setExtra("test", "test")
        assertEquals(1, scope?.extras?.count())
    }
    //endregion

    private fun generateHub(): IHub {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            cacheDirPath = file.absolutePath
            setSerializer(mock())
        }
        return Hub(options)
    }
}
