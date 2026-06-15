package org.freeplane.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CapturedPrintStreamTest {
    @Test
    public void printStreamWritesToCaptureAndLiveStream() throws Exception {
        ByteArrayOutputStream liveBuffer = new ByteArrayOutputStream();
        PrintStream liveStream = utf8PrintStream(liveBuffer);
        CapturedPrintStream capture = CapturedPrintStream.tee(liveStream, StandardCharsets.UTF_8);

        capture.printStream().print("hello");

        assertThat(capture.text()).isEqualTo("hello");
        assertThat(liveBuffer.toString("UTF-8")).isEqualTo("hello");
    }

    @Test
    public void bytesReturnsCapturedBytes() throws Exception {
        CapturedPrintStream capture = CapturedPrintStream.tee(null, StandardCharsets.UTF_8);

        capture.printStream().print("hello");

        assertThat(new String(capture.bytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    public void closeDoesNotCloseLiveStream() throws Exception {
        CloseAwareOutputStream liveBuffer = new CloseAwareOutputStream();
        PrintStream liveStream = utf8PrintStream(liveBuffer);
        CapturedPrintStream capture = CapturedPrintStream.tee(liveStream, StandardCharsets.UTF_8);

        capture.printStream().print("before");
        capture.close();
        liveStream.print("after");
        liveStream.flush();

        assertThat(liveBuffer.closed).isFalse();
        assertThat(liveBuffer.toString("UTF-8")).isEqualTo("beforeafter");
    }

    private static PrintStream utf8PrintStream(ByteArrayOutputStream stream) throws UnsupportedEncodingException {
        return new PrintStream(stream, false, "UTF-8");
    }

    private static class CloseAwareOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
