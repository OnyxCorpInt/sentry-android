package io.sentry.core

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.exception.ExceptionMechanismException
import io.sentry.core.protocol.SentryId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UncaughtExceptionHandlerIntegrationTest {

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
    fun `when UncaughtExceptionHandlerIntegration is initialized, uncaught handler is unchanged`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        UncaughtExceptionHandlerIntegration(handlerMock)
        verifyZeroInteractions(handlerMock)
    }

    @Test
    fun `when uncaughtException is called, sentry captures exception`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val hubMock = mock<IHub>()
        val options = SentryOptions()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(hubMock).captureEvent(any(), any())
    }

    @Test
    fun `when register is called, current handler is not lost`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val defaultHandlerMock = mock<Thread.UncaughtExceptionHandler>()
        whenever(handlerMock.defaultUncaughtExceptionHandler).thenReturn(defaultHandlerMock)
        val hubMock = mock<IHub>()
        val options = SentryOptions()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(defaultHandlerMock).uncaughtException(threadMock, throwableMock)
    }

    @Test
    fun `when uncaughtException is called, exception captured has handled=false`() {
        val handlerMock = mock<UncaughtExceptionHandler>()
        val threadMock = mock<Thread>()
        val throwableMock = mock<Throwable>()
        val hubMock = mock<IHub>()
        whenever(hubMock.captureException(any())).thenAnswer { invocation ->
            val e = (invocation.arguments[1] as ExceptionMechanismException)
            assertNotNull(e)
            assertNotNull(e.exceptionMechanism)
            assertTrue(e.exceptionMechanism.isHandled)
            SentryId.EMPTY_ID
        }
        val options = SentryOptions()
        val sut = UncaughtExceptionHandlerIntegration(handlerMock)
        sut.register(hubMock, options)
        sut.uncaughtException(threadMock, throwableMock)
        verify(hubMock).captureEvent(any(), any())
    }

    @Test
    fun `when hub is closed, integrations should be closed`() {
        val integrationMock = mock<UncaughtExceptionHandlerIntegration>()
        val options = SentryOptions()
        options.dsn = "https://key@sentry.io/proj"
        options.addIntegration(integrationMock)
        options.cacheDirPath = file.absolutePath
        options.setSerializer(mock())
        val hub = Hub(options)
        verify(integrationMock).register(hub, options)
        hub.close()
        verify(integrationMock, times(1)).close()
    }
}
