package org.freeplane.core.util;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.TeeOutputStream;

public final class CapturedPrintStream implements AutoCloseable {
    private final ByteArrayOutputStream captureBuffer;
    private final PrintStream printStream;
    private final Charset charset;

    public static CapturedPrintStream tee(PrintStream liveStream, Charset charset) {
        return new CapturedPrintStream(liveStream, charset);
    }

    public static CapturedPrintStream tee(PrintStream liveStream) {
        return tee(liveStream, StandardCharsets.UTF_8);
    }

    private CapturedPrintStream(PrintStream liveStream, Charset charset) {
        this.captureBuffer = new ByteArrayOutputStream();
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
        this.printStream = newPrintStream(liveStream);
    }

    public PrintStream printStream() {
        return printStream;
    }

    public String text() {
        printStream.flush();
        String text = new String(captureBuffer.toByteArray(), charset);
        return text.isEmpty() ? null : text;
    }

    public byte[] bytes() {
        printStream.flush();
        return captureBuffer.toByteArray();
    }

    @Override
    public void close() {
        printStream.close();
    }

    private PrintStream newPrintStream(PrintStream liveStream) {
        OutputStream outputStream = captureBuffer;
        if (liveStream != null) {
            outputStream = new TeeOutputStream(captureBuffer, CloseShieldOutputStream.wrap(liveStream));
        }
        try {
            return new PrintStream(outputStream, false, charset.name());
        } catch (UnsupportedEncodingException error) {
            throw new IllegalStateException(charset.name() + " is not available.", error);
        }
    }
}
