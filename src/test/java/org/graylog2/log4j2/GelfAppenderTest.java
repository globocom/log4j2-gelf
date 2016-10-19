package org.graylog2.log4j2;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;
import org.graylog2.gelfclient.GelfMessage;
import org.graylog2.gelfclient.transport.GelfTransport;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GelfAppenderTest {

    GelfTransport mockedGelfTransport;

    @Test
    public void testLog() {
        final Logger logger = LogManager.getLogger("test");
        logger.info("Hello World");
    }

    @Test
    public void testMarker() {
        final Logger logger = LogManager.getLogger("test");
        final Marker parent = MarkerManager.getMarker("PARENT");
        final Marker marker = MarkerManager.getMarker("TEST").addParents(parent);
        logger.info(marker, "Hello World");
    }

    @Test
    public void testException() {
        final Logger logger = LogManager.getLogger("test");

        try {
            throw new Exception("Test", new Exception("Cause", new RuntimeException("Inner Cause")));
        } catch (Exception e) {
            e.fillInStackTrace();
            logger.error("Hello World", e);
        }
    }

    @Test
    public void testThreadContext() {
        final Logger logger = LogManager.getLogger("test");

        ThreadContext.push("Message only");
        ThreadContext.push("int", 1);
        ThreadContext.push("int-long-string", 1, 2L, "3");
        ThreadContext.put("key", "value");

        logger.info("Hello World");

        ThreadContext.clearAll();
    }

    @Test
    public void clientShouldUseSend() throws InterruptedException {
        mockedGelfTransport = mock(GelfTransport.class);

        //given
        final GelfAppender gelfAppender = createGelfAppender(true, false, true);
        final LogEvent event = createLogEventMock();


        doNothing().when(mockedGelfTransport).send(any(GelfMessage.class));


        // when
        gelfAppender.append(event);

        ArgumentCaptor<GelfMessage> gelfMessageCaptor = ArgumentCaptor.forClass(GelfMessage.class);
        verify(mockedGelfTransport).send(gelfMessageCaptor.capture());
    }

    @Test
    public void clientShouldUseTrySend() throws InterruptedException {
        mockedGelfTransport = mock(GelfTransport.class);

        //given
        final GelfAppender gelfAppender = createGelfAppender(true, false, false);
        final LogEvent event = createLogEventMock();


        when(mockedGelfTransport.trySend(any(GelfMessage.class))).thenReturn(true);

        // when
        gelfAppender.append(event);

        ArgumentCaptor<GelfMessage> gelfMessageCaptor = ArgumentCaptor.forClass(GelfMessage.class);
        verify(mockedGelfTransport).trySend(gelfMessageCaptor.capture());
    }


    @AfterClass
    public static void shutdown() throws InterruptedException {
        //need to wait to hope the underlying gelf client pushes the messages.
        Thread.sleep(500);
    }


    private GelfAppender createGelfAppender(final boolean includeStackTrace, final boolean includeExceptionCause, final boolean bloking) {
        GelfAppender gelfAppender = new GelfAppender("appender", null, null, false, null, "host", false, false, includeStackTrace,
                null, includeExceptionCause, bloking);
        gelfAppender.setClient(mockedGelfTransport);
        return gelfAppender;
    }


    private LogEvent createLogEventMock() {
        final Message message = mock(Message.class);
        given(message.getFormattedMessage()).willReturn("Some Message");

        final LogEvent event = mock(LogEvent.class);
        given(event.getMessage()).willReturn(message);
        given(event.getLevel()).willReturn(Level.ALL);
        return event;
    }
}
